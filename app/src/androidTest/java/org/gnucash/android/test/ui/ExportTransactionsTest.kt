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
import androidx.annotation.StringRes
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.contrib.DrawerActions
import androidx.test.espresso.matcher.RootMatchers.withDecorView
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.rule.ActivityTestRule
import androidx.test.rule.GrantPermissionRule
import org.gnucash.android.R
import org.gnucash.android.app.GnuCashApplication
import org.gnucash.android.db.adapter.AccountsDbAdapter
import org.gnucash.android.db.adapter.CommoditiesDbAdapter
import org.gnucash.android.db.adapter.DatabaseAdapter
import org.gnucash.android.model.Account
import org.gnucash.android.model.Commodity
import org.gnucash.android.model.Money
import org.gnucash.android.model.Split
import org.gnucash.android.model.Transaction
import org.gnucash.android.ui.account.AccountsActivity
import org.hamcrest.Matchers.`is`
import org.hamcrest.Matchers.not
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test

class ExportTransactionsTest : GnuAndroidTest() {
    private lateinit var accountsDbAdapter: AccountsDbAdapter

    @Rule
    @JvmField
    val animationPermissionsRule =
        GrantPermissionRule.grant(Manifest.permission.SET_ANIMATION_SCALE)

    @Rule
    @JvmField
    val rule: ActivityTestRule<AccountsActivity> =
        ActivityTestRule(AccountsActivity::class.java)

    @Before
    fun setUp() {
        accountsDbAdapter = AccountsDbAdapter.getInstance()
        accountsDbAdapter.deleteAllRecords()

        //this call initializes the static variables like DEFAULT_COMMODITY which are used implicitly by accounts/transactions
        @Suppress("unused") val currencyCode = GnuCashApplication.getDefaultCurrencyCode()
        Commodity.DEFAULT_COMMODITY =
            CommoditiesDbAdapter.getInstance()!!.getCurrency(currencyCode)!!

        val account = Account("Exportable")
        val transaction = Transaction("Pizza")
        transaction.note = "What up?"
        transaction.setTime(System.currentTimeMillis())
        val split = Split(Money("8.99", currencyCode), account.uid)
        split.memo = "Hawaii is the best!"
        transaction.addSplit(split)
        transaction.addSplit(
            split.createPair(
                accountsDbAdapter.getOrCreateImbalanceAccountUID(
                    context,
                    Commodity.DEFAULT_COMMODITY
                )
            )
        )
        account.addTransaction(transaction)

        accountsDbAdapter.addRecord(account, DatabaseAdapter.UpdateMethod.insert)
    }

    @Test
    fun testCreateBackup() {
        rule.activity
        onView(withId(R.id.drawer_layout)).perform(DrawerActions.open())
        onView(withText(R.string.title_settings))
            .perform(scrollTo())
        onView(withText(R.string.title_settings)).perform(click())
        onView(withText(R.string.header_backup_and_export_settings))
            .perform(click())

        onView(withText(R.string.title_create_backup_pref))
            .perform(click())
        assertToastDisplayed(R.string.toast_backup_successful)
    }

    /**
     * Checks that a specific toast message is displayed
     *
     * @param toastString String that should be displayed
     */
    private fun assertToastDisplayed(@StringRes toastString: Int) {
        onView(withText(toastString))
            .inRoot(withDecorView(not(`is`(rule.activity.window.decorView))))
            .check(matches(isDisplayed()))
    }

    companion object {
        @BeforeClass
        @JvmStatic
        fun prepTest() {
            preventFirstRunDialogs()
        }
    }
}
