# CryptoMako for Android

Android companion to [CryptoMako](https://github.com/guillebot/cryptomako): unlock a **Cryptomator vault format 8**, browse cleartext names, download cleartext bytes, and upload small cleartext files. Storage backends:

- **Local** filesystem `ObjectStore` (golden fixtures / on-device path)
- **S3** HTTPS **path-style** client with hand-rolled **AWS SigV4** (`:s3`, OkHttp — no AWS SDK)

**License:** AGPL-3.0 (same as the macOS app and `org.cryptomator:cryptolib`).

**Play Store:** not published yet — see `docs/play-listing.md` and `store/` for draft listing + signing notes.

## Modules

| Module | Role |
|--------|------|
| `:vault` | `ObjectStore`, `LocalFilesystemObjectStore`, format-8 unlock via `org.cryptomator:cryptolib`, cleartext list / download / small upload |
| `:s3` | Real HTTPS path-style SigV4 `S3ObjectStore` (get/head/list/put/delete), fail-closed writes |
| `:app` | Compose UI: settings → unlock → browse → download/upload → **SAF Backup Sync** |

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


## Backup Sync (SAF)

Android uses the **Storage Access Framework** (tree URI), not Finder paths.

1. Unlock the vault.
2. **Backup → Pick folder…** (system folder picker).
3. The app takes a **persistable URI permission** and remembers the tree.
4. **Backup now** walks the tree, applies default excludes, encrypts each file, and PUTs into the vault under `Backups/<folderName>/<relativePath>` via `ObjectStore` (fail-closed: remote success only on HTTP 2xx).
5. Optional **periodic** WorkManager job (~12h) — still requires an **unlocked** vault in-process (passphrase is never stored for background unlock).

### Excludes (macOS-aligned names)

JSON property names match macOS `BackupSyncExcludes` (not AppPreferences):

- `directoryNames` — default includes `node_modules`, `.git`, `__pycache__`, …
- `fileNames` — `.DS_Store`, `Thumbs.db`, `desktop.ini`
- `fileExtensions` — `pyc`, `pyo` (no leading dot)

Persisted on-device as `backup_sync_excludes_json` in app prefs. No new Platforms settings schema keys.

### Can / cannot (MVP)

| Can | Cannot (yet) |
|-----|----------------|
| Pick folder via SAF, persist permission | DocumentsProvider / Finder-style mounts |
| Upload cleartext → encrypt → S3/local put | Multipart / streaming huge files (loads into memory) |
| Fail closed on put errors (visible errors) | Background backup while vault locked |
| Default path excludes | Full macOS Sync index / skip-unchanged fingerprints |
| WorkManager one-shot + optional periodic | Guaranteed periodic without unlocked session |

## Release / Play prep

```bash
./gradlew :app:assembleDebug :vault:test :s3:test :app:test
# Signed bundle when keystore.properties or CRYPTOMAKO_* env vars are set:
./gradlew :app:bundleRelease
```

See `keystore.properties.example` and `docs/play-listing.md`. Never commit `keystore.properties` or `.jks` files.

## Security notes

- No AWS SDK; SigV4 is hand-rolled and unit-tested offline.
- Never commit `PASSWORD`, access keys, secret keys, or raw masterkeys.
- Do not log passphrases, JWT contents, Authorization headers, or key material.
- **AGPL-3.0:** network use of this code obligates source disclosure under AGPL.

## Related

- macOS / CLI: https://github.com/guillebot/cryptomako
- Cryptomator cryptolib: https://github.com/cryptomator/cryptolib
