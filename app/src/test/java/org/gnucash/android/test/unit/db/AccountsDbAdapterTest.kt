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
package org.gnucash.android.test.unit.db

import android.graphics.Color
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.data.Index
import org.gnucash.android.R
import org.gnucash.android.app.GnuCashApplication
import org.gnucash.android.db.DatabaseHelper
import org.gnucash.android.db.adapter.AccountsDbAdapter
import org.gnucash.android.db.adapter.AccountsDbAdapter.Companion.ALWAYS
import org.gnucash.android.db.adapter.BooksDbAdapter
import org.gnucash.android.db.adapter.BudgetAmountsDbAdapter
import org.gnucash.android.db.adapter.BudgetsDbAdapter
import org.gnucash.android.db.adapter.CommoditiesDbAdapter
import org.gnucash.android.db.adapter.PricesDbAdapter
import org.gnucash.android.db.adapter.ScheduledActionDbAdapter
import org.gnucash.android.db.adapter.SplitsDbAdapter
import org.gnucash.android.db.adapter.TransactionsDbAdapter
import org.gnucash.android.importer.xml.GncXmlImporter
import org.gnucash.android.model.Account
import org.gnucash.android.model.AccountType
import org.gnucash.android.model.Budget
import org.gnucash.android.model.BudgetAmount
import org.gnucash.android.model.Commodity
import org.gnucash.android.model.Money
import org.gnucash.android.model.Money.Companion.createZeroInstance
import org.gnucash.android.model.PeriodType
import org.gnucash.android.model.Recurrence
import org.gnucash.android.model.ScheduledAction
import org.gnucash.android.model.Split
import org.gnucash.android.model.Transaction
import org.gnucash.android.model.TransactionType
import org.gnucash.android.test.unit.GnuCashTest
import org.gnucash.android.ui.transaction.applyNormalBalanceSign
import org.gnucash.android.ui.transaction.EARLIEST_TIMESTAMP
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.xml.sax.SAXException
import timber.log.Timber
import java.io.IOException
import java.math.BigDecimal
import javax.xml.parsers.ParserConfigurationException

class AccountsDbAdapterTest : GnuCashTest() {
    private lateinit var accountsDbAdapter: AccountsDbAdapter
    private lateinit var transactionsDbAdapter: TransactionsDbAdapter
    private lateinit var splitsDbAdapter: SplitsDbAdapter
    private lateinit var commoditiesDbAdapter: CommoditiesDbAdapter

    @Before
    fun setUp() {
        initAdapters(null)
    }

    @After
    fun after() {
        accountsDbAdapter.close()
        commoditiesDbAdapter.close()
        splitsDbAdapter.close()
        transactionsDbAdapter.close()
    }

    @After
    fun tearDown() {
        accountsDbAdapter.deleteAllRecords()
    }

    /**
     * Initialize database adapters for a specific book.
     * This method should be called everytime a new book is loaded into the database
     *
     * @param bookUID GUID of the GnuCash book
     */
    private fun initAdapters(bookUID: String?) {
        if (bookUID == null) {
            commoditiesDbAdapter = CommoditiesDbAdapter.instance
            splitsDbAdapter = SplitsDbAdapter.instance
            transactionsDbAdapter = TransactionsDbAdapter.instance
            accountsDbAdapter = AccountsDbAdapter.instance
        } else {
            val databaseHelper = DatabaseHelper(context, bookUID)
            val dbHolder = databaseHelper.holder
            commoditiesDbAdapter = CommoditiesDbAdapter(dbHolder)
            splitsDbAdapter = SplitsDbAdapter(commoditiesDbAdapter)
            transactionsDbAdapter = TransactionsDbAdapter(splitsDbAdapter)
            accountsDbAdapter = AccountsDbAdapter(transactionsDbAdapter)
            val b1 = GnuCashApplication.booksDbAdapter
            val b2 = BooksDbAdapter.instance
            assertThat(b1).isEqualTo(b2)
            b2.setActive(bookUID)
        }
    }

