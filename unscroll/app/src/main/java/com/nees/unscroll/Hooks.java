package com.nees.unscroll;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.LinearLayout;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.regex.Pattern;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Hides Shorts / Reels in the official apps using only framework hooks (View attach,
 * content descriptions, Activity intents), so it survives app updates that reshuffle
 * obfuscated classes. Signals are resource entry names and accessibility labels.
 */
public class Hooks implements IXposedHookLoadPackage {
    private static final String YT = "com.google.android.youtube";
    private static final String IG = "com.instagram.android";
    private static final String FB = "com.facebook.katana";

    // YouTube: the Shorts player surfaces. Instagram: "clips" is Reels ("reel_viewer" is Stories — left alone).
    private static final Set<String> YT_PLAYER = set("reel_watch_fragment_root", "reel_player_page_container", "reel_watch_player", "reel_watch_pager_view_pager");
    private static final Set<String> IG_TABS = set("clips_tab");
    private static final Set<String> IG_PLAYER = set("clips_viewer_container", "clips_viewer_view_pager", "clips_viewer_fragment_container");
    /** Full-screen reel feeds reached from the tab, DMs, profiles or links. */
    private static final Set<String> IG_FEEDS = set("clips_homecoming_swipe_refresh_container", "clips_homecoming_refresh_container_view_stub");
    /** Inline reel carousels inside the home feed. */
    private static final Set<String> IG_SHELVES = set("clips_carousel_viewpager");
    /** The vertical pagers that swipe to the next Short/Reel. */
    private static final Set<String> PAGERS = set("reel_watch_pager_view_pager", "clips_viewer_view_pager");
    private final Set<String> seenIds = new HashSet<>();
    private static final Pattern FB_TAB = Pattern.compile("^(reels|video)\\b.*\\btab\\b.*", Pattern.CASE_INSENSITIVE);

    private String pkg;
    private String shortsLabel = "shorts";
    /** Resource id → entry name ("" when it has none). Lookups happen once per id. */
    private final Map<Integer, String> names = new HashMap<>();
    /** Views we forced GONE; setVisibility keeps them that way. */
    private final Set<View> hidden = Collections.newSetFromMap(new WeakHashMap<>());
    /** Feed rows we collapsed. RecyclerView reuses rows, so these are restored on re-attach and re-checked. */
    private final Map<View, Integer> collapsedRows = new WeakHashMap<>();
    /** Pagers we already locked. */
    private final Set<View> locked = Collections.newSetFromMap(new WeakHashMap<>());
    private boolean labelResolved;

    private static Set<String> set(String... s) { return new HashSet<>(Arrays.asList(s)); }

