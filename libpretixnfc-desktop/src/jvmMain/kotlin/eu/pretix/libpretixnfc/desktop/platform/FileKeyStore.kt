package eu.pretix.libpretixnfc.desktop.platform

import eu.pretix.libpretixnfc.platform.HardwareBackedKeyStore
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermission
import java.nio.file.attribute.PosixFilePermissions
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPrivateCrtKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.RSAPublicKeySpec
import java.util.Base64
import javax.crypto.Cipher

/**
 * Keeps an RSA key pair in [directory], one PKCS#8 file per key name, readable by the owner only.
 *
 * HMAC keys are not supported: the desktop app only needs the RSA key pair used to receive the
 * media key sets from the server.
 */
class FileKeyStore(private val directory: File) : HardwareBackedKeyStore {
    override fun hasHmacKey(keyName: String): Boolean {
        throw UnsupportedOperationException("HMAC keys are not supported")
    }

    override fun importHmacKey(keyName: String, keyValue: ByteArray) {
        throw UnsupportedOperationException("HMAC keys are not supported")
    }

    override fun hmacSHA256(keyName: String, message: ByteArray): ByteArray {
        throw UnsupportedOperationException("HMAC keys are not supported")
    }

    @Synchronized
    override fun getOrCreateRsaPubKey(keyName: String): ByteArray {
        val privateKey = loadPrivateKey(keyName) ?: createPrivateKey(keyName)
        val publicKey = KeyFactory.getInstance(KEY_ALGORITHM)
            .generatePublic(RSAPublicKeySpec(privateKey.modulus, privateKey.publicExponent))
        return pemEncode(publicKey.encoded).toByteArray(Charsets.UTF_8)
    }

    @Synchronized
    override fun decryptRsa(keyName: String, ciphertext: ByteArray): ByteArray {
        val privateKey = checkNotNull(loadPrivateKey(keyName)) { "No key stored as $keyName" }
        val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, privateKey)
        return cipher.doFinal(ciphertext)
    }

    private fun loadPrivateKey(keyName: String): RSAPrivateCrtKey? {
        val path = keyPath(keyName)
        if (!Files.exists(path)) {
            return null
        }
        val spec = PKCS8EncodedKeySpec(Files.readAllBytes(path))
        return KeyFactory.getInstance(KEY_ALGORITHM).generatePrivate(spec) as RSAPrivateCrtKey
    }

    private fun createPrivateKey(keyName: String): RSAPrivateCrtKey {
        val generator = KeyPairGenerator.getInstance(KEY_ALGORITHM)
        generator.initialize(KEY_SIZE)
        val privateKey = generator.generateKeyPair().private as RSAPrivateCrtKey
        Files.createDirectories(directory.toPath())
        writeOwnerOnly(keyPath(keyName), privateKey.encoded)
        return privateKey
    }

    private fun writeOwnerOnly(path: Path, content: ByteArray) {
        if (path.fileSystem.supportedFileAttributeViews().contains("posix")) {
            Files.createFile(
                path,
                PosixFilePermissions.asFileAttribute(
                    setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE)
                )
            )
        }
        Files.write(path, content)
    }

    private fun keyPath(keyName: String): Path = File(directory, "$keyName.pkcs8").toPath()

    private fun pemEncode(der: ByteArray): String {
        val body = Base64.getMimeEncoder(PEM_LINE_LENGTH, "\n".toByteArray()).encodeToString(der)
        return "-----BEGIN PUBLIC KEY-----\n$body\n-----END PUBLIC KEY-----\n"
    }

    private companion object {
        const val KEY_ALGORITHM = "RSA"
        const val KEY_SIZE = 2048
        const val CIPHER_TRANSFORMATION = "RSA/ECB/PKCS1Padding"
        const val PEM_LINE_LENGTH = 64
    }
}
