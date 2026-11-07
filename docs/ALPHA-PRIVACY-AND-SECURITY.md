# KeyPop Alpha — Privacy and Security Notes

This is a testing project, not a security-certified password manager.

- **Input method:** Like every Android keyboard, KeyPop receives the text entered into the currently focused editor. A keyboard should never collect or transmit this text without an explicit user action.
- **Clipboard:** Persistent text-history capture is off by default. When enabled, it stores encrypted clips locally while accessible to the active keyboard; secure fields and clipboard items labeled sensitive are excluded where Android identifies them. Users can pin/delete saved clips. Modern Android versions limit clipboard access.
- **Images:** Imported images/GIFs are encrypted in private app storage. Sharing decrypts a temporary cache copy and grants access to a receiving app; exported cache copies are purged when the media store is next used after a grace period.
- **Translation:** Supported languages can use downloaded ML Kit on-device models. Unsupported-language text can be sent to a user-configured HTTPS translation service **only when remote translation is enabled and requested**. Downloading models may involve Google services.
- **Voice:** Android's installed speech-recognition provider may process audio under that provider's terms. Language support varies.
- **Writing tools:** The v0.6 rewriting and quick replies are local rules, not a cloud AI service.
- **Credentials:** The experimental vault encrypts locally with Android Keystore and requires device authentication for release through Android Autofill. Do not store real banking credentials in alpha APKs until independent security testing.
- **Diagnostic timings:** Off by default. When enabled, only keyboard view-construction times are stored locally; the diagnostic view does not record typed content or app identity.
- **Backups:** Design-profile JSON exports deliberately exclude clipboard data, credentials and learned words.

A public store release requires a complete privacy policy, Data Safety disclosures, permissions review, vendor terms review and a device security audit.