    private static void log(String msg) { XposedBridge.log("Unscroll: " + msg); }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!lpparam.packageName.equals(lpparam.processName)) return; // main UI process only
        pkg = lpparam.packageName;
        if (!pkg.equals(YT) && !pkg.equals(IG) && !pkg.equals(FB)) return;

        try {
            XposedHelpers.findAndHookMethod(View.class, "onAttachedToWindow", new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) { safeCheck((View) p.thisObject); }
            });
            log("hooked View.onAttachedToWindow in " + pkg);
        } catch (Throwable t) { log("attach hook failed: " + t); }

        try {
            // Litho/Compose often set labels after attach.
            XposedHelpers.findAndHookMethod(View.class, "setContentDescription", CharSequence.class, new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    View v = (View) p.thisObject;
                    if (p.args[0] != null && v.isAttachedToWindow()) safeCheck(v);
                }
            });
            log("hooked View.setContentDescription in " + pkg);
        } catch (Throwable t) { log("description hook failed: " + t); }

        try {
            XposedHelpers.findAndHookMethod(View.class, "setVisibility", int.class, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    if (!hidden.isEmpty() && hidden.contains(p.thisObject)) p.args[0] = View.GONE;
                }
            });
        } catch (Throwable t) { log("visibility hook failed: " + t); }

        try {
            XC_MethodHook rewrite = new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    try {
                        Activity a = (Activity) p.thisObject;
                        Intent i = p.method.getName().equals("onNewIntent") ? (Intent) p.args[0] : a.getIntent();
                        rewriteIntent(i);
                    } catch (Throwable t) { log("intent rewrite failed: " + t); }
                }
            };
            XposedHelpers.findAndHookMethod(Activity.class, "onCreate", Bundle.class, rewrite);
            XposedHelpers.findAndHookMethod(Activity.class, "onNewIntent", Intent.class, rewrite);
            log("hooked Activity intents in " + pkg);
        } catch (Throwable t) { log("activity hook failed: " + t); }
    }

    /** youtube.com/shorts/ID → watch?v=ID; the "open Shorts" launcher shortcut → plain launch. */
    private void rewriteIntent(Intent i) {
        if (i == null) return;
        if (pkg.equals(YT)) {
            if ("com.google.android.youtube.action.open.shorts".equals(i.getAction())) {
                i.setAction(Intent.ACTION_MAIN);
                log("blocked Shorts shortcut");
            }
            Uri d = i.getData();
            if (d != null && d.getPath() != null && d.getPath().startsWith("/shorts/")) {
                String id = d.getPath().substring("/shorts/".length()).split("/")[0];
                if (!id.isEmpty()) {
                    i.setData(Uri.parse("https://www.youtube.com/watch?v=" + id));
                    log("rewrote Shorts link to normal player: " + id);
                }
            }
        } else if (pkg.equals(IG)) {
            Uri d = i.getData();
            if (d != null && d.getPath() != null && (d.getPath().startsWith("/reel/") || d.getPath().startsWith("/reels/"))) {
                String id = d.getPath().split("/").length > 2 ? d.getPath().split("/")[2] : "";
                if (!id.isEmpty()) {
                    i.setData(Uri.parse("https://www.instagram.com/p/" + id + "/"));
                    log("rewrote Reel link to post view: " + id);
                }
            }
        }
    }

    private void safeCheck(View v) {
        try { check(v); } catch (Throwable t) { /* never break the host app */ }
    }

    private void check(View v) {
        if (!labelResolved) resolveLabel(v);
        Integer oldHeight = collapsedRows.remove(v);
        if (oldHeight != null) restore(v, oldHeight);
        String name = entryName(v);
        CharSequence descCs = v.getContentDescription();
        String desc = descCs == null ? null : descCs.toString().trim();

        if (!pkg.equals(FB) && isPagerInPlayer(v, name)) { lockPager(v, name); return; }

        switch (pkg) {
            case YT:
                if (name != null && YT_PLAYER.contains(name)) return;
                if (desc != null && desc.toLowerCase(Locale.ROOT).equals(shortsLabel)) {
                    if (hasAncestorNamed(v, "pivot_bar", 6)) hide(v, "Shorts tab");
                    else hideFeedItem(v, "Shorts shelf/chip");
                }
                break;
            case IG:
                if (name != null && IG_TABS.contains(name)) { hide(v, "Reels tab"); return; }
                if (name == null) break;
                if (isPlayer(name)) return;
                if (IG_SHELVES.contains(name)) { hideFeedItem(v, "Reels shelf (" + name + ")"); return; }
                // Learn unknown reel surfaces: log each distinct clips_* id once so the next build can target it.
                if (name.startsWith("clips_") && !name.startsWith("clips_editor") && seenIds.add(name)) {
                    Activity a = activityOf(v.getContext());
                    log("seen " + name + " in " + (a == null ? "?" : a.getClass().getSimpleName()));
                }
                break;
            case FB:
                if (desc != null && FB_TAB.matcher(desc).matches()) hide(v, "Reels/Video tab (" + desc + ")");
                break;
        }
    }

    /** YouTube's own (possibly localized) "Shorts" string. */
    private void resolveLabel(View v) {
        labelResolved = true;
        if (!pkg.equals(YT)) return;
        try {
            Resources r = v.getContext().getResources();
            int id = r.getIdentifier("shorts", "string", YT);
            if (id != 0) shortsLabel = r.getString(id).toLowerCase(Locale.ROOT);
        } catch (Throwable ignored) { }
    }

    private String entryName(View v) {
        int id = v.getId();
        if (id == View.NO_ID || (id >>> 24) != 0x7f) return null;
        String cached = names.get(id);
        if (cached == null) {
            try { cached = v.getResources().getResourceEntryName(id); } catch (Throwable t) { cached = ""; }
            names.put(id, cached);
        }
        return cached.isEmpty() ? null : cached;
    }

    private boolean hasAncestorNamed(View v, String target, int depth) {
        ViewParent p = v.getParent();
        for (int i = 0; i < depth && p instanceof View; i++, p = p.getParent()) {
            if (target.equals(entryName((View) p))) return true;
        }
        return false;
    }

    /** Collapse a view so its siblings re-flow into the space (tab bars re-space evenly). */
    private void hide(View v, String what) {
        if (hidden.contains(v)) return;
        hidden.add(v);
        v.setVisibility(View.GONE);
        ViewGroup.LayoutParams lp = v.getLayoutParams();
        if (lp instanceof LinearLayout.LayoutParams) ((LinearLayout.LayoutParams) lp).weight = 0f;
        if (lp != null) { lp.width = 0; v.setLayoutParams(lp); }
        log("hid " + what);
    }

    /** Hide the RecyclerView row containing [v] (a shelf header or chip). */
    private void hideFeedItem(View v, String what) {
        View row = v;
        ViewParent p = v.getParent();
        for (int i = 0; i < 12 && p instanceof View; i++) {
            if (p.getClass().getName().endsWith("RecyclerView")) {
                if (collapsedRows.containsKey(row)) return;
                ViewGroup.LayoutParams lp = row.getLayoutParams();
                collapsedRows.put(row, lp != null ? lp.height : ViewGroup.LayoutParams.WRAP_CONTENT);
                if (lp != null) { lp.height = 0; row.setLayoutParams(lp); }
                row.setVisibility(View.GONE);
                log("hid " + what);
                return;
            }
            row = (View) p;
            p = p.getParent();
        }
        hide(v, what);
    }

    private void restore(View row, int height) {
        ViewGroup.LayoutParams lp = row.getLayoutParams();
        if (lp != null) { lp.height = height; row.setLayoutParams(lp); }
        row.setVisibility(View.VISIBLE);
    }

    private static boolean isPlayer(String name) {
        return name != null && (YT_PLAYER.contains(name) || IG_PLAYER.contains(name) || IG_FEEDS.contains(name) || name.startsWith("clips_viewer"));
    }

    /**
     * The swipe-to-next pager of a Shorts/Reels player: a known pager id, a ViewPager inside a
     * player, or a RecyclerView just below one. Deeper lists (comments) stay scrollable.
     */
    private boolean isPagerInPlayer(View v, String name) {
        if (name != null && PAGERS.contains(name)) return true;
        String cls = v.getClass().getName();
        boolean pager = cls.contains("ViewPager");
        if (!pager && !cls.endsWith("RecyclerView")) return false;
        ViewParent p = v.getParent();
        for (int i = 0, depth = pager ? 10 : 3; i < depth && p instanceof View; i++, p = p.getParent()) {
            if (isPlayer(entryName((View) p))) return true;
        }
        return false;
    }

    /**
     * Let the opened Short/Reel play (like, comment, follow all work) but stop swiping to the next one:
     * the pager swallows drags, while taps still reach the buttons on the page.
     */
    private void lockPager(View v, String name) {
        if (!locked.add(v)) return;
        v.setOnTouchListener((x, e) -> true);
        try { v.getClass().getMethod("setUserInputEnabled", boolean.class).invoke(v, false); } catch (Throwable ignored) { }
        log("locked pager " + (name != null ? name : v.getClass().getSimpleName()));
    }

    private static Activity activityOf(Context c) {
        for (int i = 0; i < 8 && c != null; i++) {
            if (c instanceof Activity) return (Activity) c;
            if (!(c instanceof ContextWrapper)) return null;
            c = ((ContextWrapper) c).getBaseContext();
        }
        return null;
    }
}
