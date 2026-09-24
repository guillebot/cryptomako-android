package net.gschimmel.cryptomako.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import net.gschimmel.cryptomako.backup.BackupSource
import net.gschimmel.cryptomako.backup.BackupTransferMode
import net.gschimmel.cryptomako.backup.BackupUriOverlap
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
    var sources by remember { mutableStateOf(backupPrefs.loadSources()) }
    var transferMode by remember { mutableStateOf(backupPrefs.backupTransferMode) }
    var softWarn by remember { mutableStateOf<String?>(null) }
    var periodic by remember { mutableStateOf(backupPrefs.periodicEnabled) }
    var workMessage by remember { mutableStateOf<String?>(null) }
    var workError by remember { mutableStateOf<String?>(null) }
    var workRunning by remember { mutableStateOf(false) }
    var progressFraction by remember { mutableStateOf<Float?>(null) }

    val runVerb = if (transferMode == BackupTransferMode.SYNC) "Sync" else "Backup"

    fun refreshSources() {
        sources = backupPrefs.loadSources()
    }

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
        val result = backupPrefs.addSource(uri.toString(), name)
        refreshSources()
        workError = null
        when {
            result.alreadyListed -> {
                softWarn = null
                workMessage = "Already listed: $name"
            }
            result.softWarn != null -> {
                softWarn = result.softWarn
                workMessage = "Added: $name (nested overlap — $runVerb will refuse until fixed)"
            }
            else -> {
                softWarn = null
                workMessage = "Added: $name (permission persisted)"
            }
        }
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
                    workMessage = "$runVerb running…"
                }
                WorkInfo.State.SUCCEEDED -> {
                    workRunning = false
                    progressFraction = 1f
                    workMessage = info.outputData.getString(BackupWorker.KEY_MESSAGE)
                        ?: "$runVerb finished"
                    workError = null
                }
                WorkInfo.State.FAILED, WorkInfo.State.CANCELLED, WorkInfo.State.BLOCKED -> {
                    workRunning = false
                    progressFraction = null
                    val err = info.outputData.getString(BackupWorker.KEY_ERROR)
                        ?: info.outputData.getString(BackupWorker.KEY_MESSAGE)
                        ?: "$runVerb failed"
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
        Text("Backup", style = MaterialTheme.typography.titleMedium)
        Text(
            "Add device folders via Storage Access Framework. " +
                "Files are encrypted into the unlocked vault under Backups/<folder>/…. " +
                "Nested/overlapping sources: soft-warn on add, hard-fail on $runVerb. " +
                "Never deletes files on the SAF source. " +
                "Remote puts succeed only on HTTP 2xx (fail-closed). " +
                "Requires an unlocked vault; passphrase is never stored for background work.",
            style = MaterialTheme.typography.bodySmall,
        )

        Text("Transfer mode", style = MaterialTheme.typography.titleSmall)
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = transferMode == BackupTransferMode.BACKUP,
                onClick = {
                    if (workRunning) return@SegmentedButton
                    transferMode = BackupTransferMode.BACKUP
                    backupPrefs.backupTransferMode = BackupTransferMode.BACKUP
                },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                enabled = !workRunning,
                label = { Text("Backup") },
            )
            SegmentedButton(
                selected = transferMode == BackupTransferMode.SYNC,
                onClick = {
                    if (workRunning) return@SegmentedButton
                    transferMode = BackupTransferMode.SYNC
                    backupPrefs.backupTransferMode = BackupTransferMode.SYNC
                },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                enabled = !workRunning,
                label = { Text("Sync") },
            )
        }
        Text(
            if (transferMode == BackupTransferMode.BACKUP) {
                "Backup copies and updates into the vault. It never deletes the SAF source, " +
                    "and it does not remove vault files that are missing locally."
            } else {
                "Sync copies and updates, then deletes ciphertext in the vault under each " +
                    "source’s Backups/<folder>/ that is missing from the local tree. " +
                    "It never deletes the SAF source. Prefer Backup unless you intentionally " +
                    "want vault orphans removed."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Text("Backup sources", style = MaterialTheme.typography.titleSmall)
        if (sources.isEmpty()) {
            Text("(none selected)", style = MaterialTheme.typography.bodyMedium)
        } else {
            sources.forEach { src: BackupSource ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        "• ${src.displayName}",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        onClick = {
                            backupPrefs.removeSource(src.id)
                            refreshSources()
                            softWarn = BackupUriOverlap.overlapErrorOrNull(backupPrefs.loadSources())
                                ?.let { "Still overlapping — $runVerb will refuse until fixed." }
                            workMessage = "Removed: ${src.displayName}"
                        },
                    ) { Text("Remove") }
                }
            }
        }

        Button(
            onClick = { treePicker.launch(null) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Add folder…") }

        softWarn?.let {
            Text(it, color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.bodySmall)
        }

        Button(
            enabled = !workRunning && session != null && sources.isNotEmpty(),
            onClick = {
                if (session == null) {
                    workError = "Unlock the vault before $runVerb"
                    return@Button
                }
                // Preflight hard-fail in UI so the user sees the message without waiting on WM.
                val overlap = BackupUriOverlap.overlapErrorOrNull(backupPrefs.loadSources())
                if (overlap != null) {
                    workError = overlap
                    workMessage = null
                    return@Button
                }
                VaultSessionHolder.set(session)
                workError = null
                softWarn = null
                workMessage = "Enqueueing $runVerb…"
                BackupWorker.enqueueNow(context)
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                when {
                    session == null -> "$runVerb now (unlock first)"
                    sources.isEmpty() -> "$runVerb now (add folder first)"
                    workRunning -> "$runVerb running…"
                    else -> "$runVerb now"
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
                    "Periodic $runVerb enabled (every ~12h; still requires unlocked vault in process)"
                } else {
                    "Periodic $runVerb disabled"
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
