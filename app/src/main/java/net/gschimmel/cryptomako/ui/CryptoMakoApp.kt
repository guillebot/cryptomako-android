package net.gschimmel.cryptomako.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.gschimmel.cryptomako.s3.S3Config
import net.gschimmel.cryptomako.s3.S3ObjectStore
import net.gschimmel.cryptomako.settings.SettingsStore
import net.gschimmel.cryptomako.settings.VaultSettings
import net.gschimmel.cryptomako.store.LocalFilesystemObjectStore
import net.gschimmel.cryptomako.store.ObjectStore
import net.gschimmel.cryptomako.vault.NodeKind
import net.gschimmel.cryptomako.vault.VaultException
import net.gschimmel.cryptomako.vault.VaultNode
import net.gschimmel.cryptomako.vault.VaultSession
import net.gschimmel.cryptomako.backup.VaultSessionHolder
import java.io.File
import java.nio.charset.StandardCharsets

private enum class Screen { Settings, Unlock, Browser, Backup, About }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CryptoMakoApp() {
    val context = LocalContext.current
    val settingsStore = remember { SettingsStore(context) }
    val initial = remember { settingsStore.load() }
    val initialSecret = remember { String(settingsStore.loadSecretKey()) }

    // If process still holds an unlocked session (e.g. after config change), keep UI in sync
    // so Lock can destroy masterkey; do not orphan key material without a Lock affordance.
    val heldSession = remember { VaultSessionHolder.session }
    var screen by remember {
        mutableStateOf(if (heldSession != null) Screen.Browser else Screen.Settings)
    }
    var storageMode by remember { mutableStateOf(initial.storageMode) }
    var vaultPath by remember {
        mutableStateOf(initial.localVaultPath.ifEmpty { "/Users/guille/dev/cryptomako/fixtures/vault" })
    }
    var endpoint by remember { mutableStateOf(initial.endpoint) }
    var region by remember { mutableStateOf(initial.region.ifEmpty { "us-east-1" }) }
    var bucket by remember { mutableStateOf(initial.bucket) }
    var prefix by remember { mutableStateOf(initial.prefix) }
    var accessKey by remember { mutableStateOf(initial.accessKey) }
    var secretKey by remember { mutableStateOf(initialSecret) }
    var pathStyle by remember { mutableStateOf(initial.pathStyle) }

    var passphrase by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var session by remember { mutableStateOf(heldSession) }
    var nodes by remember { mutableStateOf<List<VaultNode>>(emptyList()) }
    var recursivePaths by remember { mutableStateOf<List<String>>(emptyList()) }
    var currentDirId by remember { mutableStateOf(VaultSession.ROOT_DIR_ID) }
    var breadcrumb by remember { mutableStateOf(listOf("" to VaultSession.ROOT_DIR_ID)) }
    var downloadPreview by remember { mutableStateOf<String?>(null) }
    var uploadName by remember { mutableStateOf("android-upload.txt") }
    var uploadBody by remember { mutableStateOf("hello from CryptoMako Android") }
    val scope = rememberCoroutineScope()

    fun persistSettings() {
        settingsStore.save(
            VaultSettings(
                storageMode = storageMode,
                endpoint = endpoint.trim(),
                region = region.trim().ifEmpty { "us-east-1" },
                bucket = bucket.trim(),
                prefix = prefix.trim(),
                accessKey = accessKey.trim(),
                localVaultPath = vaultPath.trim(),
                autoReconnect = false,
                pathStyle = pathStyle,
            ),
        )
        if (secretKey.isNotEmpty()) {
            settingsStore.saveSecretKey(secretKey.toCharArray())
        }
    }

    fun buildStore(): ObjectStore = when (storageMode) {
        VaultSettings.StorageMode.LOCAL -> LocalFilesystemObjectStore(File(vaultPath.trim()))
        VaultSettings.StorageMode.S3 -> S3ObjectStore(
            S3Config(
                endpoint = endpoint.trim(),
                region = region.trim().ifEmpty { "us-east-1" },
                bucket = bucket.trim(),
                prefix = prefix.trim(),
                accessKey = accessKey.trim(),
                secretKey = secretKey.toCharArray(),
                pathStyle = pathStyle,
            ),
        )
    }

