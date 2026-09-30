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

private fun ringArcMid(i: Int): Float = Geo.ringStarts[i] + 40f

/** Anchor point used for tap hit-testing, in fractions. */
private fun anchor(z: Zone): Offset = when (z) {
    Zone.A -> Offset(Geo.camera.x + Geo.cameraR * 0.9f, Geo.camera.y + Geo.cameraR * 0.4f)
    Zone.B -> Offset((Geo.diagA.x + Geo.diagB.x) / 2, (Geo.diagA.y + Geo.diagB.y) / 2)
    Zone.E -> Geo.dot
    in Zone.ring -> {
        val a = Math.toRadians(ringArcMid(Zone.ring.indexOf(z)).toDouble())
        // The ring is a true circle in pixels, so its y-offset in height-fractions shrinks by the aspect ratio.
        Offset(Geo.ring.x + Geo.ringR * cos(a).toFloat(), Geo.ring.y + Geo.ringR * sin(a).toFloat() * ASPECT)
    }
    else -> {
        val i = Zone.strip.indexOf(z)
        val seg = (Geo.stripBottom - Geo.stripTop) / Zone.strip.size
        Offset(0.5f, Geo.stripBottom - seg * (i + 0.5f))
    }
}

/**
 * The 15 Glyph zones drawn as on the phone's back. [levels] are percent per zone; zones light up
 * white with a soft glow. Tapping a zone calls [onZone].
 */
@Composable
fun GlyphDiagram(levels: IntArray, modifier: Modifier = Modifier, onZone: ((Zone) -> Unit)? = null) {
    Canvas(
        modifier.aspectRatio(ASPECT).then(
            if (onZone == null) Modifier else Modifier.pointerInput(Unit) {
                detectTapGestures { p ->
                    val fx = p.x / size.width
                    val fy = p.y / size.height
                    // Distance in width units: a height-fraction is 1/ASPECT width-fractions long.
                    val hit = Zone.entries.minByOrNull { z -> anchor(z).let { hypot((it.x - fx).toDouble(), ((it.y - fy) / ASPECT).toDouble()) } }
                    if (hit != null) onZone(hit)
                }
            },
        ),
    ) {
        val w = size.width
        val h = size.height
        // Body.
        drawRoundRect(Color(0xFF0E0E10), cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.12f))
        drawRoundRect(Color(0x14FFFFFF), cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.12f), style = Stroke(1.5f))
        // Camera module (not a glyph, just context).
        drawCircle(Color(0xFF1C1C1F), radius = w * 0.085f, center = Offset(Geo.camera.x * w - w * 0.03f, Geo.camera.y * h - h * 0.012f))

        val stroke = w * 0.035f
        fun color(z: Zone) = levels.getOrElse(z.ordinal) { 0 }.let { if (it <= 0) off else Color.White.copy(alpha = 0.35f + 0.65f * it / 100f) }
        fun lit(z: Zone) = levels.getOrElse(z.ordinal) { 0 } > 0

        // A: camera arc.
        val camR = Geo.cameraR * w
        drawGlowArc(lit(Zone.A), color(Zone.A), Offset(Geo.camera.x * w, Geo.camera.y * h), camR, -60f, 200f, stroke)
        // B: diagonal.
        drawGlowLine(lit(Zone.B), color(Zone.B), Offset(Geo.diagA.x * w, Geo.diagA.y * h), Offset(Geo.diagB.x * w, Geo.diagB.y * h), stroke)
        // C: ring quarters.
        Zone.ring.forEachIndexed { i, z ->
            drawGlowArc(lit(z), color(z), Offset(Geo.ring.x * w, Geo.ring.y * h), Geo.ringR * w, Geo.ringStarts[i], 80f, stroke)
        }
        // D: strip, bottom (D1) to top (D8).
        val seg = (Geo.stripBottom - Geo.stripTop) * h / Zone.strip.size
        Zone.strip.forEachIndexed { i, z ->
            val y1 = Geo.stripBottom * h - seg * i - seg * 0.12f
            val y2 = Geo.stripBottom * h - seg * (i + 1) + seg * 0.12f
            drawGlowLine(lit(z), color(z), Offset(w / 2, y1), Offset(w / 2, y2), stroke)
        }
        // E: dot.
        val dc = Offset(Geo.dot.x * w, Geo.dot.y * h)
        if (lit(Zone.E)) drawCircle(color(Zone.E).copy(alpha = 0.25f), radius = stroke * 1.6f, center = dc)
        drawCircle(color(Zone.E), radius = stroke * 0.7f, center = dc)
    }
}

