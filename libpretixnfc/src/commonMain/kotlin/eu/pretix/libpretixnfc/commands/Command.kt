package eu.pretix.libpretixnfc.commands

import eu.pretix.libpretixnfc.communication.AbstractNfcA
import eu.pretix.libpretixnfc.communication.NfcIOError
import eu.pretix.libpretixnfc.toHexString
import kotlin.experimental.and

abstract class Command<T> {
    abstract fun toBytes(): ByteArray
    abstract fun parseResponse(data: ByteArray): T

    fun execute(nfca: AbstractNfcA): T {
        val response = nfca.transceive(toBytes()) ?: throw NfcIOError("No response received")
        if (response.size == 1 && response[0].and(0xA) != 0xA.toByte()) {
            if (response[0].and(0xF) == 0x0.toByte()) {
                throw NfcIOError("NAK for invalid argument (i.e. invalid page address)")
            }
            if (response[0].and(0xF) == 0x1.toByte()) {
                throw NfcIOError("NAK for parity or CRC error")
            }
            if (response[0].and(0xF) == 0x4.toByte()) {
                throw NfcIOError("NAK for invalid authentication counter overflow")
            }
            if (response[0].and(0xF) == 0x5.toByte()) {
                throw NfcIOError("NAK for EEPROM write error")
            }
        }
        return parseResponse(response)
    }
}