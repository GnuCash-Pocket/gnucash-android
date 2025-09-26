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
package org.gnucash.android.test.ui

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import android.net.ConnectivityManager
import androidx.core.content.edit
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.clearText
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.action.ViewActions.typeText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.contrib.DrawerActions.open
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.rule.ActivityTestRule
import androidx.test.rule.GrantPermissionRule
import org.assertj.core.api.Assertions.assertThat
import org.gnucash.android.R
import org.gnucash.android.app.GnuCashApplication
import org.gnucash.android.db.adapter.AccountsDbAdapter
import org.gnucash.android.db.adapter.CommoditiesDbAdapter
import org.gnucash.android.export.ExportFormat
import org.gnucash.android.model.Account
import org.gnucash.android.model.Commodity
import org.gnucash.android.model.Money
import org.gnucash.android.model.Split
import org.gnucash.android.model.Transaction
import org.gnucash.android.test.ui.util.DisableAnimationsRule
import org.gnucash.android.ui.account.AccountsActivity
import org.gnucash.android.ui.settings.OwnCloudPreferences
import org.junit.Assume
import org.junit.Before
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test

class OwnCloudExportTest : GnuAndroidTest() {
    private lateinit var prefs: SharedPreferences

    /**
     * A JUnit [@Rule][Rule] to launch your activity under test. This is a replacement
     * for `ActivityInstrumentationTestCase2`.
     *
     *
     * Rules are interceptors which are executed for each test method and will run before
     * any of your setup code in the [@Before][Before] method.
     *
     *
     * [ActivityTestRule] will create and launch of the activity for you and also expose
     * the activity under test. To get a reference to the activity you can use
     * the [ActivityTestRule.getActivity] method.
     */
    @Rule
    @JvmField
    val activityRule = ActivityTestRule(AccountsActivity::class.java)

    @Rule
    @JvmField
    val animationPermissionsRule =
        GrantPermissionRule.grant(Manifest.permission.SET_ANIMATION_SCALE)

    @Before
    fun setUp() {
        prefs = context.getSharedPreferences(
            context.getString(R.string.owncloud_pref),
            Context.MODE_PRIVATE
        )

        GnuCashApplication.initializeDatabaseAdapters(context)

        val accountsDbAdapter = AccountsDbAdapter.instance
        accountsDbAdapter.deleteAllRecords()

        val currencyCode = GnuCashApplication.defaultCurrencyCode
        Commodity.DEFAULT_COMMODITY =
            CommoditiesDbAdapter.instance!!.getCurrency(currencyCode)!!

        val account = Account("ownCloud")
        val transaction = Transaction("birds")
        transaction.time = System.currentTimeMillis()
        val split = Split(Money("11.11", currencyCode), account.uid)
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

        accountsDbAdapter.insert(account)

        prefs.edit {
            putBoolean(context.getString(R.string.key_owncloud_sync), false)
            putInt(context.getString(R.string.key_last_export_destination), 0)
        }
    }

    /**
     * It might fail if it takes too long to connect to the server or if there is no network
     */
    @Test
    fun ownCloudCredentials() {
        Assume.assumeTrue(hasActiveInternetConnection(context))
        onView(withId(R.id.drawer_layout)).perform(open())
        onView(withText(R.string.title_settings))
            .perform(scrollTo(), click())
        onView(withText(R.string.header_backup_and_export_settings))
            .perform(click())
        onView(withText(R.string.title_owncloud_sync_preference))
            .perform(click())
        onView(withId(R.id.owncloud_hostname))
            .check(matches(isDisplayed()))

        onView(withId(R.id.owncloud_hostname))
            .perform(
                clearText(),
                typeText(OC_SERVER),
                closeSoftKeyboard()
            )
        onView(withId(R.id.owncloud_username))
            .perform(
                clearText(),
                replaceText(OC_USERNAME),
                closeSoftKeyboard()
            )
        onView(withId(R.id.owncloud_password))
            .perform(
                clearText(),
                replaceText(OC_PASSWORD),
                closeSoftKeyboard()
            )
        onView(withId(R.id.owncloud_dir))
            .perform(
                clearText(),
                typeText(OC_DIR),
                closeSoftKeyboard()
            )
        // owncloud demo server is offline, so fake check data succeeded.
        if (OC_DEMO_DISABLED) return
        onView(withId(BUTTON_POSITIVE)).perform(click())
        sleep(5000)
        onView(withId(BUTTON_POSITIVE)).perform(click())

        assertThat(prefs.getString(context.getString(R.string.key_owncloud_server), null))
            .isEqualTo(OC_SERVER)
        assertThat(prefs.getString(context.getString(R.string.key_owncloud_username), null))
            .isEqualTo(OC_USERNAME)
        assertThat(prefs.getString(context.getString(R.string.key_owncloud_password), null))
            .isEqualTo(OC_PASSWORD)
        assertThat(prefs.getString(context.getString(R.string.key_owncloud_dir), null))
            .isEqualTo(OC_DIR)
        assertThat(prefs.getBoolean(context.getString(R.string.key_owncloud_sync), false)).isTrue()
    }

    @Test
    fun ownCloudExport() {
        Assume.assumeTrue(hasActiveInternetConnection(context))
        prefs.edit { putBoolean(context.getString(R.string.key_owncloud_sync), true) }
        val preferences = OwnCloudPreferences(context)
        preferences.server = OC_SERVER
        preferences.username = OC_USERNAME
        preferences.password = OC_PASSWORD
        preferences.dir = OC_DIR

        onView(withId(R.id.drawer_layout)).perform(open())
        onView(withText(R.string.nav_menu_export))
            .perform(click())
        onView(withId(R.id.spinner_export_destination))
            .perform(click())
        val destinations = context.resources.getStringArray(R.array.export_destinations)
        onView(withText(destinations[2])).perform(click())

        // Close the dialog
        onView(withId(BUTTON_POSITIVE)).perform(click())
        // Export
        onView(withId(R.id.menu_save)).perform(click())

        if (OC_DEMO_DISABLED) {
            val toast = context.getString(R.string.toast_export_error, ExportFormat.XML.name)
            assertToastDisplayed(activityRule.activity, toast)
        } else {
            sleep(2000)
            val targetLocation = "ownCloud -> $OC_DIR"
            assertToastDisplayed(
                activityRule.activity,
                String.format(context.getString(R.string.toast_exported_to), targetLocation)
            )
        }
    }

    companion object {
        private const val OC_DEMO_DISABLED = true
        private const val OC_SERVER = "https://demo.owncloud.org"
        private const val OC_USERNAME = "admin"
        private const val OC_PASSWORD = "admin"
        private const val OC_DIR = "gc_test"

        @ClassRule
        @JvmField
        val disableAnimationsRule = DisableAnimationsRule()

        @BeforeClass
        @JvmStatic
        fun prepareTestCase() {
            configureDevice()
            preventFirstRunDialogs()
        }

        /**
         * Test if there is an active internet connection on the device/emulator
         *
         * @return `true` is an internet connection is available, `false` otherwise
         */
        fun hasActiveInternetConnection(context: Context): Boolean {
            val connectivityManager =
                context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val activeNetworkInfo = connectivityManager.activeNetworkInfo
            return activeNetworkInfo != null && activeNetworkInfo.isConnected
        }
    }
}

