package com.nees.talk.call

import android.content.Intent
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.os.PowerManager
import android.telecom.Call
import android.telecom.CallAudioState
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.Contacts
import androidx.compose.material.icons.rounded.Dialpad
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nees.talk.MainActivity
import com.nees.talk.NumberIntel
import com.nees.talk.ui.Avatar
import com.nees.talk.ui.C
import com.nees.talk.ui.EaseOut
import com.nees.talk.ui.Haptic
import com.nees.talk.ui.Radius
import com.nees.talk.ui.S
import com.nees.talk.ui.T
import com.nees.talk.ui.TalkTheme
import com.nees.talk.ui.hueFor
import com.nees.talk.ui.pressable
import com.nees.talk.ui.tabular
import kotlinx.coroutines.delay

class InCallActivity : ComponentActivity() {
    private var proximity: PowerManager.WakeLock? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handle(intent)
        proximity = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, "talk:proximity")
        setContent { TalkTheme { InCallScreen(onDone = ::finish, onProximity = ::setProximity) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        if (intent?.action == ACTION_ANSWER) CallHub.answer()
    }

    /** Screen goes dark against the ear only while audio is on the earpiece. */
    private fun setProximity(on: Boolean) {
        val lock = proximity ?: return
        if (on && !lock.isHeld) lock.acquire(60 * 60 * 1000L)
        if (!on && lock.isHeld) lock.release(PowerManager.RELEASE_FLAG_WAIT_FOR_NO_PROXIMITY)
    }

    override fun onDestroy() {
        setProximity(false)
        super.onDestroy()
    }

    companion object {
        const val ACTION_ANSWER = "com.nees.talk.ANSWER"
    }
}

@Composable
private fun InCallScreen(onDone: () -> Unit, onProximity: (Boolean) -> Unit) {
    val ctx = LocalContext.current
    val calls by CallHub.calls.collectAsState()
    val version by CallHub.version.collectAsState()
    val audio by CallHub.audio.collectAsState()
    val call = remember(calls, version) { CallHub.primary() }

    LaunchedEffect(calls.isEmpty()) {
        if (calls.isEmpty()) {
            delay(600) // let "Call ended" register before closing
            onDone()
        }
    }
    if (call == null) {
        Box(Modifier.fillMaxSize().background(C.bg), contentAlignment = Alignment.Center) {
            Text("Call ended", style = T.headline, color = C.text2)
        }
        return
    }

    val state = call.details.state
    val ringing = state == Call.STATE_RINGING
    val route = audio?.route ?: CallAudioState.ROUTE_EARPIECE
    val live = state == Call.STATE_ACTIVE || state == Call.STATE_DIALING || state == Call.STATE_CONNECTING
    LaunchedEffect(live, route) { onProximity(live && route == CallAudioState.ROUTE_EARPIECE) }

    val name = remember(call) { CallHub.name(ctx, call) }
    val number = CallHub.number(call)
    val info = remember(number) { if (number.isNotEmpty()) NumberIntel.label(number) else "" }
    var showKeys by remember { mutableStateOf(false) }
    val glow = hueFor(name)

    Column(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(0f to glow.copy(alpha = 0.22f), 0.55f to C.bg))
            .statusBarsPadding().navigationBarsPadding().padding(horizontal = S.edge),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(56.dp))
        if (!showKeys) {
            Avatar(name, null, 104.dp)
            Spacer(Modifier.height(S.xl))
        }
        Text(
            name, style = T.display.copy(fontWeight = FontWeight.SemiBold), color = C.text,
            maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(S.s))
        StatusLine(call)
        if (info.isNotEmpty() && name != number) {
            Spacer(Modifier.height(S.xs))
            Text("$number · $info", style = T.sub.tabular(), color = C.text3)
        } else if (info.isNotEmpty()) {
            Spacer(Modifier.height(S.xs))
            Text(info, style = T.sub, color = C.text3)
        }

        Spacer(Modifier.weight(1f))
        AnimatedContent(
            targetState = when { ringing -> 0; showKeys -> 1; else -> 2 },
            transitionSpec = { fadeIn(tween(200, easing = EaseOut)) togetherWith fadeOut(tween(120)) },
            label = "incall",
        ) { mode ->
            when (mode) {
                0 -> IncomingActions()
                1 -> InCallKeypad(call) { showKeys = false }
                else -> ActiveActions(call, audio, onKeys = { showKeys = true })
            }
        }
        Spacer(Modifier.height(S.xxl))
    }
}

@Composable
private fun StatusLine(call: Call) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(call) { while (true) { now = System.currentTimeMillis(); delay(500) } }
    val connect = call.details.connectTimeMillis
    val text = when (call.details.state) {
        Call.STATE_RINGING -> "Incoming call"
        Call.STATE_DIALING, Call.STATE_CONNECTING, Call.STATE_NEW -> "Calling…"
        Call.STATE_HOLDING -> "On hold"
        Call.STATE_DISCONNECTING, Call.STATE_DISCONNECTED -> "Call ended"
        Call.STATE_ACTIVE -> if (connect > 0) duration(now - connect) else "Connected"
        else -> ""
    }
    Text(text, style = T.headline.copy(fontWeight = FontWeight.Medium).tabular(), color = C.text2)
}

private fun duration(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%02d:%02d".format(s / 60, s % 60)
}

@Composable
private fun IncomingActions() {
    Row(Modifier.fillMaxWidth().padding(horizontal = S.xxl), horizontalArrangement = Arrangement.SpaceBetween) {
        PhoneButton(Icons.Rounded.CallEnd, "Decline", C.red, Haptic.REJECT) { CallHub.hangUp() }
        PhoneButton(Icons.Rounded.Call, "Accept", C.green, Haptic.SUCCESS) { CallHub.answer() }
    }
}

@Composable
private fun PhoneButton(icon: ImageVector, label: String, color: Color, haptic: Int, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(72.dp).clip(CircleShape).background(color).pressable(haptic, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, label, tint = Color.White, modifier = Modifier.size(30.dp)) }
        Spacer(Modifier.height(S.s))
        Text(label, style = T.caption, color = C.text)
    }
}

/** Soft square tile; filled white when its state is on. */
@Composable
private fun CallTile(icon: ImageVector, label: String, active: Boolean = false, enabled: Boolean = true, modifier: Modifier, onClick: () -> Unit) {
    val bg by animateColorAsState(if (active) C.text else Color.White.copy(alpha = 0.08f), tween(150, easing = EaseOut), label = "tile")
    val fg by animateColorAsState(if (active) C.bg else C.text, tween(150, easing = EaseOut), label = "tileFg")
    Column(
        modifier.height(84.dp).clip(RoundedCornerShape(Radius.card)).background(bg)
            .then(if (enabled) Modifier.pressable(Haptic.SELECT, onClick = onClick) else Modifier.graphicsLayer { alpha = 0.35f }),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, label, tint = fg, modifier = Modifier.size(24.dp))
        Spacer(Modifier.height(6.dp))
        Text(label, style = T.caption, color = fg)
    }
}

