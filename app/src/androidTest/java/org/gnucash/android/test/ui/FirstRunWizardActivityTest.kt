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
package org.gnucash.android.test.ui

import android.Manifest
import android.database.SQLException
import android.database.sqlite.SQLiteDatabase
import androidx.test.espresso.Espresso.onView
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
import org.gnucash.android.db.DatabaseHelper
import org.gnucash.android.db.adapter.AccountsDbAdapter
import org.gnucash.android.db.adapter.SplitsDbAdapter
import org.gnucash.android.db.adapter.TransactionsDbAdapter
import org.gnucash.android.model.BaseModel.Companion.generateUID
import org.gnucash.android.ui.wizard.FirstRunWizardActivity
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import timber.log.Timber

/**
 * Tests the first run wizard
 *
 * @author Ngewi Fet
 */
class FirstRunWizardActivityTest : GnuAndroidTest() {
    private lateinit var dbHelper: DatabaseHelper
    private lateinit var db: SQLiteDatabase
    private lateinit var accountsDbAdapter: AccountsDbAdapter
    private lateinit var transactionsDbAdapter: TransactionsDbAdapter
    private lateinit var splitsDbAdapter: SplitsDbAdapter
    private lateinit var activity: FirstRunWizardActivity

    @Rule
    @JvmField
    val rule = ActivityTestRule(FirstRunWizardActivity::class.java)

    @Rule
    @JvmField
    val animationPermissionsRule =
        GrantPermissionRule.grant(Manifest.permission.SET_ANIMATION_SCALE)

    @Before
    fun setUp() {
        activity = rule.activity
        dbHelper = DatabaseHelper(activity, generateUID())
        try {
            db = dbHelper.writableDatabase
        } catch (e: SQLException) {
            Timber.e(e, "Error getting database: " + e.message)
            db = dbHelper.readableDatabase
        }
        splitsDbAdapter = SplitsDbAdapter(db)
        transactionsDbAdapter = TransactionsDbAdapter(splitsDbAdapter)
        accountsDbAdapter = AccountsDbAdapter(transactionsDbAdapter)
        accountsDbAdapter.deleteAllRecords()
    }

    @Test
    fun shouldRunWizardToEnd() {
        assertThat(accountsDbAdapter.recordsCount).isZero()

        onView(withId(R.id.btn_save)).perform(click())

        // Select a currency.
        onView(withText("EUR (Euro)"))
            .perform(click())
        onView(withText(R.string.btn_wizard_next))
            .perform(click())
        onView(withText(R.string.wizard_title_account_setup))
            .check(matches(isDisplayed()))

        // Select accounts template.
        onView(withText(R.string.wizard_option_create_default_accounts))
            .perform(click())
        onView(withText(R.string.btn_wizard_next))
            .perform(click())
        onView(withText(R.string.wizard_option_create_default_accounts))
            .check(matches(isDisplayed()))
        onView(withText(R.string.btn_wizard_next))
            .perform(click())

        // Select feedback.
        onView(withText(R.string.wizard_option_auto_send_crash_reports))
            .perform(click())
        onView(withText(R.string.btn_wizard_next))
            .perform(click())

        // Review.
        onView(withText(R.string.review))
            .check(matches(isDisplayed()))

        onView(withId(R.id.btn_save))
            .perform(click())

        //default accounts should be created
        val actualCount = GnuCashApplication.getAccountsDbAdapter()!!.recordsCount
        assertThat(actualCount).isGreaterThan(60L)

        val enableCrashlytics = GnuCashApplication.isCrashlyticsEnabled()
        assertThat(enableCrashlytics).isTrue()

        val defaultCurrencyCode = GnuCashApplication.getDefaultCurrencyCode()
        assertThat(defaultCurrencyCode).isEqualTo("EUR")
    }

    @Test
    fun shouldDisplayFullCurrencyList() {
        assertThat(accountsDbAdapter.recordsCount).isEqualTo(0)

        onView(withId(R.id.btn_save)).perform(click())

        onView(withText(R.string.wizard_option_currency_other))
            .perform(click())
        onView(withText(R.string.btn_wizard_next))
            .perform(click())
        onView(withText(R.string.wizard_title_select_currency))
            .check(matches(isDisplayed()))

        onView(withText("AFA (Afghani)")).perform(click())
        onView(withId(R.id.btn_save)).perform(click())

        onView(withText(R.string.wizard_option_let_me_handle_it))
            .perform(click())

        onView(withText(R.string.btn_wizard_next))
            .perform(click())
        onView(withText(R.string.wizard_option_disable_crash_reports))
            .perform(click())
        onView(withText(R.string.btn_wizard_next))
            .perform(click())

        onView(withText(R.string.review))
            .check(matches(isDisplayed()))
        onView(withId(R.id.btn_save)).perform(click())

        //default accounts should not be created
        assertThat(accountsDbAdapter.recordsCount).isZero()

        val enableCrashlytics = GnuCashApplication.isCrashlyticsEnabled()
        assertThat(enableCrashlytics).isFalse()

        val defaultCurrencyCode = GnuCashApplication.getDefaultCurrencyCode()
        assertThat(defaultCurrencyCode).isEqualTo("AFA")
    }
}
