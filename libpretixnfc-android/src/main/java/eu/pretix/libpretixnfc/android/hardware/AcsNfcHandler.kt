package eu.pretix.libpretixnfc.android.hardware

import Mf0aesKeySet
import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import com.acs.smartcard.Reader
import com.acs.smartcard.ReaderException
import eu.pretix.libpretixnfc.android.BuildConfig
import eu.pretix.libpretixnfc.communication.AbstractNfcA
import eu.pretix.libpretixnfc.communication.ChipReadError
import eu.pretix.libpretixnfc.communication.NfcChipReadError
import eu.pretix.libpretixnfc.communication.NfcIOError
import eu.pretix.libpretixsync.db.ReusableMediaType
import eu.pretix.libpretixnfc.android.hardware.acs.AcsReaderService
import eu.pretix.libpretixui.android.utils.doAsyncSentry
import io.sentry.Sentry
import java.io.IOException


private var lastTagId: String = ""
private var lastTagTime: Long = 0

class AcsNfcHandler(
    private val ctx: Activity,
    private val keySets: List<Mf0aesKeySet>,
    private val useRandomIdForNewTags: Boolean,
    private val mode: NfcHandlerMode = NfcHandlerMode.DEFAULT
) : NfcHandler, (Reader, Int) -> Unit {
    private var mediaTypes: List<ReusableMediaType>? = null
    private var running = false
    private var readerService: AcsReaderService? = null
    private var chipReadListener: NfcHandler.OnChipReadListener? = null

    val supportedTypes = listOf(ReusableMediaType.NFC_UID, ReusableMediaType.NFC_MF0AES)

    companion object {
        val TAG = "AcsNfcHandler"
    }

    private val readerConnection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as AcsReaderService.LocalBinder
            readerService = binder.getService()
            readerService!!.cardHandler = this@AcsNfcHandler
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            readerService = null
        }
    }

    override fun start(mediaTypes: List<ReusableMediaType>) {
        if (!supportedTypes.any { mediaTypes.contains(it) }) {
            return
        }

        this.mediaTypes = mediaTypes
        Log.i(TAG, "start (${mediaTypes.joinToString(", ")}) @$ctx")

        running = true

        Intent(ctx, AcsReaderService::class.java).also { intent ->
            ctx.bindService(intent, readerConnection, Context.BIND_AUTO_CREATE)
        }
    }

    override fun getMediaTypes(): List<ReusableMediaType>? {
        return mediaTypes
    }

    override fun setOnChipReadListener(listener: NfcHandler.OnChipReadListener?) {
        chipReadListener = listener
    }

    override fun stop() {
        running = false
        readerService?.cardHandler = null
        Log.i(TAG, "stop @$ctx")
    }

    override fun isRunning(): Boolean {
        return running
    }

    private fun readUid(reader: Reader, slotNum: Int): String {
        val command =
            byteArrayOf(0xFF.toByte(), 0xCA.toByte(), 0x00, 0x00, 0x00)  // Read UID
        val responseBuffer = ByteArray(300)
        val responseLength = reader!!.transmit(
            slotNum,
            command,
            command.size,
            responseBuffer,
            responseBuffer.size
        )
        val response = responseBuffer.copyOfRange(0, responseLength - 2)
        val hexId = response.joinToString("") { "%02x".format(it).uppercase() }
        return hexId
    }

    fun beep(reader: Reader) {
        val duration = 10 // * 10ms
        // Set buzzer to only buzz on cart insertion, not removal
        val command =
            byteArrayOf(0xE0.toByte(), 0x00, 0x00, 0x28, 0x01, duration.toByte())
        val responseBuffer = ByteArray(300)
        if (BuildConfig.DEBUG) {
            Log.i(TAG, "beep")
        }
        reader.control(
            0,
            Reader.IOCTL_CCID_ESCAPE,
            command,
            command.size,
            responseBuffer,
            responseBuffer.size
        )
    }

    override fun invoke(reader: Reader, slotNum: Int) {
        val nfca = AcsNfcA(reader, slotNum)
        nfca.connect()

        val hexId = readUid(reader, slotNum)
        if (hexId == lastTagId && lastTagTime > System.currentTimeMillis() - 2000) {
            // Debounce duplicate reads
            Log.i(TAG, "debounced @$ctx")
            return
        }

        try {
            if (mediaTypes?.contains(ReusableMediaType.NFC_UID) == true) {
                Log.i(TAG, "found tag with id $hexId")

                lastTagId = hexId
                lastTagTime = System.currentTimeMillis()

                ctx.runOnUiThread {
                    chipReadListener?.chipReadSuccessfully(hexId, ReusableMediaType.NFC_UID)
                }
                beep(reader)
            } else if (mediaTypes?.contains(ReusableMediaType.NFC_MF0AES) == true) {
                doAsyncSentry {
                    val identifier = try {
                        val nfca = AcsNfcA(reader, slotNum)
                        processMf0aes(keySets, mode, useRandomIdForNewTags, nfca)
                    } catch (e: NfcChipReadError) {
                        ctx.runOnUiThread {
                            chipReadListener?.chipReadError(e.errorType, hexId)
                        }
                        beep(reader)
                        return@doAsyncSentry
                    } catch (e: NfcIOError) {
                        e.printStackTrace()
                        ctx.runOnUiThread {
                            chipReadListener?.chipReadError(ChipReadError.IO_ERROR, hexId)
                        }
                        beep(reader)
                        return@doAsyncSentry
                    } catch (e: IOException) {
                        e.printStackTrace()
                        ctx.runOnUiThread {
                            chipReadListener?.chipReadError(ChipReadError.IO_ERROR, hexId)
                        }
                        beep(reader)
                        return@doAsyncSentry
                    } catch (e: Exception) {
                        e.printStackTrace()
                        Sentry.captureException(e)
                        ctx.runOnUiThread {
                            chipReadListener?.chipReadError(
                                ChipReadError.UNKNOWN_ERROR,
                                hexId
                            )
                        }
                        beep(reader)
                        return@doAsyncSentry
                    }
                    ctx.runOnUiThread {
                        if (mode == NfcHandlerMode.DEFAULT) {
                            lastTagId = hexId
                            lastTagTime = System.currentTimeMillis()
                        }

                        chipReadListener?.chipReadSuccessfully(
                            identifier,
                            ReusableMediaType.NFC_MF0AES
                        )
                    }
                    beep(reader)
                }
            }


        } catch (e: ReaderException) {
            e.printStackTrace()
        }
    }

    class AcsNfcA(val reader: Reader, val slotNum: Int) : AbstractNfcA {
        var connected = false

        override fun connect() {
            if (!connected) {
                if (BuildConfig.DEBUG) {
                    Log.i("NfcTest", "reader.power()")
                }
                reader.power(slotNum, Reader.CARD_WARM_RESET)
                if (BuildConfig.DEBUG) {
                    Log.i("NfcTest", "reader.setProtocol()")
                }
                reader.setProtocol(slotNum, Reader.PROTOCOL_T0 or Reader.PROTOCOL_T1)
                connected = true
            }
        }

        @OptIn(ExperimentalStdlibApi::class)
        override fun transceive(data: ByteArray): ByteArray {
            check(data.size <= 255)
            val command = byteArrayOf(0xFF.toByte(), 0x00, 0x00, 0x00, data.size.toByte()) + data
            val responseBuffer = ByteArray(300)
            if (BuildConfig.DEBUG) {
                Log.i("NfcTest", "reader.transmit(${command.toHexString()})")
            }
            val responseLength = reader.transmit(
                slotNum,
                command,
                command.size,
                responseBuffer,
                responseBuffer.size
            )
            val response = responseBuffer.copyOfRange(0, responseLength - 2)
            return response
        }

        override fun close() {
        }
    }
}
