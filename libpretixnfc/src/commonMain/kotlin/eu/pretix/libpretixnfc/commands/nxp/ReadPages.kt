package eu.pretix.libpretixnfc.commands.nxp

import eu.pretix.libpretixnfc.commands.Command
import eu.pretix.libpretixnfc.communication.NfcIOError

class ReadPages(val firstPageNum: Byte) : Command<ByteArray>() {
    /**
     * Reads exactly 4 *pages*.
     */
    constructor(firstPageNum: Int) : this(firstPageNum.and(0xFF).toByte())

    override fun toBytes(): ByteArray {
        return byteArrayOf(0x30, firstPageNum)
    }

    override fun parseResponse(data: ByteArray): ByteArray {
        if (data.size != 16) {
            throw NfcIOError("Bad length ${data.size} received for page")
        }
        return data
    }
}