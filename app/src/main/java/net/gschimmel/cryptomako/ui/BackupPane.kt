package net.gschimmel.cryptomako.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.Observer
import androidx.work.WorkInfo
import androidx.work.WorkManager
import net.gschimmel.cryptomako.backup.BackupPreferences
import net.gschimmel.cryptomako.backup.BackupWorker
import net.gschimmel.cryptomako.backup.VaultSessionHolder
import net.gschimmel.cryptomako.vault.VaultSession

@Composable
fun BackupPane(
    session: VaultSession?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val backupPrefs = remember { BackupPreferences(context) }
    var treeLabel by remember {
        mutableStateOf(
            backupPrefs.treeDisplayName.ifBlank {
                backupPrefs.treeUri?.toString() ?: "(none selected)"
            },
        )
    }
    var periodic by remember { mutableStateOf(backupPrefs.periodicEnabled) }
    var workMessage by remember { mutableStateOf<String?>(null) }
    var workError by remember { mutableStateOf<String?>(null) }
    var workRunning by remember { mutableStateOf(false) }
    var progressFraction by remember { mutableStateOf<Float?>(null) }

    val treePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri: Uri? ->
        if (uri == null) {
            workError = "Folder pick cancelled"
            return@rememberLauncherForActivityResult
        }
        // Backup only reads device files; do not request write URI permission.
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
        try {
            context.contentResolver.takePersistableUriPermission(uri, flags)
        } catch (e: SecurityException) {
            workError = e.message ?: "Could not persist SAF read permission"
            return@rememberLauncherForActivityResult
        }
        val name = DocumentFile.fromTreeUri(context, uri)?.name?.ifBlank { null } ?: "Device"
        backupPrefs.treeUri = uri
        backupPrefs.treeDisplayName = name
        treeLabel = name
        workError = null
        workMessage = "Folder selected: $name (permission persisted)"
    }

    DisposableEffect(Unit) {
        val wm = WorkManager.getInstance(context)
        val live = wm.getWorkInfosForUniqueWorkLiveData(BackupWorker.UNIQUE_ONE_SHOT)
        val observer = Observer<List<WorkInfo>> { infos ->
            val info = infos.firstOrNull() ?: return@Observer
            when (info.state) {
                WorkInfo.State.RUNNING, WorkInfo.State.ENQUEUED -> {
                    workRunning = true
                    workError = null
                    progressFraction = null
                    workMessage = "Backup running…"
                }
                WorkInfo.State.SUCCEEDED -> {
                    workRunning = false
                    progressFraction = 1f
                    workMessage = info.outputData.getString(BackupWorker.KEY_MESSAGE)
                        ?: "Backup finished"
                    workError = null
                }
                WorkInfo.State.FAILED, WorkInfo.State.CANCELLED, WorkInfo.State.BLOCKED -> {
                    workRunning = false
                    progressFraction = null
                    val err = info.outputData.getString(BackupWorker.KEY_ERROR)
                        ?: info.outputData.getString(BackupWorker.KEY_MESSAGE)
                        ?: "Backup failed"
                    workError = err
                    workMessage = null
                }
            }
        }
        live.observeForever(observer)
        onDispose { live.removeObserver(observer) }
    }

    Column(
        modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Backup Sync (SAF)", style = MaterialTheme.typography.titleMedium)
        Text(
            "Pick a device folder via Storage Access Framework. " +
                "Files are encrypted into the unlocked vault under Backups/<folder>/…. " +
                "Remote puts succeed only on HTTP 2xx (fail-closed). " +
                "Requires an unlocked vault; passphrase is never stored for background work.",
            style = MaterialTheme.typography.bodySmall,
        )
        Text("Selected folder: $treeLabel", style = MaterialTheme.typography.bodyMedium)
        Button(
            onClick = { treePicker.launch(null) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Pick folder…") }

        Button(
            enabled = !workRunning && session != null && backupPrefs.treeUri != null,
            onClick = {
                if (session == null) {
                    workError = "Unlock the vault before backup"
                    return@Button
                }
                VaultSessionHolder.set(session)
                workError = null
                workMessage = "Enqueueing backup…"
                BackupWorker.enqueueNow(context)
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                when {
                    session == null -> "Backup now (unlock first)"
                    backupPrefs.treeUri == null -> "Backup now (pick folder first)"
                    workRunning -> "Backup running…"
                    else -> "Backup now"
                },
            )
        }

        FilterChip(
            selected = periodic,
            onClick = {
                periodic = !periodic
                backupPrefs.periodicEnabled = periodic
                BackupWorker.setPeriodicEnabled(context, periodic)
                workMessage = if (periodic) {
                    "Periodic backup enabled (every ~12h; still requires unlocked vault in process)"
                } else {
                    "Periodic backup disabled"
                }
            },
            label = {
                Text(if (periodic) "Periodic: ON (~12h)" else "Periodic: OFF")
            },
        )

        progressFraction?.let { LinearProgressIndicator(progress = { it }, modifier = Modifier.fillMaxWidth()) }
        if (workRunning && progressFraction == null) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        workMessage?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        workError?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        Text(
            "Default excludes match macOS (directoryNames / fileNames / fileExtensions): " +
                "node_modules, .git, .DS_Store, *.pyc, …",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
fun AboutPane(modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("About", style = MaterialTheme.typography.titleMedium)
        Text(
            "CryptoMako for Android — companion to the macOS CryptoMako vault client.\n" +
                "License: GNU Affero General Public License v3.0 (AGPL-3.0).\n" +
                "If you run a modified version of this app as a network service, " +
                "AGPL requires you to offer corresponding source to users.\n" +
                "Source: https://github.com/guillebot/cryptomako-android\n" +
                "Uses org.cryptomator:cryptolib (AGPL).",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
