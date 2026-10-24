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

private val keyLetters = linkedMapOf(
    '1' to "", '2' to "ABC", '3' to "DEF", '4' to "GHI", '5' to "JKL", '6' to "MNO",
    '7' to "PQRS", '8' to "TUV", '9' to "WXYZ", '*' to "", '0' to "+", '#' to "",
)

private val t9: Map<Char, Char> = buildMap {
    keyLetters.forEach { (digit, letters) -> letters.forEach { put(it.lowercaseChar(), digit) } }
}

/** True if any word of [name] starts with the T9 digit sequence [digits]. */
private fun t9Match(name: String, digits: String): Boolean =
    name.lowercase().split(' ', '-', '.').any { w -> w.length >= digits.length && w.take(digits.length).map { t9[it] ?: it }.joinToString("") == digits }

@Composable
fun KeypadScreen(store: Store, nav: Nav, initial: String) {
    val ctx = LocalContext.current
    var number by rememberSaveable(initial) { mutableStateOf(initial) }
    val tonesOn = remember { Settings.System.getInt(ctx.contentResolver, Settings.System.DTMF_TONE_WHEN_DIALING, 1) == 1 }
    val tones = remember { if (tonesOn) runCatching { ToneGenerator(AudioManager.STREAM_DTMF, 70) }.getOrNull() else null }
    val suggestions = remember(number, store.contacts) {
        val digits = number.filter(Char::isDigit)
        if (digits.length < 2) emptyList()
        else store.contacts.flatMap { c -> c.numbers.map { c to it } }
            .filter { (c, n) -> numberKey(n.number).contains(digits) || t9Match(c.name, digits) }
            .distinctBy { it.first.id }.take(3)
    }
    val info = remember(number) { if (number.length >= 7) NumberIntel.label(number) else "" }

    Column(Modifier.fillMaxSize().padding(TabBarSpace), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
        // Suggestions float at the top so the keypad never jumps.
        Column(Modifier.fillMaxWidth().weight(1f).padding(top = S.l)) {
            if (suggestions.isNotEmpty()) {
                Card {
                    suggestions.forEachIndexed { i, (c, n) ->
                        Row(
                            Modifier.fillMaxWidth().rowPress { nav.call(n.number) }.padding(horizontal = S.pad, vertical = S.m),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Avatar(c.name, c.photo, 36.dp)
                            Spacer(Modifier.width(S.m))
                            Column(Modifier.weight(1f)) {
                                Text(c.name, style = T.headline, color = C.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${n.label} · ${n.number}", style = T.sub.tabular(), color = C.text2, maxLines = 1)
                            }
                        }
                        if (i < suggestions.lastIndex) Hairline(start = S.pad + 36.dp + S.m)
                    }
                }
            }
        }
        Text(
            number.ifEmpty { " " },
            style = T.display.copy(fontSize = if (number.length > 13) 26.sp else 34.sp, lineHeight = 40.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.sp).tabular(),
            color = C.text, maxLines = 1, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = S.xl),
        )
        Box(Modifier.height(S.xl), contentAlignment = Alignment.Center) {
            if (info.isNotEmpty()) Text(info, style = T.sub, color = C.text2)
        }
        Spacer(Modifier.height(S.m))
        Column(verticalArrangement = Arrangement.spacedBy(S.m)) {
            keyLetters.keys.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(S.xl)) {
                    row.forEach { k ->
                        DialKey(k, keyLetters.getValue(k), onLong = if (k == '0') ({ number += "+" }) else null) {
                            number += k
                            tones?.startTone(dtmfTone(k), 100)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(S.l))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(S.xl)) {
            Box(Modifier.size(72.dp), contentAlignment = Alignment.Center) { SimChip(store.sims, store.callSim, store::chooseCallSim) }
            Box(
                Modifier.size(72.dp).clip(CircleShape).background(C.green).pressable(Haptic.SUCCESS) {
                    if (number.isNotEmpty()) nav.call(number)
                    else store.calls.firstOrNull { it.type == CallLog.Calls.OUTGOING_TYPE }?.let { number = it.number }
                },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.Call, "Call", tint = Color.White, modifier = Modifier.size(30.dp)) }
            Box(Modifier.size(72.dp), contentAlignment = Alignment.Center) {
                val shown by animateFloatAsState(if (number.isNotEmpty()) 1f else 0f, tween(150, easing = EaseOut), label = "del")
                Icon(
                    Icons.Rounded.Backspace, "Delete", tint = C.text2,
                    modifier = Modifier.size(48.dp)
                        .graphicsLayer { alpha = shown; val s = 0.25f + 0.75f * shown; scaleX = s; scaleY = s }
                        .then(if (number.isNotEmpty()) Modifier.pressable(Haptic.SELECT, onLongClick = { number = "" }) { number = number.dropLast(1) } else Modifier)
                        .padding(S.m),
                )
            }
        }
    }
}

@Composable
private fun DialKey(digit: Char, letters: String, onLong: (() -> Unit)?, onTap: () -> Unit) {
    Box(
        Modifier.size(72.dp).clip(CircleShape).background(C.raised).pressable(Haptic.KEY, onLongClick = onLong, onClick = onTap),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(digit.toString(), style = T.title.copy(fontSize = 28.sp, lineHeight = 32.sp, fontWeight = FontWeight.Normal, letterSpacing = 0.sp), color = C.text)
            Text(letters.ifEmpty { " " }, style = T.label.copy(fontSize = 9.sp, lineHeight = 11.sp, letterSpacing = 1.4.sp), color = C.text3)
        }
    }
}

// ============================================================ Contact / number detail

