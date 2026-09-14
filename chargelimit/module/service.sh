#!/system/bin/sh
# Charge limiter: polls once a minute (cheap) and flips the Qualcomm USB charger enable node.
NODE=/sys/class/qcom-battery/usb_charger_en
CAP=/sys/class/power_supply/battery/capacity
CONF=/data/adb/chargelimit.conf
[ -f "$CONF" ] || printf 'PAUSE=80\nRESUME=75\n' > "$CONF"
until [ "$(getprop sys.boot_completed)" = "1" ]; do sleep 5; done
state=1
echo 1 > $NODE
while true; do
  . "$CONF"
  c=$(cat $CAP)
  if [ "$state" = "1" ] && [ "$c" -ge "${PAUSE:-80}" ]; then
    echo 0 > $NODE; state=0
  elif [ "$state" = "0" ] && [ "$c" -le "${RESUME:-75}" ]; then
    echo 1 > $NODE; state=1
  fi
  sleep 60
done
