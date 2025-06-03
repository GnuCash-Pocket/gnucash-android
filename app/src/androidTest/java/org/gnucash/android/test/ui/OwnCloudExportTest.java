/*
 * Copyright (c) 2016 Felipe Morato <me@fmorato.com>
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
import static androidx.test.espresso.action.ViewActions.clearText;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.closeSoftKeyboard;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.action.ViewActions.typeText;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.RootMatchers.withDecorView;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertTrue;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

import androidx.test.espresso.Espresso;
import androidx.test.espresso.contrib.DrawerActions;
import androidx.test.rule.ActivityTestRule;
import androidx.test.rule.GrantPermissionRule;

import org.gnucash.android.R;
import org.gnucash.android.app.GnuCashApplication;
import org.gnucash.android.db.adapter.AccountsDbAdapter;
import org.gnucash.android.db.adapter.CommoditiesDbAdapter;
import org.gnucash.android.db.adapter.DatabaseAdapter;
import org.gnucash.android.model.Account;
import org.gnucash.android.model.Commodity;
import org.gnucash.android.model.Money;
import org.gnucash.android.model.Split;
import org.gnucash.android.model.Transaction;
import org.gnucash.android.test.ui.util.DisableAnimationsRule;
import org.gnucash.android.ui.account.AccountsActivity;
import org.junit.Assume;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public class OwnCloudExportTest extends GnuAndroidTest {

    private SharedPreferences mPrefs;

    private final String OC_SERVER = "https://demo.owncloud.org";
    private final String OC_USERNAME = "admin";
    private final String OC_PASSWORD = "admin";
    private final String OC_DIR = "gc_test";
    private static final boolean OC_DEMO_DISABLED = true;

    /**
     * A JUnit {@link Rule @Rule} to launch your activity under test. This is a replacement
     * for {@code ActivityInstrumentationTestCase2}.
     * <p>
     * Rules are interceptors which are executed for each test method and will run before
     * any of your setup code in the {@link Before @Before} method.
     * <p>
     * {@link ActivityTestRule} will create and launch of the activity for you and also expose
     * the activity under test. To get a reference to the activity you can use
     * the {@link ActivityTestRule#getActivity()} method.
     */
    @Rule
    public ActivityTestRule<AccountsActivity> mActivityRule = new ActivityTestRule<>(
        AccountsActivity.class);

    @Rule
    public GrantPermissionRule animationPermissionsRule = GrantPermissionRule.grant(Manifest.permission.SET_ANIMATION_SCALE);

    @ClassRule
    public static DisableAnimationsRule disableAnimationsRule = new DisableAnimationsRule();

    @BeforeClass
    public static void prepareTestCase() {
        Context context = GnuCashApplication.getAppContext();
        preventFirstRunDialogs(context);
    }

    @Before
    public void setUp() throws Exception {
        Context context = GnuCashApplication.getAppContext();
        mPrefs = context.getSharedPreferences(
            context.getString(R.string.owncloud_pref), Context.MODE_PRIVATE);

        GnuCashApplication.initializeDatabaseAdapters(context);

        AccountsDbAdapter accountsDbAdapter = AccountsDbAdapter.getInstance();
        accountsDbAdapter.deleteAllRecords();

        String currencyCode = GnuCashApplication.getDefaultCurrencyCode();
        Commodity.DEFAULT_COMMODITY = CommoditiesDbAdapter.getInstance().getCommodity(currencyCode);

        Account account = new Account("ownCloud");
        Transaction transaction = new Transaction("birds");
        transaction.setTime(System.currentTimeMillis());
        Split split = new Split(new Money("11.11", currencyCode), account.getUID());
        transaction.addSplit(split);
        transaction.addSplit(split.createPair(
            accountsDbAdapter.getOrCreateImbalanceAccountUID(context, Commodity.DEFAULT_COMMODITY)));
        account.addTransaction(transaction);

        accountsDbAdapter.addRecord(account, DatabaseAdapter.UpdateMethod.insert);

        mPrefs.edit()
            .putBoolean(context.getString(R.string.key_owncloud_sync), false)
            .putInt(context.getString(R.string.key_last_export_destination), 0)
            .apply();
    }

    /**
     * Test if there is an active internet connection on the device/emulator
     *
     * @return {@code true} is an internet connection is available, {@code false} otherwise
     */
    public static boolean hasActiveInternetConnection(Context context) {
        ConnectivityManager connectivityManager
            = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
        return activeNetworkInfo != null && activeNetworkInfo.isConnected();
    }

    /**
     * It might fail if it takes too long to connect to the server or if there is no network
     */
    @Test
    public void ownCloudCredentials() {
        Context context = GnuCashApplication.getAppContext();
        Assume.assumeTrue(hasActiveInternetConnection(context));
        onView(withId(R.id.drawer_layout)).perform(DrawerActions.open());
        onView(withText(R.string.title_settings)).perform(scrollTo(), click());
        onView(withText(R.string.header_backup_and_export_settings)).perform(click());
        onView(withText(R.string.title_owncloud_sync_preference)).perform(click());
        onView(withId(R.id.owncloud_hostname)).check(matches(isDisplayed()));

        onView(withId(R.id.owncloud_hostname))
            .perform(clearText(), typeText(OC_SERVER), closeSoftKeyboard());
        onView(withId(R.id.owncloud_username))
            .perform(clearText(), replaceText(OC_USERNAME), closeSoftKeyboard());
        onView(withId(R.id.owncloud_password))
            .perform(clearText(), replaceText(OC_PASSWORD), closeSoftKeyboard());
        onView(withId(R.id.owncloud_dir))
            .perform(clearText(), typeText(OC_DIR), closeSoftKeyboard());
        // owncloud demo server is offline, so fake check data succeeded.
        if (OC_DEMO_DISABLED) return;
        onView(withId(BUTTON_POSITIVE)).perform(click());
        sleep(5000);
        onView(withId(BUTTON_POSITIVE)).perform(click());

        assertEquals(mPrefs.getString(context.getString(R.string.key_owncloud_server), null), OC_SERVER);
        assertEquals(mPrefs.getString(context.getString(R.string.key_owncloud_username), null), OC_USERNAME);
        assertEquals(mPrefs.getString(context.getString(R.string.key_owncloud_password), null), OC_PASSWORD);
        assertEquals(mPrefs.getString(context.getString(R.string.key_owncloud_dir), null), OC_DIR);

        assertTrue(mPrefs.getBoolean(context.getString(R.string.key_owncloud_sync), false));
    }

    /// / FIXME: 20.04.2017 This test now fails since introduction of SAF.
    public void ownCloudExport() {
        Context context = GnuCashApplication.getAppContext();
        Assume.assumeTrue(hasActiveInternetConnection(context));
        mPrefs.edit().putBoolean(context.getString(R.string.key_owncloud_sync), true).commit();

        onView(withId(R.id.drawer_layout)).perform(DrawerActions.open());
        onView(withText(R.string.nav_menu_export)).perform(click());
        Espresso.closeSoftKeyboard();
        Espresso.pressBack(); //close the SAF file picker window
        onView(withId(R.id.spinner_export_destination)).perform(click());
        String[] destinations = context.getResources().getStringArray(R.array.export_destinations);
        onView(withText(destinations[3])).perform(click());
        onView(withId(R.id.menu_save)).perform(click());
        assertToastDisplayed(String.format(context.getString(R.string.toast_exported_to), "ownCloud -> " + OC_DIR));
    }

    /**
     * Checks that a specific toast message is displayed
     *
     * @param toastString String that should be displayed
     */
    private void assertToastDisplayed(String toastString) {
        onView(withText(toastString))
            .inRoot(withDecorView(not(is(mActivityRule.getActivity().getWindow().getDecorView()))))
            .check(matches(isDisplayed()));
    }
}

