package org.gnucash.android.work

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import org.gnucash.android.service.ScheduledActionService

/**
 * Worker to execute scheduled actions.
 */
class ActionWorker(context: Context, workerParams: WorkerParameters) :
    Worker(context, workerParams) {

    override fun doWork(): Result {
        val service = ScheduledActionService()
        service.doWork(applicationContext)
        return Result.success()
    }
}