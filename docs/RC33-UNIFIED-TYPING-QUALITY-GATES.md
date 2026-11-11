# KeyPop RC33 — Unified Typing Intelligence: one coordinated upgrade

RC33 is a debug candidate; it is not a signed stable release or a claim of Gboard parity.
Debug application ID: com.aetherkeys.keyboard.previewv133.

## Completed engineering changes

1. Centralized prediction/learning restrictions for passwords, URI, email and NO_SUGGESTIONS fields; corrected divergent learning-state UI defaults.
2. Shared NFC Unicode tokenization preserving contractions and Urdu/Sindhi combining marks.
3. Approximate English word retrieval augmented with an indexed deletion search of frequent SCOWL forms, and a safer autocorrection confidence threshold.
4. Unified ranking of curated, dictionary and encrypted personal word candidates, with grammatical context and user undo penalties.
5. Encrypted on-device phrase learning expanded to up to three prior words; keyed accepted/rejected correction records, plus a single user-initiated complete erase.
6. Expanded native Urdu and Sindhi suggestion vocabulary, offline trilingual phrase backoff and correct native-language learning after Roman transcription.
7. More high-confidence agreement and tense fixes, plus suggestion-only ambiguous articles and repeated-word fixes.
8. Indexed glide lookup for familiar English words, including repeated letters, without a full dictionary scan on the key UI thread.
9. Regression and emulator tests for Unicode, privacy, ranking, acceptance/undo feedback, grammar, trilingual candidates, glide and curated top-3/top-8 quality.

## Debug release gate

- Gradle unit tests, lint and both debug APK and instrumentation APK compilation must pass.
- Android 35 emulator must install RC33, run all designated instrumentation suites and remain crash-free.
- Offline Sindhi and English ↔ Urdu network-disabled acceptance must pass.
- The GitHub Release must contain the exact uploaded ARM64 and Universal files; no links before this gate.

## Unresolved gaps compared with Gboard

- No validated externally trained neural typing or sentence-level grammar model. ONNX Runtime support alone is not such a model.
- No independent head-to-head held-out comparison with Gboard on the same Redmi Note 13.
- No native-speaker-validated large-scale Urdu/Sindhi frequency corpora.
- No probabilistic touch-coordinate error model or competitive multilingual glide decoder.
- No verified Gboard-equivalent p50/p95 touch-to-commit latency on physical hardware.
- No independent privacy penetration testing or full production-signing review.

## Mandatory fair benchmark protocol

Compare KeyPop and Gboard on one phone, same input fields and locale settings, with consented native-speaker-reviewed English, Urdu, Sindhi, mixed-script and Roman samples. Report top-1/top-3 correction recall, harmful autocorrections per 1,000 words, undo rates, next-word recall, keystrokes saved, personal adaptation after days of use, p50/p95/p99 latency, PSS/CPU, dropped frames and battery. Preserve raw de-identified results. Green CI is not an accuracy benchmark.

No private messages, keyboard content or secret text may be uploaded as training or benchmark data without explicit user permission.