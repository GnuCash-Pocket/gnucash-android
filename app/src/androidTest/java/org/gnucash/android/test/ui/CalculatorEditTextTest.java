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

package org.gnucash.android.test.ui;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.Espresso.pressBack;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.assertThat;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withInputType;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

import android.Manifest;
import android.app.UiAutomation;
import android.content.Intent;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.text.InputType;

import androidx.test.espresso.action.ViewActions;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;
import androidx.test.rule.GrantPermissionRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.uiautomator.UiDevice;

import org.gnucash.android.R;
import org.gnucash.android.app.GnuCashApplication;
import org.gnucash.android.db.DatabaseHelper;
import org.gnucash.android.db.adapter.AccountsDbAdapter;
import org.gnucash.android.db.adapter.BooksDbAdapter;
import org.gnucash.android.db.adapter.CommoditiesDbAdapter;
import org.gnucash.android.db.adapter.SplitsDbAdapter;
import org.gnucash.android.db.adapter.TransactionsDbAdapter;
import org.gnucash.android.model.Account;
import org.gnucash.android.model.Commodity;
import org.gnucash.android.test.ui.util.DisableAnimationsRule;
import org.gnucash.android.test.ui.util.SoftwareKeyboard;
import org.gnucash.android.ui.common.UxArgument;
import org.gnucash.android.ui.transaction.TransactionsActivity;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import timber.log.Timber;

// TODO: Find out how to press the keys in the KeyboardView.
@RunWith(AndroidJUnit4.class)
public class CalculatorEditTextTest {
    private static final String DUMMY_ACCOUNT_UID = "transactions-account";
    private static final String DUMMY_ACCOUNT_NAME = "Transactions Account";

    private static final String TRANSFER_ACCOUNT_NAME = "Transfer account";
    private static final String TRANSFER_ACCOUNT_UID = "transfer_account";
    public static final String CURRENCY_CODE = "USD";

    private static DatabaseHelper mDbHelper;
    private static AccountsDbAdapter mAccountsDbAdapter;
    private static TransactionsDbAdapter mTransactionsDbAdapter;
    private static SplitsDbAdapter mSplitsDbAdapter;
    private TransactionsActivity mTransactionsActivity;

    public CalculatorEditTextTest() {
    }


    @Rule
    public GrantPermissionRule animationPermissionsRule = GrantPermissionRule.grant(Manifest.permission.SET_ANIMATION_SCALE);

    @ClassRule
    public static DisableAnimationsRule disableAnimationsRule = new DisableAnimationsRule();

    @Rule
    public ActivityTestRule<TransactionsActivity> mActivityRule =
            new ActivityTestRule<>(TransactionsActivity.class, true, false);


    @BeforeClass
    public static void prepTestCase() {
        String activeBookUID = BooksDbAdapter.getInstance().getActiveBookUID();
        mDbHelper = new DatabaseHelper(GnuCashApplication.getAppContext(), activeBookUID);

        mSplitsDbAdapter = SplitsDbAdapter.getInstance();
        mTransactionsDbAdapter = TransactionsDbAdapter.getInstance();
        mAccountsDbAdapter = AccountsDbAdapter.getInstance();

        AccountsActivityTest.preventFirstRunDialogs(GnuCashApplication.getAppContext());
    }

    @Before
    public void setUp() throws Exception {

        mAccountsDbAdapter.deleteAllRecords();

        CommoditiesDbAdapter commoditiesDbAdapter = CommoditiesDbAdapter.getInstance();
        Commodity commodity = commoditiesDbAdapter.getCommodity(CURRENCY_CODE);

        Account account = new Account(DUMMY_ACCOUNT_NAME, commodity);
        account.setUID(DUMMY_ACCOUNT_UID);

        Account account2 = new Account(TRANSFER_ACCOUNT_NAME, commodity);
        account2.setUID(TRANSFER_ACCOUNT_UID);

        mAccountsDbAdapter.addRecord(account);
        mAccountsDbAdapter.addRecord(account2);

        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.putExtra(UxArgument.SELECTED_ACCOUNT_UID, DUMMY_ACCOUNT_UID);
        mActivityRule.launchActivity(intent);
        mTransactionsActivity = mActivityRule.getActivity();

    }

    /**
     * Checks the calculator keyboard is showed/hided as expected.
     */
    @Test
    public void testShowingHidingOfCalculatorKeyboard() {
        clickOnView(R.id.fab_create_transaction);

        // Verify the input type is correct
        onView(withId(R.id.input_transaction_amount)).check(matches(allOf(withInputType(InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_CLASS_NUMBER))));

        // Giving the focus to the amount field shows the keyboard
        onView(withId(R.id.input_transaction_amount)).perform(click());
        assertThat(SoftwareKeyboard.isKeyboardOpen(), is(true));

        // Pressing back hides the keyboard (still with focus)
        pressBack();
        assertThat(SoftwareKeyboard.isKeyboardOpen(), is(false));

        // Clicking the amount field already focused shows the keyboard again
        clickOnView(R.id.input_transaction_amount);
        assertThat(SoftwareKeyboard.isKeyboardOpen(), is(true));

        // Changing the focus to another field keeps the software keyboard open
        clickOnView(R.id.input_transaction_name);
        assertThat(SoftwareKeyboard.isKeyboardOpen(), is(true));
    }

    /**
     * Simple wrapper for clicking on views with espresso
     *
     * @param viewId View resource ID
     */
    private void clickOnView(int viewId) {
        onView(withId(viewId)).perform(click());
    }

    @After
    public void tearDown() throws Exception {
        if (mTransactionsActivity != null)
            mTransactionsActivity.finish();
    }

    @AfterClass
    public static void cleanup() {
        if (mDbHelper != null)
            mDbHelper.close();
    }
}
