package com.nees.glyph

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/** One frame of a pattern: per-zone brightness in percent (0–100), held for [ms]. */
data class Step(val levels: IntArray, val ms: Int) {
    fun toJson() = JSONObject().put("l", JSONArray(levels.toList())).put("ms", ms)

    companion object {
        fun fromJson(o: JSONObject) = Step(IntArray(Zone.COUNT) { o.getJSONArray("l").optInt(it) }, o.getInt("ms"))
        fun of(ms: Int, vararg on: Pair<Zone, Int>) = Step(IntArray(Zone.COUNT).also { a -> on.forEach { (z, v) -> a[z.ordinal] = v } }, ms)
        fun all(ms: Int, level: Int, zones: Collection<Zone> = Zone.entries) = of(ms, *zones.map { it to level }.toTypedArray())
    }

    override fun equals(other: Any?) = other is Step && other.ms == ms && other.levels.contentEquals(levels)
    override fun hashCode() = 31 * levels.contentHashCode() + ms
}

data class Pattern(val id: String, val name: String, val steps: List<Step>) {
    val duration get() = steps.sumOf { it.ms }

    fun toJson() = JSONObject().put("id", id).put("name", name).put("steps", JSONArray(steps.map { it.toJson() }))

    companion object {
        fun fromJson(o: JSONObject) = Pattern(
            o.getString("id"), o.getString("name"),
            o.getJSONArray("steps").let { a -> List(a.length()) { Step.fromJson(a.getJSONObject(it)) } },
        )
    }
}

object Presets {
    private val ring = Zone.ring
    private val strip = Zone.strip

    val all: List<Pattern> = listOf(
        Pattern("pulse", "Pulse", listOf(Step.all(180, 100), Step.all(220, 0))),
        Pattern("double", "Double tap", listOf(Step.all(110, 100), Step.all(110, 0), Step.all(110, 100), Step.all(250, 0))),
        Pattern("halo", "Halo", listOf(Step.all(220, 100, ring), Step.all(180, 40, ring), Step.all(220, 100, ring), Step.all(250, 0))),
        Pattern("sweep", "Ring sweep", ring.map { Step.of(90, it to 100) } + Step.all(120, 0)),
        Pattern("rise", "Strip rise", strip.indices.map { i -> Step.all(45, 100, strip.take(i + 1)) } + Step.all(160, 0)),
        Pattern("comet", "Comet", listOf(Step.of(90, Zone.A to 100), Step.of(90, Zone.B to 100)) + ring.map { Step.of(70, it to 100) } +
            strip.reversed().map { Step.of(35, it to 100) } + Step.of(120, Zone.E to 100) + Step.all(100, 0)),
        Pattern("heart", "Heartbeat", listOf(Step.all(120, 100, ring + Zone.E), Step.all(90, 15, ring), Step.all(140, 100, ring + Zone.E), Step.all(450, 0))),
        Pattern("camera", "Camera blink", listOf(Step.of(150, Zone.A to 100), Step.all(150, 0), Step.of(150, Zone.A to 100), Step.all(250, 0))),
    )
}

/** All settings, persisted in one SharedPreferences file. */
class Settings(ctx: Context) {
    private val p: SharedPreferences = ctx.getSharedPreferences("glyph", Context.MODE_PRIVATE)

    private fun bool(k: String, d: Boolean = false) = p.getBoolean(k, d)
    private fun setBool(k: String, v: Boolean) = p.edit().putBoolean(k, v).apply()

    var notifications get() = bool("notif"); set(v) = setBool("notif", v)
    var calls get() = bool("calls"); set(v) = setBool("calls", v)
    var visualizer get() = bool("viz"); set(v) = setBool("viz", v)
    var chargingMeter get() = bool("charge"); set(v) = setBool("charge", v)
    var volumeIndicator get() = bool("vol"); set(v) = setBool("vol", v)
    var brightnessIndicator get() = bool("bright"); set(v) = setBool("bright", v)
    var ambient get() = bool("ambient"); set(v) = setBool("ambient", v)
    var faceDown get() = bool("flip"); set(v) = setBool("flip", v)

    /** Global ceiling for every effect, 0–100. */
    var brightness get() = p.getInt("level", 70); set(v) = p.edit().putInt("level", v).apply()
    var torchLevel get() = p.getInt("torch", 100); set(v) = p.edit().putInt("torch", v).apply()
    var vizGain get() = p.getInt("vizGain", 60); set(v) = p.edit().putInt("vizGain", v).apply()
    var timerMinutes get() = p.getInt("timer", 5); set(v) = p.edit().putInt("timer", v).apply()
    var defaultNotifPattern get() = p.getString("defNotif", "pulse")!!; set(v) = p.edit().putString("defNotif", v).apply()
    var defaultCallPattern get() = p.getString("defCall", "comet")!!; set(v) = p.edit().putString("defCall", v).apply()

