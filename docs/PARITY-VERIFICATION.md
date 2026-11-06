# KeyPop v0.3.0 — Parity Verification Gate

**Status: UNVERIFIED / NOT RELEASE READY**. This branch compiles as an Android APK, but compilation alone is not feature parity.

## Implemented in source (requires Android-device validation)

- Three languages and extended Urdu/Sindhi keys; number, symbols and long-press alternative pages.
- Fifteen designed themes with procedural backdrops, live color and geometry previews.
- Adjustable height/spacing/font/corner radius, one-handed, floating and split layouts.
- Clipboard: encrypted text history (opt-in), search, pin, delete and images imported by user/share action, rich-content paste where receiving app supports it.
- Device-authenticated encrypted login vault with Android Autofill service for exact app/website targets.
- English/Urdu translation with optional on-device model download (ML Kit) and opt-in configurable Sindhi HTTPS provider.
- Dictation through an installed Android speech-recognition service; support varies by language/device.
- Emoji panel, swipe input experiment, local suggestions/learning, optional basic autocorrect, punctuation and capitalisation.
- Browser, editor and app-dependent Send action fallback to insertion when unavailable.

## Currently *not equivalent* to top keyboards

- High-accuracy predictive models, comprehensive language-specific dictionaries, grammar models and multilingual personalization.
- Mature glide-typing word accuracy, gesture editing/selection and swipe support across Urdu/Sindhi.
- Emoji/GIF/sticker search, all system clipboard MIME formats, GIF browsing and sticker packs.
- First-run pronunciation/personal dictionary, custom keyboard layout editor, accessible keyboard-wide remapping.
- Full cross-platform native implementations for iOS, Windows and desktop; this APK targets Android only.
- Production-grade offline Sindhi translation, integrated voice provider independent of installed speech services.
- Verified UI accessibility audit, Play policy review, security penetration testing, crash-free-session testing.
- Scientific repeatable head-to-head benchmarks versus Gboard, SwiftKey, Samsung Keyboard, Fleksy and Typewise.

## Acceptance requirements before a claim of parity

1. Run all automated tests, static analysis and successful GitHub APK compilation.
2. Test on at least three physical Android classes (budget smartphone, flagship, tablet/foldable), including RTL/mixed text.
3. Validate translation and voice on supported language-service devices and document external provider failure cases.
4. Validate exact-origin Autofill and secure-field paste on a matrix of normal and banking apps; never bypass app restrictions.
5. Test clipboard encryption, persistence, imaging, pin/delete actions, and corrupted-store behavior.
6. Record latency p50/p95, sustained input CPU/RAM, frame jank, battery use, crash/ANR and translation precision with competitors on same devices.
7. Track all gaps in issue #2. Do not claim it "beats" competitors without measurable evidence.

**Release policy:** v0.3 is a consolidated development candidate, not a production or parity-certified release.
