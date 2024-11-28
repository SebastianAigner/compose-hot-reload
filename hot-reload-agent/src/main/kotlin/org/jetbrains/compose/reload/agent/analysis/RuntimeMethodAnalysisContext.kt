package org.jetbrains.compose.reload.agent.analysis

import org.jetbrains.compose.reload.agent.analysis.ComposeGroupKey
import org.jetbrains.compose.reload.agent.createLogger
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.MethodNode

private val logger = createLogger()

internal class RuntimeMethodAnalysisContext(
    val classNode: ClassNode, val methodNode: MethodNode
) {

    private val scopes = ArrayDeque<RuntimeScopeAnalysisContext>(
        listOf(RuntimeScopeAnalysisContext(classNode, methodNode, RuntimeScopeType.Method, null))
    )

    val scope: RuntimeScopeAnalysisContext? get() = scopes.lastOrNull()

    fun pushFunctionKeyAnnotation(key: ComposeGroupKey) {
        scopes.clear()
        scopes.add(RuntimeScopeAnalysisContext(classNode, methodNode, RuntimeScopeType.Method, key))
    }

    fun pushSourceInformationMarkerStart(key: ComposeGroupKey) {
        scopes.add(RuntimeScopeAnalysisContext(classNode, methodNode, RuntimeScopeType.SourceInformationMarker, key))
    }

    fun pushStartRestartGroup(key: ComposeGroupKey) {
        scopes.add(RuntimeScopeAnalysisContext(classNode, methodNode, RuntimeScopeType.RestartGroup, key))
    }

    fun pushStartReplaceGroup(key: ComposeGroupKey) {
        scopes.add(RuntimeScopeAnalysisContext(classNode, methodNode, RuntimeScopeType.ReplaceGroup, key))
    }

    fun popEndRestartGroup() {
        val tail = scopes.removeLastOrNull() ?: return
        if (tail.type != RuntimeScopeType.RestartGroup) {
            logger.warn("'popEndRestartGroup' expected ${RuntimeScopeType.RestartGroup}, but was ${tail.type} at ${tail.group}")
        }
        scopes.lastOrNull()?.attachChild(tail)
    }

    fun popEndReplaceGroup() {
        val tail = scopes.removeLastOrNull() ?: return
        if (tail.type != RuntimeScopeType.ReplaceGroup) {
            logger.warn("'popEndReplaceGroup' expected ${RuntimeScopeType.ReplaceGroup}, but was ${tail.type} at ${tail.group}")
        }
        scopes.lastOrNull()?.attachChild(tail)
    }

    fun popSourceInformationMarkerEnd() {
        val tail = scopes.removeLastOrNull() ?: return
        if (tail.type != RuntimeScopeType.SourceInformationMarker) {
            logger.warn("'popSourceInformationMarkerEnd' expected ${RuntimeScopeType.SourceInformationMarker}, but was ${tail.type} at ${tail.group}")
        }
        scopes.lastOrNull()?.attachChild(tail)
    }

    fun end(): RuntimeScopeAnalysisContext? {
        if (scopes.isEmpty()) return null

        while (scopes.size > 1) {
            val tail = scopes.removeLast()
            scopes.lastOrNull()?.attachChild(tail)
        }

        return scopes.first()
    }
}