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
import android.content.Intent
import android.preference.PreferenceManager
import android.view.View
import androidx.test.espresso.Espresso.onData
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.openActionBarOverflowOrOptionsMenu
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.action.ViewActions.clearText
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.action.ViewActions.swipeRight
import androidx.test.espresso.action.ViewActions.typeText
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.hasDescendant
import androidx.test.espresso.matcher.ViewMatchers.isChecked
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isEnabled
import androidx.test.espresso.matcher.ViewMatchers.isNotChecked
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withParent
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.rule.ActivityTestRule
import androidx.test.rule.GrantPermissionRule
import org.assertj.core.api.Assertions.assertThat
import org.gnucash.android.R
import org.gnucash.android.db.adapter.AccountsDbAdapter
import org.gnucash.android.db.adapter.SplitsDbAdapter
import org.gnucash.android.db.adapter.TransactionsDbAdapter
import org.gnucash.android.model.Account
import org.gnucash.android.model.AccountType
import org.gnucash.android.model.Commodity
import org.gnucash.android.model.Money
import org.gnucash.android.model.Split
import org.gnucash.android.model.Transaction
import org.gnucash.android.receivers.AccountCreator
import org.gnucash.android.test.ui.util.DisableAnimationsRule
import org.gnucash.android.ui.account.AccountsActivity
import org.gnucash.android.ui.adapter.AccountTypesAdapter
import org.hamcrest.Description
import org.hamcrest.Matcher
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.instanceOf
import org.hamcrest.Matchers.`is`
import org.hamcrest.Matchers.not
import org.hamcrest.TypeSafeMatcher
import org.junit.After
import org.junit.Before
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import java.math.BigDecimal

class AccountsActivityTest : GnuAndroidTest() {
    private lateinit var accountsActivity: AccountsActivity

    @Rule
    @JvmField
    val animationPermissionsRule =
        GrantPermissionRule.grant(Manifest.permission.SET_ANIMATION_SCALE)

    @Rule
    @JvmField
    val activityRule = ActivityTestRule(AccountsActivity::class.java)

    @Before
    fun setUp() {
        accountsActivity = activityRule.activity

        accountsDbAdapter.deleteAllRecords() //clear the data

        val simpleAccount =
            Account(SIMPLE_ACCOUNT_NAME, Commodity.getInstance(ACCOUNTS_CURRENCY_CODE))
        simpleAccount.setUID(SIMPLE_ACCOUNT_UID)
        accountsDbAdapter.insert(simpleAccount)

        refreshAccountsList()
    }

    @After
    fun tearDown() {
        accountsActivity.finish()
    }

    fun testDisplayAccountsList() {
        AccountsActivity.createDefaultAccounts(accountsActivity, "EUR")
        accountsActivity.recreate()

        refreshAccountsList()
        sleep(1000)
        onView(withText("Assets")).perform(scrollTo())
        onView(withText("Expenses")).perform(click())
        onView(withText("Books")).perform(scrollTo())
    }

    @Test
    fun testSearchAccounts() {
        val SEARCH_ACCOUNT_NAME = "Search Account"

        val account = Account(SEARCH_ACCOUNT_NAME)
        account.parentUID = SIMPLE_ACCOUNT_UID
        accountsDbAdapter.insert(account)

        // before search query
        onView(withText(SIMPLE_ACCOUNT_NAME))
            .check(matches(isDisplayed()))
        onView(withText(SEARCH_ACCOUNT_NAME))
            .check(doesNotExist())

        //enter search query
        onView(withId(R.id.menu_search)).perform(click())
        onView(withId(R.id.search_src_text))
            .perform(typeText(SEARCH_ACCOUNT_NAME.substring(0, 2)))
        sleep(100) //give search filter time to finish
        onView(withText(SIMPLE_ACCOUNT_NAME))
            .check(doesNotExist())
        onView(withText(SEARCH_ACCOUNT_NAME))
            .check(matches(isDisplayed()))

        // same as before search query
        onView(withId(R.id.search_src_text)).perform(clearText())
        sleep(100) //give search filter time to finish
        onView(withText(SIMPLE_ACCOUNT_NAME))
            .check(matches(isDisplayed()))
        onView(withText(SEARCH_ACCOUNT_NAME))
            .check(doesNotExist())
    }

