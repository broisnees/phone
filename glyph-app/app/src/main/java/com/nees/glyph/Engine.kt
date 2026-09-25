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

