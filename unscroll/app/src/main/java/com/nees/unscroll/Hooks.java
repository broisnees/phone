package com.nees.unscroll;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
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
    private final Set<String> seenIds = new HashSet<>();
    private static final Pattern FB_TAB = Pattern.compile("^(reels|video)\\b.*\\btab\\b.*", Pattern.CASE_INSENSITIVE);

    private static final long BACK_DEBOUNCE_MS = 900;

    private String pkg;
    private String shortsLabel = "shorts";
    /** Resource id → entry name ("" when it has none). Lookups happen once per id. */
    private final Map<Integer, String> names = new HashMap<>();
    /** Views we forced GONE; setVisibility keeps them that way. */
    private final Set<View> hidden = Collections.newSetFromMap(new WeakHashMap<>());
    /** Feed rows we collapsed. RecyclerView reuses rows, so these are restored on re-attach and re-checked. */
    private final Map<View, Integer> collapsedRows = new WeakHashMap<>();
    private long lastBack;
    private boolean labelResolved;

