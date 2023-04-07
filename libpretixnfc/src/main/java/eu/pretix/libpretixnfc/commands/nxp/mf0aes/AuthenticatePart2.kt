package eu.pretix.libpretixnfc.commands.nxp.mf0aes

import eu.pretix.libpretixnfc.commands.Command
import eu.pretix.libpretixnfc.communication.NfcChipReadError
import eu.pretix.libpretixnfc.communication.NfcIOError

class AuthenticatePart2(val ekRndARndB: ByteArray) : Command<ByteArray>() {
    override fun toBytes(): ByteArray {
        check(ekRndARndB.size == 32)
        return byteArrayOf(0xAF.toByte()) + ekRndARndB
    }

    override fun parseResponse(data: ByteArray): ByteArray {
        if (data[0] != 0x00.toByte()) {
            throw NfcIOError("Response to first AUTHENTICATE command unexpected")
        }
        if (data.size != 17) {
            throw NfcIOError("Response to first AUTHENTICATE command unexpected length ${data.size}")
        }

        return data.copyOfRange(1, data.size)
    }
}