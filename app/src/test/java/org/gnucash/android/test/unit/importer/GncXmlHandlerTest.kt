/*
 * Copyright (c) 2016 Àlex Magaz Graça <rivaldi8@gmail.com>
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
package org.gnucash.android.test.unit.importer

import android.database.DatabaseUtils.queryNumEntries
import org.assertj.core.api.Assertions.assertThat
import org.gnucash.android.db.DatabaseSchema.TransactionEntry
import org.gnucash.android.db.adapter.AccountsDbAdapter
import org.gnucash.android.db.adapter.BooksDbAdapter
import org.gnucash.android.db.adapter.BudgetsDbAdapter
import org.gnucash.android.db.adapter.CommoditiesDbAdapter
import org.gnucash.android.db.adapter.PricesDbAdapter
import org.gnucash.android.db.adapter.RecurrenceDbAdapter
import org.gnucash.android.db.adapter.ScheduledActionDbAdapter
import org.gnucash.android.db.adapter.TransactionsDbAdapter
import org.gnucash.android.export.xml.GncXmlHelper.formatDate
import org.gnucash.android.export.xml.GncXmlHelper.parseDateTime
import org.gnucash.android.model.Account
import org.gnucash.android.model.AccountType
import org.gnucash.android.model.Money
import org.gnucash.android.model.PeriodType
import org.gnucash.android.model.Price
import org.gnucash.android.model.Split
import org.gnucash.android.model.Transaction
import org.gnucash.android.model.TransactionType
import org.gnucash.android.test.unit.BookHelperTest
import org.gnucash.android.util.TimestampHelper
import org.gnucash.android.util.toMillis
import org.joda.time.DateTimeZone
import org.joda.time.LocalDate
import org.junit.Test
import java.util.Calendar

/**
 * Imports GnuCash XML files and checks the objects defined in them are imported correctly.
 */
class GncXmlHandlerTest : BookHelperTest() {
    /**
     * Tests basic accounts import.
     *
     * Checks hierarchy and attributes. We should have:
     * <pre>
     * Root
     * |_ Assets
     * |   |_ Cash in wallet
     * |_ Expenses
     * |_ Dining
    </pre> *
     */
    @Test
    fun accountsImport() {
        val bookUID = importGnuCashXml("accountsImport.xml")
        assertThat(BooksDbAdapter.isBookDatabase(bookUID)).isTrue()

        assertThat(accountsDbAdapter.recordsCount).isEqualTo(5) // 4 accounts + root

        val rootAccount = accountsDbAdapter.getRecord("308ade8cf0be2b0b05c5eec3114a65fa")
        assertThat(rootAccount.parentUID).isNull()
        assertThat(rootAccount.name).isEqualTo(AccountsDbAdapter.ROOT_ACCOUNT_NAME)
        assertThat(rootAccount.isHidden).isFalse()
        assertThat(rootAccount.isPlaceholder).isFalse()

        val assetsAccount = accountsDbAdapter.getRecord("3f44d61cb1afd201e8ea5a54ec4fbbff")
        assertThat(assetsAccount.parentUID).isEqualTo(rootAccount.uid)
        assertThat(assetsAccount.name).isEqualTo("Assets")
        assertThat(assetsAccount.isHidden).isFalse()
        assertThat(assetsAccount.isPlaceholder).isTrue()
        assertThat(assetsAccount.type).isEqualTo(AccountType.ASSET)

        val diningAccount = accountsDbAdapter.getRecord("6a7cf8267314992bdddcee56d71a3908")
        assertThat(diningAccount.parentUID).isEqualTo("9b607f63aecb1a175556676904432365")
        assertThat(diningAccount.name).isEqualTo("Dining")
        assertThat(diningAccount.description).isEqualTo("Dining")
        assertThat(diningAccount.isHidden).isFalse()
        assertThat(diningAccount.isPlaceholder).isFalse()
        assertThat(diningAccount.isFavorite).isFalse()
        assertThat(diningAccount.type).isEqualTo(AccountType.EXPENSE)
        assertThat(diningAccount.commodity.currencyCode).isEqualTo("USD")
        assertThat(diningAccount.color).isEqualTo(Account.DEFAULT_COLOR)
        assertThat(diningAccount.defaultTransferAccountUID).isNull()
    }

