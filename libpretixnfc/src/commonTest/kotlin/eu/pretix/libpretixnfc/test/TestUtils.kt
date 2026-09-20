package eu.pretix.libpretixnfc.test

import eu.pretix.libpretixnfc.chunkPayload
import eu.pretix.libpretixnfc.decodeHex
import eu.pretix.libpretixnfc.toHexString
import kotlin.test.Test
import kotlin.test.assertEquals

class TestUtils {

    @Test
    fun testChunkPayloadOfFullPages() {
        val chunks = chunkPayload("0011223344556677".decodeHex())
        assertEquals(2, chunks.size)
        assertEquals("00112233", chunks[0].toHexString())
        assertEquals("44556677", chunks[1].toHexString())
    }

    @Test
    fun testChunkPayloadPadsLastPageWithZeroes() {
        val chunks = chunkPayload("0011223344".decodeHex())
        assertEquals(2, chunks.size)
        assertEquals("00112233", chunks[0].toHexString())
        assertEquals("44000000", chunks[1].toHexString())
    }

    @Test
    fun testToHexString() {
        assertEquals("00FF7F80", byteArrayOf(0x00, 0xFF.toByte(), 0x7F, 0x80.toByte()).toHexString())
        assertEquals("00 FF", byteArrayOf(0x00, 0xFF.toByte()).toHexString(true))
    }

    @Test
    fun testDecodeHex() {
        assertEquals(listOf<Byte>(0x00, 0x7F, 0x80.toByte(), 0xFF.toByte()), "007F80ff".decodeHex().toList())
    }
}
