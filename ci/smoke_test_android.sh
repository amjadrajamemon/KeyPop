#!/usr/bin/env bash
set -euo pipefail
PKG="com.aetherkeys.keyboard.previewv093"
SERVICE="$PKG/com.aetherkeys.keyboard.AetherImeService"
APK="dist/KeyPop-v0.9.3-x86_64-debug.apk"
mkdir -p forensic-evidence
function collect_evidence() {
  adb logcat -d -v time > forensic-evidence/logcat.txt || true
  adb exec-out screencap -p > forensic-evidence/last-screen.png || true
  adb shell uiautomator dump /sdcard/keypop-ui.xml >/dev/null 2>&1 || true
  adb exec-out cat /sdcard/keypop-ui.xml > forensic-evidence/ui-hierarchy.xml || true
}
trap collect_evidence EXIT
test -s "$APK"
echo "[1/7] Inspecting APK package and launch activity"
AAPT="$(find "${ANDROID_HOME:-${ANDROID_SDK_ROOT:-/usr/local/lib/android/sdk}}/build-tools" -name aapt -type f | sort -V | tail -1)"
test -n "$AAPT"
"$AAPT" dump badging "$APK" > forensic-evidence/apk-badging.txt
grep -q "previewv093" forensic-evidence/apk-badging.txt
grep -q "KeyPopHomeActivity" forensic-evidence/apk-badging.txt
echo "[2/7] Installing and launching app on real Android framework"
adb install -r "$APK"
adb logcat -c
adb shell am start -W -n "$PKG/com.aetherkeys.keyboard.KeyPopHomeActivity" | tee forensic-evidence/startup.txt
sleep 4
adb shell pidof "$PKG" > forensic-evidence/launcher-pid.txt
test -s forensic-evidence/launcher-pid.txt
echo "[3/7] Asserting keyboard setup controls are visible"
for attempt in 1 2 3 4; do
  adb shell uiautomator dump /sdcard/keypop-home.xml >/dev/null || true
  adb exec-out cat /sdcard/keypop-home.xml > forensic-evidence/home-ui.xml || true
  if grep -q "Keyboard Setup" forensic-evidence/home-ui.xml &&
     grep -q "Enable KeyPop keyboard" forensic-evidence/home-ui.xml &&
     grep -q "Language packs and model status" forensic-evidence/home-ui.xml; then
    break
  fi
  echo "Launcher UI not ready on attempt $attempt; retrying"
  sleep 3
done
if ! grep -q "Language packs and model status" forensic-evidence/home-ui.xml; then
  echo "Launcher UI is missing expected controls; dump follows" >&2
  head -c 3000 forensic-evidence/home-ui.xml >&2
  exit 1
fi
echo "[4/7] Opening language pack manager and checking visibility"
python3 - <<'PY'
import re,subprocess,xml.etree.ElementTree as ET
root=ET.parse("forensic-evidence/home-ui.xml").getroot()
nodes=[x for x in root.iter("node") if "Language packs and model status" in (x.get("text") or "")]
assert nodes,"Missing language pack control on the launcher"
coords=list(map(int,re.findall(r"\d+",nodes[0].get("bounds",""))))
assert len(coords)==4
subprocess.run(["adb","shell","input","tap",str((coords[0]+coords[2])//2),str((coords[1]+coords[3])//2)],check=True)
PY
sleep 3
adb shell uiautomator dump /sdcard/keypop-packs.xml >/dev/null
adb exec-out cat /sdcard/keypop-packs.xml > forensic-evidence/packs-ui.xml
grep -q "Language Pack Manager" forensic-evidence/packs-ui.xml
grep -q "Download / repair Urdu" forensic-evidence/packs-ui.xml
adb shell input swipe 520 1730 520 690 370
sleep 1
adb shell uiautomator dump /sdcard/keypop-packs-bottom.xml >/dev/null
adb exec-out cat /sdcard/keypop-packs-bottom.xml > forensic-evidence/packs-bottom-ui.xml
grep -q "SINDHI" forensic-evidence/packs-bottom-ui.xml
echo "[5/7] Ensuring Android detects the InputMethodService"
adb shell ime list -a -s > forensic-evidence/input-methods.txt
cat forensic-evidence/input-methods.txt
# Include disabled IMEs: immediately after installation they are not enabled by the user.
grep -Fq "$PKG" forensic-evidence/input-methods.txt
adb shell ime enable "$SERVICE"
adb shell ime set "$SERVICE"
echo "[6/7] Focus a real EditText and attempt to show KeyPop IME"
adb shell am start -W -n "$PKG/com.aetherkeys.keyboard.KeyPopHomeActivity" >/dev/null
sleep 2
adb shell uiautomator dump /sdcard/keypop-home2.xml >/dev/null
adb exec-out cat /sdcard/keypop-home2.xml > forensic-evidence/focused-input-ui.xml
python3 - <<'PY'
import re, subprocess, xml.etree.ElementTree as ET
root=ET.parse("forensic-evidence/focused-input-ui.xml").getroot()
nodes=[x for x in root.iter("node") if x.get("class")=="android.widget.EditText"]
assert nodes, "Cannot locate setup test field"
area=nodes[0].get("bounds","")
nums=list(map(int,re.findall(r"\d+",area)))
assert len(nums)==4, ("Bad text field bounds", area)
x,y=(nums[0]+nums[2])//2,(nums[1]+nums[3])//2
subprocess.run(["adb","shell","input","tap",str(x),str(y)],check=True)
PY
sleep 3
adb shell dumpsys input_method > forensic-evidence/ime-state.txt
grep -q "$PKG" forensic-evidence/ime-state.txt
echo "[7/7] Checking for fatal app and keyboard crashes"
adb logcat -d -v time > forensic-evidence/smoke-logcat.txt
if grep -A 20 "FATAL EXCEPTION" forensic-evidence/smoke-logcat.txt | grep -q "$PKG"; then
 echo "Detected a fatal KeyPop crash in logcat" >&2; exit 1
fi
adb shell pidof "$PKG" >/dev/null
echo "PASSED: startup, setup, packs UI, installed IME registration and live EditText focus"
