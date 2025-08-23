/*
 * Copyright (c) 2016 Ngewi Fet <ngewif@gmail.com>
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
import android.database.sqlite.SQLiteDatabase
import org.assertj.core.api.Assertions.assertThat
import org.gnucash.android.app.GnuCashApplication
import org.gnucash.android.db.DatabaseHolder
import org.gnucash.android.db.DatabaseSchema.TransactionEntry
import org.gnucash.android.db.adapter.AccountsDbAdapter
import org.gnucash.android.db.adapter.ScheduledActionDbAdapter
import org.gnucash.android.db.adapter.TransactionsDbAdapter
import org.gnucash.android.export.ExportFormat
import org.gnucash.android.export.ExportParams
import org.gnucash.android.export.Exporter
import org.gnucash.android.model.Account
import org.gnucash.android.model.Commodity
import org.gnucash.android.model.Money
import org.gnucash.android.model.PeriodType
import org.gnucash.android.model.Recurrence
import org.gnucash.android.model.ScheduledAction
import org.gnucash.android.model.Split
import org.gnucash.android.model.Transaction
import org.gnucash.android.model.TransactionType
import org.gnucash.android.test.unit.GnuCashTest
import org.gnucash.android.util.set
import org.joda.time.DateTime
import org.joda.time.DateTimeConstants
import org.joda.time.LocalDateTime
import org.joda.time.Weeks
import org.junit.After
import org.junit.Before
import org.junit.Test
import timber.log.Timber
import java.io.File
import java.math.BigDecimal
import java.sql.Timestamp
import java.util.Calendar

/**
 * Test the the scheduled actions service runs as expected
 */
class ScheduledActionServiceTest : GnuCashTest() {
    private var actionUID: String? = null
    private lateinit var dbHolder: DatabaseHolder
    private lateinit var db: SQLiteDatabase

    private val baseAccount = Account("Base Account")
    private val transferAccount = Account("Transfer Account")

    private lateinit var transactionsDbAdapter: TransactionsDbAdapter

    @Before
    fun setUp() {
        db = GnuCashApplication.activeDb!!
        dbHolder = DatabaseHolder(context, db)
        baseAccount.commodity = Commodity.DEFAULT_COMMODITY
        transferAccount.commodity = Commodity.DEFAULT_COMMODITY

        val templateTransaction = Transaction("Recurring Transaction")
        templateTransaction.commodity = Commodity.DEFAULT_COMMODITY
        templateTransaction.isTemplate = true

        val split1 = Split(Money(BigDecimal.TEN, Commodity.DEFAULT_COMMODITY), baseAccount.uid)
        val split2 = split1.createPair(transferAccount.uid)

        templateTransaction.addSplit(split1)
        templateTransaction.addSplit(split2)

        actionUID = templateTransaction.uid
        Timber.v("action ID: $actionUID")

        val accountsDbAdapter = AccountsDbAdapter.instance
        accountsDbAdapter.addRecord(baseAccount)
        accountsDbAdapter.addRecord(transferAccount)

        transactionsDbAdapter = TransactionsDbAdapter.instance
        transactionsDbAdapter.insert(templateTransaction)
    }

    @After
    fun tearDown() {
        transactionsDbAdapter.deleteAllRecords()
    }

    @Test
    fun disabledScheduledActions_shouldNotRun() {
        val recurrence = Recurrence(PeriodType.WEEK)
        val scheduledAction1 = ScheduledAction(ScheduledAction.ActionType.TRANSACTION)
        scheduledAction1.startTime = System.currentTimeMillis() - 100000
        scheduledAction1.isEnabled = false
        scheduledAction1.actionUID = actionUID
        scheduledAction1.setRecurrence(recurrence)

        val trxnAdapter = TransactionsDbAdapter.instance

        assertThat(trxnAdapter.recordsCount).isZero()
        ScheduledActionService.processScheduledAction(dbHolder, scheduledAction1)
        assertThat(trxnAdapter.recordsCount).isZero()
    }

