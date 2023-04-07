package eu.pretix.libpretixnfc

import java.util.*
import kotlin.math.ceil


fun toBitSet(b: Int): BitSet {
    var b = b
    var n = 8
    val set = BitSet(n)
    while (n-- > 0) {
        val isSet = b and 0x80 != 0
        set[n] = isSet
        b = b shl 1
    }
    return set
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
        result[chunk] = Arrays.copyOfRange(source, start, start + chunkSize)
        start += chunkSize
    }
    return result
}

fun ByteArray.toHexString(spaces: Boolean = false) = joinToString(if (spaces) " " else "") {
    "%02x".format(it).uppercase()
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