    /**
     * Test that the list of accounts is always returned sorted alphabetically
     */
    @Test
    fun shouldBeAlphabeticallySortedByDefault() {
        val alpha = Account(ALPHA_ACCOUNT_NAME)
        val bravo = Account(BRAVO_ACCOUNT_NAME)
        //purposefully added the second after the first
        accountsDbAdapter.addRecord(bravo)
        accountsDbAdapter.addRecord(alpha)

        val accounts = accountsDbAdapter.simpleAccounts
        assertThat(accounts).hasSize(3)
        val root = accounts[0]
        val rootType = root.type
        assertThat(rootType).isEqualTo(AccountType.ROOT)
        assertThat(accounts).contains(alpha, Index.atIndex(1))
        assertThat(accounts).contains(bravo, Index.atIndex(2))
    }

    @Test
    fun bulkAddAccountsShouldNotModifyTransactions() {
        val account1 = Account("AlphaAccount")
        val account2 = Account("BetaAccount")

        val accounts = listOf(account1, account2)
        accountsDbAdapter.bulkAddRecords(accounts)

        val transaction = Transaction("MyTransaction")
        val split = Split(createZeroInstance(account1.commodity), account1)
        transaction.addSplit(split)
        transaction.addSplit(split.createPair(account2))
        transactionsDbAdapter.insert(transaction)

        assertThat(
            splitsDbAdapter.getSplitsForTransactionInAccount(transaction.uid, account1.uid)
        ).hasSize(1)
        assertThat(
            splitsDbAdapter.getSplitsForTransactionInAccount(transaction.uid, account2.uid)
        ).hasSize(1)

        val account = accountsDbAdapter.getRecord(account1.uid)
        assertThat(account).isNotNull()

        assertThat(transactionsDbAdapter.getTransactionsForAccount(account)).hasSize(1)
    }

    @Test
    fun shouldAddAccountsToDatabase() {
        val account1 = Account("AlphaAccount")
        val account2 = Account("BetaAccount")
        val transaction = Transaction("MyTransaction")
        val split = Split(createZeroInstance(account1.commodity), account1)
        transaction.addSplit(split)
        transaction.addSplit(split.createPair(account2))

        // Disable foreign key validation because the second split,
        // which is added during 1st account,
        // references the second account which has not been added yet.
        accountsDbAdapter.addRecord(account1)
        accountsDbAdapter.addRecord(account2)
        assertThat(accountsDbAdapter.recordsCount).isEqualTo(3) //root+account1+account2
        transactionsDbAdapter.addRecord(transaction)

        val firstAccount = accountsDbAdapter.getRecord(account1.uid)
        assertThat(firstAccount).isNotNull()
        assertThat(firstAccount.uid).isEqualTo(account1.uid)
        assertThat(firstAccount.fullName).isEqualTo(account1.fullName)

        val secondAccount = accountsDbAdapter.getRecord(account2.uid)
        assertThat(secondAccount).isNotNull()
        assertThat(secondAccount.uid).isEqualTo(account2.uid)

        assertThat(transactionsDbAdapter.recordsCount).isOne()
    }

    /**
     * Tests the foreign key constraint "ON DELETE CASCADE" between accounts and splits
     */
    @Test
    fun shouldDeleteSplitsWhenAccountDeleted() {
        val first = Account(ALPHA_ACCOUNT_NAME)
        first.setUID(ALPHA_ACCOUNT_NAME)
        val second = Account(BRAVO_ACCOUNT_NAME)
        second.setUID(BRAVO_ACCOUNT_NAME)

        accountsDbAdapter.addRecord(second)
        accountsDbAdapter.addRecord(first)

        val transaction = Transaction("TestTrn")
        val split = Split(createZeroInstance(first.commodity), ALPHA_ACCOUNT_NAME)
        transaction.addSplit(split)
        transaction.addSplit(split.createPair(BRAVO_ACCOUNT_NAME))

        transactionsDbAdapter.addRecord(transaction)

        accountsDbAdapter.deleteRecord(ALPHA_ACCOUNT_NAME)

        val trxn = transactionsDbAdapter.getRecord(transaction.uid)
        assertThat(trxn.splits).hasSize(1)
        assertThat(trxn.splits[0].accountUID).isEqualTo(BRAVO_ACCOUNT_NAME)
    }

