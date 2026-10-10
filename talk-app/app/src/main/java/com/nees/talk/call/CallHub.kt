package com.nees.talk.call

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Person
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import android.telecom.VideoProfile
import com.nees.talk.R
import com.nees.talk.TalkApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/** The system binds this while any call exists; we mirror its calls into [CallHub]. */
class CallService : InCallService() {
    override fun onCreate() {
        super.onCreate()
        CallHub.service = this
    }

