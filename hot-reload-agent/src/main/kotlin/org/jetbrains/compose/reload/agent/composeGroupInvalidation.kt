package org.jetbrains.compose.reload.agent

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.objectweb.asm.*
import org.objectweb.asm.Opcodes.ASM9
import java.lang.instrument.ClassFileTransformer
import java.lang.instrument.Instrumentation
import java.security.ProtectionDomain
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.CRC32
import kotlin.coroutines.EmptyCoroutineContext

private val logger = createLogger()

private const val composerClazzId = "androidx/compose/runtime/Composer"
private const val startReplaceGroupMethodName = "startReplaceGroup"
private const val startRestartGroupMethodName = "startRestartGroup"
private const val endReplaceGroupMethodName = "endReplaceGroup"
private const val endRestartGroupMethodName = "endRestartGroup"
private const val functionKeyMetaConstructorDescriptor = "Landroidx/compose/runtime/internal/FunctionKeyMeta;"


/**
 * The actual key emitted by the Compose Compiler associated with the group.
 * For example, a call like
 * ```kotlin
 * startRestartGroup(1902)
 *```
 *
 * will have the value '1902' recorded as [ComposeGroupKey].
 */
@JvmInline
internal value class ComposeGroupKey(val key: Int)

/**
 * The runtime 'group invalidation key'. This value is calculated purely at runtime.
 * If this key changes after reloading the class, then we know that the group requires invalidation.
 */
@JvmInline
internal value class ComposeGroupInvalidationKey(val key: Int)

/**
 * Runtime information for a given Compose group:
 * @param callSiteMethodFqn: The fqn of the callsite function.
 * ```kotlin
 * // App.kt
 * package foo
 * @Composable fun Bar() {
 *     startRestartGroup(1902) // <- callSiteMethodFqn = foo.AppKt.Bar
 * }
 * ```
 *
 * @param invalidationKey: See [ComposeGroupInvalidationKey]
 */
internal data class ComposeGroupRuntimeInfo(
    val callSiteMethodFqn: String,
    val parentComposeGroupKey: ComposeGroupKey?,
    val invalidationKey: ComposeGroupInvalidationKey,
)

private val globalComposeComposeGroupRuntimeInfo = ConcurrentHashMap<ComposeGroupKey, ComposeGroupRuntimeInfo>()

private val globalInvalidComposeGroupKeys = ConcurrentHashMap<ComposeGroupKey, ComposeGroupRuntimeInfo>()

internal fun startComposeGroupInvalidationTransformation(instrumentation: Instrumentation) {
    /*
    Instruct Compose to invalidate groups that have changed, after successful reload.
     */
    ComposeHotReloadAgent.invokeAfterReload { reloadRequestId, error ->
        if (error != null) return@invokeAfterReload

        val invalidations = globalInvalidComposeGroupKeys.keys.toList()
        globalInvalidComposeGroupKeys.clear()


        if (invalidations.isEmpty()) {
            logger.orchestration("All groups retained")
        }

        val uniqueInvalidationRoots = hashSetOf<ComposeGroupKey>()
        invalidations.forEach { groupKey ->
            /* Find root */
            var root: ComposeGroupKey = groupKey
            while (true) {
                root = globalComposeComposeGroupRuntimeInfo[root]?.parentComposeGroupKey ?: break
            }

            /* Add root to queue of invalidations */
            uniqueInvalidationRoots.add(root)
        }

        uniqueInvalidationRoots.forEach { root ->
            val rootInfo = globalComposeComposeGroupRuntimeInfo[root]
            logger.orchestration("Invalidating group at '${rootInfo?.callSiteMethodFqn}' ('$root', '${rootInfo?.invalidationKey}')")
            invalidateGroupsWithKey(root)
        }
    }

    /*
    Register the transformer which will be invoked on all byte-code updating the global group information
     */
    instrumentation.addTransformer(ComposeGroupInvalidationKeyTransformer)
}

/*
This transformer is intended to run on all classes, so that runtime information about Compose groups
is recorded and invalidations can be tracked.
 */
