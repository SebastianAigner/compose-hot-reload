package org.jetbrains.compose.reload.agent.analysis

import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.MethodNode
import java.util.zip.CRC32

internal class RuntimeScopeAnalysisContext(
    val classNode: ClassNode, val methodNode: MethodNode,
    val type: RuntimeScopeType, val group: ComposeGroupKey?
) {


    private val crc = CRC32()
    private val children = mutableListOf<RuntimeScopeAnalysisContext>()
    private val dependencies = mutableListOf<MethodId>()

    fun pushHash(value: Any?) {
        if (value == null) return
        when (value) {
            is Boolean -> crc.update(if (value) 1 else 0)
            is String -> crc.update(value.toByteArray())
            is Byte -> crc.update(value.toInt())
            is Int -> crc.updateInt(value)
            is Long -> crc.updateLong(value)
            is Float -> crc.updateFloat(value)
            is Double -> crc.updateDouble(value)
            else -> crc.update(value.toString().toByteArray())
        }
    }

    fun attachChild(scope: RuntimeScopeAnalysisContext) {
        children.add(scope)
    }

    fun attachDependency(methodId: MethodId) {
        dependencies.add(methodId)
    }

    fun hash(): RuntimeScopeHash  {
        return RuntimeScopeHash(crc.value)
    }

    fun children(): List<RuntimeScopeAnalysisContext> = children.toList()
    fun dependencies(): List<MethodId> = dependencies.toList()
}

internal fun RuntimeScopeInfo(
    context: RuntimeScopeAnalysisContext,
    parentGroup: ComposeGroupKey? = null
): RuntimeScopeInfo = RuntimeScopeInfo(
    methodId = MethodId(
        className = context.classNode.name,
        methodName = context.methodNode.name,
        methodDescriptor = context.methodNode.desc,
    ),
    type = context.type,
    group = context.group,
    parentGroup = parentGroup,
    hash = context.hash(),
    children = context.children().map { child -> RuntimeScopeInfo(child, parentGroup = context.group) },
    dependencies = context.dependencies()
)
