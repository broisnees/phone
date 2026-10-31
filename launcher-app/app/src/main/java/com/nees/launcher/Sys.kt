package com.nees.launcher

import android.content.Context
import java.io.DataOutputStream

/** Root actions through one long-lived su shell (kept open; commands written to stdin). */
object Root {
    private var proc: Process? = null
    private var out: DataOutputStream? = null

    @Synchronized
    private fun shell(): DataOutputStream? {
        out?.let { return it }
        return runCatching {
            val p = ProcessBuilder("su").redirectErrorStream(true).start()
            Thread { runCatching { p.inputStream.copyTo(java.io.OutputStream.nullOutputStream()) } }.apply { isDaemon = true }.start()
            proc = p
            DataOutputStream(p.outputStream).also { out = it }
        }.getOrNull()
    }

    @Synchronized
    fun run(cmd: String): Boolean {
        val w = shell() ?: return false
        return runCatching { w.writeBytes(cmd + "\n"); w.flush(); true }.getOrElse {
            runCatching { proc?.destroy() }; proc = null; out = null
            val w2 = shell() ?: return false
            runCatching { w2.writeBytes(cmd + "\n"); w2.flush(); true }.getOrDefault(false)
        }
    }
}

object Sys {
    /** Turn the screen off (double-tap-to-sleep). Uses root key event. */
    fun screenOff() = Root.run("input keyevent 26")

    /** Pull down the notification shade. Tries the hidden API first, then root. */
    fun expandNotifications(ctx: Context) {
        val ok = runCatching {
            @Suppress("WrongConstant")
            val sb = ctx.getSystemService("statusbar")
            Class.forName("android.app.StatusBarManager").getMethod("expandNotificationsPanel").invoke(sb)
            true
        }.getOrDefault(false)
        if (!ok) Root.run("cmd statusbar expand-notifications")
    }

    /** Tell Glyph Tweaks to run its unlock sweep, if the user enabled glyph-on-open. */
    fun glyphPulse(ctx: Context) {
        runCatching {
            ctx.sendBroadcast(
                android.content.Intent("com.nees.glyph.SWEEP").setPackage("com.nees.glyph")
                    .addFlags(android.content.Intent.FLAG_RECEIVER_FOREGROUND),
            )
        }
    }
}
