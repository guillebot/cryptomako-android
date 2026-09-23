# CryptoMako Privacy Policy

**Effective date: September 23, 2026**

CryptoMako is a free and open-source Android client for Cryptomator format-8 vaults. This policy explains what information the app handles and where it goes.

## Summary

- CryptoMako has no cloud account, backend, advertising, or third-party analytics service.
- The app does not collect or sell personal information.
- Vault contents and settings remain on your device or are sent only to the local storage location or HTTPS S3 bucket that you choose.
- Your vault passphrase is used only during the active unlock session and is never stored by CryptoMako.

## Information CryptoMako handles

### Vault contents and metadata

When you open a vault, CryptoMako processes the vault and its contents on your device so that it can display names and decrypt or encrypt files. When you upload or back up files, CryptoMako writes the resulting Cryptomator ciphertext to the storage destination you selected. SAF Backup Sync places backups under `Backups/…` in the selected vault; the backup files are encrypted before they leave the device.

For a local vault, this information stays on your device and the local storage location you selected. For an S3 vault, encrypted vault objects and the requests needed to access them are sent over HTTPS to the S3-compatible endpoint, bucket, and prefix that you configure. CryptoMako does not send vault contents to a CryptoMako server because no such server exists.

Your S3 endpoint or storage provider may independently process connection metadata, such as an IP address and request timestamps, under its own privacy policy. CryptoMako does not receive or control that provider-side processing.

### Credentials and settings

CryptoMako stores non-secret vault connection settings on your device so you can reconnect, such as the storage mode, endpoint, region, bucket, prefix, and S3 access-key identifier. If you choose to save an S3 secret key, it is stored in Android `EncryptedSharedPreferences`, backed by the Android Keystore. The vault passphrase is not stored.

## What CryptoMako does not collect

CryptoMako does not use third-party analytics, advertising SDKs, or third-party crash-reporting services. It does not create a CryptoMako account, maintain a user profile, or collect contacts, location, browsing history, or usage analytics. It does not sell or share personal information for advertising.

## Permissions and system services

- **Internet:** required only for connecting to the HTTPS S3 endpoint you configure.
- **Storage Access Framework:** when you choose Backup → Pick folder…, Android grants CryptoMako access to that folder. The app uses this user-selected access to read files for backup and may retain the system-provided tree permission so an enabled backup can run later. CryptoMako does not request broad device-storage access.
- **Notifications:** Android may use notifications to report backup progress or errors when you allow notifications.
- **Foreground/data-sync service:** Android WorkManager may use the system foreground service for an active backup. A background backup still requires an unlocked in-process vault session; the passphrase is not stored for background unlocking.

## Security

CryptoMako is designed to use HTTPS for remote S3 endpoints and to write encrypted Cryptomator data. No method of storage or transmission is completely risk-free. Keep your device, Android installation, S3 account, and vault credentials secure, and review the privacy and security practices of your chosen storage provider.

## Children’s privacy

CryptoMako is not directed to children under 13. We do not knowingly collect personal information from children.

## Changes to this policy

We may update this policy when CryptoMako’s behavior or legal requirements change. The effective date at the top will be updated when a new version is published. Continued use of the app after an update means the updated policy applies.

## Contact

For privacy questions or requests, please [open an issue on the CryptoMako Android GitHub repository](https://github.com/guillebot/cryptomako-android/issues). The source code is available at [github.com/guillebot/cryptomako-android](https://github.com/guillebot/cryptomako-android) under the AGPL-3.0 license.
