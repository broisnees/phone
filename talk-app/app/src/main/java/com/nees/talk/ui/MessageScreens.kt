package com.nees.talk.ui

import android.app.NotificationManager
import android.telephony.SmsManager
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nees.talk.Category
import com.nees.talk.Contact
import com.nees.talk.Conversation
import com.nees.talk.NumberIntel
import com.nees.talk.Otp
import com.nees.talk.Prefs
import com.nees.talk.Repo
import com.nees.talk.Sim
import com.nees.talk.Sms
import com.nees.talk.numberKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ============================================================ Inbox

@Composable
fun ChatsScreen(store: Store, nav: Nav) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf<Category?>(null) }
    var confirmDelete by remember { mutableStateOf<Conversation?>(null) }

    val bodyHits by produceState(emptySet<Long>(), query, store.smsVersion) {
        val q = query.trim()
        value = if (q.length < 2) emptySet() else withContext(Dispatchers.IO) { Repo.searchThreads(ctx, q) }
    }
    val categorized = remember(store.conversations, store.directory) { store.conversations.map { it to store.categoryOf(it) } }
    val unreadBy = remember(categorized) { categorized.groupBy { it.second }.mapValues { (_, l) -> l.count { it.first.unread > 0 } } }
    val visible = remember(categorized, filter, query, bodyHits) {
        val q = query.trim()
        categorized.filter { (c, cat) ->
            (filter == null || cat == filter) &&
                (q.isEmpty() || c.threadId in bodyHits || store.directory.name(c.address).contains(q, ignoreCase = true) || c.address.contains(q))
        }.map { it.first }
    }
    val pinned = visible.filter { it.threadId in store.pinned }
    val rest = visible.filter { it.threadId !in store.pinned }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = TabBarSpace) {
        item {
            ScreenTitle("Chats") { ComposeAction(nav::newMessage) }
            SearchBar(query, { query = it }, "Search messages")
            LazyRow(
                contentPadding = PaddingValues(horizontal = S.edge, vertical = S.m),
                horizontalArrangement = Arrangement.spacedBy(S.s),
            ) {
                item { Chip("All", filter == null, store.conversations.count { it.unread > 0 }) { filter = null } }
                items(Category.entries) { cat -> Chip(cat.label, filter == cat, unreadBy[cat] ?: 0) { filter = if (filter == cat) null else cat } }
            }
        }
        if (visible.isEmpty()) {
            item { EmptyState(Icons.Rounded.ChatBubbleOutline, if (query.isEmpty()) "Nothing here yet" else "No matches", "Start a conversation with the pencil button.") }
        }
        listOf("Pinned" to pinned, "Recent" to rest).forEach { (label, group) ->
            if (group.isEmpty()) return@forEach
            if (pinned.isNotEmpty()) item(key = "s-$label") { SectionLabel(label) }
            itemsIndexed(group, key = { _, c -> c.threadId }) { i, c ->
                Column(Modifier.cardSlice(i, group.size)) {
                    SwipeRow(
                        startAction = SwipeAction(Icons.Rounded.PushPin, if (c.threadId in store.pinned) "Unpin" else "Pin", C.accent.copy(alpha = 0.85f), Haptic.SUCCESS) {
                            store.togglePin(c.threadId)
                        },
                        endAction = SwipeAction(Icons.Rounded.Delete, "Delete", C.red, Haptic.REJECT) { confirmDelete = c },
                    ) {
                        ConversationRow(c, store, nav) {
                            scope.launch(Dispatchers.IO) { if (c.unread > 0) Repo.markRead(ctx, c.threadId) else Repo.markUnread(ctx, c.threadId) }
                        }
                    }
                    if (i < group.lastIndex) Hairline()
                }
            }
        }
    }

    confirmDelete?.let { c ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            containerColor = C.raised,
            title = { Text("Delete conversation?", style = T.headline, color = C.text) },
            text = { Text("All messages with ${store.directory.name(c.address)} will be removed from this phone.", style = T.sub, color = C.text2) },
            confirmButton = {
                TextButton({ scope.launch(Dispatchers.IO) { Repo.deleteThread(ctx, c.threadId) }; confirmDelete = null }) {
                    Text("Delete", style = T.headline, color = C.red)
                }
            },
            dismissButton = { TextButton({ confirmDelete = null }) { Text("Cancel", style = T.headline, color = C.text) } },
        )
    }
}

@Composable
private fun ConversationRow(c: Conversation, store: Store, nav: Nav, onLong: () -> Unit) {
    val contact = store.directory.find(c.address)
    val name = contact?.name ?: c.address
    val unread = c.unread > 0
    val code = remember(c.last.body) { if (!c.last.outgoing) Otp.find(c.last.body) else null }
    Row(
        Modifier.fillMaxWidth().rowPress(onLongClick = onLong) { nav.thread(c.threadId, c.address) }
            .padding(horizontal = S.pad, vertical = S.m),
        verticalAlignment = Alignment.Top,
    ) {
        Avatar(name, contact?.photo)
        Spacer(Modifier.width(S.m))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    name, style = T.headline.copy(fontWeight = if (unread) FontWeight.Bold else FontWeight.SemiBold),
                    color = C.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                )
                if (c.threadId in store.pinned) {
                    Icon(Icons.Rounded.PushPin, "Pinned", tint = C.text3, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(S.xs))
                }
                Text(relativeTime(c.last.date), style = T.caption.tabular(), color = if (unread) C.accent else C.text3)
            }
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    (if (c.last.outgoing) "You: " else "") + c.last.body,
                    style = T.sub, color = if (unread) C.text else C.text2,
                    maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                )
                if (unread) {
                    Spacer(Modifier.width(S.s))
                    Box(Modifier.padding(top = 4.dp).size(10.dp).clip(CircleShape).background(C.accent))
                }
            }
            if (code != null) {
                Spacer(Modifier.height(S.s))
                CodeChip(code)
            }
        }
    }
}

