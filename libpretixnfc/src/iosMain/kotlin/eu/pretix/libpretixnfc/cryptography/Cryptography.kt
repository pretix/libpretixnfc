package eu.pretix.libpretixnfc.cryptography

import platform.Foundation.NSData

interface Cryptography {
    /** AES-128-CBC with an all-zero IV and no padding. Returns as many bytes as [plaintext]. */
    @Throws(Throwable::class)
    fun encryptAesCbc(key: NSData, plaintext: NSData): NSData

    /** AES-128-CBC with an all-zero IV and no padding. Returns as many bytes as [ciphertext]. */
    @Throws(Throwable::class)
    fun decryptAesCbc(key: NSData, ciphertext: NSData): NSData

    /** AES-CMAC (RFC 4493). Returns 16 bytes. */
    @Throws(Throwable::class)
    fun aesCmac(key: NSData, message: NSData): NSData
}

object CryptographyRegistry {
    private var registered: Cryptography? = null

    fun register(cryptography: Cryptography) {
        registered = cryptography
    }

    internal fun require(): Cryptography = checkNotNull(registered) {
        "No Cryptography implementation registered. Call CryptographyRegistry.register()."
    }
}
