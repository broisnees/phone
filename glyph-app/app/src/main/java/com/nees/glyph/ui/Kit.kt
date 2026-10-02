package com.nees.glyph.ui

import android.graphics.BitmapFactory
import android.net.Uri
import android.view.HapticFeedbackConstants
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nees.glyph.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.roundToInt

// ============================================================ Tokens

/** Black canvas, soft charcoal cards, one warm accent. */
object C {
    val bg = Color(0xFF000000)
    val card = Color(0xFF121214)
    val raised = Color(0xFF1C1C1F)
    val pressed = Color(0xFF26262A)
    val hairline = Color(0x14FFFFFF)
    val text = Color(0xFFF4F4F6)
    val text2 = Color(0xFF9C9CA3)
    val text3 = Color(0xFF5E5E66)
    val accent = Color(0xFFFFB547)
    val accentSoft = Color(0x29FFB547)
    val onAccent = Color(0xFF1C1300)
    val green = Color(0xFF34C759)
    val red = Color(0xFFFF5A4E)
    val redSoft = Color(0x29FF5A4E)
}

/** Spacing steps. Inside a group: s/m. Between groups: l/xl (always ≥ 2× the inner gap). */
object S {
    val xs = 4.dp
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
    /** Screen margin; every card and title aligns to this edge. */
    val edge = 16.dp
    /** Card inner padding; text inside cards and section labels align to edge + pad. */
    val pad = 16.dp
    val avatar = 44.dp
}

/** Radii are concentric: outer = inner + padding. */
object Radius {
    val card = 24.dp
    val tile = 20.dp
    val field = 22.dp
}

val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
)

/** Seven steps, nothing in between. Large type tightens; the uppercase label opens up. */
object T {
    private fun s(size: Int, line: Int, weight: FontWeight, track: Double) =
        TextStyle(fontFamily = Inter, fontSize = size.sp, lineHeight = line.sp, fontWeight = weight, letterSpacing = track.sp)

    val display = s(32, 38, FontWeight.Bold, -1.0)
    val title = s(24, 30, FontWeight.SemiBold, -0.6)
    val headline = s(16, 21, FontWeight.SemiBold, -0.2)
    val body = s(16, 22, FontWeight.Normal, -0.2)
    val sub = s(14, 19, FontWeight.Normal, -0.1)
    val caption = s(12, 16, FontWeight.Medium, 0.0)
    val label = s(12, 16, FontWeight.SemiBold, 0.6)
}

fun TextStyle.tabular() = copy(fontFeatureSettings = "tnum")

/** Strong ease-out: movement starts immediately, settles softly. */
val EaseOut = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)

@Composable
fun TalkTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = C.bg, surface = C.card, primary = C.accent, onPrimary = C.onAccent,
            surfaceContainer = C.card, surfaceContainerHigh = C.raised, onSurface = C.text,
        ),
        content = content,
    )
}

// ============================================================ Haptics

/**
 * One meaning, one feeling (Apple/Pixel vocabulary), all clear system effects that follow the
 * user's intensity setting. Ordinary scrolling and row taps get nothing.
 */
object Haptic {
    /** Lightest: tabs, chips, toggles, swipe threshold. */
    const val SELECT = HapticFeedbackConstants.CLOCK_TICK
    /** Keys on the dial pad. */
    const val KEY = HapticFeedbackConstants.KEYBOARD_TAP
    /** Light impact: opening something. */
    const val LIGHT = HapticFeedbackConstants.VIRTUAL_KEY
    /** Success: send, call, copy. */
    const val SUCCESS = HapticFeedbackConstants.CONFIRM
    /** Destructive / end call. */
    const val REJECT = HapticFeedbackConstants.REJECT
    const val LONG = HapticFeedbackConstants.LONG_PRESS
    const val NONE = -1
}

@Composable
fun rememberHaptics(): (Int) -> Unit {
    val view = LocalView.current
    return remember(view) { { kind: Int -> if (kind != Haptic.NONE) view.performHapticFeedback(kind) } }
}

// ============================================================ Press feedback

/** Shrinks to 0.96 while held (120 ms ease-out), fires its haptic on release. */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.pressable(
    haptic: Int = Haptic.LIGHT,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f, tween(120, easing = EaseOut), label = "press")
    val view = LocalView.current
    graphicsLayer { scaleX = scale; scaleY = scale }.combinedClickable(
        interactionSource = source,
        indication = null,
        onLongClick = onLongClick?.let { { view.performHapticFeedback(Haptic.LONG); it() } },
        onClick = { if (haptic != Haptic.NONE) view.performHapticFeedback(haptic); onClick() },
    )
}

/** Rows inside cards: the fill brightens while held instead of scaling. No haptic (restraint). */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.rowPress(
    base: Color = C.card,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val color by animateColorAsState(if (pressed) C.pressed else base, tween(100), label = "row")
    val view = LocalView.current
    background(color).combinedClickable(
        interactionSource = source,
        indication = null,
        onLongClick = onLongClick?.let { { view.performHapticFeedback(Haptic.LONG); it() } },
        onClick = onClick,
    )
}

// ============================================================ Cards

/** Shape for the i-th of n rows that together form one card in a lazy list. */
fun sliceShape(i: Int, n: Int): Shape = when {
    n == 1 -> RoundedCornerShape(Radius.card)
    i == 0 -> RoundedCornerShape(topStart = Radius.card, topEnd = Radius.card)
    i == n - 1 -> RoundedCornerShape(bottomStart = Radius.card, bottomEnd = Radius.card)
    else -> RoundedCornerShape(0.dp)
}

/** Places a row as slice [i] of an [n]-row card. */
fun Modifier.cardSlice(i: Int, n: Int): Modifier = padding(horizontal = S.edge).clip(sliceShape(i, n))

@Composable
fun Card(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(modifier.padding(horizontal = S.edge).fillMaxWidth().clip(RoundedCornerShape(Radius.card)).background(C.card)) { content() }
}

/** Hairline between rows, inset to where the row's text starts. */
@Composable
fun Hairline(start: Dp = S.pad + S.avatar + S.m) {
    Box(Modifier.fillMaxWidth().background(C.card).padding(start = start, end = S.pad).height(0.5.dp).background(C.hairline))
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(), style = T.label, color = C.text3,
        modifier = modifier.fillMaxWidth().background(C.bg)
            .padding(start = S.edge + S.pad, end = S.edge + S.pad, top = S.xl, bottom = S.s),
    )
}

// ============================================================ Header

/** Screen title on the shared edge, with circular actions on the trailing side. */
@Composable
fun ScreenTitle(title: String, actions: @Composable () -> Unit = {}) {
    Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
    Row(
        Modifier.fillMaxWidth().padding(start = S.edge + S.xs, end = S.edge, top = S.l, bottom = S.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = T.display, color = C.text, modifier = Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(S.s)) { actions() }
    }
}

@Composable
fun CircleAction(icon: ImageVector, description: String, tint: Color = C.text, haptic: Int = Haptic.LIGHT, onClick: () -> Unit) {
    Box(
        Modifier.size(40.dp).clip(CircleShape).background(C.raised).pressable(haptic, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, description, tint = tint, modifier = Modifier.size(20.dp)) }
}

