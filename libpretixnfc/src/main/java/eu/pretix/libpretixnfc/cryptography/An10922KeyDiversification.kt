package eu.pretix.libpretixnfc.cryptography

import org.bouncycastle.jce.provider.BouncyCastleProvider
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class An10922KeyDiversification {
    // https://www.nxp.com/docs/en/application-note/AN10922.pdf

    companion object {
        val staticAesCMac: Mac = Mac.getInstance("AESCMAC", BouncyCastleProvider())
    }

    fun generateDiversifiedKeyAES128(masterKey: ByteArray, uid: ByteArray, applicationId: ByteArray, systemId: ByteArray): ByteArray {
        check(masterKey.size == 16)

        val diversificationInput = byteArrayOf(0x01) + uid + applicationId + systemId
        check (diversificationInput.size <= 32)
        check (diversificationInput.size >= 15)  // otherwise we'd need to implement CMAC ourselves

        /*var padded = false
        if (diversificationInput.size < 32) {
            padded = true
            val padding = ByteArray(32 - diversificationInput.size)
            padding[0] = 0x80.toByte()
            diversificationInput += padding
        }*/

        val secretKey = SecretKeySpec(masterKey, 0, 16, "AES")
        staticAesCMac.init(secretKey)
        val cmac = staticAesCMac.doFinal(diversificationInput)
        return cmac
    }
}