    @Test
    fun futureScheduledActions_shouldNotRun() {
        val scheduledAction = ScheduledAction(ScheduledAction.ActionType.TRANSACTION)
        scheduledAction.startTime = System.currentTimeMillis() + 100000
        scheduledAction.isEnabled = true
        scheduledAction.setRecurrence(Recurrence(PeriodType.MONTH))
        scheduledAction.actionUID = actionUID

        val trxnAdapter = TransactionsDbAdapter.instance

        assertThat(trxnAdapter.recordsCount).isZero()
        ScheduledActionService.processScheduledAction(dbHolder, scheduledAction)
        assertThat(trxnAdapter.recordsCount).isZero()
    }

    /**
     * Transactions whose execution count has reached or exceeded the planned execution count
     */
    @Test
    fun exceededExecutionCounts_shouldNotRun() {
        val scheduledAction = ScheduledAction(ScheduledAction.ActionType.TRANSACTION)
        scheduledAction.actionUID = actionUID
        scheduledAction.startTime = DateTime(2015, 5, 31, 14, 0).millis
        scheduledAction.isEnabled = true
        scheduledAction.setRecurrence(Recurrence(PeriodType.WEEK))
        scheduledAction.totalPlannedExecutionCount = 4
        scheduledAction.executionCount = 4

        val trxnAdapter = TransactionsDbAdapter.instance
        assertThat(trxnAdapter.recordsCount).isZero()
        ScheduledActionService.processScheduledAction(dbHolder, scheduledAction)
        assertThat(trxnAdapter.recordsCount).isZero()
    }

    /**
     * Test that normal scheduled transactions would lead to new transaction entries
     */
    @Test
    fun missedScheduledTransactions_shouldBeGenerated() {
        val scheduledAction = ScheduledAction(ScheduledAction.ActionType.TRANSACTION)
        val startTime = DateTime(2016, 6, 6, 9, 0)
        scheduledAction.startTime = startTime.millis
        val endTime = DateTime(2016, 9, 12, 8, 0) //end just before last appointment
        scheduledAction.endTime = endTime.millis

        scheduledAction.actionUID = actionUID

        val recurrence = Recurrence(PeriodType.WEEK)
        recurrence.multiplier = 2
        recurrence.byDays = listOf(Calendar.MONDAY)
        scheduledAction.setRecurrence(recurrence)
        ScheduledActionDbAdapter.instance.insert(scheduledAction)

        val transactionsDbAdapter = TransactionsDbAdapter.instance
        assertThat(transactionsDbAdapter.recordsCount).isZero()

        ScheduledActionService.processScheduledAction(dbHolder, scheduledAction)

        assertThat(transactionsDbAdapter.recordsCount).isEqualTo(7)
    }

    fun endTimeInTheFuture_shouldExecuteOnlyUntilPresent() {
        val scheduledAction = ScheduledAction(ScheduledAction.ActionType.TRANSACTION)
        val startTime = DateTime(2016, 6, 6, 9, 0)
        scheduledAction.startTime = startTime.millis
        scheduledAction.actionUID = actionUID

        scheduledAction.setRecurrence(PeriodType.WEEK, 2)
        scheduledAction.endTime = DateTime(2017, 8, 16, 9, 0).millis
        ScheduledActionDbAdapter.instance.insert(scheduledAction)

        val transactionsDbAdapter = TransactionsDbAdapter.instance
        assertThat(transactionsDbAdapter.recordsCount).isZero()

        ScheduledActionService.processScheduledAction(dbHolder, scheduledAction)

        val weeks = Weeks.weeksBetween(startTime, DateTime(2016, 8, 29, 10, 0)).weeks
        val expectedTransactionCount = weeks / 2 //multiplier from the PeriodType

        assertThat(transactionsDbAdapter.recordsCount)
            .isEqualTo(expectedTransactionCount.toLong())
    }

