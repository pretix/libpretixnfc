package eu.pretix.libpretixnfc.cryptography

/**
 * Encrypts [plaintext] with AES-128 in CBC mode with an all-zero IV and without padding.
 * [secret] is 16 bytes long, [plaintext] a multiple of 16 bytes.
 */
internal expect fun encryptAesCbc(secret: ByteArray, plaintext: ByteArray): ByteArray

/**
 * Decrypts [ciphertext] with AES-128 in CBC mode with an all-zero IV and without padding.
 * [secret] is 16 bytes long, [ciphertext] a multiple of 16 bytes.
 */
internal expect fun decryptAesCbc(secret: ByteArray, ciphertext: ByteArray): ByteArray

/**
 * Calculates the 16 byte AES-CMAC (RFC 4493) of [message] under the 16 byte key [secret].
 */
internal expect fun computeAesCmac(secret: ByteArray, message: ByteArray): ByteArray

/**
 * Returns [length] cryptographically secure random bytes.
 */
internal expect fun secureRandomBytes(length: Int): ByteArray
