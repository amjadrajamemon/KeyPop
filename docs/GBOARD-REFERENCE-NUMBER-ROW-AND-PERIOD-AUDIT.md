# KeyPop: screenshot-directed number row and period picker parity audit

Reference photographs (Gboard on the user's phone) show:
- Exactly one dedicated horizontal 0–9 row when numbers are enabled;
- Alphabet letters below with punctuation hints (not a second numeric hint row);
- Staggered letter row alignment, a large spacebar, and a period key at the right;
- Holding the period key presents common symbols and selecting via held drag + release;
- Symbol and numeric modes are separate, not additional duplicated rows.

Code finding before patch: AetherImeService rendered one separate 0–9 row above three alphabet rows, but KeyPopAlternates also mapped qwertyuiop to number digits, causing duplicate number hints or alternate slots especially outside the Material preset.

Fixes: KeyPopRowParity centralizes number row conditions. Where the dedicated number row is present, any digit duplicate in alphabetic long-press options and visible hints is suppressed. KeyPopPeriodPalette gives period 12 symbols in English and localized Arabic punctuation for Urdu/Sindhi; the original touch-handling popup is reused. Material preset footer now uses six controls rather than duplicating comma and '?'.

Gates: unit tests for number-row visibility, alternate-character suppression and period options, plus emulator startup/IME checks. Visual pixel differences are not certified until a screenshot capture is compared under matched device dimensions and font scales.

Known open issues: exact Gboard look, full Sindhi offline machine translation, and real-device ML Kit Urdu model download remain unverified. The GitHub release must remain gated on runtime smoke tests.
