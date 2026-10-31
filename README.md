# Nothing Phone (1) — Root Log & Recovery Kit

Last updated: 2026-10-31

## TODO (start here)

Needs you (quick):
- [ ] **Restart the phone** → applies the Inter system font (`nees_interfont`), activates the bootloop protector (YABP) and moves the charge-limit module into place
- [ ] **Test Unscroll v3** → tap a suggested reel in Instagram and a Short in YouTube: it plays, like/comment/follow work, swiping to the next one does nothing. Check with `phone log Unscroll` in Termux (look for `locked pager <id>`); if swiping still works, send the `seen …` lines so the pager id can be added to `PAGERS`
- [ ] **YouTube issue** you mentioned — open it and ask Claude to "look" (screen capture only)
- [ ] **Test MaxQ** → any video → ⚙ → Quality shows the highest option, not Auto
- [ ] **GCam** → gear → More settings → Configs → load `LMNothing_V2.0…agc` (alt: `Ghost_V2…agc`); files in `Download/AGC.9.1/configs/`
- [ ] **AdAway** → "Root based ad blocker" → Apply
- [ ] **Neo Backup** → pick a backup folder
- [ ] **Talk** → make a test call and send a test SMS (incl. one with an OTP code)
- [ ] **Banking apps** → open each (eSewa, Nabil, Khalti, MeroShare, Global IME, Nagarik) and check they work
- [x] **Restore files** → done 2026-10-31 with `scripts/restore.sh` (all real files verified on the phone)

- [ ] **Glyph Tweaks** → open the app, tap zones on the diagram, turn on the features you want (volume + brightness indicators are already on), add the Glyph light / battery / timer tiles to Quick Settings

Open problems / not possible:
- [ ] **Facebook reels inside the feed** — only the Reels tab is hidden (no stable view to detect)
- [ ] **MMS** (picture messages) not supported in Talk
- [x] **Play Integrity (keybox route, 2026-10-31)** → result: **Basic only** (Device/Strong fail; before: all UNEVALUATED). Tried keyboxes TAKR 28 and TAKR 35 (current; RKP, expires 2026-11-06), with and without PIF spoofing. PIF spoof flags (`spoofBuild`, `spoofProps`, `spoofVendingBuild`) are now false in `/data/adb/modules/playintegrityfix/pif.prop` (original in `modules/trickystore-backup-2026-10-31/pif.prop`); PIF's action button may overwrite that. Security patch spoofed to 2026-10-05. Stopped testing to avoid frequent checks; Google Pay tap-to-pay still won't work. Retry later: KsuWebUI → Tricky Store → menu → Keybox → **Repo** → newest STRONG/DEVICE entry (ignore the scam ads on that page) → reboot → check once. The menu's **Addon** source was empty on 2026-10-31. Undo: restore `modules/trickystore-backup-2026-10-31/keybox.xml` to `/data/adb/tricky_store/keybox.xml`
- [ ] Photos "not responding" after the wipe — update Photos from Play Store, let first sync finish on Wi-Fi + charger


