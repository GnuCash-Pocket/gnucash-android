package org.gnucash.android.work

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import org.gnucash.android.util.BackupManager

/**
 * Worker to execute backups of books.
 */
class BackupWorker(context: Context, workerParams: WorkerParameters) :
    Worker(context, workerParams) {

    override fun doWork(): Result {
        BackupManager.backupAllBooks(applicationContext)
        return Result.success()
    }
}