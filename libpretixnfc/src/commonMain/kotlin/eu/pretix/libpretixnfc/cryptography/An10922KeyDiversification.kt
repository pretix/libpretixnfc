package eu.pretix.libpretixnfc.cryptography

expect class An10922KeyDiversification() {
    // https://www.nxp.com/docs/en/application-note/AN10922.pdf

    fun generateDiversifiedKeyAES128(masterKey: ByteArray, uid: ByteArray, applicationId: ByteArray, systemId: ByteArray): ByteArray
}

/**
 * Runs the AN10922 AES-128 diversification with [aesCmac] as the CMAC implementation, so that every
 * platform shares the exact same diversification input.
 */
internal fun diversifyKeyAES128(
    masterKey: ByteArray,
    uid: ByteArray,
    applicationId: ByteArray,
    systemId: ByteArray,
    aesCmac: (ByteArray, ByteArray) -> ByteArray,
): ByteArray {
    check(masterKey.size == 16)

    val diversificationInput = byteArrayOf(0x01) + uid + applicationId + systemId
    check (diversificationInput.size <= 32)
    check (diversificationInput.size >= 15)  // otherwise we'd need to implement CMAC ourselves

    /*var padded = false
    if (diversificationInput.size < 32) {
        padded = true
        val padding = ByteArray(32 - diversificationInput.size)
        padding[0] = 0x80.toByte()
        diversificationInput += padding
    }*/

    val cmac = aesCmac(masterKey, diversificationInput)
    return cmac
}
