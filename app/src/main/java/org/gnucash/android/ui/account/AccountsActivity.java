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

package org.gnucash.android.ui.account;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.SearchManager;
import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.preference.PreferenceManager;

import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import com.kobakei.ratethisapp.RateThisApp;

import org.gnucash.android.BuildConfig;
import org.gnucash.android.R;
import org.gnucash.android.app.GnuCashApplication;
import org.gnucash.android.databinding.ActivityAccountsBinding;
import org.gnucash.android.db.DatabaseSchema;
import org.gnucash.android.db.adapter.AccountsDbAdapter;
import org.gnucash.android.importer.ImportAsyncTask;
import org.gnucash.android.service.ScheduledActionService;
import org.gnucash.android.ui.common.BaseDrawerActivity;
import org.gnucash.android.ui.common.FormActivity;
import org.gnucash.android.ui.common.Refreshable;
import org.gnucash.android.ui.common.UxArgument;
import org.gnucash.android.ui.transaction.TransactionsActivity;
import org.gnucash.android.ui.util.TaskDelegate;
import org.gnucash.android.ui.util.widget.FragmentStateAdapter;
import org.gnucash.android.ui.wizard.FirstRunWizardActivity;
import org.gnucash.android.util.BackupManager;

import timber.log.Timber;

/**
 * Manages actions related to accounts, displaying, exporting and creating new accounts
 * The various actions are implemented as Fragments which are then added to this activity
 *
 * @author Ngewi Fet <ngewif@gmail.com>
 * @author Oleksandr Tyshkovets <olexandr.tyshkovets@gmail.com>
 */
