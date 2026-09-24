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
    override fun onListenerConnected() = recount()

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        recount()
        val n = sbn.notification
        if (sbn.isOngoing || n.flags and Notification.FLAG_GROUP_SUMMARY != 0 || sbn.packageName == packageName) return
        // Updates to an existing "alert once" notification (progress, media) must not flash again.
        val firstPost = seen.add(sbn.key)
        if (!firstPost && n.flags and Notification.FLAG_ONLY_ALERT_ONCE != 0) return
        val settings = Settings(this)
        if (!settings.notifications) return
        val id = settings.appPattern(sbn.packageName) ?: settings.defaultNotifPattern
        if (id == "off") return
        settings.pattern(id)?.let { Engine.play(Layer.Notification, it) }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        seen.remove(sbn.key)
        recount()
    }

