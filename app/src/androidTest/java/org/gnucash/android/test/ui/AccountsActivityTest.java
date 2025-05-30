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

import static androidx.test.espresso.Espresso.onData;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.Espresso.openActionBarOverflowOrOptionsMenu;
import static androidx.test.espresso.action.ViewActions.clearText;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.closeSoftKeyboard;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.action.ViewActions.swipeRight;
import static androidx.test.espresso.action.ViewActions.typeText;
import static androidx.test.espresso.assertion.ViewAssertions.doesNotExist;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.hasDescendant;
import static androidx.test.espresso.matcher.ViewMatchers.isChecked;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.isEnabled;
import static androidx.test.espresso.matcher.ViewMatchers.isNotChecked;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withParent;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences.Editor;
import android.preference.PreferenceManager;
import android.view.View;

import androidx.test.espresso.Espresso;
import androidx.test.rule.ActivityTestRule;
import androidx.test.rule.GrantPermissionRule;

import org.gnucash.android.R;
import org.gnucash.android.app.GnuCashApplication;
import org.gnucash.android.db.adapter.AccountsDbAdapter;
import org.gnucash.android.db.adapter.DatabaseAdapter;
import org.gnucash.android.db.adapter.SplitsDbAdapter;
import org.gnucash.android.db.adapter.TransactionsDbAdapter;
import org.gnucash.android.model.Account;
import org.gnucash.android.model.AccountType;
import org.gnucash.android.model.Commodity;
import org.gnucash.android.model.Money;
import org.gnucash.android.model.Split;
import org.gnucash.android.model.Transaction;
import org.gnucash.android.receivers.AccountCreator;
import org.gnucash.android.test.ui.util.DisableAnimationsRule;
import org.gnucash.android.ui.account.AccountsActivity;
import org.gnucash.android.ui.adapter.AccountTypesAdapter;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

import java.math.BigDecimal;
import java.util.List;

public class AccountsActivityTest extends GnuAndroidTest {
    private static final String ACCOUNTS_CURRENCY_CODE = "USD";
    // Don't add static here, otherwise it gets set to null by super.tearDown()
    private final Commodity ACCOUNTS_CURRENCY = Commodity.getInstance(ACCOUNTS_CURRENCY_CODE);
    private static final String SIMPLE_ACCOUNT_NAME = "Simple account";
    private static final String SIMPLE_ACCOUNT_UID = "simple-account";
    private static final String ROOT_ACCOUNT_UID = "root-account";
    private static final String PARENT_ACCOUNT_NAME = "Parent account";
    private static final String PARENT_ACCOUNT_UID = "parent-account";
    private static final String CHILD_ACCOUNT_UID = "child-account";
    private static final String CHILD_ACCOUNT_NAME = "Child account";
    public static final String TEST_DB_NAME = "test_gnucash_db.sqlite";

    private static AccountsDbAdapter mAccountsDbAdapter;
    private static TransactionsDbAdapter mTransactionsDbAdapter;
    private static SplitsDbAdapter mSplitsDbAdapter;
    private AccountsActivity mAccountsActivity;

    @Rule
    public GrantPermissionRule animationPermissionsRule = GrantPermissionRule.grant(Manifest.permission.SET_ANIMATION_SCALE);

    @ClassRule
    public static DisableAnimationsRule disableAnimationsRule = new DisableAnimationsRule();

    @Rule
    public ActivityTestRule<AccountsActivity> mActivityRule = new ActivityTestRule<>(AccountsActivity.class);

    @BeforeClass
    public static void prepTest() {
        Context context = GnuCashApplication.getAppContext();
        preventFirstRunDialogs(context);

        mAccountsDbAdapter = AccountsDbAdapter.getInstance();
        mTransactionsDbAdapter = mAccountsDbAdapter.transactionsDbAdapter;
        mSplitsDbAdapter = mTransactionsDbAdapter.splitsDbAdapter;
        assertThat(mAccountsDbAdapter.isOpen()).isTrue();
    }

