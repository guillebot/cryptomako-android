# Play Store listing draft (not published)

Status: **placeholder** — no Play Console upload in this iteration.

## App identity

| Field | Value |
|-------|--------|
| Package / applicationId | `net.gschimmel.cryptomako` |
| Default language | English (US) |
| App name | CryptoMako |
| Short description (≤80) | Encrypted Cryptomator vault client with S3 and on-device backup |
| Full description | See below |
| Category | Productivity |
| License | AGPL-3.0 (must disclose source; see About in-app) |
| Privacy policy URL | https://guillebot.github.io/cryptomako-android/privacy.html |

## Full description (draft)

CryptoMako unlocks a **Cryptomator format-8** vault on local storage or HTTPS S3 (path-style SigV4), browses cleartext names, downloads and uploads files, and backs up a device folder you pick via Android’s Storage Access Framework into `Backups/…` inside the vault.

- Passphrase is never stored; unlock only while you use the app.
- S3 secret key can be kept in EncryptedSharedPreferences (Android Keystore).
- Remote writes succeed only after HTTP 2xx (fail-closed).
- Free software under **AGPL-3.0**. Source: https://github.com/guillebot/cryptomako-android

**AGPL network-use notice:** If you modify and run this app as a network service, you must offer corresponding source to users under AGPL-3.0.

## Release build / signing

```bash
# Option A: keystore.properties (gitignored) — see keystore.properties.example
cp keystore.properties.example keystore.properties
# edit paths/passwords

# Option B: environment
export CRYPTOMAKO_STORE_FILE=/path/to/upload.jks
export CRYPTOMAKO_STORE_PASSWORD=…
export CRYPTOMAKO_KEY_ALIAS=cryptomako
export CRYPTOMAKO_KEY_PASSWORD=…

./gradlew :app:bundleRelease
```

AAB output: `app/build/outputs/bundle/release/app-release.aab` (signed only when credentials are present).

Release builds enable **R8 minify + shrinkResources**. ProGuard keeps cryptolib ServiceLoader entries; AGPL notice remains in About / this listing.

## What Guillermo still must provide

1. Upload keystore (`.jks` / `.keystore`) + passwords — **never commit**.
2. Verify the published privacy policy URL in the Play Console listing.
3. Play Console developer account, store listing assets (icon 512, feature graphic, screenshots), content rating questionnaire.
4. Decide whether to publish under AGPL with source link prominently in listing (recommended).

## Out of scope this turn

- Play Console login / upload
- DocumentsProvider
- Multipart large-object uploads
