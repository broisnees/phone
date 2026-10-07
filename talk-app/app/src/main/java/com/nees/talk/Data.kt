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

data class Sms(
    val id: Long,
    val threadId: Long,
    val address: String,
    val body: String,
    val date: Long,
    val type: Int,
    val read: Boolean,
    val subId: Int,
) {
    val outgoing get() = type != Telephony.Sms.MESSAGE_TYPE_INBOX
    val failed get() = type == Telephony.Sms.MESSAGE_TYPE_FAILED
    val pending get() = type == Telephony.Sms.MESSAGE_TYPE_OUTBOX || type == Telephony.Sms.MESSAGE_TYPE_QUEUED
}

data class Conversation(val threadId: Long, val address: String, val last: Sms, val unread: Int)

data class Sim(val subId: Int, val slot: Int, val name: String, val handle: PhoneAccountHandle?)

/** Digits-only tail used to match the same number written in different formats (+977, 0, spaces). */
fun numberKey(number: String): String = number.filter { it.isDigit() }.takeLast(9)

/** Number → contact lookup, rebuilt whenever the contact list changes. */
class Directory(val contacts: List<Contact>) {
    private val byKey = HashMap<String, Contact>().apply {
        for (c in contacts) for (n in c.numbers) putIfAbsent(numberKey(n.number), c)
    }