    @Before
    public void setUp() throws Exception {
        mAccountsActivity = mActivityRule.getActivity();

        mAccountsDbAdapter.deleteAllRecords(); //clear the data

        Account simpleAccount = new Account(SIMPLE_ACCOUNT_NAME);
        simpleAccount.setUID(SIMPLE_ACCOUNT_UID);
        simpleAccount.setCommodity(Commodity.getInstance(ACCOUNTS_CURRENCY_CODE));
        mAccountsDbAdapter.addRecord(simpleAccount, DatabaseAdapter.UpdateMethod.insert);

        refreshAccountsList();
    }


    public void testDisplayAccountsList() {
        AccountsActivity.createDefaultAccounts(mAccountsActivity, "EUR");
        mAccountsActivity.recreate();

        refreshAccountsList();
        sleep(1000);
        onView(withText("Assets")).perform(scrollTo());
        onView(withText("Expenses")).perform(click());
        onView(withText("Books")).perform(scrollTo());
    }

    @Test
    public void testSearchAccounts() {
        String SEARCH_ACCOUNT_NAME = "Search Account";

        Account account = new Account(SEARCH_ACCOUNT_NAME);
        account.setParentUID(SIMPLE_ACCOUNT_UID);
        mAccountsDbAdapter.addRecord(account, DatabaseAdapter.UpdateMethod.insert);

        // before search query
        onView(withText(SIMPLE_ACCOUNT_NAME)).check(matches(isDisplayed()));
        onView(withText(SEARCH_ACCOUNT_NAME)).check(doesNotExist());

        //enter search query
        onView(withId(R.id.menu_search)).perform(click());
        onView(withId(R.id.search_src_text)).perform(typeText(SEARCH_ACCOUNT_NAME.substring(0, 2)));
        sleep(100); //give search filter time to finish
        onView(withText(SIMPLE_ACCOUNT_NAME)).check(doesNotExist());
        onView(withText(SEARCH_ACCOUNT_NAME)).check(matches(isDisplayed()));

        // same as before search query
        onView(withId(R.id.search_src_text)).perform(clearText());
        sleep(100); //give search filter time to finish
        onView(withText(SIMPLE_ACCOUNT_NAME)).check(matches(isDisplayed()));
        onView(withText(SEARCH_ACCOUNT_NAME)).check(doesNotExist());
    }

    /**
     * Tests that an account can be created successfully and that the account list is sorted alphabetically.
     */
    @Test
    public void testCreateAccount() {
        assertThat(mAccountsDbAdapter.getAllRecords()).hasSize(1);
        onView(allOf(isDisplayed(), withId(R.id.fab_create_account))).perform(click());

        String NEW_ACCOUNT_NAME = "A New Account";
        onView(withId(R.id.input_account_name)).perform(typeText(NEW_ACCOUNT_NAME), closeSoftKeyboard());
        sleep(1000);
        onView(withId(R.id.placeholder_status))
            .check(matches(isNotChecked()))
            .perform(click());

        onView(withId(R.id.menu_save)).perform(click());

        List<Account> accounts = mAccountsDbAdapter.getAllRecords();
        assertThat(accounts).isNotNull();
        assertThat(accounts).hasSize(2);
        Account newestAccount = accounts.get(0); //because of alphabetical sorting

        assertThat(newestAccount.getName()).isEqualTo(NEW_ACCOUNT_NAME);
        assertThat(newestAccount.getCommodity()).isEqualTo(Commodity.DEFAULT_COMMODITY);
        assertThat(newestAccount.isPlaceholder()).isTrue();
    }

    @Test
    public void should_IncludeFutureTransactionsInAccountBalance() {
        Transaction transaction = new Transaction("Future transaction");
        Split split1 = new Split(new Money("4.15", ACCOUNTS_CURRENCY_CODE), SIMPLE_ACCOUNT_UID);
        transaction.addSplit(split1);
        transaction.setTime(System.currentTimeMillis() + 4815162342L);
        mTransactionsDbAdapter.addRecord(transaction);

        refreshAccountsList();

        onView(first(withText(containsString("4.15")))).check(matches(isDisplayed()));
    }

