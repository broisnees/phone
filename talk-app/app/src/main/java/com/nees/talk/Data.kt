package com.nees.talk

import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.CallLog
import android.provider.ContactsContract
import android.provider.Telephony
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import com.nees.talk.sms.SentReceiver

data class PhoneNum(val number: String, val label: String)

data class Contact(
    val id: Long,
    val name: String,
    val numbers: List<PhoneNum>,
    val starred: Boolean,
    val photo: String?,
)

data class CallEntry(
    val number: String,
    val type: Int,
    val date: Long,
    val duration: Long,
    val accountId: String?,
    val count: Int,
) {
    val missed get() = type == CallLog.Calls.MISSED_TYPE || type == CallLog.Calls.REJECTED_TYPE
}