internal object ComposeGroupInvalidationKeyTransformer : ClassFileTransformer {
    override fun transform(
        loader: ClassLoader?, className: String?, classBeingRedefined: Class<*>?,
        protectionDomain: ProtectionDomain?, classfileBuffer: ByteArray
    ): ByteArray? {
        try {
            refreshComposeGroupRuntimeInfos(classBeingRedefined, classfileBuffer)
        } catch (t: Throwable) {
            logger.error("Failed refreshing compose group invalidation keys for '$className'", t)
        }

        return classfileBuffer
    }
}

internal fun refreshComposeGroupRuntimeInfos(
    classBeingRedefined: Class<*>?,
    classfileBuffer: ByteArray
) {
    val keys = parseComposeGroupRuntimeInfos(classfileBuffer)
    keys.forEach { groupKey, groupRuntimeInfo ->
        val previousRuntimeInfo = globalComposeComposeGroupRuntimeInfo.put(groupKey, groupRuntimeInfo)
        if (
            classBeingRedefined != null &&
            previousRuntimeInfo != null && previousRuntimeInfo.invalidationKey != groupRuntimeInfo.invalidationKey
        ) {
            globalInvalidComposeGroupKeys.put(groupKey, groupRuntimeInfo)
        }
    }
}

internal fun parseComposeGroupRuntimeInfos(classFile: ByteArray): Map<ComposeGroupKey, ComposeGroupRuntimeInfo> {
    val reader = ClassReader(classFile)
    val composeGroupRuntimeInfoBuilder = ComposeGroupRuntimeInfoBuilder()
    reader.accept(composeGroupRuntimeInfoBuilder, 0)
    return composeGroupRuntimeInfoBuilder.groups
}

private class ComposeGroupRuntimeInfoBuilder() : ClassVisitor(ASM9) {
    val groups = hashMapOf<ComposeGroupKey, ComposeGroupRuntimeInfo>()

    private var className: String? = null

    override fun visit(
        version: Int, access: Int, name: String?,
        signature: String?, superName: String?, interfaces: Array<out String?>?
    ) {
        this.className = name
        super.visit(version, access, name, signature, superName, interfaces)
    }

    override fun visitMethod(
        access: Int, name: String?, descriptor: String?, signature: String?, exceptions: Array<out String?>?
    ): MethodVisitor? = ReloadKeyMethodVisitor(
        callSiteMethodFqn = "$className.$name", groups = groups,
    )
}

