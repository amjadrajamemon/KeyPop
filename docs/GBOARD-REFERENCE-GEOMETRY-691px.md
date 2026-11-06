# Gboard geometry reference — user-provided 691 × 1536 image

Measured directly from the supplied alphabetic screenshot at y≈1400, the six visible footer keycaps occupy approximately these x pixel ranges, including antialiasing uncertainty:
- ?123: x=8–101 (94 px)
- Emoji/comma: x=110–169 (60 px)
- Language globe: x=178–237 (60 px)
- Spacebar: x=246–511 (266 px)
- Period: x=520–579 (60 px)
- Enter: about 95 px at right (accent color differs, so the same red-channel threshold cannot isolate its exact edge)

The reference horizontal **proportion** is about 94:60:60:266:60:95. Material preset width weights have been adjusted to approximately 1.57:1:1:4.43:1:1.58. A unit test enforces normalized ratios within 1.2% per key, not visual equality across arbitrary devices.

Reference vertical estimates from the same screenshot:
- Number row visible key body: y≈1048–1105 (58 px)
- First letter row: y≈1120–1185 (66 px)
- Additional alphabet rows and footer follow with approximately 12–18 px gaps
- Number-row to alphabet key-height ratio is ≈0.88, whereas KeyPop previously used (height-10)/height=32/42=0.76. The revised height uses (height-5)/height=37/42=0.88.
- Gboard utility header is proportionately shorter than KeyPop's previous 49dp toolbar; Material toolbar has been reduced to 43dp (buttons to 40dp).

These are geometry targets derived from a resized user screenshot, not a claim of Gboard internal pixel data or copied assets. Font rendering, device density, status/navigation insets and actual APK screenshots require ADB/emulator comparison for further tuning.
