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

package org.gnucash.android.test.ui;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.assertj.core.api.Assertions.assertThat;

import android.Manifest;
import android.content.Context;
import android.view.View;

import androidx.test.espresso.ViewAction;
import androidx.test.espresso.action.CoordinatesProvider;
import androidx.test.espresso.action.GeneralClickAction;
import androidx.test.espresso.action.Press;
import androidx.test.espresso.action.Tap;
import androidx.test.rule.ActivityTestRule;
import androidx.test.rule.GrantPermissionRule;

import org.gnucash.android.R;
import org.gnucash.android.app.GnuCashApplication;
import org.gnucash.android.db.adapter.AccountsDbAdapter;
import org.gnucash.android.db.adapter.BooksDbAdapter;
import org.gnucash.android.db.adapter.DatabaseAdapter;
import org.gnucash.android.db.adapter.TransactionsDbAdapter;
import org.gnucash.android.importer.GncXmlImporter;
import org.gnucash.android.model.AccountType;
import org.gnucash.android.model.Commodity;
import org.gnucash.android.model.Money;
import org.gnucash.android.model.Split;
import org.gnucash.android.model.Transaction;
import org.gnucash.android.model.TransactionType;
import org.gnucash.android.test.ui.util.DisableAnimationsRule;
import org.gnucash.android.ui.report.BaseReportFragment;
import org.gnucash.android.ui.report.ReportsActivity;
import org.gnucash.android.ui.report.piechart.PieChartFragment;
import org.gnucash.android.ui.settings.PreferenceActivity;
import org.gnucash.android.util.BookUtils;
import org.joda.time.LocalDateTime;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

import java.math.BigDecimal;
import java.util.Locale;

public class PieChartReportTest extends GnuAndroidTest {

    private static final String TRANSACTION_NAME = "Pizza";
    private static final double TRANSACTION_AMOUNT = 9.99;

    private static final String TRANSACTION2_NAME = "1984";
    private static final double TRANSACTION2_AMOUNT = 12.49;

    private static final String TRANSACTION3_NAME = "Nice gift";
    private static final double TRANSACTION3_AMOUNT = 2000.00;

    private static final String CASH_IN_WALLET_ASSET_ACCOUNT_UID = "b687a487849470c25e0ff5aaad6a522b";

    private static final String DINING_EXPENSE_ACCOUNT_UID = "62922c5ccb31d6198259739d27d858fe";
    private static final String DINING_EXPENSE_ACCOUNT_NAME = "Dining";

    private static final String BOOKS_EXPENSE_ACCOUNT_UID = "a8b342435aceac7c3cac214f9385dd72";
    private static final String BOOKS_EXPENSE_ACCOUNT_NAME = "Books";

    private static final String GIFTS_RECEIVED_INCOME_ACCOUNT_UID = "b01950c0df0890b6543209d51c8e0b0f";
    private static final String GIFTS_RECEIVED_INCOME_ACCOUNT_NAME = "Gifts Received";

    public static Commodity commodity;

    private static AccountsDbAdapter mAccountsDbAdapter;
    private static TransactionsDbAdapter mTransactionsDbAdapter;

    private ReportsActivity mReportsActivity;

    @Rule
    public ActivityTestRule<ReportsActivity> mActivityRule = new ActivityTestRule<>(ReportsActivity.class);

    @Rule
    public GrantPermissionRule animationPermissionsRule = GrantPermissionRule.grant(Manifest.permission.SET_ANIMATION_SCALE);

    @ClassRule
    public static DisableAnimationsRule disableAnimationsRule = new DisableAnimationsRule();

    private static String testBookUID;
    private static String oldActiveBookUID;


    public PieChartReportTest() {
        //nothing to se here, move along
        commodity = new Commodity("US Dollars", "USD", 100);
    }

    @BeforeClass
    public static void prepareTestCase() throws Exception {
        Context context = GnuCashApplication.getAppContext();
        preventFirstRunDialogs(context);
        oldActiveBookUID = GnuCashApplication.getActiveBookUID();
        testBookUID = GncXmlImporter.parse(context, context.getResources().openRawResource(R.raw.default_accounts));

        BookUtils.loadBook(context, testBookUID);
        mAccountsDbAdapter = AccountsDbAdapter.getInstance();
        mTransactionsDbAdapter = mAccountsDbAdapter.transactionsDbAdapter;

        commodity = mAccountsDbAdapter.commoditiesDbAdapter.getCommodity("USD");

        PreferenceActivity.getActiveBookSharedPreferences(context).edit()
            .putString(context.getString(R.string.key_default_currency), commodity.getCurrencyCode())
            .commit();
    }


    @Before
    public void setUp() {
        mTransactionsDbAdapter.deleteAllRecords();
        mReportsActivity = mActivityRule.getActivity();
        assertThat(mAccountsDbAdapter.getRecordsCount()).isGreaterThan(20); //lots of accounts in the default
        onView(withId(R.id.btn_pie_chart)).check(matches(isDisplayed())).perform(click());
    }

    /**
     * Add a transaction for the current month in order to test the report view
     */
    private void addTransactionForCurrentMonth() {
        Transaction transaction = new Transaction(TRANSACTION_NAME);
        transaction.setTime(System.currentTimeMillis());

        Split split = new Split(new Money(BigDecimal.valueOf(TRANSACTION_AMOUNT), commodity), DINING_EXPENSE_ACCOUNT_UID);
        split.setType(TransactionType.DEBIT);

        transaction.addSplit(split);
        transaction.addSplit(split.createPair(CASH_IN_WALLET_ASSET_ACCOUNT_UID));

        mTransactionsDbAdapter.addRecord(transaction, DatabaseAdapter.UpdateMethod.insert);
    }

