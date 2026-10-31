package com.nees.launcher

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.SnapshotStateList

/** Live launcher state: favourites, hidden apps, third-party widgets. Persists on every change. */
class LauncherState(val ctx: Context) {
    val store = Store(ctx)
    val favourites: SnapshotStateList<Fav> = mutableStateListOf()
    val hidden = mutableStateOf<Set<String>>(emptySet())
    val widgetIds: SnapshotStateList<Int> = mutableStateListOf()

    fun load() {
        favourites.clear(); favourites.addAll(store.favourites())
        hidden.value = store.hidden()
        widgetIds.clear(); widgetIds.addAll(store.thirdPartyWidgets())
    }

    private fun persistFavs() = store.saveFavourites(favourites.toList())

    fun isFav(key: String) = favourites.any { it.key == key }

    fun addFav(key: String) {
        if (isFav(key)) return
        favourites.add(Fav(key)); persistFavs()
    }

    fun removeFav(key: String) {
        favourites.removeAll { it.key == key }; persistFavs()
    }

    fun rename(key: String, alias: String) {
        val i = favourites.indexOfFirst { it.key == key }
        if (i >= 0) { favourites[i] = favourites[i].copy(alias = alias.trim()); persistFavs() }
    }

    fun moveFav(from: Int, to: Int) {
        if (from !in favourites.indices || to !in favourites.indices) return
        favourites.add(to, favourites.removeAt(from)); persistFavs()
    }

    fun label(fav: Fav): String = fav.alias.ifBlank { AppRepo.find(fav.key)?.label ?: fav.key.substringBefore('/') }

    fun hide(key: String) {
        hidden.value = hidden.value + key
        removeFav(key)
        store.saveHidden(hidden.value)
    }

    fun unhide(key: String) {
        hidden.value = hidden.value - key
        store.saveHidden(hidden.value)
    }

    /** Apps for the drawer: everything launchable, minus hidden, minus this launcher. */
    fun drawerApps(): List<AppInfo> =
        AppRepo.apps.value.filter { it.key !in hidden.value && it.pkg != ctx.packageName }

    fun addWidget(id: Int) { if (id !in widgetIds) { widgetIds.add(id); store.saveThirdPartyWidgets(widgetIds.toList()) } }
    fun removeWidget(id: Int) {
        widgetIds.remove(id); store.saveThirdPartyWidgets(widgetIds.toList())
        runCatching { App.instance.widgetHost.deleteAppWidgetId(id) }
    }

    /** First run: seed a few sensible favourites. */
    fun seedIfEmpty() {
        if (favourites.isNotEmpty() || store.favourites().isNotEmpty()) return
        val apps = AppRepo.apps.value
        val wanted = listOf("phone", "dialer", "messag", "chrome", "camera", "settings")
        for (w in wanted) {
            val a = apps.firstOrNull { it.pkg.contains(w, true) || it.label.contains(w, true) } ?: continue
            if (!isFav(a.key)) favourites.add(Fav(a.key))
            if (favourites.size >= 5) break
        }
        persistFavs()
    }
}
