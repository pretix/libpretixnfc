package eu.pretix.libpretixnfc.desktop.hardware

import eu.pretix.libpretixnfc.communication.AbstractNfcA
import eu.pretix.libpretixnfc.communication.NfcIOError
import eu.pretix.libpretixnfc.toHexString
import javax.smartcardio.CardChannel
import javax.smartcardio.CardException
import javax.smartcardio.CommandAPDU

/**
 * Talks to an NFC-A tag through a PC/SC reader by wrapping every tag command in the
 * `FF 00 00 00 Lc` direct-transmit pseudo-APDU understood by ACS readers.
 *
 */
class PcscNfcA(private val channel: CardChannel) : AbstractNfcA {
    override fun connect() {
    }

    override fun transceive(data: ByteArray): ByteArray {
        check(data.size <= MAX_PAYLOAD_SIZE) { "Payload of ${data.size} bytes is too long" }
        return transmit(byteArrayOf(0xFF.toByte(), 0x00, 0x00, 0x00, data.size.toByte()) + data)
    }

    override fun close() {
    }

    /**
     * Reads the UID the reader cached during anticollision, as uppercase hex without separators.
     */
    fun readUid(): String {
        val uid = transmit(GET_UID)
        check(uid.isNotEmpty()) { "Reader returned an empty UID" }
        return uid.toHexString(false)
    }

    private fun transmit(command: ByteArray): ByteArray {
        val response = try {
            channel.transmit(CommandAPDU(command))
        } catch (e: CardException) {
            throw NfcIOError("Reader communication failed: ${e.message}")
        }
        if (response.sw != STATUS_SUCCESS) {
            throw NfcIOError("Reader rejected command, SW=%04X".format(response.sw))
        }
        return response.data
    }

    private companion object {
        const val MAX_PAYLOAD_SIZE = 255
        const val STATUS_SUCCESS = 0x9000
        val GET_UID = byteArrayOf(0xFF.toByte(), 0xCA.toByte(), 0x00, 0x00, 0x00)
    }
}