    /**
     * Tests that a ROOT account will always be created in the system
     */
    @Test
    fun shouldCreateDefaultRootAccount() {
        val account = Account("Some account")
        accountsDbAdapter.addRecord(account)
        assertThat(accountsDbAdapter.recordsCount).isEqualTo(2L)

        val accounts = accountsDbAdapter.simpleAccounts
        assertThat(accounts)
            .extracting("type", AccountType::class.java)
            .contains(AccountType.ROOT)

        val rootAccountUID = accountsDbAdapter.rootAccountUID
        assertThat(rootAccountUID).isEqualTo(accounts[1].parentUID)
    }

    @Test
    fun shouldUpdateFullNameAfterParentChange() {
        var parent = Account("Test")
        var child = Account("Child")

        accountsDbAdapter.addRecord(parent)
        accountsDbAdapter.addRecord(child)
        assertThat(child.fullName).isEqualTo("Child")

        child.parentUID = parent.uid
        accountsDbAdapter.addRecord(child)

        child = accountsDbAdapter.getRecord(child.uid)
        parent = accountsDbAdapter.getRecord(parent.uid)

        assertThat(accountsDbAdapter.getSubAccountCount(parent.uid)).isOne()
        assertThat(parent.uid).isEqualTo(child.parentUID)

        assertThat(child.fullName).isEqualTo("Test:Child")
    }

    @Test
    fun shouldAddTransactionsAndSplitsWhenAddingAccounts() {
        val account = Account("Test")
        accountsDbAdapter.addRecord(account)

        val transaction = Transaction("Test description")
        val split = Split(createZeroInstance(account.commodity), account)
        transaction.addSplit(split)
        val account1 = Account("Transfer account")
        transaction.addSplit(split.createPair(account1))

        accountsDbAdapter.addRecord(account1)
        transactionsDbAdapter.addRecord(transaction)

        assertThat(transactionsDbAdapter.recordsCount).isOne()
        assertThat(splitsDbAdapter.recordsCount).isEqualTo(2)
        assertThat(accountsDbAdapter.recordsCount).isEqualTo(3) //ROOT account automatically added
    }

    @Test
    fun shouldClearAllTablesWhenDeletingAllAccounts() {
        val account = Account("Test")
        val transaction = Transaction("Test description")
        val split = Split(createZeroInstance(account.commodity), account)
        transaction.addSplit(split)
        val account2 = Account("Transfer account")
        transaction.addSplit(split.createPair(account2))

        accountsDbAdapter.addRecord(account)
        accountsDbAdapter.addRecord(account2)

        val scheduledAction = ScheduledAction(ScheduledAction.ActionType.EXPORT)
        scheduledAction.actionUID = "Test-uid"
        scheduledAction.setRecurrence(Recurrence(PeriodType.WEEK))
        val scheduledActionDbAdapter = ScheduledActionDbAdapter.instance

        scheduledActionDbAdapter.addRecord(scheduledAction)

        val budget = Budget("Test")
        val budgetAmount = BudgetAmount(createZeroInstance(account.commodity), account.uid)
        budget.addAmount(budgetAmount)
        budget.recurrence = Recurrence(PeriodType.MONTH)
        BudgetsDbAdapter.instance.addRecord(budget)

        accountsDbAdapter.deleteAllRecords()

        assertThat(accountsDbAdapter.recordsCount).isZero()
        assertThat(transactionsDbAdapter.recordsCount).isZero()
        assertThat(splitsDbAdapter.recordsCount).isZero()
        assertThat(scheduledActionDbAdapter.recordsCount).isZero()
        assertThat(BudgetAmountsDbAdapter.instance.recordsCount).isZero()
        assertThat(BudgetsDbAdapter.instance.recordsCount).isZero()
        assertThat(PricesDbAdapter.instance.recordsCount).isZero() //prices should remain
        assertThat(CommoditiesDbAdapter.instance!!.recordsCount).isGreaterThan(50) //commodities should remain
    }

