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

    fun find(number: String): Contact? = byKey[numberKey(number)]
    fun name(number: String): String = find(number)?.name ?: number
}

object Repo {
    fun contacts(ctx: Context): List<Contact> {
        val p = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val proj = arrayOf(
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.TYPE,
            ContactsContract.CommonDataKinds.Phone.LABEL,
            ContactsContract.CommonDataKinds.Phone.STARRED,
            ContactsContract.CommonDataKinds.Phone.PHOTO_THUMBNAIL_URI,
        )
        data class Acc(val id: Long, val name: String, val starred: Boolean, val photo: String?, val nums: LinkedHashMap<String, PhoneNum>)

        val byId = LinkedHashMap<Long, Acc>()
        ctx.contentResolver.query(p, proj, null, null, "${proj[1]} COLLATE LOCALIZED ASC")?.use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                val number = c.getString(2) ?: continue
                val acc = byId.getOrPut(id) {
                    Acc(id, c.getString(1) ?: number, c.getInt(5) == 1, c.getString(6), LinkedHashMap())
                }
                val label = ContactsContract.CommonDataKinds.Phone
                    .getTypeLabel(ctx.resources, c.getInt(3), c.getString(4)).toString()
                acc.nums.putIfAbsent(numberKey(number), PhoneNum(number, label.lowercase()))
            }
        }
        return byId.values.map { Contact(it.id, it.name, it.nums.values.toList(), it.starred, it.photo) }
    }

    fun setStarred(ctx: Context, id: Long, starred: Boolean) {
        val values = ContentValues().apply { put(ContactsContract.Contacts.STARRED, if (starred) 1 else 0) }
        ctx.contentResolver.update(
            ContactsContract.Contacts.CONTENT_URI, values, "${ContactsContract.Contacts._ID}=?", arrayOf(id.toString()),
        )
    }

    /** Recent calls, with back-to-back calls to the same number and outcome folded into one row. */
    fun calls(ctx: Context, limit: Int = 400): List<CallEntry> {
        val proj = arrayOf(
            CallLog.Calls.NUMBER, CallLog.Calls.TYPE, CallLog.Calls.DATE,
            CallLog.Calls.DURATION, CallLog.Calls.PHONE_ACCOUNT_ID,
        )
        val out = ArrayList<CallEntry>()
        ctx.contentResolver.query(CallLog.Calls.CONTENT_URI, proj, null, null, "${CallLog.Calls.DATE} DESC")?.use { c ->
            var n = 0
            while (c.moveToNext() && n++ < limit) {
                val e = CallEntry(c.getString(0) ?: "", c.getInt(1), c.getLong(2), c.getLong(3), c.getString(4), 1)
                val prev = out.lastOrNull()
                if (prev != null && numberKey(prev.number) == numberKey(e.number) && prev.missed == e.missed) {
                    out[out.lastIndex] = prev.copy(count = prev.count + 1)
                } else out += e
            }
        }
        return out
    }

    private val smsProj = arrayOf(
        Telephony.Sms._ID, Telephony.Sms.THREAD_ID, Telephony.Sms.ADDRESS, Telephony.Sms.BODY,
        Telephony.Sms.DATE, Telephony.Sms.TYPE, Telephony.Sms.READ, Telephony.Sms.SUBSCRIPTION_ID,
    )

    private fun android.database.Cursor.toSms() = Sms(
        getLong(0), getLong(1), getString(2) ?: "", getString(3) ?: "",
        getLong(4), getInt(5), getInt(6) == 1, getInt(7),
    )

    fun conversations(ctx: Context): List<Conversation> {
        val latest = LinkedHashMap<Long, Sms>()
        val unread = HashMap<Long, Int>()
        ctx.contentResolver.query(Telephony.Sms.CONTENT_URI, smsProj, null, null, "${Telephony.Sms.DATE} DESC")?.use { c ->
            while (c.moveToNext()) {
                val s = c.toSms()
                latest.putIfAbsent(s.threadId, s)
                if (!s.read && !s.outgoing) unread[s.threadId] = (unread[s.threadId] ?: 0) + 1
            }
        }
        return latest.values.map { Conversation(it.threadId, it.address, it, unread[it.threadId] ?: 0) }
    }

    fun thread(ctx: Context, threadId: Long): List<Sms> {
        val out = ArrayList<Sms>()
        ctx.contentResolver.query(
            Telephony.Sms.CONTENT_URI, smsProj, "${Telephony.Sms.THREAD_ID}=?", arrayOf(threadId.toString()),
            "${Telephony.Sms.DATE} ASC",
        )?.use { c -> while (c.moveToNext()) out += c.toSms() }
        return out
    }

    /** Thread ids whose message bodies contain [q]; used by message search. */
    fun searchThreads(ctx: Context, q: String): Set<Long> {
        val ids = HashSet<Long>()
        ctx.contentResolver.query(
            Telephony.Sms.CONTENT_URI, arrayOf(Telephony.Sms.THREAD_ID), "${Telephony.Sms.BODY} LIKE ?", arrayOf("%$q%"), null,
        )?.use { c -> while (c.moveToNext()) ids += c.getLong(0) }
        return ids
    }

    fun markRead(ctx: Context, threadId: Long) {
        val values = ContentValues().apply { put(Telephony.Sms.READ, 1); put(Telephony.Sms.SEEN, 1) }
        ctx.contentResolver.update(
            Telephony.Sms.CONTENT_URI, values, "${Telephony.Sms.THREAD_ID}=? AND ${Telephony.Sms.READ}=0", arrayOf(threadId.toString()),
        )
    }

