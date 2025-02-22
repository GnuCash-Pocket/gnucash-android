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

package org.gnucash.android.service;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.WorkRequest;

import org.gnucash.android.app.GnuCashApplication;
import org.gnucash.android.db.DatabaseHelper;
import org.gnucash.android.db.DatabaseSchema;
import org.gnucash.android.db.adapter.BooksDbAdapter;
import org.gnucash.android.db.adapter.DatabaseAdapter;
import org.gnucash.android.db.adapter.RecurrenceDbAdapter;
import org.gnucash.android.db.adapter.ScheduledActionDbAdapter;
import org.gnucash.android.db.adapter.TransactionsDbAdapter;
import org.gnucash.android.export.ExportAsyncTask;
import org.gnucash.android.export.ExportParams;
import org.gnucash.android.model.Book;
import org.gnucash.android.model.ScheduledAction;
import org.gnucash.android.model.Transaction;
import org.gnucash.android.util.BackupManager;
import org.gnucash.android.util.DateExtKt;
import org.gnucash.android.work.ActionWorker;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import timber.log.Timber;

/**
 * Service for running scheduled events.
 *
 * <p>It's run every time the <code>enqueueWork</code> is called. It goes
 * through all scheduled event entries in the the database and executes them.</p>
 *
 * @author Ngewi Fet <ngewif@gmail.com>
 */
public class ScheduledActionService {

    public static void schedulePeriodic(@NonNull Context context) {
        WorkManager.getInstance(context)
            .cancelAllWork();

        schedulePeriodicActions(context);
        BackupManager.schedulePeriodicBackups(context);
    }

    /**
     * Starts the service for scheduled events and schedules an alarm to call the service twice daily.
     * <p>If the alarm already exists, this method does nothing. If not, the alarm will be created
     * Hence, there is no harm in calling the method repeatedly</p>
     *
     * @param context Application context
     */
    public static void schedulePeriodicActions(@NonNull Context context) {
        Timber.i("Scheduling actions");
        WorkRequest request = new PeriodicWorkRequest.Builder(ActionWorker.class, 1, TimeUnit.HOURS)
            .setInitialDelay(15, TimeUnit.SECONDS)
            .build();

        WorkManager.getInstance(context)
            .enqueue(request);
    }

    public void doWork(@NonNull Context context) {
        Timber.i("Starting scheduled action service");
        try {
            processScheduledBooks(context);
            Timber.i("Completed service @ %s", DateExtKt.formatLongDateTime(System.currentTimeMillis()));
        } catch (Throwable e) {
            Timber.e(e, "Scheduled service error: %s", e.getMessage());
        }
    }

    private void processScheduledBooks(@NonNull Context context) {
        BooksDbAdapter booksDbAdapter = BooksDbAdapter.getInstance();
        List<Book> books = booksDbAdapter.getAllRecords();
        for (Book book : books) { //// TODO: 20.04.2017 Retrieve only the book UIDs with new method
            processScheduledBook(context, book);
        }
    }

    private void processScheduledBook(@NonNull Context context, @NonNull Book book) {
        final String activeBookUID = GnuCashApplication.getActiveBookUID();
        DatabaseHelper dbHelper = new DatabaseHelper(context, book.getUID());
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        RecurrenceDbAdapter recurrenceDbAdapter = new RecurrenceDbAdapter(db);
        ScheduledActionDbAdapter scheduledActionDbAdapter = new ScheduledActionDbAdapter(recurrenceDbAdapter);

        List<ScheduledAction> scheduledActions = scheduledActionDbAdapter.getAllEnabledScheduledActions();
        Timber.i("Processing %d total scheduled actions for Book: %s",
            scheduledActions.size(), book.getDisplayName());
        processScheduledActions(context, scheduledActions, db);

        //close all databases except the currently active database
        if (!book.getUID().equals(activeBookUID)) {
            dbHelper.close();
        }
    }

