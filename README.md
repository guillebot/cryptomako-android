# CryptoMako for Android

Android companion to [CryptoMako](https://github.com/guillebot/cryptomako): unlock a **Cryptomator vault format 8**, browse cleartext names, download cleartext bytes, and upload small cleartext files. Storage backends:

- **Local** filesystem `ObjectStore` (golden fixtures / on-device path)
- **S3** HTTPS **path-style** client with hand-rolled **AWS SigV4** (`:s3`, OkHttp — no AWS SDK)

**License:** AGPL-3.0 (same as the macOS app and `org.cryptomator:cryptolib`).

**Play Store:** still TODO (not published).

## Modules

| Module | Role |
|--------|------|
| `:vault` | `ObjectStore`, `LocalFilesystemObjectStore`, format-8 unlock via `org.cryptomator:cryptolib`, cleartext list / download / small upload |
| `:s3` | Real HTTPS path-style SigV4 `S3ObjectStore` (get/head/list/put/delete), fail-closed writes |
| `:app` | Compose UI: local/S3 settings → unlock → browse → download → upload |

`applicationId` / namespace: `net.gschimmel.cryptomako`.

## Requirements

- JDK 17+
- Android SDK (`platforms;android-35` or newer)
- Gradle wrapper included

```bash
export ANDROID_HOME="$HOME/Library/Android/sdk"
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
```

`local.properties` (gitignored) should contain `sdk.dir=…`.

## Build & test

```bash
./gradlew :app:assembleDebug :vault:test :s3:test
```

Debug APK: `app/build/outputs/apk/debug/app-debug.apk`.

## S3 settings (aligned with macOS `VaultSettings`)

Non-secret JSON keys (SharedPreferences):

```json
{
  "storageMode": "local" | "s3",
  "endpoint": "https://s3.example.com",
  "region": "us-east-1",
  "bucket": "my-bucket",
  "prefix": "optional/prefix/",
  "accessKey": "AKIA…",
  "localVaultPath": "/path/to/vault",
  "autoReconnect": false,
  "pathStyle": true
}
```

- **Secret key** and **passphrase** are never stored in that JSON. The app keeps the S3 secret in **EncryptedSharedPreferences** (Android Keystore). Passphrase is prompted at unlock only.
- **HTTPS only** for remote endpoints (`S3Config` rejects non-HTTPS). Localhost HTTP is allowed solely for unit tests (MockWebServer).
- **Path-style URLs:** `https://{host}/{bucket}/{key}` when `pathStyle` is true (default).
- **Fail-closed writes:** `putObject` / `deleteObject` return only after HTTP **2xx** from S3. Local buffering is never treated as durable success. Non-2xx / transport errors throw `ObjectStoreException.Transport` (or `NotFound` for 404 on get).

## Golden fixtures (macOS sibling repo)

| Path | Contents |
|------|----------|
| `/Users/guille/dev/cryptomako/fixtures/vault/` | Ciphertext vault |
| `/Users/guille/dev/cryptomako/fixtures/PASSWORD` | Passphrase (gitignored upstream — **never** commit) |
| `/Users/guille/dev/cryptomako/fixtures/expected-ls.txt` | Expected recursive cleartext listing |

`:vault` `FixtureUnlockTest` unlocks that vault when present; otherwise it **skips**.

## Crypto / unlock

1. Read `vault.cryptomator` (JWT) → reject unless `format == 8`.
2. Load `masterkey.cryptomator` with passphrase (`pepper = []`).
3. Verify JWT HMAC with raw masterkey; fail closed on mismatch.
4. `CryptorProvider` → list / download / upload via filename + content cryptors.

Download: ciphertext `getObject` → decrypt with cryptolib channels.  
Upload (small files): encrypt name + content → `putObject` ciphertext (`.c9r` or shortened `.c9s` + `contents.c9r`). Success requires ObjectStore put completion.

## Security notes

- No AWS SDK; SigV4 is hand-rolled and unit-tested offline.
- Never commit `PASSWORD`, access keys, secret keys, or raw masterkeys.
- Do not log passphrases, JWT contents, Authorization headers, or key material.
- **AGPL-3.0:** network use of this code obligates source disclosure under AGPL.

## Related

- macOS / CLI: https://github.com/guillebot/cryptomako
- Cryptomator cryptolib: https://github.com/cryptomator/cryptolib