    /**
     * Test that if the end time of a scheduled transaction has passed, but the schedule was missed
     * (either because the book was not opened or similar) then the scheduled transactions for the
     * relevant period should still be executed even though end time has passed.
     *
     * This holds only for transactions. Backups will be skipped
     */
    @Test
    fun scheduledTransactionsWithEndTimeInPast_shouldBeExecuted() {
        val scheduledAction = ScheduledAction(ScheduledAction.ActionType.TRANSACTION)
        val startTime = DateTime(2016, 6, 6, 9, 0)
        scheduledAction.startTime = startTime.millis
        scheduledAction.actionUID = actionUID

        val recurrence = Recurrence(PeriodType.WEEK)
        recurrence.multiplier = 2
        recurrence.byDays = listOf(Calendar.MONDAY)
        scheduledAction.setRecurrence(recurrence)
        scheduledAction.endTime = DateTime(2016, 8, 8, 9, 0).millis
        ScheduledActionDbAdapter.instance.insert(scheduledAction)

        val transactionsDbAdapter = TransactionsDbAdapter.instance
        assertThat(transactionsDbAdapter.recordsCount).isZero()

        ScheduledActionService.processScheduledAction(dbHolder, scheduledAction)

        val expectedCount = 5
        assertThat(scheduledAction.executionCount).isEqualTo(expectedCount)
        assertThat(transactionsDbAdapter.recordsCount)
            .isEqualTo(expectedCount.toLong()) //would be 6 if the end time is not respected
    }

    /**
     * Test that only scheduled actions with action UIDs are processed
     */
    @Test //(expected = IllegalArgumentException.class)
    fun recurringTransactions_shouldHaveScheduledActionUID() {
        val scheduledAction = ScheduledAction(ScheduledAction.ActionType.TRANSACTION)
        val startTime = DateTime(2016, 7, 4, 12, 0)
        scheduledAction.startTime = startTime.millis
        scheduledAction.setRecurrence(PeriodType.MONTH, 1)

        val transactionsDbAdapter = TransactionsDbAdapter.instance
        assertThat(transactionsDbAdapter.recordsCount).isZero()

        ScheduledActionService.processScheduledAction(dbHolder, scheduledAction)

        //no change in the database since no action UID was specified
        assertThat(transactionsDbAdapter.recordsCount).isZero()
    }

    /**
     * Scheduled backups should run only once.
     *
     *
     * Backups may have been missed since the last run, but still only
     * one should be done.
     *
     *
     * For example, if we have set up a daily backup, the last one
     * was done on Monday and it's Thursday, two backups have been
     * missed. Doing the two missed backups plus today's wouldn't be
     * useful, so just one should be done.
     */
    @Test
    fun scheduledBackups_shouldRunOnlyOnce() {
        val scheduledBackup = ScheduledAction(ScheduledAction.ActionType.BACKUP)
        scheduledBackup.actionUID = GnuCashApplication.activeBookUID
        scheduledBackup.startTime = LocalDateTime.now()
            .minusMonths(4).minusDays(2).toDate().time
        scheduledBackup.setRecurrence(PeriodType.MONTH, 1)
        scheduledBackup.executionCount = 2
        scheduledBackup.lastRunTime = LocalDateTime.now().minusMonths(2).toDate().time
        var previousLastRun = scheduledBackup.lastRunTime

        val backupParams = ExportParams(ExportFormat.XML)
        backupParams.exportTarget = ExportParams.ExportTarget.SD_CARD
        scheduledBackup.tag = backupParams.toTag()

        // Check there's not a backup for each missed run
        val bookUID = GnuCashApplication.activeBookUID
        assertThat(bookUID).isNotNull()
        val backupFolder = File(Exporter.getExportFolderPath(context, bookUID!!))
        assertThat(backupFolder).exists()
        assertThat(backupFolder.listFiles()).isEmpty()

        // Check there's not a backup for each missed run
        ScheduledActionService.processScheduledAction(dbHolder, scheduledBackup)
        assertThat(scheduledBackup.executionCount).isEqualTo(3)
        assertThat(scheduledBackup.lastRunTime).isGreaterThanOrEqualTo(previousLastRun)
        var backupFiles = backupFolder.listFiles()
        assertThat(backupFiles!!).hasSize(1)
        assertThat(backupFiles[0]).exists().hasExtension("xac")

        // Check also across service runs
        previousLastRun = scheduledBackup.lastRunTime
        ScheduledActionService.processScheduledAction(dbHolder, scheduledBackup)
        assertThat(scheduledBackup.executionCount).isEqualTo(3)
        assertThat(scheduledBackup.lastRunTime).isGreaterThanOrEqualTo(previousLastRun)
        backupFiles = backupFolder.listFiles()
        assertThat(backupFiles!!).hasSize(1)
        assertThat(backupFiles[0]).exists().hasExtension("xac")
    }

