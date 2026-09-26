package com.nees.glyph

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.database.ContentObserver
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioManager
import android.media.audiofx.Visualizer
import android.net.Uri
import android.os.BatteryManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.provider.ContactsContract
import android.provider.Settings as SysSettings
import android.telephony.TelephonyManager
import android.util.Log
import kotlin.math.PI
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin

/**
 * Runs the Glyph features. Each feature only registers its receiver/observer/sensor while it is
 * enabled; with every feature off the service stops itself.
 */
class GlyphService : Service() {
    private lateinit var settings: Settings
    private val main = Handler(Looper.getMainLooper())
    private val audio by lazy { getSystemService(AudioManager::class.java) }
    private val power by lazy { getSystemService(PowerManager::class.java) }

    override fun onCreate() {
        super.onCreate()
        instance = this
        settings = Settings(this)
        startForeground(NOTIF_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        settings.listen(prefListener)
        registerReceiver(screenReceiver, IntentFilter().apply { addAction(Intent.ACTION_SCREEN_ON); addAction(Intent.ACTION_SCREEN_OFF) })
        apply()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TORCH -> setTorch(!Engine.isActive(Layer.Torch))
            ACTION_BATTERY -> showBattery(3000)
            ACTION_TIMER -> if (timerEnd > 0) cancelTimer() else startTimer(settings.timerMinutes)
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        settings.unlisten(prefListener)
        runCatching { unregisterReceiver(screenReceiver) }
        disableAll()
        Engine.ceiling = settings.brightness
        Hw.off()
        instance = null
        super.onDestroy()
    }

