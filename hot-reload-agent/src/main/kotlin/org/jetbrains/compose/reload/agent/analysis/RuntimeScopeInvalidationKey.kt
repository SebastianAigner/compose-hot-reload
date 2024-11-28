package org.jetbrains.compose.reload.agent.analysis

import org.jetbrains.compose.reload.agent.withClosure
import java.util.zip.CRC32

@JvmInline
value class RuntimeScopeInvalidationKey(val value: Long)

internal fun RuntimeInfo.resolveInvalidationKey(groupKey: ComposeGroupKey): RuntimeScopeInvalidationKey? {
    val crc = CRC32()
    val scopes = groups[groupKey] ?: return null

    val scopesWithTransitiveDependencies = scopes.withClosure<RuntimeScopeInfo> { scope ->
        scope.dependencies.flatMap { methodId -> methods[methodId] ?: emptyList() }
    }

    scopesWithTransitiveDependencies.forEach { scope ->
        crc.update(scope.hash.value.toInt())
    }

    return RuntimeScopeInvalidationKey(crc.value)
}