    /**
     * Tests importing a simple transaction with default splits.
     */
    @Test
    fun simpleTransactionImport() {
        val bookUID = importGnuCashXml("simpleTransactionImport.xml")
        assertThat(BooksDbAdapter.isBookDatabase(bookUID)).isTrue()

        assertThat(transactionsDbAdapter.recordsCount).isOne()

        val transaction = transactionsDbAdapter.getRecord("b33c8a6160494417558fd143731fc26a")

        // Check attributes
        assertThat(transaction.description).isEqualTo("Kahuna Burger")
        assertThat(transaction.commodity.currencyCode).isEqualTo("USD")
        assertThat(transaction.notes).isEqualTo("")
        assertThat(transaction.scheduledActionUID).isNull()
        assertThat(transaction.isExported).isTrue()
        assertThat(transaction.isTemplate).isFalse()
        assertThat(transaction.datePosted).isEqualTo(parseDateTime("2016-08-23 10:00:00 +0200"))
        assertThat(transaction.dateEntered).isEqualTo(parseDateTime("2016-08-23 12:44:19 +0200"))

        // Check splits
        assertThat(transaction.splits).hasSize(2)

        val split1 = transaction.splits[0]
        assertThat(split1.uid).isEqualTo("ad2cbc774fc4e71885d17e6932448e8e")
        assertThat(split1.accountUID).isEqualTo("6a7cf8267314992bdddcee56d71a3908")
        assertThat(split1.transactionUID).isEqualTo("b33c8a6160494417558fd143731fc26a")
        assertThat(split1.type).isEqualTo(TransactionType.DEBIT)
        assertThat(split1.memo).isEmpty()
        assertThat(split1.value).isEqualTo(Money("10", "USD"))
        assertThat(split1.quantity).isEqualTo(Money("10", "USD"))
        assertThat(split1.reconcileState).isEqualTo(Split.FLAG_NOT_RECONCILED)

        val split2 = transaction.splits[1]
        assertThat(split2.uid).isEqualTo("61d4d604bc00a59cabff4e8875d00bee")
        assertThat(split2.accountUID).isEqualTo("dae686a1636addc0dae1ae670701aa4a")
        assertThat(split2.transactionUID).isEqualTo("b33c8a6160494417558fd143731fc26a")
        assertThat(split2.type).isEqualTo(TransactionType.CREDIT)
        assertThat(split2.memo).isEmpty()
        assertThat(split2.value).isEqualTo(Money("10", "USD"))
        assertThat(split2.quantity).isEqualTo(Money("10", "USD"))
        assertThat(split2.reconcileState).isEqualTo(Split.FLAG_NOT_RECONCILED)
        assertThat(split2.isPairOf(split1)).isTrue()
    }

    /**
     * Tests importing a transaction with non-default splits.
     */
    @Test
    fun transactionWithNonDefaultSplitsImport() {
        val bookUID = importGnuCashXml("transactionWithNonDefaultSplitsImport.xml")
        assertThat(BooksDbAdapter.isBookDatabase(bookUID)).isTrue()

        assertThat(transactionsDbAdapter.recordsCount).isOne()

        val transaction = transactionsDbAdapter.getRecord("042ff745a80e94e6237fb0549f6d32ae")

        // Ensure it's the correct one
        assertThat(transaction.description).isEqualTo("Tandoori Mahal")

        // Check splits
        assertThat(transaction.splits).hasSize(3)

        val splitExpense = transaction.splits[0]
        assertThat(splitExpense.uid).isEqualTo("c50cce06e2bf9085730821c82d0b36ca")
        assertThat(splitExpense.accountUID).isEqualTo("6a7cf8267314992bdddcee56d71a3908")
        assertThat(splitExpense.transactionUID).isEqualTo("042ff745a80e94e6237fb0549f6d32ae")
        assertThat(splitExpense.type).isEqualTo(TransactionType.DEBIT)
        assertThat(splitExpense.memo).isEmpty()
        assertThat(splitExpense.value).isEqualTo(Money("50", "USD"))
        assertThat(splitExpense.quantity).isEqualTo(Money("50", "USD"))

        val splitAsset1 = transaction.splits[1]
        assertThat(splitAsset1.uid).isEqualTo("4930f412665a705eedba39789b6c3a35")
        assertThat(splitAsset1.accountUID).isEqualTo("dae686a1636addc0dae1ae670701aa4a")
        assertThat(splitAsset1.transactionUID).isEqualTo("042ff745a80e94e6237fb0549f6d32ae")
        assertThat(splitAsset1.type).isEqualTo(TransactionType.CREDIT)
        assertThat(splitAsset1.memo).isEqualTo("tip")
        assertThat(splitAsset1.value).isEqualTo(Money("5", "USD"))
        assertThat(splitAsset1.quantity).isEqualTo(Money("5", "USD"))
        assertThat(splitAsset1.isPairOf(splitExpense)).isFalse()

        val splitAsset2 = transaction.splits[2]
        assertThat(splitAsset2.uid).isEqualTo("b97cd9bbaa17f181d0a5b39b260dabda")
        assertThat(splitAsset2.accountUID).isEqualTo("ee139a5658a0d37507dc26284798e347")
        assertThat(splitAsset2.transactionUID).isEqualTo("042ff745a80e94e6237fb0549f6d32ae")
        assertThat(splitAsset2.type).isEqualTo(TransactionType.CREDIT)
        assertThat(splitAsset2.memo).isEmpty()
        assertThat(splitAsset2.value).isEqualTo(Money("45", "USD"))
        assertThat(splitAsset2.quantity).isEqualTo(Money("45", "USD"))
        assertThat(splitAsset2.isPairOf(splitExpense)).isFalse()
    }

