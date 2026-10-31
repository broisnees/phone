package com.nees.blackwa;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.View;
import android.view.Window;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * BlackWA — turns WhatsApp's dark-grey surfaces into true black at the Android framework level,
 * so it works regardless of WhatsApp's (obfuscated, fast-changing) internal classes.
 *
 * Only neutral dark greys are recoloured; WhatsApp's greens/reds/whites are left untouched.
 * Darkest greys (backgrounds) become pure #000000; slightly lighter greys (toolbars, incoming
 * bubbles) become #0E0E0E so they stay faintly distinguishable. Our two output colours are
 * treated as already-themed and skipped, so hooks never re-process their own result.
 */
public class Hooks implements de.robv.android.xposed.IXposedHookLoadPackage {

    private static final int BLACK = 0xFF000000;
    private static final int CARD  = 0xFF0E0E0E;

    private static int retints = 0;

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lp) {
        if (!"com.whatsapp".equals(lp.packageName) && !"com.whatsapp.w4b".equals(lp.packageName)) return;
        log("loaded in " + lp.packageName);

        // View backgrounds set as a solid colour.
        try {
            XposedHelpers.findAndHookMethod(View.class, "setBackgroundColor", int.class, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    int c = (int) p.args[0];
                    int t = retarget(c);
                    if (t != c) { p.args[0] = t; count(); }
                }
            });
        } catch (Throwable t) { log("setBackgroundColor hook failed: " + t); }

        // View backgrounds set as a drawable (covers XML-inflated backgrounds too, which route here).
        XC_MethodHook bgDrawable = new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                Drawable d = (Drawable) p.args[0];
                Drawable nd = retargetDrawable(d);
                if (nd != null) { p.args[0] = nd; count(); }
            }
        };
        hook(View.class, "setBackground", Drawable.class, bgDrawable);
        hook(View.class, "setBackgroundDrawable", Drawable.class, bgDrawable);

        // Window background + system bars → pure black for every WhatsApp activity.
        try {
            XposedHelpers.findAndHookMethod(Window.class, "setBackgroundDrawable", Drawable.class, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    Drawable d = (Drawable) p.args[0];
                    if (d instanceof ColorDrawable && isDarkNeutral(((ColorDrawable) d).getColor())) {
                        p.args[0] = new ColorDrawable(BLACK);
                    }
                }
            });
        } catch (Throwable t) { log("window bg hook failed: " + t); }
        barHook("setStatusBarColor");
        barHook("setNavigationBarColor");

        // Belt-and-braces: paint the window black (and bars) as each activity is created.
        try {
            XposedHelpers.findAndHookMethod(Activity.class, "onCreate", Bundle.class, new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    try {
                        Window w = ((Activity) p.thisObject).getWindow();
                        if (w == null) return;
                        w.setBackgroundDrawable(new ColorDrawable(BLACK));
                        w.setStatusBarColor(BLACK);
                        w.setNavigationBarColor(BLACK);
                    } catch (Throwable ignored) { }
                }
            });
        } catch (Throwable t) { log("activity hook failed: " + t); }
    }

    private void barHook(String method) {
        try {
            XposedHelpers.findAndHookMethod(Window.class, method, int.class, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    int c = (int) p.args[0];
                    if (c != BLACK && isDarkNeutral(c)) p.args[0] = BLACK;
                }
            });
        } catch (Throwable t) { log(method + " hook failed: " + t); }
    }

    private void hook(Class<?> cls, String method, Class<?> arg, XC_MethodHook cb) {
        try { XposedHelpers.findAndHookMethod(cls, method, arg, cb); }
        catch (Throwable t) { log(method + " hook failed: " + t); }
    }

    /** Returns the colour to use instead of [c], or [c] itself when nothing should change. */
    private static int retarget(int c) {
        if (c == BLACK || c == CARD) return c;       // already themed — fixed point, no re-processing
        if (!isDarkNeutral(c)) return c;             // coloured or too light — leave alone
        return maxChannel(c) <= 0x1C ? BLACK : CARD; // darkest → black, dark grey → near-black
    }

    /** Returns a replacement drawable, or null to keep the original. */
    private static Drawable retargetDrawable(Drawable d) {
        if (d instanceof ColorDrawable) {
            int c = ((ColorDrawable) d).getColor();
            int t = retarget(c);
            return t != c ? new ColorDrawable(t) : null;
        }
        if (d instanceof GradientDrawable) {
            // Single solid-colour gradient drawables (rounded bubble/toolbar backgrounds).
            try {
                int[] colors = (int[]) XposedHelpers.callMethod(d, "getColors");
                if (colors != null && colors.length > 0) {
                    int c = colors[0];
                    boolean uniform = true;
                    for (int x : colors) if (x != c) { uniform = false; break; }
                    int t = retarget(c);
                    if (uniform && t != c) {
                        Drawable nd = d.getConstantState() != null ? d.getConstantState().newDrawable().mutate() : d;
                        ((GradientDrawable) nd).setColor(t);
                        return nd;
                    }
                }
            } catch (Throwable ignored) { }
        }
        return null;
    }

    /** True for opaque, low-saturation, dark colours (WhatsApp's grey surfaces), not its greens/reds. */
    private static boolean isDarkNeutral(int c) {
        if (Color.alpha(c) < 0xF0) return false;
        int r = Color.red(c), g = Color.green(c), b = Color.blue(c);
        int max = Math.max(r, Math.max(g, b));
        int min = Math.min(r, Math.min(g, b));
        if (max > 0x3C) return false;       // too light to be a dark surface
        if (max - min > 28) return false;   // too colourful — likely a branded colour
        return true;
    }

    private static int maxChannel(int c) {
        return Math.max(Color.red(c), Math.max(Color.green(c), Color.blue(c)));
    }

    private static void count() {
        retints++;
        if (retints <= 5 || retints % 100 == 0) log("retinted " + retints + " surfaces");
    }

    private static void log(String m) { XposedBridge.log("BlackWA: " + m); }
}