    @Test
    public void testChangeParentAccount() {
        final String accountName = "Euro Account";
        Account account = new Account(accountName, Commodity.EUR);
        mAccountsDbAdapter.addRecord(account, DatabaseAdapter.UpdateMethod.insert);

        refreshAccountsList();

        onView(withText(accountName)).perform(click());
        openActionBarOverflowOrOptionsMenu(mAccountsActivity);
        onView(withText(R.string.title_edit_account)).perform(click());
        onView(withId(R.id.fragment_account_form)).check(matches(isDisplayed()));
        Espresso.closeSoftKeyboard();
        onView(withId(R.id.checkbox_parent_account)).perform(scrollTo())
            .check(matches(isNotChecked()))
            .perform(click());
        // FIXME: explicitly select the parent account

        onView(withId(R.id.input_parent_account)).check(matches(isEnabled())).perform(click());

        onView(withText(SIMPLE_ACCOUNT_NAME)).perform(click());

        onView(withId(R.id.menu_save)).perform(click());

        Account editedAccount = mAccountsDbAdapter.getRecord(account.getUID());
        String parentUID = editedAccount.getParentUID();

        assertThat(parentUID).isNotNull();
        assertThat(parentUID).isEqualTo(SIMPLE_ACCOUNT_UID);
    }

    /**
     * When creating a sub-account (starting from within another account), if we change the account
     * type to another type with no accounts of that type, then the parent account list should be hidden.
     * The account which is then created is not a sub-account, but rather a top-level account
     */
    @Test
    public void shouldHideParentAccountViewWhenNoParentsExist() {
        Context context = GnuCashApplication.getAppContext();
        String textTrading = context.getResources().getStringArray(R.array.account_type_entry_values)[AccountType.TRADING.labelIndex];
        AccountTypesAdapter.Label labelTrading = new AccountTypesAdapter.Label(AccountType.TRADING, textTrading);

        onView(allOf(withText(SIMPLE_ACCOUNT_NAME), isDisplayed())).perform(click());
        onView(withId(R.id.fragment_transaction_list)).perform(swipeRight());
        onView(withId(R.id.fab_create_transaction)).check(matches(isDisplayed())).perform(click());
        sleep(1000);
        onView(withId(R.id.checkbox_parent_account)).check(matches(allOf(isChecked())));
        onView(withId(R.id.input_account_name)).perform(typeText("Trading account"));
        Espresso.closeSoftKeyboard();
        onView(withId(R.id.input_parent_account)).check(matches(isDisplayed()));
        onView(withId(R.id.checkbox_parent_account)).check(matches(isDisplayed()));

        onView(withId(R.id.input_account_type_spinner)).perform(click());

        onData(allOf(is(instanceOf(AccountTypesAdapter.Label.class)), is(labelTrading))).perform(click());

        onView(withId(R.id.input_parent_account)).check(matches(not(isDisplayed())));
        onView(withId(R.id.checkbox_parent_account)).check(matches(not(isDisplayed())));

        onView(withId(R.id.menu_save)).perform(click());
        sleep(1000);
        //no sub-accounts
        assertThat(mAccountsDbAdapter.getSubAccountCount(SIMPLE_ACCOUNT_UID)).isEqualTo(0);
        assertThat(mAccountsDbAdapter.getSubAccountCount(mAccountsDbAdapter.getOrCreateGnuCashRootAccountUID())).isEqualTo(2);
        assertThat(mAccountsDbAdapter.getSimpleAccountList()).extracting("accountType").contains(AccountType.TRADING);
    }

    @Test
    public void testEditAccount() {
        refreshAccountsList();

        onView(allOf(withParent(hasDescendant(withText(SIMPLE_ACCOUNT_NAME))),
            withId(R.id.options_menu),
            isDisplayed())).perform(click());
        onView(withText(R.string.title_edit_account)).check(matches(isDisplayed())).perform(click());
        onView(withId(R.id.fragment_account_form)).check(matches(isDisplayed()));

        String editedAccountName = "An Edited Account";
        onView(withId(R.id.input_account_name)).perform(clearText()).perform(typeText(editedAccountName));

        onView(withId(R.id.menu_save)).perform(click());

        List<Account> accounts = mAccountsDbAdapter.getAllRecords();
        Account latest = accounts.get(0);  //will be the first due to alphabetical sorting

        assertThat(latest.getName()).isEqualTo(editedAccountName);
        assertThat(latest.getCommodity().getCurrencyCode()).isEqualTo(ACCOUNTS_CURRENCY_CODE);
    }

