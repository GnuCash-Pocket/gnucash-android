/*
 * Copyright (c) 2012 - 2015 Ngewi Fet <ngewif@gmail.com>
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

package org.gnucash.android.ui.transaction;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.util.SparseArray;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;

import androidx.annotation.ColorInt;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentResultListener;
import androidx.fragment.app.FragmentStatePagerAdapter;

import com.google.android.material.tabs.TabLayout;

import org.gnucash.android.R;
import org.gnucash.android.databinding.ActivityTransactionsBinding;
import org.gnucash.android.db.DatabaseSchema;
import org.gnucash.android.db.adapter.AccountsDbAdapter;
import org.gnucash.android.db.adapter.TransactionsDbAdapter;
import org.gnucash.android.model.Account;
import org.gnucash.android.ui.account.AccountsListFragment;
import org.gnucash.android.ui.account.DeleteAccountDialogFragment;
import org.gnucash.android.ui.account.OnAccountClickedListener;
import org.gnucash.android.ui.adapter.QualifiedAccountNameAdapter;
import org.gnucash.android.ui.common.BaseDrawerActivity;
import org.gnucash.android.ui.common.FormActivity;
import org.gnucash.android.ui.common.Refreshable;
import org.gnucash.android.ui.common.UxArgument;
import org.gnucash.android.util.BackupManager;
import org.joda.time.LocalDate;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import java.util.List;

import kotlin.Unit;
import kotlin.jvm.functions.Function0;
import timber.log.Timber;

/**
 * Activity for displaying, creating and editing transactions
 *
 * @author Ngewi Fet <ngewif@gmail.com>
 */
