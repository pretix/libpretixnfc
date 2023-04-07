package eu.pretix.libpretixnfc.tagtypes

class NtagTagType(
    chipName: String,
    val size: Int,
    userDataSize: Int,
    val userDataStartIndex: Int,
    val userDataEndIndex: Int,
    val configDataStartIndex: Int,
    val configDataEndIndex: Int,
    val numberPages: Int
) : TagType(chipName, userDataSize) {

    val userDataStartPage: Int
        get() = userDataStartIndex / 4
    val configDataStartPage: Int
        get() = configDataStartIndex / 4

    companion object {
        val NTag213 = NtagTagType("NTAG213", 180, 144, 16, 156, 164, 180, 45)
        val NTag215 = NtagTagType("NTAG215", 540, 504, 16, 516, 524, 540, 135)
        val NTag216 = NtagTagType("NTAG216", 924, 888, 16, 900, 908, 924, 231)
    }
}
