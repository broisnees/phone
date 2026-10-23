package com.nees.talk.ui

import android.content.ContentUris
import android.content.Intent
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import android.provider.CallLog
import android.provider.ContactsContract
import android.provider.Settings
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.CallMade
import androidx.compose.material.icons.automirrored.rounded.CallMissed
import androidx.compose.material.icons.automirrored.rounded.CallReceived
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.rounded.Backspace
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nees.talk.Blocker
import com.nees.talk.CallEntry
import com.nees.talk.Contact
import com.nees.talk.NumberIntel
import com.nees.talk.PhoneNum
import com.nees.talk.Repo
import com.nees.talk.call.dtmfTone
import com.nees.talk.numberKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle as JTextStyle
import java.util.Locale

interface Nav {
    fun call(number: String)
    fun contact(contact: Contact?, number: String?)
    fun thread(threadId: Long, address: String)
    fun newMessage()
    fun back()
}

private val zone = ZoneId.systemDefault()
private val timeFmt = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
private val dateFmt = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)

/** "10:41", "Yesterday", "Tuesday", or a date. */
fun relativeTime(ms: Long): String {
    val t = Instant.ofEpochMilli(ms).atZone(zone)
    val days = LocalDate.now(zone).toEpochDay() - t.toLocalDate().toEpochDay()
    return when {
        days == 0L -> t.format(timeFmt)
        days == 1L -> "Yesterday"
        days < 7 -> t.dayOfWeek.getDisplayName(JTextStyle.SHORT, Locale.getDefault())
        else -> t.format(DateTimeFormatter.ofPattern("d MMM"))
    }
}

fun clockTime(ms: Long): String = Instant.ofEpochMilli(ms).atZone(zone).format(timeFmt)

/** Day bucket used for section labels: Today / Yesterday / weekday / date. */
fun dayLabel(ms: Long): String {
    val d = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()
    val days = LocalDate.now(zone).toEpochDay() - d.toEpochDay()
    return when {
        days == 0L -> "Today"
        days == 1L -> "Yesterday"
        days < 7 -> d.dayOfWeek.getDisplayName(JTextStyle.FULL, Locale.getDefault())
        else -> d.format(dateFmt)
    }
}

// ============================================================ Calls (favourites + recents)

@Composable
fun CallsScreen(store: Store, nav: Nav) {
    var missedOnly by rememberSaveable { mutableStateOf(false) }
    val favorites = remember(store.contacts) { store.contacts.filter { it.starred } }
    val list = remember(store.calls, missedOnly) { if (missedOnly) store.calls.filter { it.missed } else store.calls }
    val byDay = remember(list) { list.groupBy { dayLabel(it.date) }.toList() }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = TabBarSpace) {
        item { ScreenTitle("Calls") }
        if (favorites.isNotEmpty()) {
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = S.edge),
                    horizontalArrangement = Arrangement.spacedBy(S.m),
                ) {
                    items(favorites, key = { it.id }) { c -> FavoriteBubble(c, nav) }
                }
                Spacer(Modifier.height(S.s))
            }
        }
        item {
            Row(Modifier.padding(horizontal = S.edge, vertical = S.s), horizontalArrangement = Arrangement.spacedBy(S.s)) {
                Chip("All", !missedOnly) { missedOnly = false }
                Chip("Missed", missedOnly, count = store.calls.takeWhile { it.missed }.size) { missedOnly = true }
            }
        }
        if (list.isEmpty()) {
            item { EmptyState(Icons.Rounded.Schedule, if (missedOnly) "No missed calls" else "No calls yet", "Calls you make and receive show up here.") }
        }
        byDay.forEach { (day, entries) ->
            item(key = "d-$day") { SectionLabel(day) }
            itemsIndexed(entries, key = { _, e -> "${e.date}-${e.number}" }) { i, e ->
                Column(Modifier.cardSlice(i, entries.size)) {
                    RecentRow(e, store, nav)
                    if (i < entries.lastIndex) Hairline()
                }
            }
        }
    }
}

