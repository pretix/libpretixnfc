@file:JvmName("UtilsKt")
@file:JvmMultifileClass

package eu.pretix.libpretixnfc

import kotlin.jvm.JvmMultifileClass
import kotlin.jvm.JvmName
import kotlin.math.ceil


private const val HEX_DIGITS = "0123456789ABCDEF"

/**
 * Copies `fromIndex` until `toIndex`, filling up with zero bytes if `toIndex` is beyond the end
 * of the array.
 */
private fun ByteArray.copyOfRangePadded(fromIndex: Int, toIndex: Int): ByteArray {
    val copy = ByteArray(toIndex - fromIndex)
    copyInto(copy, 0, fromIndex, minOf(toIndex, size))
    return copy
}

fun chunkPayload(source: ByteArray): Array<ByteArray> {
    val chunkSize = 4
    val result = Array(ceil(source.size / chunkSize.toDouble()).toInt()) {
        ByteArray(
            chunkSize
        )
    }
    var start = 0
    for (chunk in result.indices) {
        result[chunk] = source.copyOfRangePadded(start, start + chunkSize)
        start += chunkSize
    }
    return result
}

fun ByteArray.toHexString(spaces: Boolean = false) = joinToString(if (spaces) " " else "") {
    val value = it.toInt() and 0xFF
    "${HEX_DIGITS[value shr 4]}${HEX_DIGITS[value and 0x0F]}"
}

fun String.decodeHex(): ByteArray {
    check(length % 2 == 0) { "Must have an even length" }

    return chunked(2)
        .map { it.toInt(16).toByte() }
        .toByteArray()
}

fun rotateLeft(byteArray: ByteArray, l: Int): ByteArray {
    return byteArray.copyOfRange(l, byteArray.size) + byteArray.copyOfRange(0, l)
}
