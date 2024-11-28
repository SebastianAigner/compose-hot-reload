package org.jetbrains.compose.reload.agent.analysis

import org.objectweb.asm.tree.*

internal object RuntimeScopeHasher : RuntimeInstructionAnalyzer {
    override fun analyze(
        context: RuntimeMethodAnalysisContext, instructionNode: AbstractInsnNode
    ) {
        val scope = context.scope ?: return

        if (instructionNode.opcode > 0) {
            scope.pushHash(instructionNode.opcode)
        }

        when (instructionNode) {
            is MethodInsnNode -> {
                scope.pushHash(instructionNode.owner)
                scope.pushHash(instructionNode.name)
                scope.pushHash(instructionNode.desc)
                scope.pushHash(instructionNode.itf)
            }

            is LdcInsnNode -> {
                scope.pushHash(instructionNode.cst)
            }

            is InvokeDynamicInsnNode -> {
                scope.pushHash(instructionNode.name)
                scope.pushHash(instructionNode.desc)
                scope.pushHash(instructionNode.bsm?.name)
                scope.pushHash(instructionNode.bsm?.owner)
                scope.pushHash(instructionNode.bsm?.tag)
                scope.pushHash(instructionNode.bsm?.desc)
                instructionNode.bsmArgs.forEach { scope.pushHash(it) }
            }

            is FieldInsnNode -> {
                scope.pushHash(instructionNode.owner)
                scope.pushHash(instructionNode.name)
                scope.pushHash(instructionNode.desc)
            }

            is IntInsnNode -> {
                scope.pushHash(instructionNode.operand)
            }

            is VarInsnNode -> {
                scope.pushHash(instructionNode.`var`)
            }
        }
    }
}