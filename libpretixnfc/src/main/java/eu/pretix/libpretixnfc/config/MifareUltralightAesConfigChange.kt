package eu.pretix.libpretixnfc.config

import eu.pretix.libpretixnfc.commands.nxp.WritePage
import eu.pretix.libpretixnfc.communication.AbstractNfcA
import kotlin.experimental.or

class MifareUltralightAesConfigChange(override val changes: List<PageChange>) : ConfigChange() {
    data class LockKeys(
        val lockAesKey0: Boolean,
        val lockAesKey1: Boolean,
        val blockLockKeys: Boolean,
    ) {
        fun toPage(): ByteArray {
            val page = ByteArray(4)
            if (lockAesKey1) page[0] = page[0] or (0x01 shl 7).toByte()
            if (lockAesKey0) page[0] = page[0] or (0x01 shl 6).toByte()
            if (blockLockKeys) page[0] = page[0] or (0x01 shl 5).toByte()
            return page
        }
    }

    data class SecurityOptions(
        val randomIdActive: Boolean,
        val secureMessagingActive: Boolean,
        val auth0: Byte
    ) {
        init {
            check(auth0 <= 0b1111111)
        }

        fun toPage(): ByteArray {
            val page = ByteArray(4)
            if (randomIdActive) page[0] = page[0] or 1
            if (secureMessagingActive) page[0] = page[0] or (0x01 shl 1).toByte()
            page[3] = auth0
            return page
        }
    }

    data class ProtectionOptions(
        val protectReads: Boolean,
        val lockUserConfig: Boolean,
        val counter2IncrementWithoutAuthEnabled: Boolean,
        val counter2ReadWithoutAuthEnabled: Boolean,
        val vctid: Byte,
        val authLim: Int,
    ) {
        init {
            check(authLim <= 0x1111111111)
        }

        fun toPage(): ByteArray {
            val page = ByteArray(4)

            // CNT
            if (protectReads) page[0] = page[0] or (0x01 shl 7).toByte()
            if (lockUserConfig) page[0] = page[0] or (0x01 shl 6).toByte()
            if (counter2IncrementWithoutAuthEnabled) page[0] = page[0] or (0x01 shl 3).toByte()
            if (counter2ReadWithoutAuthEnabled) page[0] = page[0] or (0x01 shl 2).toByte()

            // VCTID
            page[1] = vctid

            // AUTH_LIM
            page[2] = (authLim and 0xFF).toByte()
            page[3] = ((authLim shr 8) and 0b11).toByte()
            return page
        }
    }

    data class Builder(
        var lockByte0: Byte? = null,
        var lockByte1: Byte? = null,
        var lockByte2: Byte? = null,
        var lockByte3: Byte? = null,
        var lockByte4: Byte? = null,
        var securityOptions: SecurityOptions? = null,
        var protectionOptions: ProtectionOptions? = null,
        var lockKeys: LockKeys? = null,
        var dataProtKey: ByteArray? = null,
        var uidRetrKey: ByteArray? = null,
    ) {
        fun setLockByte0(value: Byte) = apply { this.lockByte0 = value }
        fun setLockByte1(value: Byte) = apply { this.lockByte0 = value }
        fun setLockByte2(value: Byte) = apply { this.lockByte0 = value }
        fun setLockByte3(value: Byte) = apply { this.lockByte0 = value }
        fun setLockByte4(value: Byte) = apply { this.lockByte0 = value }

        fun setSecurityOptions(
            randomIdActive: Boolean,
            secureMessagingActive: Boolean,
            auth0: Byte = AUTH0_DEFAULT
        ) = apply {
            this.securityOptions = SecurityOptions(randomIdActive, secureMessagingActive, auth0)
        }

        fun setLockKeys(lockAesKey0: Boolean, lockAesKey1: Boolean, blockLockKeys: Boolean) =
            apply {
                this.lockKeys = LockKeys(lockAesKey0, lockAesKey1, blockLockKeys)
            }

        fun setProtectionOptions(
            protectReads: Boolean,
            lockUserConfig: Boolean,
            counter2IncrementWithoutAuthEnabled: Boolean,
            counter2ReadWithoutAuthEnabled: Boolean,
            vctId: Byte,
            authLim: Int
        ) = apply {
            this.protectionOptions = ProtectionOptions(
                protectReads,
                lockUserConfig,
                counter2IncrementWithoutAuthEnabled,
                counter2ReadWithoutAuthEnabled,
                vctId,
                authLim
            )
        }

        fun setDataProtKey(key: ByteArray) = apply {
            check(key.size == 16)
            this.dataProtKey = key
        }

        fun setUidRetrKey(key: ByteArray) = apply {
            check(key.size == 16)
            this.uidRetrKey = key
        }

        fun build(): MifareUltralightAesConfigChange {
            val changes = mutableListOf<PageChange>()

            if (dataProtKey != null) {
                val k = dataProtKey!!.reversed().toByteArray()
                changes.add(PageChange(0x30, k.copyOfRange(0, 4)))
                changes.add(PageChange(0x31, k.copyOfRange(4, 8)))
                changes.add(PageChange(0x32, k.copyOfRange(8, 12)))
                changes.add(PageChange(0x33, k.copyOfRange(12, 16)))
            }
            if (uidRetrKey != null) {
                val k = uidRetrKey!!.reversed().toByteArray()
                changes.add(PageChange(0x34, k.copyOfRange(0, 4)))
                changes.add(PageChange(0x35, k.copyOfRange(4, 8)))
                changes.add(PageChange(0x36, k.copyOfRange(8, 12)))
                changes.add(PageChange(0x37, k.copyOfRange(12, 16)))
            }
            if (securityOptions != null) {
                changes.add(PageChange(0x29, securityOptions!!.toPage()))
            }
            if (lockKeys != null) {
                changes.add(PageChange(0x2D, lockKeys!!.toPage()))
            }
            if (protectionOptions != null) {
                changes.add(PageChange(0x2A, protectionOptions!!.toPage()))
            }
            if (lockByte0 != null || lockByte1 != null) {
                // Lock bytes are ORed with existing data on write, so there's no worry to override with 0
                changes.add(
                    PageChange(
                        0x02,
                        byteArrayOf(0x00, 0x00, lockByte0 ?: 0x00, lockByte1 ?: 0x00)
                    )
                )
            }
            if (lockByte2 != null || lockByte3 != null || lockByte4 != null) {
                // Lock bytes are ORed with existing data on write, so there's no worry to override with 0
                changes.add(
                    PageChange(
                        0x28,
                        byteArrayOf(lockByte2 ?: 0x00, lockByte3 ?: 0x00, lockByte4 ?: 0x00, 0x00)
                    )
                )
            }
            return MifareUltralightAesConfigChange(changes)
        }
    }

    override fun write(nfca: AbstractNfcA) {
        for (c in changes) {
            WritePage(c.pageAddress.toInt(), c.data).execute(nfca)
        }
    }

    companion object {
        val AUTH0_DEFAULT = 0x3C.toByte()
        val VCTID_DEFAULT = 0b00000101.toByte()
    }
}