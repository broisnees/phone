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

