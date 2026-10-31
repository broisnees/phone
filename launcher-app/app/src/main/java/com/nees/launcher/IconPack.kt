package com.nees.launcher

import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.drawable.Drawable
import org.xmlpull.v1.XmlPullParser

/**
 * A third-party icon pack. Reads appfilter.xml to map components to drawable names, plus the pack's
 * icon-mask / iconback / scale for apps the pack doesn't theme explicitly.
 */
class IconPack(private val ctx: Context, val pkg: String) {
    private val res: Resources = ctx.packageManager.getResourcesForApplication(pkg)
    private val map = HashMap<String, String>()
    private var maskName: String? = null
    private var backNames = mutableListOf<String>()
    private var scale = 1f
    private var loaded = false

    private fun ensure() {
        if (loaded) return
        loaded = true
        runCatching { parseAppfilter() }
    }

    private fun parseAppfilter() {
        val parser = openAppfilter() ?: return
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                when (parser.name) {
                    "item" -> {
                        val comp = parser.getAttributeValue(null, "component")
                        val drawable = parser.getAttributeValue(null, "drawable")
                        if (comp != null && drawable != null) {
                            val cn = Regex("ComponentInfo\\{(.+)/(.+)\\}").find(comp)
                            if (cn != null) map["${cn.groupValues[1]}/${cn.groupValues[2]}"] = drawable
                        }
                    }
                    "iconmask" -> parser.getAttributeValue(null, "img1")?.let { maskName = it }
                    "iconback" -> {
                        for (i in 0 until parser.attributeCount) if (parser.getAttributeName(i).startsWith("img")) backNames.add(parser.getAttributeValue(i))
                    }
                    "scale" -> parser.getAttributeValue(null, "factor")?.toFloatOrNull()?.let { scale = it }
                }
            }
            event = parser.next()
        }
    }

    private fun openAppfilter(): XmlPullParser? {
        // Prefer a compiled appfilter.xml resource, else the asset.
        val id = res.getIdentifier("appfilter", "xml", pkg)
        if (id != 0) return res.getXml(id)
        return runCatching {
            val p = android.util.Xml.newPullParser()
            p.setInput(res.assets.open("appfilter.xml"), "UTF-8")
            p
        }.getOrNull()
    }

    private fun drawableByName(name: String): Drawable? {
        val id = res.getIdentifier(name, "drawable", pkg)
        if (id == 0) return null
        return runCatching { res.getDrawable(id, null) }.getOrNull()
    }

    /** Themed icon for the component, or null if the pack doesn't provide one. */
    fun icon(appPkg: String, cls: String, size: Int): Bitmap? {
        ensure()
        val name = map["$appPkg/$cls"] ?: return null
        return drawableByName(name)?.toBitmap(size)
    }

    /** Applies the pack's mask/back/scale to an unthemed app icon so it blends in. */
    fun masked(base: Drawable, size: Int): Bitmap? {
        ensure()
        if (maskName == null && backNames.isEmpty()) return null
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        backNames.firstOrNull()?.let { drawableByName(it)?.apply { setBounds(0, 0, size, size); draw(canvas) } }
        val inner = (size * scale).toInt().coerceIn(1, size)
        val off = (size - inner) / 2
        val iconBmp = base.toBitmap(inner)
        canvas.drawBitmap(iconBmp, off.toFloat(), off.toFloat(), null)
        maskName?.let { mn ->
            drawableByName(mn)?.let { mask ->
                val mb = mask.toBitmap(size)
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT) }
                canvas.drawBitmap(mb, 0f, 0f, paint)
            }
        }
        return out
    }

    companion object {
        /** Installed packages that declare themselves as icon packs. */
        fun installed(ctx: Context): List<Pair<String, String>> {
            val pm = ctx.packageManager
            val seen = LinkedHashMap<String, String>()
            for (action in listOf("com.anddoes.launcher.THEME", "org.adw.launcher.THEMES", "com.novalauncher.THEME")) {
                val intent = android.content.Intent(action)
                runCatching {
                    pm.queryIntentActivities(intent, 0).forEach {
                        val p = it.activityInfo.packageName
                        seen.putIfAbsent(p, runCatching { pm.getApplicationLabel(pm.getApplicationInfo(p, 0)).toString() }.getOrDefault(p))
                    }
                }
            }
            return seen.map { it.key to it.value }
        }
    }
}
