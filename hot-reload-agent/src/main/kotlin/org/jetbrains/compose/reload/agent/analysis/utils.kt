package org.jetbrains.compose.reload.agent.analysis

import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicReference
import java.util.zip.Checksum

data class Update<T>(val previous: T, val updated: T)

internal inline fun <T> AtomicReference<T>.update(updater: (T) -> T): Update<T> {
    while (true) {
        val value = get()
        val updated = updater(value)
        if (compareAndSet(value, updated)) {
            return Update(value, updated)
        }
    }
}

internal fun Checksum.updateInt(value: Int)  {
    val buffer = ByteBuffer.allocate(4).putInt(value).array()
    update(buffer)
}

internal fun Checksum.updateFloat(value: Float)  {
    val buffer = ByteBuffer.allocate(4).putFloat(value).array()
    update(buffer)
}

internal fun Checksum.updateLong(value: Long)  {
    val buffer = ByteBuffer.allocate(8).putLong(value).array()
    update(buffer)
}

internal fun Checksum.updateDouble(value: Double)  {
    val buffer = ByteBuffer.allocate(8).putDouble(value).array()
    update(buffer)
}