    /**
     * Process scheduled actions and execute any pending actions
     *
     * @param context          The application context.
     * @param scheduledActions List of scheduled actions
     */
    //made public static for testing. Do not call these methods directly
    @VisibleForTesting
    static void processScheduledActions(@NonNull Context context, List<ScheduledAction> scheduledActions, SQLiteDatabase db) {
        for (ScheduledAction scheduledAction : scheduledActions) {
            processScheduledAction(context, scheduledAction, db);
        }
    }

    /**
     * Process scheduled action and execute any pending actions
     *
     * @param scheduledAction The scheduled action.
     */
    //made public static for testing. Do not call these methods directly
    @VisibleForTesting
    static void processScheduledAction(@NonNull ScheduledAction scheduledAction, SQLiteDatabase db) {
        processScheduledAction(GnuCashApplication.getAppContext(), scheduledAction, db);
    }

    /**
     * Process scheduled action and execute any pending actions
     *
     * @param context         The application context.
     * @param scheduledAction The scheduled action.
     */
    //made public static for testing. Do not call these methods directly
    @VisibleForTesting
    static void processScheduledAction(@NonNull Context context, @NonNull ScheduledAction scheduledAction, SQLiteDatabase db) {
        long now = System.currentTimeMillis();
        int totalPlannedExecutions = scheduledAction.getTotalPlannedExecutionCount();
        int executionCount = scheduledAction.getExecutionCount();

        //the end time of the ScheduledAction is not handled here because
        //it is handled differently for transactions and backups. See the individual methods.
        if (scheduledAction.getStartTime() > now    //if schedule begins in the future
            || !scheduledAction.isEnabled()     // of if schedule is disabled
            || (totalPlannedExecutions > 0 && executionCount >= totalPlannedExecutions)) { //limit was set and we reached or exceeded it
            Timber.i("Skipping scheduled action: %s", scheduledAction.toString());
            return;
        }

        executeScheduledEvent(context, scheduledAction, db);
    }

    /**
     * Executes a scheduled event according to the specified parameters
     *
     * @param context         The application context.
     * @param scheduledAction ScheduledEvent to be executed
     */
    private static void executeScheduledEvent(@NonNull Context context, ScheduledAction scheduledAction, SQLiteDatabase db) {
        Timber.i("Executing scheduled action: %s", scheduledAction.toString());
        int executionCount = 0;

        switch (scheduledAction.getActionType()) {
            case TRANSACTION:
                executionCount += executeTransactions(scheduledAction, db);
                break;

            case BACKUP:
                executionCount += executeBackup(context, scheduledAction, GnuCashApplication.getActiveBookUID());
                break;
        }

        if (executionCount > 0) {
            scheduledAction.setLastRunTime(System.currentTimeMillis());
            // Set the execution count in the object because it will be checked
            // for the next iteration in the calling loop.
            // This call is important, do not remove!!
            scheduledAction.setExecutionCount(scheduledAction.getExecutionCount() + executionCount);
            // Update the last run time and execution count
            ContentValues contentValues = new ContentValues();
            contentValues.put(DatabaseSchema.ScheduledActionEntry.COLUMN_LAST_RUN,
                scheduledAction.getLastRunTime());
            contentValues.put(DatabaseSchema.ScheduledActionEntry.COLUMN_EXECUTION_COUNT,
                scheduledAction.getExecutionCount());
            db.update(DatabaseSchema.ScheduledActionEntry.TABLE_NAME, contentValues,
                DatabaseSchema.ScheduledActionEntry.COLUMN_UID + "=?", new String[]{scheduledAction.getUID()});
        }
    }

    /**
     * Executes scheduled backups for a given scheduled action.
     * The backup will be executed only once, even if multiple schedules were missed
     *
     * @param context         The application context.
     * @param scheduledAction Scheduled action referencing the backup
     * @param bookUID         The book UID.
     * @return Number of times backup is executed. This should either be 1 or 0
     */
    private static int executeBackup(@NonNull Context context, ScheduledAction scheduledAction, String bookUID) {
        if (!shouldExecuteScheduledBackup(scheduledAction))
            return 0;

        ExportParams params = ExportParams.parseCsv(scheduledAction.getTag());
        // HACK: the tag isn't updated with the new date, so set the correct by hand
        params.setExportStartTime(new Timestamp(scheduledAction.getLastRunTime()));
        Integer result = null;
        try {
            //wait for async task to finish before we proceed (we are holding a wake lock)
            result = new ExportAsyncTask(context, bookUID).execute(params).get();
        } catch (InterruptedException | ExecutionException e) {
            Timber.e(e);
        }
        if (result == null || result < 0) {
            Timber.w("Backup/export did not occur. There was a problem.");
            return 0;
        }
        if (result == 0) {
            Timber.i("Backup/export did not occur." +
                " There might have been no new transactions to export");
            return 0;
        }
        return 1;
    }

