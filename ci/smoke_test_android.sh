#!/usr/bin/env bash
set -euo pipefail
PKG="com.aetherkeys.keyboard.previewv094"
SERVICE="$PKG/com.aetherkeys.keyboard.AetherImeService"
APK="dist/KeyPop-v0.9.4-x86_64-debug.apk"
TEST_APK="dist/KeyPop-v0.9.4-androidTest.apk"
mkdir -p forensic-evidence
function evidence() {
  adb logcat -d -v time > forensic-evidence/logcat.txt || true
  adb exec-out screencap -p > forensic-evidence/emulator-last-screen.png || true
  adb shell dumpsys activity activities > forensic-evidence/activity-state.txt || true
  adb shell dumpsys input_method > forensic-evidence/ime-state.txt || true
}
trap evidence EXIT
test -s "$APK"; test -s "$TEST_APK"
AAPT="$(find "${ANDROID_HOME:-${ANDROID_SDK_ROOT:-/usr/local/lib/android/sdk}}/build-tools" -name aapt -type f | sort -V | tail -1)"
test -n "$AAPT"
"$AAPT" dump badging "$APK" > forensic-evidence/apk-badging.txt
grep -q "previewv094" forensic-evidence/apk-badging.txt
grep -q "KeyPopHomeActivity" forensic-evidence/apk-badging.txt
echo "[1/6] Install the application and Android instrumentation tests"
adb install -r "$APK"
adb install -r "$TEST_APK"
adb logcat -c
echo "[2/6] Assert real Android Activity views independent of system-launcher dialogs"
adb shell am instrument -w -r "$PKG.test/androidx.test.runner.AndroidJUnitRunner" | tee forensic-evidence/instrumentation.txt
grep -E 'OK \([0-9]+ tests?\)' forensic-evidence/instrumentation.txt
if grep -q 'FAILURES!!!' forensic-evidence/instrumentation.txt; then exit 1; fi
echo "[3/6] Cold-start the installed KeyPop launcher"
adb shell am force-stop "$PKG"
adb shell am start -W -n "$PKG/com.aetherkeys.keyboard.KeyPopHomeActivity" | tee forensic-evidence/cold-start.txt
sleep 3
adb shell pidof "$PKG" > forensic-evidence/launcher-pid.txt
test -s forensic-evidence/launcher-pid.txt
echo "[4/6] Confirm the keyboard is registered and selectable"
adb shell ime list -a -s > forensic-evidence/registered-keyboards.txt
grep -Fq "$PKG" forensic-evidence/registered-keyboards.txt
adb shell ime enable "$SERVICE"
adb shell ime set "$SERVICE"
adb shell dumpsys input_method > forensic-evidence/ime-details.txt
grep -Fq "$PKG" forensic-evidence/ime-details.txt
echo "[5/6] Verify installation identity"
adb shell pm path "$PKG" | tee forensic-evidence/installed-package.txt
echo "[6/6] Inspect Android runtime logcat for KeyPop fatal crashes"
adb logcat -d -v time > forensic-evidence/runtime-logcat.txt
if grep -A 20 'FATAL EXCEPTION' forensic-evidence/runtime-logcat.txt | grep -Fq "$PKG"; then
  echo "A KeyPop process crashed" >&2; exit 1
fi
echo "PASS: runtime activity tests, cold launch, IME registration and package"
