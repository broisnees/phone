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

