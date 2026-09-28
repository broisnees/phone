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

