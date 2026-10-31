package com.nees.glyph

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * One frame of a pattern: per-zone brightness in percent (0–100), held for [ms], with an optional
 * vibration ([buzz], see [Buzz]) fired the moment the step's light comes on.
 */
data class Step(val levels: IntArray, val ms: Int, val buzz: Int = Buzz.NONE) {
    fun toJson() = JSONObject().put("l", JSONArray(levels.toList())).put("ms", ms).put("b", buzz)
    fun vibe(b: Int) = copy(buzz = b)

    companion object {
        fun fromJson(o: JSONObject) = Step(IntArray(Zone.COUNT) { o.getJSONArray("l").optInt(it) }, o.getInt("ms"), o.optInt("b", Buzz.NONE))
        fun of(ms: Int, vararg on: Pair<Zone, Int>) = Step(IntArray(Zone.COUNT).also { a -> on.forEach { (z, v) -> a[z.ordinal] = v } }, ms)
        fun all(ms: Int, level: Int, zones: Collection<Zone> = Zone.entries) = of(ms, *zones.map { it to level }.toTypedArray())
    }

    override fun equals(other: Any?) = other is Step && other.ms == ms && other.buzz == buzz && other.levels.contentEquals(levels)
    override fun hashCode() = (31 * levels.contentHashCode() + ms) * 31 + buzz
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
        Pattern("pulse", "Pulse", listOf(Step.all(180, 100).vibe(Buzz.CLICK), Step.all(220, 0))),
        Pattern("double", "Double tap", listOf(Step.all(110, 100).vibe(Buzz.CLICK), Step.all(110, 0), Step.all(110, 100).vibe(Buzz.CLICK), Step.all(250, 0))),
        Pattern("halo", "Halo", listOf(Step.all(220, 100, ring).vibe(Buzz.TICK), Step.all(180, 40, ring), Step.all(220, 100, ring).vibe(Buzz.TICK), Step.all(250, 0))),
        Pattern("sweep", "Ring sweep", ring.mapIndexed { i, z -> Step.of(90, z to 100).vibe(if (i == 0) Buzz.TICK else Buzz.NONE) } + Step.all(120, 0)),
        Pattern("rise", "Strip rise", strip.indices.map { i -> Step.all(45, 100, strip.take(i + 1)).vibe(if (i == strip.lastIndex) Buzz.CLICK else Buzz.NONE) } + Step.all(160, 0)),
        Pattern("comet", "Comet", listOf(Step.of(90, Zone.A to 100).vibe(Buzz.TICK), Step.of(90, Zone.B to 100)) + ring.map { Step.of(70, it to 100) } +
            strip.reversed().map { Step.of(35, it to 100) } + Step.of(120, Zone.E to 100).vibe(Buzz.CLICK) + Step.all(100, 0)),
        Pattern("heart", "Heartbeat", listOf(Step.all(120, 100, ring + Zone.E).vibe(Buzz.HEAVY), Step.all(90, 15, ring), Step.all(140, 100, ring + Zone.E).vibe(Buzz.CLICK), Step.all(450, 0))),
        Pattern("camera", "Camera blink", listOf(Step.of(150, Zone.A to 100).vibe(Buzz.TICK), Step.all(150, 0), Step.of(150, Zone.A to 100).vibe(Buzz.TICK), Step.all(250, 0))),
    )
}

/** Short animation played on each volume/brightness step, before the level shows. */
enum class PressEffect(val label: String) { Off("Off"), Flash("Flash"), Chase("Chase"), Spin("Spin"), Sparkle("Sparkle") }

/** Where a volume or brightness level is drawn. */
enum class LevelStyle(val label: String) { Strip("Strip"), Ring("Ring"), Diagonal("Diagonal"), Camera("Camera"), Whole("Whole phone") }

/** All settings, persisted in one SharedPreferences file. */
/**
 * Bumped on every preference change. Every getter below reads it, so any composable that shows a
 * setting redraws the moment that setting changes — from a switch, a tile, or the service.
 */
object SettingsBus {
    val version = androidx.compose.runtime.mutableIntStateOf(0)
    // Held strongly here: SharedPreferences only keeps weak references to listeners.
    val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> version.intValue++ }
}

class Settings(ctx: Context) {
    private val prefs: SharedPreferences = ctx.getSharedPreferences("glyph", Context.MODE_PRIVATE).also {
        it.registerOnSharedPreferenceChangeListener(SettingsBus.listener)
    }

    /** Reads go through here so they subscribe to [SettingsBus]. */
    private val p: SharedPreferences get() { SettingsBus.version.intValue; return prefs }

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

