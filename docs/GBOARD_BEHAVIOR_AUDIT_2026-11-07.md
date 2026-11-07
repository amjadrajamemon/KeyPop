# Gboard behavioral benchmark — KeyPop RC9 forensic audit (2026-11-07)

## Scope and evidence

This audit compares user-observable Gboard functions and documented Android IME contracts to KeyPop source, not Google's proprietary engine or byte-identical Gboard assets.

Public references:
- [Gboard Help — word suggestions, text correction, undo and dictionary](https://support.google.com/gboard/answer/7068415)
- [Gboard Help — keyboard options](https://support.google.com/gboard/answer/6380730)
- [Android InputConnection contract](https://developer.android.com/reference/android/view/inputmethod/InputConnection)
- [HeliBoard open-source architecture and dictionaries](https://github.com/HeliBorg/HeliBoard)

## Findings and priorities

| Priority | Capability | Gap in KeyPop | RC9 disposition | Verification |
|---|---|---|---|---|
| P0 | Input responsiveness | Prediction scoring and view creation occurred on input thread | Off-main single-thread scoring, 28 ms debounce, generations discard stale result, reuse three chips | Build+emulator; measure p50/p95 on physical devices |
| P0 | Correction confidence | Rare valid words and plurals could be forcibly replaced | Explicit high-confidence typo mappings only; preserve word/work/more/screenshot/screenshots | Unit tests |
| P0 | Backspace undo | Applied correction could not be reversed by immediate backspace | Restore original if suffix/editor/time match; invalidate across fields | Unit tests; physical-app typing |
| P0 | Toggles | Grammar hint could mutate text even with autocorrect disabled | Separate opt-in automatic grammar; safe autocorrect/punctuation handling in ordinary text fields only | Tests and settings instrumentation |
| P0 | Model transfers | Partial ranges, retries and time/size unclear | .part resume, Content-Range verification, bounded retries, connectivity restoration, MB/s and ETA where available | Unit tests; real interruption tests outstanding |
| P1 | Suggestions | Curated dictionary small; contextual ranking weak | Typo transpositions and fuzzy candidates; broader curated dictionary, local learning | Unit tests; representative text corpus and multilingual evaluation still needed |
| P1 | Languages | English/Urdu/Sindhi suggestion data sparse | Plan high-frequency word frequency packs and language-specific normalizers; don't conflate translation model bundles with typing dictionaries | Future language quality evaluation |
| P1 | Gboard geometry | Comma key, shift/backspace target widths, middle row offset differed | Dedicated comma and emoji keys, numbered row once, wider modifiers, staggered home row | Geometry unit tests and visual device screenshots |
| P1 | Key and suggestion typography | Separate sizes unavailable | Independent key font, suggestion font and suggestion height sliders | Settings instrumentation |
| P1 | Clipboard image offer | Recent screenshot not surfaced in active toolbar | Clipboard image offer and optional recent gallery screenshot after consent; image commit/share fallback | Emulator UI; physical-device and app-compatibility tests outstanding |
| P2 | Personal dictionary controls | No rejected-suggestion ranking and sparse usage counts | Local learned dictionary present; needs accepted/rejected frequency and import/export | Future |
| P2 | Gesture and multilingual decoding | Simplistic path/decode vs modern keyboard models | Needs language-specific gesture decoder and language probabilities | Future |
| P2 | Smart editing | Missing robust autocapitalization, abbreviations, sentence boundaries | Partial double-space; needs locale and app-specific rules | Future |
| P2 | Accessibility | Screen reader, long press, key geometry differences remain | Needs TalkBack and switch-access verification | Future |
| P2 | Battery/memory | Animated themes and image thumbnail decoding can increase cost | Debounced predictions and screenshot lookups outside typing path | Perf instrumentation outstanding |

## Correctness principles

1. **Aggressive candidate generation, conservative automatic replacement.**
   A valid word or an unrecognized proper noun must not be forcibly altered simply because it is one edit from another candidate.
2. **A prediction is not a correction.** Autocorrect OFF must never mean word suggestions OFF.
3. **Undo is a user right.** Backspace should reverse the most recent automatic substitution when safe.
4. **Secure fields are special.** No learning, clipboard display, correction or punctuation in passwords, email addresses, URLs or fields asking for no suggestions.
5. **Gboard is a behavioral target, not open source.** Don't claim pixel-perfect, byte-equivalent, equivalent neural models or full functionality merely because a debug APK compiles.
6. **Be precise about download progress.** Google ML Kit does not expose exact transfer byte counts. Sindhi model totals shown are estimates. Downloading the files is not proof of correct general-purpose Sindhi translation.

## Final compliance-report classification — strict evidence policy

The final report must **not** quietly put a feasible engineering defect into an
"impossible" exception list. Use these distinct categories:

1. **PASS — independently verified:** claim backed by automated test logs or
   repeatable physical-device measurements. Report the actual test procedure,
   device and result.
2. **OPEN — must fix:** any technically addressable deficiency, including bugs,
   incorrect predictions, jitter, clipping, visual mismatch, downloads, recovery,
   performance or settings behavior. Assign a reproduction case, code change and
   retest; do not relabel as an impossibility.
3. **NOT YET VERIFIED:** code is present but quality or parity is unmeasured,
   including end-to-end typing latency, physical Gboard screenshot geometry,
   interrupted multi-gigabyte transfers and offline Sindhi linguistic quality.
   This is not a PASS, nor an "impossible" exception.
4. **EXTERNAL / INHERENT LIMITATION:** only for genuinely unreachable objectives
   with an explanation and evidence. Examples may include access to Gboard's
   private proprietary prediction model/training data, byte-identical copying
   of proprietary assets, or Android OS APIs which do not expose precise ML Kit
   download byte progress. These do **not** rule out building an independent
   competing implementation.
5. **COMPARATIVE SUPERIORITY:** "beats Gboard" is a *separate measured claim*,
   requiring a controlled side-by-side benchmark and comparative dataset; a
   green CI run alone is insufficient.

**Decision rule:** list feasible deficiencies as **OPEN**, never **EXTERNAL**.
Publish a claim of *all-aspect superiority* only after every applicable
criterion has comparable evidence; otherwise state precisely which criteria
are satisfied and which are still being validated. No unsupported "100%"
or "nothing left" sign-off.

## Release gates

- Android unit test suite; lint; APK compilation
- Emulator UI smoke tests: home/settings/pack selection/functional toggles
- Android 15 physical-device verification: rapid typing, screenshots, autocorrect undo, key geometry, translation mode
- Connection interrupted mid-file and server returning malformed HTTP Content-Range; confirm no corrupted final files
- English, Urdu, Sindhi text corpus checks including transliteration and mixed scripts; avoid misleading model-quality claims
- APK links **only** after public GitHub Release assets are present and validated

## Suggested next-phase benchmarks (targets, not measured claims)

- Rapid keystroke input latency: measure median, p95 and worst-case spikes across supported devices; no dictionary work on the input thread.
- Suggestions: known typo top-1/top-3 accuracy, false-correction rate on valid words and morphology, next-word top-3 recall, language-aware evaluation on English, Urdu, Sindhi.
- Resource envelope: measure heap, GC pauses and dropped frames while keyboard opens, typing, translation preview refresh and screenshot proposal.
- Layout fidelity: screenshot comparison against user's original Gboard reference at same device dimensions/font scale/theme, after masking non-keyboard system decorations.
