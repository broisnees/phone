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

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> main.post { apply() } }

    /** Reconcile registrations with the current settings. */
    fun apply() {
        Engine.ceiling = if (faceDown) 100 else settings.brightness
        Engine.torchLevel = settings.torchLevel
        toggle("vol", settings.volumeIndicator, ::volumeOn, ::volumeOff)
        toggle("bright", settings.brightnessIndicator, ::brightnessOn, ::brightnessOff)
        toggle("calls", settings.calls, ::callsOn, ::callsOff)
        toggle("charge", settings.chargingMeter, ::chargeOn, ::chargeOff)
        toggle("viz", settings.visualizer, ::vizOn, ::vizOff)
        toggle("flip", settings.faceDown, ::flipOn, ::flipOff)
        updateAmbient()
        if (!settings.anyFeature() && !Engine.isActive(Layer.Torch) && timerEnd == 0L) stopSelf()
    }

    private val enabled = HashSet<String>()
    private fun toggle(key: String, on: Boolean, start: () -> Unit, stop: () -> Unit) {
        if (on && enabled.add(key)) runCatching(start).onFailure { Log.w(TAG, "start failed", it) }
        if (!on && enabled.remove(key)) runCatching(stop)
    }

    private fun disableAll() {
        volumeOff(); brightnessOff(); callsOff(); chargeOff(); vizOff(); flipOff()
        enabled.clear()
    }

    // ================================================================ Volume & brightness indicator

    /** Set when Haptix's instant per-step signal is arriving; the slower system broadcast is then ignored. */
    @Volatile private var lastFastLevelAt = 0L

    /** Instant path: Haptix sends the level from SystemUI's volume controller on each step. */
    private val fastVolumeReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, i: Intent) {
            val level = i.getIntExtra("level", -1)
            val max = i.getIntExtra("max", 1).coerceAtLeast(1)
            if (level < 0) return
            lastFastLevelAt = android.os.SystemClock.uptimeMillis()
            showLevel(level.toFloat() / max)
        }
    }

    private val volumeReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, i: Intent) {
            // Stale copies of steps the fast path already showed would flicker an older level.
            if (android.os.SystemClock.uptimeMillis() - lastFastLevelAt < 3000) return
            val stream = i.getIntExtra("android.media.EXTRA_VOLUME_STREAM_TYPE", AudioManager.STREAM_MUSIC)
            val value = i.getIntExtra("android.media.EXTRA_VOLUME_STREAM_VALUE", -1)
            val prev = i.getIntExtra("android.media.EXTRA_PREV_VOLUME_STREAM_VALUE", -1)
            if (value < 0 || value == prev) return
            val maxV = audio.getStreamMaxVolume(stream).coerceAtLeast(1)
            showLevel(value.toFloat() / maxV)
        }
    }

    private fun volumeOn() {
        registerReceiver(volumeReceiver, IntentFilter("android.media.VOLUME_CHANGED_ACTION"), RECEIVER_NOT_EXPORTED)
        // Exported so SystemUI (Haptix) can reach it; the payload can only light the strip.
        registerReceiver(fastVolumeReceiver, IntentFilter("com.nees.glyph.LEVEL"), RECEIVER_EXPORTED)
    }

    private fun volumeOff() {
        runCatching { unregisterReceiver(volumeReceiver) }
        runCatching { unregisterReceiver(fastVolumeReceiver) }
    }

    private var lastBrightness = -1
    private val brightnessObserver = object : ContentObserver(main) {
        override fun onChange(selfChange: Boolean) {
            if (!power.isInteractive) return
            val v = SysSettings.System.getInt(contentResolver, SysSettings.System.SCREEN_BRIGHTNESS, -1)
            if (v < 0 || v == lastBrightness) return
            lastBrightness = v
            showLevel(v / 255f, pulseRing = true)
        }
    }

    private fun brightnessOn() {
        lastBrightness = SysSettings.System.getInt(contentResolver, SysSettings.System.SCREEN_BRIGHTNESS, -1)
        contentResolver.registerContentObserver(SysSettings.System.getUriFor(SysSettings.System.SCREEN_BRIGHTNESS), false, brightnessObserver)
    }

    private fun brightnessOff() { contentResolver.unregisterContentObserver(brightnessObserver) }

    /** Level on the bottom strip for 1.5 s, then a 300 ms fade. */
    private fun showLevel(fraction: Float, pulseRing: Boolean = false) {
        val f = Engine.stripLevel(fraction)
        if (pulseRing) Zone.ring.forEach { f[it.ordinal] = 25 }
        Engine.flash(Layer.Indicator, f, 1500)
    }

    // ================================================================ Calls

    private val callReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, i: Intent) {
            when (i.getStringExtra(TelephonyManager.EXTRA_STATE)) {
                TelephonyManager.EXTRA_STATE_RINGING -> {
                    // The broadcast arrives twice; only the copy with the number carries caller identity.
                    val number = i.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER) ?: return
                    val pattern = settings.pattern(contactKey(number)?.let(settings::contactPattern) ?: settings.defaultCallPattern)
                    if (pattern != null) Engine.play(Layer.Call, pattern, loop = true)
                }
                else -> Engine.clear(Layer.Call)
            }
        }
    }

    private fun contactKey(number: String): String? = runCatching {
        val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
        contentResolver.query(uri, arrayOf(ContactsContract.PhoneLookup.LOOKUP_KEY), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        }
    }.getOrNull()

    private fun callsOn() { registerReceiver(callReceiver, IntentFilter(TelephonyManager.ACTION_PHONE_STATE_CHANGED)) }
    private fun callsOff() { runCatching { unregisterReceiver(callReceiver) }; Engine.clear(Layer.Call) }

    // ================================================================ Charging meter & battery

    private var lastPct = -1
    private var plugged = false
    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, i: Intent) {
            val pct = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) * 100 / i.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
            val nowPlugged = i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
            val justPlugged = nowPlugged && !plugged
            plugged = nowPlugged
            if (justPlugged || (plugged && pct != lastPct && lastPct >= 0)) showBattery(2500, pct)
            lastPct = pct
        }
    }

    private fun chargeOn() {
        plugged = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
        registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    }

    private fun chargeOff() { runCatching { unregisterReceiver(batteryReceiver) } }

    /** Battery % on the strip; while charging the next segment breathes. */
    fun showBattery(ms: Long, pctArg: Int = -1) {
        val pct = if (pctArg >= 0) pctArg else getSystemService(BatteryManager::class.java).getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val charging = getSystemService(BatteryManager::class.java).isCharging
        val base = Engine.stripLevel(pct / 100f)
        val next = Zone.strip.getOrNull((pct * Zone.strip.size / 100).coerceAtMost(Zone.strip.lastIndex))
        val end = SystemClock.uptimeMillis() + ms
        Engine.set(Layer.Meter) { now ->
            if (now >= end) null else base.copyOf().also { f ->
                if (charging && next != null) f[next.ordinal] = (50 + 50 * sin(now / 300.0)).toInt()
                f[Zone.E.ordinal] = if (charging) 100 else 0
            }
        }
    }

    // ================================================================ Timer

    fun startTimer(minutes: Int) {
        val total = minutes * 60_000L
        timerEnd = SystemClock.uptimeMillis() + total
        Engine.set(Layer.Timer) { now ->
            val left = timerEnd - now
            if (left <= 0) {
                timerEnd = 0
                main.post {
                    settings.pattern("double")?.let { Engine.play(Layer.Notification, Pattern("done", "Done", List(3) { _ -> it.steps }.flatten())) }
                    TimerTile.refresh(this)
                    apply()
                }
                null
            } else Engine.stripLevel(left.toFloat() / total, 40)
        }
        TimerTile.refresh(this)
    }

    fun cancelTimer() {
        timerEnd = 0
        Engine.clear(Layer.Timer)
        TimerTile.refresh(this)
        apply()
    }

    // ================================================================ Torch

    fun setTorch(on: Boolean) {
        if (on) Engine.set(Layer.Torch) { IntArray(Zone.COUNT) { 100 } } else Engine.clear(Layer.Torch)
        TorchTile.refresh(this)
        if (!on) main.post { apply() }
    }

    // ================================================================ Music visualizer

