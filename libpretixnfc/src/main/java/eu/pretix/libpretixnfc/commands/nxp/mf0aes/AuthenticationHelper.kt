package eu.pretix.libpretixnfc.commands.nxp.mf0aes

import eu.pretix.libpretixnfc.commands.nxp.ReadPages
import eu.pretix.libpretixnfc.communication.AbstractNfcA
import eu.pretix.libpretixnfc.communication.NfcIOError
import eu.pretix.libpretixnfc.rotateLeft
import eu.pretix.libpretixnfc.toHexString
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.experimental.and
import kotlin.experimental.xor


private val defaultAesEncrypt = { secret: ByteArray ->
    { plaintext: ByteArray ->
        val secretKey = SecretKeySpec(secret, 0, 16, "AES")
        val iv = IvParameterSpec(ByteArray(16))
        val cipher: Cipher = Cipher.getInstance("AES/CBC/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, iv)
        cipher.doFinal(plaintext)
    }
}
private val defaultAesDecrypt = { secret: ByteArray ->
    { ciphertext: ByteArray ->
        val secretKey = SecretKeySpec(secret, 0, 16, "AES")
        val iv = IvParameterSpec(ByteArray(16))
        val cipher: Cipher = Cipher.getInstance("AES/CBC/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secretKey, iv)
        cipher.doFinal(ciphertext)
    }
}
private val defaultRndGenerator = { length: Int ->
    val b = ByteArray(length)
    SecureRandom().nextBytes(b)
    b
}

fun aesCmac(secret: ByteArray, plaintext: ByteArray): ByteArray {
    val secretKey = SecretKeySpec(secret, 0, 16, "AES")
    val mac = Mac.getInstance("AESCMAC", BouncyCastleProvider())
    mac.init(secretKey)
    return mac.doFinal(plaintext)
}

fun truncateMac(mac: ByteArray): ByteArray {
    return byteArrayOf(mac[1], mac[3], mac[5], mac[7], mac[9], mac[11], mac[13], mac[15])
}

private val defaultAesCmac = { secret: ByteArray ->
    { plaintext: ByteArray -> aesCmac(secret, plaintext) }
}

class AuthenticationHelper(
    val keyId: KeyId,
    val nfca: AbstractNfcA,
    val rndGenerator: ((Int) -> ByteArray),
    // The following functions are defined as functions only of the plain/ciphertext, not of the key,
    // so a caller of this class could pass in functions that perform the cryptographic operations
    // on a hardware security module that does not allow extracting the keys.
    val aesEncrypt: ((ByteArray) -> ByteArray),
    val aesDecrypt: ((ByteArray) -> ByteArray),
    val aesCmac: ((ByteArray) -> ByteArray),
) {
    constructor(keyId: KeyId, nfca: AbstractNfcA, key: ByteArray) : this(
        keyId,
        nfca,
        defaultRndGenerator,
        defaultAesEncrypt(key),
        defaultAesDecrypt(key),
        defaultAesCmac(key),
    ) {
        check(key.size == 16)
    }

    enum class KeyId(val keyId: Byte) {
        DATA_PROT_KEY(0x00),
        UID_RETR_KEY(0x01),
        ORIGINALITY_KEY(0x02),
    }

    class AuthenticatedNfcA(val sesAuthMacKey: ByteArray, val base: AbstractNfcA): AbstractNfcA {
        var cmdCtr = 0

        override fun connect() {
            base.connect()
        }

        override fun transceive(data: ByteArray): ByteArray {
            // In cryptographic calculations, the CmdCtr is represented with LSB first
            val cmdCtrBytes = byteArrayOf((cmdCtr and 0xFF).toByte(), ((cmdCtr and 0xFF00) shr 8).toByte())
            val sendCmac = truncateMac(aesCmac(sesAuthMacKey, cmdCtrBytes + data))
            check(sendCmac.size == 8)
            val recvData = base.transceive(data + sendCmac)
            if (recvData!!.size < 8) {
                throw NfcIOError("Response does not include CMAC")
            }

            cmdCtr++ // The command counter is incremented between each command and response

            val newCmdCtrBytes = byteArrayOf((cmdCtr and 0xFF).toByte(), ((cmdCtr and 0xFF00) shr 8).toByte())
            val recvCmac = recvData.copyOfRange(recvData.size - 8, recvData.size)
            val recvPayload = recvData.copyOfRange(0, recvData.size - 8)
            check(recvCmac.size == 8)
            if (!recvCmac.contentEquals(truncateMac(aesCmac(sesAuthMacKey, newCmdCtrBytes + recvPayload))) ){
                throw NfcIOError("CMAC mismatch")
            }

            cmdCtr++ // The command counter is incremented after each response

            return if (recvPayload.isNotEmpty()) {
                recvPayload
            } else {
                // "For commands where the expected response is only an ACK, this ACK is replaced by
                // just the CMAC calculated over the command counter."
                return byteArrayOf(0x0A)
            }
        }

        override fun close() {
            base.close()
        }
    }

    fun checkCmacEnabled(): Boolean {
        val configPages = ReadPages(0x29).execute(nfca)
        return configPages.get(0).and(0b10.toByte()) > 0
    }

    fun authenticate(): AbstractNfcA {
        val cmacEnabled = checkCmacEnabled()

        // Execute three-way AUTHENTICATE function
        val encryptedRndB = AuthenticatePart1(keyId.keyId).execute(nfca)

        val rndB = aesDecrypt(encryptedRndB)
        val rndBPrime = rotateLeft(rndB, 1)

        val rndA = rndGenerator(16)
        val rndAPrime = rotateLeft(rndA, 1)
        val encryptedRndARndB = aesEncrypt(rndA + rndBPrime)

        val encryptedRndAPrime = AuthenticatePart2(encryptedRndARndB).execute(nfca)
        val receivedRndAPrime = aesDecrypt(encryptedRndAPrime)
        if (!rndAPrime.contentEquals(receivedRndAPrime)) {
            throw NfcIOError("Authentication failed")
        }

        if (cmacEnabled) {
            val sesAuthMacKey = deriveSesAuthMacKey(rndA, rndB)
            return AuthenticatedNfcA(sesAuthMacKey, if (nfca is AuthenticatedNfcA) nfca.base else nfca)
        } else {
            return nfca
        }
    }

    fun deriveSesAuthMacKey(rndA: ByteArray, rndB: ByteArray): ByteArray {
        // Authentication was successful, now we need to compute the session key
        val sv2 = byteArrayOf(
            0x5A, 0xA5.toByte(), // a 2-byte label, distinguishing the purpose of the key: 5AA5h for MACing
            0x00, 0x01, // a 2-byte counter, fixed to 0001h as only 128-bit keys are generated.
            0x00, 0x80.toByte(), // a 2-byte length, fixed to 0080h as only 128-bit keys are generated.
            // a 26-byte context, constructed using the two random numbers exchanged, RndA and RndB
            rndA[0],
            rndA[1],
            rndA[2] xor rndB[0],
            rndA[3] xor rndB[1],
            rndA[4] xor rndB[2],
            rndA[5] xor rndB[3],
            rndA[6] xor rndB[4],
            rndA[7] xor rndB[5],
            rndB[6],
            rndB[7],
            rndB[8],
            rndB[9],
            rndB[10],
            rndB[11],
            rndB[12],
            rndB[13],
            rndB[14],
            rndB[15],
            rndA[8],
            rndA[9],
            rndA[10],
            rndA[11],
            rndA[12],
            rndA[13],
            rndA[14],
            rndA[15],
        )
        check(sv2.size == 32)

        val sesAuthMacKey = aesCmac(sv2)
        check(sesAuthMacKey.size == 16)
        return sesAuthMacKey
    }
}