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
public class Hooks implements IXposedHookLoadPackage {
    private static final String TAG = "Haptix: ";

    private static final int SELECTION = HapticFeedbackConstants.CLOCK_TICK;
    private static final int IMPACT = HapticFeedbackConstants.VIRTUAL_KEY;
    private static final int SUCCESS = HapticFeedbackConstants.CONFIRM;

    private static final long GAP = 120;        // between unrelated haptics
    private static final long KEY_REPEAT_GAP = 60; // volume key auto-repeat
    private static final long SLIDER_GAP = 80;  // volume panel drag detents

    // AOSP ShadeExpansionStateManager panel states.
    private static final int STATE_CLOSED = 0;
    private static final int STATE_OPEN = 2;

    private static long lastFire;
    private static boolean hooked;
    private static Vibrator vibrator;
    private static Method vibratorFeedback; // hidden Vibrator#performHapticFeedback, used when no View is at hand
    private static final SparseIntArray lastLevel = new SparseIntArray();

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lp) {
        // SystemUI reports its package again for a secondary classloader; hook only the first one.
        if (!lp.isFirstApplication || hooked) return;
        if ("com.android.systemui".equals(lp.packageName)) { hooked = true; hookSystemUi(lp.classLoader); }
        else if ("com.nothing.launcher".equals(lp.packageName)) { hooked = true; hookLauncher(lp.classLoader); }
    }

    // ---------------------------------------------------------------- SystemUI

    private void hookSystemUi(ClassLoader cl) {
        // Panel state transitions only (closed / opening / open).
        hook(cl, "com.android.systemui.shade.ShadeExpansionStateManager", "updateStateInternal",
                new Object[]{int.class}, before(p -> {
                    int next = (int) p.args[0];
                    int prev = XposedHelpers.getIntField(p.thisObject, "state");
                    if (next == prev) return;
                    if (next == STATE_OPEN) fire(SELECTION, null, GAP, "shade open");
                    else if (next == STATE_CLOSED && prev == STATE_OPEN) fire(SELECTION, null, GAP, "shade closed");
                }));

        Class<?> expandable = XposedHelpers.findClassIfExists("com.android.systemui.animation.Expandable", cl);
        if (expandable != null) {
            hook(cl, "com.android.systemui.qs.tileimpl.QSTileImpl", "click", new Object[]{expandable},
                    before(p -> fire(SELECTION, null, GAP, "qs tile")));
        }

        // Notification swiped away: fires once when the swipe commits to dismiss.
        hook(cl, "com.android.systemui.SwipeHelper", "dismissChild", new Object[]{View.class, float.class, boolean.class},
                before(p -> {
                    if (!p.thisObject.getClass().getName().contains("Notification")) return;
                    fire(SUCCESS, (View) p.args[0], GAP, "notification dismissed");
                }));

        // Volume: hardware key steps arrive here once per step (with FLAG_SHOW_UI).
        String vc = "com.android.systemui.volume.VolumeDialogControllerImpl";
        hook(cl, vc, "onVolumeChangedW", new Object[]{int.class, int.class}, after(p -> {
            int flags = (int) p.args[1];
            if ((flags & AudioManager.FLAG_SHOW_UI) == 0) return; // app-driven changes stay silent
            volumeStep((int) p.args[0], KEY_REPEAT_GAP, "volume key");
        }));
        // Volume panel slider: called per detent the user drags across.
        hook(cl, vc, "onSetStreamVolumeW", new Object[]{int.class, int.class}, after(p ->
                volumeStep((int) p.args[0], SLIDER_GAP, "volume slider")));
        hook(cl, vc, "onSetRingerModeW", new Object[]{int.class, boolean.class}, before(p -> {
            if (!(boolean) p.args[1]) fire(IMPACT, null, GAP, "ringer mode");
        }));
    }

    /**
     * Hands the new level straight to Glyph Tweaks. The public VOLUME_CHANGED_ACTION goes through
     * the batched broadcast queue and arrives late on fast presses; this foreground broadcast,
     * sent from the volume controller itself, lands in a few ms.
     */