    /**
     * Tests importing a transaction with multiple currencies.
     */
    @Test
    fun multiCurrencyTransactionImport() {
        val bookUID = importGnuCashXml("multiCurrencyTransactionImport.xml")
        assertThat(BooksDbAdapter.isBookDatabase(bookUID)).isTrue()

        assertThat(transactionsDbAdapter.recordsCount).isOne()

        val transaction = transactionsDbAdapter.getRecord("ded49386f8ea319ccaee043ba062b3e1")

        // Ensure it's the correct one
        assertThat(transaction.description).isEqualTo("Salad express")
        assertThat(transaction.commodity.currencyCode).isEqualTo("USD")
        assertThat(transaction.commodity.smallestFraction).isEqualTo(100)

        // Check splits
        assertThat(transaction.splits).hasSize(2)

        val splitDebit = transaction.splits[0]
        assertThat(splitDebit.uid).isEqualTo("88bbbbac7689a8657b04427f8117a783")
        assertThat(splitDebit.accountUID).isEqualTo("6a7cf8267314992bdddcee56d71a3908")
        assertThat(splitDebit.transactionUID).isEqualTo("ded49386f8ea319ccaee043ba062b3e1")
        assertThat(splitDebit.type).isEqualTo(TransactionType.DEBIT)
        assertThat(splitDebit.value.numerator).isEqualTo(2000)
        assertThat(splitDebit.value.denominator).isEqualTo(100)
        assertThat(splitDebit.value).isEqualTo(Money("20", "USD"))
        assertThat(splitDebit.quantity.numerator).isEqualTo(2000)
        assertThat(splitDebit.quantity.denominator).isEqualTo(100)
        assertThat(splitDebit.quantity).isEqualTo(Money("20", "USD"))

        val splitCredit = transaction.splits[1]
        assertThat(splitCredit.uid).isEqualTo("e0dd885065bfe3c9ef63552fe84c6d23")
        assertThat(splitCredit.accountUID).isEqualTo("0469e915a22ba7846aca0e69f9f9b683")
        assertThat(splitCredit.transactionUID).isEqualTo("ded49386f8ea319ccaee043ba062b3e1")
        assertThat(splitCredit.type).isEqualTo(TransactionType.CREDIT)
        assertThat(splitCredit.value.numerator).isEqualTo(2000)
        assertThat(splitCredit.value.denominator).isEqualTo(100)
        assertThat(splitCredit.value).isEqualTo(Money("20", "USD"))
        assertThat(splitCredit.quantity.numerator).isEqualTo(1793)
        assertThat(splitCredit.quantity.denominator).isEqualTo(100)
        assertThat(splitCredit.quantity).isEqualTo(Money("17.93", "EUR"))
        assertThat(splitCredit.isPairOf(splitDebit)).isTrue()

        // Check prices
        assertThat(pricesDbAdapter.recordsCount).isOne()
        val price = pricesDbAdapter.getPriceForCurrencies("EUR", "USD")
        assertThat(price!!).isNotNull()
        assertThat(price.commodity.currencyCode).isEqualTo("EUR")
        assertThat(price.currency.currencyCode).isEqualTo("USD")
        assertThat(price.source).isEqualTo("Finance::Quote")
        assertThat(price.type).isEqualTo(Price.Type.Last)
        assertThat(price.valueNum).isEqualTo(11153L)
        assertThat(price.valueDenom).isEqualTo(10000L)
        assertThat(price.date).isEqualTo(parseDateTime("2016-09-18 20:23:55 +0200"))
    }