    fun vaultPrefix(): String =
        if (storageMode == VaultSettings.StorageMode.S3) prefix.trim() else ""

    fun lockVault() {
        // Holder closes + destroys cryptor/masterkey; close is idempotent if UI also held it.
        VaultSessionHolder.clear()
        session = null
        nodes = emptyList()
        recursivePaths = emptyList()
        passphrase = ""
        downloadPreview = null
        currentDirId = VaultSession.ROOT_DIR_ID
        breadcrumb = listOf("" to VaultSession.ROOT_DIR_ID)
        screen = Screen.Unlock
        status = null
    }

    LaunchedEffect(heldSession) {
        val s = heldSession ?: return@LaunchedEffect
        val result = withContext(Dispatchers.IO) {
            runCatching { s.list() to s.listRecursive() }
        }
        result.onSuccess { (root, all) ->
            nodes = root
            recursivePaths = all
        }.onFailure {
            VaultSessionHolder.clear()
            session = null
            screen = Screen.Unlock
            error = "Session restore failed"
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("CryptoMako") },
                actions = {
                    if (session != null) {
                        TextButton(onClick = { screen = Screen.Backup }) { Text("Backup") }
                        TextButton(onClick = { screen = Screen.About }) { Text("About") }
                        TextButton(onClick = { lockVault() }) { Text("Lock") }
                    } else if (screen == Screen.Settings || screen == Screen.Unlock) {
                        TextButton(onClick = { screen = Screen.About }) { Text("About") }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (screen) {
                Screen.Settings -> SettingsPane(
                    storageMode = storageMode,
                    onStorageMode = { storageMode = it },
                    vaultPath = vaultPath,
                    onVaultPath = { vaultPath = it },
                    endpoint = endpoint,
                    onEndpoint = { endpoint = it },
                    region = region,
                    onRegion = { region = it },
                    bucket = bucket,
                    onBucket = { bucket = it },
                    prefix = prefix,
                    onPrefix = { prefix = it },
                    accessKey = accessKey,
                    onAccessKey = { accessKey = it },
                    secretKey = secretKey,
                    onSecretKey = { secretKey = it },
                    pathStyle = pathStyle,
                    onPathStyle = { pathStyle = it },
                    onContinue = {
                        error = null
                        status = null
                        try {
                            persistSettings()
                            when (storageMode) {
                                VaultSettings.StorageMode.LOCAL -> {
                                    val dir = File(vaultPath.trim())
                                    if (!dir.isDirectory) {
                                        error = "Path is not a directory"; return@SettingsPane
                                    }
                                    if (!File(dir, "vault.cryptomator").isFile) {
                                        error = "vault.cryptomator not found"; return@SettingsPane
                                    }
                                }
                                VaultSettings.StorageMode.S3 -> {
                                    if (!endpoint.trim().startsWith("https://")) {
                                        error = "Endpoint must be HTTPS"; return@SettingsPane
                                    }
                                    if (bucket.isBlank() || accessKey.isBlank() || secretKey.isBlank()) {
                                        error = "bucket, accessKey, and secretKey are required"
                                        return@SettingsPane
                                    }
                                }
                            }
                            screen = Screen.Unlock
                        } catch (e: Exception) {
                            error = e.message ?: "Settings error"
                        }
                    },
                )

                Screen.Unlock -> {
                    Text("Unlock vault", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (storageMode == VaultSettings.StorageMode.LOCAL) vaultPath
                        else "$endpoint / $bucket / ${prefix.ifEmpty { "(root)" }}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedTextField(
                        value = passphrase,
                        onValueChange = { passphrase = it },
                        label = { Text("Passphrase") },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    Button(
                        enabled = !busy && passphrase.isNotEmpty(),
                        onClick = {
                            busy = true
                            error = null
                            status = null
                            scope.launch {
                                val result = withContext(Dispatchers.IO) {
                                    runCatching {
                                        persistSettings()
                                        val store = buildStore()
                                        val s = VaultSession.unlock(store, passphrase, vaultPrefix())
                                        Triple(s, s.list(), s.listRecursive())
                                    }
                                }
                                busy = false
                                result.onSuccess { (s, root, all) ->
                                    session = s
                                    VaultSessionHolder.set(s)
                                    nodes = root
                                    recursivePaths = all
                                    currentDirId = VaultSession.ROOT_DIR_ID
                                    breadcrumb = listOf("" to VaultSession.ROOT_DIR_ID)
                                    passphrase = ""
                                    screen = Screen.Browser
                                }.onFailure { e ->
                                    error = when (e) {
                                        is VaultException.UnsupportedFormat ->
                                            "Unsupported format ${e.format} (require 8)"
                                        is VaultException -> e.message ?: "Unlock failed"
                                        else -> e.message ?: "Unlock failed"
                                    }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(if (busy) "Unlocking…" else "Unlock") }
                    TextButton(onClick = { screen = Screen.Settings }) { Text("Back to settings") }
                }

                Screen.Browser -> BrowserPane(
                    session = session,
                    nodes = nodes,
                    recursivePaths = recursivePaths,
                    breadcrumb = breadcrumb,
                    downloadPreview = downloadPreview,
                    uploadName = uploadName,
                    uploadBody = uploadBody,
                    busy = busy,
                    onBreadcrumb = { index, id ->
                        currentDirId = id
                        breadcrumb = breadcrumb.take(index + 1)
                        session?.let { nodes = it.list(id) }
                    },
                    onOpenDir = { node ->
                        val childId = node.dirId ?: return@BrowserPane
                        currentDirId = childId
                        breadcrumb = breadcrumb + (node.cleartextName to childId)
                        session?.let { nodes = it.list(childId) }
                    },
                    onDownload = { node ->
                        busy = true
                        error = null
                        status = null
                        downloadPreview = null
                        scope.launch {
                            val result = withContext(Dispatchers.IO) {
                                runCatching { session!!.downloadCleartext(node) }
                            }
                            busy = false
                            result.onSuccess { bytes ->
                                val preview = String(bytes, StandardCharsets.UTF_8).take(512)
                                downloadPreview =
                                    "Downloaded ${node.cleartextName} (${bytes.size} bytes)\n$preview"
                                status = "Download OK: ${node.cleartextName}"
                            }.onFailure { e ->
                                error = e.message ?: "Download failed"
                            }
                        }
                    },
                    onUploadName = { uploadName = it },
                    onUploadBody = { uploadBody = it },
                    onUpload = {
                        busy = true
                        error = null
                        status = null
                        scope.launch {
                            val result = withContext(Dispatchers.IO) {
                                runCatching {
                                    val node = session!!.uploadCleartextFile(
                                        parentDirId = currentDirId,
                                        cleartextName = uploadName.trim(),
                                        contents = uploadBody.toByteArray(StandardCharsets.UTF_8),
                                    )
                                    Triple(node, session!!.list(currentDirId), session!!.listRecursive())
                                }
                            }
                            busy = false
                            result.onSuccess { (node, root, all) ->
                                nodes = root
                                recursivePaths = all
                                status = "Upload OK: ${node.cleartextName} → ${node.ciphertextKey}"
                            }.onFailure { e ->
                                error = e.message ?: "Upload failed"
                            }
                        }
                    },
                )

                Screen.Backup -> {
                    BackupPane(session = session)
                    TextButton(onClick = { screen = Screen.Browser }) { Text("Back to browser") }
                }

                Screen.About -> {
                    AboutPane()
                    TextButton(
                        onClick = {
                            screen = if (session != null) Screen.Browser else Screen.Settings
                        },
                    ) { Text("Back") }
                }
            }

            if (busy) CircularProgressIndicator()
            status?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
}

@Composable
private fun SettingsPane(
    storageMode: VaultSettings.StorageMode,
    onStorageMode: (VaultSettings.StorageMode) -> Unit,
    vaultPath: String,
    onVaultPath: (String) -> Unit,
    endpoint: String,
    onEndpoint: (String) -> Unit,
    region: String,
    onRegion: (String) -> Unit,
    bucket: String,
    onBucket: (String) -> Unit,
    prefix: String,
    onPrefix: (String) -> Unit,
    accessKey: String,
    onAccessKey: (String) -> Unit,
    secretKey: String,
    onSecretKey: (String) -> Unit,
    pathStyle: Boolean,
    onPathStyle: (Boolean) -> Unit,
    onContinue: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Connection", style = MaterialTheme.typography.titleMedium)
        Text(
            "Local filesystem or HTTPS path-style S3 (SigV4). " +
                "Remote writes succeed only after HTTP 2xx (fail-closed).",
            style = MaterialTheme.typography.bodySmall,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = storageMode == VaultSettings.StorageMode.LOCAL,
                onClick = { onStorageMode(VaultSettings.StorageMode.LOCAL) },
                label = { Text("Local") },
            )
            FilterChip(
                selected = storageMode == VaultSettings.StorageMode.S3,
                onClick = { onStorageMode(VaultSettings.StorageMode.S3) },
                label = { Text("S3") },
            )
        }
        if (storageMode == VaultSettings.StorageMode.LOCAL) {
            OutlinedTextField(
                value = vaultPath,
                onValueChange = onVaultPath,
                label = { Text("localVaultPath") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
        } else {
            OutlinedTextField(value = endpoint, onValueChange = onEndpoint, label = { Text("endpoint (https://…)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(value = region, onValueChange = onRegion, label = { Text("region") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(value = bucket, onValueChange = onBucket, label = { Text("bucket") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(value = prefix, onValueChange = onPrefix, label = { Text("prefix") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(value = accessKey, onValueChange = onAccessKey, label = { Text("accessKey") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(
                value = secretKey,
                onValueChange = onSecretKey,
                label = { Text("secretKey (encrypted prefs)") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = pathStyle, onClick = { onPathStyle(true) }, label = { Text("pathStyle") })
                FilterChip(selected = !pathStyle, onClick = { onPathStyle(false) }, label = { Text("virtual-hosted") })
            }
        }
        Button(onClick = onContinue, modifier = Modifier.fillMaxWidth()) { Text("Continue") }
    }
}

@Composable
private fun BrowserPane(
    session: VaultSession?,
    nodes: List<VaultNode>,
    recursivePaths: List<String>,
    breadcrumb: List<Pair<String, String>>,
    downloadPreview: String?,
    uploadName: String,
    uploadBody: String,
    busy: Boolean,
    onBreadcrumb: (Int, String) -> Unit,
    onOpenDir: (VaultNode) -> Unit,
    onDownload: (VaultNode) -> Unit,
    onUploadName: (String) -> Unit,
    onUploadBody: (String) -> Unit,
    onUpload: () -> Unit,
) {
    Text(
        "Format ${session?.config?.format} · ${session?.config?.cipherCombo}",
        style = MaterialTheme.typography.bodySmall,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        breadcrumb.forEachIndexed { index, (name, id) ->
            val label = if (index == 0) "/" else name
            Text(
                text = label,
                modifier = Modifier.clickable { onBreadcrumb(index, id) },
                color = MaterialTheme.colorScheme.primary,
            )
            if (index < breadcrumb.lastIndex) Text("›")
        }
    }
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 8.dp),
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
    ) {
        items(nodes, key = { "${it.kind}:${it.cipherName}:${it.dirId}" }) { node ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        when (node.kind) {
                            NodeKind.DIRECTORY -> onOpenDir(node)
                            NodeKind.FILE -> onDownload(node)
                            NodeKind.SYMLINK -> Unit
                        }
                    },
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text(
                        when (node.kind) {
                            NodeKind.DIRECTORY -> "📁 ${node.cleartextName}/"
                            NodeKind.SYMLINK -> "🔗 ${node.cleartextName}"
                            NodeKind.FILE -> "📄 ${node.cleartextName}  (tap to download)"
                        },
                    )
                    node.size?.let {
                        Text("$it bytes ciphertext", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
    downloadPreview?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
    Text("Upload small cleartext file", style = MaterialTheme.typography.titleSmall)
    OutlinedTextField(value = uploadName, onValueChange = onUploadName, label = { Text("filename") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
    OutlinedTextField(value = uploadBody, onValueChange = onUploadBody, label = { Text("contents") }, modifier = Modifier.fillMaxWidth())
    Button(
        enabled = !busy && session != null && uploadName.isNotBlank(),
        onClick = onUpload,
        modifier = Modifier.fillMaxWidth(),
    ) { Text(if (busy) "Working…" else "Upload") }
    Text("Recursive listing", style = MaterialTheme.typography.titleSmall)
    LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        items(recursivePaths) { path ->
            Text(path, style = MaterialTheme.typography.bodySmall)
        }
    }
}
