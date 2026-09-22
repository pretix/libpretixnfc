package eu.pretix.libpretixnfc.desktop.test.hardware

import eu.pretix.libpretixnfc.communication.NfcIOError
import eu.pretix.libpretixnfc.decodeHex
import eu.pretix.libpretixnfc.desktop.hardware.PcscNfcA
import eu.pretix.libpretixnfc.toHexString
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import javax.smartcardio.Card
import javax.smartcardio.CardChannel
import javax.smartcardio.CardException
import javax.smartcardio.CommandAPDU
import javax.smartcardio.ResponseAPDU

class PcscNfcATest {
    private class FakeCardChannel(
        private val response: ByteArray = "9000".decodeHex(),
        private val failure: CardException? = null,
    ) : CardChannel() {
        val commands = mutableListOf<ByteArray>()

        override fun getCard(): Card {
            throw UnsupportedOperationException()
        }

        override fun getChannelNumber(): Int = 0

        override fun transmit(apdu: CommandAPDU): ResponseAPDU {
            commands.add(apdu.bytes)
            failure?.let { throw it }
            return ResponseAPDU(response)
        }

        override fun transmit(command: ByteBuffer, response: ByteBuffer): Int {
            throw UnsupportedOperationException()
        }

        override fun close() {
        }
    }

    @Test
    fun transceiveWrapsPayloadInPseudoApduAndStripsStatusWord() {
        val channel = FakeCardChannel("0004030104000F039000".decodeHex())

        val result = PcscNfcA(channel).transceive("60".decodeHex())

        assertEquals("0004030104000F03", result.toHexString())
        assertEquals("FF0000000160", channel.commands.single().toHexString())
    }

    @Test
    fun transceiveThrowsOnUnexpectedStatusWord() {
        val channel = FakeCardChannel("6300".decodeHex())

        val error = assertThrows(NfcIOError::class.java) {
            PcscNfcA(channel).transceive("60".decodeHex())
        }

        assertTrue(error.message!!.contains("6300"))
    }

    @Test
    fun transceiveThrowsOnReaderFailure() {
        val channel = FakeCardChannel(failure = CardException("card removed"))

        assertThrows(NfcIOError::class.java) {
            PcscNfcA(channel).transceive("60".decodeHex())
        }
    }

    @Test(expected = IllegalStateException::class)
    fun transceiveRejectsOversizedPayload() {
        PcscNfcA(FakeCardChannel()).transceive(ByteArray(256))
    }

    @Test
    fun readUidReturnsUppercaseHex() {
        val channel = FakeCardChannel("04a1b2c3d4e5f69000".decodeHex())

        val uid = PcscNfcA(channel).readUid()

        assertEquals("04A1B2C3D4E5F6", uid)
        assertEquals("FFCA000000", channel.commands.single().toHexString())
    }

    @Test(expected = IllegalStateException::class)
    fun readUidRejectsEmptyResponse() {
        PcscNfcA(FakeCardChannel()).readUid()
    }

    @Test
    fun connectAndCloseLeaveTheCardAlone() {
        val channel = FakeCardChannel()
        val nfca = PcscNfcA(channel)

        nfca.connect()
        nfca.close()

        assertTrue(channel.commands.isEmpty())
    }

    @Test
    fun transceiveAcceptsMaximumPayload() {
        val channel = FakeCardChannel()

        PcscNfcA(channel).transceive(ByteArray(255))

        assertArrayEquals(
            byteArrayOf(0xFF.toByte(), 0x00, 0x00, 0x00, 0xFF.toByte()) + ByteArray(255),
            channel.commands.single()
        )
    }
}
