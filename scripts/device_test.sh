#!/usr/bin/env bash
# Installs the debug build and captures the empty state + sample-document reader.
# Usage: scripts/device_test.sh [adb-path]
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
ADB="${1:-/mnt/nas_active/rea/.toolchain/sdk/platform-tools/adb}"
OUT="$ROOT/artifacts"
PKG=com.refmanager.tablet
ACT="$PKG/.MainActivity"

mkdir -p "$OUT"

echo "== waiting for device =="
"$ADB" wait-for-device
"$ADB" shell getprop ro.product.model
"$ADB" shell getprop ro.build.version.sdk
"$ADB" shell wm size
"$ADB" shell wm density

echo "== install =="
"$ADB" install -r "$ROOT/app/build/outputs/apk/debug/app-debug.apk"

shot() {
  "$ADB" exec-out screencap -p > "$OUT/$1.png"
  echo "screenshot: $OUT/$1.png"
}

echo "== empty state =="
"$ADB" shell am force-stop "$PKG"
"$ADB" logcat -c
"$ADB" shell am start -n "$ACT"
sleep 3
shot 01-empty

echo "== sample document =="
"$ADB" shell am force-stop "$PKG"
"$ADB" shell am start -n "$ACT" --ez sample true
sleep 4
shot 02-reader

"$ADB" shell uiautomator dump /sdcard/ui-reader.xml >/dev/null
"$ADB" pull /sdcard/ui-reader.xml "$OUT/ui-reader.xml" >/dev/null
echo "ui dump: $OUT/ui-reader.xml"

echo "== logs =="
"$ADB" logcat -d -v brief '*:E' > "$OUT/logcat-errors.txt" || true
grep -i "refmanager\|AndroidRuntime" "$OUT/logcat-errors.txt" | tail -40 || true

echo "done"
