/*
 * Copyright (c) 2015 Ngewi Fet <ngewif@gmail.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gnucash.android.service

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import androidx.annotation.VisibleForTesting
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import androidx.work.WorkRequest
import org.gnucash.android.app.GnuCashApplication
import org.gnucash.android.db.DatabaseHelper
import org.gnucash.android.db.DatabaseHolder
import org.gnucash.android.db.DatabaseSchema.ScheduledActionEntry
import org.gnucash.android.db.adapter.BooksDbAdapter
import org.gnucash.android.db.adapter.RecurrenceDbAdapter
import org.gnucash.android.db.adapter.ScheduledActionDbAdapter
import org.gnucash.android.db.adapter.TransactionsDbAdapter
import org.gnucash.android.export.ExportAsyncTask
import org.gnucash.android.model.Book
import org.gnucash.android.model.ScheduledAction
import org.gnucash.android.model.Transaction
import org.gnucash.android.util.BackupManager.schedulePeriodicBackups
import org.gnucash.android.util.formatLongDateTime
import org.gnucash.android.util.set
import org.gnucash.android.work.ActionWorker
import timber.log.Timber
import java.sql.Timestamp
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import kotlin.math.min

/**
 * Service for running scheduled events.
 *
 *
 * It's run every time the `enqueueWork` is called. It goes
 * through all scheduled event entries in the the database and executes them.
 *
 * @author Ngewi Fet <ngewif@gmail.com>
 */
class ScheduledActionService {
    fun doWork(context: Context) {
        Timber.i("Starting scheduled action service")
        try {
            processScheduledBooks(context)
            Timber.i("Completed service @ %s", formatLongDateTime(System.currentTimeMillis()))
        } catch (e: Throwable) {
            Timber.e(e, "Scheduled service error: %s", e.message)
        }
    }

    private fun processScheduledBooks(context: Context) {
        val booksDbAdapter = BooksDbAdapter.instance
        val books = booksDbAdapter.allRecords
        for (book in books) {
            processScheduledBook(context, book)
        }
    }

    private fun processScheduledBook(context: Context, book: Book) {
        val activeBookUID = GnuCashApplication.activeBookUID
        val dbHelper = DatabaseHelper(context, book.uid)
        val dbHolder = dbHelper.holder
        val recurrenceDbAdapter = RecurrenceDbAdapter(dbHolder)
        val transactionsDbAdapter = TransactionsDbAdapter(dbHolder)
        val scheduledActionDbAdapter =
            ScheduledActionDbAdapter(recurrenceDbAdapter, transactionsDbAdapter)

        val scheduledActions = scheduledActionDbAdapter.allEnabledScheduledActions
        Timber.i(
            "Processing %d total scheduled actions for Book: %s",
            scheduledActions.size, book.displayName
        )
        processScheduledActions(dbHolder, scheduledActions)

        //close all databases except the currently active database
        if (book.uid != activeBookUID) {
            dbHelper.close()
        }
    }

