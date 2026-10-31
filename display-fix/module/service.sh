#!/system/bin/sh
# nees_fullscreen — run late, after boot completes.
until [ "$(getprop sys.boot_completed)" = "1" ]; do sleep 3; done
sleep 8

DIR=/data/adb/fullscreen
mkdir -p "$DIR"
# Seed config files on first run.
[ -f "$DIR/exclude.txt" ] || cat > "$DIR/exclude.txt" <<'X'
# One package per line: these are NEVER forced full-screen (viewfinders/games distort).
com.nothing.camera
org.codeaurora.snapcam
X
[ -f "$DIR/apps.txt" ] || cat > "$DIR/apps.txt" <<'X'
# Optional: one package per line to force taller via the aspect-ratio compat override,
# for stubborn apps that still show black bars after the global switch below.
# Example:
# com.whatsapp
X

# (A) Global: make activities resizable so they fill the 20:9 display instead of letterboxing.
settings put global force_resizable_activities 1

# (A, per-app) Aspect-ratio override for listed apps, skipping excluded ones.
grep -vE '^\s*#|^\s*$' "$DIR/apps.txt" 2>/dev/null | while read -r pkg; do
  grep -qxF "$pkg" "$DIR/exclude.txt" && continue
  am compat enable OVERRIDE_MIN_ASPECT_RATIO "$pkg" >/dev/null 2>&1
  am compat enable OVERRIDE_MIN_ASPECT_RATIO_LARGE "$pkg" >/dev/null 2>&1
done