    @Test
    fun shouldComputeAccountBalanceCorrectly() {
        val account = Account("Test", Commodity.USD)
        account.type = AccountType.ASSET //debit normal account balance
        val transferAcct = Account("Transfer")

        accountsDbAdapter.addRecord(account)
        accountsDbAdapter.addRecord(transferAcct)

        val transaction = Transaction("Test description")
        transactionsDbAdapter.addRecord(transaction)
        var split = Split(Money(BigDecimal.TEN, Commodity.USD), account)
        split.transactionUID = transaction.uid
        split.type = TransactionType.DEBIT
        splitsDbAdapter.addRecord(split)

        split = Split(Money("4.99", "USD"), account)
        split.transactionUID = transaction.uid
        split.type = TransactionType.DEBIT
        splitsDbAdapter.addRecord(split)

        split = Split(Money("1.19", "USD"), account)
        split.transactionUID = transaction.uid
        split.type = TransactionType.CREDIT
        splitsDbAdapter.addRecord(split)

        split = Split(Money("3.49", "EUR"), account)
        split.transactionUID = transaction.uid
        split.type = TransactionType.DEBIT
        splitsDbAdapter.addRecord(split)

        split = Split(Money("8.39", "USD"), transferAcct)
        split.transactionUID = transaction.uid
        splitsDbAdapter.addRecord(split)

        //balance computation ignores the currency of the split
        val balance = accountsDbAdapter.getAccountBalance(account)
        val expectedBalance = Money("17.29", "USD") //EUR splits should be ignored

        assertThat(balance).isEqualTo(expectedBalance)
    }

    /**
     * Test creating an account hierarchy by specifying fully qualified name
     */
    @Test
    fun shouldCreateAccountHierarchy() {
        val uid = accountsDbAdapter.createAccountHierarchy(
            "Assets:Current Assets:Cash in Wallet",
            AccountType.ASSET
        )

        val accounts = accountsDbAdapter.allRecords
        assertThat(accounts).hasSize(3)
        assertThat(accounts[0].name).isEqualTo("Assets")
        assertThat(accounts[0].fullName).isEqualTo("Assets")
        assertThat(accounts[0].type).isEqualTo(AccountType.ASSET)
        assertThat(accounts[1].name).isEqualTo("Current Assets")
        assertThat(accounts[1].fullName).isEqualTo("Assets:Current Assets")
        assertThat(accounts[1].type).isEqualTo(AccountType.ASSET)
        assertThat(accounts[2].name).isEqualTo("Cash in Wallet")
        assertThat(accounts[2].fullName).isEqualTo("Assets:Current Assets:Cash in Wallet")
        assertThat(accounts[2].type).isEqualTo(AccountType.ASSET)
    }

    @Test
    fun shouldRecursivelyDeleteAccount() {
        val account = Account("Parent")
        val account2 = Account("Child")
        account2.parentUID = account.uid
        accountsDbAdapter.addRecord(account)
        accountsDbAdapter.addRecord(account2)

        val transaction = Transaction("Random")

        val split = Split(createZeroInstance(account.commodity), account)
        transaction.addSplit(split)
        transaction.addSplit(split.createPair(account2))
        transactionsDbAdapter.addRecord(transaction)

        assertThat(accountsDbAdapter.recordsCount).isEqualTo(3)
        assertThat(transactionsDbAdapter.recordsCount).isOne()
        assertThat(splitsDbAdapter.recordsCount).isEqualTo(2)

        val result = accountsDbAdapter.recursiveDeleteAccount(account.uid)
        assertThat(result).isTrue()

        assertThat(accountsDbAdapter.recordsCount).isOne() //the root account
        assertThat(transactionsDbAdapter.recordsCount).isZero()
        assertThat(splitsDbAdapter.recordsCount).isZero()
    }

    @Test
    fun shouldGetDescendantAccounts() {
        loadDefaultAccounts()

        val uid = accountsDbAdapter.findAccountUidByFullName("Expenses:Auto")!!
        val descendants = accountsDbAdapter.getDescendantAccountUIDs(uid, null, null)

        assertThat(descendants).hasSize(4)
    }

