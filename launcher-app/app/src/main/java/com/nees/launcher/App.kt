package com.nees.launcher

import android.app.Application
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager

/** Holds the one AppWidgetHost for the process. */
class App : Application() {
    lateinit var widgetHost: AppWidgetHost
        private set
    lateinit var widgetManager: AppWidgetManager
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        widgetManager = AppWidgetManager.getInstance(this)
        widgetHost = AppWidgetHost(this, HOST_ID)
    }

    companion object {
        const val HOST_ID = 0x4C41 // "LA"
        lateinit var instance: App
            private set
    }
}
