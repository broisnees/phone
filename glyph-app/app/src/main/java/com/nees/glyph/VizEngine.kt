package com.nees.glyph

import android.os.SystemClock
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

/**
 * Turns FFT captures (~20/s) into glyph frames (~30/s).
 *
 * Six log-spaced bands, each auto-levelled between a slowly rising floor and a decaying ceiling so
 * quiet and loud tracks both use the full range. Levels rise instantly and fall on a time constant,
 * evaluated at render time, so the lights glide between captures instead of stepping.
 *
 * Layout: strip = loudness VU with a peak-hold segment · ring = 4-band circular spectrum ·
 * diagonal + dot = kick detection · camera = hi-hat / cymbal transients.
 */
enum class VizStyle(val label: String, val blurb: String) {
    Spectrum("Spectrum", "Strip meter, ring spectrum, kicks and hi-hats"),
    Pulse("Pulse", "The whole back breathes; kicks bloom outward"),
    Orbit("Orbit", "Light spins round the ring with the energy"),
    Wave("Shockwave", "Every kick sends a wave up the phone"),
}

class VizEngine {
    @Volatile var style = VizStyle.Spectrum
    /** Called on each new kick (capture thread). */
    @Volatile var onKick: (() -> Unit)? = null
    private val waves = ArrayList<Long>()
    private var orbit = 0f
    private var lastFrameAt = 0L
    private val edges = floatArrayOf(30f, 60f, 150f, 400f, 1200f, 3500f, 12000f)
    private val bands = edges.size - 1
    private val floor = FloatArray(bands) { 1f }
    private val ceil = FloatArray(bands) { 3f }
    private val level = FloatArray(bands)
    private var loud = 0f
    private var peak = 0f
    private var peakAt = 0L
    private var kick = 0f
    private var hat = 0f
    private var bassAvg = 0f
    private var trebleAvg = 0f
    private var updatedAt = 0L

    /** Sensitivity 0–100 (settings). */
    @Volatile var sensitivity = 60

    fun capture(fft: ByteArray, samplingRateMilliHz: Int) {
        val n = fft.size / 2
        val hzPerBin = samplingRateMilliHz / 1000f / fft.size
        val raw = FloatArray(bands) { b ->
            val a = (edges[b] / hzPerBin).toInt().coerceIn(1, n - 1)
            val z = (edges[b + 1] / hzPerBin).toInt().coerceIn(a + 1, n)
            var sum = 0f
            for (k in a until z) sum += hypot(fft[2 * k].toFloat(), fft[2 * k + 1].toFloat())
            // Log magnitude: perceptually even, and keeps a single loud band from flattening the rest.
            ln(1f + sum / (z - a))
        }
        val gain = 0.6f + sensitivity / 100f
        synchronized(this) {
            decayTo(SystemClock.uptimeMillis())
            for (b in 0 until bands) {
                floor[b] = if (raw[b] < floor[b]) raw[b] else floor[b] + (raw[b] - floor[b]) * 0.002f
                ceil[b] = max(raw[b], ceil[b] * 0.997f).coerceAtLeast(floor[b] + 0.6f)
                val norm = ((raw[b] - floor[b]) / (ceil[b] - floor[b]) * gain).coerceIn(0f, 1f)
                level[b] = max(level[b], norm) // instant attack
            }
            val l = (level[0] * 0.25f + level[1] * 0.35f + level[2] * 0.25f + level[3] * 0.15f).coerceIn(0f, 1f)
            loud = max(loud, l)
            if (loud >= peak) { peak = loud; peakAt = updatedAt }

            // Kick: bass spectral flux well above its own recent average.
            val bass = raw[0] + raw[1]
            if (bass > bassAvg * 1.18f && level[1] > 0.55f) {
                if (kick < 0.5f) { waves += updatedAt; orbit += 1f; onKick?.invoke() } // a new hit, not the tail of the last one
                kick = 1f
            }
            bassAvg = bassAvg * 0.88f + bass * 0.12f
            // Hi-hat: sudden treble energy.
            if (raw[5] > trebleAvg * 1.25f && level[5] > 0.5f) hat = 1f
            trebleAvg = trebleAvg * 0.85f + raw[5] * 0.15f
        }
    }

    /** Current frame (percent per zone), decayed to [now]. */
    fun frame(now: Long): IntArray = synchronized(this) {
        decayTo(now)
        val dt = if (lastFrameAt == 0L) 0f else (now - lastFrameAt).coerceIn(0, 100) / 1000f
        lastFrameAt = now
        when (style) {
            VizStyle.Spectrum -> spectrum()
            VizStyle.Pulse -> pulse()
            VizStyle.Orbit -> orbit(dt)
            VizStyle.Wave -> wave(now)
        }
    }

