# KeyPop permanent APK signing — one-time owner setup

KeyPop RC11–RC14 preview APKs used auto-generated GitHub runner debug signing
certificates. Those certificates change between runners, so Android correctly
rejects some updates as `INSTALL_FAILED_UPDATE_INCOMPATIBLE`. Increasing the
version code alone does **not** fix mismatched signatures.

## What is now in place

- A **stable package**: `com.aetherkeys.keyboard.stablev1` (RC15 and all
  later **signed** updates must retain this exact package ID).
- A **persistent signing certificate** loaded only from the private GitHub
  Actions environment `keypop-signing`, never committed to this repository.
- Certificate SHA-256 verification of each signed ABI APK against a separately
  stored, public fingerprint. A changed or missing certificate blocks publishing.
- Normal tests, Android 35 emulator runtime checks, and an installation test
  for the *signed* stable APK, including reinstall/upgrade-in-place.
- Publication only after all build, signature, and installation checks pass.
- Debug builds are **not** sent to the public release as a substitute for
  missing signing secrets.

## One-time setup on the repository owner's Windows PC

Install **JDK 17+** (provides `keytool`) and the **GitHub CLI** (`gh`).
The GitHub CLI must authenticate as a repository administrator.

1. Download `scripts/Setup-KeyPop-Signing.ps1` from this branch.
2. Run PowerShell in its download directory:

   ```powershell
   powershell -ExecutionPolicy Bypass -File .\Setup-KeyPop-Signing.ps1
   ```

3. The script uses a cryptographically random 256-bit password to generate
   **one** private 3072-bit RSA PKCS#12 signing key under
   `Documents\KeyPop-Signing-Backup\`.
4. It creates the GitHub environment `keypop-signing`, uploads the keystore
   and three credentials to **environment secrets**, and sets a public
   fingerprint as an environment variable.
5. It triggers the signed-build workflow on the
   `feature/rc15-permanent-signing-20261107` branch.
6. **Before reformatting or switching PCs**, make a secure offline backup of
   both `keypop-stable-v1.p12` and `keypop-recovery.txt`. Protect the
   recovery text with an encrypted vault or offline locked media. Anyone with
   both can impersonate a KeyPop update. Do not upload either to GitHub files,
   an issue, a chat, or a public release.

The script **never overwrites an existing keystore**. On a repeat run, it
reuses the original keystore and credentials. If either backup is missing,
restore it instead of generating a new key. Rotating keys will break direct
updates to an already installed app.

### Required GitHub environment items

Environment: `keypop-signing`

Secrets:
- `KEYPOP_SIGNING_KEYSTORE_B64`
- `KEYPOP_SIGNING_STORE_PASSWORD`
- `KEYPOP_SIGNING_KEY_ALIAS`
- `KEYPOP_SIGNING_KEY_PASSWORD`

Environment variable (public fingerprint):
- `KEYPOP_SIGNING_CERT_SHA256`

The workflow refuses to sign or publish when these items are absent.
For extra protection, configure GitHub Environment deployment restrictions
and required reviewers for `keypop-signing` if available for your plan.

## Installation and data

The first signed stable APK installs **alongside** RC14. Android cannot
seamlessly update an old APK signed by a different unknown key. Do not
uninstall the old preview until its clipboard history, learned words, settings,
and offline language packs have been backed up or manually transferred.
**App-private data does not automatically migrate across package IDs.**

All future stable releases using the same package ID and certificate and a
**higher versionCode** can be installed directly over the previous signed
stable release without clearing that package's private data.

Avoid ever using debug APKs as release updates. A Google Play production
release may need an additional app-signing strategy, including Play App Signing;
this workflow is for downloadable APKs distributed through GitHub.
