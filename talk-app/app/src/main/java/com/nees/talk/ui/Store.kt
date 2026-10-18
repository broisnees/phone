package com.nees.talk.ui

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.CallLog
import android.provider.ContactsContract
import android.provider.Telephony
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.nees.talk.CallEntry
import com.nees.talk.Category
import com.nees.talk.Classifier
import com.nees.talk.Prefs
import com.nees.talk.Contact
import com.nees.talk.Conversation
import com.nees.talk.Directory
import com.nees.talk.Repo
import com.nees.talk.Sim
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** App-wide data, reloaded off the main thread whenever the backing provider changes. */
class Store(private val ctx: Context, private val scope: CoroutineScope) {
    var contacts by mutableStateOf<List<Contact>>(emptyList()); private set
    var directory by mutableStateOf(Directory(emptyList())); private set
    var calls by mutableStateOf<List<CallEntry>>(emptyList()); private set
    var conversations by mutableStateOf<List<Conversation>>(emptyList()); private set
    var sims by mutableStateOf<List<Sim>>(emptyList()); private set
    var pinned by mutableStateOf(Prefs.pinned(ctx)); private set

    fun togglePin(threadId: Long) {
        Prefs.togglePin(ctx, threadId)
        pinned = Prefs.pinned(ctx)
    }

    /** Smart-inbox bucket for each conversation, from its latest message. */
    fun categoryOf(c: Conversation): Category = Classifier.of(c.address, c.last.body, directory.find(c.address) != null)

    /** Bumped on every SMS change so an open thread reloads too. */
    var smsVersion by mutableStateOf(0); private set

    private val prefs = ctx.getSharedPreferences("talk", Context.MODE_PRIVATE)
    var callSimId by mutableStateOf(prefs.getInt("call_sim", -1)); private set

    val callSim: Sim? get() = sims.firstOrNull { it.subId == callSimId } ?: sims.firstOrNull()

    fun chooseCallSim(sim: Sim) {
        callSimId = sim.subId
        prefs.edit().putInt("call_sim", sim.subId).apply()
    }

