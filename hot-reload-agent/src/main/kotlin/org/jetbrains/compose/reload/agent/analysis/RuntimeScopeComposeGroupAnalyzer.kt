package org.jetbrains.compose.reload.agent.analysis

import org.jetbrains.compose.reload.agent.analysis.ComposeGroupKey
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.MethodInsnNode

private const val composerClazzId = "androidx/compose/runtime/Composer"
private const val startReplaceGroupMethodName = "startReplaceGroup"
private const val startRestartGroupMethodName = "startRestartGroup"
private const val sourceInformationMarkerStartMethodName = "sourceInformationMarkerStart"

private const val endReplaceGroupMethodName = "endReplaceGroup"
private const val endRestartGroupMethodName = "endRestartGroup"
private const val sourceInformationMarkerEndMethodName = "sourceInformationMarkerEnd"

internal object RuntimeScopeComposeGroupAnalyzer : RuntimeInstructionAnalyzer {
    override fun analyze(
        context: RuntimeMethodAnalysisContext,
        instructionIndex: Int, instructionNode: AbstractInsnNode
    ) {
        if (instructionNode !is MethodInsnNode) return
        if (instructionNode.owner != composerClazzId) return


        when (instructionNode.name) {
            startReplaceGroupMethodName -> context.pushStartReplaceGroup(
                ComposeGroupKey(instructionNode.previous.intValueOrNull() ?: return)
            )

            startRestartGroupMethodName -> context.pushStartRestartGroup(
                ComposeGroupKey(instructionNode.previous.intValueOrNull() ?: return)
            )

            sourceInformationMarkerStartMethodName -> context.pushSourceInformationMarkerStart(
                ComposeGroupKey(instructionNode.previous.intValueOrNull() ?: return)
            )

            endReplaceGroupMethodName -> context.popEndReplaceGroup()
            endRestartGroupMethodName -> context.popEndRestartGroup()
            sourceInformationMarkerEndMethodName -> context.popSourceInformationMarkerEnd()
            else -> Unit
        }
    }
}