package eu.pretix.libpretixnfc.desktop.test.highlevel

import Mf0aesKeySet
import PretixMf0aes
import eu.pretix.libpretixnfc.communication.AbstractNfcA
import eu.pretix.libpretixnfc.communication.ChipReadError
import eu.pretix.libpretixnfc.communication.NfcChipReadError
import eu.pretix.libpretixnfc.decodeHex
import eu.pretix.libpretixnfc.toHexString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PretixMf0aesProcessTest {
    private class ScriptedNfcA(private val responses: Map<String, String>) : AbstractNfcA {
        var closed = false

        override fun connect() {
        }

        override fun transceive(data: ByteArray): ByteArray? = responses[data.toHexString()]?.decodeHex()

        override fun close() {
            closed = true
        }
    }

    private fun keySet(publicId: Long) = Mf0aesKeySet(
        publicId = publicId,
        canEncode = false,
        uidKey = ByteArray(16),
        diversificationKey = ByteArray(16),
    )

    @Test
    fun aNtagReportsAnUnknownChipType() {
        val nfca = ScriptedNfcA(mapOf(GET_VERSION to NTAG213_VERSION))

        val error = assertThrows(NfcChipReadError::class.java) {
            PretixMf0aes(listOf(keySet(0x2B)), false, false).process(nfca)
        }

        assertEquals(ChipReadError.UNKNOWN_CHIP_TYPE, error.errorType)
        assertTrue(nfca.closed)
    }

    @Test
    fun anUnwrittenChipReportsAnEmptyChip() {
        val nfca = ScriptedNfcA(
            mapOf(
                GET_VERSION to ULTRALIGHT_AES_VERSION,
                READ_PAGE_4 to "00000000000000000000000000000000",
            )
        )

        val error = assertThrows(NfcChipReadError::class.java) {
            PretixMf0aes(listOf(keySet(0x2B)), false, false).process(nfca)
        }

        assertEquals(ChipReadError.EMPTY_CHIP, error.errorType)
        assertTrue(nfca.closed)
    }

    @Test
    fun anUnknownPublicIdReportsAForeignChip() {
        val nfca = ScriptedNfcA(
            mapOf(
                GET_VERSION to ULTRALIGHT_AES_VERSION,
                READ_PAGE_4 to "0000002A000000000000000000000000",
            )
        )

        val error = assertThrows(NfcChipReadError::class.java) {
            PretixMf0aes(listOf(keySet(0x2B)), false, false).process(nfca)
        }

        assertEquals(ChipReadError.FOREIGN_CHIP, error.errorType)
        assertTrue(nfca.closed)
    }

    private companion object {
        const val GET_VERSION = "60"
        const val READ_PAGE_4 = "3004"
        const val NTAG213_VERSION = "0004040201000F03"
        const val ULTRALIGHT_AES_VERSION = "0004030104000F03"
    }
}