    /**
     * Tests that importing a weekly scheduled action sets the days of the
     * week of the recursion.
     */
    @Test
    fun importingScheduledAction_shouldSetByDays() {
        val bookUID = importGnuCashXml("importingScheduledAction_shouldSetByDays.xml")
        assertThat(BooksDbAdapter.isBookDatabase(bookUID)).isTrue()

        val scheduledTransaction =
            scheduledActionDbAdapter.getRecord("b5a13acb5a9459ebed10d06b75bbad10")

        // There are 3 byDays but, for now, getting one is enough to ensure it is executed
        assertThat(scheduledTransaction.recurrence.byDays).hasSizeGreaterThanOrEqualTo(1)

        // Until we implement parsing of days of the week for scheduled actions,
        // we'll just use the day of the week of the start time.
        val dayOfWeekFromByDays = scheduledTransaction.recurrence.byDays[0]
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = scheduledTransaction.startDate
        val dayOfWeekFromStartTime = calendar[Calendar.DAY_OF_WEEK]
        assertThat(dayOfWeekFromByDays).isEqualTo(dayOfWeekFromStartTime)
    }

    /**
     * Checks for bug 562 - Scheduled transaction imported with imbalanced splits.
     *
     *
     * Happens when an scheduled transaction is defined with both credit and
     * debit slots in each split.
     */
    @Test
    fun bug562_scheduledTransactionImportedWithImbalancedSplits() {
        val bookUID =
            importGnuCashXml("bug562_scheduledTransactionImportedWithImbalancedSplits.xml")
        assertThat(BooksDbAdapter.isBookDatabase(bookUID)).isTrue()

        assertThat(transactionsDbAdapter.templateTransactionsCount).isOne()

        val scheduledTransaction =
            transactionsDbAdapter.getRecord("b645bef06d0844aece6424ceeec03983")

        // Ensure it's the correct transaction
        assertThat(scheduledTransaction.description).isEqualTo("Los pollos hermanos")
        assertThat(scheduledTransaction.isTemplate).isTrue()
        assertThat(scheduledTransaction.commodity.currencyCode).isEqualTo("USD")

        // Check splits
        assertThat(scheduledTransaction.splits).hasSize(2)

        val amount = Money("20", "USD")
        val splitCredit = scheduledTransaction.splits[0]
        assertThat(splitCredit.accountUID).isEqualTo("2e9b02b5ed6fb07c7d4536bb8a03599e")
        assertThat(splitCredit.scheduledActionAccountUID).isEqualTo("6a7cf8267314992bdddcee56d71a3908")
        assertThat(splitCredit.type).isEqualTo(TransactionType.CREDIT)
        assertThat(splitCredit.value).isEqualTo(amount)
        assertThat(splitCredit.quantity.isAmountZero).isTrue()

        val splitDebit = scheduledTransaction.splits[1]
        assertThat(splitDebit.accountUID).isEqualTo("2e9b02b5ed6fb07c7d4536bb8a03599e")
        assertThat(splitDebit.scheduledActionAccountUID).isEqualTo("dae686a1636addc0dae1ae670701aa4a")
        assertThat(splitDebit.type).isEqualTo(TransactionType.DEBIT)
        assertThat(splitDebit.value).isEqualTo(amount)
        assertThat(splitDebit.quantity.isAmountZero).isTrue()
        assertThat(splitDebit.isPairOf(splitCredit)).isTrue()
    }

