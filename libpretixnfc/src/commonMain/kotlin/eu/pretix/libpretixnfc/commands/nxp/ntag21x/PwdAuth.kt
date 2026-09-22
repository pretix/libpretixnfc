package eu.pretix.libpretixnfc.commands.nxp.ntag21x

import eu.pretix.libpretixnfc.commands.Command

class PwdAuth(val password: ByteArray) : Command<ByteArray>() {
    override fun toBytes(): ByteArray {
        return byteArrayOf(0x1B.toByte(), password[0], password[1], password[2], password[3])
    }

    override fun parseResponse(data: ByteArray): ByteArray {
        return data
    }
}