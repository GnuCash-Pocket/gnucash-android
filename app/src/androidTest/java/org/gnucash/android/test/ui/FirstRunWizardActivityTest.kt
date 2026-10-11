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

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.rule.ActivityTestRule
import org.assertj.core.api.Assertions.assertThat
import org.gnucash.android.R
import org.gnucash.android.app.GnuCashApplication
import org.gnucash.android.app.GnuCashApplication.Companion.activeBookUID
import org.gnucash.android.ui.wizard.FirstRunWizardActivity
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Tests the first run wizard
 *
 * @author Ngewi Fet
 */
class FirstRunWizardActivityTest : DatabaseTest() {
    @Rule
    @JvmField
    val activityRule = ActivityTestRule(FirstRunWizardActivity::class.java, false, false)

    @Before
    fun setUp() {
        accountsDbAdapter.deleteAllRecords()
        activityRule.launchActivity(null)
    }

    @Test
    fun shouldRunWizardToEnd() {
        assertThat(accountsDbAdapter.recordsCount).isZero()

        clickViewId(R.id.btn_save)

        // Select a currency.
        clickViewText("EUR (Euro)")
        clickViewText(R.string.btn_wizard_next)
        onView(withText(R.string.wizard_title_account_setup))
            .check(matches(isDisplayed()))

        // Select accounts template.
        clickViewText(R.string.wizard_option_create_default_accounts)
        clickViewText(R.string.btn_wizard_next)
        onView(withText(R.string.wizard_option_create_default_accounts))
            .check(matches(isDisplayed()))
        clickViewText(R.string.btn_wizard_next)

        // Select feedback.
        clickViewText(R.string.wizard_option_auto_send_crash_reports)
        clickViewText(R.string.btn_wizard_next)

        // Review.
        onView(withText(com.tech.freak.wizardpager.R.string.review))
            .check(matches(isDisplayed()))

        clickViewId(R.id.btn_save)
        sleep(5000) //give import time to finish
        initAdapters(activeBookUID)

        //default accounts should be created
        val actualCount = accountsDbAdapter.recordsCount
        assertThat(actualCount).isGreaterThan(60L)

        val enableCrashlytics = GnuCashApplication.isCrashlyticsEnabled
        assertThat(enableCrashlytics).isTrue()

        val defaultCurrencyCode = GnuCashApplication.defaultCurrencyCode
        assertThat(defaultCurrencyCode).isEqualTo("EUR")
    }

    @Test
    fun shouldDisplayFullCurrencyList() {
        assertThat(accountsDbAdapter.recordsCount).isZero()

        clickViewId(R.id.btn_save)

        clickViewText(R.string.wizard_option_currency_other)
        clickViewText(R.string.btn_wizard_next)
        onView(withText(R.string.wizard_title_select_currency))
            .check(matches(isDisplayed()))

        clickViewText("AFA (Afghani)")
        clickViewId(R.id.btn_save)

        clickViewText(R.string.wizard_option_let_me_handle_it)

        clickViewText(R.string.btn_wizard_next)
        clickViewText(R.string.wizard_option_disable_crash_reports)
        clickViewText(R.string.btn_wizard_next)

        onView(withText(com.tech.freak.wizardpager.R.string.review))
            .check(matches(isDisplayed()))

        clickViewId(R.id.btn_save)
        sleep(5000) //give import time to finish
        initAdapters(null)

        //default accounts should not be created
        //ROOT is only account.
        assertThat(accountsDbAdapter.recordsCount).isOne()

        val enableCrashlytics = GnuCashApplication.isCrashlyticsEnabled
        assertThat(enableCrashlytics).isFalse()

        val defaultCurrencyCode = GnuCashApplication.defaultCurrencyCode
        assertThat(defaultCurrencyCode).isEqualTo("AFA")
    }
}
