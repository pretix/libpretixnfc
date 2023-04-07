package eu.pretix.libpretixnfc.communication

interface AbstractNfcA {
    fun connect()
    fun transceive(data: ByteArray): ByteArray?
    fun close()
}
