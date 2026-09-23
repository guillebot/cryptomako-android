package net.gschimmel.cryptomako.backup

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
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
 */
class BackupWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val prefs = BackupPreferences(applicationContext)
        val treeUri = prefs.treeUri
            ?: return fail("No backup folder selected (SAF tree URI missing)")
        val folderName = prefs.treeDisplayName.ifBlank { "Device" }
        val session = VaultSessionHolder.session
            ?: return fail("Vault is locked — unlock CryptoMako before backup")

        setForeground(createForegroundInfo("Starting backup…"))

        val engine = BackupEngine(applicationContext, session, prefs.loadExcludes())
        val result = engine.run(
            treeUri = treeUri,
            folderName = folderName,
            onProgress = { p ->
                // Do not put cleartext relative paths into the notification shade.
                val label = when (p.phase) {
                    BackupProgress.Phase.SCANNING -> "Scanning…"
                    BackupProgress.Phase.UPLOADING ->
                        "Uploading ${p.filesDone}/${p.filesTotal}"
                    BackupProgress.Phase.FINISHED -> "Backup finished"
                    BackupProgress.Phase.FAILED -> "Backup failed"
                }
                notify(label)
            },
            isCancelled = { isStopped },
        )

        return when (result.phase) {
            BackupProgress.Phase.FINISHED -> {
                notify("Uploaded ${result.filesDone} files")
                Result.success(
                    workDataOf(
                        KEY_FILES to result.filesDone,
                        KEY_BYTES to result.bytesDone,
                        KEY_MESSAGE to "Uploaded ${result.filesDone} files (${result.bytesDone} bytes)",
                    ),
                )
            }
            else -> fail(result.error ?: "Backup failed")
        }
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
