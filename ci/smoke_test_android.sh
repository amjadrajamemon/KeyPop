#!/usr/bin/env bash
set -euo pipefail
PKG="com.aetherkeys.keyboard.previewv136"
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
if ! grep -q "previewv136" forensic-evidence/apk-badging.txt; then
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
# Regression for the reported INSTALL_FAILED_UPDATE_INCOMPATIBLE.
# Install the last released RC21 APK on the *same* emulator first, then
# install RC36 without uninstalling RC21. Both must remain installed.
OLD_APK="dist/KeyPop-v1.0.20-rc21-x86_64-debug.apk"
LEGACY_AVAILABLE=0
echo "[coexistence] Attempt prior preview download (optional external dependency)"
if curl -fLsS --retry 2 --connect-timeout 12 --max-time 40 \
  "https://github.com/amjadrajamemon/KeyPop/releases/download/v1.0.20-rc21-debug-preview/KeyPop-v1.0.20-rc21-x86_64-debug.apk" \
  -o "$OLD_APK" && test -s "$OLD_APK"; then
  adb install -r "$OLD_APK"
  adb shell pm path com.aetherkeys.keyboard.previewv121 | grep -q package:
  LEGACY_AVAILABLE=1
else
  echo "::warning::The legacy RC21 asset could not be fetched. Proceeding with all mandatory RC36 emulator and instrumentation validation."
  rm -f "$OLD_APK"
fi
echo "[installation] Install RC36 and check its package identity"
adb install -r "$APK"
adb shell pm path com.aetherkeys.keyboard.previewv136 | grep -q package:
if [ "$LEGACY_AVAILABLE" = "1" ]; then
  adb shell pm path com.aetherkeys.keyboard.previewv121 | grep -q package:
  echo "PASS: RC21 and RC36 coexist without a signature conflict"
else
  echo "PASS: RC36 installs with a unique package ID. Legacy coexistence was not independently tested in this run."
fi
adb install -r "$TEST_APK"
adb logcat -c
echo "[2/6] Assert Android Activity views and privacy-safe Google Translate handoff intents"
adb shell am instrument -w -r \
  -e class com.aetherkeys.keyboard.KeyPopRuntimeSmokeTest,com.aetherkeys.keyboard.GoogleTranslateHandoffTest,com.aetherkeys.keyboard.KeyPopRC10DictionarySmokeTest,com.aetherkeys.keyboard.KeyPopRC11InstrumentedTest,com.aetherkeys.keyboard.KeyPopRC12InstrumentedTest,com.aetherkeys.keyboard.KeyPopRC17ClipboardBackupInstrumentedTest,com.aetherkeys.keyboard.KeyPopRC18TypingPerformanceInstrumentedTest,com.aetherkeys.keyboard.KeyPopRC19ScreenshotSetupInstrumentedTest,com.aetherkeys.keyboard.KeyPopRC20ClipboardUiInstrumentedTest,com.aetherkeys.keyboard.KeyPopRC21PersonalArchiveInstrumentedTest,com.aetherkeys.keyboard.KeyPopRC22MixMatchInstrumentedTest,com.aetherkeys.keyboard.KeyPopRC23LatencyInstrumentedTest,com.aetherkeys.keyboard.KeyPopRC24GlassInstrumentedTest,com.aetherkeys.keyboard.KeyPopRC26PredictionInstrumentedTest,com.aetherkeys.keyboard.KeyPopRC27ControlGeometryInstrumentedTest,com.aetherkeys.keyboard.KeyPopRC33AdaptiveTypingInstrumentedTest,com.aetherkeys.keyboard.KeyPopRC33QualityMetricsInstrumentedTest,com.aetherkeys.keyboard.KeyPopRC34ResizeAndNavigationInstrumentedTest,com.aetherkeys.keyboard.KeyPopRC35SuggestionDesignInstrumentedTest,com.aetherkeys.keyboard.KeyPopRC35SuggestionStyleInstrumentedTest,com.aetherkeys.keyboard.KeyPopRC36SearchAndOptionsInstrumentedTest \
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

# Prior 50 passing instrumentation tests did not catch a user-visible EMPTY
# prediction strip. Inspect the actual active IME's accessibility hierarchy
# with an ordinary focused EditText on the running Android emulator.
echo "[visual] Focus real text field and verify visible prediction row"
# Force a fresh Activity rather than reusing the launcher started above.
# Otherwise the test-only intent extra might never reach onCreate.
adb shell am force-stop "$PKG"
adb shell am start -W -n "$PKG/com.aetherkeys.keyboard.KeyPopHomeActivity" \
    --ez keypopVisualSmoke true > forensic-evidence/prediction-focus.txt
sleep 3
# Android can silently revert the selected IME to the system Latin keyboard
# when the package providing the active IME was force-stopped. The previous
# screen capture verified Gboard, not KeyPop. Select KeyPop again *after*
# opening the editor and assert it remains selected before inspecting chips.
adb shell ime enable "$SERVICE"
adb shell ime set "$SERVICE"
adb shell dumpsys input_method > forensic-evidence/prediction-selected-ime.txt
if ! grep -Fq "mCurMethodId=$SERVICE" forensic-evidence/prediction-selected-ime.txt; then
    echo "KEYPOP NOT ACTIVE: Android switched to another IME" >&2
    grep 'mCurMethodId=' forensic-evidence/prediction-selected-ime.txt || true
    exit 1