    /**
     * Add a transactions for the previous month for testing pie chart
     *
     * @param minusMonths Number of months prior
     */
    private void addTransactionForPreviousMonth(int minusMonths) {
        Transaction transaction = new Transaction(TRANSACTION2_NAME);
        transaction.setTime(new LocalDateTime().minusMonths(minusMonths).toDateTime().getMillis());

        Split split = new Split(new Money(BigDecimal.valueOf(TRANSACTION2_AMOUNT), commodity), BOOKS_EXPENSE_ACCOUNT_UID);
        split.setType(TransactionType.DEBIT);

        transaction.addSplit(split);
        transaction.addSplit(split.createPair(CASH_IN_WALLET_ASSET_ACCOUNT_UID));

        mTransactionsDbAdapter.addRecord(transaction, DatabaseAdapter.UpdateMethod.insert);
    }

    @Test
    public void testNoData() {
        onView(withId(R.id.pie_chart)).perform(click());
        onView(withId(R.id.selected_chart_slice)).check(matches(withText(R.string.label_select_pie_slice_to_see_details)));
    }

    @Test
    public void testSelectingValue() {
        addTransactionForCurrentMonth();
        addTransactionForPreviousMonth(1);
        assertThat(mTransactionsDbAdapter.getRecordsCount()).isGreaterThan(1);
        refreshReport();

        onView(withId(R.id.pie_chart)).perform(clickXY(Position.BEGIN, Position.MIDDLE));
        float percent = (float) ((TRANSACTION_AMOUNT * 100) / (TRANSACTION_AMOUNT + TRANSACTION2_AMOUNT));
        String selectedText = String.format(Locale.US, BaseReportFragment.SELECTED_VALUE_PATTERN, DINING_EXPENSE_ACCOUNT_NAME, TRANSACTION_AMOUNT, percent);
        onView(withId(R.id.selected_chart_slice)).check(matches(withText(selectedText)));
    }

    @Test
    public void testSpinner() throws Exception {
        Split split = new Split(new Money(BigDecimal.valueOf(TRANSACTION3_AMOUNT), commodity), GIFTS_RECEIVED_INCOME_ACCOUNT_UID);
        Transaction transaction = new Transaction(TRANSACTION3_NAME);
        transaction.addSplit(split);
        transaction.addSplit(split.createPair(CASH_IN_WALLET_ASSET_ACCOUNT_UID));

        mTransactionsDbAdapter.addRecord(transaction, DatabaseAdapter.UpdateMethod.insert);

        refreshReport();

        Thread.sleep(1000);

        onView(withId(R.id.report_account_type_spinner)).perform(click());
        onView(withText(AccountType.INCOME.name())).perform(click());
        onView(withId(R.id.pie_chart)).perform(clickXY(Position.BEGIN, Position.MIDDLE));
        String selectedText = String.format(PieChartFragment.SELECTED_VALUE_PATTERN, GIFTS_RECEIVED_INCOME_ACCOUNT_NAME, TRANSACTION3_AMOUNT, 100f);
        onView(withId(R.id.selected_chart_slice)).check(matches(withText(selectedText)));

        onView(withId(R.id.report_account_type_spinner)).perform(click());
        onView(withText(AccountType.EXPENSE.name())).perform(click());

        onView(withId(R.id.pie_chart)).perform(click());
        onView(withId(R.id.selected_chart_slice)).check(matches(withText(R.string.label_select_pie_slice_to_see_details)));
    }

    public static ViewAction clickXY(final Position horizontal, final Position vertical) {
        return new GeneralClickAction(
            Tap.SINGLE,
            new CoordinatesProvider() {
                @Override
                public float[] calculateCoordinates(View view) {
                    int[] xy = new int[2];
                    view.getLocationOnScreen(xy);

                    float x = horizontal.getPosition(xy[0], view.getWidth());
                    float y = vertical.getPosition(xy[1], view.getHeight());
                    return new float[]{x, y};
                }
            },
            Press.FINGER);
    }

    private enum Position {
        BEGIN {
            @Override
            public float getPosition(int viewPos, int viewLength) {
                return viewPos + (viewLength * 0.15f);
            }
        },
        MIDDLE {
            @Override
            public float getPosition(int viewPos, int viewLength) {
                return viewPos + (viewLength * 0.5f);
            }
        },
        END {
            @Override
            public float getPosition(int viewPos, int viewLength) {
                return viewPos + (viewLength * 0.85f);
            }
        };

        abstract float getPosition(int widgetPos, int widgetLength);
    }

    /**
     * Refresh reports
     */
    private void refreshReport() {
        try {
            mActivityRule.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mReportsActivity.refresh();
                }
            });
            sleep(1000);
        } catch (Throwable t) {
            System.err.println("Failed to refresh reports");
        }
    }

    @After
    public void tearDown() throws Exception {
        mReportsActivity.finish();
    }

    @AfterClass
    public static void cleanup() {
        BooksDbAdapter booksDbAdapter = BooksDbAdapter.getInstance();
        booksDbAdapter.setActive(oldActiveBookUID);
        booksDbAdapter.deleteRecord(testBookUID);
    }
}
