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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.gschimmel.cryptomako.store.LocalFilesystemObjectStore
import net.gschimmel.cryptomako.vault.NodeKind
import net.gschimmel.cryptomako.vault.VaultException
import net.gschimmel.cryptomako.vault.VaultNode
import net.gschimmel.cryptomako.vault.VaultSession
import java.io.File

private enum class Screen { Settings, Unlock, Browser }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CryptoMakoApp() {
    var screen by remember { mutableStateOf(Screen.Settings) }
    var vaultPath by remember {
        mutableStateOf("/Users/guille/dev/cryptomako/fixtures/vault")
    }
    var passphrase by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var session by remember { mutableStateOf<VaultSession?>(null) }
    var nodes by remember { mutableStateOf<List<VaultNode>>(emptyList()) }
    var recursivePaths by remember { mutableStateOf<List<String>>(emptyList()) }
    var currentDirId by remember { mutableStateOf(VaultSession.ROOT_DIR_ID) }
    var breadcrumb by remember { mutableStateOf(listOf("" to VaultSession.ROOT_DIR_ID)) }
    val scope = rememberCoroutineScope()

    fun lock() {
        session?.close()
        session = null
        nodes = emptyList()
        recursivePaths = emptyList()
        passphrase = ""
        currentDirId = VaultSession.ROOT_DIR_ID
        breadcrumb = listOf("" to VaultSession.ROOT_DIR_ID)
        screen = Screen.Unlock
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("CryptoMako") },
                actions = {
                    if (session != null) {
                        TextButton(onClick = { lock() }) { Text("Lock") }
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
                Screen.Settings -> {
                    Text("Connection / vault path", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "MVP unlocks a local Cryptomator format-8 vault directory. " +
                            "S3 (HTTPS path-style + SigV4) is sketched in :s3 but not wired yet.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedTextField(
                        value = vaultPath,
                        onValueChange = { vaultPath = it },
                        label = { Text("Local vault path") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    Button(
                        onClick = {
                            error = null
                            val dir = File(vaultPath)
                            if (!dir.isDirectory) {
                                error = "Path is not a directory (on-device SAF coming later; use emulator host path or push fixtures)."
                            } else if (!File(dir, "vault.cryptomator").isFile) {
                                error = "vault.cryptomator not found"
                            } else {
                                screen = Screen.Unlock
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Continue") }
                }

                Screen.Unlock -> {
                    Text("Unlock vault", style = MaterialTheme.typography.titleMedium)
                    Text(vaultPath, style = MaterialTheme.typography.bodySmall)
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
                            scope.launch {
                                val result = withContext(Dispatchers.IO) {
                                    runCatching {
                                        val store = LocalFilesystemObjectStore(File(vaultPath))
                                        val s = VaultSession.unlock(store, passphrase)
                                        val root = s.list()
                                        val all = s.listRecursive()
                                        Triple(s, root, all)
                                    }
                                }
                                busy = false
                                result.onSuccess { (s, root, all) ->
                                    session = s
                                    nodes = root
                                    recursivePaths = all
                                    currentDirId = VaultSession.ROOT_DIR_ID
                                    breadcrumb = listOf("" to VaultSession.ROOT_DIR_ID)
                                    passphrase = "" // drop from UI state after unlock
                                    screen = Screen.Browser
                                }.onFailure { e ->
                                    error = when (e) {
                                        is VaultException.UnsupportedFormat ->
                                            "Unsupported format ${e.format} (require 8)"
                                        is VaultException -> "Unlock failed"
                                        else -> "Unlock failed"
                                    }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(if (busy) "Unlocking…" else "Unlock") }
                    TextButton(onClick = { screen = Screen.Settings }) { Text("Back to settings") }
                }

                Screen.Browser -> {
                    Text(
                        "Format ${session?.config?.format} · ${session?.config?.cipherCombo}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        breadcrumb.forEachIndexed { index, (name, id) ->
                            val label = if (index == 0) "/" else name
                            Text(
                                text = label,
                                modifier = Modifier.clickable {
                                    currentDirId = id
                                    breadcrumb = breadcrumb.take(index + 1)
                                    session?.let { nodes = it.list(id) }
                                },
                                color = MaterialTheme.colorScheme.primary,
                            )
                            if (index < breadcrumb.lastIndex) Text("›")
                        }
                    }
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(vertical = 8.dp),
                        modifier = Modifier.weight(1f, fill = true),
                    ) {
                        items(nodes, key = { "${it.kind}:${it.cipherName}:${it.dirId}" }) { node ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = node.kind == NodeKind.DIRECTORY) {
                                        val childId = node.dirId ?: return@clickable
                                        currentDirId = childId
                                        breadcrumb = breadcrumb + (node.cleartextName to childId)
                                        session?.let { nodes = it.list(childId) }
                                    },
                            ) {
                                Column(Modifier.padding(12.dp)) {
                                    Text(
                                        when (node.kind) {
                                            NodeKind.DIRECTORY -> "📁 ${node.cleartextName}/"
                                            NodeKind.SYMLINK -> "🔗 ${node.cleartextName}"
                                            NodeKind.FILE -> "📄 ${node.cleartextName}"
                                        },
                                    )
                                    node.size?.let {
                                        Text("$it bytes", style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                    }
                    Text("Recursive listing", style = MaterialTheme.typography.titleSmall)
                    LazyColumn(
                        modifier = Modifier.weight(0.6f, fill = true),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        items(recursivePaths) { path ->
                            Text(path, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            if (busy) CircularProgressIndicator()
            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
