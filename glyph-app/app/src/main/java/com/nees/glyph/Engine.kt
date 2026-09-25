package com.nees.glyph

import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock

/** Higher ordinal wins when two effects want the LEDs at once. */
enum class Layer { Ambient, Visualizer, Meter, Timer, Torch, Notification, Call, Indicator }

/** A frame source: returns per-zone percent (0–100) for time [now], or null when finished. */
typealias Source = (now: Long) -> IntArray?

/**
 * Composites layers into one frame and pushes it to [Hw] at up to ~30 fps. The ticker only
 * runs while at least one layer is active; with nothing to show it stops completely.
 */
object Engine {
    private val thread = HandlerThread("glyph-engine").apply { start() }
    private val handler = Handler(thread.looper)
    private val sources = arrayOfNulls<Source>(Layer.entries.size)
    private var running = false
    private var lastFrame: IntArray? = null

    /** Global ceiling (0–100) applied to everything except the torch. */
    @Volatile var ceiling = 70
    @Volatile var torchLevel = 100
    /** Called whenever the set of active layers changes. */
    @Volatile var onActiveChanged: ((Set<Layer>) -> Unit)? = null

    fun set(layer: Layer, source: Source?) = handler.post {
        sources[layer.ordinal] = source
        notifyActive()
        // Draw the new state now rather than on the next 33 ms tick, so key presses feel instant.
        render()
        if (source != null) start()
    }

    fun clear(layer: Layer) = set(layer, null)

    fun isActive(layer: Layer) = sources[layer.ordinal] != null

    /** Play [pattern] once (or looped until cleared). */
    fun play(layer: Layer, pattern: Pattern, loop: Boolean = false) {
        if (pattern.steps.isEmpty()) return
        val start = SystemClock.uptimeMillis()
        val total = pattern.duration.coerceAtLeast(1)
        set(layer) { now ->
            val t = now - start
            if (!loop && t >= total) null else {
                var rem = (t % total).toInt()
                var frame = pattern.steps.last().levels
                for (s in pattern.steps) {
                    if (rem < s.ms) { frame = s.levels; break }
                    rem -= s.ms
                }
                frame
            }
        }
    }

    /** Hold a frame for [ms], fading out over the last 300 ms. */
    fun flash(layer: Layer, frame: IntArray, ms: Long) {
        val end = SystemClock.uptimeMillis() + ms
        set(layer) { now ->
            val left = end - now
            when {
                left <= 0 -> null
                left >= 300 -> frame
                else -> IntArray(frame.size) { (frame[it] * left / 300).toInt() }
            }
        }
    }

    private fun start() {
        if (running) return
        running = true
        handler.post(tick)
    }

    private val tick = object : Runnable {
        override fun run() {
            render()
            if (sources.any { it != null }) handler.postDelayed(this, 33) else running = false
        }
    }

    private fun render() {
        val now = SystemClock.uptimeMillis()
        var frame: IntArray? = null
        var torch = false
        for (i in sources.indices.reversed()) {
            val src = sources[i] ?: continue
            val f = runCatching { src(now) }.getOrNull()
            if (f == null) { sources[i] = null; notifyActive(); continue }
            frame = f
            torch = i == Layer.Torch.ordinal
            break
        }
        val scale = if (torch) torchLevel else ceiling
        val out = IntArray(Zone.COUNT) { z -> ((frame?.getOrNull(z) ?: 0) * scale * Hw.MAX / 10_000) }
        if (!out.contentEquals(lastFrame)) {
            Hw.write(out)
            lastFrame = out
        }
    }

