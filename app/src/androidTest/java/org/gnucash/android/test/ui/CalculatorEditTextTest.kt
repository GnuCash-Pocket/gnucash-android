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
import android.text.InputType
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.assertThat
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withInputType
import androidx.test.rule.ActivityTestRule
import androidx.test.rule.GrantPermissionRule
import org.gnucash.android.R
import org.gnucash.android.db.adapter.AccountsDbAdapter
import org.gnucash.android.db.adapter.CommoditiesDbAdapter
import org.gnucash.android.model.Account
import org.gnucash.android.test.ui.util.DisableAnimationsRule
import org.gnucash.android.test.ui.util.SoftwareKeyboard.isKeyboardOpen
import org.gnucash.android.ui.common.UxArgument
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

// TODO: Find out how to press the keys in the KeyboardView.
class CalculatorEditTextTest : GnuAndroidTest() {
    private lateinit var accountsDbAdapter: AccountsDbAdapter
    private lateinit var transactionsActivity: TransactionsActivity

    @Rule
    @JvmField
    val animationPermissionsRule =
        GrantPermissionRule.grant(Manifest.permission.SET_ANIMATION_SCALE)

    @Rule
    @JvmField
    val activityRule = ActivityTestRule(TransactionsActivity::class.java, true, false)

    @Before
    fun setUp() {
        accountsDbAdapter = AccountsDbAdapter.getInstance()
        accountsDbAdapter.deleteAllRecords()

        val commoditiesDbAdapter = CommoditiesDbAdapter.getInstance()!!
        val commodity = commoditiesDbAdapter.getCommodity(CURRENCY_CODE)!!

        val account = Account(DUMMY_ACCOUNT_NAME, commodity)
        account.setUID(DUMMY_ACCOUNT_UID)

        val account2 = Account(TRANSFER_ACCOUNT_NAME, commodity)
        account2.setUID(TRANSFER_ACCOUNT_UID)

        accountsDbAdapter.addRecord(account)
        accountsDbAdapter.addRecord(account2)

        val intent = Intent(Intent.ACTION_VIEW)
            .putExtra(UxArgument.SELECTED_ACCOUNT_UID, DUMMY_ACCOUNT_UID)
        activityRule.launchActivity(intent)
        transactionsActivity = activityRule.activity
    }

    @After
    fun tearDown() {
        transactionsActivity.finish()
    }

    /**
     * Checks the calculator keyboard is showed/hided as expected.
     */
    @Test
    fun testShowingHidingOfCalculatorKeyboard() {
        clickOnView(R.id.fab_create_transaction)

        // Verify the input type is correct
        onView(withId(R.id.input_transaction_amount)).check(
            matches(allOf(withInputType(InputType.TYPE_CLASS_NUMBER)))
        )

        // Giving the focus to the amount field shows the keyboard
        onView(withId(R.id.input_transaction_amount))
            .perform(click())
        assertThat(isKeyboardOpen, `is`(false))
        onView(withId(R.id.calculator_keyboard))
            .check(matches(isDisplayed()))

        // Pressing back hides the keyboard (still with focus)
        pressBack()
        assertThat(isKeyboardOpen, `is`(false))
        onView(withId(R.id.calculator_keyboard))
            .check(matches(not(isDisplayed())))

        // Clicking the amount field already focused shows the keyboard again
        clickOnView(R.id.input_transaction_amount)
        assertThat(isKeyboardOpen, `is`(false))
        onView(withId(R.id.calculator_keyboard))
            .check(matches(isDisplayed()))

        // Changing the focus to another field keeps the software keyboard open
        clickOnView(R.id.input_transaction_name)
        assertThat(isKeyboardOpen, `is`(true))
        onView(withId(R.id.calculator_keyboard))
            .check(matches(not(isDisplayed())))
    }

    /**
     * Simple wrapper for clicking on views with espresso
     *
     * @param viewId View resource ID
     */
    private fun clickOnView(viewId: Int) {
        onView(withId(viewId)).perform(click())
    }

    companion object {
        private const val DUMMY_ACCOUNT_UID = "transactions-account"
        private const val DUMMY_ACCOUNT_NAME = "Transactions Account"

        private const val TRANSFER_ACCOUNT_NAME = "Transfer account"
        private const val TRANSFER_ACCOUNT_UID = "transfer_account"
        private const val CURRENCY_CODE: String = "USD"

        @ClassRule
        @JvmField
        val disableAnimationsRule = DisableAnimationsRule()

        @BeforeClass
        @JvmStatic
        fun prepTestCase() {
            preventFirstRunDialogs()
        }
    }
}
