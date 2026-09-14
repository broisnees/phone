package com.nees.haptix;

import android.app.AndroidAppHelper;
import android.content.Context;
import android.media.AudioManager;
import android.os.SystemClock;
import android.os.VibrationAttributes;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.Settings;
import android.util.SparseIntArray;
import android.view.HapticFeedbackConstants;
import android.view.View;

import java.lang.reflect.Method;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Apple/Pixel-style haptics for system events Nothing OS leaves silent.
 *
 * Vocabulary (one meaning → one effect):
 *   selection  = CLOCK_TICK   shade open/close, QS tile, volume step, slider detent, recents snap
 *   impact     = VIRTUAL_KEY  app launch, folder open, ringer mode change
 *   success    = CONFIRM      home commit, notification dismissed, volume hits max/min
 *
 * Only state-change hooks are used — never per-frame expansion, scroll or draw callbacks.
 * Back gesture is left alone: Nothing's BackPanelController already vibrates on arm/commit.
 */
