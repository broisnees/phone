package com.nees.glyph

import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock

/** Higher ordinal wins when two effects want the LEDs at once. */
enum class Layer { Busy, Ambient, Visualizer, Meter, Timer, Torch, Notification, Call, Indicator }

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

    /** While paused, every layer except the Glyph light is refused. */
    @Volatile var paused = false
        set(v) {
            field = v
            if (v) handler.post { for (l in Layer.entries) if (l != Layer.Torch) sources[l.ordinal] = null; notifyActive(); render() }
        }

    fun set(layer: Layer, source: Source?) = handler.post {
        if (source != null && paused && layer != Layer.Torch) return@post
        sources[layer.ordinal] = source
        notifyActive()
        // Draw the new state now rather than on the next 33 ms tick, so key presses feel instant.
        render()
        if (source != null) start()
    }

    fun clear(layer: Layer) = set(layer, null)

    fun isActive(layer: Layer) = sources[layer.ordinal] != null

    /**
     * Play [pattern] once (or looped until cleared). Each step's vibration fires as its light
     * comes on; [usage] tells Android what kind of vibration it is (touch, notification…), so the
     * user's vibration settings and ring mode apply. Calls never buzz: the phone already vibrates.
     */
    fun play(layer: Layer, pattern: Pattern, loop: Boolean = false, usage: Int = Buzz.usageFor(layer)) {
        if (pattern.steps.isEmpty()) return
        val start = SystemClock.uptimeMillis()
        val total = pattern.duration.coerceAtLeast(1)
        var lastIndex = -1
        var lastCycle = -1L
        set(layer) { now ->
            val t = now - start
            if (!loop && t >= total) null else {
                var rem = (t % total).toInt()
                var index = pattern.steps.lastIndex
                for ((i, s) in pattern.steps.withIndex()) {
                    if (rem < s.ms) { index = i; break }
                    rem -= s.ms
                }
                val cycle = t / total
                if (index != lastIndex || cycle != lastCycle) {
                    lastIndex = index; lastCycle = cycle
                    if (layer != Layer.Call) Buzz.fire(pattern.steps[index].buzz, usage)
                }
                pattern.steps[index].levels
            }
        }
    }

    /**
     * Shows [frame] for [ms] (fading out over the last 300 ms) with a press [effect] layered on top
     * for its first moments, at full LED power so it always reads. [up] sets the direction.
     */
    fun flashWithEffect(layer: Layer, frame: IntArray, ms: Long, effect: PressEffect, up: Boolean) {
        val start = SystemClock.uptimeMillis()
        val end = start + ms
        val pathOrder = Zone.entries.sortedBy { Zone.path[it.ordinal] }.let { if (up) it else it.reversed() }
        val ringOrder = if (up) Zone.ring else Zone.ring.reversed()
        val sparks = Zone.entries.shuffled().take(7).map { it to kotlin.random.Random.nextInt(0, 160).toLong() }
        set(layer) { now ->
            val left = end - now
            if (left <= 0) null else {
                val f = if (left >= 300) frame.copyOf() else IntArray(frame.size) { (frame[it] * left / 300).toInt() }
                val t = now - start
                when (effect) {
                    PressEffect.Off -> Unit
                    PressEffect.Flash -> if (t < 180) f[Zone.B.ordinal] = FULL_POWER
                    // A head with a two-light tail racing the whole path in 320 ms.
                    PressEffect.Chase -> if (t < 400) {
                        val head = (t / 320f * pathOrder.size).toInt()
                        for (k in 0..2) pathOrder.getOrNull(head - k)?.let { if (k == 0) f[it.ordinal] = FULL_POWER else f[it.ordinal] = maxOf(f[it.ordinal], 100 - k * 30) }
                    }
                    // Two fast laps of the ring.
                    PressEffect.Spin -> if (t < 360) {
                        val i = (t / 45).toInt() % ringOrder.size
                        f[ringOrder[i].ordinal] = FULL_POWER
                    }
                    // Seven lights twinkle at staggered moments.
                    PressEffect.Sparkle -> for ((z, at) in sparks) if (t in at..at + 90) f[z.ordinal] = FULL_POWER
                }
                f
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
            // A lone breathing busy-dot only needs 10 fps; everything else gets 30.
            val onlyBusy = sources.withIndex().all { (i, s) -> s == null || i == Layer.Busy.ordinal }
            if (sources.any { it != null }) handler.postDelayed(this, if (onlyBusy) 100 else 33) else running = false
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
        // The busy dot sits on top of other effects instead of being hidden by them.
        val busy = sources[Layer.Busy.ordinal]
        if (busy != null && frame !== null && !torch) {
            val b = runCatching { busy(now) }.getOrNull()
            if (b != null) frame = frame.copyOf().also { it[Zone.E.ordinal] = maxOf(it[Zone.E.ordinal], b[Zone.E.ordinal]) }
        }
        val scale = (if (torch) torchLevel else ceiling) / 100f
        // Perceptual curve: the user's level and each frame value combine, then square, so 5 % is
        // a faint glow and 100 % is the LEDs' full power.
        val out = IntArray(Zone.COUNT) { z ->
            val raw = frame?.getOrNull(z) ?: 0
            if (raw == FULL_POWER) return@IntArray Hw.MAX
            val v = raw / 100f * scale
            if (v <= 0f) 0 else (VizEngine.perceptual(v) * Hw.MAX).toInt().coerceAtLeast(1)
        }
        if (!out.contentEquals(lastFrame)) {
            Hw.write(out)
            lastFrame = out
        }
    }

    private fun notifyActive() {
        onActiveChanged?.invoke(Layer.entries.filter { sources[it.ordinal] != null }.toSet())
    }

    // ---------------------------------------------------------------- frame helpers

    /** Bottom strip filled to [fraction] (0–1); the partial top segment is dimmed proportionally. */
    fun stripLevel(fraction: Float, level: Int = 100): IntArray {
        val f = IntArray(Zone.COUNT)
        val lit = fraction.coerceIn(0f, 1f) * Zone.strip.size
        Zone.strip.forEachIndexed { i, z ->
            f[z.ordinal] = when {
                lit >= i + 1 -> level
                lit > i -> (level * (lit - i)).toInt()
                else -> 0
            }
        }
        return f
    }

    /** A 0–1 level drawn in [style]: segments fill in order, the partial one dimmed proportionally. */
    fun level(fraction: Float, style: LevelStyle): IntArray {
        val x = fraction.coerceIn(0f, 1f)
        fun fill(order: List<Zone>) = IntArray(Zone.COUNT).also { f ->
            val lit = x * order.size
            order.forEachIndexed { i, z -> f[z.ordinal] = when { lit >= i + 1 -> 100; lit > i -> (100 * (lit - i)).toInt(); else -> 0 } }
        }
        return when (style) {
            LevelStyle.Strip -> stripLevel(x)
            LevelStyle.Ring -> fill(Zone.ring)
            LevelStyle.Diagonal -> frame(Zone.B to (x * 100).toInt().coerceAtLeast(4))
            LevelStyle.Camera -> frame(Zone.A to (x * 100).toInt().coerceAtLeast(4))
            LevelStyle.Whole -> fill(Zone.entries.sortedBy { Zone.path[it.ordinal] })
        }
    }

    /** One sweep of light up the back over [ms]: a soft band travelling dot → camera. */
    fun sweep(ms: Long = 700, width: Float = 2.5f): Source {
        val start = SystemClock.uptimeMillis()
        return { now ->
            val t = (now - start).toFloat() / ms
            if (t > 1.15f) null else {
                val front = t * (Zone.PATH_END + width) - width / 2
                IntArray(Zone.COUNT) { z ->
                    val d = kotlin.math.abs(Zone.path[z] - front)
                    if (d >= width) 0 else ((1f - d / width) * 100).toInt()
                }
            }
        }
    }

    /** Frame value meaning "this light at the LEDs' full power, regardless of brightness". */
    const val FULL_POWER = 1000

    fun frame(vararg on: Pair<Zone, Int>) = IntArray(Zone.COUNT).also { a -> on.forEach { (z, v) -> a[z.ordinal] = v } }
}
