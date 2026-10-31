package com.nees.launcher

import android.content.ComponentName
import android.content.Context
import android.content.pm.LauncherApps
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Process
import android.os.UserHandle
import android.util.LruCache
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Loads launchable apps off the main thread, caches their icons, and keeps the list fresh as
 * apps are installed, removed or changed.
 */
object AppRepo {
    val apps = mutableStateOf<List<AppInfo>>(emptyList())
    private var byKey = mapOf<String, AppInfo>()
    private lateinit var launcherApps: LauncherApps
    private var pack: IconPack? = null
    private val iconPx get() = iconSizePx

    private var iconSizePx = 144
    private val cache = object : LruCache<String, Bitmap>(8 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    private val callback = object : LauncherApps.Callback() {
        override fun onPackageRemoved(p: String?, u: UserHandle?) = reload()
        override fun onPackageAdded(p: String?, u: UserHandle?) = reload()
        override fun onPackageChanged(p: String?, u: UserHandle?) = reload()
        override fun onPackagesAvailable(p: Array<out String>?, u: UserHandle?, r: Boolean) = reload()
        override fun onPackagesUnavailable(p: Array<out String>?, u: UserHandle?, r: Boolean) = reload()
    }

    private lateinit var scope: CoroutineScope
    private lateinit var appContext: Context

    fun init(ctx: Context, scope: CoroutineScope, iconSize: Int, iconPackPkg: String) {
        appContext = ctx.applicationContext
        this.scope = scope
        iconSizePx = iconSize
        launcherApps = appContext.getSystemService(LauncherApps::class.java)
        launcherApps.registerCallback(callback)
        setIconPack(iconPackPkg)
    }

    fun setIconPack(pkg: String) {
        pack = if (pkg.isBlank()) null else runCatching { IconPack(appContext, pkg) }.getOrNull()
        cache.evictAll()
        reload()
    }

    fun find(key: String): AppInfo? = byKey[key]

    private fun reload() {
        scope.launch {
            val list = withContext(Dispatchers.IO) {
                runCatching {
                    launcherApps.getActivityList(null, Process.myUserHandle())
                        .map { AppInfo(it.componentName.packageName, it.componentName.className, it.label.toString()) }
                        .sortedBy { it.label.lowercase() }
                }.getOrDefault(emptyList())
            }
            byKey = list.associateBy { it.key }
            apps.value = list
        }
    }

    /** Cached square icon for [key] (pkg/cls). Loads synchronously off the cache; call from IO. */
    fun icon(key: String): Bitmap? {
        cache.get(key)?.let { return it }
        val info = byKey[key] ?: return null
        val bmp = loadIcon(info) ?: return null
        cache.put(key, bmp)
        return bmp
    }

    private fun loadIcon(info: AppInfo): Bitmap? {
        pack?.icon(info.pkg, info.cls, iconPx)?.let { return it }
        val d: Drawable = runCatching {
            launcherApps.getActivityList(info.pkg, Process.myUserHandle())
                .firstOrNull { it.componentName.className == info.cls }
                ?.getIcon(0)
        }.getOrNull() ?: runCatching { appContext.packageManager.getApplicationIcon(info.pkg) }.getOrNull() ?: return null
        return pack?.masked(d, iconPx) ?: d.toBitmap(iconPx)
    }

    fun launch(ctx: Context, key: String) {
        val info = byKey[key] ?: return
        runCatching {
            launcherApps.startMainActivity(ComponentName(info.pkg, info.cls), Process.myUserHandle(), null, null)
        }
    }

    fun appInfo(ctx: Context, pkg: String) {
        runCatching { launcherApps.startAppDetailsActivity(ComponentName(pkg, byKey.values.firstOrNull { it.pkg == pkg }?.cls ?: ""), Process.myUserHandle(), null, null) }
    }
}

fun Drawable.toBitmap(size: Int): Bitmap {
    if (this is BitmapDrawable && bitmap != null && !(this is AdaptiveIconDrawable)) {
        if (bitmap.width == size && bitmap.height == size) return bitmap
    }
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    setBounds(0, 0, size, size)
    draw(canvas)
    return bmp
}
