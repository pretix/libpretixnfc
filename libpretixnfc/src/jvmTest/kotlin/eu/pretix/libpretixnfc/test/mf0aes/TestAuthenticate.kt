package eu.pretix.libpretixnfc.test.mf0aes

import eu.pretix.libpretixnfc.commands.nxp.mf0aes.AuthenticationHelper
import eu.pretix.libpretixnfc.commands.nxp.mf0aes.aesCmac
import eu.pretix.libpretixnfc.commands.nxp.mf0aes.truncateMac
import eu.pretix.libpretixnfc.communication.AbstractNfcA
import eu.pretix.libpretixnfc.decodeHex
import eu.pretix.libpretixnfc.rotateLeft
import eu.pretix.libpretixnfc.toHexString
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.charset.Charset
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class TestAuthenticate() {

    @Test
    fun testRotateLeft() {
        assertEquals("12345670", rotateLeft("01234567".toByteArray(Charset.defaultCharset()), 1).decodeToString())
    }

    @Test
    fun testAesAuthFromDataSheet() {
        // example from https://www.nxp.com/docs/en/data-sheet/MF0AES(H)20.pdf
        val fakeNfca = object : AbstractNfcA {
            override fun connect() {}

            override fun transceive(data: ByteArray): ByteArray? {
                if (data[0] == 0x1A.toByte()) {
                    return "AFD5A847B84862FF3874A7F07B8DDF351B".decodeHex()
                }
                if (data[0] == 0xAF.toByte()) {
                    assertEquals("AFCDF22C5F7A92F0AF0155612B9B236AC7A424BC5238D41AD041B8165B7D99E524", data.toHexString())
                    return "002C743D6B1E128F8076BD197B76012CE8".decodeHex()
                }
                if (data[0] == 0x30.toByte() && data[1] == 0x29.toByte()) {
                    // Read config byte with enabled CMAC
                    return "02000000000000000000000000000000".decodeHex()
                }
                return null
            }

            override fun close() {}
        }

        val secretKey = SecretKeySpec(ByteArray(16), 0, 16, "AES")
        val iv = IvParameterSpec(ByteArray(16))
        val aesEncrypt = { plaintext: ByteArray ->
            val cipher: Cipher = Cipher.getInstance("AES/CBC/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, iv)
            cipher.doFinal(plaintext)
        }
        val aesDecrypt = { ciphertext: ByteArray ->
            val cipher: Cipher = Cipher.getInstance("AES/CBC/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey, iv)
            cipher.doFinal(ciphertext)
        }
        val aesCmac = { plaintext: ByteArray ->
            val mac = Mac.getInstance("AESCMAC", BouncyCastleProvider())
            mac.init(secretKey)
            mac.doFinal(plaintext)
        }
        val rndGenerator = { length: Int ->
            "F29B0123F5C00DF612487BBF42468C7E".decodeHex()
        }
        AuthenticationHelper(
            AuthenticationHelper.KeyId.DATA_PROT_KEY,
            fakeNfca,
            rndGenerator,
            aesEncrypt,
            aesDecrypt,
            aesCmac,
        ).authenticate()
    }

    @Test
    fun testAesAuthFromAN13452() {
        // example from https://www.nxp.com/docs/en/application-note/AN13452.pdf

        val fakeNfca = object : AbstractNfcA {
            override fun connect() {}

            override fun transceive(data: ByteArray): ByteArray? {
                if (data[0] == 0x1A.toByte()) {
                    return "AF374142DAFB0AB97183D846EB7ED379E0".decodeHex()
                }
                if (data[0] == 0xAF.toByte()) {
                    assertEquals("AF794693B2A31E7F10964A2BD834590AC485F84E9F8B13197AB32433346F60B821", data.toHexString())
                    return "00A17673DCC20D27EE80584ACCFC39E0A3".decodeHex()
                }
                if (data[0] == 0x30.toByte() && data[1] == 0x29.toByte()) {
                    // Read config byte with enabled CMAC
                    return "02000000000000000000000000000000".decodeHex()
                }
                return null
            }

            override fun close() {}
        }

        val secretKey = SecretKeySpec(ByteArray(16), 0, 16, "AES")
        val iv = IvParameterSpec(ByteArray(16))
        val aesEncrypt = { plaintext: ByteArray ->
            val cipher: Cipher = Cipher.getInstance("AES/CBC/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, iv)
            cipher.doFinal(plaintext)
        }
        val aesDecrypt = { ciphertext: ByteArray ->
            val cipher: Cipher = Cipher.getInstance("AES/CBC/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey, iv)
            cipher.doFinal(ciphertext)
        }
        val aesCmac = { plaintext: ByteArray ->
            val mac = Mac.getInstance("AESCMAC", BouncyCastleProvider())
            mac.init(secretKey)
            mac.doFinal(plaintext)
        }
        val rndGenerator = { length: Int ->
            "42BDF7E08E110F14B6D3323D14F1C2B9".decodeHex()
        }
        val ah = AuthenticationHelper(
            AuthenticationHelper.KeyId.DATA_PROT_KEY,
            fakeNfca,
            rndGenerator,
            aesEncrypt,
            aesDecrypt,
            aesCmac,
        )
        ah.authenticate()
        val sesAuthMacKey = ah.deriveSesAuthMacKey("42BDF7E08E110F14B6D3323D14F1C2B9".decodeHex(), "0D2BBA17011098E9864C8AA5192AF796".decodeHex())
        assertEquals("D6B4F8AC7A66CFA041DC179A154543BF", sesAuthMacKey.toHexString())
    }

    @Test
    fun testCmacCalculation() {
        // example from https://www.nxp.com/docs/en/application-note/AN13452.pdf
        val cmacPayload = "000060".decodeHex()
        val sesAuthMackey = "D6B4F8AC7A66CFA041DC179A154543BF".decodeHex()
        val cmac = aesCmac(sesAuthMackey, cmacPayload)
        assertEquals("551D3B5D8FE8A902A9D6A3752E164C70", cmac.toHexString())
        val truncatedMac = truncateMac(cmac)
        assertEquals("1D5DE802D6751670", truncatedMac.toHexString())
    }

    @Test
    fun testAesAuthFromActualExample() {
        val fakeNfca = object : AbstractNfcA {
            override fun connect() {}

            override fun transceive(data: ByteArray): ByteArray? {
                if (data[0] == 0x1A.toByte()) {
                    return "af85707404563f8ac0cd2d9a18c7db6239".uppercase().decodeHex()
                }
                if (data[0] == 0xAF.toByte()) {
                    assertEquals("af79c178d209dd780364fefda24240b757f02e08a6ad0cabf4a2b3f6557509718f".uppercase(), data.toHexString())
                    return "006251131285824545e764e56a5f8592c4".uppercase().decodeHex()
                }
                if (data[0] == 0x30.toByte() && data[1] == 0x29.toByte()) {
                    // Read config byte with enabled CMAC
                    return "02000000000000000000000000000000".decodeHex()
                }
                return null
            }

            override fun close() {}
        }

        val key = "000102030405060708090A0B0C0D0E0F".decodeHex()
        val secretKey = SecretKeySpec(key, 0, 16, "AES")
        val iv = IvParameterSpec(ByteArray(16))
        val aesEncrypt = { plaintext: ByteArray ->
            val cipher: Cipher = Cipher.getInstance("AES/CBC/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, iv)
            cipher.doFinal(plaintext)
        }
        val aesDecrypt = { ciphertext: ByteArray ->
            val cipher: Cipher = Cipher.getInstance("AES/CBC/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey, iv)
            cipher.doFinal(ciphertext)
        }
        val aesCmac = { plaintext: ByteArray ->
            val mac = Mac.getInstance("AESCMAC", BouncyCastleProvider())
            mac.init(secretKey)
            mac.doFinal(plaintext)
        }
        val rndGenerator = { length: Int ->
            "0293746d6cbe0d637672bd9d3bec4422".uppercase().decodeHex()
        }
        val ah = AuthenticationHelper(
            AuthenticationHelper.KeyId.DATA_PROT_KEY,
            fakeNfca,
            rndGenerator,
            aesEncrypt,
            aesDecrypt,
            aesCmac,
        )
        ah.authenticate()
        val sesAuthMacKey = ah.deriveSesAuthMacKey("0293746d6cbe0d637672bd9d3bec4422".uppercase().decodeHex(), "4f8a30afa500343a9e850485fba905d7".uppercase().decodeHex())
        assertEquals("fca816ce894300d9674a29dc8341010a".uppercase(), sesAuthMacKey.toHexString())

        val cmacPayload = "00003029".uppercase().decodeHex()
        val cmac = aesCmac(sesAuthMacKey, cmacPayload)
        assertEquals("218f09e0c13b5ee1".uppercase(), truncateMac(cmac).toHexString())
    }
}