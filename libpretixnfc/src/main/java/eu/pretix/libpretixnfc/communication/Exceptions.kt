package eu.pretix.libpretixnfc.communication

open class NfcIOError(message: String) : Exception(message)
class NfcChipReadError(val errorType: ChipReadError) : NfcIOError(errorType.toString())

