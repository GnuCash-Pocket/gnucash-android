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

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.contrib.DrawerActions.open
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.rule.ActivityTestRule
import org.gnucash.android.R
import org.gnucash.android.app.GnuCashApplication
import org.gnucash.android.model.Account
import org.gnucash.android.model.Money
import org.gnucash.android.model.Split
import org.gnucash.android.model.Transaction
import org.gnucash.android.ui.account.AccountsActivity
import org.junit.After
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test

class ExportTransactionsTest : DatabaseTest() {
    @Rule
    @JvmField
    val activityRule = ActivityTestRule(AccountsActivity::class.java, false, false)

    @Before
    fun setUp() {
        accountsDbAdapter.deleteAllRecords()

        //this call initializes the static variables like DEFAULT_COMMODITY which are used implicitly by accounts/transactions
        val currencyCode = GnuCashApplication.defaultCurrencyCode
        val currency = commoditiesDbAdapter.getCurrency(currencyCode)!!

        val account = Account("Exportable")
        accountsDbAdapter.insert(account)

        val transaction = Transaction("Pizza")
        transaction.notes = "What up?"
        transaction.datePosted = System.currentTimeMillis()
        val split = Split(Money("8.99", currency), account)
        split.memo = "Hawaii is the best!"
        transaction.addSplit(split)
        transaction.addSplit(
            split.createPair(
                accountsDbAdapter.getOrCreateImbalanceAccountUID(context, currency)
            )
        )

        transactionsDbAdapter.insert(transaction)

        activityRule.launchActivity(null)
    }

    @After
    fun tearDown() {
        activityRule.finishActivity()
    }

    @Test
    fun testCreateBackup() {
        val activity = activityRule.activity

        onView(withId(R.id.drawer_layout)).perform(open())
        onView(withText(R.string.title_settings))
            .perform(scrollTo())
        clickViewText(R.string.title_settings)
        clickViewText(R.string.header_backup_and_export_settings)

        clickViewText(R.string.title_create_backup_pref)
        assertToastDisplayed(activity, R.string.toast_backup_successful)
    }

    companion object {
        @BeforeClass
        @JvmStatic
        fun prepTest() {
            configureDevice()
            preventFirstRunDialogs()
        }
    }
}