    /** Whole phone glows with loudness; a kick blooms from the ring outward. */
    private fun pulse(): IntArray {
        val base = (loud * 55).toInt()
        return IntArray(Zone.COUNT) { z ->
            // Ring is the centre of the bloom; lights further along the path catch less of it.
            val dist = kotlin.math.abs(Zone.path[z] - 10f) / 10f
            (base + kick * 100 * (1f - dist * 0.6f)).toInt().coerceIn(0, 100)
        }
    }

    /** A bright head circles the ring; speed follows energy, kicks jump it a quarter turn. */
    private fun orbit(dt: Float): IntArray {
        orbit = (orbit + dt * (0.6f + loud * 3.5f)) % 4f
        val f = IntArray(Zone.COUNT)
        Zone.ring.forEachIndexed { i, z ->
            var d = kotlin.math.abs(i - orbit); d = minOf(d, 4f - d)
            f[z.ordinal] = ((1f - d / 1.6f).coerceAtLeast(0f) * 100).toInt()
        }
        Engine.stripLevel(loud * 0.7f).forEachIndexed { i, x -> if (x > 0) f[i] = x / 2 }
        f[Zone.A.ordinal] = (hat * 100).toInt()
        f[Zone.B.ordinal] = (hat * 70).toInt()
        f[Zone.E.ordinal] = (kick * 100).toInt()
        return f
    }

    /** Each kick launches a band of light travelling dot → camera; waves overlap. */
    private fun wave(now: Long): IntArray {
        val f = IntArray(Zone.COUNT)
        val speed = 26f // path units per second
        val width = 2.2f
        waves.removeAll { now - it > 900 }
        for (start in waves) {
            val front = (now - start) / 1000f * speed
            val fade = 1f - (now - start) / 900f
            for (z in 0 until Zone.COUNT) {
                val d = kotlin.math.abs(Zone.path[z] - front)
                if (d < width) f[z] = maxOf(f[z], ((1f - d / width) * fade * 100).toInt())
            }
        }
        // A faint bed of loudness so quiet passages aren't dark.
        val bed = (loud * 18).toInt()
        for (z in 0 until Zone.COUNT) f[z] = maxOf(f[z], bed)
        return f
    }

    private fun spectrum(): IntArray {
        val f = IntArray(Zone.COUNT)
        // Strip: VU fill plus a held peak segment that drops after 400 ms.
        val fill = Engine.stripLevel(loud)
        fill.copyInto(f)
        val peakSeg = (peak * Zone.strip.size).toInt().coerceIn(0, Zone.strip.size - 1)
        if (peak > 0.08f) f[Zone.strip[peakSeg].ordinal] = 100
        // Ring: bass → low-mid → mid → upper-mid, clockwise.
        Zone.ring.forEachIndexed { i, z -> f[z.ordinal] = (level[i + 1] * 100).toInt() }
        f[Zone.B.ordinal] = (kick * 100).toInt()
        f[Zone.E.ordinal] = if (kick > 0.6f) 100 else 0
        f[Zone.A.ordinal] = (hat * 100).toInt()
        return f
    }

    /** Exponential fall since the last update: bands 140 ms, loudness 180 ms, transients 90 ms. */
    private fun decayTo(now: Long) {
        val dt = (now - updatedAt).coerceIn(0, 500).toFloat()
        if (updatedAt != 0L && dt > 0f) {
            val band = exp(-dt / 140f)
            for (b in 0 until bands) level[b] *= band
            loud *= exp(-dt / 180f)
            kick *= exp(-dt / 90f)
            hat *= exp(-dt / 90f)
            if (now - peakAt > 400) peak = max(loud, peak - dt / 900f)
        }
        updatedAt = now
    }

    fun reset() = synchronized(this) {
        level.fill(0f); loud = 0f; peak = 0f; kick = 0f; hat = 0f; updatedAt = 0L
        floor.fill(1f); ceil.fill(3f); bassAvg = 0f; trebleAvg = 0f
        waves.clear(); orbit = 0f; lastFrameAt = 0L
    }

    companion object {
        /** Perceptual brightness: 0–1 in, 0–1 out, so low settings get genuinely dim. */
        fun perceptual(x: Float): Float = min(1f, max(0f, x)).let { it * it }
    }
}
