package net.gschimmel.cryptomako.vault

sealed class VaultException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class MissingVaultConfig : VaultException("missing vault.cryptomator")
    class MissingMasterkey : VaultException("missing masterkey.cryptomator")
    class UnlockFailed(cause: Throwable? = null) : VaultException("unlock failed", cause)
    class UnsupportedFormat(val format: Int) : VaultException("unsupported vault format: $format (require 8)")
    class UnsupportedCipherCombo(val combo: String) : VaultException("unsupported cipherCombo: $combo")
    class InvalidJwt : VaultException("invalid vault.cryptomator JWT")
}