    @Test
    fun commodities() {
        val bookUID = importGnuCashXml("commodities.xml")
        assertThat(bookUID).isEqualTo("76d1839cfd30459998717d04ce719add")
        assertThat(BooksDbAdapter.isBookDatabase(bookUID)).isTrue()

        assertThat(commoditiesDbAdapter).isNotNull()
        val commodities = commoditiesDbAdapter.allRecords
        assertThat(commodities).isNotNull()
        assertThat(commodities.size).isGreaterThanOrEqualTo(3)

        val commodity1 = commodities.first { it.currencyCode == "APPS" }
        assertThat(commodity1).isNotNull()
        assertThat(commodity1.namespace).isEqualTo("NASDAQ")
        assertThat(commodity1.fullname).isEqualTo("Digital Turbine")
        assertThat(commodity1.smallestFraction).isEqualTo(10000)
        assertThat(commodity1.quoteFlag).isFalse()
        assertThat(commodity1.quoteSource).isNull()
        assertThat(commodity1.quoteTimeZone).isNull()

        val commodity2 = commodities.first { it.currencyCode == "QUAN_ELSS_TAX_KBGFAS" }
        assertThat(commodity2).isNotNull()
        assertThat(commodity2.namespace).isEqualTo("MF")
        assertThat(commodity2.fullname).isEqualTo("Quant ELSS Growth")
        assertThat(commodity2.smallestFraction).isEqualTo(10000)
        assertThat(commodity2.quoteFlag).isTrue()
        assertThat(commodity2.quoteSource).isEqualTo("googleweb")
        assertThat(commodity2.quoteTimeZone).isNull()
    }

