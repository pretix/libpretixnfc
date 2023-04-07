package eu.pretix.libpretixnfc.commands.nxp.mf0aes

import eu.pretix.libpretixnfc.commands.Command
import eu.pretix.libpretixnfc.communication.NfcIOError

class AuthenticatePart1(val keyId: Byte) : Command<ByteArray>() {
    override fun toBytes(): ByteArray {
        check(keyId < 0x03)
        return byteArrayOf(0x1A, keyId)
    }

    override fun parseResponse(data: ByteArray): ByteArray {
        if (data[0] != 0xAF.toByte()) {
            throw NfcIOError("Response to first AUTHENTICATE command unexpected")
        }
        if (data.size != 17) {
            throw NfcIOError("Response to first AUTHENTICATE command unexpected length ${data.size}")
        }

        return data.copyOfRange(1, data.size)
    }
}