    /**
     * Check if a scheduled action is due for execution
     *
     * @param scheduledAction Scheduled action
     * @return {@code true} if execution is due, {@code false} otherwise
     */
    @SuppressWarnings("RedundantIfStatement")
    private static boolean shouldExecuteScheduledBackup(ScheduledAction scheduledAction) {
        long now = System.currentTimeMillis();
        long endTime = scheduledAction.getEndTime();

        if (endTime > 0 && endTime < now)
            return false;

        if (scheduledAction.computeNextTimeBasedScheduledExecutionTime() > now)
            return false;

        return true;
    }

    /**
     * Executes scheduled transactions which are to be added to the database.
     * <p>If a schedule was missed, all the intervening transactions will be generated, even if
     * the end time of the transaction was already reached</p>
     *
     * @param scheduledAction Scheduled action which references the transaction
     * @param db              SQLiteDatabase where the transactions are to be executed
     * @return Number of transactions created as a result of this action
     */
    private static int executeTransactions(@NonNull ScheduledAction scheduledAction, @NonNull SQLiteDatabase db) {
        int executionCount = 0;
        String actionUID = scheduledAction.getActionUID();
        if (TextUtils.isEmpty(actionUID)) {
            Timber.w("Scheduled transaction without action");
            return executionCount;
        }
        TransactionsDbAdapter transactionsDbAdapter = new TransactionsDbAdapter(db);
        Transaction trxnTemplate;
        try {
            trxnTemplate = transactionsDbAdapter.getRecord(actionUID);
        } catch (IllegalArgumentException ex) { //if the record could not be found, abort
            Timber.e(ex, "Scheduled transaction with action " + actionUID + " could not be found in the db with path " + db.getPath());
            return executionCount;
        }

        long now = System.currentTimeMillis();
        //if there is an end time in the past, we execute all schedules up to the end time.
        //if the end time is in the future, we execute all schedules until now (current time)
        //if there is no end time, we execute all schedules until now
        long endTime = scheduledAction.getEndTime() > 0 ? Math.min(scheduledAction.getEndTime(), now) : now;
        int totalPlannedExecutions = scheduledAction.getTotalPlannedExecutionCount();
        List<Transaction> transactions = new ArrayList<>();

        int previousExecutionCount = scheduledAction.getExecutionCount(); // We'll modify it
        //we may be executing scheduled action significantly after scheduled time (depending on when Android fires the alarm)
        //so compute the actual transaction time from pre-known values
        long transactionTime = scheduledAction.computeNextCountBasedScheduledExecutionTime();
        while (transactionTime <= endTime) {
            Transaction recurringTrxn = new Transaction(trxnTemplate, true);
            recurringTrxn.setTime(transactionTime);
            transactions.add(recurringTrxn);
            recurringTrxn.setScheduledActionUID(scheduledAction.getUID());
            scheduledAction.setExecutionCount(++executionCount); //required for computingNextScheduledExecutionTime

            if (totalPlannedExecutions > 0 && executionCount >= totalPlannedExecutions)
                break; //if we hit the total planned executions set, then abort
            transactionTime = scheduledAction.computeNextCountBasedScheduledExecutionTime();
        }

        transactionsDbAdapter.bulkAddRecords(transactions, DatabaseAdapter.UpdateMethod.insert);
        // Be nice and restore the parameter's original state to avoid confusing the callers
        scheduledAction.setExecutionCount(previousExecutionCount);
        return executionCount;
    }
}