    /**
     * Tests importing a transaction with multiple currencies that are scheduled.
     */
    @Test
    fun multiCurrencyTransactionScheduled() {
        val bookUID = importGnuCashXml("multiCurrencyTransactionSchedule.xml")
        assertThat(BooksDbAdapter.isBookDatabase(bookUID)).isTrue()

        assertThat(transactionsDbAdapter.recordsCount).isOne()
        // 1 regular + 1 template
        assertThat(queryNumEntries(dbHolder.db, TransactionEntry.TABLE_NAME, null, null))
            .isEqualTo(2)

        var transaction = transactionsDbAdapter.getRecord("ded49386f8ea319ccaee043ba062b3e1")

        // Ensure it's the correct one
        assertThat(transaction.description).isEqualTo("Salad express")
        assertThat(transaction.commodity.currencyCode).isEqualTo("USD")
        assertThat(transaction.commodity.smallestFraction).isEqualTo(100)

        // Check splits
        assertThat(transaction.splits.size).isEqualTo(2)

        var splitDebit = transaction.splits[0]
        assertThat(splitDebit.uid).isEqualTo("88bbbbac7689a8657b04427f8117a783")
        assertThat(splitDebit.accountUID).isEqualTo("6a7cf8267314992bdddcee56d71a3908")
        assertThat(splitDebit.transactionUID).isEqualTo("ded49386f8ea319ccaee043ba062b3e1")
        assertThat(splitDebit.type).isEqualTo(TransactionType.DEBIT)
        assertThat(splitDebit.value.numerator).isEqualTo(2000)
        assertThat(splitDebit.value.denominator).isEqualTo(100)
        assertThat(splitDebit.value).isEqualTo(Money("20", "USD"))
        assertThat(splitDebit.quantity.numerator).isEqualTo(2000)
        assertThat(splitDebit.quantity.denominator).isEqualTo(100)
        assertThat(splitDebit.quantity).isEqualTo(Money("20", "USD"))

        var splitCredit = transaction.splits[1]
        assertThat(splitCredit.uid).isEqualTo("e0dd885065bfe3c9ef63552fe84c6d23")
        assertThat(splitCredit.accountUID).isEqualTo("0469e915a22ba7846aca0e69f9f9b683")
        assertThat(splitCredit.transactionUID).isEqualTo("ded49386f8ea319ccaee043ba062b3e1")
        assertThat(splitCredit.type).isEqualTo(TransactionType.CREDIT)
        assertThat(splitCredit.value.numerator).isEqualTo(2000)
        assertThat(splitCredit.value.denominator).isEqualTo(100)
        assertThat(splitCredit.value).isEqualTo(Money("20", "USD"))
        assertThat(splitCredit.quantity.numerator).isEqualTo(1793)
        assertThat(splitCredit.quantity.denominator).isEqualTo(100)
        assertThat(splitCredit.quantity).isEqualTo(Money("17.93", "EUR"))
        assertThat(splitCredit.isPairOf(splitDebit)).isTrue()

        assertThat(scheduledActionDbAdapter.recordsCount).isOne()

        val scheduledAction = scheduledActionDbAdapter.getRecord("d1ecc943a53e48de91dac65dfbcd23b3")
        assertThat(scheduledAction.name).isEqualTo("Salad express Scheduled")
        assertThat(scheduledAction.actionUID).isEqualTo("a61cb5e0fc8f46e49f47a4812bfcd1e6")
        assertThat(scheduledAction.isEnabled).isTrue()
        assertThat(scheduledAction.isAutoCreate).isFalse()
        assertThat(scheduledAction.isAutoCreateNotify).isFalse()
        assertThat(scheduledAction.instanceCount).isOne()
        assertThat(formatDate(scheduledAction.startDate)).isEqualTo("2016-09-25")
        assertThat(formatDate(scheduledAction.endDate)).isEqualTo("2025-12-31")
        assertThat(scheduledAction.templateAccountUID).isEqualTo("ea8dc2da727542c9becc721e5f05f0f9")
        assertThat(scheduledAction.recurrence).isNotNull()
        assertThat(scheduledAction.recurrence.periodType).isEqualTo(PeriodType.WEEK)
        assertThat(scheduledAction.recurrence.multiplier).isOne()

        transaction = transactionsDbAdapter.getRecord("a61cb5e0fc8f46e49f47a4812bfcd1e6")

        // Ensure it's the correct one
        assertThat(transaction.description).isEqualTo("Salad express")
        assertThat(transaction.commodity.currencyCode).isEqualTo("USD")
        assertThat(transaction.commodity.smallestFraction).isEqualTo(100)

        // Check splits
        assertThat(transaction.splits.size).isEqualTo(2)

        splitDebit = transaction.splits[0]
        assertThat(splitDebit.uid).isEqualTo("9fc187a80d444c0cadeda57d384e2f1f")
        assertThat(splitDebit.accountUID).isEqualTo("ea8dc2da727542c9becc721e5f05f0f9")
        assertThat(splitDebit.scheduledActionAccountUID).isEqualTo("6a7cf8267314992bdddcee56d71a3908")
        assertThat(splitDebit.transactionUID).isEqualTo("a61cb5e0fc8f46e49f47a4812bfcd1e6")
        assertThat(splitDebit.type).isEqualTo(TransactionType.DEBIT)
        assertThat(splitDebit.value.numerator).isEqualTo(2000)
        assertThat(splitDebit.value.denominator).isEqualTo(100)
        assertThat(splitDebit.value).isEqualTo(Money("20", "USD"))
        assertThat(splitDebit.quantity.isAmountZero).isTrue()

        splitCredit = transaction.splits[1]
        assertThat(splitCredit.uid).isEqualTo("7a61df8f81a64741a31e276a6d82ac9f")
        assertThat(splitCredit.accountUID).isEqualTo("ea8dc2da727542c9becc721e5f05f0f9")
        assertThat(splitCredit.scheduledActionAccountUID).isEqualTo("0469e915a22ba7846aca0e69f9f9b683")
        assertThat(splitCredit.transactionUID).isEqualTo("a61cb5e0fc8f46e49f47a4812bfcd1e6")
        assertThat(splitCredit.type).isEqualTo(TransactionType.CREDIT)
        assertThat(splitCredit.value.numerator).isEqualTo(2000)
        assertThat(splitCredit.value.denominator).isEqualTo(100)
        assertThat(splitCredit.value).isEqualTo(Money("20", "USD"))
        assertThat(splitCredit.quantity.isAmountZero).isTrue()
        assertThat(splitCredit.isPairOf(splitDebit)).isTrue()
    }