    var vizStyle: VizStyle
        get() = runCatching { VizStyle.valueOf(p.getString("vizStyle", "Spectrum")!!) }.getOrDefault(VizStyle.Spectrum)
        set(v) = p.edit().putString("vizStyle", v.name).apply()
    /** Light sweeps up the back when the phone is unlocked. */
    var unlockAnim get() = bool("unlock"); set(v) = setBool("unlock", v)
    /** The bottom dot breathes while anything with a progress bar is running. */
    var busyDot get() = bool("busy"); set(v) = setBool("busy", v)
    // What lights the busy dot.
    var busyDownloads get() = bool("busyDl", true); set(v) = setBool("busyDl", v)
    var busyOngoing get() = bool("busyOngoing", true); set(v) = setBool("busyOngoing", v)
    var busyMic get() = bool("busyMic", true); set(v) = setBool("busyMic", v)
    var busyCamera get() = bool("busyCam", true); set(v) = setBool("busyCam", v)

    /** Progress bars from any app show on the strip as they update. */
    var volumeStyle: LevelStyle
        get() = runCatching { LevelStyle.valueOf(p.getString("volStyle", "Strip")!!) }.getOrDefault(LevelStyle.Strip)
        set(v) = p.edit().putString("volStyle", v.name).apply()
    var brightnessStyle: LevelStyle
        get() = runCatching { LevelStyle.valueOf(p.getString("brightStyle", "Strip")!!) }.getOrDefault(LevelStyle.Strip)
        set(v) = p.edit().putString("brightStyle", v.name).apply()

    /** The diagonal blinks once on every volume step / brightness change (separate switches). */
    var volumeEffect: PressEffect
        get() = runCatching { PressEffect.valueOf(p.getString("volFx", "Chase")!!) }.getOrDefault(PressEffect.Chase)
        set(v) = p.edit().putString("volFx", v.name).apply()
    var brightnessEffect: PressEffect
        get() = runCatching { PressEffect.valueOf(p.getString("brightFx", "Off")!!) }.getOrDefault(PressEffect.Off)
        set(v) = p.edit().putString("brightFx", v.name).apply()

    /** Soft tick on each kick while the visualizer runs. */
    var vizHaptics get() = bool("vizHaptics"); set(v) = setBool("vizHaptics", v)
    var liveProgress get() = bool("progress"); set(v) = setBool("progress", v)

    /** Notification, call and ambient glyphs stay dark while Do Not Disturb is on. */
    var respectDnd get() = bool("dnd", true); set(v) = setBool("dnd", v)
    /** Master pause: nothing but the Glyph light runs. */
    var paused get() = bool("paused"); set(v) = setBool("paused", v)

    fun anyFeature() = unlockAnim || busyDot || liveProgress || notifications || calls || visualizer || chargingMeter || volumeIndicator || brightnessIndicator || ambient || faceDown

    // ---- per-app and per-contact patterns: key → pattern id ("" = use default, "off" = none)
    fun appPattern(pkg: String): String? = p.getString("app:$pkg", null)
    fun setAppPattern(pkg: String, id: String?) = p.edit().apply { if (id == null) remove("app:$pkg") else putString("app:$pkg", id) }.apply()
    fun appsWithPatterns(): Map<String, String> =
        p.all.filterKeys { it.startsWith("app:") }.mapKeys { it.key.removePrefix("app:") }.mapValues { it.value as String }

    fun contactPattern(key: String): String? = p.getString("contact:$key", null)
    fun setContactPattern(key: String, id: String?) = p.edit().apply { if (id == null) remove("contact:$key") else putString("contact:$key", id) }.apply()

    // ---- custom patterns
    fun customPatterns(): List<Pattern> = runCatching {
        val a = JSONArray(p.getString("patterns", "[]"))
        List(a.length()) { Pattern.fromJson(a.getJSONObject(it)) }
    }.getOrDefault(emptyList())

    fun savePattern(pattern: Pattern) {
        val list = customPatterns().filterNot { it.id == pattern.id } + pattern
        p.edit().putString("patterns", JSONArray(list.map { it.toJson() }).toString()).apply()
    }

    fun deletePattern(id: String) {
        p.edit().putString("patterns", JSONArray(customPatterns().filterNot { it.id == id }.map { it.toJson() }).toString()).apply()
    }

    fun allPatterns() = Presets.all + customPatterns()
    fun pattern(id: String?): Pattern? = allPatterns().firstOrNull { it.id == id }

    fun listen(l: SharedPreferences.OnSharedPreferenceChangeListener) = prefs.registerOnSharedPreferenceChangeListener(l)
    fun unlisten(l: SharedPreferences.OnSharedPreferenceChangeListener) = prefs.unregisterOnSharedPreferenceChangeListener(l)
}
