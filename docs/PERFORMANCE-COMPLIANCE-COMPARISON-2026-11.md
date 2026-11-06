# KeyPop: performance, compliance and competitor review

Reviewed: 6 November 2026 | Scope: KeyPop 0.2.0 Android alpha, PR #1

## Executive verdict
KeyPop is **not yet demonstrably faster or better** than Gboard, SwiftKey, Samsung Keyboard, Typewise or Fleksy. A build passing is not a performance benchmark. The current competitive opportunity is a **friendlier live customization studio** plus quality Urdu/Sindhi support, not superiority in mature typing features.

## Current KeyPop alpha
- 15 visually differentiated profiles: 10 original skins, five familiar-style choices (not five independent remappable layouts).
- Live selection and drawing preview; preferences for key height, radius, spacing, label size, number row, key click sound and haptics.
- Three basic writing layouts: English, Urdu and Sindhi; no native-speaker or predictive-text validation yet.
- AES-GCM encrypted text clipboard with pin; no image clipboard or integrated password vault in the GitHub test build.
- Explicit symbol mode and keyboard toolbar. No production-quality autocorrection, swipe-to-type, voice typing, floating or translation engine yet.
- Android-only. iOS, desktop and cross-device sync are unimplemented.

## Feature evidence matrix
| Area | KeyPop 0.2 alpha | Strong competitor evidence |
|---|---|---|
| Theme customization | 15 selectable visual profiles, miniature live preview | SwiftKey free theme gallery; Gboard photo/color themes; Typewise offers additional themes |
| Keyboard size/control | Height, corners, gap, font, number row | SwiftKey offers drag resize/reposition. Samsung Keys Cafe allows editing, key size and remapping |
| Clipboard | Encrypted text history retained until user action, pin | Gboard auto-clears unpinned clips after a short time; it supports pinning |
| Translate while typing | Not in GitHub alpha | Both Gboard and SwiftKey already support translation |
| Prediction/swipe | Not in GitHub alpha | Gboard mature suggestions and glide typing |
| Speech typing | Not in GitHub alpha | Gboard voice input |
| Alternate shape/layout | One-handed-like compact shift only, not true floating | Samsung floating/split; Typewise honeycomb |
| Login Autofill | Not in GitHub alpha | Requires future Android Autofill service and security audit |
| Cross-platform | Android only | SwiftKey supports Android and iOS |

Fleksy is included as a fifth evaluation candidate, not as a source of verified per-feature superiority claims.

## Measured results versus hypotheses
- **Measured debug APK sizes:** KeyPop 0.1.1 = 872,504 bytes (0.83 MiB), KeyPop 0.2.0 = 888,801 bytes (0.85 MiB), a 16,297-byte increase. These are actual compiled APK file measurements, not apples-to-apples competitor performance comparisons.
- **Not measured:** memory/PSS, startup time, keyboard render latency, touch-to-commit p50/p95, battery, typing errors, crashes, device compatibility, and competitor baselines.
- **Do not publish percentage wins, faster/lighter badges or rankings** without comparable device testing.

## Benchmark protocol required for proof
1. Comparable Gboard/SwiftKey/Samsung/Typewise/Fleksy versions; same text editor, phone, power mode and keyboard settings.
2. Three devices: Android 10/11 budget, Android 14 mid-tier, Android 16 flagship.
3. 30 cold IME starts and 100 warm starts; median, p95 and p99 startup to first usable key.
4. 1,000 scripted taps to measure input latency and missed taps; 30-minute typing sessions for memory, frame jank, CPU and battery.
5. Consent-based native English, Urdu, Sindhi test passages for words/minute, text correctness and error-correction effort.
6. Test TalkBack, RTL input, tablet, split screen, secure fields, screenshots, sensitive clipboard flags, orientation and Android 16 KB page-size support.
7. Publish raw CSV, script and app versions to support any future performance claim.

Initial engineering goals, **not results**: p95 touch-to-commit under 35 ms, less than 2% missed taps, 99.9% crash-free beta sessions.

## Compliance review (not a certification)
| Requirement | Review |
|---|---|
| Android 16/API 36 for Google Play new submissions in 2026 | v0.1.1 targets API 35: not submission-ready. v0.2 PR changes target and compile SDK to 36; must verify final build. |
| IME registered with Android | InputMethodService and manifest declared; needs functional input and Android lifecycle tests. |
| User data / privacy policy | No public privacy policy and user-data disclosure verified; required before Play submission. |
| Clipboard handling | Local AES-GCM Android Keystore encrypted text; sensitive flags/password fields are skipped, but storage, deletion and leak tests remain. |
| Password vault / Autofill | Not integrated in GitHub alpha. Implement through sanctioned Autofill APIs with strong reauthentication and exact domain/app scoping. Do not bypass secure apps. |
| Translation / cloud AI | Not integrated; future opt-in and data handling consent needed. |
| Accessibility | No TalkBack/manual high-contrast test results. |
| 16 KB page size | Kotlin-only current module; verify no bundled incompatible native binaries as dependencies evolve. |
| Store readiness | Not ready: requires release signing, privacy link, Play Data safety form, QA, accessibility and policy review. |

## Priority work
P0: robust typing and language layouts, IME/RTL/password/OTP behavior, accessibility and crash testing.
P1: visual key-layout editor, truly different geometries, per-key remapping, user-made themes and floating/split modes.
P2: image-aware clipboard, secure Autofill, optional translation and speech recognition.
P3: on-device Urdu/Sindhi suggestions, transliteration, swipe and verified performance optimization.

## Sources (official; reviewed 2026-11-06)
- Gboard overview: https://support.google.com/gboard/
- Gboard translation: https://support.google.com/gboard/answer/7421372
- Gboard clipboard: https://support.google.com/gboard/answer/10742542
- SwiftKey theme gallery: https://support.microsoft.com/en-us/swiftkey-keyboard/which-themes-are-available-for-my-microsoft-swiftkey-keyboard
- SwiftKey resize: https://support.microsoft.com/en-us/swiftkey-keyboard/how-to-resize-microsoft-swiftkey-keyboard
- SwiftKey Translator: https://support.microsoft.com/en-us/swiftkey-keyboard/how-to-use-microsoft-translator-with-your-microsoft-swiftkey-keyboard
- Samsung Good Lock: https://www.samsung.com/in/support/mobile-devices/explore-good-lock-and-how-its-applications-enhance-your-galaxy-device/
- Samsung Keyboard: https://www.samsung.com/us/support/answer/ANS10002366/
- Typewise Play listing: https://play.google.com/store/apps/details?id=ch.icoaching.typewise
- Android input method: https://developer.android.com/develop/ui/views/touch-and-input/creating-input-method
- Android clipboard security: https://developer.android.com/develop/ui/views/touch-and-input/copy-paste
- Google Play target API: https://support.google.com/googleplay/android-developer/answer/11926878
- Google Play user data: https://support.google.com/googleplay/android-developer/answer/10144311
- Android 16 KB devices: https://developer.android.com/guide/practices/page-sizes

**This report is an evidence-based gap assessment; it does not certify policy compliance or superiority.**