    @Test
    fun shouldReassignDescendantAccounts() {
        loadDefaultAccounts()

        val assetsUID = accountsDbAdapter.findAccountUidByFullName("Assets")!!
        val savingsAcctUID =
            accountsDbAdapter.findAccountUidByFullName("Assets:Current Assets:Savings Account")!!
        val currentAssetsUID =
            accountsDbAdapter.findAccountUidByFullName("Assets:Current Assets")!!

        assertThat(accountsDbAdapter.getParentAccountUID(savingsAcctUID))
            .isEqualTo(currentAssetsUID)
        accountsDbAdapter.reassignDescendantAccounts(currentAssetsUID, assetsUID)
        assertThat(accountsDbAdapter.getParentAccountUID(savingsAcctUID))
            .isEqualTo(assetsUID)
        assertThat(accountsDbAdapter.getFullyQualifiedAccountName(savingsAcctUID))
            .isEqualTo("Assets:Savings Account")
    }

    @Test
    fun shouldCreateImbalanceAccountOnDemand() {
        assertThat(accountsDbAdapter.recordsCount).isEqualTo(1L)

        val usd = commoditiesDbAdapter.getCurrency("USD")!!
        var imbalanceUID = accountsDbAdapter.getImbalanceAccountUID(context, usd)
        assertThat(imbalanceUID).isNull()
        assertThat(accountsDbAdapter.recordsCount).isEqualTo(1L)

        imbalanceUID = accountsDbAdapter.getOrCreateImbalanceAccountUID(context, usd)
        assertThat(imbalanceUID).isNotNull().isNotEmpty()
        assertThat(accountsDbAdapter.recordsCount).isEqualTo(2)
    }


    @Test
    fun editingAccountShouldNotDeleteTemplateSplits() {
        val account = Account("First", Commodity.EUR)
        val transferAccount = Account("Transfer", Commodity.EUR)
        accountsDbAdapter.addRecord(account)
        accountsDbAdapter.addRecord(transferAccount)

        assertThat(accountsDbAdapter.recordsCount).isEqualTo(3) //plus root account

        val money = Money(BigDecimal.TEN, Commodity.EUR)
        val transaction = Transaction("Template")
        transaction.isTemplate = true
        transaction.commodity = Commodity.EUR
        val split = Split(money, account)
        transaction.addSplit(split)
        transaction.addSplit(split.createPair(transferAccount))

        transactionsDbAdapter.addRecord(transaction)
        val transactions = transactionsDbAdapter.allRecords
        assertThat(transactions).hasSize(1)

        assertThat(transactionsDbAdapter.getTransactionsForAccount(account)).hasSize(1)

        //edit the account
        account.name = "Edited account"
        accountsDbAdapter.update(account)

        assertThat(transactionsDbAdapter.getTransactionsForAccount(account)).hasSize(1)
        assertThat(splitsDbAdapter.getSplitsForTransaction(transaction)).hasSize(2)
    }

    @Test
    fun shouldSetDefaultTransferColumnToNull_WhenTheAccountIsDeleted() {
        accountsDbAdapter.deleteAllRecords()
        assertThat(accountsDbAdapter.recordsCount).isZero()

        val account1 = Account("Test")
        val account2 = Account("Transfer Account")

        assertThat(accountsDbAdapter.getDefaultTransferAccount(account1)).isNull()
        assertThat(accountsDbAdapter.findDefaultTransferAccount(account1)).isNull()
        account1.defaultTransferAccountUID = account2.uid

        accountsDbAdapter.addRecord(account1)
        accountsDbAdapter.addRecord(account2)
        assertThat(accountsDbAdapter.recordsCount).isEqualTo(3L) //plus ROOT account

        assertThat(accountsDbAdapter.getDefaultTransferAccount(account1)).isEqualTo(account2)
        assertThat(accountsDbAdapter.findDefaultTransferAccount(account1)).isEqualTo(account2)

        accountsDbAdapter.deleteRecord(account2.uid)
        assertThat(accountsDbAdapter.recordsCount).isEqualTo(2L)

        assertThat(accountsDbAdapter.getRecord(account1.uid).defaultTransferAccountUID).isNull()
        assertThat(accountsDbAdapter.getDefaultTransferAccount(account1)).isNull()
        assertThat(accountsDbAdapter.findDefaultTransferAccount(account1)).isNull()

        val account3 = Account("Sub-test")
        account3.parentUID = account1.uid
        val account4 = Account("Third-party")
        account4.defaultTransferAccountUID = account3.uid

        accountsDbAdapter.addRecord(account3)
        accountsDbAdapter.addRecord(account4)
        assertThat(accountsDbAdapter.recordsCount).isEqualTo(4L)

        accountsDbAdapter.recursiveDeleteAccount(account1.uid)
        assertThat(accountsDbAdapter.recordsCount).isEqualTo(2L)
        assertThat(accountsDbAdapter.getRecord(account4.uid).defaultTransferAccountUID)
            .isNull()
    }

