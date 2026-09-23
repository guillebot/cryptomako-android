# CryptoMako for Android

Android companion to [CryptoMako](https://github.com/guillebot/cryptomako): unlock a **Cryptomator vault format 8** and list cleartext names. This MVP prioritizes a **local filesystem** `ObjectStore` so golden fixtures pass without live S3. An `ObjectStore` interface + `:s3` sketch leave room for HTTPS path-style SigV4 later.

**License:** AGPL-3.0 (same as the macOS app and `org.cryptomator:cryptolib`).

## Modules

| Module | Role |
|--------|------|
| `:vault` | `ObjectStore`, `LocalFilesystemObjectStore`, format-8 unlock via `org.cryptomator:cryptolib`, cleartext list (root + recursive) |
| `:s3` | Draft `S3Config` + fail-closed `S3ObjectStore` stub (OkHttp; SigV4 not implemented yet) |
| `:app` | Kotlin + Jetpack Compose UI: settings (local path) → unlock → browse / recursive list |

`applicationId` / namespace: `net.gschimmel.cryptomako`.

## Requirements

- JDK 17+
- Android SDK (`platforms;android-35` or newer)
- Gradle 8.14.x (wrapper included)

```bash
export ANDROID_HOME="$HOME/Library/Android/sdk"   # or your SDK path
```

`local.properties` (gitignored) should contain:

```properties
sdk.dir=/Users/guille/Library/Android/sdk
```

## Build

```bash
./gradlew :app:assembleDebug
./gradlew :vault:test
```

Debug APK: `app/build/outputs/apk/debug/app-debug.apk`.

## Golden fixtures (macOS sibling repo)

Read-only reference vault (do **not** commit the password):

| Path | Contents |
|------|----------|
| `/Users/guille/dev/cryptomako/fixtures/vault/` | Ciphertext (`vault.cryptomator`, `masterkey.cryptomator`, `d/…`) |
| `/Users/guille/dev/cryptomako/fixtures/PASSWORD` | Passphrase only (gitignored in the macOS repo; **never** commit or put in README) |
| `/Users/guille/dev/cryptomako/fixtures/expected-ls.txt` | Expected recursive cleartext listing |

`:vault` unit test `FixtureUnlockTest` unlocks that vault when the path exists and asserts the listing matches `expected-ls.txt` (includes `hello.txt`, `notes/`, Unicode + shortened names, etc.). If fixtures are missing, the test **skips**.

Point the app’s settings screen at `…/fixtures/vault` on an emulator with host filesystem access, or copy the ciphertext directory onto the device. Paste the passphrase from `PASSWORD` only into the unlock field (not into logs or VCS).

## Crypto / unlock path

1. Read `vault.cryptomator` (JWT) → decode unverified → **reject unless `format == 8`**.
2. Load `masterkey.cryptomator` with `MasterkeyFileAccess` + passphrase (`pepper = []`).
3. Verify JWT HMAC with the 64-byte raw masterkey; fail closed on mismatch.
4. `CryptorProvider.forScheme(cipherCombo)` (`SIV_GCM` / `SIV_CTRMAC`) → list via `FileNameCryptor`.

Other vault formats are rejected. Wrong password and corrupt vault both surface as unlock failed (do not distinguish in logs).

Dependency: Maven `org.cryptomator:cryptolib` **2.2.2** (AGPL-compatible, format 8 / `SIV_GCM`).

## Security notes

- **HTTPS-only for S3:** `S3Config` requires an `https://` endpoint. Cleartext HTTP is rejected.
- **Fail-closed:** `:s3` never reports a successful remote write; `putObject` / `deleteObject` throw until a real SigV4 client exists.
- **No fake unlock:** JWT must verify; format must be 8.
- **Secrets:** never commit `PASSWORD`, access keys, or raw masterkeys. Do not log passphrases, JWT contents, or key material.
- **AGPL-3.0:** shipping a network service that uses this code obligates source disclosure under AGPL.

## DRAFT shared settings shape (not wired)

Proposed JSON for a future shared / imported connection file (keys are **draft** — not yet aligned with macOS `poc.json`):

```json
{
  "storageMode": "local" | "s3",
  "localPath": "/path/to/vault",
  "endpoint": "https://s3.example.com",
  "region": "us-east-1",
  "bucket": "my-bucket",
  "prefix": "optional/prefix/",
  "accessKey": "AKIA…",
  "secretKeyRef": "keystore-alias-or-env"
}
```

Passphrase stays out of this file (prompt / Keystore). Until documented elsewhere, treat the above as a proposal only.

## Related

- macOS / CLI: https://github.com/guillebot/cryptomako
- Cryptomator cryptolib: https://github.com/cryptomator/cryptolib
