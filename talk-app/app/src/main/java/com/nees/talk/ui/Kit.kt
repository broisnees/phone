package com.nees.talk.ui

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
import com.nees.talk.R
import com.nees.talk.Sim
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

@Composable
fun SearchBar(query: String, onQuery: (String) -> Unit, placeholder: String) {
    Row(
        Modifier.padding(horizontal = S.edge).fillMaxWidth().height(44.dp)
            .clip(RoundedCornerShape(Radius.field)).background(C.raised).padding(horizontal = S.m + S.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Search, null, tint = C.text2, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(S.s))
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) Text(placeholder, style = T.body, color = C.text3)
            BasicTextField(
                query, onQuery, singleLine = true, textStyle = T.body.copy(color = C.text),
                cursorBrush = SolidColor(C.accent), modifier = Modifier.fillMaxWidth(),
            )
        }
        if (query.isNotEmpty()) {
            Icon(Icons.Rounded.Cancel, "Clear", tint = C.text3, modifier = Modifier.size(20.dp).pressable(Haptic.SELECT) { onQuery("") })
        }
    }
}

// ============================================================ Chips & buttons

@Composable
fun Chip(label: String, selected: Boolean, count: Int = 0, onClick: () -> Unit) {
    val bg by animateColorAsState(if (selected) C.accent else C.raised, tween(150, easing = EaseOut), label = "chip")
    val fg by animateColorAsState(if (selected) C.onAccent else C.text2, tween(150, easing = EaseOut), label = "chipText")
    Row(
        Modifier.height(32.dp).clip(CircleShape).background(bg).pressable(Haptic.SELECT, onClick = onClick).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = T.sub.copy(fontWeight = FontWeight.Medium), color = fg, maxLines = 1)
        if (count > 0) {
            Spacer(Modifier.width(6.dp))
            Text("$count", style = T.caption.tabular(), color = fg.copy(alpha = 0.7f))
        }
    }
}

/** Tonal action tile: icon over label, used in rows of equal-width actions. */
@Composable
fun ActionTile(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    tint: Color = C.accent,
    enabled: Boolean = true,
    haptic: Int = Haptic.LIGHT,
    onClick: () -> Unit,
) {
    Column(
        modifier.clip(RoundedCornerShape(Radius.tile)).background(C.card)
            .then(if (enabled) Modifier.pressable(haptic, onClick = onClick) else Modifier.graphicsLayer { alpha = 0.4f })
            .padding(vertical = S.m),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, label, tint = tint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(6.dp))
        Text(label, style = T.caption, color = C.text, maxLines = 1)
    }
}

@Composable
fun SimChip(sims: List<Sim>, selected: Sim?, onSelect: (Sim) -> Unit, modifier: Modifier = Modifier) {
    if (sims.size < 2 || selected == null) return
    Row(
        modifier.height(32.dp).clip(CircleShape).background(C.raised)
            .pressable(Haptic.SELECT) { onSelect(sims[(sims.indexOf(selected) + 1) % sims.size]) }
            .padding(horizontal = S.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(16.dp).clip(RoundedCornerShape(4.dp)).background(C.accentSoft),
            contentAlignment = Alignment.Center,
        ) { Text("${selected.slot + 1}", style = T.caption.copy(fontSize = 10.sp, lineHeight = 12.sp), color = C.accent) }
        Spacer(Modifier.width(6.dp))
        Text(selected.name, style = T.caption, color = C.text, maxLines = 1)
    }
}

// ============================================================ Avatar

private val palette = listOf(
    Color(0xFF7D7AFF), Color(0xFF5AC8D8), Color(0xFFFF9F45), Color(0xFFFF6B8B),
    Color(0xFF64B5FF), Color(0xFFC17BFF), Color(0xFF5FD38A), Color(0xFFD4B483),
)

/** Stable per-name hue, so a person keeps their colour everywhere. */
fun hueFor(name: String): Color = palette[(name.hashCode() and Int.MAX_VALUE) % palette.size]

@Composable
fun Avatar(name: String, photo: String?, size: Dp = S.avatar) {
    val ctx = LocalContext.current
    val bitmap by produceState<android.graphics.Bitmap?>(null, photo) {
        value = photo?.let { p ->
            withContext(Dispatchers.IO) {
                runCatching { ctx.contentResolver.openInputStream(Uri.parse(p))?.use(BitmapFactory::decodeStream) }.getOrNull()
            }
        }
    }
    val hue = hueFor(name)
    Box(
        Modifier.size(size).clip(CircleShape).background(hue.copy(alpha = 0.18f)).border(1.dp, Color.White.copy(alpha = 0.08f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        val bmp = bitmap
        if (bmp != null) {
            Image(bmp.asImageBitmap(), null, Modifier.size(size), contentScale = ContentScale.Crop)
        } else {
            val fs = (size.value * 0.38f).sp
            Text(initials(name), color = hue, style = T.headline.copy(fontSize = fs, lineHeight = fs, letterSpacing = 0.sp))
        }
    }
}