fi
# The accessibility/UIAutomator root intermittently returns "null root node"
# even when the actual KeyPop IME and three suggestion chips are visible in
# the saved screenshot. Inspect the real attached IME view via its debug-only,
# on-device SharedPreferences probe instead; this is stricter and deterministic.
read_live_probe() {
    adb shell run-as "$PKG" cat shared_prefs/keypop_visual_probe.xml 2>/dev/null || true
}
echo "[visual] Verify actual IME view is on-screen with a visible suggestion chip"
probe_ok=0
for attempt in 1 2 3 4 5 6 7 8 9 10; do
    sleep 1
    read_live_probe > forensic-evidence/prediction-idle-probe.xml
    if grep -Fq 'name="active_visible" value="true"' forensic-evidence/prediction-idle-probe.xml &&
       grep -Eq '<int name="visible_chips" value="[1-9]' forensic-evidence/prediction-idle-probe.xml; then
        probe_ok=1
        break
    fi
done
adb exec-out screencap -p > forensic-evidence/prediction-actual-ime.png
if [ "$probe_ok" != 1 ]; then
    echo "REAL KEYPOP IME PREDICTION ROW MISSING (or detached)" >&2
    cat forensic-evidence/prediction-idle-probe.xml >&2 || true
    adb shell dumpsys input_method > forensic-evidence/prediction-ime-state.txt
    grep 'mCurMethodId=' forensic-evidence/prediction-ime-state.txt >&2 || true
    exit 1
fi
echo "[visual] Type known misspelling and verify live suggestion refresh"
adb shell input text improvem
typing_ok=0
for attempt in 1 2 3 4 5 6 7 8 9 10; do
    sleep 1
    read_live_probe > forensic-evidence/prediction-typing-probe.xml
    if grep -Fq 'name="active_visible" value="true"' forensic-evidence/prediction-typing-probe.xml &&
       grep -Fq '<string name="typed">improvem</string>' forensic-evidence/prediction-typing-probe.xml &&
       grep -Eq '<int name="visible_chips" value="[1-9]' forensic-evidence/prediction-typing-probe.xml; then
        typing_ok=1
        break
    fi
done
adb exec-out screencap -p > forensic-evidence/prediction-typing-screen.png
if [ "$typing_ok" != 1 ]; then
    echo "REAL KEYPOP PREDICTIONS FAILED TO UPDATE DURING TYPING" >&2
    cat forensic-evidence/prediction-typing-probe.xml >&2 || true
    exit 1
fi
echo "PASS: real attached KeyPop IME row visible while empty and after typing"
adb shell dumpsys input_method > forensic-evidence/prediction-ime-state.txt
grep -Fq "mCurMethodId=$SERVICE" forensic-evidence/prediction-ime-state.txt

# Reproduce the reported Google search input on a genuine Android EditText.
# It sets WEB_EDIT_TEXT + NO_SUGGESTIONS, which historically hid the strip
# despite the keyboard's Suggestions setting being enabled.
echo "[search] Verify KeyPop predictions remain visible in host NO_SUGGESTIONS fields"
adb shell am force-stop "$PKG"
adb shell am start -W -n "$PKG/com.aetherkeys.keyboard.KeyPopHomeActivity" \
    --ez keypopSearchVisualSmoke true > forensic-evidence/search-field-launch.txt
sleep 3
adb shell ime enable "$SERVICE"
adb shell ime set "$SERVICE"
# Programmatic showSoftInput() can be denied during app launch, and simply
# choosing an IME does not force it to attach. Reproduce the user's real tap
# on the search field after KeyPop is selected using that field's measured
# screen coordinates (not a guessed emulator position).
for attempt in 1 2 3 4 5 6 7 8 9 10; do
    adb shell run-as "$PKG" cat shared_prefs/keypop_visual_test.xml \
        > forensic-evidence/search-field-tap-coordinates.xml 2>/dev/null || true
    if grep -Fq 'name="field_center_x"' forensic-evidence/search-field-tap-coordinates.xml &&
       grep -Fq 'name="field_center_y"' forensic-evidence/search-field-tap-coordinates.xml; then
        break
    fi
    sleep 1
done
coordinates="$(python3 - <<'PY'
import xml.etree.ElementTree as ET
root=ET.parse('forensic-evidence/search-field-tap-coordinates.xml').getroot()
vals={node.attrib.get('name'):node.attrib.get('value')
      for node in root if node.tag=='int'}
x,y=vals.get('field_center_x'),vals.get('field_center_y')
if x is None or y is None:
    raise SystemExit('Search field was not positioned by the debug Activity')
print(x,y)
PY
)"
read -r search_x search_y <<< "$coordinates"
adb shell input tap "$search_x" "$search_y"
sleep 2
search_ok=0
for attempt in 1 2 3 4 5 6 7 8 9 10; do
    sleep 1
    read_live_probe > forensic-evidence/search-field-prediction-probe.xml
    if grep -Fq 'name="active_visible" value="true"' forensic-evidence/search-field-prediction-probe.xml &&
       grep -Fq '<string name="typed">do</string>' forensic-evidence/search-field-prediction-probe.xml &&
       grep -Eq '<int name="visible_chips" value="[1-9]' forensic-evidence/search-field-prediction-probe.xml; then
        search_ok=1
        break
    fi
done
adb exec-out screencap -p > forensic-evidence/search-field-keypop-screenshot.png
if [ "$search_ok" != 1 ]; then
    echo "REGRESSION: KeyPop suggestion strip is hidden in Google-style search editors" >&2
    cat forensic-evidence/search-field-prediction-probe.xml >&2 || true
    exit 1
fi
adb shell dumpsys input_method > forensic-evidence/search-field-ime-state.txt
grep -Fq "mCurMethodId=$SERVICE" forensic-evidence/search-field-ime-state.txt
echo "PASS: KeyPop suggestion strip visibly attached while typing 'How do' in a NO_SUGGESTIONS search editor"

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
