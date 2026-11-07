#!/usr/bin/env bash
set -euo pipefail

# Run only AFTER GitHub CI verified the keystore and signing certificate.
# Checks real Android package installation and repeat installation of the
# *same signed package* without uninstalling or clearing its data.

PKG="com.aetherkeys.keyboard.stablev1"
OLD_PKG="com.aetherkeys.keyboard.previewv114"
SERVICE="$PKG/com.aetherkeys.keyboard.AetherImeService"
NEW_APK="dist/KeyPop-v1.0.14-rc15-x86_64-release.apk"
OLD_APK="dist/KeyPop-v1.0.13-rc14-x86_64-debug.apk"

mkdir -p forensic-signed
record() {
  adb logcat -d -v time > forensic-signed/logcat.txt || true
  adb shell dumpsys input_method > forensic-signed/input-method.txt || true
  adb shell pm list packages -f > forensic-signed/packages.txt || true
  adb exec-out screencap -p > forensic-signed/signed-last-screen.png || true
}
trap record EXIT

test -s "$NEW_APK"
echo "[1/6] Obtain and install an existing older preview"
curl -fLsS --retry 3 --connect-timeout 30 \
  "https://github.com/amjadrajamemon/KeyPop/releases/download/v1.0.13-rc14-preview/KeyPop-v1.0.13-rc14-x86_64-debug.apk" \
  -o "$OLD_APK"
test -s "$OLD_APK"
adb install -r "$OLD_APK"
adb shell pm path "$OLD_PKG" | grep -q '^package:'

echo "[2/6] Install stable-signed release without uninstalling old KeyPop"
adb install -r "$NEW_APK"
adb shell pm path "$PKG" | grep -q '^package:'
adb shell pm path "$OLD_PKG" | grep -q '^package:'

echo "[3/6] Launch the signed KeyPop app and verify it remains alive"
adb shell am start -W -n "$PKG/com.aetherkeys.keyboard.KeyPopHomeActivity" \
  | tee forensic-signed/stable-cold-start.txt
sleep 2
adb shell pidof "$PKG" > forensic-signed/launcher.pid
test -s forensic-signed/launcher.pid

echo "[4/6] Confirm IME is exposed and can be selected"
adb shell ime list -a -s | tee forensic-signed/stable-imes.txt
grep -Fq "$PKG" forensic-signed/stable-imes.txt
adb shell ime enable "$SERVICE"
adb shell ime set "$SERVICE"

echo "[5/6] Reinstall the SAME permanently signed APK in-place"
adb install -r "$NEW_APK" | tee forensic-signed/signed-upgrade-result.txt
adb shell pm path "$PKG" | grep -q '^package:'
adb shell pm path "$OLD_PKG" | grep -q '^package:'

echo "[6/6] Check for crashes after signing/install/upgrade"
adb shell am start -W -n "$PKG/com.aetherkeys.keyboard.KeyPopHomeActivity" \
  | tee forensic-signed/stable-after-upgrade.txt
sleep 2
adb logcat -d -v time > forensic-signed/final-logcat.txt
if grep -A 20 'FATAL EXCEPTION' forensic-signed/final-logcat.txt | grep -Fq "$PKG"; then
  echo "::error::The signed KeyPop process crashed after in-place install" >&2
  exit 1
fi
echo "PASS: RC14 and stable KeyPop coexist; stable APK installs and reinstalls with the pinned signature."