public class AccountsActivity extends BaseDrawerActivity implements
    OnAccountClickedListener,
    Refreshable,
    SearchView.OnQueryTextListener,
    SearchView.OnCloseListener {

    /**
     * Request code for GnuCash account structure file to import
     */
    public static final int REQUEST_PICK_ACCOUNTS_FILE = 0x1;

    /**
     * Index for the recent accounts tab
     */
    public static final int INDEX_RECENT_ACCOUNTS_FRAGMENT = 0;

    /**
     * Index of the top level (all) accounts tab
     */
    public static final int INDEX_TOP_LEVEL_ACCOUNTS_FRAGMENT = 1;

    /**
     * Index of the favorite accounts tab
     */
    public static final int INDEX_FAVORITE_ACCOUNTS_FRAGMENT = 2;

    /**
     * Used to save the index of the last open tab and restore the pager to that index
     */
    public static final String LAST_OPEN_TAB_INDEX = "last_open_tab";

    /**
     * Key for putting argument for tab into bundle arguments
     */
    public static final String EXTRA_TAB_INDEX = BuildConfig.APPLICATION_ID + ".extra.TAB_INDEX";

    /**
     * Configuration for rating the app
     */
    public static RateThisApp.Config rateAppConfig = new RateThisApp.Config(14, 100);
    private AccountViewPagerAdapter mPagerAdapter;

    private ActivityAccountsBinding mBinding;

    /**
     * Search view for searching accounts
     */
    private SearchView mSearchView;
    /**
     * Filter for which accounts should be displayed. Used by search interface
     */
    private String mCurrentFilter;

    @SuppressLint("NotifyDataSetChanged")
    @Override
    public void refresh() {
        final int count = mPagerAdapter.getItemCount();
        for (int i = 0; i < count; i++) {
            Fragment fragment = mPagerAdapter.getFragment(i);
            if (fragment instanceof Refreshable) {
                ((Refreshable) fragment).refresh();
            }
        }
        mPagerAdapter.notifyDataSetChanged();
    }

    @Override
    public void refresh(String uid) {
        refresh();
    }

    /**
     * Adapter for managing the sub-account and transaction fragment pages in the accounts view
     */
    private static class AccountViewPagerAdapter extends FragmentStateAdapter {
        /**
         * Number of pages to show
         */
        private static final int DEFAULT_NUM_PAGES = 3;

        public AccountViewPagerAdapter(FragmentActivity activity) {
            super(activity);
        }

        @NonNull
        public Fragment createFragment(int position) {
            switch (position) {
                case INDEX_RECENT_ACCOUNTS_FRAGMENT:
                    return AccountsListFragment.newInstance(AccountsListFragment.DisplayMode.RECENT);

                case INDEX_FAVORITE_ACCOUNTS_FRAGMENT:
                    return AccountsListFragment.newInstance(AccountsListFragment.DisplayMode.FAVORITES);

                case INDEX_TOP_LEVEL_ACCOUNTS_FRAGMENT:
                default:
                    return AccountsListFragment.newInstance(AccountsListFragment.DisplayMode.TOP_LEVEL);
            }
        }

        @Override
        public int getItemCount() {
            return DEFAULT_NUM_PAGES;
        }
    }

    @Override
    public void inflateView() {
        mBinding = ActivityAccountsBinding.inflate(getLayoutInflater());
        setContentView(mBinding.getRoot());
        mDrawerLayout = mBinding.drawerLayout;
        mNavigationView = mBinding.navView;
        mToolbar = mBinding.toolbarLayout.toolbar;
        mToolbarProgress = mBinding.toolbarLayout.toolbarProgress.progress;
    }

    @Override
    public int getTitleRes() {
        return R.string.title_accounts;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final Intent intent = getIntent();
        handleOpenFileIntent(intent);

        init();

        TabLayout tabLayout = mBinding.tabLayout;
        for (int i = 0; i < AccountViewPagerAdapter.DEFAULT_NUM_PAGES; i++) {
            tabLayout.addTab(tabLayout.newTab());
        }
        tabLayout.setTabGravity(TabLayout.GRAVITY_FILL);

        //show the simple accounts list
        mPagerAdapter = new AccountViewPagerAdapter(this);
        mBinding.pager.setAdapter(mPagerAdapter);

        TabLayoutMediator tabLayoutMediator = new TabLayoutMediator(tabLayout, mBinding.pager, new TabLayoutMediator.TabConfigurationStrategy() {

            @Override
            public void onConfigureTab(@NonNull TabLayout.Tab tab, int position) {
                switch (position) {
                    case INDEX_RECENT_ACCOUNTS_FRAGMENT:
                        tab.setText(R.string.title_recent_accounts);
                        break;
                    case INDEX_TOP_LEVEL_ACCOUNTS_FRAGMENT:
                        tab.setText(R.string.title_all_accounts);
                        break;
                    case INDEX_FAVORITE_ACCOUNTS_FRAGMENT:
                        tab.setText(R.string.title_favorite_accounts);
                        break;
                }
            }
        });
        tabLayoutMediator.attach();

        setCurrentTab();

        mBinding.fabCreateAccount.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent addAccountIntent = new Intent(AccountsActivity.this, FormActivity.class);
                addAccountIntent.setAction(Intent.ACTION_INSERT_OR_EDIT);
                addAccountIntent.putExtra(UxArgument.FORM_TYPE, FormActivity.FormType.ACCOUNT.name());
                startActivity(addAccountIntent);
            }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();

        if (BuildConfig.CAN_REQUEST_RATING) {
            RateThisApp.init(rateAppConfig);
            RateThisApp.onStart(this);
            RateThisApp.showRateDialogIfNeeded(this);
        }
    }

    /**
     * Handles the case where another application has selected to open a (.gnucash or .gnca) file with this app
     *
     * @param intent Intent containing the data to be imported
     */
    private void handleOpenFileIntent(Intent intent) {
        //when someone launches the app to view a (.gnucash or .gnca) file
        final Uri data = intent.getData();
        if (data != null) {
            Activity activity = this;
            BackupManager.backupActiveBookAsync(activity, result -> {
                intent.setData(null);
                new ImportAsyncTask(activity).execute(data);
                removeFirstRunFlag(activity);
                return null;
            });
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        setCurrentTab();
        handleOpenFileIntent(intent);
    }

    /**
     * Sets the current tab in the ViewPager
     */
    public void setCurrentTab() {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        int lastTabIndex = preferences.getInt(LAST_OPEN_TAB_INDEX, INDEX_TOP_LEVEL_ACCOUNTS_FRAGMENT);
        int index = getIntent().getIntExtra(EXTRA_TAB_INDEX, lastTabIndex);
        mBinding.pager.setCurrentItem(index);
    }

    /**
     * Loads default setting for currency and performs app first-run initialization.
     * <p>Also handles displaying the What's New dialog</p>
     */
    private void init() {
        final Context context = this;
        PreferenceManager.setDefaultValues(context, GnuCashApplication.getActiveBookUID(),
            Context.MODE_PRIVATE, R.xml.fragment_transaction_preferences, true);

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        boolean firstRun = prefs.getBoolean(getString(R.string.key_first_run), true);

        if (firstRun) {
            //default to using double entry and save the preference explicitly
            prefs.edit().putBoolean(getString(R.string.key_use_double_entry), true).apply();

            startActivity(new Intent(context, FirstRunWizardActivity.class));
            finish();
            return;
        }

        ScheduledActionService.schedulePeriodic(context);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        preferences.edit().putInt(LAST_OPEN_TAB_INDEX, mBinding.pager.getCurrentItem()).apply();
    }

    /**
     * Displays the dialog for exporting transactions
     */
    public static void openExportFragment(AppCompatActivity activity) {
        Intent intent = new Intent(activity, FormActivity.class);
        intent.putExtra(UxArgument.FORM_TYPE, FormActivity.FormType.EXPORT.name());
        activity.startActivity(intent);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        menu.clear();
        inflater.inflate(R.menu.account_actions, menu);
        // Associate searchable configuration with the SearchView
        SearchView searchView = mSearchView = (SearchView) menu.findItem(R.id.menu_search).getActionView();
        if (searchView != null) {
            Activity activity = this;
            SearchManager searchManager = (SearchManager) activity.getSystemService(Context.SEARCH_SERVICE);
            searchView.setSearchableInfo(searchManager.getSearchableInfo(activity.getComponentName()));
            searchView.setOnQueryTextListener(this);
            searchView.setOnCloseListener(this);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                return super.onOptionsItemSelected(item);

            default:
                return false;
        }
    }

    /**
     * Creates default accounts with the specified currency code.
     * If the currency parameter is null, then locale currency will be used if available
     *
     * @param currencyCode Currency code to assign to the imported accounts
     * @param activity     Activity for providing context and displaying dialogs
     */
    public static void createDefaultAccounts(@NonNull final String currencyCode, @NonNull final Activity activity) {
        createDefaultAccounts(currencyCode, activity, null);
    }

    /**
     * Creates default accounts with the specified currency code.
     * If the currency parameter is null, then locale currency will be used if available
     *
     * @param currencyCode Currency code to assign to the imported accounts
     * @param activity     Activity for providing context and displaying dialogs
     * @param callback     The callback to call when the book has been imported.
     */
    public static void createDefaultAccounts(@NonNull final String currencyCode, @NonNull final Activity activity, @Nullable final TaskDelegate callback) {
        TaskDelegate delegate = callback;
        if (!TextUtils.isEmpty(currencyCode)) {
            delegate = new TaskDelegate() {
                @Override
                public void onTaskComplete() {
                    AccountsDbAdapter.getInstance().updateAllAccounts(DatabaseSchema.AccountEntry.COLUMN_CURRENCY, currencyCode);
                    GnuCashApplication.setDefaultCurrencyCode(activity, currencyCode);
                    if (callback != null) {
                        callback.onTaskComplete();
                    }
                }
            };
        }

        Uri uri = new Uri.Builder()
            .scheme(ContentResolver.SCHEME_ANDROID_RESOURCE)
            .authority(BuildConfig.APPLICATION_ID)
            .path(String.valueOf(R.raw.default_accounts))
            .build();
        new ImportAsyncTask(activity, delegate).execute(uri);
    }

    /**
     * Starts Intent chooser for selecting a GnuCash accounts file to import.
     * <p>The {@code activity} is responsible for the actual import of the file and can do so by calling {@link #importXmlFileFromIntent(Activity, Intent, TaskDelegate)}<br>
     * The calling class should respond to the request code {@link AccountsActivity#REQUEST_PICK_ACCOUNTS_FILE} in its {@link #onActivityResult(int, int, Intent)} method</p>
     *
     * @param activity Activity starting the request and will also handle the response
     * @see #importXmlFileFromIntent(Activity, Intent, TaskDelegate)
     */
    public static void startXmlFileChooser(Activity activity) {
        Intent pickIntent = new Intent(Intent.ACTION_GET_CONTENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType("*/*");
        Intent chooser = Intent.createChooser(pickIntent, "Select GnuCash account file"); //todo internationalize string

        try {
            activity.startActivityForResult(chooser, REQUEST_PICK_ACCOUNTS_FILE);
        } catch (ActivityNotFoundException ex) {
            Timber.e(ex, "No file manager for selecting files available");
            Toast.makeText(activity, R.string.toast_install_file_manager, Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Overloaded method.
     * Starts chooser for selecting a GnuCash account file to import
     *
     * @param fragment Fragment creating the chooser and which will also handle the result
     * @see #startXmlFileChooser(Activity)
     */
    public static void startXmlFileChooser(Fragment fragment) {
        Intent pickIntent = new Intent(Intent.ACTION_GET_CONTENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType("*/*");
        Intent chooser = Intent.createChooser(pickIntent, "Select GnuCash account file"); //todo internationalize string

        try {
            fragment.startActivityForResult(chooser, REQUEST_PICK_ACCOUNTS_FILE);
        } catch (ActivityNotFoundException ex) {
            Timber.e(ex, "No file manager for selecting files available");
            Toast.makeText(fragment.getActivity(), R.string.toast_install_file_manager, Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Reads and XML file from an intent and imports it into the database
     * <p>This method is usually called in response to {@link AccountsActivity#startXmlFileChooser(Activity)}</p>
     *
     * @param context      Activity context
     * @param data         Intent data containing the XML uri
     * @param onFinishTask Task to be executed when import is complete
     */
    public static void importXmlFileFromIntent(Activity context, Intent data, TaskDelegate onFinishTask) {
        boolean backup = GnuCashApplication.shouldBackupForImport(context);
        new ImportAsyncTask(context, onFinishTask, backup).execute(data.getData());
    }

    /**
     * Starts the AccountsActivity and clears the activity stack
     *
     * @param context Application context
     */
    public static void start(Context context) {
        start(context, INDEX_TOP_LEVEL_ACCOUNTS_FRAGMENT);
    }

    /**
     * Starts the AccountsActivity and clears the activity stack
     *
     * @param context  Application context
     * @param tabIndex the initial tab index to select.
     */
    public static void start(Context context, int tabIndex) {
        Intent intent = new Intent(context, AccountsActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra(AccountsActivity.EXTRA_TAB_INDEX, tabIndex);
        context.startActivity(intent);
    }

    @Override
    public void accountSelected(String accountUID) {
        Intent intent = new Intent(this, TransactionsActivity.class);
        intent.setAction(Intent.ACTION_VIEW);
        intent.putExtra(UxArgument.SELECTED_ACCOUNT_UID, accountUID);

        startActivity(intent);
    }

    /**
     * Removes the flag indicating that the app is being run for the first time.
     * This is called every time the app is started because the next time won't be the first time
     *
     * @param context the context.
     */
    public static void removeFirstRunFlag(Context context) {
        PreferenceManager.getDefaultSharedPreferences(context)
            .edit()
            .putBoolean(context.getString(R.string.key_first_run), false)
            .apply();
    }

    @Override
    public boolean onClose() {
        if (!TextUtils.isEmpty(mSearchView.getQuery())) {
            mSearchView.setQuery(null, true);
        }
        return true;
    }

    @Override
    public boolean onQueryTextChange(String newText) {
        String newFilter = !TextUtils.isEmpty(newText) ? newText : null;
        String oldFilter = mCurrentFilter;
        if (oldFilter == null && newFilter == null) {
            return true;
        }
        if (oldFilter != null && oldFilter.equals(newFilter)) {
            return true;
        }
        setSearchFilter(newFilter);
        return true;
    }

    @Override
    public boolean onQueryTextSubmit(String query) {
        //nothing to see here, move along
        return true;
    }

    private void setSearchFilter(String filter) {
        mCurrentFilter = filter;
        // apply to each page
        final int count = mPagerAdapter.getItemCount();
        for (int i = 0; i < count; i++) {
            AccountsListFragment fragment = (AccountsListFragment) mPagerAdapter.getFragment(i);
            if (fragment != null) {
                fragment.onQueryTextChange(filter);
            }
        }
    }

}
