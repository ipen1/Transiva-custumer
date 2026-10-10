#!/usr/bin/env bash
# Run on a computer with adb and one connected device; captures no logcat or personal payloads.
set -euo pipefail
OUT="${1:-device-performance}"
mkdir -p "$OUT"
adb get-state > /dev/null
adb shell getprop ro.product.model > "$OUT/model.txt"
adb shell getprop ro.build.version.sdk > "$OUT/android-sdk.txt"
printf 'run,total_time_ms,wait_time_ms\n' > "$OUT/startup.csv"
for run in $(seq 1 10); do
  adb shell am force-stop com.transiva.customer
  result=$(adb shell am start -W -n com.transiva.customer/com.transiva.app.SplashActivity)
  printf '%s\n' "$result" > "$OUT/startup-$run.txt"
  if ! printf '%s\n' "$result" | tr -d '\r' | grep -q '^Status: ok'; then
    printf 'Startup failed; see saved startup report.\n' >&2
    exit 1
  fi
  total=$(printf '%s\n' "$result" | awk '/^TotalTime:/ {print $2}' | tr -d '\r')
  wait=$(printf '%s\n' "$result" | awk '/^WaitTime:/ {print $2}' | tr -d '\r')
  printf '%s,%s,%s\n' "$run" "$total" "$wait" >> "$OUT/startup.csv"
  sleep 2
  adb shell dumpsys meminfo com.transiva.customer > "$OUT/memory-$run.txt"
done
printf 'Reports saved to %s. These startup timings end at Splash, not dashboard readiness.\n' "$OUT"
