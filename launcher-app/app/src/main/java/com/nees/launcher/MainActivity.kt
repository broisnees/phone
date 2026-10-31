package com.nees.launcher

import android.app.WallpaperManager
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.lifecycleScope
import com.nees.launcher.ui.C
import com.nees.launcher.ui.Drawer
import com.nees.launcher.ui.DrawerAppMenu
import com.nees.launcher.ui.FavMenu
import com.nees.launcher.ui.HomeMenu
import com.nees.launcher.ui.HomeScreen
import com.nees.launcher.ui.LauncherTheme
import com.nees.launcher.ui.RenameDialog
import com.nees.launcher.ui.SettingsScreen
import com.nees.launcher.ui.WidgetPicker
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var state: LauncherState
    private var pendingId = -1
    private var pendingProvider: AppWidgetProviderInfo? = null
    private var drawerOpen by mutableStateOf(false)
    private var settingsOpen by mutableStateOf(false)
    private var wallpaper by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        state = LauncherState(this)
        state.load()
        wallpaper = state.store.showWallpaper
        AppRepo.init(this, lifecycleScope)

        lifecycleScope.launch {
            snapshotFlow { AppRepo.apps.value }.collect { list ->
                if (list.isNotEmpty() && state.favourites.isEmpty()) state.seedIfEmpty()
            }
        }

        setContent {
            LauncherTheme {
                var favMenu by remember { mutableStateOf<Fav?>(null) }
                var drawerMenu by remember { mutableStateOf<AppInfo?>(null) }
                var rename by remember { mutableStateOf<Fav?>(null) }
                var homeMenu by remember { mutableStateOf(false) }
                var widgetPicker by remember { mutableStateOf(false) }

                Box(Modifier.fillMaxSize().background(if (wallpaper) Color.Transparent else C.bg)) {
                    HomeScreen(
                        state = state,
                        onOpenDrawer = { drawerOpen = true },
                        onLongFav = { favMenu = it },
                        onEmptyLong = { homeMenu = true },
                        onDoubleTap = { if (state.store.doubleTapLock) Sys.screenOff() },
                        onSwipeDown = { if (state.store.swipeDownNotifications) Sys.expandNotifications(this@MainActivity) },
                        onClockTap = { openClock() },
                        onAlarmTap = { openAlarm() },
                    )

                    if (drawerOpen) Drawer(state, onClose = { drawerOpen = false }, onLongApp = { drawerMenu = it })
                    if (settingsOpen) SettingsScreen(state, onAddWidget = { widgetPicker = true }, onClose = { settingsOpen = false })

                    favMenu?.let { FavMenu(state, it, onDismiss = { favMenu = null }, onRename = { rename = it }) }
                    drawerMenu?.let { DrawerAppMenu(state, it, onDismiss = { drawerMenu = null }) }
                    rename?.let { RenameDialog(state, it, onDismiss = { rename = null }) }
                    if (homeMenu) HomeMenu(onAddWidget = { widgetPicker = true }, onWallpaper = { pickWallpaper() }, onSettings = { settingsOpen = true }, onDismiss = { homeMenu = false })
                    if (widgetPicker) WidgetPicker(state, onPick = { widgetPicker = false; requestWidget(it) }, onDismiss = { widgetPicker = false })
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        runCatching { App.instance.widgetHost.startListening() }
    }

    override fun onStop() {
        super.onStop()
        runCatching { App.instance.widgetHost.stopListening() }
    }

    override fun onResume() {
        super.onResume()
        wallpaper = state.store.showWallpaper
        if (wallpaper) window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (settingsOpen) settingsOpen = false
        else if (drawerOpen) drawerOpen = false
        else if (state.store.glyphOnOpen) Sys.glyphPulse(this)
    }

    override fun onBackPressed() {
        when {
            settingsOpen -> settingsOpen = false
            drawerOpen -> drawerOpen = false
            // else: stay on home
        }
    }

    private fun openClock() = runCatching {
        startActivity(Intent(android.provider.AlarmClock.ACTION_SHOW_ALARMS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
    private fun openAlarm() = openClock()

    private fun pickWallpaper() {
        runCatching { startActivity(Intent(Intent.ACTION_SET_WALLPAPER)) }
    }

    // ---- third-party widgets (secondary)
    private fun requestWidget(provider: AppWidgetProviderInfo) {
        val id = App.instance.widgetHost.allocateAppWidgetId()
        pendingId = id; pendingProvider = provider
        if (App.instance.widgetManager.bindAppWidgetIdIfAllowed(id, provider.provider)) configureOrAdd()
        else {
            @Suppress("DEPRECATION")
            startActivityForResult(
                Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, provider.provider),
                REQ_BIND,
            )
        }
    }

    private fun configureOrAdd() {
        val provider = pendingProvider ?: return
        if (provider.configure != null) runCatching {
            @Suppress("DEPRECATION")
            App.instance.widgetHost.startAppWidgetConfigureActivityForResult(this, pendingId, 0, REQ_CONFIG, null)
        }.onFailure { add() } else add()
    }

    private fun add() { if (pendingId >= 0) state.addWidget(pendingId); pendingId = -1; pendingProvider = null }

    private fun cancel() { if (pendingId >= 0) runCatching { App.instance.widgetHost.deleteAppWidgetId(pendingId) }; pendingId = -1; pendingProvider = null }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQ_BIND -> if (resultCode == RESULT_OK) configureOrAdd() else cancel()
            REQ_CONFIG -> if (resultCode == RESULT_OK) add() else cancel()
        }
    }

    companion object {
        private const val REQ_BIND = 1001
        private const val REQ_CONFIG = 1002
    }
}
