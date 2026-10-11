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

    override fun onDestroy() {
        CallHub.service = null
        super.onDestroy()
    }

    override fun onCallAdded(call: Call) = CallHub.add(this, call)
    override fun onCallRemoved(call: Call) = CallHub.remove(this, call)

    @Deprecated("Superseded by CallEndpoint APIs on 34+, still delivered on all versions")
    override fun onCallAudioStateChanged(audioState: CallAudioState) {
        CallHub.audio.value = audioState
    }
}

object CallHub {
    var service: CallService? = null
    val calls = MutableStateFlow<List<Call>>(emptyList())
    val audio = MutableStateFlow<CallAudioState?>(null)
    /** Bumped on every call state/detail change so observers recompose. */
    val version = MutableStateFlow(0)

    private lateinit var app: Context
    private val names = HashMap<String, String>()

    private val callback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) = changed(call)
        override fun onDetailsChanged(call: Call, details: Call.Details) = changed(call)
    }

    fun add(ctx: Context, call: Call) {
        app = ctx.applicationContext
        call.registerCallback(callback)
        calls.update { it + call }
        changed(call)
        if (call.details.state != Call.STATE_RINGING) openScreen(ctx)
    }

    fun remove(ctx: Context, call: Call) {
        call.unregisterCallback(callback)
        calls.update { it - call }
        version.update { it + 1 }
        if (calls.value.isEmpty()) ctx.getSystemService(NotificationManager::class.java).cancel(NOTIF_ID)
        else primary()?.let { notify(it) }
    }

    private fun changed(call: Call) {
        version.update { it + 1 }
        if (call == primary()) notify(call)
    }

    /** The call the UI should focus on: ringing first, then the live one, then anything held. */
    fun primary(): Call? {
        val list = calls.value
        return list.firstOrNull { it.details.state == Call.STATE_RINGING }
            ?: list.firstOrNull { it.details.state != Call.STATE_HOLDING && it.details.state != Call.STATE_DISCONNECTED }
            ?: list.firstOrNull()
    }

    fun number(call: Call): String = call.details.handle?.schemeSpecificPart ?: ""

    fun name(ctx: Context, call: Call): String {
        val number = number(call)
        if (number.isEmpty()) return "Unknown"
        return names.getOrPut(number) {
            val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
            runCatching {
                ctx.contentResolver.query(uri, arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME), null, null, null)
                    ?.use { if (it.moveToFirst()) it.getString(0) else null }
            }.getOrNull() ?: number
        }
    }

