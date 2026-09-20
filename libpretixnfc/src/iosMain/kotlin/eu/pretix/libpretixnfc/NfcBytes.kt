package eu.pretix.libpretixnfc

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.create
import platform.posix.memcpy

/**
 * Copies this byte array into an [NSData]. Swift callers use this instead of reading a
 * `KotlinByteArray` element by element.
 */
@OptIn(BetaInteropApi::class, ExperimentalForeignApi::class)
fun ByteArray.toNSData(): NSData {
    if (isEmpty()) {
        return NSData()
    }
    return usePinned { NSData.create(bytes = it.addressOf(0), length = size.convert()) }
}

/**
 * Copies this [NSData] into a byte array the library accepts.
 */
@OptIn(ExperimentalForeignApi::class)
fun NSData.toByteArray(): ByteArray {
    val size = length.toInt()
    if (size == 0) {
        return ByteArray(0)
    }
    val bytes = ByteArray(size)
    bytes.usePinned { memcpy(it.addressOf(0), this.bytes, length) }
    return bytes
}