private class ReloadKeyMethodVisitor(
    private val callSiteMethodFqn: String,
    private val groups: MutableMap<ComposeGroupKey, ComposeGroupRuntimeInfo>
) : MethodVisitor(ASM9) {

    private var lastIntValue = 0

    private val stack = ArrayDeque<ComposeGroupRuntimeInfoBuilder>()

    inner class ComposeGroupRuntimeInfoBuilder(
        val parentComposeGroupKey: ComposeGroupKey?,
        val composeGroupKey: ComposeGroupKey,
    ) {
        private var internalStackSize = 0
        private val crc: CRC32 = CRC32()

        fun pushGroup() {
            internalStackSize++
        }

        fun popGroup(): Boolean {
            if (internalStackSize == 0) return false
            internalStackSize--
            return true
        }

        fun pushHashIfNecessary(value: Any?) {
            if (value == null) return
            when (value) {
                is Boolean -> crc.update(if (value) 1 else 0)
                is String -> crc.update(value.toByteArray())
                is Int -> crc.update(value)
                is Byte -> crc.update(value.toInt())
                is Float -> crc.update(value.toRawBits())
                else -> crc.update(value.toString().toByteArray())
            }
        }

        fun createGroupRuntimeInfo(): ComposeGroupRuntimeInfo {
            return ComposeGroupRuntimeInfo(
                parentComposeGroupKey = parentComposeGroupKey,
                callSiteMethodFqn = callSiteMethodFqn,
                invalidationKey = ComposeGroupInvalidationKey(crc.value.toInt())
            )
        }
    }

    fun pushGroup(key: ComposeGroupKey) {
        val tail = stack.lastOrNull()

        /* We're currently building this group, so we're just tracking this 'startGroup' call */
        if (tail?.composeGroupKey == key) {
            tail.pushGroup()
            return
        }

        stack += ComposeGroupRuntimeInfoBuilder(
            parentComposeGroupKey = tail?.composeGroupKey,
            composeGroupKey = key,
        )
    }

    fun popGroup(): ComposeGroupRuntimeInfoBuilder? {
        val tail = stack.lastOrNull() ?: return null
        /* We popped a startGroup call which is nested in the same group */
        if (tail.popGroup()) return null

        /* Now actually pop the group. Finished! */
        return stack.removeLastOrNull()
    }

    override fun visitLdcInsn(value: Any?) {
        if (value is Int) lastIntValue = value
        stack.lastOrNull()?.pushHashIfNecessary(value)
        super.visitLdcInsn(value)
    }

    override fun visitMethodInsn(
        opcode: Int, owner: String?, name: String?, descriptor: String?, isInterface: Boolean
    ) {

        stack.lastOrNull()?.apply {
            pushHashIfNecessary(owner)
            pushHashIfNecessary(name)
            pushHashIfNecessary(descriptor)
            pushHashIfNecessary(isInterface)
        }

        if (owner == composerClazzId) {
            if (name == startRestartGroupMethodName) {
                pushGroup(ComposeGroupKey(lastIntValue))
            }

            if (name == startReplaceGroupMethodName) {
                pushGroup(ComposeGroupKey(lastIntValue))
            }

            if (name == endReplaceGroupMethodName || name == endRestartGroupMethodName) {
                val popped = popGroup() ?: return
                groups[popped.composeGroupKey] = popped.createGroupRuntimeInfo()
            }
        }
    }

    override fun visitAnnotation(descriptor: String?, visible: Boolean): AnnotationVisitor? {
        if (descriptor != functionKeyMetaConstructorDescriptor) return null

        return object : AnnotationVisitor(ASM9) {
            override fun visit(name: String?, value: Any?) {
                if (name == "key" && value is Int) {
                    stack += ComposeGroupRuntimeInfoBuilder(
                        parentComposeGroupKey = stack.lastOrNull()?.composeGroupKey,
                        composeGroupKey = ComposeGroupKey(value)
                    )
                }
            }
        }
    }

    override fun visitInvokeDynamicInsn(
        name: String?,
        descriptor: String?,
        bootstrapMethodHandle: Handle?,
        vararg bootstrapMethodArguments: Any?
    ) {
        stack.lastOrNull()?.apply {
            pushHashIfNecessary(name)
            pushHashIfNecessary(descriptor)
            pushHashIfNecessary(bootstrapMethodHandle?.name)
            pushHashIfNecessary(bootstrapMethodHandle?.owner)
            pushHashIfNecessary(bootstrapMethodHandle?.tag)
            pushHashIfNecessary(bootstrapMethodHandle?.desc)
            bootstrapMethodArguments.forEach { pushHashIfNecessary(it) }
        }
    }

    override fun visitInsn(opcode: Int) {
        stack.lastOrNull()?.pushHashIfNecessary(opcode)
        when (opcode) {
            Opcodes.ICONST_0 -> lastIntValue = 0
            Opcodes.ICONST_1 -> lastIntValue = 1
            Opcodes.ICONST_2 -> lastIntValue = 2
            Opcodes.ICONST_3 -> lastIntValue = 3
            Opcodes.ICONST_4 -> lastIntValue = 4
            Opcodes.ICONST_5 -> lastIntValue = 5
            Opcodes.ICONST_M1 -> lastIntValue = -1
        }
    }

    override fun visitIntInsn(opcode: Int, operand: Int) {
        stack.lastOrNull()?.pushHashIfNecessary(opcode)
        stack.lastOrNull()?.pushHashIfNecessary(operand)
        lastIntValue = operand
    }

    override fun visitEnd() {
        if (stack.isNotEmpty()) {
            while (true) {
                val element = stack.removeLastOrNull() ?: break
                groups[element.composeGroupKey] = element.createGroupRuntimeInfo()
            }
        }
    }
}
