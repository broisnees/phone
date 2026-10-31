package com.nees.launcher

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** An installed, launchable app. */
data class AppInfo(
    val pkg: String,
    val cls: String,
    val label: String,
) {
    val key get() = "$pkg/$cls"
}

enum class ItemType { APP, FOLDER, WIDGET }

/**
 * One placeable thing on the home grid or dock.
 * - APP: [pkg]/[cls]
 * - FOLDER: [label] + [children] (app keys)
 * - WIDGET: [widgetId] spanning [spanX]×[spanY] cells
 */
data class Item(
    val id: Long,
    val type: ItemType,
    val pkg: String = "",
    val cls: String = "",
    val label: String = "",
    val children: MutableList<String> = mutableListOf(),
    val widgetId: Int = -1,
    var page: Int = 0,
    var x: Int = 0,
    var y: Int = 0,
    var spanX: Int = 1,
    var spanY: Int = 1,
    var inDock: Boolean = false,
) {
    val appKey get() = "$pkg/$cls"

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id); put("type", type.name)
        put("pkg", pkg); put("cls", cls); put("label", label)
        put("children", JSONArray(children))
        put("widgetId", widgetId)
        put("page", page); put("x", x); put("y", y); put("spanX", spanX); put("spanY", spanY)
        put("dock", inDock)
    }

    companion object {
        fun fromJson(o: JSONObject) = Item(
            id = o.getLong("id"),
            type = ItemType.valueOf(o.getString("type")),
            pkg = o.optString("pkg"), cls = o.optString("cls"), label = o.optString("label"),
            children = o.optJSONArray("children")?.let { a -> MutableList(a.length()) { a.getString(it) } } ?: mutableListOf(),
            widgetId = o.optInt("widgetId", -1),
            page = o.optInt("page"), x = o.optInt("x"), y = o.optInt("y"),
            spanX = o.optInt("spanX", 1), spanY = o.optInt("spanY", 1),
            inDock = o.optBoolean("dock"),
        )
    }
}

/** The whole home layout, persisted as one JSON file. */
class Layout {
    val items = mutableListOf<Item>()
    var pageCount = 1
    var columns = 5
    var rows = 5

    fun page(p: Int) = items.filter { !it.inDock && it.page == p }
    fun dock() = items.filter { it.inDock }.sortedBy { it.x }

    fun occupied(page: Int, x: Int, y: Int, ignore: Item? = null): Item? = items.firstOrNull {
        !it.inDock && it.page == page && it !== ignore &&
            x in it.x until (it.x + it.spanX) && y in it.y until (it.y + it.spanY)
    }

    fun freeCell(page: Int): Pair<Int, Int>? {
        for (y in 0 until rows) for (x in 0 until columns) if (occupied(page, x, y) == null) return x to y
        return null
    }

    fun toJson(): String = JSONObject().apply {
        put("pageCount", pageCount); put("columns", columns); put("rows", rows)
        put("items", JSONArray(items.map { it.toJson() }))
    }.toString()

    companion object {
        fun fromJson(s: String): Layout = Layout().apply {
            val o = JSONObject(s)
            pageCount = o.optInt("pageCount", 1).coerceAtLeast(1)
            columns = o.optInt("columns", 5); rows = o.optInt("rows", 5)
            val a = o.getJSONArray("items")
            for (i in 0 until a.length()) items.add(Item.fromJson(a.getJSONObject(i)))
        }
    }
}

/** Layout file + preferences. */
class Store(ctx: Context) {
    private val file = File(ctx.filesDir, "layout.json")
    private val p = ctx.getSharedPreferences("launcher", Context.MODE_PRIVATE)

    fun load(): Layout? = runCatching { if (file.exists()) Layout.fromJson(file.readText()) else null }.getOrNull()
    fun save(layout: Layout) = runCatching { file.writeText(layout.toJson()) }

    var iconPack: String get() = p.getString("iconPack", "") ?: ""; set(v) = p.edit().putString("iconPack", v).apply()
    var doubleTapLock: Boolean get() = p.getBoolean("dtLock", true); set(v) = p.edit().putBoolean("dtLock", v).apply()
    var swipeDownNotifications: Boolean get() = p.getBoolean("swDown", true); set(v) = p.edit().putBoolean("swDown", v).apply()
    var glyphOnOpen: Boolean get() = p.getBoolean("glyph", false); set(v) = p.edit().putBoolean("glyph", v).apply()
    var columns: Int get() = p.getInt("cols", 5); set(v) = p.edit().putInt("cols", v).apply()
    var rows: Int get() = p.getInt("rows", 5); set(v) = p.edit().putInt("rows", v).apply()

    fun nextId(): Long = (p.getLong("seq", 1) + 1).also { p.edit().putLong("seq", it).apply() }
}
