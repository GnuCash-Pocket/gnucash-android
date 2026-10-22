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
import android.text.format.DateUtils
import org.assertj.core.api.Assertions.assertThat
import org.gnucash.android.app.GnuCashApplication
import org.gnucash.android.db.DatabaseSchema.TransactionEntry
import org.gnucash.android.db.adapter.AccountsDbAdapter
import org.gnucash.android.db.adapter.ScheduledActionDbAdapter
import org.gnucash.android.db.adapter.TransactionsDbAdapter
import org.gnucash.android.db.toTimestamp
import org.gnucash.android.export.ExportFormat
import org.gnucash.android.export.ExportParams
import org.gnucash.android.export.Exporter
import org.gnucash.android.export.xml.GncXmlHelper.parseDateTime
import org.gnucash.android.model.Account
import org.gnucash.android.model.AccountType
import org.gnucash.android.model.Commodity
import org.gnucash.android.model.Money
import org.gnucash.android.model.PeriodType
import org.gnucash.android.model.Recurrence
import org.gnucash.android.model.ScheduledAction
import org.gnucash.android.model.Split
import org.gnucash.android.model.Transaction
import org.gnucash.android.model.TransactionType
import org.gnucash.android.test.unit.BookHelperTest
import org.gnucash.android.test.unit.importer.GncXmlHandlerTest.Companion.testCommon1
import org.gnucash.android.ui.util.RecurrenceParser
import org.gnucash.android.util.TimestampHelper.getUtcStringFromTimestamp
import org.gnucash.android.util.set
import org.gnucash.android.util.toMillis
import org.joda.time.DateTime
import org.joda.time.DateTimeConstants
import org.joda.time.LocalDateTime
import org.joda.time.Weeks
import org.junit.Before
import org.junit.Test
import timber.log.Timber
import java.io.File
import java.math.BigDecimal
import java.util.Calendar
import java.util.TimeZone

/**
 * Test the scheduled actions service runs as expected
 */
class ScheduledActionServiceTest : BookHelperTest() {
    private var actionUID: String? = null

    private val baseAccount = Account("Base Account")
    private val transferAccount = Account("Transfer Account")

