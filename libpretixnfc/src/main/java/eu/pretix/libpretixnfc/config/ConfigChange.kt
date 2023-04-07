package eu.pretix.libpretixnfc.config

import eu.pretix.libpretixnfc.communication.AbstractNfcA

abstract class ConfigChange {
    data class PageChange(
        val pageAddress: Byte,
        val data: ByteArray
    ) {
        init {
            check(data.size == 4)
        }
    }

    abstract val changes: List<PageChange>
    abstract fun write(nfca: AbstractNfcA)
}