package org.jetbrains.compose.reload.agent.analysis

import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.InsnNode
import org.objectweb.asm.tree.LdcInsnNode

fun AbstractInsnNode.intValueOrNull(): Int? {
    if (this is LdcInsnNode) return this.cst as? Int
    return when (opcode) {
        Opcodes.ICONST_0 -> 0
        Opcodes.ICONST_1 -> 1
        Opcodes.ICONST_2 -> 2
        Opcodes.ICONST_3 -> 3
        Opcodes.ICONST_4 -> 4
        Opcodes.ICONST_5 -> 5
        Opcodes.ICONST_M1 -> -1
        else -> null
    }
}
