/*
 * Copyright (c) 2012 - 2014 Ngewi Fet <ngewif@gmail.com>
 * Copyright (c) 2014 Yongxin Wang <fefe.wyx@gmail.com>
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
package org.gnucash.android.ui.account

import android.annotation.SuppressLint
import android.app.Activity
import android.app.SearchManager
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.annotation.DrawableRes
import androidx.appcompat.widget.SearchView
import androidx.core.content.edit
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.preference.PreferenceManager
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.kobakei.ratethisapp.RateThisApp
import org.gnucash.android.BuildConfig
import org.gnucash.android.R
import org.gnucash.android.app.GnuCashApplication.Companion.activeBookUID
import org.gnucash.android.app.GnuCashApplication.Companion.shouldBackupForImport
import org.gnucash.android.databinding.ActivityAccountsBinding
import org.gnucash.android.db.DatabaseSchema
import org.gnucash.android.db.adapter.AccountsDbAdapter
import org.gnucash.android.importer.ImportAsyncTask
import org.gnucash.android.importer.ImportBookCallback
import org.gnucash.android.service.ScheduledActionService.Companion.schedulePeriodic
import org.gnucash.android.ui.common.BaseDrawerActivity
import org.gnucash.android.ui.common.FormActivity
import org.gnucash.android.ui.common.Refreshable
import org.gnucash.android.ui.common.UxArgument
import org.gnucash.android.ui.transaction.TransactionsActivity
import org.gnucash.android.ui.util.widget.FragmentStateAdapter
import org.gnucash.android.ui.wizard.FirstRunWizardActivity
import org.gnucash.android.util.BackupManager.backupActiveBookAsync
import org.gnucash.android.util.chooseContent

/**
 * Manages actions related to accounts, displaying, exporting and creating new accounts
 * The various actions are implemented as Fragments which are then added to this activity
 *
 * @author Ngewi Fet <ngewif@gmail.com>
 * @author Oleksandr Tyshkovets <olexandr.tyshkovets@gmail.com>
 */