    @Before
    override fun setUp() {
        super.setUp()
        baseAccount.commodity = Commodity.DEFAULT_COMMODITY
        transferAccount.commodity = Commodity.DEFAULT_COMMODITY

        val templateTransaction = Transaction("Recurring Transaction")
        templateTransaction.commodity = Commodity.DEFAULT_COMMODITY
        templateTransaction.isTemplate = true

        val split1 = Split(Money(BigDecimal.TEN, Commodity.DEFAULT_COMMODITY), baseAccount)
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

    @Test
    fun disabledScheduledActions_shouldNotRun() {
        val recurrence = Recurrence(PeriodType.WEEK)
        val scheduledAction1 = ScheduledAction(ScheduledAction.ActionType.TRANSACTION) {
            startDate = System.currentTimeMillis() - DateUtils.HOUR_IN_MILLIS
            isEnabled = false
            actionUID = this@ScheduledActionServiceTest.actionUID
            setRecurrence(recurrence)
        }

        assertThat(transactionsDbAdapter.recordsCount).isZero()
        ScheduledActionService.processScheduledAction(dbHolder, scheduledAction1)
        assertThat(transactionsDbAdapter.recordsCount).isZero()
    }

    @Test
    fun futureScheduledActions_shouldNotRun() {
        val scheduledAction = ScheduledAction(ScheduledAction.ActionType.TRANSACTION) {
            startDate = System.currentTimeMillis() + DateUtils.HOUR_IN_MILLIS
            isEnabled = true
            setRecurrence(Recurrence(PeriodType.MONTH))
            actionUID = this@ScheduledActionServiceTest.actionUID
        }

        assertThat(transactionsDbAdapter.recordsCount).isZero()
        ScheduledActionService.processScheduledAction(dbHolder, scheduledAction)
        assertThat(transactionsDbAdapter.recordsCount).isZero()
    }

    /**
     * Transactions whose execution count has reached or exceeded the planned execution count
     */
    @Test
    fun exceededExecutionCounts_shouldNotRun() {
        val scheduledAction = ScheduledAction(ScheduledAction.ActionType.TRANSACTION) {
            actionUID = this@ScheduledActionServiceTest.actionUID
            startDate = DateTime(2015, 5, 31, 14, 0).millis
            isEnabled = true
            setRecurrence(Recurrence(PeriodType.WEEK))
            totalPlannedExecutionCount = 4
            instanceCount = 4
        }

        assertThat(transactionsDbAdapter.recordsCount).isZero()
        ScheduledActionService.processScheduledAction(dbHolder, scheduledAction)
        assertThat(transactionsDbAdapter.recordsCount).isZero()
    }

    /**
     * Test that normal scheduled transactions would lead to new transaction entries
     */
    @Test
    fun missedScheduledTransactions_shouldBeGenerated() {
        val startTime = DateTime(2016, 6, 6, 9, 0)
        val endTime = DateTime(2016, 9, 12, 8, 0) //end just before last appointment
        val scheduledAction = ScheduledAction(ScheduledAction.ActionType.TRANSACTION) {
            startDate = startTime.millis
            endDate = endTime.millis
            instanceCount = 1
            actionUID = this@ScheduledActionServiceTest.actionUID
        }

        val recurrence = Recurrence(PeriodType.WEEK, 2).apply {
            byDays = listOf(Calendar.MONDAY)
        }
        scheduledAction.setRecurrence(recurrence)
        ScheduledActionDbAdapter.instance.insert(scheduledAction)

        assertThat(transactionsDbAdapter.recordsCount).isZero()

        ScheduledActionService.processScheduledAction(dbHolder, scheduledAction)

        assertThat(transactionsDbAdapter.recordsCount).isEqualTo(7)
    }

    fun endTimeInTheFuture_shouldExecuteOnlyUntilPresent() {
        val startTime = DateTime(2016, 6, 6, 9, 0)
        val scheduledAction = ScheduledAction(ScheduledAction.ActionType.TRANSACTION) {
            startDate = startTime.millis
            actionUID = this@ScheduledActionServiceTest.actionUID
        }

        scheduledAction.setRecurrence(PeriodType.WEEK, 2)
        scheduledAction.endDate = DateTime(2017, 8, 16, 9, 0).millis
        ScheduledActionDbAdapter.instance.insert(scheduledAction)

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
        val instanceCountInitial = 1
        val startTime = DateTime(2016, 6, 6, 9, 0)
        val scheduledAction = ScheduledAction(ScheduledAction.ActionType.TRANSACTION) {
            startDate = startTime.millis
            actionUID = this@ScheduledActionServiceTest.actionUID
            instanceCount = instanceCountInitial
            endDate = DateTime(2016, 8, 8, 9, 0).millis
        }

        val recurrence = Recurrence(PeriodType.WEEK, 2).apply {
            byDays = listOf(Calendar.MONDAY)
        }
        scheduledAction.setRecurrence(recurrence)
        ScheduledActionDbAdapter.instance.insert(scheduledAction)

        assertThat(transactionsDbAdapter.recordsCount).isZero()

        ScheduledActionService.processScheduledAction(dbHolder, scheduledAction)

        // Occurrences on Monday through the end date are:
        // 1. 2016-06-06
        // 2. 2016-06-20
        // 3. 2016-07-04
        // 4. 2016-07-18
        // 5. 2016-08-01
        val expectedCount = 5
        assertThat(scheduledAction.instanceCount).isEqualTo(instanceCountInitial + expectedCount)
        assertThat(transactionsDbAdapter.recordsCount)
            .isEqualTo(expectedCount.toLong()) //would be 6 if the end time is not respected
    }

    /**
     * Test that only scheduled actions with action UIDs are processed
     */
    @Test //(expected = IllegalArgumentException.class)
    fun recurringTransactions_shouldHaveScheduledActionUID() {
        val startTime = DateTime(2016, 7, 4, 12, 0)
        val scheduledAction = ScheduledAction(ScheduledAction.ActionType.TRANSACTION) {
            startDate = startTime.millis
            setRecurrence(PeriodType.MONTH, 1)
        }

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
        val bookUID = GnuCashApplication.activeBookUID
        val now = LocalDateTime.now()
        val scheduledBackup = ScheduledAction(ScheduledAction.ActionType.EXPORT) {
            actionUID = bookUID
            startDate = now.minusMonths(4).minusDays(2).toMillis()
            lastRunDate = now.minusMonths(2).toMillis()
            instanceCount = 2
            setRecurrence(PeriodType.MONTH, 1)
            val backupParams = ExportParams(ExportFormat.XML)
            setExportParams(backupParams)
        }
        var previousLastRun = scheduledBackup.lastRunDate

        // Check there's not a backup for each missed run
        assertThat(bookUID).isNotNull()
        val backupFolder = File(Exporter.getExportFolderPath(context, bookUID!!))
        assertThat(backupFolder).exists()
        assertThat(backupFolder.listFiles()).isEmpty()

        // Check there's not a backup for each missed run
        ScheduledActionService.processScheduledAction(dbHolder, scheduledBackup)
        assertThat(scheduledBackup.instanceCount).isEqualTo(3)
        assertThat(scheduledBackup.lastRunDate).isGreaterThanOrEqualTo(previousLastRun)
        var backupFiles = backupFolder.listFiles()
        assertThat(backupFiles!!).hasSize(1)
        assertThat(backupFiles[0]).exists().hasExtension("xac")

        // Check also across service runs
        previousLastRun = scheduledBackup.lastRunDate
        ScheduledActionService.processScheduledAction(dbHolder, scheduledBackup)
        assertThat(scheduledBackup.instanceCount).isEqualTo(3)
        assertThat(scheduledBackup.lastRunDate).isGreaterThanOrEqualTo(previousLastRun)
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
        val bookUID = GnuCashApplication.activeBookUID
        val now = LocalDateTime.now()
        val scheduledBackup = ScheduledAction(ScheduledAction.ActionType.EXPORT) {
            actionUID = bookUID
            startDate = now.withDayOfWeek(DateTimeConstants.WEDNESDAY).toMillis()
            lastRunDate = startDate
            instanceCount = 1
            val recurrence = Recurrence(PeriodType.WEEK).apply {
                byDays = listOf(Calendar.MONDAY)
            }
            setRecurrence(recurrence)
            val backupParams = ExportParams(ExportFormat.QIF).apply {
                exportStartTime = startDate.toTimestamp()
            }
            setExportParams(backupParams)
        }
        val previousLastRun = scheduledBackup.lastRunDate

        val backupParams = ExportParams(ExportFormat.XML)
        scheduledBackup.setExportParams(backupParams)

        assertThat(bookUID).isNotNull()
        val backupFolder = File(Exporter.getExportFolderPath(context, bookUID!!))
        assertThat(backupFolder).exists()
        assertThat(backupFolder.listFiles()).isEmpty()

        ScheduledActionService.processScheduledAction(dbHolder, scheduledBackup)

        assertThat(scheduledBackup.instanceCount).isOne
        assertThat(scheduledBackup.lastRunDate).isEqualTo(previousLastRun)
        assertThat(backupFolder.listFiles()).isEmpty()
    }

    /**
     * Tests that a scheduled QIF backup isn't done when no transactions have
     * been added or modified after the last run.
     */
    @Test
    fun scheduledBackups_shouldNotIncludeTransactionsPreviousToTheLastRun() {
        val bookUID = GnuCashApplication.activeBookUID
        val now = LocalDateTime.now()
        val scheduledBackup = ScheduledAction(ScheduledAction.ActionType.EXPORT) {
            actionUID = bookUID
            startDate = now.minusDays(15).toMillis()
            lastRunDate = now.minusDays(8).toMillis()
            instanceCount = 1
            val recurrence = Recurrence(PeriodType.WEEK).apply {
                byDays = listOf(Calendar.WEDNESDAY)
            }
            setRecurrence(recurrence)
            val backupParams = ExportParams(ExportFormat.QIF).apply {
                exportStartTime = startDate.toTimestamp()
            }
            setExportParams(backupParams)
        }
        val previousLastRun = scheduledBackup.lastRunDate

        // Create a transaction with a modified date previous to the last run
        val transaction = Transaction("Tandoori express")
        val split = Split(
            Money("10", Commodity.DEFAULT_COMMODITY),
            baseAccount.uid
        )
        transaction.addSplit(split)
        transaction.addSplit(split.createPair(transferAccount))
        transactionsDbAdapter.addRecord(transaction)
        // We set the date directly in the database as the corresponding field
        // is ignored when the object is stored. It's set through a trigger instead.
        setTransactionInDbTimestamp(
            transaction.uid,
            now.minusDays(9).toMillis()
        )

        assertThat(bookUID).isNotNull()
        assertThat(bookUID).isEqualTo(dbHolder.name)
        val backupFolder = File(Exporter.getExportFolderPath(context, bookUID!!))
        assertThat(backupFolder).exists()
        assertThat(backupFolder.listFiles()).isEmpty()

        ScheduledActionService.processScheduledAction(dbHolder, scheduledBackup)

        assertThat(scheduledBackup.instanceCount).isOne()
        assertThat(scheduledBackup.lastRunDate).isGreaterThanOrEqualTo(previousLastRun)
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
    private fun setTransactionInDbTimestamp(transactionUID: String, timestamp: Long) {
        val db = dbHolder.db
        db.execSQL("DROP TRIGGER IF EXISTS update_time_trigger_" + TransactionEntry.TABLE_NAME)

        val values = ContentValues()
        values[TransactionEntry.COLUMN_MODIFIED_AT] = getUtcStringFromTimestamp(timestamp)
        transactionsDbAdapter.updateTransaction(
            values,
            TransactionEntry.COLUMN_UID + "=?",
            arrayOf(transactionUID)
        )
    }

    /**
     * Tests that a scheduled backup includes transactions added or modified
     * after the last run.
     */
    @Test
    fun scheduledBackups_shouldIncludeTransactionsAfterTheLastRun() {
        val bookUID = GnuCashApplication.activeBookUID
        val now = LocalDateTime.now().withDayOfWeek(DateTimeConstants.WEDNESDAY)
        val scheduledBackup = ScheduledAction(ScheduledAction.ActionType.EXPORT) {
            actionUID = bookUID
            startDate = now.minusDays(15).toMillis()
            lastRunDate = now.minusDays(8).toMillis()
            instanceCount = 1
            val recurrence = Recurrence(PeriodType.WEEK).apply {
                byDays = listOf(Calendar.MONDAY)
            }
            setRecurrence(recurrence)
            val backupParams = ExportParams(ExportFormat.QIF).apply {
                exportStartTime = startDate.toTimestamp()
            }
            setExportParams(backupParams)
        }
        val previousLastRun = scheduledBackup.lastRunDate

        // Add a fresh transaction for exporting.
        val transaction = Transaction("Orient palace")
        val split = Split(
            Money("10", Commodity.DEFAULT_COMMODITY),
            baseAccount.uid
        )
        transaction.addSplit(split)
        transaction.addSplit(split.createPair(transferAccount))
        transactionsDbAdapter.addRecord(transaction)

        assertThat(bookUID).isNotNull()
        val backupFolder = File(Exporter.getExportFolderPath(context, bookUID!!))
        assertThat(backupFolder).exists()
        assertThat(backupFolder.listFiles()).isEmpty()

        ScheduledActionService.processScheduledAction(dbHolder, scheduledBackup)

        assertThat(scheduledBackup.instanceCount).isEqualTo(2)
        assertThat(scheduledBackup.lastRunDate).isGreaterThanOrEqualTo(previousLastRun)
        val files = backupFolder.listFiles()
        assertThat(files!!).isNotNull()
        assertThat(files).hasSize(1)
        assertThat(files[0]).isNotNull()
        assertThat(files[0].name).endsWith(".qif")
    }

    @Test
    fun `simple accounts with 1 of each type - once`() {
        val bookUID = importGnuCashXml("simpleScheduledTransactionImport.xml")
        assertSimpleScheduledTransactionImport(bookUID)
        val actions = scheduledActionDbAdapter.allRecords
        val scheduledAction = actions[0]
        assertThat(scheduledAction.uid).isEqualTo("9def659b35e85b09fe2bfade35053487")
        assertThat(scheduledAction.instanceCount).isOne

        scheduledAction.setRecurrence(PeriodType.ONCE, 1)
        scheduledAction.instanceCount = 1
        ScheduledActionService.processScheduledAction(dbHolder, scheduledAction)

        assertThat(scheduledAction.instanceCount).isEqualTo(2)
    }

    @Test
    fun `simple accounts with 1 of each type - 1 month`() {
        val bookUID = importGnuCashXml("simpleScheduledTransactionImport.xml")
        assertSimpleScheduledTransactionImport(bookUID)
        val actions = scheduledActionDbAdapter.allRecords
        val scheduledAction = actions[0]

        // 2016-09-24 to 2016-10-27
        // 1 month from the start date
        val endDate = Calendar.getInstance().apply {
            set(Calendar.YEAR, 2016)
            set(Calendar.MONTH, Calendar.OCTOBER)
            set(Calendar.DAY_OF_MONTH, 27)
        }
        scheduledAction.endDate = endDate.timeInMillis
        ScheduledActionService.processScheduledAction(dbHolder, scheduledAction)

        // Instances are only posted for 2016-09-24 and 2016-10-24 => instance count = 1 + 2
        assertThat(scheduledAction.instanceCount).isEqualTo(3)
    }

    @Test
    fun `simple accounts with 1 of each type - 109 months`() {
        val bookUID = importGnuCashXml("simpleScheduledTransactionImport.xml")
        assertSimpleScheduledTransactionImport(bookUID)
        val actions = scheduledActionDbAdapter.allRecords
        val scheduledAction = actions[0]

        // 2016-09-24 to 2025-10-27
        // 109 months from the start date to the end date
        val endDate = Calendar.getInstance().apply {
            timeInMillis = scheduledAction.startDate
            add(Calendar.MONTH, 109)
            add(Calendar.DAY_OF_MONTH, 3)
        }
        scheduledAction.endDate = endDate.timeInMillis
        ScheduledActionService.processScheduledAction(dbHolder, scheduledAction)

        // Instances are only posted from 2016-09-24 to 2025-10-24 => instance count = 1 + 110
        assertThat(scheduledAction.instanceCount).isEqualTo(111)
    }

    private fun assertSimpleScheduledTransactionImport(bookUID: String) {
        assertThat(bookUID).isEqualTo("fb0911dd508266db9446bc605edad3e4")
        assertThat(bookUID).isEqualTo(dbHolder.name)

        val actions = scheduledActionDbAdapter.allRecords
        assertThat(actions).hasSize(1)

        val scheduledAction = actions[0]
        assertThat(scheduledAction.uid).isEqualTo("9def659b35e85b09fe2bfade35053487")
        assertThat(scheduledAction.actionUID).isEqualTo("b645bef06d0844aece6424ceeec03983")
        assertThat(scheduledAction.templateAccountUID).isEqualTo("2e9b02b5ed6fb07c7d4536bb8a03599e")
        assertThat(scheduledAction.name).isEqualTo("Los pollos hermanos - monthly")
        assertThat(scheduledAction.isEnabled).isTrue()
        assertThat(scheduledAction.instanceCount).isOne()

        val recurrence = scheduledAction.recurrence
        assertThat(recurrence).isNotNull()
        assertThat(recurrence.multiplier).isOne()
        assertThat(recurrence.periodType).isEqualTo(PeriodType.MONTH)
        val startDate = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            set(Calendar.YEAR, 2016)
            set(Calendar.MONTH, Calendar.SEPTEMBER)
            set(Calendar.DAY_OF_MONTH, 24)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        assertThat(recurrence.periodStart).isEqualTo(startDate.timeInMillis)

        val account = accountsDbAdapter.getRecord(scheduledAction.templateAccountUID)
        assertThat(account).isNotNull()
        assertThat(account.uid).isEqualTo("2e9b02b5ed6fb07c7d4536bb8a03599e")
        assertThat(account.name).isEqualTo("9def659b35e85b09fe2bfade35053487")
        assertThat(account.type).isEqualTo(AccountType.BANK)
        assertThat(account.isTemplate).isTrue()
        assertThat(account.commodity).isEqualTo(Commodity.template)

        val transactions =
            transactionsDbAdapter.getTransactionsForAccount(scheduledAction.templateAccountUID)
        assertThat(transactions).hasSize(1)
        val transaction = transactions[0]
        assertThat(transaction).isNotNull()
        assertThat(transaction.commodity).isEqualTo(Commodity.USD)
        assertThat(transaction.description).isEqualTo("Los pollos hermanos")
        assertThat(transaction.notes).isEmpty()
        assertThat(transaction.isExported).isTrue()
        assertThat(transaction.isTemplate).isTrue()
        assertThat(transaction.datePosted).isEqualTo(parseDateTime("2016-08-24 10:00:00 +0200"))
        assertThat(transaction.createdTimestamp.time).isEqualTo(parseDateTime("2016-08-24 19:50:15 +0200"))
        assertThat(transaction.scheduledActionUID).isEqualTo("9def659b35e85b09fe2bfade35053487")

        // Check splits
        assertThat(transaction.splits).hasSize(2)

        val splitDebit = transaction.splits[0]
        assertThat(splitDebit.type).isEqualTo(TransactionType.DEBIT)
        assertThat(splitDebit.uid).isEqualTo("f66794ef262aac3ae085ecc3030f2769")
        assertThat(splitDebit.transactionUID).isEqualTo(transaction.uid)
        assertThat(splitDebit.accountUID).isEqualTo(account.uid)
        assertThat(splitDebit.scheduledActionAccountUID).isEqualTo("6a7cf8267314992bdddcee56d71a3908")
        assertThat(splitDebit.memo).isEmpty()
        assertThat(splitDebit.value).isEqualTo(Money(20.00, Commodity.USD))

        val splitCredit = transaction.splits[1]
        assertThat(splitCredit.type).isEqualTo(TransactionType.CREDIT)
        assertThat(splitCredit.uid).isEqualTo("57e2be6ca6b568f8f7c9b2e455e1e21f")
        assertThat(splitCredit.transactionUID).isEqualTo(transaction.uid)
        assertThat(splitCredit.accountUID).isEqualTo(account.uid)
        assertThat(splitCredit.scheduledActionAccountUID).isEqualTo("dae686a1636addc0dae1ae670701aa4a")
        assertThat(splitCredit.memo).isEmpty()
        assertThat(splitCredit.value).isEqualTo(Money(20.00, Commodity.USD))

        assertThat(splitDebit.isPairOf(splitCredit)).isTrue()
    }

    @Test
    fun `common accounts with 1 of each type - schedule`() {
        val bookUID = importGnuCashXml("common_1.gnucash")
        testCommon1(
            bookUID,
            accountsDbAdapter,
            booksDbAdapter,
            budgetsDbAdapter,
            commoditiesDbAdapter,
            pricesDbAdapter,
            recurrenceDbAdapter,
            scheduledActionDbAdapter,
            transactionsDbAdapter,
        )

        val actions = scheduledActionDbAdapter.allRecords
        val scheduledAction = actions[0]
        assertThat(scheduledAction.uid).isEqualTo("11d621073ed745debd5027325d7853a4")
        assertThat(scheduledAction.instanceCount).isOne
        assertThat(scheduledAction.isEnabled).isTrue
        assertThat(scheduledAction.isAutoCreate).isFalse
        assertThat(scheduledAction.isAutoCreateNotify).isFalse
        val recurrence = scheduledAction.recurrence
        assertThat(recurrence.periodType).isEqualTo(PeriodType.END_OF_MONTH)
        assertThat(recurrence.periodStart).isEqualTo(1748649600000L)

        ScheduledActionService.processScheduledAction(dbHolder, scheduledAction)
        val count = scheduledAction.instanceCount - 1
        assertThat(count).isGreaterThanOrEqualTo(15)

        // 4+ regular transactions + 1 template transaction
        assertThat(transactionsDbAdapter.recordsCount).isEqualTo(3L + count)

        val transactions = transactionsDbAdapter.allRecords
        val transaction = transactions[transactions.lastIndex]
        assertThat(transaction.description).isEqualTo("AT&T")
        val splits = transaction.splits
        assertThat(splits[0].type).isEqualTo(TransactionType.DEBIT)
        assertThat(splits[0].accountUID).isEqualTo("84e8882f38514ee8b9c7ad6ede968a8b")
        assertThat(splits[0].value.toDouble()).isEqualTo(99.90)
        assertThat(splits[0].quantity.toDouble()).isEqualTo(99.90)
        assertThat(splits[1].type).isEqualTo(TransactionType.CREDIT)
        assertThat(splits[1].accountUID).isEqualTo("64eaf21b57a04b9d8ebe7455db238b89")
        assertThat(splits[1].value.toDouble()).isEqualTo(99.90)
        assertThat(splits[1].quantity.toDouble()).isEqualTo(99.90)
    }

    @Test
    fun `weekly actions with day set should be finite`() {
        assertThat(transactionsDbAdapter.allRecords).hasSize(1) // 1 template

        val startDate = DateTime(2026, 9, 1, 9, 0)
        val endDate = DateTime(2026, 9, 16, 10, 0)

        val recurrence = Recurrence(PeriodType.WEEK)
        recurrence.byDays = listOf(Calendar.WEDNESDAY)
        val scheduledAction = ScheduledAction(ScheduledAction.ActionType.TRANSACTION) {
            setRecurrence(recurrence)
            instanceCount = 1
            this.startDate = startDate.millis
            this.endDate = endDate.millis
            actionUID = this@ScheduledActionServiceTest.actionUID
        }

        // 1. 2026-09-02
        // 2. 2026-09-09
        // 3. 2026-09-16
        ScheduledActionService.processScheduledAction(dbHolder, scheduledAction)
        assertThat(scheduledAction.instanceCount).isEqualTo(4L)
        assertThat(transactionsDbAdapter.allRecords).hasSize(4) // 1 template + 3 regular
    }

    @Test
    fun `monthly action for fixed count should not be executed more than the count`() {
        val templateUID = actionUID
        val rrule = "FREQ=MONTHLY;COUNT=5;WKST=SU"
        val recurrence = RecurrenceParser.parse(rrule)
        val scheduledAction = ScheduledAction(ScheduledAction.ActionType.TRANSACTION) {
            setRecurrence(recurrence)
            instanceCount = 1
            startDate = LocalDateTime(2026, 1, 1, 12, 30, 0).toMillis()
            actionUID = templateUID
        }
        assertThat(scheduledAction.totalPlannedExecutionCount).isEqualTo(5)
        ScheduledActionService.processScheduledAction(dbHolder, scheduledAction)
        assertThat(scheduledAction.instanceCount).isEqualTo(6L)
        ScheduledActionService.processScheduledAction(dbHolder, scheduledAction)
        assertThat(scheduledAction.instanceCount).isEqualTo(6L)
    }
}