    /**
     * Opening an XML file should set the default currency to that used by the most accounts in the file
     */
    @Test
    fun importingXml_shouldSetDefaultCurrencyFromXml() {
        GnuCashApplication.defaultCurrencyCode = "JPY"

        var defaultCurrency = GnuCashApplication.defaultCurrencyCode
        assertThat(defaultCurrency).isEqualTo("JPY")
        assertThat(Commodity.DEFAULT_COMMODITY).isEqualTo(Commodity.JPY)

        accountsDbAdapter.deleteAllRecords()
        val bookUID = loadDefaultAccounts()
        val activeBookUID = GnuCashApplication.activeBookUID
        assertThat(activeBookUID).isEqualTo(bookUID)

        //the book has USD occurring most often and this will be used as the default currency
        defaultCurrency = GnuCashApplication.defaultCurrencyCode
        assertThat(defaultCurrency).isEqualTo("USD")
        assertThat(Commodity.DEFAULT_COMMODITY).isEqualTo(Commodity.USD)

        println("Default currency is now: " + Commodity.DEFAULT_COMMODITY)
    }

    @Test
    fun testChangesToAccount() {
        accountsDbAdapter.deleteAllRecords()
        assertThat(accountsDbAdapter.recordsCount).isZero()

        val account1 = Account("Test")
        accountsDbAdapter.insert(account1)
        assertThat(account1.id).isNotEqualTo(0) //plus ROOT account
        assertThat(accountsDbAdapter.recordsCount).isEqualTo(2) //plus ROOT account

        val account2 = accountsDbAdapter.getRecord(account1.uid)
        assertThat(account2).isEqualTo(account1)
        assertThat(account2.isPlaceholder).isFalse()
        assertThat(account2.isFavorite).isFalse()
        assertThat(account2.color).isEqualTo(Account.DEFAULT_COLOR)

        account2.isPlaceholder = true
        account2.isFavorite = true
        account2.color = Color.MAGENTA
        accountsDbAdapter.replace(account2)
        val account3 = accountsDbAdapter.getRecord(account2.uid)
        assertThat(account3).isEqualTo(account2)
        assertThat(account3.isPlaceholder).isTrue()
        assertThat(account3.isFavorite).isTrue()
        assertThat(account3.color).isEqualTo(Color.MAGENTA)

        account3.isPlaceholder = true
        account3.isFavorite = false
        account3.color = Color.YELLOW
        accountsDbAdapter.update(account3)
        val account4 = accountsDbAdapter.getRecord(account3.uid)
        assertThat(account4).isEqualTo(account3)
        assertThat(account4.isPlaceholder).isTrue()
        assertThat(account4.isFavorite).isFalse()
        assertThat(account4.color).isEqualTo(Color.YELLOW)
    }

    @Test
    fun `cached COLUMN_BALANCE should match computed balance from splits`() {
        val account = Account("CacheCheck", Commodity.USD)
        account.type = AccountType.ASSET
        val transferAcct = Account("CacheTransfer", Commodity.USD)
        accountsDbAdapter.addRecord(account)
        accountsDbAdapter.addRecord(transferAcct)

        val transaction = Transaction("Cache check transaction")
        transactionsDbAdapter.addRecord(transaction)
        var split = Split(Money("10.00", "USD"), account)
        split.transactionUID = transaction.uid
        split.type = TransactionType.DEBIT
        splitsDbAdapter.addRecord(split)
        split = Split(Money("4.99", "USD"), account)
        split.transactionUID = transaction.uid
        split.type = TransactionType.DEBIT
        splitsDbAdapter.addRecord(split)
        split = Split(Money("1.19", "USD"), account)
        split.transactionUID = transaction.uid
        split.type = TransactionType.CREDIT
        splitsDbAdapter.addRecord(split)
        split = Split(Money("13.80", "USD"), transferAcct)
        split.transactionUID = transaction.uid
        split.type = TransactionType.CREDIT
        splitsDbAdapter.addRecord(split)

        // Cached value (ALWAYS, ALWAYS reads COLUMN_BALANCE) must equal the value
        // recomputed from splits. A mismatch means the cache went stale and the
        // account header (which reads the cache) would disagree with the
        // per-transaction running balance (which bypasses the cache).
        val cached = accountsDbAdapter.getAccountBalance(account)
        val computed = accountsDbAdapter.getAccountBalance(
            account, EARLIEST_TIMESTAMP, ALWAYS, includeSubAccounts = false
        )
        assertThat(cached).isEqualTo(computed)
        assertThat(computed).isEqualTo(Money("13.80", "USD"))
    }