    @Test
    public void editingAccountShouldNotDeleteTransactions() {
        onView(allOf(withParent(hasDescendant(withText(SIMPLE_ACCOUNT_NAME))),
            withId(R.id.options_menu),
            isDisplayed())).perform(click());

        Account account = new Account("Transfer Account");
        account.setCommodity(ACCOUNTS_CURRENCY);
        Transaction transaction = new Transaction("Simple transaction");
        transaction.setCommodity(ACCOUNTS_CURRENCY);
        Split split = new Split(new Money(BigDecimal.TEN, ACCOUNTS_CURRENCY), account.getUID());
        transaction.addSplit(split);
        transaction.addSplit(split.createPair(SIMPLE_ACCOUNT_UID));
        account.addTransaction(transaction);
        mAccountsDbAdapter.addRecord(account, DatabaseAdapter.UpdateMethod.insert);

        assertThat(mAccountsDbAdapter.getTransactionCount(account.getUID())).isEqualTo(1);
        assertThat(mAccountsDbAdapter.getTransactionCount(SIMPLE_ACCOUNT_UID)).isEqualTo(1);
        assertThat(mSplitsDbAdapter.getSplitsForTransaction(transaction.getUID())).hasSize(2);

        onView(withText(R.string.title_edit_account)).perform(click());

        onView(withId(R.id.menu_save)).perform(click());
        assertThat(mAccountsDbAdapter.getTransactionCount(SIMPLE_ACCOUNT_UID)).isEqualTo(1);
        assertThat(mSplitsDbAdapter.fetchSplitsForAccount(SIMPLE_ACCOUNT_UID).getCount()).isEqualTo(1);
        assertThat(mSplitsDbAdapter.getSplitsForTransaction(transaction.getUID())).hasSize(2);

    }

    public void testDeleteSimpleAccount() {
        refreshAccountsList();
        assertThat(mAccountsDbAdapter.getRecordsCount()).isEqualTo(2);
        onView(allOf(withParent(hasDescendant(withText(SIMPLE_ACCOUNT_NAME))),
            withId(R.id.options_menu))).perform(click());

        onView(withText(R.string.title_delete_account)).perform(click());

        assertThat(mAccountsDbAdapter.getRecordsCount()).isEqualTo(1);

        List<Account> accounts = mAccountsDbAdapter.getAllRecords();
        assertThat(accounts).hasSize(0); //root account is never returned
    }

    @Test
    public void testDeleteAccountWithSubaccounts() {
        refreshAccountsList();
        Account account = new Account("Sub-account");
        account.setParentUID(SIMPLE_ACCOUNT_UID);
        account.setUID(CHILD_ACCOUNT_UID);
        mAccountsDbAdapter.addRecord(account);

        refreshAccountsList();

        onView(allOf(withParent(hasDescendant(withText(SIMPLE_ACCOUNT_NAME))),
            withId(R.id.options_menu))).perform(click());
        onView(withText(R.string.title_delete_account)).perform(click());

        onView(allOf(withParent(withId(R.id.accounts_options)),
            withId(R.id.radio_delete))).perform(click());
        onView(withText(R.string.alert_dialog_ok_delete)).perform(click());

        assertThat(accountExists(SIMPLE_ACCOUNT_UID)).isFalse();
        assertThat(accountExists(CHILD_ACCOUNT_UID)).isFalse();
    }

