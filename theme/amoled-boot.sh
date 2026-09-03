#!/system/bin/sh
# Magisk service.d script: keeps the AMOLED black overlay installed and stacked above
# SystemUI's dynamic colour overlay. SystemUI regenerates that overlay on theme changes
# *and* on every SystemUI restart, so we check the effective colour rather than a setting.
until [ "$(getprop sys.boot_completed)" = "1" ]; do sleep 2; done
sleep 10
if ! cmd overlay list | grep -q "com.android.shell:amoled"; then
  cmd overlay fabricate --target android --name amoled --file /data/system/amoled.xml
fi
restack() {
  cmd overlay disable com.android.shell:amoled
  cmd overlay enable --user 0 com.android.shell:amoled
}
while true; do
  bg=$(cmd overlay lookup android android:color/system_background_dark 2>/dev/null | tail -n 1)
  if [ "$bg" != "#ff000000" ]; then
    sleep 2   # let SystemUI finish writing its overlay
    restack
  fi
  sleep 5
done