    @Test
    fun `cached balance of parent account should match computed balance including sub-accounts`() {
        val parent = Account("Parent", Commodity.USD)
        parent.type = AccountType.ASSET
        val child = Account("Child", Commodity.USD)
        child.type = AccountType.ASSET
        child.parentUID = parent.uid
        val other = Account("Other", Commodity.USD)
        accountsDbAdapter.addRecord(parent)
        accountsDbAdapter.addRecord(child)
        accountsDbAdapter.addRecord(other)

        val t1 = Transaction("to parent")
        transactionsDbAdapter.addRecord(t1)
        var s = Split(Money("100.00", "USD"), parent)
        s.transactionUID = t1.uid
        s.type = TransactionType.DEBIT
        splitsDbAdapter.addRecord(s)
        s = Split(Money("100.00", "USD"), other)
        s.transactionUID = t1.uid
        s.type = TransactionType.CREDIT
        splitsDbAdapter.addRecord(s)

        val t2 = Transaction("to child")
        transactionsDbAdapter.addRecord(t2)
        s = Split(Money("40.00", "USD"), child)
        s.transactionUID = t2.uid
        s.type = TransactionType.DEBIT
        splitsDbAdapter.addRecord(s)
        s = Split(Money("40.00", "USD"), other)
        s.transactionUID = t2.uid
        s.type = TransactionType.CREDIT
        splitsDbAdapter.addRecord(s)

        val cached = accountsDbAdapter.getAccountBalance(parent)
        val computedWithChildren = accountsDbAdapter.getAccountBalance(
            parent, EARLIEST_TIMESTAMP, ALWAYS, includeSubAccounts = true
        )
        assertThat(cached).isEqualTo(computedWithChildren)
        assertThat(cached).isEqualTo(Money("140.00", "USD"))

        // the running-balance anchor deliberately excludes sub-accounts
        val computedOwnOnly = accountsDbAdapter.getAccountBalance(
            parent, EARLIEST_TIMESTAMP, ALWAYS, includeSubAccounts = false
        )
        assertThat(computedOwnOnly).isEqualTo(Money("100.00", "USD"))
    }

    @Test
    fun `signed quantities per transaction are consistent with account balance anchor`() {
        val liability = Account("Card", Commodity.USD)
        liability.type = AccountType.LIABILITY // credit-normal
        val other = Account("Other", Commodity.USD)
        accountsDbAdapter.addRecord(liability)
        accountsDbAdapter.addRecord(other)

        // Charge 100 (CREDIT on card), then pay 50 (DEBIT on card)
        val t1 = Transaction("charge")
        transactionsDbAdapter.addRecord(t1)
        var s = Split(Money("100.00", "USD"), liability)
        s.transactionUID = t1.uid
        s.type = TransactionType.CREDIT
        splitsDbAdapter.addRecord(s)
        s = Split(Money("100.00", "USD"), other)
        s.transactionUID = t1.uid
        s.type = TransactionType.DEBIT
        splitsDbAdapter.addRecord(s)

        val t2 = Transaction("payment")
        transactionsDbAdapter.addRecord(t2)
        s = Split(Money("50.00", "USD"), liability)
        s.transactionUID = t2.uid
        s.type = TransactionType.DEBIT
        splitsDbAdapter.addRecord(s)
        s = Split(Money("50.00", "USD"), other)
        s.transactionUID = t2.uid
        s.type = TransactionType.CREDIT
        splitsDbAdapter.addRecord(s)

        val amounts = splitsDbAdapter.getSignedQuantitiesPerTransaction(liability.uid)

        val anchor = accountsDbAdapter.getAccountBalance(
            liability, EARLIEST_TIMESTAMP, ALWAYS, includeSubAccounts = false
        )

        // applyNormalBalanceSign must be applied to amounts to match the anchor's convention
        val flippedAmounts = applyNormalBalanceSign(
            amounts, liability.type.hasDebitNormalBalance
        )

        val sum = flippedAmounts.values.fold(createZeroInstance(liability.commodity)) { acc, m -> acc + m }
        assertThat(anchor).isEqualTo(sum)
    }