@Composable
fun ContactDetail(store: Store, nav: Nav, contactId: Long?, number: String?) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val contact = contactId?.let { id -> store.contacts.firstOrNull { it.id == id } }
    val name = contact?.name ?: number ?: "Unknown"
    val numbers = contact?.numbers ?: listOfNotNull(number?.let { PhoneNum(it, NumberIntel.of(it).kind.lowercase()) })
    val primary = numbers.firstOrNull()?.number
    var blockVersion by remember { mutableIntStateOf(0) }
    val blocked by produceState(false, primary, blockVersion) {
        value = primary?.let { withContext(Dispatchers.IO) { Blocker.isBlocked(ctx, it) } } ?: false
    }
    val history = remember(store.calls, numbers) {
        val keys = numbers.map { numberKey(it.number) }.toSet()
        store.calls.filter { numberKey(it.number) in keys }.take(6)
    }
    fun openThread(n: String) = scope.launch {
        val t = withContext(Dispatchers.IO) { Repo.threadIdFor(ctx, n) }
        nav.thread(t, n)
    }

    LazyColumn(Modifier.fillMaxSize().background(C.bg), contentPadding = PaddingValues(bottom = S.xxl)) {
        item {
            Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
            Row(Modifier.fillMaxWidth().padding(horizontal = S.edge, vertical = S.s), verticalAlignment = Alignment.CenterVertically) {
                CircleAction(Icons.AutoMirrored.Rounded.ArrowBack, "Back") { nav.back() }
                Spacer(Modifier.weight(1f))
                if (contact != null) {
                    CircleAction(Icons.Rounded.Edit, "Edit") {
                        val uri = ContactsContract.Contacts.getLookupUri(ctx.contentResolver, ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, contact.id))
                        ctx.startActivity(Intent(Intent.ACTION_EDIT, uri))
                    }
                }
            }
            Column(Modifier.fillMaxWidth().padding(top = S.l), horizontalAlignment = Alignment.CenterHorizontally) {
                Avatar(name, contact?.photo, 96.dp)
                Spacer(Modifier.height(S.l))
                Text(name, style = T.title, color = C.text, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = S.xl))
                primary?.let {
                    Spacer(Modifier.height(S.xs))
                    Text(NumberIntel.label(it), style = T.sub, color = C.text2)
                }
            }
            Spacer(Modifier.height(S.xl))
            Row(Modifier.fillMaxWidth().padding(horizontal = S.edge), horizontalArrangement = Arrangement.spacedBy(S.s)) {
                ActionTile(Icons.Rounded.Call, "Call", Modifier.weight(1f), enabled = primary != null, haptic = Haptic.SUCCESS) { primary?.let(nav::call) }
                ActionTile(Icons.Rounded.ChatBubble, "Message", Modifier.weight(1f), enabled = primary != null) { primary?.let(::openThread) }
                if (contact != null) {
                    ActionTile(
                        if (contact.starred) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                        if (contact.starred) "Favorite" else "Add favorite", Modifier.weight(1f), haptic = Haptic.SELECT,
                    ) { scope.launch(Dispatchers.IO) { Repo.setStarred(ctx, contact.id, !contact.starred) } }
                } else {
                    ActionTile(Icons.Rounded.PersonAdd, "Save", Modifier.weight(1f)) {
                        ctx.startActivity(Intent(Intent.ACTION_INSERT, ContactsContract.Contacts.CONTENT_URI).putExtra(ContactsContract.Intents.Insert.PHONE, primary))
                    }
                }
            }
        }
        item {
            SectionLabel("Numbers")
            Card {
                numbers.forEachIndexed { i, n ->
                    Row(
                        Modifier.fillMaxWidth().rowPress(onLongClick = { openThread(n.number) }) { nav.call(n.number) }
                            .padding(horizontal = S.pad, vertical = S.m),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(n.label.replaceFirstChar(Char::uppercase), style = T.sub, color = C.text2)
                            Text(n.number, style = T.body.tabular(), color = C.text)
                        }
                        Icon(Icons.Rounded.Call, null, tint = C.accent, modifier = Modifier.size(20.dp))
                    }
                    if (i < numbers.lastIndex) Hairline(start = S.pad)
                }
            }
        }
        if (history.isNotEmpty()) item {
            SectionLabel("History")
            Card {
                history.forEachIndexed { i, e ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = S.pad, vertical = S.m), verticalAlignment = Alignment.CenterVertically) {
                        Text("${dayLabel(e.date)}, ${clockTime(e.date)}", style = T.body.tabular(), color = C.text, modifier = Modifier.weight(1f))
                        val kind = when { e.missed -> "Missed"; e.type == CallLog.Calls.OUTGOING_TYPE -> "Outgoing"; else -> "Incoming" }
                        val dur = if (e.duration > 0) " · ${e.duration / 60}:${"%02d".format(e.duration % 60)}" else ""
                        Text(kind + dur, style = T.sub.tabular(), color = if (e.missed) C.red else C.text2)
                    }
                    if (i < history.lastIndex) Hairline(start = S.pad)
                }
            }
        }
        if (primary != null) item {
            SectionLabel("More")
            Card {
                DetailRow(Icons.Rounded.Search, "Look up this number", C.text) {
                    ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode(primary))))
                }
                Hairline(start = S.pad + 20.dp + S.m)
                DetailRow(Icons.Rounded.Block, if (blocked) "Unblock" else "Block and report spam", C.red) {
                    scope.launch(Dispatchers.IO) {
                        if (blocked) Blocker.unblock(ctx, primary) else Blocker.block(ctx, primary)
                        blockVersion++
                    }
                }
            }
        }
    }
}

