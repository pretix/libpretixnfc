package eu.pretix.libpretixnfc.commands.nxp.mf0aes

import eu.pretix.libpretixnfc.commands.nxp.ReadPages
import eu.pretix.libpretixnfc.communication.AbstractNfcA
import eu.pretix.libpretixnfc.communication.NfcIOError
import kotlin.experimental.xor

class UidHelper(
    val nfca: AbstractNfcA,
) {
    fun readUid(): ByteArray {
        val data = ReadPages(0x00).execute(nfca)
        val uid = byteArrayOf(data[0], data[1], data[2], data[4], data[5], data[6], data[7])

        val checkByte0 = 0x88.toByte() xor data[0] xor data[1] xor data[2]
        val checkByte1 = data[4] xor data[5] xor data[6] xor data[7]

        if (checkByte0 != data[3] || checkByte1 != data[8]) {
            throw NfcIOError("UID check byte mismatch")
        }
        return uid
    }
}