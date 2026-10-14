#!/usr/bin/env bash
# Non-destructive snapshots on one USB-connected tester device.
set -euo pipefail
OUT="${1:-device-session}"
PHASE="${2:-dashboard}"
case "$PHASE" in dashboard|chat|chat_retry|maps|call_foreground|call_home|call_chatroom|after_30min) ;; *) echo 'Unknown phase' >&2; exit 1;; esac
mkdir -p "$OUT"
adb get-state >/dev/null
adb shell getprop ro.product.model > "$OUT/device-model.txt"
adb shell getprop ro.build.version.sdk > "$OUT/android-sdk.txt"
adb shell dumpsys meminfo com.transiva.customer > "$OUT/$PHASE-memory.txt"
adb shell dumpsys gfxinfo com.transiva.customer framestats > "$OUT/$PHASE-frames.txt"
# Exit reason lines only; never collect chat contents, token logs, or trace payloads.
adb shell dumpsys activity exit-info com.transiva.customer 2>/dev/null | awk '/reason=/{print}' > "$OUT/$PHASE-exit-reasons.txt" || true
printf 'Snapshot %s saved. Record network, behavior, crash/ANR and call outcome manually.\n' "$PHASE"