public class TransactionsActivity extends BaseDrawerActivity implements
    Refreshable, OnAccountClickedListener, FragmentResultListener {

    /**
     * ViewPager index for sub-accounts fragment
     */
    private static final int INDEX_SUB_ACCOUNTS_FRAGMENT = 0;

    /**
     * ViewPager index for transactions fragment
     */
    private static final int INDEX_TRANSACTIONS_FRAGMENT = 1;

    /**
     * Number of pages to show
     */
    private static final int DEFAULT_NUM_PAGES = 2;
    private static final DateTimeFormatter dayMonthFormatter = DateTimeFormat.forPattern("EEE, d MMM");

    /**
     * GUID of {@link Account} whose transactions are displayed
     */
    private Account account = null;

    /**
     * Account database adapter for manipulating the accounts list in navigation
     */
    private final AccountsDbAdapter mAccountsDbAdapter = AccountsDbAdapter.getInstance();
    private final TransactionsDbAdapter transactionsDbAdapter = TransactionsDbAdapter.getInstance();
    private QualifiedAccountNameAdapter accountNameAdapter;

    private final SparseArray<Refreshable> mFragmentPageReferenceMap = new SparseArray<>();
    private boolean isShowHiddenAccounts = false;

    private ActivityTransactionsBinding mBinding;

    private final AdapterView.OnItemSelectedListener accountSpinnerListener = new AdapterView.OnItemSelectedListener() {

        @Override
        public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
            Account account = accountNameAdapter.getAccount(position);
            if (account != null) {
                TransactionsActivity.this.account = account;
                String accountUID = account.getUID();
                getIntent().putExtra(UxArgument.SELECTED_ACCOUNT_UID, accountUID); //update the intent in case the account gets rotated
                if (account.isPlaceholder()) {
                    if (mBinding.tabLayout.getTabCount() > 1) {
                        mPagerAdapter.notifyDataSetChanged();
                        mBinding.tabLayout.removeTabAt(1);
                    }
                } else {
                    if (mBinding.tabLayout.getTabCount() < 2) {
                        mPagerAdapter.notifyDataSetChanged();
                        mBinding.tabLayout.addTab(mBinding.tabLayout.newTab().setText(R.string.section_header_transactions));
                    }
                }
                //refresh any fragments in the tab with the new account UID
                refresh(accountUID);

                //if there are no transactions, and there are sub-accounts, show the sub-accounts
                long txCount = transactionsDbAdapter.getTransactionsCount(accountUID);
                long subCount = mAccountsDbAdapter.getSubAccountCount(accountUID);
                if (txCount == 0 && subCount > 0) {
                    mBinding.pager.setCurrentItem(INDEX_SUB_ACCOUNTS_FRAGMENT);
                } else {
                    mBinding.pager.setCurrentItem(INDEX_TRANSACTIONS_FRAGMENT);
                }
            } else {
                //refresh any fragments in the tab with the new account UID
                refresh();
            }
            supportInvalidateOptionsMenu();
        }

        @Override
        public void onNothingSelected(AdapterView<?> parent) {
            //nothing to see here, move along
        }
    };

    private AccountViewPagerAdapter mPagerAdapter;

    @Override
    public void onFragmentResult(@NonNull String requestKey, @NonNull Bundle result) {
        if (DeleteAccountDialogFragment.TAG.equals(requestKey)) {
            boolean refresh = result.getBoolean(Refreshable.EXTRA_REFRESH);
            if (refresh) {
                finish();
            }
        }
    }

    /**
     * Adapter for managing the sub-account and transaction fragment pages in the accounts view
     */
    private class AccountViewPagerAdapter extends FragmentStatePagerAdapter {

        public AccountViewPagerAdapter(FragmentManager fm) {
            super(fm);
        }

        @NonNull
        @Override
        public Fragment getItem(int position) {
            if (account.isPlaceholder()) {
                Fragment transactionsListFragment = prepareSubAccountsListFragment();
                mFragmentPageReferenceMap.put(position, (Refreshable) transactionsListFragment);
                return transactionsListFragment;
            }

            Fragment currentFragment;
            switch (position) {
                case INDEX_SUB_ACCOUNTS_FRAGMENT:
                    currentFragment = prepareSubAccountsListFragment();
                    break;

                case INDEX_TRANSACTIONS_FRAGMENT:
                default:
                    currentFragment = prepareTransactionsListFragment();
                    break;
            }

            mFragmentPageReferenceMap.put(position, (Refreshable) currentFragment);
            return currentFragment;
        }

        @Override
        public void destroyItem(@NonNull ViewGroup container, int position, @NonNull Object object) {
            super.destroyItem(container, position, object);
            mFragmentPageReferenceMap.remove(position);
        }

        @Override
        public CharSequence getPageTitle(int position) {
            if (account.isPlaceholder())
                return getString(R.string.section_header_subaccounts);

            switch (position) {
                case INDEX_SUB_ACCOUNTS_FRAGMENT:
                    return getString(R.string.section_header_subaccounts);

                case INDEX_TRANSACTIONS_FRAGMENT:
                default:
                    return getString(R.string.section_header_transactions);
            }
        }

        @Override
        public int getCount() {
            if (account.isPlaceholder())
                return 1;
            else
                return DEFAULT_NUM_PAGES;
        }

        /**
         * Creates and initializes the fragment for displaying sub-account list
         *
         * @return {@link AccountsListFragment} initialized with the sub-accounts
         */
        private AccountsListFragment prepareSubAccountsListFragment() {
            String accountUID = (account != null) ? account.getUID() : null;
            Bundle args = new Bundle();
            args.putString(UxArgument.PARENT_ACCOUNT_UID, accountUID);
            args.putBoolean(UxArgument.SHOW_HIDDEN, isShowHiddenAccounts);
            AccountsListFragment fragment = new AccountsListFragment();
            fragment.setArguments(args);
            return fragment;
        }

        /**
         * Creates and initializes fragment for displaying transactions
         *
         * @return {@link TransactionsListFragment} initialized with the current account transactions
         */
        private TransactionsListFragment prepareTransactionsListFragment() {
            String accountUID = (account != null) ? account.getUID() : null;
            Bundle args = new Bundle();
            args.putString(UxArgument.SELECTED_ACCOUNT_UID, accountUID);
            Timber.i("Opening transactions for account: %s", accountUID);
            TransactionsListFragment fragment = new TransactionsListFragment();
            fragment.setArguments(args);
            return fragment;
        }
    }

    /**
     * Refreshes the fragments currently in the transactions activity
     */
    @Override
    public void refresh(String accountUID) {
        setTitleIndicatorColor();

        for (int i = 0; i < mFragmentPageReferenceMap.size(); i++) {
            mFragmentPageReferenceMap.valueAt(i).refresh(accountUID);
        }

        if (mPagerAdapter != null) {
            mPagerAdapter.notifyDataSetChanged();
        }

        mBinding.toolbarLayout.toolbarSpinner.setEnabled(false);
        accountNameAdapter.load(this, new Function0<Unit>() {
                @Override
                public Unit invoke() {
                    updateNavigationSelection();
                    setTitleIndicatorColor();
                    mBinding.toolbarLayout.toolbarSpinner.setEnabled(true);
                    return null;
                }
            }
        );
    }

    @Override
    public void refresh() {
        String accountUID = (account != null) ? account.getUID() : null;
        refresh(accountUID);
    }

    @Override
    public void inflateView() {
        mBinding = ActivityTransactionsBinding.inflate(getLayoutInflater());
        setContentView(mBinding.getRoot());
        mDrawerLayout = mBinding.drawerLayout;
        mNavigationView = mBinding.navView;
        mToolbar = mBinding.toolbarLayout.toolbar;
        mToolbarProgress = mBinding.toolbarLayout.toolbarProgress.progress;
    }

    @Override
    public int getTitleRes() {
        return R.string.title_transactions;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ActionBar actionBar = getSupportActionBar();
        assert actionBar != null;
        actionBar.setDisplayShowTitleEnabled(false);
        actionBar.setDisplayHomeAsUpEnabled(true);

        isShowHiddenAccounts = getIntent().getBooleanExtra(UxArgument.SHOW_HIDDEN, isShowHiddenAccounts);

        final Context contextWithTheme = mBinding.toolbarLayout.toolbar.getContext();
        accountNameAdapter = new QualifiedAccountNameAdapter(contextWithTheme, null, null, mAccountsDbAdapter);
        String accountUID = getIntent().getStringExtra(UxArgument.SELECTED_ACCOUNT_UID);
        if (TextUtils.isEmpty(accountUID)) {
            accountUID = mAccountsDbAdapter.getOrCreateGnuCashRootAccountUID();
        }
        account = accountNameAdapter.getAccount(accountUID);
        if (account == null) {
            Timber.e("Account not found %s", accountUID);
            finish();
            return;
        }

        mBinding.tabLayout.addTab(mBinding.tabLayout.newTab().setText(R.string.section_header_subaccounts));
        if (!account.isPlaceholder()) {
            mBinding.tabLayout.addTab(mBinding.tabLayout.newTab().setText(R.string.section_header_transactions));
        }

        setupActionBarNavigation();

        mPagerAdapter = new AccountViewPagerAdapter(getSupportFragmentManager());
        mBinding.pager.setAdapter(mPagerAdapter);
        mBinding.pager.addOnPageChangeListener(new TabLayout.TabLayoutOnPageChangeListener(mBinding.tabLayout));

        mBinding.tabLayout.setOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                mBinding.pager.setCurrentItem(tab.getPosition());
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
                //nothing to see here, move along
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
                //nothing to see here, move along
            }
        });

        mBinding.fabCreateTransaction.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switch (mBinding.pager.getCurrentItem()) {
                    case INDEX_SUB_ACCOUNTS_FRAGMENT:
                        createNewAccount(account.getUID());
                        break;

                    case INDEX_TRANSACTIONS_FRAGMENT:
                        createNewTransaction(account.getUID());
                        break;
                }
            }
        });

        List<Fragment> fragments = getSupportFragmentManager().getFragments();
        for (Fragment fragment : fragments) {
            if (fragment instanceof AccountsListFragment) {
                mFragmentPageReferenceMap.put(INDEX_SUB_ACCOUNTS_FRAGMENT, (AccountsListFragment) fragment);
            } else if (fragment instanceof TransactionsListFragment) {
                mFragmentPageReferenceMap.put(INDEX_TRANSACTIONS_FRAGMENT, (TransactionsListFragment) fragment);
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    /**
     * Sets the color for the ViewPager title indicator to match the account color
     */
    private void setTitleIndicatorColor() {
        @ColorInt int color = mAccountsDbAdapter.getActiveAccountColor(this, account.getUID());
        setTitlesColor(color);
        mBinding.tabLayout.setBackgroundColor(color);
    }

    /**
     * Set up action bar navigation list and listener callbacks
     */
    private void setupActionBarNavigation() {
        mBinding.toolbarLayout.toolbarSpinner.setAdapter(accountNameAdapter);
        mBinding.toolbarLayout.toolbarSpinner.setOnItemSelectedListener(accountSpinnerListener);
        updateNavigationSelection();
    }

    /**
     * Updates the action bar navigation list selection to that of the current account
     * whose transactions are being displayed/manipulated
     */
    private void updateNavigationSelection() {
        Account account = this.account;
        String accountUID = account.getUID();
        int position = accountNameAdapter.getPosition(accountUID);
        // In case the account was deleted.
        if (position == AdapterView.INVALID_POSITION) {
            accountUID = account.getParentUID();
            position = accountNameAdapter.getPosition(accountUID);
            account = accountNameAdapter.getAccount(position);
            this.account = account;
        }
        mBinding.toolbarLayout.toolbarSpinner.setSelection(position);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem favoriteAccountMenuItem = menu.findItem(R.id.menu_favorite);

        if (favoriteAccountMenuItem == null) //when the activity is used to edit a transaction
            return super.onPrepareOptionsMenu(menu);

        boolean isFavoriteAccount = account.isFavorite();
        @DrawableRes int favoriteIcon = isFavoriteAccount ? R.drawable.ic_favorite : R.drawable.ic_favorite_border;
        favoriteAccountMenuItem.setIcon(favoriteIcon);

        MenuItem itemHidden = menu.findItem(R.id.menu_hidden);
        if (itemHidden != null) {
            boolean isHidden = !isShowHiddenAccounts;
            itemHidden.setChecked(isHidden);
            @DrawableRes int hiddenIcon = isHidden ? R.drawable.ic_visibility_off : R.drawable.ic_visibility;
            itemHidden.setIcon(hiddenIcon);
        }

        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                return super.onOptionsItemSelected(item);

            case R.id.menu_favorite:
                toggleFavorite(account);
                return true;

            case R.id.menu_edit:
                editAccount(account.getUID());
                return true;

            case R.id.menu_delete:
                deleteAccount(account.getUID());
                return true;

            case R.id.menu_hidden:
                toggleHidden(item);
                return true;

            default:
                return false;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == RESULT_CANCELED) {
            super.onActivityResult(requestCode, resultCode, data);
            return;
        }
        refresh();
    }

    /**
     * Formats the date to show the the day of the week if the {@code dateMillis} is within 7 days
     * of today. Else it shows the actual date formatted as short string. <br>
     * It also shows "today", "yesterday" or "tomorrow" if the date is on any of those days
     *
     * @param dateMillis
     * @return
     */
    @NonNull
    public static String getPrettyDateFormat(Context context, long dateMillis) {
        LocalDate transactionTime = new LocalDate(dateMillis);
        LocalDate today = new LocalDate();
        final String prettyDateText;
        if (transactionTime.compareTo(today.minusDays(1)) >= 0 && transactionTime.compareTo(today.plusDays(1)) <= 0) {
            prettyDateText = DateUtils.getRelativeTimeSpanString(dateMillis, System.currentTimeMillis(), DateUtils.DAY_IN_MILLIS).toString();
        } else if (transactionTime.getYear() == today.getYear()) {
            prettyDateText = dayMonthFormatter.print(dateMillis);
        } else {
            prettyDateText = DateUtils.formatDateTime(context, dateMillis, DateUtils.FORMAT_ABBREV_MONTH | DateUtils.FORMAT_SHOW_YEAR);
        }

        return prettyDateText;
    }

    private void createNewAccount(String accountUID) {
        Intent intent = new Intent(this, FormActivity.class)
            .setAction(Intent.ACTION_INSERT_OR_EDIT)
            .putExtra(UxArgument.PARENT_ACCOUNT_UID, account.getUID())
            .putExtra(UxArgument.FORM_TYPE, FormActivity.FormType.ACCOUNT.name());
        startActivityForResult(intent, 0);
    }

    private void createNewTransaction(String accountUID) {
        Intent intent = new Intent(this, FormActivity.class)
            .setAction(Intent.ACTION_INSERT_OR_EDIT)
            .putExtra(UxArgument.SELECTED_ACCOUNT_UID, accountUID)
            .putExtra(UxArgument.FORM_TYPE, FormActivity.FormType.TRANSACTION.name());
        startActivityForResult(intent, 0);
    }

    @Override
    public void accountSelected(String accountUID) {
        Intent intent = new Intent(this, TransactionsActivity.class)
            .setAction(Intent.ACTION_VIEW)
            .putExtra(UxArgument.SELECTED_ACCOUNT_UID, accountUID)
            .putExtra(UxArgument.SHOW_HIDDEN, isShowHiddenAccounts);
        startActivity(intent);
    }

    private void toggleFavorite(Account account) {
        AccountsDbAdapter accountsDbAdapter = mAccountsDbAdapter;
        long accountId = account.id;
        boolean isFavorite = !account.isFavorite();
        //toggle favorite preference
        account.setFavorite(isFavorite);
        accountsDbAdapter.updateAccount(accountId, DatabaseSchema.AccountEntry.COLUMN_FAVORITE, isFavorite ? "1" : "0");
        supportInvalidateOptionsMenu();
    }

    private void editAccount(String accountUID) {
        Intent editAccountIntent = new Intent(this, FormActivity.class)
            .setAction(Intent.ACTION_INSERT_OR_EDIT)
            .putExtra(UxArgument.SELECTED_ACCOUNT_UID, accountUID)
            .putExtra(UxArgument.FORM_TYPE, FormActivity.FormType.ACCOUNT.name());
        startActivity(editAccountIntent);
    }

    /**
     * Delete the account with UID.
     * It shows the delete confirmation dialog if the account has transactions,
     * else deletes the account immediately
     *
     * @param accountUID The UID of the account
     */
    private void deleteAccount(final String accountUID) {
        if (mAccountsDbAdapter.getTransactionCount(accountUID) > 0 || mAccountsDbAdapter.getSubAccountCount(accountUID) > 0) {
            showConfirmationDialog(accountUID);
        } else {
            BackupManager.backupActiveBookAsync(this, result -> {
                // Avoid calling AccountsDbAdapter.deleteRecord(long). See #654
                if (mAccountsDbAdapter.deleteRecord(accountUID)) {
                    finish();
                }
                return null;
            });
        }
    }

    /**
     * Shows the delete confirmation dialog
     *
     * @param accountUID Unique ID of account to be deleted after confirmation
     */
    private void showConfirmationDialog(String accountUID) {
        FragmentManager fm = getSupportFragmentManager();
        DeleteAccountDialogFragment alertFragment =
            DeleteAccountDialogFragment.newInstance(accountUID);
        fm.setFragmentResultListener(DeleteAccountDialogFragment.TAG, this, this);
        alertFragment.show(fm, DeleteAccountDialogFragment.TAG);
    }

    private void toggleHidden(@NonNull MenuItem item) {
        boolean isHidden = !item.isChecked();
        item.setChecked(isHidden);
        @DrawableRes int hiddenIcon = isHidden ? R.drawable.ic_visibility_off : R.drawable.ic_visibility;
        item.setIcon(hiddenIcon);
        isShowHiddenAccounts = !isHidden;

        final int count = mFragmentPageReferenceMap.size();
        for (int i = 0; i < count; i++) {
            Refreshable refreshable = mFragmentPageReferenceMap.valueAt(i);
            if (refreshable instanceof AccountsListFragment) {
                AccountsListFragment fragment = (AccountsListFragment) refreshable;
                fragment.setShowHiddenAccounts(!isHidden);
            }
        }
    }
}
