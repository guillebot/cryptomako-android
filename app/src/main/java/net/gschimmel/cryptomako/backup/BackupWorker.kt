package net.gschimmel.cryptomako.backup

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit

/**
 * Runs Backup Sync while a [VaultSession] is held in [VaultSessionHolder].
 * Fail-closed: if the vault is locked, the worker fails with a visible error (never silent success).
 * Hard-fails before any upload when nested/overlapping backup sources are present.
 */
class BackupWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val prefs = BackupPreferences(applicationContext)
        val sources = prefs.loadSources()
        if (sources.isEmpty()) {
            return fail("No backup folder selected (SAF tree URI missing)")
        }
        val session = VaultSessionHolder.session
            ?: return fail("Vault is locked — unlock CryptoMako before backup")

        // Platforms consensus: hard-fail Sync when one resolved URI prefixes another.
        val overlapErr = BackupUriOverlap.overlapErrorOrNull(sources)
        if (overlapErr != null) {
            return fail(overlapErr)
        }

        setForeground(createForegroundInfo("Starting backup…"))

        val engine = BackupEngine(applicationContext, session, prefs.loadExcludes())
        var filesDone = 0
        var bytesDone = 0L
        var filesSkipped = 0

        for ((index, source) in sources.withIndex()) {
            val label = source.displayName.ifBlank { "Device" }
            notify("Backup ${index + 1}/${sources.size}: $label…")
            val treeUri = try {
                Uri.parse(source.safUri)
            } catch (e: Exception) {
                return fail("Invalid SAF URI for source '$label': ${e.message}")
            }
            val result = engine.run(
                treeUri = treeUri,
                folderName = label,
                onProgress = { p ->
                    val phaseLabel = when (p.phase) {
                        BackupProgress.Phase.SCANNING -> "Scanning $label…"
                        BackupProgress.Phase.UPLOADING ->
                            "Uploading $label ${p.filesDone}/${p.filesTotal}"
                        BackupProgress.Phase.FINISHED -> "Finished $label"
                        BackupProgress.Phase.FAILED -> "Failed $label"
                    }
                    notify(phaseLabel)
                },
                isCancelled = { isStopped },
            )
            when (result.phase) {
                BackupProgress.Phase.FINISHED -> {
                    filesDone += result.filesDone
                    bytesDone += result.bytesDone
                    filesSkipped += result.filesSkipped
                }
                else -> return fail(result.error ?: "Backup failed for $label")
            }
        }

        notify("Uploaded $filesDone files")
        return Result.success(
            workDataOf(
                KEY_FILES to filesDone,
                KEY_BYTES to bytesDone,
                KEY_MESSAGE to "Uploaded $filesDone files ($bytesDone bytes)" +
                    if (filesSkipped > 0) "; skipped $filesSkipped" else "",
            ),
        )
    }

    private fun fail(message: String): Result {
        notify(message)
        return Result.failure(workDataOf(KEY_MESSAGE to message, KEY_ERROR to message))
    }

    private fun notify(text: String) {
        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        ensureChannel(nm)
        nm.notify(
            NOTIFICATION_ID,
            NotificationCompat.Builder(applicationContext, CHANNEL_ID)
                .setContentTitle("CryptoMako Backup")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.stat_sys_upload)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .build(),
        )
    }

    private fun createForegroundInfo(text: String): ForegroundInfo {
        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        ensureChannel(nm)
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle("CryptoMako Backup")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    private fun ensureChannel(nm: NotificationManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Backup Sync", NotificationManager.IMPORTANCE_LOW),
            )
        }
    }

    companion object {
        const val UNIQUE_ONE_SHOT = "cryptomako_backup_now"
        const val UNIQUE_PERIODIC = "cryptomako_backup_periodic"
        const val KEY_MESSAGE = "message"
        const val KEY_ERROR = "error"
        const val KEY_FILES = "files"
        const val KEY_BYTES = "bytes"
        private const val CHANNEL_ID = "cryptomako_backup"
        private const val NOTIFICATION_ID = 42

        fun enqueueNow(context: Context) {
            val req = OneTimeWorkRequestBuilder<BackupWorker>().build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_ONE_SHOT,
                ExistingWorkPolicy.REPLACE,
                req,
            )
        }

        fun setPeriodicEnabled(context: Context, enabled: Boolean) {
            val wm = WorkManager.getInstance(context)
            if (enabled) {
                val req = PeriodicWorkRequestBuilder<BackupWorker>(12, TimeUnit.HOURS).build()
                wm.enqueueUniquePeriodicWork(
                    UNIQUE_PERIODIC,
                    ExistingPeriodicWorkPolicy.UPDATE,
                    req,
                )
            } else {
                wm.cancelUniqueWork(UNIQUE_PERIODIC)
            }
        }

        /**
         * Unique work names cancelled on Lock (High parity with Mac: stop Backup Sync
         * before tearing down the in-process vault session).
         */
        fun lockCancelWorkNames(): List<String> = listOf(UNIQUE_ONE_SHOT, UNIQUE_PERIODIC)

        /** Cancel one-shot and periodic Backup Sync WorkManager jobs. */
        fun cancelAll(context: Context) {
            val wm = WorkManager.getInstance(context)
            for (name in lockCancelWorkNames()) {
                wm.cancelUniqueWork(name)
            }
        }
    }
}
