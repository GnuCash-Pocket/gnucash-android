/*
 * Copyright (c) 2012 - 2015 Ngewi Fet <ngewif@gmail.com>
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
package org.gnucash.android.test.ui

import android.Manifest
import android.content.ContentValues
import android.content.Intent
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions
import androidx.test.espresso.action.ViewActions.clearText
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.pressBack
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.action.ViewActions.typeText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.withDecorView
import androidx.test.espresso.matcher.ViewMatchers.hasDescendant
import androidx.test.espresso.matcher.ViewMatchers.isChecked
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withParent
import androidx.test.espresso.matcher.ViewMatchers.withTagValue
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.ActivityTestRule
import androidx.test.rule.GrantPermissionRule
import androidx.test.uiautomator.UiDevice
import org.assertj.core.api.Assertions.assertThat
import org.gnucash.android.R
import org.gnucash.android.app.GnuCashApplication
import org.gnucash.android.db.DatabaseSchema
import org.gnucash.android.db.adapter.AccountsDbAdapter
import org.gnucash.android.db.adapter.CommoditiesDbAdapter
import org.gnucash.android.db.adapter.DatabaseAdapter
import org.gnucash.android.db.adapter.SplitsDbAdapter
import org.gnucash.android.db.adapter.TransactionsDbAdapter
import org.gnucash.android.model.Account
import org.gnucash.android.model.Commodity
import org.gnucash.android.model.Commodity.Companion.getInstance
import org.gnucash.android.model.Money
import org.gnucash.android.model.Split
import org.gnucash.android.model.Transaction
import org.gnucash.android.model.TransactionType
import org.gnucash.android.receivers.TransactionRecorder
import org.gnucash.android.test.ui.util.DisableAnimationsRule
import org.gnucash.android.ui.common.UxArgument
import org.gnucash.android.ui.settings.PreferenceActivity
import org.gnucash.android.ui.transaction.TransactionFormFragment.DATE_FORMATTER
import org.gnucash.android.ui.transaction.TransactionFormFragment.TIME_FORMATTER
import org.gnucash.android.ui.transaction.TransactionsActivity
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.`is`
import org.hamcrest.Matchers.not
import org.junit.After
import org.junit.Before
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Locale

class TransactionsActivityTest : GnuAndroidTest() {
    private lateinit var transaction: Transaction
    private var transactionTimeMillis: Long = 0

    private lateinit var transactionsActivity: TransactionsActivity

    @Rule
    @JvmField
    val animationPermissionsRule =
        GrantPermissionRule.grant(Manifest.permission.SET_ANIMATION_SCALE)

    @Rule
    @JvmField
    val activityRule = ActivityTestRule(TransactionsActivity::class.java, true, false)

    private lateinit var baseAccount: Account
    private lateinit var transferAccount: Account

    @Before
    fun setUp() {
        setDoubleEntryEnabled(true)
        setDefaultTransactionType(TransactionType.DEBIT)

        accountsDbAdapter.deleteAllRecords()

        baseAccount = Account(TRANSACTIONS_ACCOUNT_NAME, COMMODITY)
        baseAccount.setUID(TRANSACTIONS_ACCOUNT_UID)
        accountsDbAdapter.addRecord(baseAccount, DatabaseAdapter.UpdateMethod.insert)

        transferAccount = Account(TRANSFER_ACCOUNT_NAME, COMMODITY)
        transferAccount.setUID(TRANSFER_ACCOUNT_UID)
        accountsDbAdapter.addRecord(transferAccount, DatabaseAdapter.UpdateMethod.insert)

        assertThat(accountsDbAdapter.recordsCount)
            .isEqualTo(3) //including ROOT account

        transactionTimeMillis = System.currentTimeMillis()
        transaction = Transaction(TRANSACTION_NAME)
        transaction.commodity = COMMODITY
        transaction.note = "What up?"
        transaction.setTime(transactionTimeMillis)
        val split = Split(Money(TRANSACTION_AMOUNT, CURRENCY_CODE), TRANSACTIONS_ACCOUNT_UID)
        split.type = TransactionType.DEBIT

        transaction.addSplit(split)
        transaction.addSplit(split.createPair(TRANSFER_ACCOUNT_UID))

        transactionsDbAdapter.addRecord(transaction, DatabaseAdapter.UpdateMethod.insert)
        assertThat(transactionsDbAdapter.recordsCount).isOne()

        val intent = Intent(Intent.ACTION_VIEW)
            .putExtra(UxArgument.SELECTED_ACCOUNT_UID, TRANSACTIONS_ACCOUNT_UID)
        transactionsActivity = activityRule.launchActivity(intent)
    }

    @After
    fun tearDown() {
        transactionsActivity.finish()
    }

    private fun validateTransactionListDisplayed() {
        sleep(500) // wait for adapters to load
        onView(
            allOf(
                withId(android.R.id.list),
                withTagValue(`is`("transactions"))
            )
        ).check(matches(isDisplayed()))
    }

    private val transactionCount: Int
        get() = transactionsDbAdapter.getAllTransactionsForAccount(TRANSACTIONS_ACCOUNT_UID).size

    private fun validateTimeInput(timeMillis: Long) {
        var expectedValue = DATE_FORMATTER.print(timeMillis)
        onView(withId(R.id.input_date))
            .check(matches(withText(expectedValue)))

        expectedValue = TIME_FORMATTER.print(timeMillis)
        onView(withId(R.id.input_time))
            .check(matches(withText(expectedValue)))
    }

    @Test
    fun testAddTransactionShouldRequireAmount() {
        validateTransactionListDisplayed()

        val beforeCount = transactionsDbAdapter.getTransactionsCount(TRANSACTIONS_ACCOUNT_UID)
        onView(withId(R.id.fab_create_transaction))
            .perform(click())

        onView(withId(R.id.input_transaction_name))
            .check(matches(isDisplayed()))
            .perform(typeText("Lunch"))

        closeSoftKeyboard()

        onView(withId(R.id.menu_save))
            .check(matches(isDisplayed()))
            .perform(click())
        onView(withText(R.string.title_add_transaction))
            .check(matches(isDisplayed()))

        assertToastDisplayed(R.string.toast_transaction_amount_required)

        val afterCount = transactionsDbAdapter.getTransactionsCount(TRANSACTIONS_ACCOUNT_UID)
        assertThat(afterCount).isEqualTo(beforeCount)
    }

    /**
     * Checks that a specific toast message is displayed
     *
     * @param toastString String that should be displayed
     */
    private fun assertToastDisplayed(toastString: Int) {
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).waitForIdle()
        onView(withText(toastString))
            .inRoot(withDecorView(not(transactionsActivity.window.decorView)))
            .check(matches(isDisplayed()))
    }

    private fun validateEditTransactionFields(transaction: Transaction) {
        onView(withId(R.id.input_transaction_name))
            .check(matches(withText(transaction.description)))

        val balance = transaction.getBalance(TRANSACTIONS_ACCOUNT_UID)
        val formatter = NumberFormat.getInstance(Locale.getDefault())
        formatter.minimumFractionDigits = 2
        formatter.maximumFractionDigits = 2
        onView(withId(R.id.input_transaction_amount))
            .check(matches(withText(formatter.format(balance.toDouble()))))
        onView(withId(R.id.input_date))
            .check(matches(withText(DATE_FORMATTER.print(transaction.timeMillis))))
        onView(withId(R.id.input_time))
            .check(matches(withText(TIME_FORMATTER.print(transaction.timeMillis))))
        onView(withId(R.id.notes))
            .check(matches(withText(transaction.note)))

        validateTimeInput(transaction.timeMillis)
    }

    //TODO: Add test for only one account but with double-entry enabled
    @Test
    fun testAddTransaction() {
        setDefaultTransactionType(TransactionType.DEBIT)
        validateTransactionListDisplayed()

        onView(withId(R.id.fab_create_transaction))
            .perform(click())

        onView(withId(R.id.input_transaction_name))
            .perform(typeText("Lunch"))
        closeSoftKeyboard()
        onView(withId(R.id.input_transaction_amount))
            .perform(typeText("899"))
        closeSoftKeyboard()
        onView(withId(R.id.input_transaction_type))
            .check(
                matches(
                    allOf(
                        isDisplayed(),
                        withText(R.string.label_receive)
                    )
                )
            )
            .perform(click())
            .check(matches(withText(R.string.label_spend)))

        val expectedValue = NumberFormat.getInstance().format(-899)
        onView(withId(R.id.input_transaction_amount))
            .check(matches(withText(expectedValue)))

        val transactionsCount = transactionCount
        onView(withId(R.id.menu_save))
            .perform(click())

        validateTransactionListDisplayed()

        val transactions = transactionsDbAdapter.getAllTransactionsForAccount(
            TRANSACTIONS_ACCOUNT_UID
        )
        assertThat(transactions).hasSize(2)
        val transaction = transactions[0]
        assertThat(transaction.splits).hasSize(2)

        assertThat(transactionCount).isEqualTo(transactionsCount + 1)
    }

    @Test
    fun testAddMultiCurrencyTransaction() {
        transactionsDbAdapter.deleteTransactionsForAccount(TRANSACTIONS_ACCOUNT_UID)

        val euro = getInstance("EUR")
        val euroAccount = Account("Euro Konto", euro)
        accountsDbAdapter.addRecord(euroAccount)

        val transactionCount = transactionsDbAdapter.getTransactionsCount(
            TRANSACTIONS_ACCOUNT_UID
        )
        setDefaultTransactionType(TransactionType.DEBIT)
        validateTransactionListDisplayed()

        onView(withId(R.id.fab_create_transaction))
            .perform(click())

        val transactionName = "Multicurrency lunch"
        onView(withId(R.id.input_transaction_name))
            .perform(typeText(transactionName))
        onView(withId(R.id.input_transaction_amount))
            .perform(typeText("10"))
        pressBack() //close calculator keyboard

        onView(withId(R.id.input_transfer_account_spinner))
            .perform(click())
        onView(withText(euroAccount.fullName))
            .check(matches(isDisplayed()))
            .perform(click())

        onView(withId(R.id.menu_save))
            .perform(click())

        onView(withText(R.string.msg_provide_exchange_rate))
            .check(matches(isDisplayed()))
        onView(withId(R.id.radio_converted_amount))
            .perform(click())
        onView(withId(R.id.input_converted_amount))
            .perform(typeText("5"))
        closeSoftKeyboard()
        onView(withId(BUTTON_POSITIVE)).perform(click())

        val allTransactions = transactionsDbAdapter.getAllTransactionsForAccount(
            TRANSACTIONS_ACCOUNT_UID
        )
        assertThat(allTransactions).hasSize(transactionCount + 1)
        val multiTrans = allTransactions[0]
        assertThat(multiTrans.splits).hasSize(2)
        val accountUID = assertThat(multiTrans.splits).extracting("accountUID", String::class.java)
        accountUID.contains(TRANSACTIONS_ACCOUNT_UID)
        accountUID.contains(euroAccount.uid)

        val euroSplit = multiTrans.getSplits(euroAccount.uid)[0]
        val expectedQty = Money("5", euro.currencyCode)
        val expectedValue = Money(BigDecimal.TEN, COMMODITY)
        assertThat(euroSplit.quantity).isEqualTo(expectedQty)
        assertThat(euroSplit.value).isEqualTo(expectedValue)

        val usdSplit = multiTrans.getSplits(TRANSACTIONS_ACCOUNT_UID)[0]
        assertThat(usdSplit.quantity).isEqualTo(expectedValue)
        assertThat(usdSplit.value).isEqualTo(expectedValue)
    }

    @Test
    fun testEditTransaction() {
        validateTransactionListDisplayed()

        onView(withId(R.id.edit_transaction))
            .perform(click())

        validateEditTransactionFields(transaction)

        val trnName = "Pasta"
        onView(withId(R.id.input_transaction_name))
            .perform(clearText(), typeText(trnName))
        onView(withId(R.id.menu_save))
            .perform(click())

        val editedTransaction = transactionsDbAdapter.getRecord(transaction.uid)
        assertThat(editedTransaction.description).isEqualTo(trnName)
        assertThat(editedTransaction.splits).hasSize(2)

        var split = transaction.getSplits(TRANSACTIONS_ACCOUNT_UID)[0]
        var editedSplit = editedTransaction.getSplits(TRANSACTIONS_ACCOUNT_UID)[0]
        assertThat(split.isEquivalentTo(editedSplit)).isTrue()

        split = transaction.getSplits(TRANSFER_ACCOUNT_UID)[0]
        editedSplit = editedTransaction.getSplits(TRANSFER_ACCOUNT_UID)[0]
        assertThat(split.isEquivalentTo(editedSplit)).isTrue()
    }

    /**
     * Tests that transactions splits are automatically balanced and an imbalance account will be created
     * This test case assumes that single entry is used
     */
    //TODO: move this to the unit tests
    fun testAutoBalanceTransactions() {
        setDoubleEntryEnabled(false)
        transactionsDbAdapter.deleteAllRecords()

        assertThat(transactionsDbAdapter.recordsCount).isZero()
        var imbalanceAcctUID = accountsDbAdapter.getImbalanceAccountUID(context, COMMODITY)
        assertThat(imbalanceAcctUID).isNull()

        validateTransactionListDisplayed()
        onView(withId(R.id.fab_create_transaction))
            .perform(click())
        onView(withId(R.id.fragment_transaction_form))
            .check(matches(isDisplayed()))

        onView(withId(R.id.input_transaction_name))
            .perform(typeText("Autobalance"))
        onView(withId(R.id.input_transaction_amount))
            .perform(typeText("499"))

        //no double entry so no split editor
        //TODO: check that the split drawable is not displayed
        onView(withId(R.id.menu_save))
            .perform(click())

        assertThat(transactionsDbAdapter.recordsCount).isOne()
        val transaction = transactionsDbAdapter.allTransactions[0]
        assertThat(transaction.splits).hasSize(2)
        imbalanceAcctUID = accountsDbAdapter.getImbalanceAccountUID(context, COMMODITY)
        assertThat(imbalanceAcctUID).isNotNull()
        assertThat(imbalanceAcctUID).isNotEmpty()
        assertThat(accountsDbAdapter.isHiddenAccount(imbalanceAcctUID))
            .isTrue() //imbalance account should be hidden in single entry mode

        assertThat(transaction.splits).extracting("accountUID", String::class.java)
            .contains(imbalanceAcctUID)
    }

    /**
     * Tests input of transaction splits using the split editor.
     * Also validates that the imbalance from the split editor will be automatically added as a split
     * //FIXME: find a more reliable way to test opening of the split editor
     */
    @Test
    fun testSplitEditor() {
        setDefaultTransactionType(TransactionType.DEBIT)
        transactionsDbAdapter.deleteAllRecords()

        //when we start there should be no imbalance account in the system
        var imbalanceAcctUID = accountsDbAdapter.getImbalanceAccountUID(context, COMMODITY)
        assertThat(imbalanceAcctUID).isNull()

        validateTransactionListDisplayed()
        onView(withId(R.id.fab_create_transaction))
            .perform(click())

        onView(withId(R.id.input_transaction_name))
            .perform(typeText("Autobalance"))
        onView(withId(R.id.input_transaction_amount))
            .perform(typeText("499"))
        closeSoftKeyboard()
        onView(withId(R.id.btn_split_editor))
            .perform(click())

        onView(withId(R.id.split_list_layout)).check(
            matches(
                allOf(
                    isDisplayed(),
                    hasDescendant(withId(R.id.input_split_amount))
                )
            )
        )

        onView(
            allOf(
                withId(R.id.input_split_amount),
                withText("-499")
            )
        ).perform(clearText())
        onView(
            allOf(
                withId(R.id.input_split_amount),
                withText("")
            )
        ).perform(typeText("400"))

        onView(withId(R.id.menu_save))
            .perform(click())
        //after we use split editor, we should not be able to toggle the transaction type
        onView(withId(R.id.input_transaction_type))
            .check(matches(not(isDisplayed())))

        onView(withId(R.id.menu_save))
            .perform(click())

        val transactions = transactionsDbAdapter.allTransactions
        assertThat(transactions).hasSize(1)

        val transaction = transactions[0]

        assertThat(transaction.splits).hasSize(3) //auto-balanced
        imbalanceAcctUID = accountsDbAdapter.getImbalanceAccountUID(
            context,
            COMMODITY
        )
        assertThat(imbalanceAcctUID).isNotNull()
        assertThat(imbalanceAcctUID).isNotEmpty()
        assertThat(accountsDbAdapter.isHiddenAccount(imbalanceAcctUID)).isFalse()

        //at least one split will belong to the imbalance account
        assertThat(transaction.splits).extracting("accountUID", String::class.java)
            .contains(imbalanceAcctUID)

        val imbalanceSplits = splitsDbAdapter.getSplitsForTransactionInAccount(
            transaction.uid,
            imbalanceAcctUID
        )
        assertThat(imbalanceSplits).hasSize(1)

        val split = imbalanceSplits[0]
        assertThat(split.value.toBigDecimal()).isEqualTo(BigDecimal("99.00"))
        assertThat(split.type).isEqualTo(TransactionType.CREDIT)
    }


    private fun setDoubleEntryEnabled(enabled: Boolean) {
        GnuCashApplication.getBookPreferences(context)
            .edit()
            .putBoolean(
                context.getString(R.string.key_use_double_entry),
                enabled
            )
            .apply()
    }

    @Test
    fun testDefaultTransactionType() {
        setDefaultTransactionType(TransactionType.CREDIT)

        onView(withId(R.id.fab_create_transaction))
            .perform(click())
        onView(withId(R.id.input_transaction_type)).check(
            matches(
                allOf(
                    isChecked(),
                    withText(R.string.label_spend)
                )
            )
        )
    }

    private fun setDefaultTransactionType(type: TransactionType) {
        GnuCashApplication.getBookPreferences(context)
            .edit()
            .putString(
                context.getString(R.string.key_default_transaction_type),
                type.value
            )
            .commit()
    }

    //FIXME: Improve on this test
    fun childAccountsShouldUseParentTransferAccountSetting() {
        val transferAccount = Account("New Transfer Acct")
        accountsDbAdapter.addRecord(transferAccount, DatabaseAdapter.UpdateMethod.insert)
        accountsDbAdapter.addRecord(
            Account("Higher account"),
            DatabaseAdapter.UpdateMethod.insert
        )

        val childAccount = Account("Child Account")
        childAccount.parentUID = TRANSACTIONS_ACCOUNT_UID
        accountsDbAdapter.addRecord(childAccount, DatabaseAdapter.UpdateMethod.insert)
        val contentValues = ContentValues()
        contentValues.put(
            DatabaseSchema.AccountEntry.COLUMN_DEFAULT_TRANSFER_ACCOUNT_UID,
            transferAccount.uid
        )
        accountsDbAdapter.updateRecord(TRANSACTIONS_ACCOUNT_UID, contentValues)

        val intent = Intent(transactionsActivity, TransactionsActivity::class.java)
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .setAction(Intent.ACTION_INSERT_OR_EDIT)
            .putExtra(UxArgument.SELECTED_ACCOUNT_UID, childAccount.uid)
        transactionsActivity.startActivity(intent)

        onView(withId(R.id.input_transaction_amount))
            .perform(typeText("1299"))
        clickOnView(R.id.menu_save)

        //if our transfer account has a transaction then the right transfer account was used
        val transactions =
            transactionsDbAdapter.getAllTransactionsForAccount(transferAccount.uid)
        assertThat(transactions).hasSize(1)
    }

    @Test
    fun testToggleTransactionType() {
        validateTransactionListDisplayed()
        onView(withId(R.id.edit_transaction))
            .perform(click())

        validateEditTransactionFields(transaction)

        onView(withId(R.id.input_transaction_type)).check(
            matches(
                allOf(
                    isDisplayed(),
                    withText(R.string.label_receive)
                )
            )
        ).perform(click())
            .check(matches(withText(R.string.label_spend)))

        onView(withId(R.id.input_transaction_amount))
            .check(matches(withText("-9.99")))

        onView(withId(R.id.menu_save))
            .perform(click())

        val transactions = transactionsDbAdapter.getAllTransactionsForAccount(
            TRANSACTIONS_ACCOUNT_UID
        )
        assertThat(transactions).hasSize(1)
        val trx = transactions[0]
        assertThat(trx.splits).hasSize(2) //auto-balancing of splits
        assertThat(trx.getBalance(TRANSACTIONS_ACCOUNT_UID).isNegative).isTrue()
    }

    @Test
    fun testOpenTransactionEditShouldNotModifyTransaction() {
        validateTransactionListDisplayed()

        onView(withId(R.id.edit_transaction))
            .perform(click())
        validateTimeInput(transactionTimeMillis)

        clickOnView(R.id.menu_save)

        val transactions = transactionsDbAdapter.getAllTransactionsForAccount(
            TRANSACTIONS_ACCOUNT_UID
        )

        assertThat(transactions).hasSize(1)
        val transaction = transactions[0]
        assertThat(TRANSACTION_NAME).isEqualTo(transaction.description)
        val expectedDate = transactionTimeMillis
        val trxDate = transaction.timeMillis
        assertThat(DATE_FORMATTER.print(expectedDate))
            .isEqualTo(DATE_FORMATTER.print(trxDate))
        assertThat(TIME_FORMATTER.print(expectedDate))
            .isEqualTo(TIME_FORMATTER.print(trxDate))

        val baseSplit = transaction.getSplits(TRANSACTIONS_ACCOUNT_UID)[0]
        val expectedAmount = Money(TRANSACTION_AMOUNT, CURRENCY_CODE)
        assertThat(baseSplit.value).isEqualTo(expectedAmount)
        assertThat(baseSplit.quantity).isEqualTo(expectedAmount)
        assertThat(baseSplit.type).isEqualTo(TransactionType.DEBIT)

        val transferSplit = transaction.getSplits(TRANSFER_ACCOUNT_UID)[0]
        assertThat(transferSplit.value).isEqualTo(expectedAmount)
        assertThat(transferSplit.quantity).isEqualTo(expectedAmount)
        assertThat(transferSplit.type).isEqualTo(TransactionType.CREDIT)
    }

    @Test
    fun testDeleteTransaction() {
        onView(withId(R.id.options_menu))
            .perform(click())
        onView(withText(R.string.menu_delete))
            .perform(click())

        assertThat(0).isEqualTo(
            transactionsDbAdapter.getTransactionsCount(TRANSACTIONS_ACCOUNT_UID)
        )
    }

    @Test
    fun testMoveTransaction() {
        val account = Account("Move account", COMMODITY)
        accountsDbAdapter.addRecord(account, DatabaseAdapter.UpdateMethod.insert)

        assertThat(
            transactionsDbAdapter.getAllTransactionsForAccount(account.uid)
        ).isEmpty()

        onView(withId(R.id.options_menu))
            .perform(click())
        onView(withText(R.string.menu_move_transaction))
            .perform(click())

        onView(withId(BUTTON_POSITIVE)).perform(click())

        assertThat(
            transactionsDbAdapter.getAllTransactionsForAccount(TRANSACTIONS_ACCOUNT_UID)
        ).isEmpty()

        assertThat(
            transactionsDbAdapter.getAllTransactionsForAccount(account.uid)
        ).hasSize(1)
    }

    /**
     * This test edits a transaction from within an account and removes the split belonging to that account.
     * The account should then have a balance of 0 and the transaction has "moved" to another account
     */
    @Test
    fun editingSplit_shouldNotSetAmountToZero() {
        transactionsDbAdapter.deleteAllRecords()

        val account = Account("Z Account", COMMODITY)
        accountsDbAdapter.addRecord(account, DatabaseAdapter.UpdateMethod.insert)

        //create new transaction "Transaction Acct" --> "Transfer Account"
        onView(withId(R.id.fab_create_transaction))
            .perform(click())
        onView(withId(R.id.input_transaction_name))
            .perform(typeText("Test Split"))
        onView(withId(R.id.input_transaction_amount))
            .perform(typeText("1024"))

        onView(withId(R.id.menu_save))
            .perform(click())

        assertThat(transactionsDbAdapter.getTransactionsCount(TRANSACTIONS_ACCOUNT_UID)).isOne()

        sleep(500)
        onView(withText("Test Split")).perform(click())
        onView(withId(R.id.fab_edit_transaction))
            .perform(click())

        onView(withId(R.id.btn_split_editor))
            .perform(click())

        onView(withText(TRANSACTIONS_ACCOUNT_NAME))
            .perform(click())
        onView(withText(account.fullName)).perform(click())

        onView(withId(R.id.menu_save))
            .perform(click())
        onView(withId(R.id.menu_save))
            .perform(click())

        assertThat(
            transactionsDbAdapter.getTransactionsCount(
                TRANSACTIONS_ACCOUNT_UID
            )
        ).isZero()

        assertThat(
            accountsDbAdapter.getAccountBalance(account)
        )
            .isEqualTo(Money("1024", CURRENCY_CODE))
    }

    @Test
    fun testDuplicateTransaction() {
        assertThat(
            transactionsDbAdapter.getAllTransactionsForAccount(TRANSACTIONS_ACCOUNT_UID)
        ).hasSize(1)

        onView(withId(R.id.options_menu))
            .perform(click())
        onView(withText(R.string.menu_duplicate_transaction))
            .perform(click())

        val dummyAccountTrns =
            transactionsDbAdapter.getAllTransactionsForAccount(TRANSACTIONS_ACCOUNT_UID)
        assertThat(dummyAccountTrns).hasSize(2)

        assertThat(dummyAccountTrns[0].description)
            .isEqualTo(dummyAccountTrns[1].description)
        assertThat(dummyAccountTrns[0].timeMillis)
            .isNotEqualTo(dummyAccountTrns[1].timeMillis)
    }

    //TODO: add normal transaction recording
    @Test
    fun testLegacyIntentTransactionRecording() {
        val beforeCount = transactionsDbAdapter.getTransactionsCount(TRANSACTIONS_ACCOUNT_UID)
        val transactionIntent = Intent(Intent.ACTION_INSERT)
            .setType(Transaction.MIME_TYPE)
            .putExtra(Intent.EXTRA_TITLE, "Power intents")
            .putExtra(Intent.EXTRA_TEXT, "Intents for sale")
            .putExtra(Transaction.EXTRA_AMOUNT, BigDecimal.valueOf(4.99))
            .putExtra(Transaction.EXTRA_ACCOUNT_UID, TRANSACTIONS_ACCOUNT_UID)
            .putExtra(Transaction.EXTRA_TRANSACTION_TYPE, TransactionType.DEBIT.name)
            .putExtra(Account.EXTRA_CURRENCY_CODE, "USD")

        TransactionRecorder().onReceive(transactionsActivity, transactionIntent)

        val afterCount = transactionsDbAdapter.getTransactionsCount(TRANSACTIONS_ACCOUNT_UID)

        assertThat(beforeCount + 1).isEqualTo(afterCount)

        val transactions = transactionsDbAdapter.getAllTransactionsForAccount(
            TRANSACTIONS_ACCOUNT_UID
        )

        for (transaction in transactions) {
            if (transaction.description == "Power intents") {
                assertThat(transaction.note).isEqualTo("Intents for sale")
                assertThat(
                    transaction.getBalance(
                        TRANSACTIONS_ACCOUNT_UID
                    ).toDouble()
                ).isEqualTo(4.99)
            }
        }
    }

    /**
     * Opening a transactions and then hitting save button without changing anything should have no side-effects
     * This is similar to the test @[.testOpenTransactionEditShouldNotModifyTransaction]
     * with the difference that this test checks multi-currency transactions
     */
    @Test
    fun openingAndSavingMultiCurrencyTransaction_shouldNotModifyTheSplits() {
        val bgnCommodity = commoditiesDbAdapter.getCommodity("BGN")!!
        val account = Account("Zen Account", bgnCommodity)

        accountsDbAdapter.addRecord(account)

        onView(withId(R.id.fab_create_transaction))
            .perform(click())
        val trnDescription = "Multi-currency trn"
        onView(withId(R.id.input_transaction_name))
            .perform(typeText(trnDescription))
        onView(withId(R.id.input_transaction_name))
            .perform(replaceText(trnDescription)) //Fix auto-correct.
        onView(withId(R.id.input_transaction_amount))
            .perform(typeText("10"))
        closeSoftKeyboard()

        onView(withId(R.id.input_transfer_account_spinner))
            .perform(click())
        onView(withText(account.fullName)).perform(click())

        //at this point, the transfer funds dialog should be shown
        onView(withText(R.string.msg_provide_exchange_rate))
            .check(matches(isDisplayed()))
        onView(withId(R.id.radio_converted_amount))
            .perform(click())
        onView(withId(R.id.input_converted_amount))
            .perform(typeText("5"))

        closeSoftKeyboard()
        onView(withId(BUTTON_POSITIVE))
            .perform(click()) //close currency exchange dialog
        onView(withId(R.id.menu_save))
            .perform(click()) //save transaction

        val transactions = transactionsDbAdapter.getAllTransactionsForAccount(account.uid)
        assertThat(transactions).hasSize(1)
        var transaction = transactions[0]
        assertThat(transaction.description).isEqualTo(trnDescription)
        assertThat(transaction.splits).hasSize(2)
        val accountUID = assertThat(transaction.splits).extracting("accountUID", String::class.java)
        accountUID.contains(account.uid)
        accountUID.contains(baseAccount.uid)

        onView(
            allOf(
                withParent(hasDescendant(withText(trnDescription))),
                withId(R.id.edit_transaction)
            )
        ).perform(click())

        //do nothing to the transaction, just save it
        onView(withId(R.id.menu_save))
            .perform(click())

        transaction = transactionsDbAdapter.getRecord(transaction.uid)

        val baseSplit = transaction.getSplits(baseAccount.uid)[0]
        val expectedValueAmount = Money(BigDecimal.TEN, COMMODITY)
        assertThat(baseSplit.value).isEqualTo(expectedValueAmount)
        assertThat(baseSplit.quantity).isEqualTo(expectedValueAmount)

        val transferSplit = transaction.getSplits(account.uid)[0]
        val convertedQuantity = Money("5", "BGN")
        assertThat(transferSplit.value).isEqualTo(expectedValueAmount)
        assertThat(transferSplit.quantity).isEqualTo(convertedQuantity)
    }

    /**
     * If a multi-currency transaction is edited so that it is no longer multicurrency, then the
     * values for split and quantity should be adjusted accordingly so that they are consistent
     *
     *
     * Basically the test works like this:
     *
     *  1. Create a multi-currency transaction
     *  1. Change the transfer account so that both splits are of the same currency
     *  1. We now expect both the values and quantities of the splits to be the same
     */
    @Test
    fun testEditingTransferAccountOfMultiCurrencyTransaction() {
        transactionsDbAdapter.deleteAllRecords() //clean slate
        val euroCommodity = commoditiesDbAdapter.getCommodity("EUR")!!
        val euroAccount = Account("Euro Account", euroCommodity)

        accountsDbAdapter.addRecord(euroAccount)

        val expectedValue = Money(BigDecimal.TEN, COMMODITY)
        val expectedQty = Money("5", "EUR")

        val trnDescription = "Multicurrency Test Trn"
        val multiTransaction = Transaction(trnDescription)
        val split1 = Split(expectedValue, TRANSACTIONS_ACCOUNT_UID)
        split1.type = TransactionType.DEBIT
        val split2 = Split(expectedValue, expectedQty, euroAccount.uid)
        split2.type = TransactionType.CREDIT
        multiTransaction.addSplit(split1)
        multiTransaction.addSplit(split2)
        multiTransaction.commodity = COMMODITY

        transactionsDbAdapter.addRecord(multiTransaction)

        val savedTransaction = transactionsDbAdapter.getRecord(multiTransaction.uid)
        assertThat(savedTransaction.splits).extracting("quantity", Money::class.java).contains(expectedQty)
        assertThat(savedTransaction.splits).extracting("value", Money::class.java).contains(expectedValue)

        refreshTransactionsList()
        onView(withText(trnDescription))
            .check(matches(isDisplayed())) //transaction was added
        onView(
            allOf(
                withParent(hasDescendant(withText(trnDescription))),
                withId(R.id.edit_transaction)
            )
        ).perform(click())

        //now change the transfer account to be no longer multi-currency
        onView(withId(R.id.input_transfer_account_spinner))
            .check(matches(isDisplayed()))
        onView(withId(R.id.input_transfer_account_spinner))
            .perform(click())
        onView(withText(transferAccount.fullName))
            .perform(click())

        onView(withId(R.id.menu_save))
            .perform(click())

        //no splits should be in the euro account anymore
        val euroTransxns =
            transactionsDbAdapter.getAllTransactionsForAccount(euroAccount.uid)
        assertThat(euroTransxns).hasSize(0)

        val transferAcctTrns = transactionsDbAdapter.getAllTransactionsForAccount(
            transferAccount.uid
        )
        assertThat(transferAcctTrns).hasSize(1)

        val singleCurrencyTrn = transferAcctTrns[0]
        assertThat(singleCurrencyTrn.uid)
            .isEqualTo(multiTransaction.uid) //should be the same one, just different splits

        //the crux of the test. All splits should now have value and quantity of USD $10
        val allSplits = singleCurrencyTrn.splits
        val accountUID = assertThat(allSplits).extracting("accountUID", String::class.java)
        accountUID.contains(transferAccount.uid)
        accountUID.doesNotContain(euroAccount.uid)
        val value = assertThat(allSplits).extracting("value", Money::class.java)
        value.contains(expectedValue)
        value.doesNotContain(expectedQty)
        val quantity = assertThat(allSplits).extracting("quantity", Money::class.java)
        quantity.contains(expectedValue)
        quantity.doesNotContain(expectedQty)
    }

    /**
     * In this test we check that editing a transaction and switching the transfer account to one
     * which is of a different currency and then back again should not have side-effects.
     * The split value and quantity should remain consistent.
     */
    @Test
    fun editingTransferAccount_shouldKeepSplitAmountsConsistent() {
        transactionsDbAdapter.deleteAllRecords() //clean slate
        val currencyOther = if ("EUR" == COMMODITY.currencyCode) "USD" else "EUR"
        val commodityOther = commoditiesDbAdapter.getCommodity(currencyOther)!!
        val accountOther = Account("Other Account", commodityOther)

        accountsDbAdapter.addRecord(accountOther)

        val expectedValue = Money(BigDecimal.TEN, COMMODITY)
        val expectedQty = Money("5", commodityOther)

        val trnDescription = "Multicurrency Test Trn"
        val multiTransaction = Transaction(trnDescription)
        val split1 = Split(expectedValue, TRANSACTIONS_ACCOUNT_UID)
        split1.type = TransactionType.CREDIT
        val split2 = Split(expectedValue, expectedQty, accountOther.uid)
        split2.type = TransactionType.DEBIT
        multiTransaction.addSplit(split1)
        multiTransaction.addSplit(split2)
        multiTransaction.commodity = COMMODITY

        transactionsDbAdapter.addRecord(multiTransaction)

        val savedTransaction = transactionsDbAdapter.getRecord(multiTransaction.uid)
        assertThat(savedTransaction.splits).extracting("quantity", Money::class.java)
            .contains(expectedQty)
        assertThat(savedTransaction.splits).extracting("value", Money::class.java)
            .contains(expectedValue)

        assertThat(
            savedTransaction.getSplits(TRANSACTIONS_ACCOUNT_UID)[0]
                .isEquivalentTo(multiTransaction.getSplits(TRANSACTIONS_ACCOUNT_UID)[0])
        ).isTrue()

        refreshTransactionsList()

        //open transaction for editing
        onView(withText(trnDescription))
            .check(matches(isDisplayed())) //transaction was added
        onView(
            allOf(
                withParent(hasDescendant(withText(trnDescription))),
                withId(R.id.edit_transaction)
            )
        ).perform(click())

        onView(withId(R.id.input_transfer_account_spinner))
            .perform(click())
        onView(withText(TRANSFER_ACCOUNT_NAME)).perform(click())

        onView(withId(R.id.input_transfer_account_spinner))
            .perform(click())
        onView(withText(accountOther.fullName)).perform(click())
        // Exchange dialog should be shown already.
        onView(withId(R.id.radio_converted_amount))
            .check(matches(isDisplayed()))
            .perform(click())
        onView(withId(R.id.input_converted_amount))
            .check(matches(isDisplayed()))
            .perform(typeText("5"))
        closeSoftKeyboard()
        onView(withId(BUTTON_POSITIVE)).perform(click())

        onView(withId(R.id.input_transfer_account_spinner))
            .perform(click())
        onView(withText(TRANSFER_ACCOUNT_NAME)).perform(click())

        onView(withId(R.id.menu_save))
            .perform(click())

        val editedTransaction = transactionsDbAdapter.getRecord(multiTransaction.uid)
        assertThat(
            editedTransaction.getSplits(TRANSACTIONS_ACCOUNT_UID)[0]
                .isEquivalentTo(savedTransaction.getSplits(TRANSACTIONS_ACCOUNT_UID)[0])
        ).isTrue()

        val firstAcctBalance = accountsDbAdapter.getAccountBalance(TRANSACTIONS_ACCOUNT_UID)
        assertThat(firstAcctBalance)
            .isEqualTo(editedTransaction.getBalance(TRANSACTIONS_ACCOUNT_UID))

        val transferBalance = accountsDbAdapter.getAccountBalance(TRANSFER_ACCOUNT_UID)
        assertThat(transferBalance)
            .isEqualTo(editedTransaction.getBalance(TRANSFER_ACCOUNT_UID))

        assertThat(editedTransaction.getBalance(TRANSFER_ACCOUNT_UID))
            .isEqualTo(expectedValue)

        val transferAcctSplit = editedTransaction.getSplits(TRANSFER_ACCOUNT_UID)[0]
        assertThat(transferAcctSplit.quantity).isEqualTo(expectedValue)
        assertThat(transferAcctSplit.value).isEqualTo(expectedValue)
    }

    /**
     * Simple wrapper for clicking on views with espresso
     *
     * @param viewId View resource ID
     */
    private fun clickOnView(viewId: Int) {
        onView(withId(viewId)).perform(click())
    }

    /**
     * Refresh the account list fragment
     */
    private fun refreshTransactionsList() {
        try {
            activityRule.runOnUiThread { transactionsActivity.refresh() }
            sleep(1000)
        } catch (throwable: Throwable) {
            System.err.println("Failed to refresh transactions")
        }
    }

    companion object {
        private const val TRANSACTION_AMOUNT = "9.99"
        private const val TRANSACTION_NAME = "Pizza"
        private const val TRANSACTIONS_ACCOUNT_UID = "transactions-account"
        private const val TRANSACTIONS_ACCOUNT_NAME = "Transactions Account"

        private const val TRANSFER_ACCOUNT_NAME = "Transfer account"
        private const val TRANSFER_ACCOUNT_UID = "transfer_account"
        private const val CURRENCY_CODE = "USD"

        private var COMMODITY: Commodity = Commodity.DEFAULT_COMMODITY

        private lateinit var accountsDbAdapter: AccountsDbAdapter
        private lateinit var transactionsDbAdapter: TransactionsDbAdapter
        private lateinit var splitsDbAdapter: SplitsDbAdapter
        private lateinit var commoditiesDbAdapter: CommoditiesDbAdapter

        @ClassRule
        @JvmField
        val disableAnimationsRule = DisableAnimationsRule()

        @BeforeClass
        @JvmStatic
        fun prepareTestCase() {
            preventFirstRunDialogs()

            accountsDbAdapter = AccountsDbAdapter.getInstance()
            transactionsDbAdapter = accountsDbAdapter.transactionsDbAdapter
            splitsDbAdapter = transactionsDbAdapter.splitsDbAdapter
            commoditiesDbAdapter = accountsDbAdapter.commoditiesDbAdapter
            COMMODITY = commoditiesDbAdapter.getCommodity(CURRENCY_CODE)!!
        }
    }
}