    @Test
    fun `import xml - common_1`() {
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
            transactionsDbAdapter
        )
    }

    @Test
    fun `export since`() {
        val since = TimestampHelper.timestampFromEpochZero
        importGnuCashXml("common_1.gnucash")
        // 3 normal transactions + 1 template transaction
        assertThat(transactionsDbAdapter.recordsCount).isEqualTo(3)
        val transactionsImported = transactionsDbAdapter.fetchTransactionsToExportSince(since)
        assertThat(transactionsImported.count).isZero()
        assertThat(transactionsImported.moveToFirst()).isFalse()
        transactionsImported.close()

        val account = accountsDbAdapter.getRecord("2525cbd0457c4c8db12e311c960e5f45")
        val transaction = Transaction("Food")
        val split = Split(Money(123.45, "USD"), account)
        transaction.addSplit(split)
        transaction.addSplit(split.createPair("377cc9fff6ad44daa3873e070afaf2e1"))
        transactionsDbAdapter.insert(transaction)
        assertThat(transactionsDbAdapter.recordsCount).isEqualTo(4)

        val transactionsModified = transactionsDbAdapter.fetchTransactionsToExportSince(since)
        assertThat(transactionsModified.count).isOne()
        assertThat(transactionsModified.moveToFirst()).isTrue()
        val transactionToExport = transactionsDbAdapter.buildModelInstance(transactionsModified)
        transactionsModified.close()
        assertThat(transactionToExport).isEqualTo(transaction)
    }

    companion object {
        fun testCommon1(
            bookUID: String,
            accountsDbAdapter: AccountsDbAdapter,
            booksDbAdapter: BooksDbAdapter,
            budgetsDbAdapter: BudgetsDbAdapter,
            commoditiesDbAdapter: CommoditiesDbAdapter,
            pricesDbAdapter: PricesDbAdapter,
            recurrenceDbAdapter: RecurrenceDbAdapter,
            scheduledActionDbAdapter: ScheduledActionDbAdapter,
            transactionsDbAdapter: TransactionsDbAdapter
        ) {
            val currencyILS = commoditiesDbAdapter.getCurrency("ILS")!!
            val currencyUSD = commoditiesDbAdapter.getCurrency("USD")!!

            assertThat(bookUID).isEqualTo("a7682e5d878e43cea216611401f08463")
            val book = booksDbAdapter.getRecord(bookUID)
            assertThat(book.uid).isEqualTo("a7682e5d878e43cea216611401f08463")
            assertThat(book.rootAccountUID).isEqualTo("2fd3dc45e1974993a15ea1c611d3d345")
            assertThat(book.rootTemplateUID).isEqualTo("5569f2318478400f94606478b0de55c5")

            assertThat(pricesDbAdapter.recordsCount).isEqualTo(2)
            // 67 regular accounts + 2 template accounts
            assertThat(accountsDbAdapter.recordsCount).isEqualTo(69)
            assertThat(budgetsDbAdapter.recordsCount).isOne()
            assertThat(recurrenceDbAdapter.recordsCount).isEqualTo(2)
            assertThat(scheduledActionDbAdapter.recordsCount).isOne()
            // 3 regular transactions + 1 template transaction
            assertThat(transactionsDbAdapter.recordsCount).isEqualTo(3)
            assertThat(transactionsDbAdapter.splitsDbAdapter.recordsCount).isEqualTo(8)

            assertThat(accountsDbAdapter.recordsCount).isEqualTo(69)

            val root = accountsDbAdapter.getRecord("2fd3dc45e1974993a15ea1c611d3d345")
            assertThat(root.parentUID).isNull()
            assertThat(root.name).isEqualTo("Root Account")
            assertThat(root.fullName).isEqualTo("Root Account")
            assertThat(root.type).isEqualTo(AccountType.ROOT)

            val rootTemplate = accountsDbAdapter.getRecord("5569f2318478400f94606478b0de55c5")
            assertThat(rootTemplate.parentUID).isNull()
            assertThat(rootTemplate.name).isEqualTo("Template Root")
            assertThat(rootTemplate.fullName).isEqualTo("Template Root")
            assertThat(rootTemplate.type).isEqualTo(AccountType.ROOT)
            assertThat(rootTemplate.commodity.isTemplate).isTrue()

            val account = accountsDbAdapter.getRecord("dbbf295a331d4182bca73b548f5f7eaf")
            assertThat(account.parentUID).isEqualTo("84bbb4a4c12844fa9a4a7b4d6915288a")
            assertThat(account.name).isEqualTo("Groceries")
            assertThat(account.fullName).isEqualTo("Expenses:Groceries")
            assertThat(account.type).isEqualTo(AccountType.EXPENSE)

            val transaction = transactionsDbAdapter.getRecord("ca215bd292094eae8267d4cba5e6a0f1")
            assertThat(transaction.description).isEqualTo("Amazon")
            assertThat(transaction.notes).isEqualTo("hardcover")
            assertThat(transaction.number).isEqualTo("N123")
            assertThat(transaction.splits).hasSize(2)
            assertThat(transaction.splits[0].uid).isEqualTo("5d7db05823ab46f3b0930e632c0c8e8f")
            assertThat(transaction.splits[0].accountUID).isEqualTo("0c1ed5137c5d43c0a2d5668c7ae89d72")
            assertThat(transaction.splits[0].value).isEqualTo(Money(123.45, currencyUSD))
            assertThat(transaction.splits[0].type).isEqualTo(TransactionType.DEBIT)
            assertThat(transaction.splits[0].memo).isEqualTo("bible")
            assertThat(transaction.splits[1].uid).isEqualTo("f2bbfea2313f4a92aef8bc4fbe7db9b1")
            assertThat(transaction.splits[1].accountUID).isEqualTo("377cc9fff6ad44daa3873e070afaf2e1")
            assertThat(transaction.splits[1].value).isEqualTo(Money(123.45, currencyUSD))
            assertThat(transaction.splits[1].type).isEqualTo(TransactionType.CREDIT)
            assertThat(transaction.splits[1].memo).isEmpty()

            val template = transactionsDbAdapter.getRecord("9b42fbb885db4918819a05fc42dd63e0")
            assertThat(template.isTemplate).isTrue()
            assertThat(template.description).isEqualTo("AT&T")
            assertThat(template.splits).hasSize(2)
            assertThat(template.splits[0].value).isEqualTo(Money(99.90, currencyILS))

            val scheduledDate = LocalDate(2025, 5, 31).toDateTimeAtStartOfDay(DateTimeZone.UTC)
            val scheduledAction =
                scheduledActionDbAdapter.getRecord("11d621073ed745debd5027325d7853a4")
            assertThat(scheduledAction.name).isEqualTo("AT&T subscription")
            assertThat(scheduledAction.templateAccountUID).isEqualTo("3bdbf5e3b9364a6fb2dd463d1241a4ea")
            assertThat(scheduledAction.actionUID).isEqualTo(template.uid)
            assertThat(scheduledAction.startDate).isEqualTo(scheduledDate.toMillis())
            assertThat(scheduledAction.endDate).isEqualTo(0L)
            assertThat(scheduledAction.lastRunDate).isEqualTo(0L)
            assertThat(scheduledAction.instanceCount).isEqualTo(1)

            val budget = budgetsDbAdapter.getRecord("2f4e4e47cedb413e86ab4823757d1d84")
            assertThat(budget.name).isEqualTo("Groceries Budget")
            assertThat(budget.description).isEqualTo("shopping")
            assertThat(budget.recurrence).isNotNull()
            assertThat(budget.numberOfPeriods).isEqualTo(12)
            assertThat(budget.budgetAmounts).hasSize(13)

            val budgetAmount = budget.budgetAmounts.first { it.periodIndex == 1 }
            assertThat(budgetAmount.budgetUID).isEqualTo("2f4e4e47cedb413e86ab4823757d1d84")
            assertThat(budgetAmount.accountUID).isEqualTo("dbbf295a331d4182bca73b548f5f7eaf")
            assertThat(budgetAmount.amount.numerator).isEqualTo(1010_00L)
            assertThat(budgetAmount.amount.denominator).isEqualTo(100L)
            assertThat(budgetAmount.notes).isEqualTo("note for 04")

            val budgetAmountParent =
                budget.budgetAmounts.first { it.periodIndex == 0 && it.accountUID == account.parentUID }
            assertThat(budgetAmountParent.budgetUID).isEqualTo("2f4e4e47cedb413e86ab4823757d1d84")
            assertThat(budgetAmountParent.accountUID).isEqualTo("84bbb4a4c12844fa9a4a7b4d6915288a")
            assertThat(budgetAmountParent.notes).isEqualTo("note #1")
            assertThat(budgetAmountParent.amount.isAmountZero).isTrue()
        }
    }
}