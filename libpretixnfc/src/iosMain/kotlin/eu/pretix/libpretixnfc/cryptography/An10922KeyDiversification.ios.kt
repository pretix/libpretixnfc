package eu.pretix.libpretixnfc.cryptography

actual class An10922KeyDiversification {
    // https://www.nxp.com/docs/en/application-note/AN10922.pdf

    actual fun generateDiversifiedKeyAES128(masterKey: ByteArray, uid: ByteArray, applicationId: ByteArray, systemId: ByteArray): ByteArray {
        return diversifyKeyAES128(masterKey, uid, applicationId, systemId, ::computeAesCmac)
    }
}
