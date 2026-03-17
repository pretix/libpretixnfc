package eu.pretix.libpretixnfc.android.hardware

import Mf0aesKeySet
import android.app.Activity
import android.content.Context
import android.nfc.NfcAdapter
import android.nfc.NfcAdapter.FLAG_READER_NFC_A
import android.nfc.NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK
import android.nfc.Tag
import android.nfc.tech.NfcA
import android.os.Bundle
import android.os.Vibrator
import android.util.Log
import eu.pretix.libpretinfc.android.BuildConfig
import eu.pretix.libpretixnfc.communication.AbstractNfcA
import eu.pretix.libpretixnfc.communication.ChipReadError
import eu.pretix.libpretixnfc.communication.NfcChipReadError
import eu.pretix.libpretixnfc.communication.NfcIOError
import eu.pretix.libpretixnfc.toHexString
import eu.pretix.libpretixsync.db.ReusableMediaType
import eu.pretix.libpretixui.android.utils.doAsyncSentry
import io.sentry.Sentry
import java.io.IOException


private var lastTagId: String = ""
private var lastTagTime: Long = 0


enum class NfcHandlerMode {
    DEFAULT,
    ENCODE
}


class AndroidNativeNfcHandler(
    private val ctx: Activity,
    private val keySets: List<Mf0aesKeySet>,
    private val useRandomIdForNewTags: Boolean,
    private val mode: NfcHandlerMode = NfcHandlerMode.DEFAULT
) : NfcHandler, NfcAdapter.ReaderCallback {
    private var chipReadListener: NfcHandler.OnChipReadListener? = null
    private var nfcAdapter: NfcAdapter? = NfcAdapter.getDefaultAdapter(ctx)
    private var mediaTypes: List<ReusableMediaType>? = null
    private val buzzer =
        ctx.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator?
    private var running = false

    val supportedTypes = listOf(ReusableMediaType.NFC_UID, ReusableMediaType.NFC_MF0AES)

    companion object {
        val TAG = "AndroidNativeNfcHandler"
    }

    override fun start(mediaTypes: List<ReusableMediaType>) {
        if (!supportedTypes.any { mediaTypes.contains(it) }) {
            return
        }

        this.mediaTypes = mediaTypes
        Log.i(TAG, "start (${mediaTypes.joinToString(", ")}) @$ctx")
        if (nfcAdapter == null) {
            throw NfcUnsupported()
        }
        if (!nfcAdapter!!.isEnabled) {
            throw NfcDisabled()
        }

        nfcAdapter!!.enableReaderMode(
            ctx,
            this,
            FLAG_READER_SKIP_NDEF_CHECK or FLAG_READER_NFC_A,
            Bundle().apply {
                putInt(NfcAdapter.EXTRA_READER_PRESENCE_CHECK_DELAY, 500)
            }
        )
        running = true
    }

    override fun getMediaTypes(): List<ReusableMediaType>? {
        return mediaTypes
    }

    override fun stop() {
        nfcAdapter?.disableReaderMode(ctx)
        running = false
        Log.i(TAG, "stop @$ctx")
    }

    override fun isRunning(): Boolean {
        return running
    }

    override fun setOnChipReadListener(listener: NfcHandler.OnChipReadListener?) {
        chipReadListener = listener
    }

    override fun onTagDiscovered(tag: Tag) {
        Log.i(TAG, "onTagDiscovered @$ctx")
        if (tag.hexId() == lastTagId && lastTagTime > System.currentTimeMillis() - 2000) {
            // Debounce duplicate reads
            Log.i(TAG, "debounced @$ctx")
            return
        }

        lastTagId = tag.hexId()
        lastTagTime = System.currentTimeMillis()

        if (mediaTypes?.contains(ReusableMediaType.NFC_UID) == true) {
            ctx.runOnUiThread {
                buzzer?.vibrate(125)
                chipReadListener?.chipReadSuccessfully(tag.hexId(), ReusableMediaType.NFC_UID)
            }
        } else if (mediaTypes?.contains(ReusableMediaType.NFC_MF0AES) == true) {
            doAsyncSentry {
                val identifier = try {
                    val nfca = AndroidNfcA(tag)
                    processMf0aes(keySets, mode, useRandomIdForNewTags, nfca)
                } catch (e: NfcChipReadError) {
                    ctx.runOnUiThread {
                        buzzer?.vibrate(125)
                        chipReadListener?.chipReadError(e.errorType, tag.hexId())
                    }
                    return@doAsyncSentry
                } catch (e: NfcIOError) {
                    e.printStackTrace()
                    ctx.runOnUiThread {
                        buzzer?.vibrate(125)
                        chipReadListener?.chipReadError(ChipReadError.IO_ERROR, tag.hexId())
                    }
                    return@doAsyncSentry
                } catch (e: IOException) {
                    e.printStackTrace()
                    ctx.runOnUiThread {
                        buzzer?.vibrate(125)
                        chipReadListener?.chipReadError(ChipReadError.IO_ERROR, tag.hexId())
                    }
                    return@doAsyncSentry
                } catch (e: Exception) {
                    e.printStackTrace()
                    Sentry.captureException(e)
                    ctx.runOnUiThread {
                        buzzer?.vibrate(125)
                        chipReadListener?.chipReadError(ChipReadError.UNKNOWN_ERROR, tag.hexId())
                    }
                    return@doAsyncSentry
                }
                ctx.runOnUiThread {
                    buzzer?.vibrate(125)
                    chipReadListener?.chipReadSuccessfully(identifier, ReusableMediaType.NFC_MF0AES)
                }
            }
        }
    }

    class AndroidNfcA(val tag: Tag) : AbstractNfcA {
        val raw = NfcA.get(tag) ?: throw NfcChipReadError(ChipReadError.UNKNOWN_CHIP_TYPE)

        override fun connect() {
            raw.connect()
        }

        override fun transceive(data: ByteArray): ByteArray {
            if (BuildConfig.DEBUG) {
                Log.i("NFC TRANSMISSION", "=> " + data.toHexString(true))
            }
            val reply = raw.transceive(data)
            if (BuildConfig.DEBUG) {
                Log.i("NFC TRANSMISSION", "<= " + reply.toHexString(true))
            }
            return reply
        }

        override fun close() {
            raw.close()
        }
    }
}

fun Tag.hexId(): String = id.joinToString("") { "%02x".format(it).uppercase() }
