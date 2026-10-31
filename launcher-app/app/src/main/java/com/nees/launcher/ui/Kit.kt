package com.nees.launcher.ui

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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

/** Pure dark, true black, monochrome. Type carries the whole design; no cards, no colour. */
object C {
    val bg = Color(0xFF000000)
    val text = Color(0xFFF2F2F2)
    val text2 = Color(0xFF8A8A8E)
    val text3 = Color(0xFF5A5A5E)
    val hairline = Color(0x14FFFFFF)
    val scrim = Color(0xF2000000)
    // Monochrome "accent" is just white; kept so call sites read clearly.
    val accent = Color(0xFFF2F2F2)
}

object S {
    val xs = 4.dp
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
    val edge = 28.dp
}

val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
)

/** Big clean type with tight tracking on the large sizes. */
object T {
    private fun s(size: Int, line: Int, weight: FontWeight, track: Double) =
        TextStyle(fontFamily = Inter, fontSize = size.sp, lineHeight = line.sp, fontWeight = weight, letterSpacing = track.sp)

    val clock = s(72, 76, FontWeight.Bold, -3.0)
    val display = s(34, 40, FontWeight.SemiBold, -1.2)
    val fav = s(26, 34, FontWeight.Medium, -0.8)
    val title = s(22, 28, FontWeight.SemiBold, -0.6)
    val list = s(19, 26, FontWeight.Normal, -0.4)
    val body = s(16, 22, FontWeight.Normal, -0.2)
    val sub = s(14, 19, FontWeight.Normal, -0.1)
    val label = s(12, 16, FontWeight.Medium, 1.0)
}

fun TextStyle.tabular() = copy(fontFeatureSettings = "tnum")

val EaseOut = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)

@Composable
fun LauncherTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = C.bg, surface = C.bg, primary = C.text, onPrimary = C.bg,
            surfaceContainer = C.bg, surfaceContainerHigh = C.bg, onSurface = C.text,
        ),
        content = content,
    )
}

object Haptic {
    const val SELECT = HapticFeedbackConstants.CLOCK_TICK
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

/** Text rows dim slightly while pressed instead of scaling — calmer for a text UI. */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.tappable(
    haptic: Int = Haptic.LIGHT,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val alpha by animateFloatAsState(if (pressed) 0.45f else 1f, tween(110, easing = EaseOut), label = "press")
    val view = LocalView.current
    graphicsLayer { this.alpha = alpha }.combinedClickable(
        interactionSource = source,
        indication = null,
        onLongClick = onLongClick?.let { { view.performHapticFeedback(Haptic.LONG); it() } },
        onClick = { if (haptic != Haptic.NONE) view.performHapticFeedback(haptic); onClick() },
    )
}
