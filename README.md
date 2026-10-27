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