**Glyph Tweaks v2 (2026-10-31):** large tappable zone diagram (taps only count near a light) with All on / All off; accent Nothing red (#E8302A); settings reads are reactive via SettingsBus so switches redraw instantly; brightness is a drag-anywhere pill slider that lights every glyph live while dragging and saves on release (tick every 10 %); new Control card: master "Glyph effects" pause (only the Glyph light still works) and "Respect Do Not Disturb" (default on: notification, call and ambient glyphs stay dark in DND); visualizer is event-driven (AudioPlaybackCallback, no polling, off with screen off); the foreground "Glyph Tweaks is running" notification is removed once the notification listener is connected. Measured: ~10.6 MB PSS, 0.0 % CPU idle. Volume levels arrive instantly from Haptix (`com.nees.glyph.LEVEL` broadcast from SystemUI's volume controller).
Vector's "Vector loaded" notification turned off: `/data/adb/lspd/cli config set status-notification false`.

**Glyph Tweaks v3 (2026-10-31):** perceptual brightness (level² → 1–100 % goes from faint glow to full power); new `VizEngine` (6 log bands, per-band auto-levelling, 20 captures/s rendered at 30 fps with time-based decay) with 4 styles using all 15 lights — Spectrum, Pulse, Orbit, Shockwave (kick-triggered waves travelling dot → strip → ring → diagonal → camera); new Live features — Unlock animation (sweep on `USER_PRESENT`), Busy dot (dot breathes at 10 fps while any notification shows a progress bar), Live progress (progress % on the strip, max 1 update/s). Measured: ~12 MB PSS, 0.0 % CPU idle.

**Glyph Tweaks v4 (2026-10-31):** glyph-synced haptics (`Buzz`: predefined TICK/CLICK/HEAVY only; notification/media vibes silent in silent mode; calls never double-vibrate). Every pattern step has a vibration (presets ship with matching ones); editor gets per-step Vibration chips and "Record taps" (tap lights in rhythm → steps + timing); visualizer "Feel the beat" (tick per kick, ≥150 ms apart, USAGE_MEDIA); unlock sweep gets tick → click. Added missing VIBRATE permission. Measured ~11 MB PSS, 0.0 % CPU idle, 0 crashes.

**Glyph Tweaks v5 (2026-10-31):** volume and brightness each choose a display style (`LevelStyle`): Strip (8-segment bar), Ring (4 segments clockwise from top-right), Diagonal (dimmer), Camera (dimmer), Whole phone (all 15 lights fill dot → camera). Picking a style previews it at 60 %.

**Glyph Tweaks v6 (2026-10-31, built — install pending reconnect):** separate "⚡ Blink" chip per volume / brightness row (blink fires at full LED power, 180 ms); brightness slider rewritten (single gesture handler: jumps to the finger on touch-down, owns the drag so the page can't scroll it away, commits on lift) and relabelled "Effect brightness" (Nothing's own Glyph brightness is the separate system setting `led_brightness_value`); busy dot sources with chips — Downloads (progress bars), Background tasks (ongoing service/navigation/stopwatch/workout/location-sharing notifications, media + system excluded), Mic in use and Camera in use (steady privacy glow, via AudioRecordingCallback / CameraManager.AvailabilityCallback).

**Glyph Tweaks v7 (2026-10-31):** the ⚡ blink became a per-row press-effect picker (`PressEffect`): Off, Flash (diagonal), Chase (head + tail racing all 15 lights along the path in 320 ms), Spin (two laps of the ring), Sparkle (7 random lights twinkling). Direction-aware (volume/brightness up runs forward, down runs backward), drawn at full LED power on top of the level. Defaults: volume Chase, brightness Off. LED-map walk-through run (hw channels 1–16, 2.5 s each) — awaiting the user's mismatch report to correct `Zone.hw`.


### Fonts switcher (2026-10-31)
Two OFL system fonts, one active at a time (SF Pro / Google Sans are proprietary — not bundled):
- `nees_interfont` — Inter (SF-Pro-like), **active**.
- `nees_robotoflex` — Roboto Flex (Google's own, Pixel base), staged/disabled.
Switch: `adb shell su -c 'sh /data/adb/fontswitch inter|roboto|stock'` then reboot. Sources in `fonts/` (rf_module), zips in `install/`.

## Protection & battery (2026-10-31)
- **Bootloop protector**: YetAnotherBootloopProtector v8.138 (reviewed: local-only scripts). 3 failed boots, boot > 100 s, zygote/SystemUI crash-loop or SystemUI dead > 25 s → disables all modules + `service.d` scripts, reboots. Log: `/data/local/tmp/service.log`. Whitelist: `/data/adb/YABP/allowed-modules.txt`. Re-enable modules afterwards in Magisk (or its action button).
- **Charge limit** (`nees_chargelimit`, source `chargelimit/`): pauses at 80 %, resumes at 75 % via `/sys/class/qcom-battery/usb_charger_en` (0 = charger input off, 1 = on). Change limits in `/data/adb/chargelimit.conf` (`PAUSE=`/`RESUME=`). Removing the module re-enables charging. ACC removed (it couldn't find a switch).
- **AdAway**: active — 76,741 domains in the systemless hosts file (Magisk `hosts` module).

## Status (2026-10-31)

| Area | State |
|---|---|
| Root | Magisk 31.1, Zygisk, Vector (LSPosed) 2.2 |
| Theme | AMOLED black overlay + self-healing watcher; rounded notification cards (`nees_notifstyle`) |
| Haptics | Haptix v3 **enabled** (app launch, home, shade, QS, notification dismiss, volume); intensity Medium |
| Shorts/Reels | Unscroll v2 **enabled** (YouTube, Instagram, Facebook) |
| YouTube quality | MaxQ **enabled** (verified `1440p60 (was 720p60)`) |
| Phone/SMS | Talk app (default dialer + SMS) |
| Speed | 120 Hz locked, animations 0.3×, apps AOT-compiled, Smart Center disabled, FB/IG/X/Google Home background-restricted, debloat |
| Banking | DenyList for 7 apps; Play Integrity: Basic only (keybox route) |

## Device

| | |
|---|---|
| Model | Nothing Phone (1) — A063, codename **Spacewar** |
| Serial (adb) | [redacted] · fastboot: [redacted] |
| Firmware | **V3.2-260618-1045** (Android 15, EOL build — no more OTAs) |
| Fingerprint | `Nothing/Spacewar/Spacewar:15/AQ3A.240929.001/2606181045:user/release-keys` |
| Partitions | A/B, active slot **_b**. Root goes to `boot` (no `init_boot` on this phone) |

## Current state

| Item | Status |
|---|---|
| Bootloader | **Unlocked** (verified boot: orange) |
| Root | **Magisk 31.1**, installed permanently to `boot_b` |
| Shell root | Granted in Magisk → Superuser → Shell |
| Zygisk | On |
| Modules | Play Integrity Fix v4.9-inject-s (KOWX712), TrickyStore 1.4.1 (default AOSP keybox, `DeviceID="sw"`) |
| TrickyStore targets | `com.google.android.gms`, `com.android.vending`, `com.google.android.gsf`, `gr.nikolasspyr.integritycheck` |
| Tricky Addon | v4.4 (KOWX712, `install/TrickyAddonModule-v4.4.zip`) — hides itself as `/data/adb/modules/.TA_utl`, WebUI via KsuWebUI 1.0-34 (root granted in Magisk). Original AOSP keybox + target list backed up in `modules/trickystore-backup-2026-10-31/` |
| Play Integrity | **Basic only** since the 2026-10-31 keybox route (was all UNEVALUATED); Device/Strong fail — see below |
| DenyList (option A) | **Enforced** for eSewa, Nabil Bank, Khalti, MeroShare, Global IME (globalsmart), Nagarik App, Google Wallet |
| AMOLED black | **On**: fabricated overlay `com.android.shell:amoled`, re-applied at boot by `/data/adb/service.d/amoled.sh` |

## Folder contents

| Path | What it is |
|---|---|
| `firmware/boot.img` | **Stock boot image — your recovery file. Never delete.** SHA-256 `57f19345…3d9e4e` (matches archive hash) |
| `firmware/dtbo.img`, `vbmeta.img`, `vendor_boot.img` | Other stock boot-chain images, same build |
| `firmware/*-image-boot.7z`, `*-hash.sha256` | Original download from [spike0en/nothing_archive](https://github.com/spike0en/nothing_archive/releases/tag/Spacewar_V3.2-260618-1045) |
| `magisk/magisk_patched.img` | Stock boot patched with Magisk 31.1 (KEEPVERITY/KEEPFORCEENCRYPT=true). SHA-256 `e35a9202…3eaec7` |
| `magisk/Magisk-v31.1.apk` | Official Magisk app (github.com/topjohnwu/Magisk) |
| `modules/` | Play Integrity Fix + TrickyStore zips as installed |
| `theme/amoled.xml` | AMOLED overlay colours (system backgrounds → `#000000`, cards → `#0b0b0b`–`#222222`). Copy on phone: `/data/system/amoled.xml` |
| `theme/amoled-boot.sh` | Magisk boot script (`/data/adb/service.d/amoled.sh`). Every 5 s it checks the *effective* `system_background_dark`; if it isn't `#000000` (SystemUI regenerated its dynamic overlay on a theme change or a SystemUI restart) it re-stacks the black overlay on top |
| `scripts/backup.sh` | Incremental phone → Mac backup (pulls only new/changed files into `~/phone-backup`) |
| `install/` | **Every ready-to-install file**: Talk.apk, Haptix.apk, Unscroll.apk, MaxQ.apk, NotifStyle/InterFont Magisk zips, Magisk, Vector, PIF, TrickyStore, AdAway, Neo Backup, AFWall+, ACC, GCam configs |
| `talk-app/` | Talk (dialer + SMS) source — Kotlin/Compose. Build: `gradle :app:assembleRelease`, then `cmd package compile -m speed -f com.nees.talk` |
| `haptics-module/`, `unscroll/`, `maxq/` | Source of the three LSPosed modules (Haptix, Unscroll, MaxQ) |
| `overlay-src/` | NotifStyle RRO source + test notification app |
| `fonts/` | Inter 4.1 + the Inter system-font Magisk module source |
| `camera/` | GCam AGC 9.1 V13 (snap) APK + Phone (1) configs |
| `apps/` | Downloaded APKs (AdAway, Neo Backup, AFWall+, ACC) |
| `phone-backup` | Shortcut (symlink) to `~/phone-backup` |

Phone backup: **`~/phone-backup`** (65 GB — DCIM, Pictures/Screenshots, Movies, Documents, Download, WhatsApp media, Telegram). Taken 2026-10-30 before unlocking; 1 file skipped (a camera crash log).

## What was done (in order)

1. Deleted `/sdcard/np4` (2× 3.1 GB leftover DSU system images).
2. Mac cleanup (~52 GB freed): Docker images/cache (volumes kept), Claude `vm_bundles`, caches, Gradle caches, pnpm store, Ollama models.
3. Backed up the phone with `scripts/backup.sh`.
4. Downloaded the exact-build stock boot image and verified its hash.
5. `fastboot flashing unlock` → phone wiped and set up again.
6. Patched `boot.img` with Magisk's own `boot_patch.sh` over adb (the app's UI kept closing when driven remotely).
7. `fastboot boot magisk_patched.img` (test boot) → Magisk → Install → **Direct install** → restarted. Root confirmed after restart.
8. Granted Shell root, enabled Zygisk, installed Play Integrity Fix, refreshed its fingerprint.
9. Integrity still failed → found keystore error, added TrickyStore → still UNEVALUATED.

## Why Play Integrity fails (root cause)

On Nothing/OnePlus/OPPO Qualcomm phones, **unlocking the bootloader makes the TEE switch to an alternate keystore that has no attestation key**. Logs:

```
keystore2: While generating Key without explicit attestation key. Error::Km(-10003)
servicemanager: Could not find ...IRemotelyProvisionedComponent/default in the VINTF manifest
```

`-10003` = TEE can't open its attestation key. Since Android 13, Google requires hardware key attestation for **every** verdict (even Basic), so all verdicts come back UNEVALUATED. TrickyStore with the default AOSP key is rejected by Google. Play Store (45.0.21) and Play Services (26.42.31) are current, so they are not the cause. Widevine has likely dropped to L3 (Netflix/Prime in SD).

2026-10-31: chose the **TrickyStore leaked-keybox route** (Tricky Addon → Keybox → Repo → TAKR 35 (TAKR 28 gave Basic only); security patch spoofed to 2026-10-05 in `/data/adb/tricky_store/security_patch.txt`). TrickyStore signs attestation in software with the keybox, so it works around the dead TEE key. It spoofs another device's identity and gets revoked by Google every few weeks; swapping `keybox.xml` back undoes it. Still avoided: KmInstallKeybox (one-time TEE provisioning, can't be undone).

## Options from here

- **A. Stay rooted + Magisk DenyList**: hide root from banking/wallet apps that only check for root on the phone (often local banks, eSewa, Khalti). Google Pay tap-to-pay, apps that check with Play Integrity, and Netflix HD will not work.
- **B. Unroot + relock**: restores the original keystore → full integrity, Google Pay, Widevine L1. **Wipes the phone.** No root/theme.

## Recovery commands

Phone stuck on the logo (bootloop) — hold Power + Vol-Down for fastboot, then:
```sh
fastboot flash boot firmware/boot.img
fastboot reboot
```

Unroot + relock (option B) — order matters. **Never relock while Magisk is flashed (can brick):**
```sh
# 1. Magisk → Modules: remove all; then restore stock boot
adb reboot bootloader
fastboot flash boot firmware/boot.img
# 2. Relock (wipes the phone)
fastboot flashing lock
# 3. Set up, then restore files from ~/phone-backup
```

OTA updates: none expected (EOL build). If one ever appears: Magisk → Uninstall → Restore images → install OTA (don't restart) → Magisk → Install → Install to inactive slot.

## Useful commands

```sh
adb shell su -c 'magisk -v'                       # Magisk running?
adb shell su -c 'ls /data/adb/modules'            # installed modules
adb shell su -c 'magisk --sqlite "SELECT * FROM settings"'   # zygisk=1?
bash scripts/backup.sh                            # re-run incremental backup
```

Note: `adb reboot ...` is blocked by this Mac's Claude hook; run it yourself with `!adb reboot bootloader`.

## Still to do

See **TODO (start here)** at the top. Done: banking option A (DenyList), AMOLED black theme.

## AMOLED theme commands

```sh
adb shell su -c 'cmd overlay disable com.android.shell:amoled'   # turn off (back to grey)
adb shell su -c 'cmd overlay enable --user 0 com.android.shell:amoled'   # turn on
# change colours: edit theme/amoled.xml, push to /data/system/amoled.xml, then
adb shell su -c 'cmd overlay fabricate --target android --name amoled --file /data/system/amoled.xml'
# remove completely
adb shell su -c 'rm /data/adb/service.d/amoled.sh'; adb shell su -c 'cmd overlay fabricate --help' # (re-fabricating with --file replaces it)
```

## UI mods (2026-10-31)

- Iconify skipped: its README lists Nothing OS as unsupported.
- **Vector v2.2** (JingMatrix's LSPosed continuation) — Magisk module `zygisk_vector`, file `modules/Vector-v2.2-3080-Release.zip`.
- **Nothing Tweaks v3.1** (`com.souleven.nothingos`, github.com/RevealedSoulEven/NothingTweaks) — `modules/NothingTweaks-v3.1-release.apk`. Enabled in Vector, scope: `system`, `com.android.systemui`, `com.nothing.launcher`, `com.android.settings`.

```sh
# Vector CLI (as root on the phone)
/data/adb/lspd/cli modules ls
/data/adb/lspd/cli modules disable com.souleven.nothingos
/data/adb/lspd/cli scope ls com.souleven.nothingos
```

**Notification Style** (own build) — Magisk module `nees_notifstyle`: static RRO `com.nees.overlay.notifstyle` on SystemUI.
Source `overlay-src/NotifStyle/res/values/dimens.xml` (stock → new): `notification_corner_radius` 16→28dp, `notification_corner_radius_small` 4→28dp, `notification_divider_height` 2→6dp.
Rebuild: aapt2 compile/link against `android-35/android.jar` → zipalign → apksigner (key `overlay-src/build/key.jks`, pass `overlay`) → copy into `overlay-src/module/system/product/overlay/NotifStyle/` → zip → `magisk --install-module`. Shell-fabricated overlays can only change colours/ints, which is why sizes need a real APK.

If System UI crash-loops: hold **Volume Down** during boot (Magisk safe mode, disables all modules for that boot).

## Performance (2026-10-31)

- Force-compiled (`speed`) Settings, Settings search, SystemUI, Nothing Launcher, Gboard — they were `status=verify` (uncompiled) after the wipe.
- Ran `cmd package bg-dexopt-job` to compile every other app.
- Animation scales already 0.5×. CPU governor left on stock `schedutil` (kernel "boost" tweaks cost battery/heat for little gain).
- Removed for user 0 (reversible): Google TV, YouTube Music, Keep, Docs, Gemini, Meet, AR Core, Google Feedback, Digital Wellbeing.

### Speed-ups (2026-10-31, later)
- Screen locked at 120 Hz: `settings put system min_refresh_rate 120.0` (and peak 120).
- Animation scale 0.5× (all three scales; user preference 2026-10-31).
- Disabled Nothing Smart Center: `pm disable-user --user 0 com.nothing.smartcenter` (undo: `pm enable com.nothing.smartcenter`).
- Background-restricted Facebook, Instagram, Google Home (X un-restricted again + added to DenyList, 2026-10-31 — X login still blocked by Play Integrity; use x.com as a Chrome web app): `cmd appops set <pkg> RUN_ANY_IN_BACKGROUND ignore` + standby bucket `restricted` (undo: `... allow` and `am set-standby-bucket <pkg> active`).
- Removed AFWall+ (and its Vector module entry).
- Talk AOT-compiled: `cmd package compile -m speed -f com.nees.talk` (re-run after each Talk update).

Restore a removed app: `adb shell cmd package install-existing <package>`
e.g. `com.google.android.apps.tachyon` (Meet), `com.google.android.apps.bard` (Gemini).

DenyList commands:
```sh
adb shell su -c 'magisk --denylist ls'          # list hidden apps
adb shell su -c 'magisk --denylist add <pkg>'   # hide root from another app
```

## Sources

- [XDA: Fix Widevine L1 unlocked bootloader (alternate keystore, no attestation key)](https://xdaforums.com/t/fix-widevine-l1-unlocked-bootloader.4731374/)
- [GitHub: -10003 traced to km_read_attestation_key](https://github.com/openai/codex/issues/54137)
- [TEESimulator #216: -10003 hard HAL status](https://github.com/JingMatrix/TEESimulator/issues/216) · [#293: all-UNEVALUATED with bad keybox](https://github.com/JingMatrix/TEESimulator/issues/293)
- [XDA: Tricky Store thread](https://xdaforums.com/t/tricky-store-bootloader-keybox-spoofing.4683446/page-397)
- [XDA: Nothing Phone 3a Pro integrity — relock restored it](https://xdaforums.com/t/nothing-phone-3a-pro-best-way-to-fix-play-integrity.4770068/)
- [KOWX712/PlayIntegrityFix](https://github.com/KOWX712/PlayIntegrityFix) · [5ec1cff/TrickyStore](https://github.com/5ec1cff/TrickyStore)
- [Google: Android 13+ verdict changes](https://developer.android.com/google/play/integrity/improvements) · [Integrity verdicts](https://developer.android.com/google/play/integrity/verdicts)
- [XDA: Unlock & root Nothing Phone 1](https://xda-developers.com/how-to-bootloader-unlock-root-magisk-nothing-phone-1/)

## Haptics module (Haptix) — ENABLED (v3, 2026-10-31)

`com.nees.haptix` (Xposed, runs under Vector). Source `haptics-module/app/src/main/java/com/nees/haptix/Hooks.java`, APK `haptics-module/app/build/outputs/apk/release/app-release.apk` (v3 built, **not yet installed** — the phone has v2 installed, disabled).

Rules: state-change hooks only (no per-frame expansion/scroll/draw work), one effect per event, ≥120 ms between haptics (60 ms for volume key repeat, 80 ms for slider detents). Effects go through `View.performHapticFeedback` (or the hidden `Vibrator#performHapticFeedback` when no view), so they follow the system touch-haptic toggle and intensity.

| Event | Effect | Hook |
|---|---|---|
| Shade reaches fully open / closes | CLOCK_TICK (selection) | SystemUI `ShadeExpansionStateManager.updateStateInternal(int)` |
| Quick-settings tile tap | CLOCK_TICK | SystemUI `QSTileImpl.click(Expandable)` |
| Notification swiped away | CONFIRM (success) | SystemUI `SwipeHelper.dismissChild(View,float,boolean)` (notification helpers only) |
| Volume key step | CLOCK_TICK | SystemUI `VolumeDialogControllerImpl.onVolumeChangedW(int,int)` (FLAG_SHOW_UI only) |
| Volume reaches max or min | CONFIRM once (not repeated while held) | same |
| Volume panel slider detent | CLOCK_TICK, ≤1 per 80 ms | SystemUI `VolumeDialogControllerImpl.onSetStreamVolumeW(int,int)` |
| Ringer mode change | VIRTUAL_KEY (impact) | SystemUI `VolumeDialogControllerImpl.onSetRingerModeW(int,boolean)` |
| App icon tap / launch | VIRTUAL_KEY | Launcher `Launcher.startActivitySafely(View,Intent,ItemInfo)` |
| Folder open | VIRTUAL_KEY | Launcher `Folder.animateOpen()` / `animateOpen(List,int)` |
| Swipe-up commits to home (app minimize) | CONFIRM | Launcher `AbsSwipeUpHandler.animateToProgress(…, GestureEndTarget=HOME, …)` |
| Swipe-up into recents | CLOCK_TICK | same, `GestureEndTarget=RECENTS` |
| Recents card snap | CLOCK_TICK | Launcher `PagedView.onPageEndTransition()` on `RecentsView` only |
| Back gesture | — (Nothing already vibrates in `BackPanelController`) | not hooked |

Measured (v2, before the user asked to stop testing): SystemUI CPU over 4 shade open/close cycles was 53–59% of one core with the module disabled and 61–79% enabled (first run right after a SystemUI restart). v1 used a per-frame expansion hook and made SystemUI laggy; v2/v3 removed all per-frame hooks.

### Test it yourself (when the phone is free)
1. Install v3: `adb install -r haptics-module/app/build/outputs/apk/release/app-release.apk`
2. Enable + scope (as root): `/data/adb/lspd/cli modules enable com.nees.haptix` and `/data/adb/lspd/cli scope set com.nees.haptix com.android.systemui/0 com.nothing.launcher/0`
3. Restart the phone (hooks load at boot).
4. Check the bind log: `grep Haptix $(ls -t /data/adb/lspd/log/modules_*.log | head -1)` — every hook should say `hooked …`, none `FAILED`.
5. Try each row of the table above; the same log prints `fired <event>` for each haptic.
6. If anything feels laggy or wrong: `/data/adb/lspd/cli modules disable com.nees.haptix` and restart the phone.

## Unscroll (Shorts/Reels) — ENABLED (2026-10-31)

Vector/LSPosed module `com.nees.unscroll` (source `unscroll/`, APK `unscroll/app/build/outputs/apk/release/app-release.apk`). Works on the **official** apps — no ReVanced, no patched APKs. Uses only framework hooks (`View.onAttachedToWindow`, `View.setContentDescription`, `View.setVisibility`, `Activity.onCreate/onNewIntent`), matching stable resource names and accessibility labels, so app updates rarely break it.

| App (version tested against) | Hidden / blocked |
|---|---|
| YouTube 21.41 | Shorts tab in the bottom bar (label "Shorts" under `pivot_bar`); Shorts shelves/chips in feeds (row collapsed, restored if the row is recycled); Shorts player (`reel_watch_fragment_root`, `reel_player_page_container`, `reel_watch_player`, `reel_watch_pager_view_pager`) → plays, but swiping to the next Short is locked; `youtube.com/shorts/ID` links → open in the normal player; "Shorts" launcher shortcut → normal launch |
| Instagram 453 | Reels tab (`clips_tab`); Reels viewer (`clips_viewer_*`) → plays, but swiping to the next reel is locked; `/reel/ID` links → open as a post. Stories (`reel_viewer_*`) are untouched |
| Facebook 584 | Bottom/top tab labelled "Reels…tab" or "Video…tab" (Litho, matched by label). Reels opened from the feed are not blocked yet (no stable signal) |

Disable: `adb shell su -c '/data/adb/lspd/cli modules disable com.nees.unscroll'`, then force-stop the three apps. Log lines: grep `Unscroll` in the newest `/data/adb/lspd/log/modules_*.log`.

**Unscroll v2 (2026-10-31):** Instagram reel viewers matched by any `clips_viewer*` id plus the `clips_homecoming_*` feed; inline `clips_carousel_viewpager` shelves hidden. Players now get their pagers' touch input disabled before closing; a reel opened on its own screen (e.g. from a DM) finishes that activity, otherwise back is pressed up to 3× until the player is gone. Unknown `clips_*` ids are logged once as `Unscroll: seen <id> in <Activity>` — check with `grep Unscroll` in the newest `/data/adb/lspd/log/modules_*.log` and add them to `IG_FEEDS`.

**Unscroll v3 (2026-10-31):** no more backing out. A Short/Reel you tap stays open so you can watch, like, comment and follow; only its swipe-to-next pager is locked (drags swallowed, taps still reach the buttons). Locked: ids in `PAGERS` (`reel_watch_pager_view_pager`, `clips_viewer_view_pager`), any ViewPager inside a player, or a RecyclerView ≤ 3 levels below one (deeper lists like comments still scroll). Each lock logs `Unscroll: locked pager <id>`. Tabs stay hidden and Shorts links still open in the normal player.

## Termux, SSH, Claude Code and the `phone` CLI (2026-10-31)

- **Termux** 0.118.3 + **Termux:Boot** 0.8.1 (GitHub builds), battery-optimization exempt, root granted in Magisk (uid 10296, user `u0_a296`).
- **SSH**: `sshd` on port **8022**, key-only (Mac `~/.ssh/id_ed25519.pub`), started at boot by `~/.termux/boot/start-sshd`.
  - USB: `adb forward tcp:8022 tcp:8022 && ssh -p 8022 u0_a296@127.0.0.1`
  - Wi-Fi: `ssh -p 8022 u0_a296@192.168.18.225` (needs macOS Settings → Privacy & Security → Local Network on for your terminal)
- **Claude Code**: no Android build, so it runs in a Debian proot (`proot-distro`, Node 20). `claude` in Termux is a wrapper (`$PREFIX/bin/claude`) that enters Debian with Termux home bind-mounted and the current directory kept. First run: `claude` → log in. Update: `proot-distro login debian -- npm update -g @anthropic-ai/claude-code`.
- **`phone` CLI** (source `cli/phone`, installed at `$PREFIX/bin/phone`): `phone status` (battery, charge limit, root, keybox, ssh, Magisk + LSPosed modules) · `phone charge 80 75` · `phone mods` / `phone mod on|off <id>` · `phone xposed` / `phone xposed on|off <pkg>` · `phone log Unscroll` · `phone restart <pkg>` · `phone keybox` / `phone keybox restore <file>` · `phone ssh start|stop` · `phone claude`. Reinstall after editing: `scp -P 8022 cli/phone u0_a296@127.0.0.1:/data/data/com.termux/files/usr/bin/phone`.
- Gotchas: commands run as the Termux user through Magisk `su` need the app's groups (`-G 3003` inet) for network. The Vector CLI (`/data/adb/lspd/cli`) prints nothing when started via Termux's `su`, so `phone xposed` talks to the Vector daemon's socket `/data/adb/lspd/.cli_sock` directly (Node client in `cli/phone`: two magic longs + Java `writeUTF` JSON `{command, action, targets, options}`) — listing and `phone xposed on|off <pkg>` both work from Termux.

## MaxQ (YouTube max quality) — ENABLED

LSPosed/Vector module `com.nees.maxq` (source `maxq/app/src/main/java/com/nees/maxq/Hooks.java`, APK `maxq/app/build/outputs/apk/release/app-release.apk`). Official YouTube, no patched APK.

- Every time YouTube sets a video's quality list, MaxQ picks the **highest resolution** (skipping "Premium" entries) and applies it through YouTube's own quality-menu method. It switches when the player is on Auto, and once per new video; a quality you pick by hand on the same video is left alone.
- Mechanism ported from Morphe (ReVanced continuation) `VideoInformationPatch` / `RememberVideoQualityPatch`, MorpheApp/morphe-patches @ `0bb704a9a4edb5324f6e607f3fc5a531713c607c` (ReVanced's own GitHub repo is DMCA-blocked).
- Fingerprints (found at runtime with DexKit 2.3.0, cached in `/data/data/com.google.android.youtube/maxq_cache.properties` until YouTube's APK changes):
  - quality setter: `public final void ?(VideoQuality[], int, boolean)` using string `"menu_item_video_quality"` → `ocq.l` on 21.41.529
  - its `void ?(X)` method reading field F then writing field G on F's object → `ocq.f` (`qrw`) → `qrw.av` (`arqs`)
  - `void ?(VideoQuality)` on G's type → `arqs.b(albv)`; VideoQuality `albv`: int resolution, String label
- Log: `grep MaxQ` in the newest `/data/adb/lspd/log/modules_*.log` — expect `ready … (scanned|cached)` at YouTube start and `set 1080p60 (was Auto)` per video.
- Disable: `adb shell su -c '/data/adb/lspd/cli modules disable com.nees.maxq'` then `am force-stop com.google.android.youtube`.
- After a YouTube update: nothing to do if the log still says `ready`; it rescans automatically. If it logs `quality setter not found` / `setQuality … not found`, YouTube changed the code shape — re-check the Morphe fingerprints (`patches/youtube/video/information/Fingerprints.kt`).

## Glyph Tweaks (com.nees.glyph) — 2026-10-31

Root-powered Glyph app. Source `glyph-app/`, APK `install/GlyphTweaks.apk`.

**LED interface:** aw210xx driver, `/sys/class/leds/aw210xx_led/single_led_br` ← `"<hw> <0-4095>"`, written through one persistent `su` shell (only changed LEDs are written). Zone → hw LED (from `drivers/leds/leds_aw210xx.c` `frame_leds_effect`): A camera 7 · B diagonal 1 · C1–C4 ring 2–5 · E dot 16 · D1–D8 strip (bottom→top) 13, 11, 9, 12, 10, 14, 15, 8. Read-back: `cat .../reg` (2 bytes per LED from 0x21).

**Features** (all toggles on the Home screen; service runs only while one is on, 0 % CPU idle):
- Notifications: pattern per app (or default / off) via NotificationListener; "alert once" updates don't re-flash
- Calls: looping pattern per contact while ringing (PHONE_STATE broadcast + contact lookup key)
- Volume & brightness indicators: level on the strip for 1.5 s, 300 ms fade (**on by default**)
- Charging meter: battery % on plug-in and on each % while charging; next segment breathes, dot lit
- Music visualizer: Visualizer on session 0 only while music plays; bass → strip + ring beat, mids → diagonal, highs → camera; sensitivity slider
- Ambient glow: ring breathes with unread notifications while the screen is off; Face-down mode: full brightness + ambient ring when face down (accelerometer only while enabled)
- Glyph light (all zones, own brightness), countdown timer (strip drains, flashes at end), battery-on-demand — plus Quick Settings tiles "Glyph light", "Glyph battery", "Glyph timer"
- Patterns: 8 presets + editor (step timeline, tap zones on the diagram, per-step brightness/duration, preview)

**Permissions granted via adb:** POST_NOTIFICATIONS, RECORD_AUDIO (+ appop allow for background capture), READ_CONTACTS, READ_PHONE_STATE, READ_CALL_LOG, notification-listener access, Magisk root (uid policy).

**Disable:** turn features off in the app (service stops), or `adb shell am force-stop com.nees.glyph`; uninstall: `adb uninstall com.nees.glyph`. If LEDs are ever left lit: `adb shell su -c 'echo 0 > /sys/class/leds/aw210xx_led/all_white_leds_br'`.

**Verified:** service starts in foreground; volume up/down lit strip D1–D3 at 70 % (reg read-back) and faded to 0; all 15 zones distinct; no crashes. **Not verified on device:** brightness indicator, visualizer, calls, notifications, meters, tiles (need real use). Our frames override Nothing's own Glyph while an effect is active; when idle we leave the LEDs alone.


## Display: full-screen / no-letterbox (2026-10-31)

Magisk module **nees_fullscreen** (source `display-fix/module/`, zip `install/FullScreen-magisk.zip`).

**(A) No letterbox — works.** Two layers, applied by the module's `service.sh` at every boot:
- Global `settings put global force_resizable_activities 1` — makes activities resizable so they fill the 20:9 display instead of showing black bars. (Standard developer-option; needs one reboot to take effect.)
- Per-app aspect override for stubborn apps: `am compat enable OVERRIDE_MIN_ASPECT_RATIO <pkg>` + `OVERRIDE_MIN_ASPECT_RATIO_LARGE <pkg>`, read from `/data/adb/fullscreen/apps.txt`, skipping `/data/adb/fullscreen/exclude.txt`.
- Excludes by default: `com.nothing.camera`, `org.codeaurora.snapcam` (viewfinders distort if forced). Add more apps to force by editing `apps.txt`; add apps to protect in `exclude.txt`.

**(B) Status/nav bar waste — partial, by design.** Nav is already gesture mode (`navigation_mode=2`), so the bottom is already just the thin pill. There is **no safe global way to hide the status bar on Android 15** (`policy_control immersive` is unreliable and SystemUI may ignore it; hiding it breaks the clock/notifications). Left as-is. `force_resizable` already lets modern apps draw edge-to-edge under the bars.

**Test:** reboot, then open an app that showed black bars before (an old app, or one you'd set to "Default aspect ratio"). It should now fill the screen. If one still shows bars, add its package to `/data/adb/fullscreen/apps.txt` and reboot (or run the two `am compat enable` lines + `am force-stop <pkg>`).

**Add an app now without reboot:** `adb shell su -c 'am compat enable OVERRIDE_MIN_ASPECT_RATIO <pkg>; am compat enable OVERRIDE_MIN_ASPECT_RATIO_LARGE <pkg>; am force-stop <pkg>'`

**Revert:** uninstall the module in Magisk (restores `force_resizable` off on next boot), or now: `adb shell su -c 'settings delete global force_resizable_activities'`. Undo one app's aspect override: `adb shell su -c 'am compat reset-all <pkg>'`.

**Risks:** a few apps may render oddly when forced resizable (rare, reversible); camera/games excluded; forcing aspect on a fixed-portrait game can crop it — use the exclude list.


## Nees Launcher (minimal text launcher) — DEFAULT (2026-10-31)

Olauncher/Niagara-style launcher (`com.nees.launcher`, source `launcher-app/`, APK `install/NeesLauncher.apk`). Pure dark, true-black, monochrome; typography-led, no app icons. Default home; **Nothing Launcher still installed** — revert via Settings → Apps → Default apps → Home app, or `adb shell su -c 'cmd package set-home-activity com.nothing.launcher/com.android.launcher3.uioverrides.QuickstepLauncher'`.

**Design:** `#000000` everywhere, white primary / grey (`#8A8A8E`) secondary text, no cards, no colour, no elevation. Inter font; big clock with tight tracking. Status-bar/nav insets respected.

**Home (text only):**
- Native monochrome widgets: large CLOCK, date, BATTERY %, NEXT ALARM (each toggleable; tap clock/alarm → Clock app). One minute-aligned ticker, near-zero cost.
- Favourites as a left-aligned plain-text stack. Tap → launch; long-press → Rename (alias) / Remove from home / Hide app / App info.
- Long-press empty → Add widget / Wallpaper / Settings. Double-tap → screen off. Swipe down → notifications. Swipe up → drawer.
- Background defaults to solid true-black; "Show wallpaper" setting flips `FLAG_SHOW_WALLPAPER`.

**App drawer (swipe up):** searchable true-black text list, no icons, alphabetical; type to filter, Enter launches top hit; long-press → Add to home / Hide / App info. Hidden apps are excluded from the drawer and home.

**Settings screen:** toggles (clock/date/battery/alarm, show wallpaper, double-tap lock, swipe-down notifications, glyph-on-open — now default ON), reorder/remove favourites, manage (unhide) hidden apps, add widget.

**Widgets:** native ones above are first-class. Third-party AppWidgetHost widgets are secondary — pick → bind → configure → stacked full-width on home, true-black clipped.

**Perf:** no icon bitmaps anywhere; app list loads off-main-thread. Settled **76 MB PSS** (Graphics 42 MB = fullscreen surface, Java heap 4.4 MB), **0.0 % CPU idle**, 0 crashes. Before (icon grid): 102 MB with icons loaded.

**Known limits:** "Glyph sweep on open" sends `com.nees.glyph.SWEEP` (default on) — needs Glyph Tweaks to handle that action; third-party widget bind may prompt once. No per-page grid (by design — it's a text launcher).


### Nees Launcher update (2026-10-31, later)
- **Clock redesign:** large light-weight Inter numerals, tight negative tracking, tabular figures (no jitter), minute-aligned. Muted-grey date line under it. Two styles in Settings → **Stacked** (big time over date) or **Inline** (time + date on one line).
- **Claude usage widget (root):** reads Termux Claude Code JSONL (`/data/data/com.termux/files/home/.claude/projects/*/*.jsonl`) via `su --mount-master` (needed so the launcher's root shell sees Termux's data in the global mount namespace), extracts `timestamp`+token fields with `awk` from files modified in the last 6h, sums the current **5-hour rolling window**, and shows `<tokens> · resets in Hh Mm` with a thin elapsed bar. Verified live: **4.6M tokens, resets in 2h55m**. Only reads usage/timestamp numbers — never credentials. Shows "—" if Termux/Claude absent or root denied. Toggle in Settings. Refreshes every 60s while home is visible. Needs Magisk root granted to `com.nees.launcher`.
- **Black-wallpaper flash fix:** the grey system wallpaper was peeking at the home window's rounded corners during app-open. When "Show wallpaper" is off (default), the launcher now sets a solid-black system wallpaper (`WallpaperManager.setBitmap`, FLAG_SYSTEM), idempotently (pref-guarded, re-armed when wallpaper is turned on). Verified: wallpaper bitmap brightness **0**; left edge black in **61/62** app-open frames (was a persistent grey strip). `windowBackground` stays opaque black; `FLAG_SHOW_WALLPAPER` not re-added.
- **Pin/unpin (first-class):** drawer long-press → "Pin to home" / "Unpin from home" (wording reflects state); pinned apps show "· pinned" in the drawer. Home long-press → "Unpin from home" + rename/hide/app-info. Reorder pinned apps in Settings (↑/↓) or remove (✕). Persisted in the favourites store.
- **Drawer auto-focus:** opening the drawer (swipe up) focuses the search field and raises the keyboard immediately; keyboard hides on close/launch. Home never steals focus.
- `glyphOnOpen` now defaults ON (Glyph Tweaks has the `com.nees.glyph.SWEEP` receiver).
- Perf: **51 MB PSS, 0.0% CPU idle, 0 crashes.** Root su resolved by absolute path + `--mount-master`.


### Claude widget v2 (2026-10-31, later)
- Scans jsonl modified in the last **7 days** (`find -mtime -7`) via `su --mount-master`, parses once off-main-thread, and shows three figures: the **5-hour window** (`<used> tokens · resets in Hh Mm` + elapsed bar), and a line **`Today <x> · Week <y>`** (today = since local midnight; week = last 7×24h; both = input+output+cache_creation+cache_read).
- Refresh changed to **every 5 minutes** while home is visible, plus an immediate refresh on each home resume (via `LauncherState.resumeTick`). Caches last value; shows "—" if Termux/Claude files or root are unavailable.
- Live numbers read on this device: window **4.6M**, Today **4.6M**, Week **4.6M** (all equal — the phone was set up today, so all usage is within today). Perf: **38.8 MB PSS settled, 0.0% CPU idle, 0 crashes.**