    @Test
    fun `getSignedQuantitiesPerTransaction excludes template transactions`() {
        val account = Account("TemplateCheck", Commodity.USD)
        accountsDbAdapter.addRecord(account)

        // Create a template transaction
        val templateTx = Transaction("Template")
        templateTx.isTemplate = true
        transactionsDbAdapter.addRecord(templateTx)

        val split = Split(Money("10.00", "USD"), account)
        split.transactionUID = templateTx.uid
        split.type = TransactionType.DEBIT
        splitsDbAdapter.addRecord(split)

        // The SQL query must ignore template transactions
        val amounts = splitsDbAdapter.getSignedQuantitiesPerTransaction(account.uid)
        assertThat(amounts).isEmpty()
    }

    @Test
    fun `getSignedQuantitiesPerTransaction ignores splits of other accounts`() {
        val account = Account("Mine", Commodity.USD)
        val other = Account("Theirs", Commodity.USD)
        accountsDbAdapter.addRecord(account)
        accountsDbAdapter.addRecord(other)

        val t = Transaction("mixed")
        transactionsDbAdapter.addRecord(t)

        var s = Split(Money("10.00", Commodity.USD), account)
        s.transactionUID = t.uid
        s.type = TransactionType.DEBIT
        splitsDbAdapter.addRecord(s)

        s = Split(Money("99.00", Commodity.USD), other)
        s.transactionUID = t.uid
        s.type = TransactionType.DEBIT
        splitsDbAdapter.addRecord(s)

        val amounts = splitsDbAdapter.getSignedQuantitiesPerTransaction(account.uid)
        assertThat(amounts).containsOnlyKeys(t.uid)
        assertThat(amounts[t.uid]).isEqualTo(Money("10.00", Commodity.USD))
    }

    @Test
    fun `getSignedQuantitiesPerTransaction uses account currency (quantity) not transaction currency (value)`() {
        val usdAccount = Account("USD Account", Commodity.USD)
        accountsDbAdapter.addRecord(usdAccount)

        val t = Transaction("Multi-currency")
        t.commodity = Commodity.EUR // Transaction is in EUR
        transactionsDbAdapter.addRecord(t)

        // Split in USD account: value = 100 EUR, quantity = 110 USD
        val s = Split(Money("100.00", Commodity.EUR), usdAccount)
        s.transactionUID = t.uid
        s.type = TransactionType.DEBIT
        s.quantity = Money("110.00", Commodity.USD)
        splitsDbAdapter.addRecord(s)

        val amounts = splitsDbAdapter.getSignedQuantitiesPerTransaction(usdAccount.uid)
        // Should take 110 (quantity), not 100 (value)
        assertThat(amounts[t.uid]).isEqualTo(Money("110.00", Commodity.USD))
    }

    /**
     * Loads the default accounts from file resource
     */
    private fun loadDefaultAccounts(): String {
        try {
            val bookUID = GncXmlImporter.parse(
                context,
                context.resources.openRawResource(R.raw.default_accounts)
            )
            initAdapters(bookUID)
            return bookUID
        } catch (e: ParserConfigurationException) {
            Timber.e(e)
            throw RuntimeException("Could not create default accounts")
        } catch (e: SAXException) {
            Timber.e(e)
            throw RuntimeException("Could not create default accounts")
        } catch (e: IOException) {
            Timber.e(e)
            throw RuntimeException("Could not create default accounts")
        }
    }

    companion object {
        private const val BRAVO_ACCOUNT_NAME = "Bravo"
        private const val ALPHA_ACCOUNT_NAME = "Alpha"
    }
}