    /**
     * Tests that an account can be created successfully and that the account list is sorted alphabetically.
     */
    @Test
    fun testCreateAccount() {
        assertThat(accountsDbAdapter.allRecords).hasSize(1)
        onView(allOf(isDisplayed(), withId(R.id.fab_create_account))).perform(click())
        sleep(1000)

        val NEW_ACCOUNT_NAME = "A New Account"
        onView(withId(R.id.input_account_name))
            .perform(typeText(NEW_ACCOUNT_NAME), closeSoftKeyboard())
        onView(withId(R.id.placeholder_status))
            .check(matches(isNotChecked()))
            .perform(click())

        onView(withId(R.id.menu_save)).perform(click())

        val accounts = accountsDbAdapter.allRecords
        assertThat(accounts).isNotNull()
        assertThat(accounts).hasSize(2)
        val newestAccount = accounts[1] //because of db _id sorting

        assertThat(newestAccount.name).isEqualTo(NEW_ACCOUNT_NAME)
        assertThat(newestAccount.commodity).isEqualTo(Commodity.DEFAULT_COMMODITY)
        assertThat(newestAccount.isPlaceholder).isTrue()
    }

    @Test
    fun should_IncludeFutureTransactionsInAccountBalance() {
        val transaction = Transaction("Future transaction")
        val split1 = Split(Money("4.15", ACCOUNTS_CURRENCY_CODE), SIMPLE_ACCOUNT_UID)
        transaction.addSplit(split1)
        transaction.time = System.currentTimeMillis() + 4815162342L
        transactionsDbAdapter.addRecord(transaction)

        refreshAccountsList()

        onView(first(withText(containsString("4.15"))))
            .check(matches(isDisplayed()))
    }

    @Test
    fun testChangeParentAccount() {
        val accountName = "Euro Account"
        val account = Account(accountName, Commodity.EUR)
        accountsDbAdapter.insert(account)

        refreshAccountsList()

        onView(withText(accountName)).perform(click())
        openActionBarOverflowOrOptionsMenu(accountsActivity)
        onView(withText(R.string.title_edit_account))
            .perform(click())
        onView(withId(R.id.fragment_account_form))
            .check(matches(isDisplayed()))
        closeSoftKeyboard()
        onView(withId(R.id.checkbox_parent_account))
            .perform(scrollTo())
            .check(matches(isNotChecked()))
            .perform(click())

        // FIXME: explicitly select the parent account
        onView(withId(R.id.input_parent_account))
            .check(matches(isEnabled())).perform(click())

        onView(withText(SIMPLE_ACCOUNT_NAME)).perform(click())

        onView(withId(R.id.menu_save)).perform(click())

        val editedAccount = accountsDbAdapter.getRecord(account.uid)
        val parentUID = editedAccount.parentUID

        assertThat(parentUID).isNotNull()
        assertThat(parentUID).isEqualTo(SIMPLE_ACCOUNT_UID)
    }

    /**
     * When creating a sub-account (starting from within another account), if we change the account
     * type to another type with no accounts of that type, then the parent account list should be hidden.
     * The account which is then created is not a sub-account, but rather a top-level account
     */
    @Test
    fun shouldHideParentAccountViewWhenNoParentsExist() {
        val textTrading =
            context.resources.getStringArray(R.array.account_type_entry_values)[AccountType.TRADING.labelIndex]
        val labelTrading = AccountTypesAdapter.Label(AccountType.TRADING, textTrading)

        onView(allOf(withText(SIMPLE_ACCOUNT_NAME), isDisplayed()))
            .perform(click())
        onView(withId(R.id.fragment_transaction_list))
            .perform(swipeRight())
        onView(withId(R.id.fab_create_transaction))
            .check(matches(isDisplayed())).perform(click())
        sleep(1000)
        onView(withId(R.id.checkbox_parent_account))
            .check(matches(allOf(isChecked())))
        onView(withId(R.id.input_account_name))
            .perform(typeText("Trading account"))
        closeSoftKeyboard()
        onView(withId(R.id.input_parent_account))
            .check(matches(isDisplayed()))
        onView(withId(R.id.checkbox_parent_account))
            .check(matches(isDisplayed()))

        onView(withId(R.id.input_account_type_spinner))
            .perform(click())

        onData(
            allOf(
                `is`(instanceOf<Any>(AccountTypesAdapter.Label::class.java)),
                `is`(labelTrading)
            )
        ).perform(click())

        onView(withId(R.id.input_parent_account))
            .check(matches(not(isDisplayed())))
        onView(withId(R.id.checkbox_parent_account))
            .check(matches(not(isDisplayed())))

        onView(withId(R.id.menu_save)).perform(click())
        sleep(1000)
        //no sub-accounts
        assertThat(accountsDbAdapter.getSubAccountCount(SIMPLE_ACCOUNT_UID)).isZero()
        assertThat(
            accountsDbAdapter.getSubAccountCount(accountsDbAdapter.rootAccountUID)
        ).isEqualTo(2)
        assertThat(accountsDbAdapter.simpleAccounts).extracting(
            "accountType",
            AccountType::class.java
        ).contains(AccountType.TRADING)
    }

