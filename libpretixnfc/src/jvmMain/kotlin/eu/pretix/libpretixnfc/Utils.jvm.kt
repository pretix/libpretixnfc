@file:JvmName("UtilsKt")
@file:JvmMultifileClass

package eu.pretix.libpretixnfc

import java.util.*


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
