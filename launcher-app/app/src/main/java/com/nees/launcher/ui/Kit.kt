package com.nees.launcher.ui

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nees.launcher.R

/** Black canvas, charcoal cards, Nothing red accent. */
object C {
    val bg = Color(0xFF000000)
    val scrim = Color(0xE6000000)
    val card = Color(0xFF121214)
    val raised = Color(0xFF1C1C1F)
    val pressed = Color(0xFF26262A)
    val hairline = Color(0x1FFFFFFF)
    val text = Color(0xFFF4F4F6)
    val text2 = Color(0xFF9C9CA3)
    val text3 = Color(0xFF5E5E66)
    val accent = Color(0xFFE8302A)
    val accentSoft = Color(0x33E8302A)
    val onAccent = Color(0xFFFFFFFF)
    val glass = Color(0x401C1C1F)
}

object S {
    val xs = 4.dp
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
    val edge = 16.dp
    val pad = 16.dp
}

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

object T {
    private fun s(size: Int, line: Int, weight: FontWeight, track: Double) =
        TextStyle(fontFamily = Inter, fontSize = size.sp, lineHeight = line.sp, fontWeight = weight, letterSpacing = track.sp)

    val display = s(32, 38, FontWeight.Bold, -1.0)
    val title = s(24, 30, FontWeight.SemiBold, -0.6)
    val headline = s(16, 21, FontWeight.SemiBold, -0.2)
    val body = s(16, 22, FontWeight.Normal, -0.2)
    val sub = s(14, 19, FontWeight.Normal, -0.1)
    val caption = s(12, 16, FontWeight.Medium, 0.0)
    val label = s(11, 14, FontWeight.Medium, 0.1)
    val iconLabel = s(12, 14, FontWeight.Medium, 0.0)
}

val EaseOut = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)

@Composable
fun LauncherTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = C.bg, surface = C.card, primary = C.accent, onPrimary = C.onAccent,
            surfaceContainer = C.card, surfaceContainerHigh = C.raised, onSurface = C.text,
        ),
        content = content,
    )
}

object Haptic {
    const val SELECT = HapticFeedbackConstants.CLOCK_TICK
    const val KEY = HapticFeedbackConstants.KEYBOARD_TAP
    const val LIGHT = HapticFeedbackConstants.VIRTUAL_KEY
    const val SUCCESS = HapticFeedbackConstants.CONFIRM
    const val LONG = HapticFeedbackConstants.LONG_PRESS
    const val NONE = -1
}

@Composable
fun rememberHaptics(): (Int) -> Unit {
    val view = LocalView.current
    return remember(view) { { kind: Int -> if (kind != Haptic.NONE) view.performHapticFeedback(kind) } }
}

/** Shrinks to 0.9 while held, fires its haptic on release. */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.pressable(
    haptic: Int = Haptic.LIGHT,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.9f else 1f, tween(120, easing = EaseOut), label = "press")
    val view = LocalView.current
    graphicsLayer { scaleX = scale; scaleY = scale }.combinedClickable(
        interactionSource = source,
        indication = null,
        onLongClick = onLongClick?.let { { view.performHapticFeedback(Haptic.LONG); it() } },
        onClick = { if (haptic != Haptic.NONE) view.performHapticFeedback(haptic); onClick() },
    )
}

fun Modifier.glassCard() = this
    .background(C.glass, RoundedCornerShape(Radius.card))