    @Test
    fun testEditAccount() {
        refreshAccountsList()

        onView(
            allOf(
                withParent(hasDescendant(withText(SIMPLE_ACCOUNT_NAME))),
                withId(R.id.options_menu),
                isDisplayed()
            )
        ).perform(click())
        onView(withText(R.string.title_edit_account))
            .check(matches(isDisplayed())).perform(click())
        onView(withId(R.id.fragment_account_form))
            .check(matches(isDisplayed()))

        val editedAccountName = "An Edited Account"
        onView(withId(R.id.input_account_name))
            .perform(clearText())
            .perform(typeText(editedAccountName))

        onView(withId(R.id.menu_save)).perform(click())

        val accounts = accountsDbAdapter.allRecords
        val latest = accounts[0] //will be the first due to alphabetical sorting

        assertThat(latest.name).isEqualTo(editedAccountName)
        assertThat(latest.commodity.currencyCode).isEqualTo(ACCOUNTS_CURRENCY_CODE)
    }

    @Test
    fun editingAccountShouldNotDeleteTransactions() {
        onView(
            allOf(
                withParent(hasDescendant(withText(SIMPLE_ACCOUNT_NAME))),
                withId(R.id.options_menu),
                isDisplayed()
            )
        ).perform(click())

        val accountsCurrency = Commodity.getInstance(ACCOUNTS_CURRENCY_CODE)
        val account = Account("Transfer Account", accountsCurrency)
        val transaction = Transaction("Simple transaction")
        transaction.commodity = accountsCurrency
        val split = Split(Money(BigDecimal.TEN, accountsCurrency), account.uid)
        transaction.addSplit(split)
        transaction.addSplit(split.createPair(SIMPLE_ACCOUNT_UID))
        account.addTransaction(transaction)
        accountsDbAdapter.insert(account)

        assertThat(accountsDbAdapter.getTransactionCount(account.uid)).isOne()
        assertThat(accountsDbAdapter.getTransactionCount(SIMPLE_ACCOUNT_UID)).isOne()
        assertThat(splitsDbAdapter.getSplitsForTransaction(transaction.uid)).hasSize(2)

        onView(withText(R.string.title_edit_account))
            .perform(click())

        onView(withId(R.id.menu_save)).perform(click())
        assertThat(accountsDbAdapter.getTransactionCount(SIMPLE_ACCOUNT_UID)).isOne()
        assertThat(splitsDbAdapter.fetchSplitsForAccount(SIMPLE_ACCOUNT_UID)?.count).isOne()
        assertThat(splitsDbAdapter.getSplitsForTransaction(transaction.uid)).hasSize(2)
    }

    fun testDeleteSimpleAccount() {
        refreshAccountsList()
        assertThat(accountsDbAdapter.recordsCount).isEqualTo(2)
        onView(
            allOf(
                withParent(hasDescendant(withText(SIMPLE_ACCOUNT_NAME))),
                withId(R.id.options_menu)
            )
        ).perform(click())

        onView(withText(R.string.title_delete_account))
            .perform(click())

        assertThat(accountsDbAdapter.recordsCount).isOne()

        val accounts = accountsDbAdapter.allRecords
        assertThat(accounts).hasSize(0) //root account is never returned
    }

    @Test
    fun testDeleteAccountWithSubaccounts() {
        refreshAccountsList()
        val account = Account("Sub-account")
        account.parentUID = SIMPLE_ACCOUNT_UID
        account.setUID(CHILD_ACCOUNT_UID)
        accountsDbAdapter.addRecord(account)

        refreshAccountsList()

        onView(
            allOf(
                withParent(hasDescendant(withText(SIMPLE_ACCOUNT_NAME))),
                withId(R.id.options_menu)
            )
        ).perform(click())
        onView(withText(R.string.title_delete_account))
            .perform(click())

        onView(
            allOf(
                withParent(withId(R.id.accounts_options)),
                withId(R.id.radio_delete)
            )
        ).perform(click())
        onView(withText(R.string.alert_dialog_ok_delete))
            .perform(click())

        assertThat(accountExists(SIMPLE_ACCOUNT_UID)).isFalse()
        assertThat(accountExists(CHILD_ACCOUNT_UID)).isFalse()
    }

