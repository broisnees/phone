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
