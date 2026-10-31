package com.nees.launcher

import android.app.WallpaperManager
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.nees.launcher.ui.Drawer
import com.nees.launcher.ui.FolderPopup
import com.nees.launcher.ui.HomeMenu
import com.nees.launcher.ui.HomeScreen
import com.nees.launcher.ui.ItemSheet
import com.nees.launcher.ui.LauncherTheme
import com.nees.launcher.ui.SettingsSheet
import com.nees.launcher.ui.WidgetPicker
import com.nees.launcher.ui.WidgetResize
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var state: LauncherState
    private var pendingProvider: AppWidgetProviderInfo? = null
    private var pendingId = -1
    private var drawerOpen by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        state = LauncherState(this)
        state.load()

        val density = resources.displayMetrics.density
        val iconPx = (56 * density).toInt()
        AppRepo.init(this, lifecycleScope, iconPx, state.store.iconPack)
        state.columns = state.store.columns
        state.rows = state.store.rows

        // Seed the dock once apps have loaded.
        lifecycleScope.launch {
            snapshotFlow { AppRepo.apps.value }.collect { list ->
                if (list.isNotEmpty() && state.items.isEmpty()) state.seedIfEmpty()
            }
        }

        setContent {
            LauncherTheme {
                var sheetItem by remember { mutableStateOf<Item?>(null) }
                var homeMenu by remember { mutableStateOf(false) }
                var folder by remember { mutableStateOf<Item?>(null) }
                var widgetPicker by remember { mutableStateOf(false) }
                var settings by remember { mutableStateOf(false) }
                var resize by remember { mutableStateOf<Item?>(null) }

                Box(Modifier.fillMaxSize()) {
                    HomeScreen(
                        state = state,
                        onOpenDrawer = { drawerOpen = true },
                        onLongItem = { if (it.type == ItemType.FOLDER) folder = it else sheetItem = it },
                        onEmptyLongPress = { homeMenu = true },
                        onDoubleTap = { if (state.store.doubleTapLock) Sys.screenOff() },
                        onSwipeDown = { if (state.store.swipeDownNotifications) Sys.expandNotifications(this@MainActivity) },
                    )

                    if (drawerOpen) Drawer(state, onClose = { drawerOpen = false }, onLongApp = { state.addApp(it) })
                    sheetItem?.let { ItemSheet(state, it, onDismiss = { sheetItem = null }, onOpenFolder = { folder = it }, onResizeWidget = { resize = it }) }
                    if (homeMenu) HomeMenu(onWidgets = { widgetPicker = true }, onWallpaper = { pickWallpaper() }, onSettings = { settings = true }, onDismiss = { homeMenu = false })
                    folder?.let { FolderPopup(state, it, onDismiss = { folder = null }) }
                    if (widgetPicker) WidgetPicker(state, onPick = { widgetPicker = false; requestWidget(it) }, onDismiss = { widgetPicker = false })
                    if (settings) SettingsSheet(state, onDismiss = { settings = false })
                    resize?.let { WidgetResize(state, it, onDismiss = { resize = null }) }
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

    /** HOME pressed while already here: close overlays, go back to first page. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        drawerOpen = false
        if (state.store.glyphOnOpen) Sys.glyphPulse(this)
    }

    override fun onBackPressed() {
        if (drawerOpen) { drawerOpen = false; return }
        // Home: stay put (don't leave the launcher).
    }

    // ---- wallpaper
    private fun pickWallpaper() {
        runCatching { startActivity(Intent(WallpaperManager.getInstance(this).getCropAndSetWallpaperIntent(android.net.Uri.EMPTY))) }
            .onFailure { runCatching { startActivity(Intent(Intent.ACTION_SET_WALLPAPER)) } }
    }

    // ---- widgets
    private fun requestWidget(provider: AppWidgetProviderInfo) {
        val host = App.instance.widgetHost
        val id = host.allocateAppWidgetId()
        pendingProvider = provider
        pendingId = id
        val allowed = App.instance.widgetManager.bindAppWidgetIdIfAllowed(id, provider.provider)
        if (allowed) {
            configureOrAdd()
        } else {
            val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, provider.provider)
            }
            @Suppress("DEPRECATION")
            startActivityForResult(intent, REQ_BIND)
        }
    }

    private fun configureOrAdd() {
        val provider = pendingProvider ?: return
        if (provider.configure != null) {
            runCatching {
                @Suppress("DEPRECATION")
                App.instance.widgetHost.startAppWidgetConfigureActivityForResult(this, pendingId, 0, REQ_CONFIG, null)
            }.onFailure { addPending() }
        } else addPending()
    }

    private fun addPending() {
        val provider = pendingProvider ?: return
        val cols = (provider.minWidth / cellUnitPx() + 1).coerceIn(1, state.columns)
        val rowsSpan = (provider.minHeight / cellUnitPx() + 1).coerceIn(1, state.rows)
        state.addWidget(pendingId, cols, rowsSpan)
        pendingProvider = null; pendingId = -1
    }

    private fun cellUnitPx(): Int {
        val w = resources.displayMetrics.widthPixels
        return (w / state.columns).coerceAtLeast(1)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQ_BIND -> if (resultCode == RESULT_OK) configureOrAdd() else cancelPending()
            REQ_CONFIG -> if (resultCode == RESULT_OK) addPending() else cancelPending()
        }
    }

    private fun cancelPending() {
        if (pendingId >= 0) runCatching { App.instance.widgetHost.deleteAppWidgetId(pendingId) }
        pendingProvider = null; pendingId = -1
    }

    companion object {
        private const val REQ_BIND = 1001
        private const val REQ_CONFIG = 1002
    }
}
