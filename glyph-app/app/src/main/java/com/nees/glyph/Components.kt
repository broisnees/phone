package com.nees.glyph

import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

class GlyphApp : Application() {
    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CH_SERVICE, "Glyph service", NotificationManager.IMPORTANCE_MIN).apply { setShowBadge(false) },
        )
        Engine.ceiling = Settings(this).brightness
    }

    companion object {
        const val CH_SERVICE = "service"
    }
}

/** Plays per-app patterns and keeps an unread count for the ambient ring. */
class NotifListener : NotificationListenerService() {
    override fun onListenerConnected() {
        connected = true
        Buzz.init(this)
        recount()
        GlyphService.instance?.dropForeground()
    }

    override fun onListenerDisconnected() { connected = false }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        recount()
        progress(sbn)
        val n = sbn.notification
        if (sbn.isOngoing || n.flags and Notification.FLAG_GROUP_SUMMARY != 0 || sbn.packageName == packageName) return
        // Updates to an existing "alert once" notification (progress, media) must not flash again.
        val firstPost = seen.add(sbn.key)
        if (!firstPost && n.flags and Notification.FLAG_ONLY_ALERT_ONCE != 0) return
        val settings = Settings(this)
        if (!settings.notifications || GlyphService.quiet(this)) return
        val id = settings.appPattern(sbn.packageName) ?: settings.defaultNotifPattern
        if (id == "off") return
        settings.pattern(id)?.let { Engine.play(Layer.Notification, it) }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        seen.remove(sbn.key)
        recount()
    }

    private val seen = HashSet<String>()

    private var lastProgressAt = 0L

    /** Live progress on the strip, throttled to one update a second. */
    private fun progress(sbn: StatusBarNotification) {
        val x = sbn.notification.extras
        val max = x.getInt(Notification.EXTRA_PROGRESS_MAX, 0)
        if (max <= 0 || x.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE)) return
        val s = Settings(this)
        val now = android.os.SystemClock.uptimeMillis()
        if (!s.liveProgress || s.paused || now - lastProgressAt < 1000) return
        lastProgressAt = now
        Engine.flash(Layer.Meter, Engine.stripLevel(x.getInt(Notification.EXTRA_PROGRESS, 0).toFloat() / max), 1600)
    }

    private fun recount() {
        val active = runCatching { activeNotifications.toList() }.getOrDefault(emptyList())
            .filter { it.packageName != packageName && it.packageName !in systemPackages && !it.packageName.startsWith("com.nothing.") }
        progressBusy = active.any { n ->
            val x = n.notification.extras
            x.getInt(Notification.EXTRA_PROGRESS_MAX, 0) > 0 || x.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE)
        }
        ongoingBusy = active.any { n ->
            val nn = n.notification
            val media = nn.extras.containsKey(Notification.EXTRA_MEDIA_SESSION)
            n.isOngoing && !media && (nn.category in ongoingCategories || nn.flags and Notification.FLAG_FOREGROUND_SERVICE != 0)
        }
        GlyphService.instance?.updateBusy()
        unread = runCatching { activeNotifications.count { !it.isOngoing && it.packageName != packageName && it.notification.flags and Notification.FLAG_GROUP_SUMMARY == 0 } }
            .getOrDefault(0)
        GlyphService.instance?.updateAmbient()
    }

    companion object {
        @Volatile var unread = 0
            private set
        @Volatile var connected = false
        /** Any app shows a progress bar (download, upload, transfer…). */
        @Volatile var progressBusy = false
        /** Any app has an ongoing background task (service, navigation, stopwatch, location sharing…). */
        @Volatile var ongoingBusy = false

        private val systemPackages = setOf("android", "com.android.systemui", "com.topjohnwu.magisk", "com.android.shell", "com.google.android.gms")
        private val ongoingCategories = setOf(
            Notification.CATEGORY_PROGRESS, Notification.CATEGORY_SERVICE, Notification.CATEGORY_NAVIGATION,
            Notification.CATEGORY_CALL, Notification.CATEGORY_STOPWATCH, Notification.CATEGORY_WORKOUT,
            Notification.CATEGORY_LOCATION_SHARING,
        )

        fun component(ctx: Context) = ComponentName(ctx, NotifListener::class.java)
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (Settings(ctx).anyFeature()) GlyphService.start(ctx)
    }
}

// ---------------------------------------------------------------- Quick Settings tiles

class TorchTile : TileService() {
    override fun onStartListening() { update() }

    override fun onClick() {
        val svc = GlyphService.instance
        if (svc != null) svc.setTorch(!Engine.isActive(Layer.Torch)) else GlyphService.start(this, GlyphService.ACTION_TORCH)
        qsTile?.let { it.state = if (Engine.isActive(Layer.Torch)) Tile.STATE_INACTIVE else Tile.STATE_ACTIVE; it.updateTile() }
    }

    private fun update() {
        val tile = qsTile ?: return
        tile.state = if (Engine.isActive(Layer.Torch)) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.updateTile()
    }

    companion object {
        fun refresh(ctx: Context) = requestListeningState(ctx, ComponentName(ctx, TorchTile::class.java))
    }
}

class BatteryTile : TileService() {
    override fun onClick() {
        GlyphService.instance?.showBattery(3000) ?: GlyphService.start(this, GlyphService.ACTION_BATTERY)
    }
}

class TimerTile : TileService() {
    override fun onStartListening() {
        qsTile?.let {
            val on = GlyphService.timerEnd > 0
            it.state = if (on) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            it.subtitle = if (on) "Running" else "${Settings(this).timerMinutes} min"
            it.updateTile()
        }
    }

    override fun onClick() {
        val svc = GlyphService.instance
        if (svc == null) GlyphService.start(this, GlyphService.ACTION_TIMER)
        else if (GlyphService.timerEnd > 0) svc.cancelTimer() else svc.startTimer(Settings(this).timerMinutes)
        onStartListening()
    }

    companion object {
        fun refresh(ctx: Context) = requestListeningState(ctx, ComponentName(ctx, TimerTile::class.java))
    }
}
