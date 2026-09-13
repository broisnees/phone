#!/bin/bash
# Restore ~/phone-backup to the phone. --sync skips files already present with the same timestamp/size.
B=~/phone-backup
for d in DCIM Pictures Movies Documents Download Android/media/com.whatsapp; do
  [ -d "$B/$d" ] || continue
  echo "== $d"
  adb shell mkdir -p "/sdcard/$(dirname "$d")" </dev/null
  adb push --sync "$B/$d" "/sdcard/$(dirname "$d")/" </dev/null 2>&1 | tail -1
done
# Ask MediaStore to index the restored files so Gallery/Photos show them.
adb shell content call --uri content://media --method scan_volume --arg external_primary >/dev/null 2>&1
echo "DONE"
