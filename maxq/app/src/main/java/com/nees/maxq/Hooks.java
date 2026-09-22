package com.nees.maxq;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Properties;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.IXposedHookZygoteInit;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import org.luckypray.dexkit.DexKitBridge;
import org.luckypray.dexkit.query.FindMethod;
import org.luckypray.dexkit.query.matchers.MethodMatcher;
import org.luckypray.dexkit.result.MethodData;
import org.luckypray.dexkit.result.UsingFieldData;

/**
 * Makes official YouTube play every video at its highest available quality.
 *
 * Mechanism (ported from ReVanced/Morphe "VideoInformation" + "RememberVideoQuality"):
 *  - The quality setter {@code public final void X(VideoQuality[] qualities, int index, boolean)}
 *    references "menu_item_video_quality" and runs whenever a video's quality list is (re)set.
 *  - Its class has a single-object-param method that reads field F (the quality item click
 *    listener) and writes field G on it; G's type has {@code void Y(VideoQuality)}, which applies
 *    a quality exactly as tapping it in the quality menu does.
 * Everything is located by fingerprint with DexKit and cached until YouTube's APK changes.
 */
public class Hooks implements IXposedHookLoadPackage, IXposedHookZygoteInit {
    private static String modulePath;
    private static final String YT = "com.google.android.youtube";
    private static final String QUALITY_STRING = "menu_item_video_quality";
    private static final int AUTO = -2;

    /** Created lazily: the module is constructed before the app's main looper exists. */
    private Handler main;

    private Field listenerField;      // F, on the setter class
    private Field menuField;          // G, on the listener's class
    private Method setQuality;        // Y(VideoQuality), on G's type
    private Field resolutionField;    // VideoQuality int
    private Field nameField;          // VideoQuality String

    private WeakReference<Object[]> lastApplied = new WeakReference<>(null);
    private long lastApplyAt;

    private static void log(String msg) { XposedBridge.log("MaxQ: " + msg); }

    @Override
    public void initZygote(StartupParam startupParam) { modulePath = startupParam.modulePath; }

    /** Vector's in-memory module class loader doesn't search the module's lib dir; load by path. */
    private static void loadDexKit() {
        try { System.loadLibrary("dexkit"); return; } catch (Throwable ignored) { }
        String dir = new File(modulePath).getParent();
        for (String abi : new String[] {"arm64", "arm64-v8a"}) {
            File so = new File(dir, "lib/" + abi + "/libdexkit.so");
            if (so.exists()) { System.load(so.getAbsolutePath()); return; }
        }
        throw new UnsatisfiedLinkError("libdexkit.so not found next to " + modulePath);
    }

