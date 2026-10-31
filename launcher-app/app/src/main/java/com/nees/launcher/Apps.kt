package com.nees.launcher

import android.content.ComponentName
import android.content.Context
import android.content.pm.LauncherApps
import android.os.Process
import android.os.UserHandle
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Loads launchable apps off the main thread and keeps the list fresh. No icons (text launcher). */
object AppRepo {
    val apps = mutableStateOf<List<AppInfo>>(emptyList())
    private var byKey = mapOf<String, AppInfo>()
    private lateinit var launcherApps: LauncherApps
    private lateinit var scope: CoroutineScope
    private lateinit var appContext: Context

    private val callback = object : LauncherApps.Callback() {
        override fun onPackageRemoved(p: String?, u: UserHandle?) = reload()
        override fun onPackageAdded(p: String?, u: UserHandle?) = reload()
        override fun onPackageChanged(p: String?, u: UserHandle?) = reload()
        override fun onPackagesAvailable(p: Array<out String>?, u: UserHandle?, r: Boolean) = reload()
        override fun onPackagesUnavailable(p: Array<out String>?, u: UserHandle?, r: Boolean) = reload()
    }

    fun init(ctx: Context, scope: CoroutineScope) {
        appContext = ctx.applicationContext
        this.scope = scope
        launcherApps = appContext.getSystemService(LauncherApps::class.java)
        launcherApps.registerCallback(callback)
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

    fun launch(key: String) {
        val info = byKey[key] ?: return
        // Center scale+fade open over the black launcher/wallpaper — the app expands rather than
        // sliding in from the edge, so no grey app-edge reveal (old-Nothing feel).
        val opts = android.app.ActivityOptions.makeCustomAnimation(appContext, R.anim.app_open_enter, R.anim.app_open_exit).toBundle()
        runCatching { launcherApps.startMainActivity(ComponentName(info.pkg, info.cls), Process.myUserHandle(), null, opts) }
            .onFailure { runCatching { launcherApps.startMainActivity(ComponentName(info.pkg, info.cls), Process.myUserHandle(), null, null) } }
    }

    fun appInfo(key: String) {
        val info = byKey[key] ?: return
        runCatching { launcherApps.startAppDetailsActivity(ComponentName(info.pkg, info.cls), Process.myUserHandle(), null, null) }
    }
}
