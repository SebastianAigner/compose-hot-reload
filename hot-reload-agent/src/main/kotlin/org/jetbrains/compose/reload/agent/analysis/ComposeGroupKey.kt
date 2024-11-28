package org.jetbrains.compose.reload.agent.analysis

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