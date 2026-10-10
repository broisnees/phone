package com.nees.talk

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager

class TalkApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannels(
            listOf(
                // Telecom plays the ringtone itself; this channel only carries the call UI.
                NotificationChannel(CH_INCOMING, "Incoming calls", NotificationManager.IMPORTANCE_HIGH).apply {
                    setSound(null, null)
                    enableVibration(false)
                },
                NotificationChannel(CH_ONGOING, "Ongoing call", NotificationManager.IMPORTANCE_LOW),
                NotificationChannel(CH_MESSAGES, "Messages", NotificationManager.IMPORTANCE_HIGH),
            ),
        )
    }

    companion object {
        const val CH_INCOMING = "incoming"
        const val CH_ONGOING = "ongoing"
        const val CH_MESSAGES = "messages"
    }
}
