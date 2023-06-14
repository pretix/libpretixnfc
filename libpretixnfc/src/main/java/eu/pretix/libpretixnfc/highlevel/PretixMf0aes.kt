import eu.pretix.libpretixnfc.chunkPayload
import eu.pretix.libpretixnfc.commands.nxp.GetVersion
import eu.pretix.libpretixnfc.commands.nxp.ReadPages
import eu.pretix.libpretixnfc.commands.nxp.WritePage
import eu.pretix.libpretixnfc.commands.nxp.mf0aes.AuthenticationHelper
import eu.pretix.libpretixnfc.commands.nxp.mf0aes.UidHelper
import eu.pretix.libpretixnfc.communication.AbstractNfcA
import eu.pretix.libpretixnfc.communication.ChipReadError
import eu.pretix.libpretixnfc.communication.NfcChipReadError
import eu.pretix.libpretixnfc.config.MifareUltralightAesConfigChange
import eu.pretix.libpretixnfc.cryptography.An10922KeyDiversification
import eu.pretix.libpretixnfc.tagtypes.UltralightAESTagType
import eu.pretix.libpretixnfc.toHexString
import java.io.ByteArrayOutputStream
import java.nio.charset.Charset


data class Mf0aesKeySet(
    val publicId: Long,
    val canEncode: Boolean,
    val uidKey: ByteArray,
    val diversificationKey: ByteArray,
)

class PretixMf0aes(val keySets: List<Mf0aesKeySet>, val useRandomIdForNewTags: Boolean, val debug: Boolean) {
    /*
    This scheme writes the last two pages of the user data like this:

    Page 0x04: public_id (big endian)
     */

    fun process(nfca: AbstractNfcA, encodeWith: Mf0aesKeySet? = null): String {
        nfca.connect()
        try {
            val tagType = GetVersion().execute(nfca) as? UltralightAESTagType
            if (tagType != UltralightAESTagType.UltralightAES) {
                throw NfcChipReadError(ChipReadError.UNKNOWN_CHIP_TYPE)
            }

            val firstPage = readPages(nfca, 0x04, 1)
            if (firstPage.contentEquals(byteArrayOf(0, 0, 0, 0))) {
                if (encodeWith != null) {
                    return encode(nfca, encodeWith)
                } else {
                    throw NfcChipReadError(ChipReadError.EMPTY_CHIP)
                }
            }
            for (keySet in keySets) {
                val expectedPage = idToBytes(keySet.publicId)
                if (firstPage.contentEquals(expectedPage)) {
                    return decode(nfca, keySet)
                }
            }
            throw NfcChipReadError(ChipReadError.FOREIGN_CHIP)
        } finally {
            nfca.close()
        }
    }

    private fun idToBytes(publicId: Long): ByteArray {
        return byteArrayOf(
            (publicId shr 24).toByte(),
            (publicId shr 16).toByte(),
            (publicId shr 8).toByte(),
            (publicId shr 0).toByte(),
        )
    }

    private fun decode(nfca: AbstractNfcA, keySet: Mf0aesKeySet): String {
        var authenticatedNfcA = AuthenticationHelper(
            AuthenticationHelper.KeyId.UID_RETR_KEY,
            nfca,
            keySet.uidKey
        ).authenticate()
        val uid = UidHelper(authenticatedNfcA).readUid()

        val diversifiedKey = An10922KeyDiversification().generateDiversifiedKeyAES128(
            keySet.diversificationKey,
            uid,  // 4 bytes
            "eu.pretix".toByteArray(Charset.defaultCharset()),  // 9 bytes
            idToBytes(keySet.publicId)  // 4 bytes
            // total diversification input: 17 bytes (must be 16..32)
        )

        AuthenticationHelper(
            AuthenticationHelper.KeyId.DATA_PROT_KEY,
            authenticatedNfcA,
            diversifiedKey
        ).authenticate()
        return uid.toHexString(false)
    }

    private fun encode(nfca: AbstractNfcA, keySet: Mf0aesKeySet): String {
        var uid = UidHelper(nfca).readUid()
        if (uid[0] == 0x08.toByte()) {
            AuthenticationHelper(
                AuthenticationHelper.KeyId.UID_RETR_KEY,
                nfca,
                keySet.uidKey
            ).authenticate()
            uid = UidHelper(nfca).readUid()
        }

        val diversifiedKey = An10922KeyDiversification().generateDiversifiedKeyAES128(
            keySet.diversificationKey,
            uid,  // 4 bytes
            "eu.pretix".toByteArray(Charset.defaultCharset()),  // 9 bytes
            idToBytes(keySet.publicId)  // 4 bytes
            // total diversification input: 17 bytes (must be 16..32)
        )

        /*val initialKey = ByteArray(16)
        AuthenticationHelper(
            AuthenticationHelper.KeyId.DATA_PROT_KEY,
            nfca,
            initialKey
        ).authenticate()*/

        MifareUltralightAesConfigChange.Builder()
            .setDataProtKey(diversifiedKey)
            .setUidRetrKey(keySet.uidKey)
            .setSecurityOptions(
                useRandomIdForNewTags,
                true,
                // Don't write-protect chip in debug mode so it can be easily recovered and reused
                if (debug) MifareUltralightAesConfigChange.AUTH0_DEFAULT else 0x04
            )
            .setProtectionOptions(
                false,
                false,
                false,
                false,
                0x05,
                256
            )
            .build()
            .write(nfca)

        writePages(nfca, 0x04, idToBytes(keySet.publicId))

        return uid.toHexString(false)
    }

    private fun readPages(nfca: AbstractNfcA, first: Int, number: Int): ByteArray {
        val ba = ByteArrayOutputStream()
        for (page in first until first + number step 4) {
            ba.write(ReadPages(page).execute(nfca))
        }
        return ba.toByteArray().slice(0 until number * 4).toByteArray()
    }

    private fun writePages(nfca: AbstractNfcA, first: Int, data: ByteArray) {
        val chunks = chunkPayload(data)
        for (chunk in chunks.withIndex()) {
            WritePage(first + chunk.index, chunk.value).execute(nfca)
        }
    }
}
