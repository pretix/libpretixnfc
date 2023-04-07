package eu.pretix.libpretixnfc.tagtypes

class UltralightAESTagType(
    chipName: String,
    userDataSize: Int,
) : TagType(chipName, userDataSize) {

    companion object {
        val UltralightAES = UltralightAESTagType("UltralightAES", 144)
    }
}
