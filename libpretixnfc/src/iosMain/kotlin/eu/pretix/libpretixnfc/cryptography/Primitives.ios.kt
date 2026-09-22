package eu.pretix.libpretixnfc.cryptography

import eu.pretix.libpretixnfc.toByteArray
import eu.pretix.libpretixnfc.toNSData
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Security.SecRandomCopyBytes
import platform.Security.errSecSuccess
import platform.Security.kSecRandomDefault

internal actual fun encryptAesCbc(secret: ByteArray, plaintext: ByteArray): ByteArray {
    check(secret.size == 16) { "AES-128 needs a 16 byte key, got ${secret.size}" }
    check(plaintext.size % 16 == 0) { "AES-CBC needs a multiple of 16 bytes, got ${plaintext.size}" }
    val ciphertext = cryptography("encryptAesCbc") {
        encryptAesCbc(secret.toNSData(), plaintext.toNSData())
    }.toByteArray()
    check(ciphertext.size == plaintext.size) {
        "encryptAesCbc returned ${ciphertext.size} bytes instead of ${plaintext.size}"
    }
    return ciphertext
}

internal actual fun decryptAesCbc(secret: ByteArray, ciphertext: ByteArray): ByteArray {
    check(secret.size == 16) { "AES-128 needs a 16 byte key, got ${secret.size}" }
    check(ciphertext.size % 16 == 0) { "AES-CBC needs a multiple of 16 bytes, got ${ciphertext.size}" }
    val plaintext = cryptography("decryptAesCbc") {
        decryptAesCbc(secret.toNSData(), ciphertext.toNSData())
    }.toByteArray()
    check(plaintext.size == ciphertext.size) {
        "decryptAesCbc returned ${plaintext.size} bytes instead of ${ciphertext.size}"
    }
    return plaintext
}

internal actual fun computeAesCmac(secret: ByteArray, message: ByteArray): ByteArray {
    check(secret.size == 16) { "AES-CMAC needs a 16 byte key, got ${secret.size}" }
    val mac = cryptography("aesCmac") {
        aesCmac(secret.toNSData(), message.toNSData())
    }.toByteArray()
    check(mac.size == 16) { "aesCmac returned ${mac.size} bytes instead of 16" }
    return mac
}

/**
 * Runs [operation] on the registered [Cryptography]. An error thrown by the application's
 * implementation is reported as an [IllegalStateException] naming the operation.
 */
private fun cryptography(operation: String, block: Cryptography.() -> NSData): NSData {
    val cryptography = CryptographyRegistry.require()
    return try {
        cryptography.block()
    } catch (error: Throwable) {
        throw IllegalStateException("$operation failed: $error", error)
    }
}

@OptIn(ExperimentalForeignApi::class)
internal actual fun secureRandomBytes(length: Int): ByteArray {
    check(length > 0) { "Cannot generate $length random bytes" }
    val bytes = ByteArray(length)
    val status = bytes.usePinned {
        SecRandomCopyBytes(kSecRandomDefault, length.convert(), it.addressOf(0))
    }
    check(status == errSecSuccess) { "SecRandomCopyBytes failed with status $status" }
    return bytes
}
