package eu.pretix.libpretixnfc.platform

interface HardwareBackedKeyStore {
    fun hasHmacKey(keyName: String): Boolean
    fun importHmacKey(keyName: String, keyValue: ByteArray)
    fun hmacSHA256(keyName: String, message: ByteArray): ByteArray
    fun getOrCreateRsaPubKey(keyName: String): ByteArray?
    fun decryptRsa(keyName: String, ciphertext: ByteArray): ByteArray
}