/** One-tap copy for a detected one-time code. */
@Composable
fun CodeChip(code: String) {
    val ctx = LocalContext.current
    var copied by remember { mutableStateOf(false) }
    Row(
        Modifier.height(32.dp).clip(CircleShape).background(C.accentSoft)
            .pressable(Haptic.SUCCESS) { Otp.copy(ctx, code); copied = true }
            .padding(horizontal = S.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.ContentCopy, null, tint = C.accent, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(6.dp))
        Text(if (copied) "Copied" else "Copy $code", style = T.sub.copy(fontWeight = FontWeight.SemiBold).tabular(), color = C.accent)
    }
}

// ============================================================ Thread

@Composable
fun ThreadScreen(store: Store, nav: Nav, threadId: Long, address: String) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val contact = store.directory.find(address)
    val name = contact?.name ?: address
    val messages by produceState(emptyList<Sms>(), threadId, store.smsVersion) {
        value = withContext(Dispatchers.IO) { Repo.thread(ctx, threadId) }
    }
    var sim by remember(threadId) { mutableStateOf<Sim?>(null) }
    val activeSim = sim ?: messages.lastOrNull()?.subId?.let(store::simFor)
        ?: store.simFor(SmsManager.getDefaultSmsSubscriptionId()) ?: store.sims.firstOrNull()

    LaunchedEffect(threadId, messages.size) {
        withContext(Dispatchers.IO) { Repo.markRead(ctx, threadId) }
        ctx.getSystemService(NotificationManager::class.java).cancel(threadId.toInt())
    }

    Column(Modifier.fillMaxSize().background(C.bg).imePadding()) {
        ThreadHeader(name, contact, address, nav)
        val listState = rememberLazyListState()
        LaunchedEffect(messages.size) { if (messages.isNotEmpty()) listState.scrollToItem(messages.lastIndex) }
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = listState, contentPadding = PaddingValues(vertical = S.s)) {
            itemsIndexed(messages, key = { _, m -> m.id }) { i, m ->
                val prev = messages.getOrNull(i - 1)
                val next = messages.getOrNull(i + 1)
                if (prev == null || m.date - prev.date > 60 * 60 * 1000) {
                    Text(
                        "${dayLabel(m.date)} · ${clockTime(m.date)}", style = T.caption.tabular(), color = C.text3, textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(top = S.l, bottom = S.s),
                    )
                }
                val joinsNext = next != null && next.outgoing == m.outgoing && next.date - m.date < 60_000
                Bubble(m, last = !joinsNext) { scope.launch(Dispatchers.IO) { Repo.deleteSms(ctx, m.id) } }
            }
        }
        val showTemplates = messages.lastOrNull()?.outgoing == false
        Composer(store.sims, activeSim, { sim = it }, showTemplates) { text ->
            val s = activeSim ?: return@Composer
            scope.launch(Dispatchers.IO) { Repo.sendSms(ctx, address, text, s.subId) }
        }
    }
}

@Composable
private fun ThreadHeader(name: String, contact: Contact?, address: String, nav: Nav) {
    Column(Modifier.fillMaxWidth().background(C.bg)) {
        Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
        Row(Modifier.fillMaxWidth().padding(horizontal = S.edge, vertical = S.s), verticalAlignment = Alignment.CenterVertically) {
            CircleAction(Icons.AutoMirrored.Rounded.ArrowBack, "Back") { nav.back() }
            Spacer(Modifier.width(S.m))
            Row(
                Modifier.weight(1f).clip(RoundedCornerShape(Radius.tile)).pressable(Haptic.LIGHT) { nav.contact(contact, address) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Avatar(name, contact?.photo, 40.dp)
                Spacer(Modifier.width(S.m))
                Column {
                    Text(name, style = T.headline, color = C.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(NumberIntel.label(address), style = T.caption, color = C.text2, maxLines = 1)
                }
            }
            Spacer(Modifier.width(S.s))
            CircleAction(Icons.Rounded.Call, "Call", tint = C.accent, haptic = Haptic.SUCCESS) { nav.call(address) }
        }
    }
}

@Composable
private fun Bubble(m: Sms, last: Boolean, onDelete: () -> Unit) {
    val out = m.outgoing
    val big = 20.dp
    val tight = 6.dp
    // The corner facing the next bubble from the same sender tightens, so a burst reads as one block.
    val shape = if (out) RoundedCornerShape(big, big, if (last) big else tight, big)
    else RoundedCornerShape(big, big, big, if (last) big else tight)
    val bg = when { !out -> C.raised; m.failed -> C.redSoft; else -> C.accent }
    val fg = if (out && !m.failed) C.onAccent else C.text
    val code = remember(m.body) { if (!out) Otp.find(m.body) else null }
    Column(
        Modifier.fillMaxWidth().padding(horizontal = S.edge, vertical = if (last) 4.dp else 1.dp),
        horizontalAlignment = if (out) Alignment.End else Alignment.Start,
    ) {
        Box(
            Modifier.widthIn(max = 296.dp).clip(shape).rowPress(bg, onLongClick = onDelete) {}.padding(horizontal = 14.dp, vertical = 10.dp),
        ) { Text(m.body, style = T.body, color = fg) }
        if (code != null) {
            Spacer(Modifier.height(6.dp))
            CodeChip(code)
        }
        if (out && last && (m.failed || m.pending)) {
            Text(
                if (m.failed) "Not delivered · long-press to delete" else "Sending…",
                style = T.caption, color = if (m.failed) C.red else C.text3,
                modifier = Modifier.padding(top = S.xs, end = S.xs),
            )
        }
    }
}

