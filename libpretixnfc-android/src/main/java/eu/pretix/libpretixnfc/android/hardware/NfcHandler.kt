package eu.pretix.libpretixnfc.android.hardware

import Mf0aesKeySet
import PretixMf0aes
import android.app.Activity
import eu.pretix.libpretixnfc.android.BuildConfig
import eu.pretix.libpretixnfc.communication.AbstractNfcA

import eu.pretix.libpretixnfc.communication.ChipReadError
import eu.pretix.libpretixsync.db.ReusableMediaType
import java.lang.RuntimeException

class NfcUnsupported : Exception()
class NfcDisabled : Exception()

interface NfcHandler {
    interface OnChipReadListener {
        fun chipReadSuccessfully(identifier: String, mediaType: ReusableMediaType)
        fun chipReadError(error: ChipReadError, identifier: String?)
    }

    /**
     * Start listening for chips. Should e.g. be called in Activity.onResume().
     */
    @Throws(NfcDisabled::class, NfcUnsupported::class)
    fun start(mediaTypes: List<ReusableMediaType>)

    /**
     * Stop listening for chips. Should e.g. be called in Activity.onPause().
     */
    fun stop()

    fun isRunning(): Boolean

    fun getMediaTypes(): List<ReusableMediaType>?

    /**
     * Set the callback to be fired whenever a chip is read.
     */
    fun setOnChipReadListener(listener: OnChipReadListener?)
}


fun getNfcHandler(activity: Activity, keySets: List<Mf0aesKeySet>, useRandomIdForNewTags: Boolean, mode: NfcHandlerMode = NfcHandlerMode.DEFAULT, nfcReaderType: String): NfcHandler {
    return when (nfcReaderType) {
        "acs" -> AcsNfcHandler(activity, keySets, useRandomIdForNewTags, mode)
        "native" -> AndroidNativeNfcHandler(activity, keySets, useRandomIdForNewTags, mode)
        else -> throw RuntimeException("Unknown NFC reader type ${nfcReaderType}")
    }
}

fun processMf0aes(keySets: List<Mf0aesKeySet>, mode: NfcHandlerMode, useRandomIdForNewTags: Boolean = false, nfca: AbstractNfcA): String {
    return PretixMf0aes(
        keySets,
        useRandomIdForNewTags,
        BuildConfig.DEBUG,
    ).process(
        nfca,
        encodeWith = if (mode == NfcHandlerMode.ENCODE) keySets.firstOrNull { it.canEncode } else null)
}
