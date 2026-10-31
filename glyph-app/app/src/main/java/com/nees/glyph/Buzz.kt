package com.nees.glyph

import android.content.Context
import android.media.AudioManager
import android.os.SystemClock
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator

/**
 * Glyph-synced vibration using the motor's clear predefined effects only (no buzzy one-shots).
 * Notification and media vibrations stay silent when the phone is on silent.
 */
object Buzz {
    const val NONE = 0
    const val TICK = 1
    const val CLICK = 2
    const val HEAVY = 3
    val labels = listOf("None", "Tick", "Click", "Heavy")

    private var vibrator: Vibrator? = null
    private var audio: AudioManager? = null
    private var lastAt = 0L

    fun init(ctx: Context) {
        if (vibrator != null) return
        vibrator = ctx.applicationContext.getSystemService(Vibrator::class.java)
        audio = ctx.applicationContext.getSystemService(AudioManager::class.java)
    }

    fun usageFor(layer: Layer): Int = when (layer) {
        Layer.Notification, Layer.Meter, Layer.Timer -> VibrationAttributes.USAGE_NOTIFICATION
        Layer.Call -> VibrationAttributes.USAGE_RINGTONE
        else -> VibrationAttributes.USAGE_TOUCH
    }

    fun fire(kind: Int, usage: Int, minGapMs: Long = 40) {
        if (kind == NONE) return
        val v = vibrator ?: return
        if (usage != VibrationAttributes.USAGE_TOUCH && audio?.ringerMode == AudioManager.RINGER_MODE_SILENT) return
        val now = SystemClock.uptimeMillis()
        if (now - lastAt < minGapMs) return
        lastAt = now
        val effect = when (kind) {
            TICK -> VibrationEffect.EFFECT_TICK
            HEAVY -> VibrationEffect.EFFECT_HEAVY_CLICK
            else -> VibrationEffect.EFFECT_CLICK
        }
        runCatching { v.vibrate(VibrationEffect.createPredefined(effect), VibrationAttributes.createForUsage(usage)) }
    }
}
