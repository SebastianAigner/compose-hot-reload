package org.jetbrains.compose.reload.agent.analysis

import org.objectweb.asm.Handle
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.InvokeDynamicInsnNode
import org.objectweb.asm.tree.MethodInsnNode

private const val lambdaMetaFactoryClassId = "java/lang/invoke/LambdaMetafactory"
private const val metafactoryMethodName = "metafactory"

internal object RuntimeScopeMethodDependencyAnalyzer : RuntimeInstructionAnalyzer {
    override fun analyze(
        context: RuntimeMethodAnalysisContext, instructionNode: AbstractInsnNode
    ) {
        val scope = context.scope ?: return

        if (instructionNode is MethodInsnNode && instructionNode.opcode == Opcodes.INVOKESTATIC) {
            if(instructionNode.owner.startsWith("androidx/compose/")) return
            if(instructionNode.owner.startsWith("android/")) return
            if(instructionNode.owner.startsWith("java/")) return
            if(instructionNode.owner.startsWith("kotlin/")) return

            scope.attachDependency(
                MethodId(
                    className = instructionNode.owner,
                    methodName = instructionNode.name,
                    methodDescriptor = instructionNode.desc
                )
            )
        }

        if (instructionNode is InvokeDynamicInsnNode &&
            instructionNode.bsm.owner == lambdaMetaFactoryClassId &&
            instructionNode.bsm.name == metafactoryMethodName
        ) {
            val handle = instructionNode.bsmArgs.getOrNull(1) as? Handle ?: return
            scope.attachDependency(
                MethodId(
                    className = handle.owner,
                    methodName = handle.name,
                    methodDescriptor = handle.desc
                )
            )
        }
    }
}