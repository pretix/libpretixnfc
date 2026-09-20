package eu.pretix.libpretixnfc.test.cryptography

import eu.pretix.libpretixnfc.commands.nxp.mf0aes.AuthenticationHelper
import eu.pretix.libpretixnfc.communication.AbstractNfcA
import eu.pretix.libpretixnfc.cryptography.An10922KeyDiversification
import eu.pretix.libpretixnfc.decodeHex
import eu.pretix.libpretixnfc.rotateLeft
import eu.pretix.libpretixnfc.toHexString
import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.charset.Charset
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class TestAn10922KeyDiversification() {

    @Test
    fun testExampleFromSpec() {
        // example from https://www.nxp.com/docs/en/application-note/AN10922.pdf
        val k = An10922KeyDiversification().generateDiversifiedKeyAES128(
            "00112233445566778899AABBCCDDEEFF".decodeHex(),
            "04782E21801D80".decodeHex(),
            "3042F5".decodeHex(),
            "4E585020416275".decodeHex()
        )
        assertEquals("A8DD63A3B89D54B37CA802473FDA9175", k.toHexString())
    }
}