    @Test
    fun testDeleteAccountMovingSubaccounts() {
        val accountCount = accountsDbAdapter.recordsCount
        val subAccount = Account("Child account")
        subAccount.parentUID = SIMPLE_ACCOUNT_UID

        val tranferAcct = Account("Other account")
        accountsDbAdapter.insert(subAccount)
        accountsDbAdapter.insert(tranferAcct)

        assertThat(accountsDbAdapter.recordsCount).isEqualTo(accountCount + 2)

        refreshAccountsList()

        onView(
            allOf(
                withParent(hasDescendant(withText(SIMPLE_ACCOUNT_NAME))),
                withId(R.id.options_menu)
            )
        ).perform(click())
        onView(withText(R.string.title_delete_account))
            .perform(click())

        /* FIXME: 17.08.2016 This enabled check fails during some test runs-not reliable, investigate why */
        onView(allOf(withParent(withId(R.id.accounts_options)), withId(R.id.radio_move)))
            .check(matches(isEnabled())).perform(click())

        onView(withText(R.string.alert_dialog_ok_delete))
            .perform(click())

        assertThat(accountExists(SIMPLE_ACCOUNT_UID)).isFalse()
        assertThat(accountExists(subAccount.uid)).isTrue()

        val newParentUID = accountsDbAdapter.getParentAccountUID(subAccount.uid)
        assertThat(newParentUID).isEqualTo(tranferAcct.uid)
    }

    /**
     * Checks if an account exists in the database
     *
     * @param accountUID GUID of the account
     * @return `true` if the account exists, `false` otherwise
     */
    private fun accountExists(accountUID: String): Boolean {
        try {
            accountsDbAdapter.getID(accountUID)
            return true
        } catch (e: IllegalArgumentException) {
            return false
        }
    }

    //TODO: Test import of account file
    //TODO: test settings activity
    @Test
    fun testIntentAccountCreation() {
        val intent = Intent(Intent.ACTION_INSERT)
            .putExtra(Intent.EXTRA_TITLE, "Intent Account")
            .putExtra(Intent.EXTRA_UID, "intent-account")
            .putExtra(Account.EXTRA_CURRENCY_CODE, "EUR")
            .setType(Account.MIME_TYPE)

        AccountCreator().onReceive(accountsActivity, intent)

        val account = accountsDbAdapter.getRecord("intent-account")
        assertThat(account).isNotNull()
        assertThat(account.name).isEqualTo("Intent Account")
        assertThat(account.uid).isEqualTo("intent-account")
        assertThat(account.commodity.currencyCode).isEqualTo("EUR")
    }

    /**
     * Tests that the setup wizard is displayed on first run
     */
    @Test
    fun shouldShowWizardOnFirstRun() {
        //commit for immediate effect
        val editor = PreferenceManager.getDefaultSharedPreferences(context).edit()
        editor.remove(accountsActivity.getString(R.string.key_first_run)).commit()

        activityRule.runOnUiThread { accountsActivity.recreate() }

        //check that wizard is shown
        onView(withText(accountsActivity.getString(R.string.title_setup_gnucash)))
            .check(matches(isDisplayed()))

        editor.putBoolean(accountsActivity.getString(R.string.key_first_run), false).apply()
    }

    /**
     * Refresh the account list fragment
     */
    private fun refreshAccountsList() {
        try {
            activityRule.runOnUiThread { accountsActivity.refresh() }
            sleep(1000)
        } catch (throwable: Throwable) {
            System.err.println("Failed to refresh accounts")
        }
    }

