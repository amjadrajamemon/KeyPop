# KeyPop — 360° forensic audit and v0.9.2 corrective build

## Reported release blockers
1. v0.9.0 and v0.9.1 APKs installed but displayed blank green UI / did not provide a usable first-launch experience.
2. English model downloaded but Urdu failed; Sindhi offline translation pack was missing.
3. Prior GitHub builds reported success without testing actual activity startup or the input method service on Android.

## Verified code-level findings
- v0.9.1 used a very large MainActivity (~35k characters), constructed theme galleries, preferences, secure clipboard and other heavyweight settings on launcher entry, and had previously launched a modal pack setup activity from first run. Even after removing the forced jump, this path was never runtime smoke tested.
- The language manager used a dark green background and only en/ur ML Kit model management, with no in-app Sindhi translation model. Its optional Google Translate link was not integrated with KeyPop.
- Google ML Kit's published language list supports English (en) and Urdu (ur), but **does not include Sindhi (sd)**. Claiming Sindhi ML Kit model download would mislead users.
- Android SpeechRecognizer model downloads are delegated to Android's installed provider; selecting a locale is not evidence a voice model exists.
- The existing CI used Gradle unit tests, lint, and assembleDebug only; it had no Android framework/emulator startup, visible-layout, IME-registration, or crash checks. Five ABI APKs being created did not prove runnability.
- The default branch is behind the recent preview development branch. Merge/release discipline must prevent older code from superseding tested builds.

## v0.9.2 changes
- New minimal **KeyPopHomeActivity** is the default exported launcher and never initializes ML Kit or heavy theme previews before displaying setup controls.
- The large pre-existing MainActivity remains accessible as optional Advanced Settings without taking over first run.
- Language Pack Manager renders visible English, Urdu, and Sindhi status sections first, then performs explicit translation model requests. Wi-Fi-only default remains disabled.
- Adds exact package and launch-activity validation, Android 35 emulator installation, visible on-device UI hierarchy checks, launcher process checks, pack-manager UI assertions, IME detection/activation, EditText focus, crash log checks, and screenshots/logcat artifacts.
- Blocks GitHub v0.9.2 release until both Gradle checks **and** emulator smoke test succeed.
- A new debug package ID avoids confusing installed earlier preview variants when selecting the active keyboard.

## Outstanding — not falsely claimed as fixed
- **Full Sindhi translation**: An inference runtime plus a licensed IndicTrans2-type model, model checksum/distribution, tokenization, on-device memory profiling, and quality validation are not included in this version. The existing 30-phrase offline phrasebook is not a full translator.
- **Urdu ML Kit download** requires real device/network/Google Play services verification; emulator smoke does not download multi-megabyte models.
- Full Gboard UI/typing feature parity, screen-size matrix, TalkBack accessibility, performance, security, battery, and sensitive-field handling remain open.
- A screenshot and logcat from the user's real device are required to diagnose device-specific failures accurately.
