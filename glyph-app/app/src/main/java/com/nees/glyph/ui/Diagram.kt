package com.nees.glyph.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import com.nees.glyph.Zone
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

private val off = Color(0xFF2A2A2E)
private const val ASPECT = 0.48f

/** Geometry of the Phone (1) back, in fractions of the drawing box. */
private object Geo {
    val camera = Offset(0.30f, 0.11f)
    const val cameraR = 0.14f
    val diagA = Offset(0.62f, 0.07f)
    val diagB = Offset(0.80f, 0.22f)
    val ring = Offset(0.5f, 0.47f)
    const val ringR = 0.27f
    const val stripTop = 0.70f
    const val stripBottom = 0.90f
    val dot = Offset(0.5f, 0.945f)
    /** Ring arcs clockwise from the top-right: start angle in degrees. */
    val ringStarts = listOf(-85f, 5f, 95f, 185f)
}

