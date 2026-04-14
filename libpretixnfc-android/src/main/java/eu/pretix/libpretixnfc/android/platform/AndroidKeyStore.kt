package eu.pretix.libpretixnfc.android.platform

import android.content.Context
import android.os.Build
import android.security.KeyStoreParameter
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.KeyProtection
import eu.pretix.libpretixnfc.platform.HardwareBackedKeyStore
import org.spongycastle.util.io.pem.PemObject
import org.spongycastle.util.io.pem.PemWriter
import java.io.StringWriter
import java.nio.charset.Charset
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.util.concurrent.locks.ReentrantLock
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.SecretKey
import kotlin.concurrent.withLock


val keyLock = ReentrantLock()

class AndroidKeyStore(val ctx: Context) : HardwareBackedKeyStore {
    override fun importHmacKey(keyName: String, keyValue: ByteArray) {
        val key = object : SecretKey {
            override fun getAlgorithm(): String {
                return "HmacSHA512"
            }

            override fun getFormat(): String {
                return "RAW"
            }

            override fun getEncoded(): ByteArray {
                return keyValue
            }
        }

        val keyStore = KeyStore.getInstance("AndroidKeyStore")
        keyStore.load(null)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            keyStore.setEntry(
                keyName,
                KeyStore.SecretKeyEntry(key),
                KeyProtection.Builder(KeyProperties.PURPOSE_SIGN).build()
            )
        } else {
            keyStore.setEntry(
                keyName,
                KeyStore.SecretKeyEntry(key),
                KeyStoreParameter.Builder(ctx).build(),
            )
        }
    }

    override fun hmacSHA256(keyName: String, message: ByteArray): ByteArray {
        val keyStore = KeyStore.getInstance("AndroidKeyStore")
        keyStore.load(null)
        // Key imported, obtain a reference to it.
        val keyStoreKey = keyStore.getKey(keyName, null)
        // The original key can now be discarded.

        val mac = Mac.getInstance("HmacSHA512")
        mac.init(keyStoreKey)
        mac.update(message)
        return mac.doFinal()
    }

    override fun hasHmacKey(keyName: String): Boolean {
        val keyStore = KeyStore.getInstance("AndroidKeyStore")
        keyStore.load(null)
        return keyStore.isKeyEntry(keyName)
    }

    override fun getOrCreateRsaPubKey(keyName: String): ByteArray? {
        return keyLock.withLock {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                return null
            }
            val keyStore = KeyStore.getInstance("AndroidKeyStore")
            keyStore.load(null)
            val pubkey = if (keyStore.isKeyEntry(keyName)) {
                keyStore.getCertificate(keyName).publicKey
            } else {
                val keyPair =
                    KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, "AndroidKeyStore")
                        .apply {
                            val parameterSpec = KeyGenParameterSpec.Builder(
                                keyName,
                                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                            ).run {
                                setBlockModes(KeyProperties.BLOCK_MODE_ECB)
                                setDigests(
                                    KeyProperties.DIGEST_SHA1,
                                    KeyProperties.DIGEST_SHA256,
                                    KeyProperties.DIGEST_SHA512
                                )
                                setEncryptionPaddings(
                                    KeyProperties.ENCRYPTION_PADDING_RSA_OAEP,
                                    KeyProperties.ENCRYPTION_PADDING_RSA_PKCS1
                                )
                                setKeySize(2048)
                                setUserAuthenticationRequired(false)
                                build()
                            }
                            initialize(parameterSpec)
                        }.genKeyPair()
                keyPair.public
            }
            val writer = StringWriter()
            val pemWriter = PemWriter(writer)
            pemWriter.writeObject(PemObject("PUBLIC KEY", pubkey.encoded))
            pemWriter.flush()
            pemWriter.close()
            return@withLock writer.toString().toByteArray(Charset.defaultCharset())
        }
    }

    override fun decryptRsa(keyName: String, ciphertext: ByteArray): ByteArray {
        val keyStore = KeyStore.getInstance("AndroidKeyStore")
        keyStore.load(null)
        val privateKey = keyStore.getKey(keyName, null) as PrivateKey?
        val cipher: Cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
        cipher.init(Cipher.DECRYPT_MODE, privateKey)
        return cipher.doFinal(ciphertext)
    }
}