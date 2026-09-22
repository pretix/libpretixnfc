package eu.pretix.libpretixnfc.commands.nxp

import eu.pretix.libpretixnfc.commands.Command

class WritePage(val pageNum: Byte, val data: ByteArray) : Command<ByteArray>() {
    constructor(pageNum: Int, data: ByteArray) : this(pageNum.and(0xFF).toByte(), data)

    override fun toBytes(): ByteArray {
        return byteArrayOf(0xA2.toByte(), pageNum, data[0], data[1], data[2], data[3])
    }

    override fun parseResponse(data: ByteArray): ByteArray {
        return data
    }
}