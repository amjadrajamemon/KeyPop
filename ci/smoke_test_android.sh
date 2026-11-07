#!/usr/bin/env bash
set -euo pipefail
PKG="com.aetherkeys.keyboard.previewv107"
SERVICE="$PKG/com.aetherkeys.keyboard.AetherImeService"
# Derive the actual file names published by this build, not a stale version.
APK="$(find dist -maxdepth 1 -name 'KeyPop-*-x86_64-debug.apk' -type f | head -1)"
TEST_APK="$(find dist -maxdepth 1 -name 'KeyPop-*-androidTest.apk' -type f | head -1)"
mkdir -p forensic-evidence
function evidence() {
  adb logcat -d -v time > forensic-evidence/logcat.txt || true
  adb exec-out screencap -p > forensic-evidence/emulator-last-screen.png || true
  adb shell dumpsys activity activities > forensic-evidence/activity-state.txt || true
  adb shell dumpsys input_method > forensic-evidence/ime-state.txt || true
}
trap evidence EXIT
echo "[0/8] Inspect APK metadata and wait for a responsive Android emulator"
test -s "$APK"; test -s "$TEST_APK"
AAPT="$(find "${ANDROID_HOME:-${ANDROID_SDK_ROOT:-/usr/local/lib/android/sdk}}/build-tools" -name aapt -type f | sort -V | tail -1)"
test -n "$AAPT"
"$AAPT" dump badging "$APK" > forensic-evidence/apk-badging.txt
if ! grep -q "previewv107" forensic-evidence/apk-badging.txt; then
    echo "Unexpected APK package identity; dump:" >&2
    head -30 forensic-evidence/apk-badging.txt >&2
    exit 1
fi
# The real launcher Activity is checked by the instrumentation and cold-start
# steps below. Different aapt versions may format launchable-activity differently.
head -5 forensic-evidence/apk-badging.txt
ready=0
for attempt in $(seq 1 50); do
    if adb get-state 2>/dev/null | grep -qx device &&
       [ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ] &&
       adb shell pm list packages >/dev/null 2>&1; then
        ready=1;break
    fi
    sleep 2
done
if [ "$ready" != "1" ]; then
    echo "Android emulator did not provide a stable ADB session" >&2
    adb devices -l >&2 || true
    exit 1
fi
echo "[1/6] Install the application and Android instrumentation tests"
adb install -r "$APK"
adb install -r "$TEST_APK"
adb logcat -c
echo "[2/6] Assert Android Activity views and privacy-safe Google Translate handoff intents"
adb shell am instrument -w -r \
  -e class com.aetherkeys.keyboard.KeyPopRuntimeSmokeTest,com.aetherkeys.keyboard.GoogleTranslateHandoffTest \
  "$PKG.test/androidx.test.runner.AndroidJUnitRunner" \
  | tee forensic-evidence/instrumentation.txt
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
echo "[7/8] Install and verify real offline Sindhi weights on Android"
adb shell svc wifi enable || true
adb shell am instrument -w -r \
  -e class com.aetherkeys.keyboard.OfflineSindhiModelInstallTest \
  "$PKG.test/androidx.test.runner.AndroidJUnitRunner" \
  | tee forensic-evidence/offline-model-install.txt
grep -E 'OK \([0-9]+ tests?\)' forensic-evidence/offline-model-install.txt
echo "[8/10] Install both English and Urdu ML Kit offline models"
adb shell am instrument -w -r \
  -e class com.aetherkeys.keyboard.OfflineEnglishUrduModelInstallTest \
  "$PKG.test/androidx.test.runner.AndroidJUnitRunner" \
  | tee forensic-evidence/english-urdu-model-install.txt
grep -E 'OK \([0-9]+ tests?\)' forensic-evidence/english-urdu-model-install.txt
echo "[9/10] Verify actual Sindhi translation with remote provider disabled and no connectivity"
# A true finished release must download model packs through the app first. This
# test deliberately fails for 0.9.4: downloading ONNX files without decoding
# does not produce Sindhi translations.
adb shell svc wifi disable || true
adb shell svc data disable || true
adb shell am instrument -w -r \
  -e class com.aetherkeys.keyboard.OfflineSindhiAcceptanceTest \
  "$PKG.test/androidx.test.runner.AndroidJUnitRunner" \
  | tee forensic-evidence/offline-sindhi-acceptance.txt
grep -E 'OK \([0-9]+ tests?\)' forensic-evidence/offline-sindhi-acceptance.txt
if grep -q 'FAILURES!!!' forensic-evidence/offline-sindhi-acceptance.txt; then
    echo "OFFLINE SINDHI NOT READY: mandatory acceptance tests failed" >&2
    exit 1
fi
echo "[10/10] Verify English ↔ Urdu translation in both directions with network disabled"
adb shell am instrument -w -r \
  -e class com.aetherkeys.keyboard.OfflineEnglishUrduAcceptanceTest \
  "$PKG.test/androidx.test.runner.AndroidJUnitRunner" \
  | tee forensic-evidence/offline-english-urdu-acceptance.txt
grep -E 'OK \([0-9]+ tests?\)' forensic-evidence/offline-english-urdu-acceptance.txt
if grep -q 'FAILURES!!!' forensic-evidence/offline-english-urdu-acceptance.txt; then
  echo "OFFLINE ENGLISH ⇄ URDU FAILED; refusing to publish" >&2
  exit 1
fi
echo "PASS: offline Sindhi and bidirectional English ↔ Urdu acceptance checks"
