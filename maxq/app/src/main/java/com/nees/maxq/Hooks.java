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

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lp) {
        if (!YT.equals(lp.packageName) || !YT.equals(lp.processName)) return;
        // DexKit scans can take a second on YouTube's dex; never block app start.
        new Thread(() -> {
            try { setUp(lp); } catch (Throwable t) { log("setup failed, doing nothing: " + t); }
        }, "MaxQ-setup").start();
    }

    // ------------------------------------------------------------ discovery

    private void setUp(XC_LoadPackage.LoadPackageParam lp) throws Throwable {
        long start = SystemClock.uptimeMillis();
        ClassLoader cl = lp.classLoader;
        // Changes on every YouTube update (new APK path / size), which is all the cache needs.
        File apk = new File(lp.appInfo.sourceDir);
        String version = apk.getParent() + "|" + apk.length();
        File cacheFile = new File(lp.appInfo.dataDir, "maxq_cache.properties");
        Properties cache = load(cacheFile);

        String setterCls, setterName, fCls, fName, gCls, gName, yCls, yName;
        boolean fromCache = String.valueOf(version).equals(cache.getProperty("version"));
        if (fromCache) {
            setterCls = cache.getProperty("setterCls"); setterName = cache.getProperty("setterName");
            fCls = cache.getProperty("fCls"); fName = cache.getProperty("fName");
            gCls = cache.getProperty("gCls"); gName = cache.getProperty("gName");
            yCls = cache.getProperty("yCls"); yName = cache.getProperty("yName");
        } else {
            loadDexKit();
            try (DexKitBridge bridge = DexKitBridge.create(lp.appInfo.sourceDir)) {
                List<MethodData> setters = bridge.findMethod(FindMethod.create().matcher(
                    MethodMatcher.create()
                        .modifiers(Modifier.PUBLIC | Modifier.FINAL)
                        .returnType("void")
                        .paramCount(3)
                        .paramTypes(null, "int", "boolean")
                        .usingStrings(QUALITY_STRING)));
                MethodData setter = null;
                for (MethodData m : setters) if (m.getParamTypeNames().get(0).endsWith("[]")) { setter = m; break; }
                if (setter == null) { log("quality setter not found (" + setters.size() + " candidates)"); return; }
                setterCls = setter.getClassName();
                setterName = setter.getMethodName();

                // Method on the setter class that does: iget F (this) -> iput G (on F's object).
                List<MethodData> candidates = bridge.findMethod(FindMethod.create().matcher(
                    MethodMatcher.create().declaredClass(setterCls).returnType("void").paramCount(1)));
                String[] fg = null;
                for (MethodData m : candidates) {
                    fg = listenerAndMenuFields(m, setterCls);
                    if (fg != null) break;
                }
                if (fg == null) { log("listener/menu fields not found in " + setterCls); return; }
                fCls = fg[0]; fName = fg[1]; gCls = fg[2]; gName = fg[3];
                yCls = fg[4];
                yName = null; // resolved by reflection below (method taking VideoQuality)
            }
        }

        Class<?> setterClass = Class.forName(setterCls, false, cl);
        Method setter = null;
        for (Method m : setterClass.getDeclaredMethods()) {
            Class<?>[] p = m.getParameterTypes();
            if (m.getName().equals(setterName) && p.length == 3 && p[0].isArray() && p[1] == int.class && p[2] == boolean.class) { setter = m; break; }
        }
        if (setter == null) { log("setter method vanished: " + setterCls + "." + setterName); return; }
        Class<?> qualityClass = setter.getParameterTypes()[0].getComponentType();

        listenerField = Class.forName(fCls, false, cl).getDeclaredField(fName);
        listenerField.setAccessible(true);
        menuField = Class.forName(gCls, false, cl).getDeclaredField(gName);
        menuField.setAccessible(true);

        Class<?> menuClass = Class.forName(yCls, false, cl);
        for (Class<?> c = menuClass; c != null && setQuality == null; c = c.getSuperclass()) {
            for (Method m : c.getDeclaredMethods()) {
                Class<?>[] p = m.getParameterTypes();
                if (m.getReturnType() == void.class && p.length == 1 && p[0] == qualityClass
                        && (yName == null || m.getName().equals(yName))) { setQuality = m; break; }
            }
        }
        if (setQuality == null) { log("setQuality(" + qualityClass.getName() + ") not found on " + yCls); return; }
        setQuality.setAccessible(true);

        for (Field f : qualityClass.getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers())) continue;
            if (f.getType() == int.class && resolutionField == null) resolutionField = f;
            if (f.getType() == String.class && nameField == null) nameField = f;
        }
        if (resolutionField == null) { log("no resolution field on " + qualityClass.getName()); return; }
        resolutionField.setAccessible(true);
        if (nameField != null) nameField.setAccessible(true);

        if (!fromCache) {
            cache.clear();
            cache.setProperty("version", String.valueOf(version));
            cache.setProperty("setterCls", setterCls); cache.setProperty("setterName", setterName);
            cache.setProperty("fCls", fCls); cache.setProperty("fName", fName);
            cache.setProperty("gCls", gCls); cache.setProperty("gName", gName);
            cache.setProperty("yCls", yCls); cache.setProperty("yName", setQuality.getName());
            save(cacheFile, cache);
        }

        XposedBridge.hookMethod(setter, new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam param) {
                try { onQualities(param.thisObject, (Object[]) param.args[0], (int) param.args[1]); }
                catch (Throwable t) { log("onQualities: " + t); }
            }
        });
        log("ready on YouTube " + lp.appInfo.packageName + " in "
            + (SystemClock.uptimeMillis() - start) + " ms" + (fromCache ? " (cached)" : " (scanned)")
            + " — setter " + setterCls + "." + setterName + ", setQuality " + menuClass.getName() + "." + setQuality.getName());
    }

    /** From DexKit field usage: [F class, F name, G class, G name, G type] or null. */
    private static String[] listenerAndMenuFields(MethodData m, String setterCls) {
        List<UsingFieldData> uses = m.getUsingFields();
        if (uses == null || uses.size() < 2) return null;
        UsingFieldData read = uses.get(0), write = uses.get(1);
        if (!String.valueOf(read.getUsingType()).toUpperCase().contains("READ")) return null;
        if (!String.valueOf(write.getUsingType()).toUpperCase().contains("WRITE")) return null;
        if (!read.getField().getClassName().equals(setterCls)) return null;
        // G is a field declared on F's type.
        if (!write.getField().getClassName().equals(read.getField().getTypeName())) return null;
        return new String[] {
            read.getField().getClassName(), read.getField().getFieldName(),
            write.getField().getClassName(), write.getField().getFieldName(),
            write.getField().getTypeName(),
        };
    }

    // ------------------------------------------------------------ behaviour

    /**
     * Called each time YouTube (re)sets a video's quality list. Switches to the highest quality
     * when the player is on Auto, or once for each new list (new video). A quality the user picks
     * by hand on the same video is left alone.
     */
