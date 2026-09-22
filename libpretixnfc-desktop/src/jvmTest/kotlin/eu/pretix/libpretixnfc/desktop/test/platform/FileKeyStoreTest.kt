package eu.pretix.libpretixnfc.desktop.test.platform

import eu.pretix.libpretixnfc.desktop.platform.FileKeyStore
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission
import java.security.KeyFactory
import java.security.PublicKey
import java.security.interfaces.RSAPublicKey
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import javax.crypto.Cipher

class FileKeyStoreTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private fun keyDirectory(): File = File(temporaryFolder.root, "keys")

    private fun parsePem(pem: ByteArray): PublicKey {
        val text = pem.toString(Charsets.UTF_8)
        assertTrue(text.startsWith("-----BEGIN PUBLIC KEY-----\n"))
        assertTrue(text.endsWith("-----END PUBLIC KEY-----\n"))
        val der = Base64.getMimeDecoder().decode(
            text.removePrefix("-----BEGIN PUBLIC KEY-----\n").removeSuffix("-----END PUBLIC KEY-----\n")
        )
        return KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(der))
    }

    @Test
    fun publicKeyIsPemEncodedRsa2048() {
        val pem = FileKeyStore(keyDirectory()).getOrCreateRsaPubKey("device")

        val publicKey = parsePem(pem)

        assertEquals("RSA", publicKey.algorithm)
        assertEquals(2048, (publicKey as RSAPublicKey).modulus.bitLength())
    }

    @Test
    fun decryptRsaReversesEncryptionWithThePublishedKey() {
        val keyStore = FileKeyStore(keyDirectory())
        val publicKey = parsePem(keyStore.getOrCreateRsaPubKey("device"))
        val plaintext = ByteArray(16) { it.toByte() }

        val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
        cipher.init(Cipher.ENCRYPT_MODE, publicKey)
        val ciphertext = cipher.doFinal(plaintext)

        assertArrayEquals(plaintext, keyStore.decryptRsa("device", ciphertext))
    }

    @Test
    fun aSecondInstanceReusesTheStoredKey() {
        val directory = keyDirectory()

        val first = FileKeyStore(directory).getOrCreateRsaPubKey("device")
        val second = FileKeyStore(directory).getOrCreateRsaPubKey("device")

        assertArrayEquals(first, second)
    }

    @Test
    fun theKeyFileIsReadableByTheOwnerOnly() {
        val directory = keyDirectory()
        assumeTrue(directory.toPath().fileSystem.supportedFileAttributeViews().contains("posix"))

        FileKeyStore(directory).getOrCreateRsaPubKey("device")

        val permissions = Files.getPosixFilePermissions(File(directory, "device.pkcs8").toPath())
        assertEquals(setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE), permissions)
    }
}