@Composable
private fun FavoriteBubble(c: Contact, nav: Nav) {
    Column(
        Modifier.width(64.dp).pressable(Haptic.SUCCESS, onLongClick = { nav.contact(c, null) }) {
            c.numbers.firstOrNull()?.let { nav.call(it.number) }
        },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Avatar(c.name, c.photo, 56.dp)
        Spacer(Modifier.height(6.dp))
        Text(c.name.substringBefore(' '), style = T.caption, color = C.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun RecentRow(e: CallEntry, store: Store, nav: Nav) {
    val contact = store.directory.find(e.number)
    val name = contact?.name ?: e.number.ifBlank { "Unknown" }
    val (icon, tint) = when (e.type) {
        CallLog.Calls.OUTGOING_TYPE -> Icons.AutoMirrored.Rounded.CallMade to C.text3
        CallLog.Calls.INCOMING_TYPE -> Icons.AutoMirrored.Rounded.CallReceived to C.text3
        else -> Icons.AutoMirrored.Rounded.CallMissed to C.red
    }
    val detail = listOfNotNull(
        if (contact != null) contact.numbers.firstOrNull { numberKey(it.number) == numberKey(e.number) }?.label else NumberIntel.label(e.number),
        store.simName(e.accountId),
    ).joinToString(" · ")

    Row(
        Modifier.fillMaxWidth().rowPress { nav.call(e.number) }.padding(start = S.pad, end = S.s, top = S.m, bottom = S.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(name, contact?.photo)
        Spacer(Modifier.width(S.m))
        Column(Modifier.weight(1f)) {
            Text(
                if (e.count > 1) "$name  ${e.count}" else name,
                style = T.headline, color = if (e.missed) C.red else C.text, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = tint, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(S.xs))
                Text("${clockTime(e.date)} · $detail", style = T.sub.tabular(), color = C.text2, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Box(Modifier.size(40.dp).pressable(Haptic.LIGHT) { nav.contact(contact, e.number) }, contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.Info, "Details", tint = C.text2, modifier = Modifier.size(20.dp))
        }
    }
}

// ============================================================ Contacts

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ContactsScreen(store: Store, nav: Nav) {
    val ctx = LocalContext.current
    var query by rememberSaveable { mutableStateOf("") }
    val filtered = remember(store.contacts, query) {
        val q = query.trim()
        val digits = q.filter(Char::isDigit)
        if (q.isEmpty()) store.contacts
        else store.contacts.filter { c ->
            c.name.contains(q, ignoreCase = true) || (digits.isNotEmpty() && c.numbers.any { numberKey(it.number).contains(digits) })
        }
    }
    val sections = remember(filtered) {
        filtered.groupBy { c -> c.name.firstOrNull()?.uppercaseChar()?.takeIf { it.isLetter() } ?: '#' }.toSortedMap().toList()
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = TabBarSpace) {
        item {
            ScreenTitle("Contacts") {
                CircleAction(Icons.Rounded.PersonAdd, "New contact") {
                    ctx.startActivity(Intent(Intent.ACTION_INSERT, ContactsContract.Contacts.CONTENT_URI))
                }
            }
            SearchBar(query, { query = it }, "Search ${store.contacts.size} contacts")
        }
        if (filtered.isEmpty()) item { EmptyState(Icons.Rounded.Search, if (query.isEmpty()) "No contacts" else "No matches", "Try another name or number.") }
        sections.forEach { (letter, group) ->
            stickyHeader(key = "h$letter") { SectionLabel(letter.toString()) }
            itemsIndexed(group, key = { _, c -> c.id }) { i, c ->
                Column(Modifier.cardSlice(i, group.size)) {
                    Row(
                        Modifier.fillMaxWidth().rowPress { nav.contact(c, null) }.padding(horizontal = S.pad, vertical = S.m),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Avatar(c.name, c.photo)
                        Spacer(Modifier.width(S.m))
                        Text(c.name, style = T.body, color = C.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        if (c.starred) Icon(Icons.Rounded.Star, "Favorite", tint = C.accent, modifier = Modifier.size(16.dp))
                    }
                    if (i < group.lastIndex) Hairline()
                }
            }
        }
    }
}

// ============================================================ Keypad

