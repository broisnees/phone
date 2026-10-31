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
        Buzz.init(this)
        startForeground(NOTIF_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        // The notification listener keeps this process alive, so the "running" notification can go.
        if (NotifListener.connected) dropForeground()
        settings.listen(prefListener)
        registerReceiver(screenReceiver, IntentFilter().apply { addAction(Intent.ACTION_SCREEN_ON); addAction(Intent.ACTION_SCREEN_OFF) })
        apply()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Every startForegroundService() call must be answered with startForeground(), even when
        // the service already exists; then the notification is dropped again if the listener keeps us alive.
        startForeground(NOTIF_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        if (NotifListener.connected) dropForeground()
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
        Engine.paused = settings.paused
        vizEngine.sensitivity = settings.vizGain
        vizEngine.style = settings.vizStyle
        vizEngine.onKick = if (settings.vizHaptics) ({ Buzz.fire(Buzz.TICK, android.os.VibrationAttributes.USAGE_MEDIA, minGapMs = 150) }) else null
        toggle("unlock", settings.unlockAnim, {
            registerReceiver(unlockReceiver, IntentFilter(Intent.ACTION_USER_PRESENT))
            // Nees Launcher pings this when you open it / the app drawer.
            registerReceiver(sweepReceiver, IntentFilter("com.nees.glyph.SWEEP"), RECEIVER_EXPORTED)
        }, {
            runCatching { unregisterReceiver(unlockReceiver) }
            runCatching { unregisterReceiver(sweepReceiver) }
        })
        toggle("busy", settings.busyDot, ::privacyOn, ::privacyOff)
        updateBusy()
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
        volumeOff(); brightnessOff(); callsOff(); chargeOff(); vizOff(); flipOff(); privacyOff()
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
            val up = level >= lastVolume
            lastVolume = level
            showLevel(level.toFloat() / max, settings.volumeStyle, settings.volumeEffect, up)
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
            showLevel(value.toFloat() / maxV, settings.volumeStyle, settings.volumeEffect, prev < 0 || value >= prev)
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
            val up = v > lastBrightness
            lastBrightness = v
            showLevel(v / 255f, settings.brightnessStyle, settings.brightnessEffect, up)
        }
    }

    private fun brightnessOn() {
        lastBrightness = SysSettings.System.getInt(contentResolver, SysSettings.System.SCREEN_BRIGHTNESS, -1)
        contentResolver.registerContentObserver(SysSettings.System.getUriFor(SysSettings.System.SCREEN_BRIGHTNESS), false, brightnessObserver)
    }

    private fun brightnessOff() { contentResolver.unregisterContentObserver(brightnessObserver) }

    /** Level in the chosen style for 1.5 s, then a 300 ms fade. */
    private var lastVolume = -1

    private fun showLevel(fraction: Float, style: LevelStyle, effect: PressEffect, up: Boolean) {
        Engine.flashWithEffect(Layer.Indicator, Engine.level(fraction, style), 1500, effect, up)
    }

    // ================================================================ Calls

    private val callReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, i: Intent) {
            when (i.getStringExtra(TelephonyManager.EXTRA_STATE)) {
                TelephonyManager.EXTRA_STATE_RINGING -> {
                    // The broadcast arrives twice; only the copy with the number carries caller identity.
                    val number = i.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER) ?: return
                    if (quiet(ctx)) return
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

    private var viz: Visualizer? = null
    private var peak = 1f

    /** Event-driven: no polling. Capture starts when media starts playing and stops when it stops. */
    private val playbackCallback = object : AudioManager.AudioPlaybackCallback() {
        override fun onPlaybackConfigChanged(configs: MutableList<android.media.AudioPlaybackConfiguration>) = syncViz()
    }

    private fun syncViz() {
        val playing = audio.isMusicActive && power.isInteractive
        if (playing && viz == null) startCapture()
        if (!playing && viz != null) stopCapture()
    }

    private fun vizOn() { audio.registerAudioPlaybackCallback(playbackCallback, main); syncViz() }
    private fun vizOff() { audio.unregisterAudioPlaybackCallback(playbackCallback); stopCapture() }

    private fun startCapture() {
        val v = runCatching { Visualizer(0) }.onFailure { Log.w(TAG, "Visualizer unavailable", it) }.getOrNull() ?: return
        runCatching {
            v.enabled = false
            v.captureSize = 1024.coerceIn(Visualizer.getCaptureSizeRange()[0], Visualizer.getCaptureSizeRange()[1])
            v.setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                override fun onWaveFormDataCapture(vis: Visualizer?, waveform: ByteArray?, rate: Int) = Unit
                override fun onFftDataCapture(vis: Visualizer?, fft: ByteArray?, rate: Int) { if (fft != null) vizEngine.capture(fft, vis?.samplingRate ?: 44_100_000) }
            }, Visualizer.getMaxCaptureRate(), false, true)
            v.enabled = true
        }.onFailure { Log.w(TAG, "Visualizer setup failed", it); v.release(); return }
        viz = v
        vizEngine.sensitivity = settings.vizGain
        vizEngine.style = settings.vizStyle
        vizEngine.reset()
        Engine.set(Layer.Visualizer) { now -> vizEngine.frame(now) }
    }

    private fun stopCapture() {
        viz?.let { runCatching { it.enabled = false; it.release() } }
        viz = null
        Engine.clear(Layer.Visualizer)
    }

    private val vizEngine = VizEngine()

    // ================================================================ Ambient + face-down

    @Volatile private var faceDown = false
    private var downSince = 0L
    private val sensors by lazy { getSystemService(SensorManager::class.java) }
    private val flipListener = object : SensorEventListener {
        override fun onSensorChanged(e: SensorEvent) {
            val z = e.values[2]
            val down = z < -8.5f
            val now = SystemClock.uptimeMillis()
            if (down) { if (downSince == 0L) downSince = now } else downSince = 0
            val state = down && now - downSince > 800
            if (state != faceDown) { faceDown = state; main.post { apply() } }
        }
        override fun onAccuracyChanged(s: Sensor?, a: Int) = Unit
    }

    private fun flipOn() { sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let { sensors.registerListener(flipListener, it, SensorManager.SENSOR_DELAY_NORMAL) } }
    private fun flipOff() { sensors.unregisterListener(flipListener); faceDown = false }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, i: Intent) {
            updateAmbient()
            if ("viz" in enabled) syncViz()
        }
    }

    private val sweepReceiver = object : BroadcastReceiver() {
        private var lastAt = 0L
        override fun onReceive(ctx: Context, i: Intent) {
            val now = android.os.SystemClock.uptimeMillis()
            if (quiet(ctx) || now - lastAt < 400) return // debounce bursts of app opens
            lastAt = now
            Engine.set(Layer.Indicator, Engine.sweep())
        }
    }

    private val unlockReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, i: Intent) {
            if (quiet(ctx)) return
            Engine.set(Layer.Indicator, Engine.sweep())
            // Feel matches the light: a tick as it starts, a click as it reaches the camera.
            Buzz.fire(Buzz.TICK, android.os.VibrationAttributes.USAGE_TOUCH)
            main.postDelayed({ Buzz.fire(Buzz.CLICK, android.os.VibrationAttributes.USAGE_TOUCH) }, 560)
        }
    }

    // ---- Busy dot sources: notifications (from the listener) + mic and camera use (callbacks).
    @Volatile private var micBusy = false
    private val camerasInUse = HashSet<String>()
    private val cameraManager by lazy { getSystemService(android.hardware.camera2.CameraManager::class.java) }

    private val recordingCallback = object : AudioManager.AudioRecordingCallback() {
        override fun onRecordingConfigChanged(configs: MutableList<android.media.AudioRecordingConfiguration>) {
            micBusy = configs.isNotEmpty(); updateBusy()
        }
    }
    private val cameraCallback = object : android.hardware.camera2.CameraManager.AvailabilityCallback() {
        override fun onCameraUnavailable(id: String) { camerasInUse += id; updateBusy() }
        override fun onCameraAvailable(id: String) { camerasInUse -= id; updateBusy() }
    }

    private fun privacyOn() {
        audio.registerAudioRecordingCallback(recordingCallback, main)
        micBusy = audio.activeRecordingConfigurations.isNotEmpty()
        cameraManager.registerAvailabilityCallback(cameraCallback, main)
    }

    private fun privacyOff() {
        runCatching { audio.unregisterAudioRecordingCallback(recordingCallback) }
        runCatching { cameraManager.unregisterAvailabilityCallback(cameraCallback) }
        micBusy = false; camerasInUse.clear()
    }

    private var busyMode = 0

    /**
     * Bottom dot: a steady glow while the mic or a camera is in use (privacy), a slow breathe while
     * downloads or other background tasks run. 10 fps only, and off when nothing qualifies.
     */
    fun updateBusy() = main.post {
        val s = settings
        val privacy = s.busyDot && ((s.busyMic && micBusy) || (s.busyCamera && camerasInUse.isNotEmpty()))
        val work = s.busyDot && ((s.busyDownloads && NotifListener.progressBusy) || (s.busyOngoing && NotifListener.ongoingBusy))
        val mode = when { privacy -> 2; work -> 1; else -> 0 }
        if (mode == busyMode && (mode == 0) == !Engine.isActive(Layer.Busy)) return@post
        busyMode = mode
        when (mode) {
            2 -> Engine.set(Layer.Busy) { now -> IntArray(Zone.COUNT).also { it[Zone.E.ordinal] = (55 + 10 * sin(now * 2 * PI / 3000)).toInt() } }
            1 -> Engine.set(Layer.Busy) { now -> IntArray(Zone.COUNT).also { it[Zone.E.ordinal] = (20 + 60 * (0.5 + 0.5 * sin(now * 2 * PI / 2400))).toInt() } }
            else -> Engine.clear(Layer.Busy)
        }
    }

    fun dropForeground() = main.post { runCatching { stopForeground(STOP_FOREGROUND_REMOVE) } }

    /** Unread notifications breathe on the ring while the screen is off (always when face down). */
    fun updateAmbient() {
        val want = (settings.ambient || (settings.faceDown && faceDown)) && !power.isInteractive && NotifListener.unread > 0 && !quiet(this)
        if (want && !Engine.isActive(Layer.Ambient)) {
            Engine.set(Layer.Ambient) { now ->
                val b = (12 + 38 * (0.5 + 0.5 * sin(now * 2 * PI / 3000))).toInt()
                IntArray(Zone.COUNT).also { f -> Zone.ring.forEach { f[it.ordinal] = b } }
            }
        } else if (!want) Engine.clear(Layer.Ambient)
    }

    // ================================================================ Notification

    private fun buildNotification(): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, GlyphApp.CH_SERVICE)
            .setSmallIcon(R.drawable.ic_glyph)
            .setContentTitle("Glyph Tweaks is running")
            .setContentIntent(open)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val TAG = "Glyph"
        private const val NOTIF_ID = 7
        const val ACTION_TORCH = "com.nees.glyph.TORCH"
        const val ACTION_BATTERY = "com.nees.glyph.BATTERY"
        const val ACTION_TIMER = "com.nees.glyph.TIMER"

        @Volatile var instance: GlyphService? = null
            private set

        /** True when Do Not Disturb is on and the user wants glyphs to respect it. */
        fun quiet(ctx: Context): Boolean =
            Settings(ctx).respectDnd &&
                ctx.getSystemService(android.app.NotificationManager::class.java).currentInterruptionFilter > android.app.NotificationManager.INTERRUPTION_FILTER_ALL

        @Volatile var timerEnd = 0L
            private set

        fun start(ctx: Context, action: String? = null) {
            runCatching { ctx.startForegroundService(Intent(ctx, GlyphService::class.java).setAction(action)) }
                .onFailure { Log.w(TAG, "could not start service", it) }
        }

        /** Start when any feature is on; otherwise stop. */
        fun sync(ctx: Context) {
            // A running service just re-reads its settings; only a stopped one needs starting.
            val running = instance
            when {
                running != null -> running.apply()
                Settings(ctx).anyFeature() -> start(ctx)
            }
        }
    }
}
