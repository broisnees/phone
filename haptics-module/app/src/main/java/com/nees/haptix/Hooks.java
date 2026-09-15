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
    private static void notifyGlyph(Context ctx, int level, int max) {
        try {
            ctx.sendBroadcast(new android.content.Intent("com.nees.glyph.LEVEL")
                    .setPackage("com.nees.glyph")
                    .addFlags(android.content.Intent.FLAG_RECEIVER_FOREGROUND)
                    .putExtra("level", level)
                    .putExtra("max", Math.max(max, 1)));
        } catch (Throwable ignored) { }
    }

    /** Selection tick per step; a single success tap on reaching max or min, not repeated while held there. */
    private static void volumeStep(int stream, long gap, String why) {
        Context ctx = AndroidAppHelper.currentApplication();
        if (ctx == null) return;
        AudioManager am = ctx.getSystemService(AudioManager.class);
        int level = am.getStreamVolume(stream);
        notifyGlyph(ctx, level, am.getStreamMaxVolume(stream));
        int prev = lastLevel.get(stream, Integer.MIN_VALUE);
        lastLevel.put(stream, level);
        if (level == prev) return; // pressing against a limit
        boolean boundary = level >= am.getStreamMaxVolume(stream) || level <= am.getStreamMinVolume(stream);
        fire(boundary ? SUCCESS : SELECTION, null, gap, why + (boundary ? " limit" : ""));
    }

    // ---------------------------------------------------------------- Launcher

    private void hookLauncher(ClassLoader cl) {
        Class<?> itemInfo = XposedHelpers.findClassIfExists("com.android.launcher3.model.data.ItemInfo", cl);
        if (itemInfo != null) {
            // Every icon tap (workspace, dock, app drawer, search) funnels through here.
            hook(cl, "com.android.launcher3.Launcher", "startActivitySafely",
                    new Object[]{View.class, android.content.Intent.class, itemInfo},
                    before(p -> fire(IMPACT, (View) p.args[0], GAP, "app launch")));
        }

        XC_MethodHook folder = before(p -> fire(IMPACT, (View) p.thisObject, GAP, "folder open"));
        hook(cl, "com.android.launcher3.folder.Folder", "animateOpen", new Object[]{}, folder);
        hook(cl, "com.android.launcher3.folder.Folder", "animateOpen", new Object[]{java.util.List.class, int.class}, folder);

        // Swipe-up destination decided once on release.
        Class<?> endTarget = XposedHelpers.findClassIfExists("com.android.quickstep.GestureState$GestureEndTarget", cl);
        if (endTarget != null) {
            hook(cl, "com.android.quickstep.AbsSwipeUpHandler", "animateToProgress",
                    new Object[]{float.class, float.class, long.class, android.view.animation.Interpolator.class, endTarget, android.graphics.PointF.class},
                    before(p -> {
                        Object target = p.args[4];
                        if (target == null) return;
                        String name = ((Enum<?>) target).name();
                        View v = viewField(p.thisObject, "mRecentsView");
                        if ("HOME".equals(name)) fire(SUCCESS, v, GAP, "gesture home");
                        else if ("RECENTS".equals(name)) fire(SELECTION, v, GAP, "gesture recents");
                    }));
        }

        // Recents carousel settling on a card (not workspace page swipes).
        Class<?> recents = XposedHelpers.findClassIfExists("com.android.quickstep.views.RecentsView", cl);
        if (recents != null) {
            hook(cl, "com.android.launcher3.PagedView", "onPageEndTransition", new Object[]{}, after(p -> {
                if (recents.isInstance(p.thisObject)) fire(SELECTION, (View) p.thisObject, GAP, "recents snap");
            }));
        }
    }

    // ---------------------------------------------------------------- helpers

