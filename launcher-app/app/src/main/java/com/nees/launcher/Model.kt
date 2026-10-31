package com.nees.launcher

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** An installed, launchable app. */
data class AppInfo(
    val pkg: String,
    val cls: String,
    val label: String,
) {
    val key get() = "$pkg/$cls"
}

/** A favourite on the home screen: an app, with an optional display alias. */
data class Fav(val key: String, val alias: String = "") {
    fun toJson() = JSONObject().put("key", key).put("alias", alias)
    companion object {
        fun fromJson(o: JSONObject) = Fav(o.getString("key"), o.optString("alias"))
    }
}

/** Clock widget layout. */
enum class ClockStyle { Stacked, Inline }

/** All persisted state: favourites, hidden apps, native-widget toggles, preferences. */
class Store(ctx: Context) {
    private val p = ctx.getSharedPreferences("launcher", Context.MODE_PRIVATE)

    fun favourites(): List<Fav> = runCatching {
        val a = JSONArray(p.getString("favs", "[]"))
        List(a.length()) { Fav.fromJson(a.getJSONObject(it)) }
    }.getOrDefault(emptyList())

    fun saveFavourites(list: List<Fav>) = p.edit().putString("favs", JSONArray(list.map { it.toJson() }).toString()).apply()

    fun hidden(): Set<String> = p.getStringSet("hidden", emptySet())!!.toSet()
    fun saveHidden(set: Set<String>) = p.edit().putStringSet("hidden", set).apply()

    fun thirdPartyWidgets(): List<Int> = runCatching {
        val a = JSONArray(p.getString("tpw", "[]")); List(a.length()) { a.getInt(it) }
    }.getOrDefault(emptyList())
    fun saveThirdPartyWidgets(ids: List<Int>) = p.edit().putString("tpw", JSONArray(ids).toString()).apply()

    var showClock get() = p.getBoolean("clock", true); set(v) = p.edit().putBoolean("clock", v).apply()
    var clockStyle: ClockStyle
        get() = runCatching { ClockStyle.valueOf(p.getString("clockStyle", "Stacked")!!) }.getOrDefault(ClockStyle.Stacked)
        set(v) = p.edit().putString("clockStyle", v.name).apply()
    var showClaude get() = p.getBoolean("claude", true); set(v) = p.edit().putBoolean("claude", v).apply()
    var showDate get() = p.getBoolean("date", true); set(v) = p.edit().putBoolean("date", v).apply()
    var showBattery get() = p.getBoolean("battery", true); set(v) = p.edit().putBoolean("battery", v).apply()
    var showAlarm get() = p.getBoolean("alarm", true); set(v) = p.edit().putBoolean("alarm", v).apply()
    var showWallpaper get() = p.getBoolean("wallpaper", false); set(v) = p.edit().putBoolean("wallpaper", v).apply()

    var doubleTapLock get() = p.getBoolean("dtLock", true); set(v) = p.edit().putBoolean("dtLock", v).apply()
    var swipeDownNotifications get() = p.getBoolean("swDown", true); set(v) = p.edit().putBoolean("swDown", v).apply()
    var glyphOnOpen get() = p.getBoolean("glyph", true); set(v) = p.edit().putBoolean("glyph", v).apply()
    /** Tracks that we've already applied the solid-black system wallpaper (so we don't re-set each resume). */
    var blackWallpaperSet get() = p.getBoolean("blackWp", false); set(v) = p.edit().putBoolean("blackWp", v).apply()
}
