package com.nees.talk

import android.Manifest
import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.offset
import androidx.compose.animation.animateColorAsState
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Dialpad
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nees.talk.sms.ReplyReceiver
import com.nees.talk.ui.C
import com.nees.talk.ui.ContactDetail
import com.nees.talk.ui.ContactsScreen
import com.nees.talk.ui.Haptic
import com.nees.talk.ui.KeypadScreen
import com.nees.talk.ui.ChatsScreen
import com.nees.talk.ui.Nav
import com.nees.talk.ui.NewMessageScreen
import com.nees.talk.ui.CallsScreen
import com.nees.talk.ui.EaseOut
import com.nees.talk.ui.S
import com.nees.talk.ui.Radius
import com.nees.talk.ui.Card
import com.nees.talk.ui.Hairline
import com.nees.talk.ui.rowPress
import com.nees.talk.ui.tabular
import com.nees.talk.ui.Store
import com.nees.talk.ui.T
import com.nees.talk.ui.TalkTheme
import com.nees.talk.ui.ThreadScreen
import com.nees.talk.ui.pressable
import com.nees.talk.ui.rememberHaptics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface Route {
    data class ContactRoute(val id: Long?, val number: String?) : Route
    data class ThreadRoute(val threadId: Long, val address: String) : Route
    data object NewMessage : Route
}

enum class Tab(val label: String, val on: ImageVector, val off: ImageVector) {
    Calls("Calls", Icons.Filled.Call, Icons.Outlined.Call),
    Chats("Chats", Icons.Filled.ChatBubble, Icons.Outlined.ChatBubbleOutline),
    Contacts("Contacts", Icons.Filled.AccountCircle, Icons.Outlined.AccountCircle),
    Keypad("Keypad", Icons.Filled.Dialpad, Icons.Outlined.Dialpad),
}

class MainActivity : ComponentActivity() {
    /** Latest launch intent; Compose reacts to each new one. */
    private val launch = mutableStateOf<Intent?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        launch.value = intent
        setContent { TalkTheme { Root(launch.value) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        launch.value = intent
    }

    companion object {
        const val ACTION_OPEN_THREAD = "com.nees.talk.OPEN_THREAD"
        const val ACTION_KEYPAD = "com.nees.talk.KEYPAD"
        const val ACTION_CONTACTS = "com.nees.talk.CONTACTS"

        val PERMISSIONS = arrayOf(
            Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS,
            Manifest.permission.READ_CALL_LOG, Manifest.permission.CALL_PHONE,
            Manifest.permission.READ_PHONE_STATE, Manifest.permission.READ_PHONE_NUMBERS,
            Manifest.permission.READ_SMS, Manifest.permission.SEND_SMS, Manifest.permission.RECEIVE_SMS,
            Manifest.permission.POST_NOTIFICATIONS,
        )
    }
}

