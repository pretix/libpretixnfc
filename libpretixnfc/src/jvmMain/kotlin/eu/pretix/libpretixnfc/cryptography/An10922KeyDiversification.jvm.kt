package eu.pretix.libpretixnfc.cryptography

import org.bouncycastle.jce.provider.BouncyCastleProvider
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

actual class An10922KeyDiversification {
    // https://www.nxp.com/docs/en/application-note/AN10922.pdf

    companion object {
        val staticAesCMac: Mac = Mac.getInstance("AESCMAC", BouncyCastleProvider())
    }

    actual fun generateDiversifiedKeyAES128(masterKey: ByteArray, uid: ByteArray, applicationId: ByteArray, systemId: ByteArray): ByteArray {
        return diversifyKeyAES128(masterKey, uid, applicationId, systemId) { key, diversificationInput ->
            val secretKey = SecretKeySpec(key, 0, 16, "AES")
            staticAesCMac.init(secretKey)
            staticAesCMac.doFinal(diversificationInput)
        }
    }
}