class AccountsActivity : BaseDrawerActivity(),
    OnAccountClickedListener,
    Refreshable,
    SearchView.OnQueryTextListener,
    SearchView.OnCloseListener {
    private lateinit var pagerAdapter: AccountViewPagerAdapter
    private lateinit var binding: ActivityAccountsBinding

    /**
     * Search view for searching accounts
     */
    private var searchView: SearchView? = null

    /**
     * Filter for which accounts should be displayed. Used by search interface
     */
    private var currentFilter: String? = null
    private var isShowHiddenAccounts = false

    @SuppressLint("NotifyDataSetChanged")
    override fun refresh() {
        val count = pagerAdapter.itemCount
        for (i in 0 until count) {
            val fragment = pagerAdapter.getFragment(i)
            if (fragment is Refreshable) {
                fragment.refresh()
            }
        }
        pagerAdapter.notifyDataSetChanged()
    }

    override fun refresh(uid: String?) {
        refresh()
    }

    /**
     * Adapter for managing the sub-account and transaction fragment pages in the accounts view
     */
    private class AccountViewPagerAdapter(activity: FragmentActivity) :
        FragmentStateAdapter(activity) {
        override fun createFragment(position: Int): Fragment {
            return when (position) {
                INDEX_RECENT_ACCOUNTS_FRAGMENT ->
                    AccountsListFragment.newInstance(AccountsListFragment.DisplayMode.RECENT)

                INDEX_FAVORITE_ACCOUNTS_FRAGMENT ->
                    AccountsListFragment.newInstance(AccountsListFragment.DisplayMode.FAVORITES)

                INDEX_TOP_LEVEL_ACCOUNTS_FRAGMENT ->
                    AccountsListFragment.newInstance(AccountsListFragment.DisplayMode.TOP_LEVEL)

                else -> AccountsListFragment.newInstance(AccountsListFragment.DisplayMode.TOP_LEVEL)
            }
        }

        override fun getItemCount(): Int {
            return NUM_PAGES
        }
    }

    override fun inflateView() {
        val binding = ActivityAccountsBinding.inflate(layoutInflater)
        this.binding = binding
        setContentView(binding.root)
        drawerLayout = binding.drawerLayout
        navigationView = binding.navView
        toolbar = binding.toolbarLayout.toolbar
        toolbarProgress = binding.toolbarLayout.toolbarProgress.progress
    }

    override val titleRes: Int = R.string.title_accounts

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        handleOpenFileIntent(intent)
        init()

        val tabLayout = binding.tabLayout
        (0 until NUM_PAGES).forEach { _ ->
            tabLayout.addTab(tabLayout.newTab())
        }
        tabLayout.setTabGravity(TabLayout.GRAVITY_FILL)

        //show the simple accounts list
        pagerAdapter = AccountViewPagerAdapter(this)
        binding.pager.adapter = pagerAdapter

        TabLayoutMediator(tabLayout, binding.pager) { tab, position ->
            when (position) {
                INDEX_RECENT_ACCOUNTS_FRAGMENT -> tab.setText(R.string.title_recent_accounts)
                INDEX_TOP_LEVEL_ACCOUNTS_FRAGMENT -> tab.setText(R.string.title_all_accounts)
                INDEX_FAVORITE_ACCOUNTS_FRAGMENT -> tab.setText(R.string.title_favorite_accounts)
            }
        }.attach()

        setCurrentTab()
    }

    override fun onStart() {
        super.onStart()

        if (BuildConfig.CAN_REQUEST_RATING) {
            RateThisApp.init(rateAppConfig)
            RateThisApp.onStart(this)
            RateThisApp.showRateDialogIfNeeded(this)
        }
    }

    /**
     * Handles the case where another application has selected to open a (.gnucash or .gnca) file with this app
     *
     * @param intent Intent containing the data to be imported
     */
    private fun handleOpenFileIntent(intent: Intent) {
        //when someone launches the app to view a (.gnucash or .gnca) file
        val data = intent.data ?: return
        val activity: Activity = this
        backupActiveBookAsync(activity) {
            intent.setData(null)
            ImportAsyncTask(activity).execute(data)
            removeFirstRunFlag(activity)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        setCurrentTab()
        handleOpenFileIntent(intent)
    }

    /**
     * Sets the current tab in the ViewPager
     */
    fun setCurrentTab() {
        val preferences = PreferenceManager.getDefaultSharedPreferences(this)
        val lastTabIndex =
            preferences.getInt(LAST_OPEN_TAB_INDEX, INDEX_TOP_LEVEL_ACCOUNTS_FRAGMENT)
        val index = intent.getIntExtra(EXTRA_TAB_INDEX, lastTabIndex)
        binding.pager.currentItem = index
    }

    /**
     * Loads default setting for currency and performs app first-run initialization.
     *
     * Also handles displaying the What's New dialog
     */
    private fun init() {
        val context: Context = this
        PreferenceManager.setDefaultValues(
            context,
            activeBookUID,
            MODE_PRIVATE,
            R.xml.fragment_transaction_preferences,
            true
        )

        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val firstRun = prefs.getBoolean(getString(R.string.key_first_run), true)
        if (firstRun) {
            startActivity(Intent(context, FirstRunWizardActivity::class.java))
            finish()
            return
        }

        schedulePeriodic(context)
    }

    override fun onDestroy() {
        super.onDestroy()
        PreferenceManager.getDefaultSharedPreferences(this).edit {
            putInt(LAST_OPEN_TAB_INDEX, binding.pager.currentItem)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        super.onCreateOptionsMenu(menu)
        menuInflater.inflate(R.menu.account_actions, menu)
        // Associate searchable configuration with the SearchView
        val searchView = menu.findItem(R.id.menu_search).actionView as? SearchView
        this.searchView = searchView
        if (searchView != null) {
            val activity: Activity = this
            val searchManager = activity.getSystemService(SEARCH_SERVICE) as SearchManager
            searchView.setSearchableInfo(searchManager.getSearchableInfo(activity.componentName))
            searchView.setOnQueryTextListener(this)
            searchView.setOnCloseListener(this)
        }
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        val itemHidden = menu.findItem(R.id.menu_hidden)
        showHiddenAccounts(itemHidden, isShowHiddenAccounts)
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_hidden -> {
                toggleHidden(item)
                return true
            }

            else -> return super.onOptionsItemSelected(item)
        }
    }

    override fun accountSelected(accountUID: String) {
        val intent = Intent(this, TransactionsActivity::class.java)
            .setAction(Intent.ACTION_VIEW)
            .putExtra(UxArgument.SELECTED_ACCOUNT_UID, accountUID)
            .putExtra(UxArgument.SHOW_HIDDEN, isShowHiddenAccounts)

        startActivity(intent)
    }

    override fun onClose(): Boolean {
        val searchView = this.searchView
        if (searchView != null) {
            if (!searchView.query.isNullOrEmpty()) {
                searchView.setQuery(null, true)
            }
        }
        return true
    }

    override fun onQueryTextChange(newText: String?): Boolean {
        val newFilter = newText
        val oldFilter = currentFilter
        if (oldFilter == null && newFilter == null) {
            return true
        }
        if (oldFilter != null && oldFilter == newFilter) {
            return true
        }
        setSearchFilter(newFilter)
        return true
    }

    override fun onQueryTextSubmit(query: String?): Boolean {
        //nothing to see here, move along
        return true
    }

    private fun setSearchFilter(filter: String?) {
        currentFilter = filter
        // apply to each page
        val count = pagerAdapter.itemCount
        for (i in 0 until count) {
            val fragment = pagerAdapter.getFragment(i) as AccountsListFragment?
            fragment?.onQueryTextChange(filter)
        }
    }

    private fun toggleHidden(item: MenuItem) {
        showHiddenAccounts(item, !item.isChecked)
    }

    private fun showHiddenAccounts(item: MenuItem, isVisible: Boolean) {
        item.isChecked = isVisible
        @DrawableRes val visibilityIcon =
            if (isVisible) R.drawable.ic_visibility else R.drawable.ic_visibility_off
        item.setIcon(visibilityIcon)
        isShowHiddenAccounts = isVisible
        // apply to each page
        val count = pagerAdapter.itemCount
        for (i in 0 until count) {
            val fragment = pagerAdapter.getFragment(i) as AccountsListFragment?
            fragment?.setShowHiddenAccounts(isShowHiddenAccounts)
        }
    }

    companion object {
        /**
         * Request code for GnuCash account structure file to import
         */
        const val REQUEST_PICK_ACCOUNTS_FILE = 0x1

        /**
         * Index for the recent accounts tab
         */
        const val INDEX_RECENT_ACCOUNTS_FRAGMENT = 0

        /**
         * Index of the top level (all) accounts tab
         */
        const val INDEX_TOP_LEVEL_ACCOUNTS_FRAGMENT = 1

        /**
         * Index of the favorite accounts tab
         */
        const val INDEX_FAVORITE_ACCOUNTS_FRAGMENT = 2

        /**
         * Number of pages to show
         */
        const val NUM_PAGES = 3

        /**
         * Used to save the index of the last open tab and restore the pager to that index
         */
        const val LAST_OPEN_TAB_INDEX: String = "last_open_tab"

        /**
         * Key for putting argument for tab into bundle arguments
         */
        const val EXTRA_TAB_INDEX: String = BuildConfig.APPLICATION_ID + ".extra.TAB_INDEX"

        /**
         * Configuration for rating the app
         */
        var rateAppConfig: RateThisApp.Config = RateThisApp.Config(14, 100)

        /**
         * Displays the dialog for exporting transactions
         */
        fun openExportFragment(context: Context) {
            val intent = Intent(context, FormActivity::class.java)
                .putExtra(UxArgument.FORM_TYPE, FormActivity.FormType.EXPORT.name)
            context.startActivity(intent)
        }

        /**
         * Creates default accounts with the specified currency code.
         * If the currency parameter is null, then locale currency will be used if available
         *
         * @param activity     Activity for providing context and displaying dialogs
         * @param currencyCode Currency code to assign to the imported accounts
         */
        fun createDefaultAccounts(activity: Activity, currencyCode: String) {
            val uri = Uri.Builder()
                .scheme(ContentResolver.SCHEME_ANDROID_RESOURCE)
                .authority(BuildConfig.APPLICATION_ID)
                .path(R.raw.default_accounts.toString())
                .build()
            createDefaultAccounts(activity, currencyCode, uri, null)
        }

        /**
         * Creates default accounts with the specified currency code.
         * If the currency parameter is null, then locale currency will be used if available
         *
         * @param activity     Activity for providing context and displaying dialogs
         * @param currencyCode Currency code to assign to the imported accounts
         */
        fun createDefaultAccounts(
            activity: Activity,
            currencyCode: String,
            assetId: String,
            callback: ImportBookCallback?
        ) {
            val uri = Uri.Builder()
                .scheme(ContentResolver.SCHEME_FILE)
                .encodedAuthority("/android_asset")
                .path(assetId)
                .build()
            createDefaultAccounts(activity, currencyCode, uri, callback)
        }

        /**
         * Creates default accounts with the specified currency code.
         * If the currency parameter is null, then locale currency will be used if available
         *
         * @param activity     Activity for providing context and displaying dialogs
         * @param currencyCode Currency code to assign to the imported accounts
         * @param callback     The callback to call when the book has been imported.
         */
        fun createDefaultAccounts(
            activity: Activity,
            currencyCode: String?,
            uri: Uri,
            callback: ImportBookCallback?
        ) {
            ImportAsyncTask(activity) { bookUID ->
                if (!currencyCode.isNullOrEmpty()) {
                    val accountsDbAdapter = AccountsDbAdapter.instance
                    val commoditiesDbAdapter = accountsDbAdapter.commoditiesDbAdapter
                    val currencyUID = commoditiesDbAdapter.getCommodityUID(currencyCode)
                    accountsDbAdapter.updateAllAccounts(
                        DatabaseSchema.AccountEntry.COLUMN_COMMODITY_UID,
                        currencyUID
                    )
                    commoditiesDbAdapter.setDefaultCurrencyCode(currencyCode)
                }
                callback?.invoke(bookUID)
            }.execute(uri)
        }

        /**
         * Starts Intent chooser for selecting a GnuCash accounts file to import.
         *
         * The `activity` is responsible for the actual import of the file and can do so by calling [.importXmlFileFromIntent]<br></br>
         * The calling class should respond to the request code [AccountsActivity.REQUEST_PICK_ACCOUNTS_FILE] in its [.onActivityResult] method
         *
         * @param activity Activity starting the request and will also handle the response
         * @see .importXmlFileFromIntent
         */
        fun startXmlFileChooser(activity: Activity) {
            activity.chooseContent(REQUEST_PICK_ACCOUNTS_FILE)
        }

        /**
         * Overloaded method.
         * Starts chooser for selecting a GnuCash account file to import
         *
         * @param fragment Fragment creating the chooser and which will also handle the result
         * @see .startXmlFileChooser
         */
        fun startXmlFileChooser(fragment: Fragment) {
            fragment.chooseContent(REQUEST_PICK_ACCOUNTS_FILE)
        }

        /**
         * Reads and XML file from an intent and imports it into the database
         *
         * This method is usually called in response to [AccountsActivity.startXmlFileChooser]
         *
         * @param context      Activity context
         * @param data         Intent data containing the XML uri
         * @param onFinishTask Task to be executed when import is complete
         */
        fun importXmlFileFromIntent(
            context: Activity,
            data: Intent,
            onFinishTask: ImportBookCallback?
        ) {
            val backup = shouldBackupForImport(context)
            ImportAsyncTask(context, backup, onFinishTask).execute(data.data)
        }

        /**
         * Starts the AccountsActivity and clears the activity stack
         *
         * @param context  Application context
         * @param tabIndex the initial tab index to select.
         */
        /**
         * Starts the AccountsActivity and clears the activity stack
         *
         * @param context Application context
         */
        fun start(context: Context, tabIndex: Int = INDEX_TOP_LEVEL_ACCOUNTS_FRAGMENT) {
            val intent = Intent(context, AccountsActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(EXTRA_TAB_INDEX, tabIndex)
            context.startActivity(intent)
        }

        /**
         * Removes the flag indicating that the app is being run for the first time.
         * This is called every time the app is started because the next time won't be the first time
         *
         * @param context the context.
         */
        fun removeFirstRunFlag(context: Context) {
            PreferenceManager.getDefaultSharedPreferences(context).edit {
                putBoolean(context.getString(R.string.key_first_run), false)
            }
        }
    }
}
