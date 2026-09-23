package net.gschimmel.cryptomako.vault

data class VaultConfig(
    val format: Int,
    val shorteningThreshold: Int,
    val cipherCombo: String,
    val jti: String?,
    val kid: String?,
)

enum class NodeKind { FILE, DIRECTORY, SYMLINK }

data class VaultNode(
    val cleartextName: String,
    val kind: NodeKind,
    val cipherName: String,
    val parentDirId: String,
    val dirId: String? = null,
    val ciphertextKey: String = "",
    val size: Long? = null,
)