    @Test
    fun showHiddenAccounts() {
        // Root + SIMPLE_ACCOUNT_NAME
        assertThat(accountsDbAdapter.recordsCount).isEqualTo(2)

        val hiddenAccount = Account(PARENT_ACCOUNT_NAME)
        hiddenAccount.setUID(PARENT_ACCOUNT_UID)
        hiddenAccount.isHidden = true
        accountsDbAdapter.insert(hiddenAccount)
        assertThat(accountsDbAdapter.recordsCount).isEqualTo(3)

        refreshAccountsList()
        onView(allOf(withText(PARENT_ACCOUNT_NAME)))
            .check(doesNotExist())

        // Show hidden accounts.
        onView(withId(R.id.menu_hidden)).perform(click())
        sleep(500) // wait for animations to finish
        onView(allOf(withText(PARENT_ACCOUNT_NAME)))
            .check(matches(isDisplayed()))

        // Hide hidden accounts.
        onView(withId(R.id.menu_hidden)).perform(click())
        onView(allOf(withText(PARENT_ACCOUNT_NAME)))
            .check(doesNotExist())
    }

    @Test
    fun showHiddenAccountsAfterChildren() {
        // Root + SIMPLE_ACCOUNT_NAME
        assertThat(accountsDbAdapter.recordsCount).isEqualTo(2)

        val hiddenAccount = Account(PARENT_ACCOUNT_NAME)
        hiddenAccount.setUID(PARENT_ACCOUNT_UID)
        hiddenAccount.isHidden = true
        accountsDbAdapter.insert(hiddenAccount)
        assertThat(accountsDbAdapter.recordsCount).isEqualTo(3)

        val hiddenAccountChild = Account("Child of Hidden")
        hiddenAccountChild.setUID(CHILD_ACCOUNT_UID)
        hiddenAccountChild.parentUID = PARENT_ACCOUNT_UID
        accountsDbAdapter.insert(hiddenAccountChild)
        assertThat(accountsDbAdapter.recordsCount).isEqualTo(4)

        refreshAccountsList()
        onView(allOf(withText(PARENT_ACCOUNT_NAME)))
            .check(doesNotExist())

        // Show hidden accounts.
        onView(withId(R.id.menu_hidden))
            .perform(click())
        sleep(500) // wait for animations to finish
        onView(allOf(withText(PARENT_ACCOUNT_NAME)))
            .check(matches(isDisplayed()))

        // Show children accounts.
        onView(allOf(withText(PARENT_ACCOUNT_NAME)))
            .perform(click())
        onView(allOf(withText("Child of Hidden")))
            .check(matches(isDisplayed()))

        pressBack()

        // Hidden accounts still visible
        onView(allOf(withText(PARENT_ACCOUNT_NAME)))
            .check(matches(isDisplayed()))

        // Hide the accounts
        onView(withId(R.id.menu_hidden))
            .perform(click())
        sleep(500) // wait for animations to finish
        onView(allOf(withText(PARENT_ACCOUNT_NAME)))
            .check(doesNotExist())
    }

    companion object {
        private const val ACCOUNTS_CURRENCY_CODE = "USD"
        private const val SIMPLE_ACCOUNT_NAME = "Simple account"
        private const val SIMPLE_ACCOUNT_UID = "simple-account"
        private const val CHILD_ACCOUNT_UID = "child-account"
        private const val PARENT_ACCOUNT_NAME = "Parent account"
        private const val PARENT_ACCOUNT_UID = "parent-account"

        private lateinit var accountsDbAdapter: AccountsDbAdapter
        private lateinit var transactionsDbAdapter: TransactionsDbAdapter
        private lateinit var splitsDbAdapter: SplitsDbAdapter

        @ClassRule
        @JvmField
        val disableAnimationsRule = DisableAnimationsRule()

        @BeforeClass
        @JvmStatic
        fun prepTest() {
            preventFirstRunDialogs()

            accountsDbAdapter = AccountsDbAdapter.instance
            transactionsDbAdapter = accountsDbAdapter.transactionsDbAdapter
            splitsDbAdapter = transactionsDbAdapter.splitsDbAdapter
            assertThat(accountsDbAdapter.isOpen).isTrue()
        }

        /**
         * Matcher to select the first of multiple views which are matched in the UI
         *
         * @param expected Matcher which fits multiple views
         * @return Single match
         */
        fun first(expected: Matcher<View>): Matcher<View> {
            return object : TypeSafeMatcher<View>() {
                private var first = false

                override fun matchesSafely(item: View): Boolean {
                    if (expected.matches(item) && !first) {
                        return true.also { first = it }
                    }
                    return false
                }

                override fun describeTo(description: Description) {
                    description.appendText("Matcher.first( $expected )")
                }
            }
        }
    }
}
