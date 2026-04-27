package eu.pretix.libpretixnfc.android.hardware.acs

import android.annotation.SuppressLint
import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.PendingIntentCompat
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import com.acs.smartcard.Reader
import eu.pretix.libpretixnfc.android.R
import eu.pretix.libpretixnfc.android.hardware.AcsNfcHandler
import eu.pretix.libpretixnfc.android.launchWithSentry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

class AcsReaderService : Service() {
    /*
    Tested with ACR1252U

    API docs: https://www.acs.com.hk/download-manual/6402/API-ACR1252U-1.17.pdf
     */

    private val binder = LocalBinder()
    val notificationChannel = "libpretixnfc_android:AcsReaderService"
    val description = "ACS reader Service"
    val notificationId = 40182724
    public var cardHandler: ((Reader, Int) -> Unit)? = null
    private var reader: Reader? = null
    val VENDOR_ID = 0x072f
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /**
     * Class used for the client Binder.  Because we know this service always
     * runs in the same process as its clients, we don't need to deal with IPC.
     */
    inner class LocalBinder : Binder() {
        // Return this instance of LocalService so clients can call public methods
        fun getService(): AcsReaderService = this@AcsReaderService
    }

    private fun readerConfig() {
        // Disable auto buzzer
        val command =
            byteArrayOf(0xE0.toByte(), 0x00, 0x00, 0x21, 0x01, 0b01100111.toByte())
        val responseBuffer = ByteArray(300)
        reader!!.control(
            0,
            Reader.IOCTL_CCID_ESCAPE,
            command,
            command.size,
            responseBuffer,
            responseBuffer.size,
        )
    }

    private fun setup() {
        val manager = getSystemService(USB_SERVICE) as UsbManager
        if (reader == null) {
            reader = Reader(manager)
            var firstStateChange = true

            reader!!.setOnStateChangeListener { slotNum, prevState, currState ->
                Log.i(
                    AcsNfcHandler.Companion.TAG,
                    "onStateChange slot=$slotNum prevState=$prevState currState=$currState",
                )
                if (firstStateChange) {
                    firstStateChange = false
                    scope.launchWithSentry {
                        readerConfig()
                    }
                }
                if (slotNum == 0 && prevState == Reader.CARD_ABSENT && currState == Reader.CARD_PRESENT) {
                    scope.launchWithSentry {
                        cardHandler?.invoke(reader!!, slotNum)
                    }
                }
            }

            findAndConnectDevice()

            val usbFilter = IntentFilter()
            usbFilter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
            usbFilter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)

            ContextCompat.registerReceiver(
                this,
                object : BroadcastReceiver() {
                    override fun onReceive(
                        p0: Context,
                        intent: Intent,
                    ) {
                        val device = IntentCompat.getParcelableExtra(intent, "device", UsbDevice::class.java) ?: return
                        if (intent.action == UsbManager.ACTION_USB_DEVICE_ATTACHED && device.vendorId == VENDOR_ID) {
                            Log.i(
                                AcsNfcHandler.Companion.TAG,
                                "device attached",
                            )
                            connectDevice(device)
                        }
                    }
                },
                usbFilter,
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
        }
    }

    private fun findAndConnectDevice() {
        val manager = getSystemService(USB_SERVICE) as UsbManager
        for (dev in manager.deviceList.values) {
            if (dev.vendorId == VENDOR_ID) {
                connectDevice(dev)
                return
            }
        }
    }

    @SuppressLint("UnspecifiedImmutableFlag")
    private fun connectDevice(dev: UsbDevice) {
        val manager = getSystemService(USB_SERVICE) as UsbManager
        val intent = Intent("eu.pretix.libpretixnfc.android.hardware.nfc.USB_PERMISSION")
        intent.setPackage(packageName)
        val permissionIntent = PendingIntentCompat.getBroadcast(
            this,
            0,
            intent,
            0,
            true
        )

        val filter = IntentFilter("eu.pretix.libpretixnfc.android.hardware.nfc.USB_PERMISSION")
        ContextCompat.registerReceiver(
            this,
            object : BroadcastReceiver() {
                override fun onReceive(
                    ctx: Context,
                    intent: Intent,
                ) {
                    val device = IntentCompat.getParcelableExtra(intent, "device", UsbDevice::class.java)
                    if (!intent.getBooleanExtra("permission", false)) {
                        // show error
                    } else if (device == null) {
                        // show error
                    } else {
                        reader!!.open(device)
                    }
                }
            },
            filter,
            ContextCompat.RECEIVER_EXPORTED,
        )
        manager.requestPermission(dev, permissionIntent)
    }

    override fun onBind(intent: Intent): IBinder {
        setup()
        return binder
    }

    override fun onUnbind(intent: Intent?): Boolean {
        return true
    }

    private fun _startForeground() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel =
                NotificationChannel(
                    notificationChannel,
                    description,
                    NotificationManager.IMPORTANCE_LOW,
                )
            channel.setSound(null, null)
            channel.enableVibration(false)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        //val startBaseActivity = Intent(this, MainActivity::class.java)
        val startBaseActivity = Intent(this, this::class.java)
        startBaseActivity.action = Intent.ACTION_MAIN
        startBaseActivity.addCategory(Intent.CATEGORY_LAUNCHER)
        startBaseActivity.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        val keepAliveNotification: Notification =
            NotificationCompat.Builder(this, notificationChannel)
                .setContentTitle(getString(R.string.nfc_connected_notification))
                .setSmallIcon(R.drawable.ic_nfc_white_24dp)
                .setContentIntent(
                    PendingIntent.getActivity(
                        this,
                        0,
                        startBaseActivity,
                        if (Build.VERSION.SDK_INT >= 23) {
                            PendingIntent.FLAG_IMMUTABLE
                        } else {
                            0
                        },
                    ),
                )
                .build()
        this.startForeground(notificationId, keepAliveNotification)
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        _startForeground()
        return START_NOT_STICKY
    }

    override fun onRebind(intent: Intent?) {
        setup()
        super.onRebind(intent)
    }

    override fun onCreate() {
        super.onCreate()
        _startForeground()
    }

    override fun onDestroy() {
        reader?.close()
        reader = null
        scope.cancel()
        super.onDestroy()
    }
}