    companion object {
        fun schedulePeriodic(context: Context) {
            WorkManager.getInstance(context)
                .cancelAllWork()

            schedulePeriodicActions(context)
            schedulePeriodicBackups(context)
        }

        /**
         * Starts the service for scheduled events and schedules an alarm to call the service twice daily.
         *
         * If the alarm already exists, this method does nothing. If not, the alarm will be created
         * Hence, there is no harm in calling the method repeatedly
         *
         * @param context Application context
         */
        fun schedulePeriodicActions(context: Context) {
            Timber.i("Scheduling actions")
            val request: WorkRequest =
                PeriodicWorkRequest.Builder(ActionWorker::class.java, 1, TimeUnit.HOURS)
                    .setInitialDelay(15, TimeUnit.SECONDS)
                    .build()

            WorkManager.getInstance(context)
                .enqueue(request)
        }

        /**
         * Process scheduled actions and execute any pending actions
         *
         * @param dbHolder         Database holder
         * @param scheduledActions List of scheduled actions
         */
        //made public static for testing. Do not call these methods directly
        @VisibleForTesting
        fun processScheduledActions(
            dbHolder: DatabaseHolder,
            scheduledActions: List<ScheduledAction>
        ) {
            for (scheduledAction in scheduledActions) {
                processScheduledAction(dbHolder, scheduledAction)
            }
        }

        /**
         * Process scheduled action and execute any pending actions
         *
         * @param dbHolder        Database holder
         * @param scheduledAction The scheduled action.
         */
        //made public static for testing. Do not call these methods directly
        @VisibleForTesting
        fun processScheduledAction(dbHolder: DatabaseHolder, scheduledAction: ScheduledAction) {
            val now = System.currentTimeMillis()
            val totalPlannedExecutions = scheduledAction.totalPlannedExecutionCount
            val executionCount = scheduledAction.instanceCount

            //the end time of the ScheduledAction is not handled here because
            //it is handled differently for transactions and backups. See the individual methods.
            if (scheduledAction.startDate > now //if schedule begins in the future
                || !scheduledAction.isEnabled // of if schedule is disabled
                || (totalPlannedExecutions > 0 && executionCount >= totalPlannedExecutions)
            ) { //limit was set and we reached or exceeded it
                Timber.i("Skipping scheduled action: %s", scheduledAction)
                return
            }

            executeScheduledEvent(dbHolder, scheduledAction)
        }

        /**
         * Executes a scheduled event according to the specified parameters
         *
         * @param dbHolder        Database holder
         * @param scheduledAction ScheduledEvent to be executed
         */
        private fun executeScheduledEvent(
            dbHolder: DatabaseHolder,
            scheduledAction: ScheduledAction
        ) {
            Timber.i("Executing scheduled action: %s", scheduledAction)
            val instanceCount = scheduledAction.instanceCount

            val executionCount = when (scheduledAction.actionType) {
                ScheduledAction.ActionType.TRANSACTION ->
                    executeTransactions(dbHolder, scheduledAction)

                ScheduledAction.ActionType.EXPORT ->
                    executeBackup(dbHolder, scheduledAction)
            }

            if (executionCount > 0) {
                scheduledAction.lastRunTime = System.currentTimeMillis()
                // Set the execution count in the object because it will be checked
                // for the next iteration in the calling loop.
                // This call is important, do not remove!!
                scheduledAction.instanceCount = instanceCount + executionCount
                // Update the last run time and execution count
                val contentValues = ContentValues()
                contentValues[ScheduledActionEntry.COLUMN_LAST_OCCUR] = scheduledAction.lastRunTime
                contentValues[ScheduledActionEntry.COLUMN_INSTANCE_COUNT] =
                    scheduledAction.instanceCount

                val db = dbHolder.db
                db.update(
                    ScheduledActionEntry.TABLE_NAME,
                    contentValues,
                    ScheduledActionEntry.COLUMN_UID + "=?",
                    arrayOf<String?>(scheduledAction.uid)
                )
            }
        }

        /**
         * Executes scheduled backups for a given scheduled action.
         * The backup will be executed only once, even if multiple schedules were missed
         *
         * @param dbHolder Databas holder
         * @param scheduledAction Scheduled action referencing the backup
         * @return Number of times backup is executed. This should either be 1 or 0
         */
        private fun executeBackup(dbHolder: DatabaseHolder, scheduledAction: ScheduledAction): Int {
            if (!shouldExecuteScheduledBackup(scheduledAction)) return 0

            val context = dbHolder.context
            val bookUID = scheduledAction.actionUID ?: dbHolder.name
            val params = scheduledAction.getExportParams() ?: return 0
            // HACK: the tag isn't updated with the new date, so set the correct by hand
            params.exportStartTime = Timestamp(scheduledAction.lastRunTime)
            var result: Uri? = null
            try {
                //wait for async task to finish before we proceed (we are holding a wake lock)
                val exporter = ExportAsyncTask.createExporter(context, params, bookUID, null)
                result = exporter.export()
            } catch (e: InterruptedException) {
                Timber.e(e)
            } catch (e: ExecutionException) {
                Timber.e(e)
            }
            if (result == null) {
                Timber.w("Backup/export did not occur. There might have been no new transactions to export")
                return 0
            }
            return 1
        }

        /**
         * Check if a scheduled action is due for execution
         *
         * @param scheduledAction Scheduled action
         * @return `true` if execution is due, `false` otherwise
         */
        private fun shouldExecuteScheduledBackup(scheduledAction: ScheduledAction): Boolean {
            if (scheduledAction.actionType != ScheduledAction.ActionType.EXPORT) {
                return false
            }
            val now = System.currentTimeMillis()
            val endTime = scheduledAction.endTime

            if (endTime > 0 && endTime < now) {
                return false
            }
            if (scheduledAction.computeNextTimeBasedScheduledExecutionTime() > now) {
                return false
            }

            return true
        }

        /**
         * Executes scheduled transactions which are to be added to the database.
         *
         * If a schedule was missed, all the intervening transactions will be generated, even if
         * the end time of the transaction was already reached
         *
         * @param dbHolder        Database holder
         * @param scheduledAction Scheduled action which references the transaction
         * @return Number of transactions created as a result of this action
         */
        private fun executeTransactions(
            dbHolder: DatabaseHolder,
            scheduledAction: ScheduledAction
        ): Int {
            val actionUID = scheduledAction.actionUID
            if (actionUID.isNullOrEmpty()) {
                Timber.w("Scheduled transaction without action")
                return 0
            }
            val transactionsDbAdapter = TransactionsDbAdapter(dbHolder)
            val template: Transaction?
            try {
                template = transactionsDbAdapter.getRecord(actionUID)
            } catch (ex: IllegalArgumentException) { //if the record could not be found, abort
                Timber.e(
                    ex,
                    "Scheduled transaction with action %s could not be found in the db with path %s",
                    actionUID, dbHolder.db.path
                )
                return 0
            }

            val now = System.currentTimeMillis()
            //if there is an end time in the past, we execute all schedules up to the end time.
            //if the end time is in the future, we execute all schedules until now (current time)
            //if there is no end time, we execute all schedules until now
            val endTime = if (scheduledAction.endTime > 0) {
                min(scheduledAction.endTime, now)
            } else {
                now
            }
            val totalPlannedExecutions = scheduledAction.totalPlannedExecutionCount

            var executionCount = 0
            val previousExecutionCount = scheduledAction.instanceCount // We'll modify it
            //we may be executing scheduled action significantly after scheduled time (depending on when Android fires the alarm)
            //so compute the actual transaction time from pre-known values
            var transactionTime = scheduledAction.computeNextCountBasedScheduledExecutionTime()
            while (transactionTime <= endTime) {
                val transaction = template.copy()
                transaction.time = transactionTime
                transaction.scheduledActionUID = scheduledAction.uid
                transactionsDbAdapter.insert(transaction)
                //required for computingNextScheduledExecutionTime
                scheduledAction.instanceCount = previousExecutionCount + ++executionCount

                if (totalPlannedExecutions > 0 && executionCount >= totalPlannedExecutions) {
                    break //if we hit the total planned executions set, then abort
                }

                transactionTime = scheduledAction.computeNextCountBasedScheduledExecutionTime()
            }

            // Be nice and restore the parameter's original state to avoid confusing the callers
            scheduledAction.instanceCount = previousExecutionCount
            return executionCount
        }
    }
}
