package eu.pretix.libpretixnfc.commands.nxp

import eu.pretix.libpretixnfc.commands.Command
import eu.pretix.libpretixnfc.communication.ChipReadError
import eu.pretix.libpretixnfc.communication.NfcChipReadError
import eu.pretix.libpretixnfc.tagtypes.NtagTagType
import eu.pretix.libpretixnfc.tagtypes.TagType
import eu.pretix.libpretixnfc.tagtypes.UltralightAESTagType


class GetVersion : Command<TagType>() {
    override fun toBytes(): ByteArray {
        return byteArrayOf(0x60)
    }

    override fun parseResponse(data: ByteArray): TagType {
        /*
         * Bytes:
         * 0 fixed Header
         * 1 vendor ID, 04h=NXP Semiconductors
         * 2 product type, 04h=NTAG, 03h=Ultralight
         * 3 product subtype = 01h=17pF, 02h=50pF
         * 4 major product version, NTAG: 01h=1, Ultralight: 04h=AES
         * 5 minor product version: 00h=V0
         * 6 storage size (see data sheet)
         * 7 protocol type: 03h=ISO/IEC 14443-3 compliant
         */
        if (data.size < 7) {
            throw NfcChipReadError(ChipReadError.IO_ERROR)
        }
        if (data[1].toInt() != 0x04) {
            throw NfcChipReadError(ChipReadError.UNKNOWN_CHIP_TYPE)
        }
        if (data[2].toInt() == 0x04 && data[3].toInt() == 0x02 && data[4].toInt() == 0x01) {
            return if (data[6].toInt() == 0x0F) {
                NtagTagType.NTag213
            } else if (data[6].toInt() == 0x11) {
                NtagTagType.NTag215
            } else if (data[6].toInt() == 0x13) {
                NtagTagType.NTag216
            } else {
                throw NfcChipReadError(ChipReadError.UNKNOWN_CHIP_TYPE)
            }
        } else if (data[2].toInt() == 0x03 && data[4].toInt() == 0x04) {
            return UltralightAESTagType.UltralightAES
        }
        throw NfcChipReadError(ChipReadError.UNKNOWN_CHIP_TYPE)
    }
}