    /**
     * Tests that a scheduled backup isn't executed before the next scheduled
     * execution according to its recurrence.
     *
     *
     * Tests for bug [codinguser/gnucash-android#583](https://github.com/codinguser/gnucash-android/issues/583)
     */
    @Test
    fun scheduledBackups_shouldNotRunBeforeNextScheduledExecution() {
        val scheduledBackup = ScheduledAction(ScheduledAction.ActionType.BACKUP)
        scheduledBackup.startTime =
            LocalDateTime.now().withDayOfWeek(DateTimeConstants.WEDNESDAY).toDate().time
        scheduledBackup.lastRunTime = scheduledBackup.startTime
        val previousLastRun = scheduledBackup.lastRunTime
        scheduledBackup.executionCount = 0
        val recurrence = Recurrence(PeriodType.WEEK)
        recurrence.multiplier = 1
        recurrence.byDays = listOf(Calendar.MONDAY)
        scheduledBackup.setRecurrence(recurrence)

        val backupParams = ExportParams(ExportFormat.XML)
        backupParams.exportTarget = ExportParams.ExportTarget.SD_CARD
        scheduledBackup.tag = backupParams.toTag()

        val bookUID = GnuCashApplication.activeBookUID
        assertThat(bookUID).isNotNull()
        val backupFolder = File(Exporter.getExportFolderPath(context, bookUID!!))
        assertThat(backupFolder).exists()
        assertThat(backupFolder.listFiles()).isEmpty()

        ScheduledActionService.processScheduledAction(dbHolder, scheduledBackup)

        assertThat(scheduledBackup.executionCount).isZero()
        assertThat(scheduledBackup.lastRunTime).isEqualTo(previousLastRun)
        assertThat(backupFolder.listFiles()).isEmpty()
    }

    /**
     * Tests that a scheduled QIF backup isn't done when no transactions have
     * been added or modified after the last run.
     */
    @Test
    fun scheduledBackups_shouldNotIncludeTransactionsPreviousToTheLastRun() {
        val scheduledBackup = ScheduledAction(ScheduledAction.ActionType.BACKUP).apply {
            startTime = LocalDateTime.now().minusDays(15).toDate().time
            lastRunTime = LocalDateTime.now().minusDays(8).toDate().time
            executionCount = 1
            val recurrence = Recurrence(PeriodType.WEEK).apply {
                multiplier = 1
                byDays = listOf(Calendar.WEDNESDAY)
            }
            setRecurrence(recurrence)
            val backupParams = ExportParams(ExportFormat.QIF).apply {
                exportTarget = ExportParams.ExportTarget.SD_CARD
                exportStartTime = Timestamp(startTime)
            }
            tag = backupParams.toTag()
        }
        val previousLastRun = scheduledBackup.lastRunTime

        // Create a transaction with a modified date previous to the last run
        val transaction = Transaction("Tandoori express")
        val split = Split(
            Money("10", Commodity.DEFAULT_COMMODITY.currencyCode),
            baseAccount.uid
        )
        split.type = TransactionType.DEBIT
        transaction.addSplit(split)
        transaction.addSplit(split.createPair(transferAccount.uid))
        transactionsDbAdapter.addRecord(transaction)
        // We set the date directly in the database as the corresponding field
        // is ignored when the object is stored. It's set through a trigger instead.
        setTransactionInDbTimestamp(
            transaction.uid,
            Timestamp(LocalDateTime.now().minusDays(9).toDate().time)
        )

        val bookUID = GnuCashApplication.activeBookUID
        assertThat(bookUID).isNotNull()
        val backupFolder = File(Exporter.getExportFolderPath(context, bookUID!!))
        assertThat(backupFolder).exists()
        assertThat(backupFolder.listFiles()).isEmpty()

        ScheduledActionService.processScheduledAction(dbHolder, scheduledBackup)

        assertThat(scheduledBackup.executionCount).isOne()
        assertThat(scheduledBackup.lastRunTime).isGreaterThanOrEqualTo(previousLastRun)
        val files = backupFolder.listFiles()
        assertThat(files).isNotNull()
        assertThat(files).isEmpty()
    }

