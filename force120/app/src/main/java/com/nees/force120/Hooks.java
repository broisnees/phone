package com.nees.force120;

import android.content.Context;
import android.os.Handler;
import android.provider.Settings;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Force120 — runs in system_server and neutralises every per-window refresh-rate request
 * (WindowManager.LayoutParams preferredDisplayModeId / preferredMin/MaxDisplayRefreshRate,
 * the high-refresh denylist and per-package game/OEM ranges). With those gone, DisplayModeDirector
 * is left with only the user votes (min = peak = 120), so apps like Snapchat can no longer pin 60 Hz.
 */
public class Hooks implements IXposedHookLoadPackage {

    private static final String POLICY = "com.android.server.wm.RefreshRatePolicy";
    private static final String WS = "com.android.server.wm.WindowState";

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lp) {
        if (!"android".equals(lp.packageName)) return;
        ClassLoader cl = lp.classLoader;
        hook(cl, "getPreferredMaxRefreshRate", 0f);  // 0 = no cap
        hook(cl, "getPreferredMinRefreshRate", 0f);  // 0 = no floor request (user min still applies)
        hook(cl, "getPreferredModeId", 0);           // 0 = no forced base mode

        // The getters above are tiny and get inlined into DisplayContent's AOT code, so the hooks can be
        // bypassed. Sink: every app request lands in AppRequestObserver.setAppRequest(display, modeId,
        // requestedRefreshRate, minRange, maxRange) — zero them all here so nothing app-side can cap.
        try {
            XposedHelpers.findAndHookMethod(
                    "com.android.server.display.mode.DisplayModeDirector$AppRequestObserver", cl,
                    "setAppRequest", int.class, int.class, float.class, float.class, float.class,
                    new XC_MethodHook() {
                        @Override protected void beforeHookedMethod(MethodHookParam p) {
                            p.args[1] = 0;
                            p.args[2] = 0f;
                            p.args[3] = 0f;
                            p.args[4] = 0f;
                        }
                    });
            XposedBridge.log("Force120: hooked setAppRequest");
        } catch (Throwable t) {
            XposedBridge.log("Force120: failed setAppRequest: " + t);
        }

        // At boot the USER_SETTING_MIN_RENDER_FRAME_RATE vote is missing until min_refresh_rate changes,
        // so nudge the setting (bump then restore) a few seconds after boot to (re)register the floor.
        try {
            XposedHelpers.findAndHookMethod("com.android.server.display.mode.DisplayModeDirector", cl,
                    "onBootCompleted", new XC_MethodHook() {
                        @Override protected void afterHookedMethod(MethodHookParam p) {
                            Context ctx = (Context) XposedHelpers.getObjectField(p.thisObject, "mContext");
                            Handler h = (Handler) XposedHelpers.getObjectField(p.thisObject, "mHandler");
                            h.postDelayed(() -> reassertMin(ctx), 5000);
                        }
                    });
        } catch (Throwable t) {
            XposedBridge.log("Force120: failed onBootCompleted: " + t);
        }
    }

    private static void reassertMin(Context ctx) {
        try {
            float min = Settings.System.getFloat(ctx.getContentResolver(), "min_refresh_rate", 120f);
            if (min <= 0f) min = 120f;
            Settings.System.putFloat(ctx.getContentResolver(), "min_refresh_rate", 0f);
            Settings.System.putFloat(ctx.getContentResolver(), "min_refresh_rate", min);
            XposedBridge.log("Force120: reasserted min_refresh_rate=" + min);
        } catch (Throwable t) {
            XposedBridge.log("Force120: reassertMin failed: " + t);
        }
    }

    private static void hook(ClassLoader cl, String method, Object value) {
        try {
            XposedHelpers.findAndHookMethod(POLICY, cl, method, WS, XC_MethodReplacement.returnConstant(value));
            XposedBridge.log("Force120: hooked " + method);
        } catch (Throwable t) {
            XposedBridge.log("Force120: failed " + method + ": " + t);
        }
    }
}
