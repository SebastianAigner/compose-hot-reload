@file:Suppress("DuplicatedCode")

package org.jetbrains.compose.reload.agent.analysis

import org.jetbrains.compose.reload.agent.analysis.ComposeGroupKey
import org.jetbrains.compose.reload.agent.closure
import org.jetbrains.compose.reload.agent.createLogger
import org.jetbrains.compose.reload.agent.withClosure
import org.objectweb.asm.ClassReader
import org.objectweb.asm.Opcodes.ASM9
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.MethodNode

private val logger = createLogger()

private const val functionKeyMetaConstructorDescriptor = "Landroidx/compose/runtime/internal/FunctionKeyMeta;"


internal data class RuntimeInfo(val scopes: List<RuntimeScopeInfo>) {
    /**
     * Index from [MethodId] to the 'root scopes' belonging to this method id
     */
    val methods: Map<MethodId, List<RuntimeScopeInfo>> = scopes.groupBy { info -> info.methodId }

    /**
     * Index from [ComposeGroupKey] to *all* scopes that are associated with this group.
     * (null as key means 'no known group')
     */
    val groups: Map<ComposeGroupKey?, List<RuntimeScopeInfo>> =
        scopes.withClosure<RuntimeScopeInfo> { info -> info.children }
            .groupBy { info -> info.group }

    companion object {
        val EMPTY = RuntimeInfo(emptyList())
    }
}

internal data class MethodId(
    val className: String,
    val methodName: String,
    val methodDescriptor: String,
) {
    override fun toString(): String {
        return "$className.$methodName $methodDescriptor"
    }
}

internal data class RuntimeScopeInfo(
    val methodId: MethodId,
    val type: RuntimeScopeType,
    val group: ComposeGroupKey?,
    val parentGroup: ComposeGroupKey?,
    val hash: RuntimeScopeHash,
    val children: List<RuntimeScopeInfo>,
    val dependencies: List<MethodId>
)

internal enum class RuntimeScopeType {
    Method, RestartGroup, ReplaceGroup, SourceInformationMarker,
}

internal fun RuntimeInfo(bytecode: ByteArray): RuntimeInfo? {
    val reader = ClassReader(bytecode)
    val node = ClassNode(ASM9)
    reader.accept(node, 0)

    return RuntimeInfo(node)
}

internal fun RuntimeInfo(classNode: ClassNode): RuntimeInfo? {
    if (classNode.name.startsWith("java/") ||
        classNode.name.startsWith("javax/") ||
        classNode.name.startsWith("jdk/") ||
        classNode.name.startsWith("sun/") ||
        classNode.name.startsWith("kotlin/") ||
        classNode.name.startsWith("kotlinx/") ||
        classNode.name.startsWith("androidx/")
    ) return null
    return RuntimeInfo(classNode.methods.mapNotNull { methodNode -> RuntimeScopeInfo(classNode, methodNode) })
}

internal fun RuntimeScopeInfo(classNode: ClassNode, methodNode: MethodNode): RuntimeScopeInfo? {
    val context = RuntimeMethodAnalysisContext(classNode, methodNode)

    val functionKey = methodNode.visibleAnnotations.orEmpty().find { annotationNode ->
        annotationNode.desc == functionKeyMetaConstructorDescriptor
    }?.values?.zipWithNext()?.find { (name, _) -> name == "key" }?.second as? Int

    if (functionKey != null) {
        context.pushFunctionKeyAnnotation(ComposeGroupKey(functionKey))
    }

    methodNode.instructions.forEach { instructionNode ->
        RuntimeInstructionAnalyzer.analyze(context, instructionNode)
    }

    return RuntimeScopeInfo(context.end() ?: return null)
}

/**
 * Creates a new [RuntimeInfo] by updating [this] RuntimeInfo with 'new' scopes from [other]
 */
internal operator fun RuntimeInfo?.plus(other: RuntimeInfo?): RuntimeInfo {
    if (this == null) return other ?: RuntimeInfo(emptyList())
    if (other == null) return this
    val newMethods = this.methods + other.methods
    val newScopes = newMethods.values.flatten()
    return RuntimeInfo(newScopes)
}