    /**
     * Sets the transaction timestamp directly in the database.
     *
     * @param transactionUID UID of the transaction to set the timestamp.
     * @param timestamp      the new timestamp.
     */
    private fun setTransactionInDbTimestamp(transactionUID: String, timestamp: Timestamp) {
        val values = ContentValues()
        values[TransactionEntry.COLUMN_TIMESTAMP] = timestamp.time
        transactionsDbAdapter.updateTransaction(
            values, TransactionEntry.COLUMN_UID + "=?",
            arrayOf(transactionUID)
        )
    }

    /**
     * Tests that an scheduled backup includes transactions added or modified
     * after the last run.
     */
    @Test
    fun scheduledBackups_shouldIncludeTransactionsAfterTheLastRun() {
        val scheduledBackup = ScheduledAction(ScheduledAction.ActionType.BACKUP)
        scheduledBackup.actionUID = GnuCashApplication.activeBookUID
        scheduledBackup.startTime = LocalDateTime.now().minusDays(15).toDate().time
        scheduledBackup.lastRunTime = LocalDateTime.now().minusDays(8).toDate().time
        val previousLastRun = scheduledBackup.lastRunTime
        scheduledBackup.executionCount = 1
        val recurrence = Recurrence(PeriodType.WEEK)
        recurrence.multiplier = 1
        recurrence.byDays = listOf(Calendar.FRIDAY)
        scheduledBackup.setRecurrence(recurrence)
        val backupParams = ExportParams(ExportFormat.QIF)
        backupParams.exportTarget = ExportParams.ExportTarget.SD_CARD
        backupParams.exportStartTime = Timestamp(scheduledBackup.startTime)
        scheduledBackup.tag = backupParams.toTag()

        val transaction = Transaction("Orient palace")
        val split = Split(
            Money("10", Commodity.DEFAULT_COMMODITY.currencyCode),
            baseAccount.uid
        )
        split.type = TransactionType.DEBIT
        transaction.addSplit(split)
        transaction.addSplit(split.createPair(transferAccount.uid))
        transactionsDbAdapter.addRecord(transaction)

        val bookUID = GnuCashApplication.activeBookUID
        assertThat(bookUID).isNotNull()
        val backupFolder = File(Exporter.getExportFolderPath(context, bookUID!!))
        assertThat(backupFolder).exists()
        assertThat(backupFolder.listFiles()).isEmpty()

        ScheduledActionService.processScheduledAction(dbHolder, scheduledBackup)

        assertThat(scheduledBackup.executionCount).isEqualTo(2)
        assertThat(scheduledBackup.lastRunTime).isGreaterThanOrEqualTo(previousLastRun)
        val files = backupFolder.listFiles()
        assertThat(files!!).isNotNull()
        assertThat(files).hasSize(1)
        assertThat(files[0]).isNotNull()
        assertThat(files[0].name).endsWith(".qif")
    }
}
