package eu.pretix.libpretixnfc.cryptography

import eu.pretix.libpretixnfc.commands.nxp.mf0aes.staticAesCMac
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

internal actual fun encryptAesCbc(secret: ByteArray, plaintext: ByteArray): ByteArray {
    val secretKey = SecretKeySpec(secret, 0, 16, "AES")
    val iv = IvParameterSpec(ByteArray(16))
    val cipher: Cipher = Cipher.getInstance("AES/CBC/NoPadding")
    cipher.init(Cipher.ENCRYPT_MODE, secretKey, iv)
    return cipher.doFinal(plaintext)
}

internal actual fun decryptAesCbc(secret: ByteArray, ciphertext: ByteArray): ByteArray {
    val secretKey = SecretKeySpec(secret, 0, 16, "AES")
    val iv = IvParameterSpec(ByteArray(16))
    val cipher: Cipher = Cipher.getInstance("AES/CBC/NoPadding")
    cipher.init(Cipher.DECRYPT_MODE, secretKey, iv)
    return cipher.doFinal(ciphertext)
}

internal actual fun computeAesCmac(secret: ByteArray, message: ByteArray): ByteArray {
    val secretKey = SecretKeySpec(secret, 0, 16, "AES")
    staticAesCMac.init(secretKey)
    return staticAesCMac.doFinal(message)
}

internal actual fun secureRandomBytes(length: Int): ByteArray {
    val b = ByteArray(length)
    SecureRandom().nextBytes(b)
    return b
}
