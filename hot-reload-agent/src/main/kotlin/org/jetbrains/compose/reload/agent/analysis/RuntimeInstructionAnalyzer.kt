package org.jetbrains.compose.reload.agent.analysis

import org.objectweb.asm.tree.AbstractInsnNode

internal fun interface RuntimeInstructionAnalyzer {
    fun analyze(
        context: RuntimeMethodAnalysisContext,
        instructionNode: AbstractInsnNode
    )

    companion object : RuntimeInstructionAnalyzer by CompositeRuntimeInstructionAnalyzer(
        RuntimeScopeHasher,
        RuntimeScopeMethodDependencyAnalyzer,
        RuntimeScopeComposeGroupAnalyzer
    )
}

private class CompositeRuntimeInstructionAnalyzer(
    val children: List<RuntimeInstructionAnalyzer>
) : RuntimeInstructionAnalyzer {

    constructor(vararg children: RuntimeInstructionAnalyzer) : this(children.toList())

    override fun analyze(
        context: RuntimeMethodAnalysisContext,
        instructionNode: AbstractInsnNode
    ) {
        children.forEach { it.analyze(context, instructionNode) }
    }
}