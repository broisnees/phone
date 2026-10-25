# Nothing Phone (1) — Root Log & Recovery Kit

Last updated: 2026-10-31

## TODO (start here)

Needs you (quick):
- [ ] **Restart the phone** → applies the Inter system font (`nees_interfont`), activates the bootloop protector (YABP) and moves the charge-limit module into place
- [ ] **Test Instagram DM reel** → open a reel from a DM; Unscroll v2 should close it instantly with no scrolling. If not: `grep Unscroll` in the newest `/data/adb/lspd/log/modules_*.log`, look for `seen <id>` lines, add them to `IG_FEEDS` in `unscroll/.../Hooks.java`
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