    @Test
    public void testDeleteAccountMovingSubaccounts() {
        long accountCount = mAccountsDbAdapter.getRecordsCount();
        Account subAccount = new Account("Child account");
        subAccount.setParentUID(SIMPLE_ACCOUNT_UID);

        Account tranferAcct = new Account("Other account");
        mAccountsDbAdapter.addRecord(subAccount, DatabaseAdapter.UpdateMethod.insert);
        mAccountsDbAdapter.addRecord(tranferAcct, DatabaseAdapter.UpdateMethod.insert);

        assertThat(mAccountsDbAdapter.getRecordsCount()).isEqualTo(accountCount + 2);

        refreshAccountsList();

        onView(allOf(withParent(hasDescendant(withText(SIMPLE_ACCOUNT_NAME))),
            withId(R.id.options_menu))).perform(click());
        onView(withText(R.string.title_delete_account)).perform(click());

        //// FIXME: 17.08.2016 This enabled check fails during some test runs - not reliable, investigate why
        onView(allOf(withParent(withId(R.id.accounts_options)),
            withId(R.id.radio_move))).check(matches(isEnabled())).perform(click());

        onView(withText(R.string.alert_dialog_ok_delete)).perform(click());

        assertThat(accountExists(SIMPLE_ACCOUNT_UID)).isFalse();
        assertThat(accountExists(subAccount.getUID())).isTrue();

        String newParentUID = mAccountsDbAdapter.getParentAccountUID(subAccount.getUID());
        assertThat(newParentUID).isEqualTo(tranferAcct.getUID());
    }

    /**
     * Checks if an account exists in the database
     *
     * @param accountUID GUID of the account
     * @return {@code true} if the account exists, {@code false} otherwise
     */
    private boolean accountExists(String accountUID) {
        try {
            mAccountsDbAdapter.getID(accountUID);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    //TODO: Test import of account file
    //TODO: test settings activity
    @Test
    public void testIntentAccountCreation() {
        Intent intent = new Intent(Intent.ACTION_INSERT)
            .putExtra(Intent.EXTRA_TITLE, "Intent Account")
            .putExtra(Intent.EXTRA_UID, "intent-account")
            .putExtra(Account.EXTRA_CURRENCY_CODE, "EUR")
            .setType(Account.MIME_TYPE);

        new AccountCreator().onReceive(mAccountsActivity, intent);

        Account account = mAccountsDbAdapter.getRecord("intent-account");
        assertThat(account).isNotNull();
        assertThat(account.getName()).isEqualTo("Intent Account");
        assertThat(account.getUID()).isEqualTo("intent-account");
        assertThat(account.getCommodity().getCurrencyCode()).isEqualTo("EUR");
    }

    /**
     * Tests that the setup wizard is displayed on first run
     */
    @Test
    public void shouldShowWizardOnFirstRun() throws Throwable {
        Editor editor = PreferenceManager.getDefaultSharedPreferences(mAccountsActivity)
            .edit();
        //commit for immediate effect
        editor.remove(mAccountsActivity.getString(R.string.key_first_run)).commit();


        mActivityRule.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mAccountsActivity.recreate();
            }
        });

        //check that wizard is shown
        onView(withText(mAccountsActivity.getString(R.string.title_setup_gnucash)))
            .check(matches(isDisplayed()));

        editor.putBoolean(mAccountsActivity.getString(R.string.key_first_run), false).apply();
    }

    @After
    public void tearDown() throws Exception {
        if (mAccountsActivity != null) {
            mAccountsActivity.finish();
        }
    }

    /**
     * Refresh the account list fragment
     */
    private void refreshAccountsList() {
        try {
            mActivityRule.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mAccountsActivity.refresh();
                }
            });
            sleep(1000);
        } catch (Throwable throwable) {
            System.err.println("Failed to refresh accounts");
        }
    }

    /**
     * Matcher to select the first of multiple views which are matched in the UI
     *
     * @param expected Matcher which fits multiple views
     * @return Single match
     */
    public static Matcher<View> first(final Matcher<View> expected) {

        return new TypeSafeMatcher<View>() {
            private boolean first = false;

            @Override
            protected boolean matchesSafely(View item) {

                if (expected.matches(item) && !first) {
                    return first = true;
                }

                return false;
            }

            @Override
            public void describeTo(Description description) {
                description.appendText("Matcher.first( " + expected.toString() + " )");
            }
        };
    }
}
