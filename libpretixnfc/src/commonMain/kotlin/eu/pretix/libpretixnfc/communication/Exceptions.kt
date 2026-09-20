package eu.pretix.libpretixnfc.communication

open class NfcIOError(message: String) : Exception(message)
class NfcChipReadError(val errorType: ChipReadError) : NfcIOError(errorType.toString())

/**
 * The Android NFC stack raises this when a tag is no longer in range. On the JVM this is
 * `java.lang.SecurityException`.
 */
internal expect class SecurityException : RuntimeException
