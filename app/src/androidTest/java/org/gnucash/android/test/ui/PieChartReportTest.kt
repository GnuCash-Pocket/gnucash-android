/*
 * Copyright (c) 2015 Oleksandr Tyshkovets <olexandr.tyshkovets@gmail.com>
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
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.ViewAction
import androidx.test.espresso.action.GeneralClickAction
import androidx.test.espresso.action.Press
import androidx.test.espresso.action.Tap
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.rule.ActivityTestRule
import androidx.test.rule.GrantPermissionRule
import org.assertj.core.api.Assertions.assertThat
import org.gnucash.android.R
import org.gnucash.android.app.GnuCashApplication
import org.gnucash.android.db.adapter.AccountsDbAdapter
import org.gnucash.android.db.adapter.BooksDbAdapter
import org.gnucash.android.db.adapter.DatabaseAdapter
import org.gnucash.android.db.adapter.TransactionsDbAdapter
import org.gnucash.android.importer.GncXmlImporter
import org.gnucash.android.model.AccountType
import org.gnucash.android.model.Commodity
import org.gnucash.android.model.Money
import org.gnucash.android.model.Split
import org.gnucash.android.model.Transaction
import org.gnucash.android.model.TransactionType
import org.gnucash.android.test.ui.util.DisableAnimationsRule
import org.gnucash.android.ui.report.BaseReportFragment
import org.gnucash.android.ui.report.ReportsActivity
import org.gnucash.android.ui.report.piechart.PieChartFragment
import org.gnucash.android.util.BookUtils
import org.joda.time.LocalDateTime
import org.junit.After
import org.junit.AfterClass
import org.junit.Before
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import java.math.BigDecimal
import java.util.Locale

class PieChartReportTest : GnuAndroidTest() {

    @Rule
    @JvmField
    val activityRule = ActivityTestRule(ReportsActivity::class.java)

    @Rule
    @JvmField
    val animationPermissionsRule =
        GrantPermissionRule.grant(Manifest.permission.SET_ANIMATION_SCALE)

    private lateinit var reportsActivity: ReportsActivity

    @Before
    fun setUp() {
        transactionsDbAdapter.deleteAllRecords()
        reportsActivity = activityRule.activity
        assertThat(accountsDbAdapter.recordsCount)
            .isGreaterThan(20) //lots of accounts in the default
        onView(withId(R.id.btn_pie_chart))
            .check(matches(isDisplayed()))
            .perform(click())
    }

    /**
     * Add a transaction for the current month in order to test the report view
     */
    private fun addTransactionForCurrentMonth() {
        val transaction = Transaction(TRANSACTION_NAME)
        transaction.setTime(System.currentTimeMillis())

        val split = Split(
            Money(BigDecimal.valueOf(TRANSACTION_AMOUNT), commodity), DINING_EXPENSE_ACCOUNT_UID
        )
        split.type = TransactionType.DEBIT

        transaction.addSplit(split)
        transaction.addSplit(split.createPair(CASH_IN_WALLET_ASSET_ACCOUNT_UID))

        transactionsDbAdapter.addRecord(transaction, DatabaseAdapter.UpdateMethod.insert)
    }

    /**
     * Add a transactions for the previous month for testing pie chart
     *
     * @param minusMonths Number of months prior
     */
    private fun addTransactionForPreviousMonth(minusMonths: Int) {
        val transaction = Transaction(TRANSACTION2_NAME)
        transaction.setTime(LocalDateTime().minusMonths(minusMonths).toDateTime().millis)

        val split = Split(
            Money(BigDecimal.valueOf(TRANSACTION2_AMOUNT), commodity), BOOKS_EXPENSE_ACCOUNT_UID
        )
        split.type = TransactionType.DEBIT

        transaction.addSplit(split)
        transaction.addSplit(split.createPair(CASH_IN_WALLET_ASSET_ACCOUNT_UID))

        transactionsDbAdapter.addRecord(transaction, DatabaseAdapter.UpdateMethod.insert)
    }

    @Test
    fun testNoData() {
        onView(withId(R.id.pie_chart)).perform(click())
        onView(withId(R.id.selected_chart_slice))
            .check(matches(withText(R.string.label_select_pie_slice_to_see_details)))
    }

    @Test
    fun testSelectingValue() {
        addTransactionForCurrentMonth()
        addTransactionForPreviousMonth(1)
        assertThat(transactionsDbAdapter.recordsCount).isGreaterThan(1)
        refreshReport()

        onView(withId(R.id.pie_chart)).perform(clickXY(Position.BEGIN, Position.MIDDLE))
        val percent =
            ((TRANSACTION_AMOUNT * 100) / (TRANSACTION_AMOUNT + TRANSACTION2_AMOUNT)).toFloat()
        val selectedText = String.format(
            Locale.US,
            BaseReportFragment.SELECTED_VALUE_PATTERN,
            DINING_EXPENSE_ACCOUNT_NAME,
            TRANSACTION_AMOUNT,
            percent
        )
        onView(withId(R.id.selected_chart_slice))
            .check(matches(withText(selectedText)))
    }

    @Test
    fun testSpinner() {
        val split = Split(
            Money(BigDecimal.valueOf(TRANSACTION3_AMOUNT), commodity),
            GIFTS_RECEIVED_INCOME_ACCOUNT_UID
        )
        val transaction = Transaction(TRANSACTION3_NAME)
        transaction.addSplit(split)
        transaction.addSplit(split.createPair(CASH_IN_WALLET_ASSET_ACCOUNT_UID))

        transactionsDbAdapter.addRecord(transaction, DatabaseAdapter.UpdateMethod.insert)

        refreshReport()

        Thread.sleep(1000)

        onView(withId(R.id.report_account_type_spinner))
            .perform(click())
        onView(withText(AccountType.INCOME.name)).perform(click())
        onView(withId(R.id.pie_chart))
            .perform(clickXY(Position.BEGIN, Position.MIDDLE))
        val selectedText = String.format(
            PieChartFragment.SELECTED_VALUE_PATTERN,
            GIFTS_RECEIVED_INCOME_ACCOUNT_NAME,
            TRANSACTION3_AMOUNT,
            100f
        )
        onView(withId(R.id.selected_chart_slice))
            .check(matches(withText(selectedText)))

        onView(withId(R.id.report_account_type_spinner))
            .perform(click())
        onView(withText(AccountType.EXPENSE.name))
            .perform(click())

        onView(withId(R.id.pie_chart)).perform(click())
        onView(withId(R.id.selected_chart_slice)).check(
            matches(withText(R.string.label_select_pie_slice_to_see_details))
        )
    }

    enum class Position {
        BEGIN {
            override fun getPosition(viewPos: Int, viewLength: Int): Float {
                return viewPos + (viewLength * 0.15f)
            }
        },
        MIDDLE {
            override fun getPosition(viewPos: Int, viewLength: Int): Float {
                return viewPos + (viewLength * 0.5f)
            }
        },
        END {
            override fun getPosition(viewPos: Int, viewLength: Int): Float {
                return viewPos + (viewLength * 0.85f)
            }
        };

        abstract fun getPosition(widgetPos: Int, widgetLength: Int): Float
    }

    /**
     * Refresh reports
     */
    private fun refreshReport() {
        try {
            activityRule.runOnUiThread { reportsActivity.refresh() }
            sleep(1000)
        } catch (t: Throwable) {
            System.err.println("Failed to refresh reports")
        }
    }

    @After
    fun tearDown() {
        reportsActivity.finish()
    }

    companion object {
        private const val TRANSACTION_NAME = "Pizza"
        private const val TRANSACTION_AMOUNT = 9.99

        private const val TRANSACTION2_NAME = "1984"
        private const val TRANSACTION2_AMOUNT = 12.49

        private const val TRANSACTION3_NAME = "Nice gift"
        private const val TRANSACTION3_AMOUNT = 2000.00

        private const val CASH_IN_WALLET_ASSET_ACCOUNT_UID = "b687a487849470c25e0ff5aaad6a522b"

        private const val DINING_EXPENSE_ACCOUNT_UID = "62922c5ccb31d6198259739d27d858fe"
        private const val DINING_EXPENSE_ACCOUNT_NAME = "Dining"

        private const val BOOKS_EXPENSE_ACCOUNT_UID = "a8b342435aceac7c3cac214f9385dd72"
        private const val BOOKS_EXPENSE_ACCOUNT_NAME = "Books"

        private const val GIFTS_RECEIVED_INCOME_ACCOUNT_UID = "b01950c0df0890b6543209d51c8e0b0f"
        private const val GIFTS_RECEIVED_INCOME_ACCOUNT_NAME = "Gifts Received"

        private lateinit var commodity: Commodity
        private lateinit var accountsDbAdapter: AccountsDbAdapter
        private lateinit var transactionsDbAdapter: TransactionsDbAdapter
        private lateinit var testBookUID: String
        private lateinit var oldActiveBookUID: String

        @ClassRule
        @JvmField
        val disableAnimationsRule = DisableAnimationsRule()

        @BeforeClass
        @JvmStatic
        fun prepareTestCase() {
            val context = GnuCashApplication.getAppContext()
            preventFirstRunDialogs(context)
            oldActiveBookUID = GnuCashApplication.getActiveBookUID()!!
            testBookUID = GncXmlImporter.parse(
                context,
                context.resources.openRawResource(R.raw.default_accounts)
            )

            BookUtils.loadBook(context, testBookUID)
            accountsDbAdapter = AccountsDbAdapter.getInstance()
            transactionsDbAdapter = accountsDbAdapter.transactionsDbAdapter

            commodity = accountsDbAdapter.commoditiesDbAdapter.getCurrency("USD")!!

            accountsDbAdapter.commoditiesDbAdapter.setDefaultCurrencyCode(commodity.currencyCode)
        }


        fun clickXY(horizontal: Position, vertical: Position): ViewAction {
            return GeneralClickAction(
                Tap.SINGLE,
                { view ->
                    val xy = IntArray(2)
                    view.getLocationOnScreen(xy)

                    val x = horizontal.getPosition(xy[0], view.width)
                    val y = vertical.getPosition(xy[1], view.height)
                    floatArrayOf(x, y)
                },
                Press.FINGER
            )
        }

        @AfterClass
        @JvmStatic
        fun cleanup() {
            val booksDbAdapter = BooksDbAdapter.getInstance()
            booksDbAdapter.setActive(oldActiveBookUID)
            booksDbAdapter.deleteRecord(testBookUID)
        }
    }
}
