package com.nees.talk.sms

import android.app.Activity
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Person
import android.app.RemoteInput
import android.app.Service
import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.IBinder
import android.provider.Telephony
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import com.nees.talk.Directory
import com.nees.talk.Otp
import com.nees.talk.MainActivity
import com.nees.talk.R
import com.nees.talk.Repo
import com.nees.talk.TalkApp
import kotlin.concurrent.thread

/** Default-SMS-app receiver: the system hands us each incoming SMS and we must store it. */
class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val parts = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        val subId = intent.getIntExtra(SubscriptionManager.EXTRA_SUBSCRIPTION_INDEX, -1)
        val pending = goAsync()
        thread {
            try {
                parts.groupBy { it.displayOriginatingAddress ?: "" }.forEach { (address, msgs) ->
                    val values = ContentValues().apply {
                        put(Telephony.Sms.ADDRESS, address)
                        put(Telephony.Sms.BODY, msgs.joinToString("") { it.displayMessageBody ?: "" })
                        put(Telephony.Sms.DATE, System.currentTimeMillis())
                        put(Telephony.Sms.DATE_SENT, msgs.first().timestampMillis)
                        put(Telephony.Sms.READ, 0)
                        put(Telephony.Sms.SEEN, 0)
                        put(Telephony.Sms.SUBSCRIPTION_ID, subId)
                    }
                    ctx.contentResolver.insert(Telephony.Sms.Inbox.CONTENT_URI, values)
                    val threadId = Repo.threadIdFor(ctx, address)
                    val code = Otp.find(values.getAsString(Telephony.Sms.BODY))
                    if (code != null) {
                        Otp.copy(ctx, code)
                        MessageNotifier.showCode(ctx, threadId, address, code)
                    } else {
                        MessageNotifier.show(ctx, threadId, address)
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }
}

/** Required for the SMS role. MMS download isn't implemented; the carrier push is acknowledged and dropped. */
class MmsReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) = Unit
}

/** "Respond via message" from the incoming-call screen. */
class RespondService : Service() {
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val number = intent?.data?.schemeSpecificPart?.substringBefore('?')
        val text = intent?.getStringExtra(Intent.EXTRA_TEXT)
        if (!number.isNullOrBlank() && !text.isNullOrBlank()) {
            Repo.sendSms(this, number, text, SmsManager.getDefaultSmsSubscriptionId())
        }
        stopSelf(startId)
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

class SentReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val uri = intent.data ?: return
        val type = if (resultCode == Activity.RESULT_OK) Telephony.Sms.MESSAGE_TYPE_SENT else Telephony.Sms.MESSAGE_TYPE_FAILED
        Repo.setSmsType(ctx, uri, type)
    }
}

class ReplyReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val threadId = intent.getLongExtra(EXTRA_THREAD, -1)
        val address = intent.getStringExtra(EXTRA_ADDRESS) ?: return
        val text = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(KEY_REPLY)?.toString()
        val pending = goAsync()
        thread {
            try {
                if (!text.isNullOrBlank()) {
                    val subId = Repo.thread(ctx, threadId).lastOrNull()?.subId?.takeIf { it >= 0 }
                        ?: SmsManager.getDefaultSmsSubscriptionId()
                    Repo.sendSms(ctx, address, text, subId)
                }
                Repo.markRead(ctx, threadId)
                ctx.getSystemService(NotificationManager::class.java).cancel(threadId.toInt())
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val KEY_REPLY = "reply"
        const val EXTRA_THREAD = "thread"
        const val EXTRA_ADDRESS = "address"
        const val ACTION_READ = "com.nees.talk.READ"
    }
}

/** "Copy" action on a code notification. */
class CopyCodeReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        intent.getStringExtra(EXTRA_CODE)?.let { Otp.copy(ctx, it) }
    }

    companion object {
        const val EXTRA_CODE = "code"
    }
}

