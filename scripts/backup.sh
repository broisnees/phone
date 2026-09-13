#!/bin/bash
# Incremental phone backup: pulls only files missing locally or with a different size.
DEST=~/phone-backup
DIRS=(DCIM Pictures Movies Documents Download Android/media/com.whatsapp/WhatsApp/Media Android/media/org.telegram.messenger)

list=$(mktemp)
for d in "${DIRS[@]}"; do
  adb shell "find '/sdcard/$d' -type f -exec stat -c '%s|%n' {} + 2>/dev/null" | tr -d '\r'
done > "$list"

total=$(wc -l < "$list"); n=0; pulled=0; skipped=0; failed=0
while IFS='|' read -r size path; do
  n=$((n+1))
  rel=${path#/sdcard/}
  local_f="$DEST/$rel"
  if [ -f "$local_f" ] && [ "$(stat -f %z "$local_f")" = "$size" ]; then
    skipped=$((skipped+1)); continue
  fi
  mkdir -p "$(dirname "$local_f")"
  if adb pull -a "$path" "$local_f" </dev/null >/dev/null 2>&1; then
    pulled=$((pulled+1))
  else
    failed=$((failed+1)); echo "FAILED: $path"
  fi
  [ $((n % 100)) -eq 0 ] && echo "progress $n/$total pulled=$pulled skipped=$skipped failed=$failed"
done < "$list"
echo "DONE total=$total pulled=$pulled skipped=$skipped failed=$failed"
rm -f "$list"
