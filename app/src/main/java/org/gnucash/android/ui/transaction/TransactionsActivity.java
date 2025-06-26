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

import static org.apache.commons.lang3.math.NumberUtils.min;

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
import androidx.annotation.Nullable;
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
            swapAccount(account);
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
            if (account != null && account.isPlaceholder()) {
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
            if (account != null && !account.isPlaceholder()) {
                return DEFAULT_NUM_PAGES;
            }
            return 1;
        }

        /**
         * Creates and initializes the fragment for displaying sub-account list
         *
         * @return {@link AccountsListFragment} initialized with the sub-accounts
         */
        private AccountsListFragment prepareSubAccountsListFragment() {
            String accountUID = getAccountUID();
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
            String accountUID = getAccountUID();
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
    public void refresh(final String accountUID) {
        final ActivityTransactionsBinding binding = mBinding;
        setTitleIndicatorColor(binding, accountUID);

        for (int i = 0; i < mFragmentPageReferenceMap.size(); i++) {
            mFragmentPageReferenceMap.valueAt(i).refresh(accountUID);
        }

        if (mPagerAdapter != null) {
            mPagerAdapter.notifyDataSetChanged();
        }

        binding.toolbarLayout.toolbarSpinner.setEnabled(!accountNameAdapter.isEmpty());
    }

    @Override
    public void refresh() {
        String accountUID = getAccountUID();
        refresh(accountUID);
    }

    @Override
    public void inflateView() {
        final ActivityTransactionsBinding binding = mBinding = ActivityTransactionsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        mDrawerLayout = binding.drawerLayout;
        mNavigationView = binding.navView;
        mToolbar = binding.toolbarLayout.toolbar;
        mToolbarProgress = binding.toolbarLayout.toolbarProgress.progress;
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

        final ActivityTransactionsBinding binding = mBinding;
        String accountUID = getIntent().getStringExtra(UxArgument.SELECTED_ACCOUNT_UID);
        if (TextUtils.isEmpty(accountUID)) {
            accountUID = mAccountsDbAdapter.getOrCreateRootAccountUID();
        }
        account = mAccountsDbAdapter.getSimpleRecord(accountUID);
        if (account == null) {
            Timber.e("Account not found");
            finish();
            return;
        }

        binding.tabLayout.addTab(binding.tabLayout.newTab().setText(R.string.section_header_subaccounts));
        if (!account.isPlaceholder()) {
            binding.tabLayout.addTab(binding.tabLayout.newTab().setText(R.string.section_header_transactions));
        }

        setupActionBarNavigation(binding, accountUID);

        mPagerAdapter = new AccountViewPagerAdapter(getSupportFragmentManager());
        binding.pager.setAdapter(mPagerAdapter);
        binding.pager.addOnPageChangeListener(new TabLayout.TabLayoutOnPageChangeListener(binding.tabLayout));

        binding.tabLayout.setOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                int position = tab.getPosition();
                binding.pager.setCurrentItem(min(position, binding.pager.getChildCount()));
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

        binding.fabCreateTransaction.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switch (binding.pager.getCurrentItem()) {
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
    private void setTitleIndicatorColor(ActivityTransactionsBinding binding, String accountUID) {
        Account account = this.account;
        if (account == null) return;
        @ColorInt int accountColor = account.getColor();
        if (accountColor == Account.DEFAULT_COLOR) {
            accountColor = mAccountsDbAdapter.getActiveAccountColor(this, account.getUID());
        }
        setTitlesColor(accountColor);
        binding.tabLayout.setBackgroundColor(accountColor);
    }

    /**
     * Set up action bar navigation list and listener callbacks
     */
    private void setupActionBarNavigation(ActivityTransactionsBinding binding, final String accountUID) {
        if (accountNameAdapter == null) {
            final Context contextWithTheme = binding.toolbarLayout.toolbarSpinner.getContext();
            accountNameAdapter = new QualifiedAccountNameAdapter(contextWithTheme, mAccountsDbAdapter, this);
            accountNameAdapter.load(new Function0<Unit>() {
                @Override
                public Unit invoke() {
                    int position = accountNameAdapter.getPosition(accountUID);
                    binding.toolbarLayout.toolbarSpinner.setSelection(position);
                    return null;
                }
            });
        }
        binding.toolbarLayout.toolbarSpinner.setAdapter(accountNameAdapter);
        binding.toolbarLayout.toolbarSpinner.setOnItemSelectedListener(accountSpinnerListener);
        updateNavigationSelection(binding, accountUID);
        setTitleIndicatorColor(binding, accountUID);
    }

    /**
     * Updates the action bar navigation list selection to that of the current account
     * whose transactions are being displayed/manipulated
     */
    private void updateNavigationSelection(ActivityTransactionsBinding binding, String accountUID) {
        if (accountNameAdapter.isEmpty()) return;
        int position = accountNameAdapter.getPosition(accountUID);
        // In case the account was deleted.
        Account account = this.account;
        if (position == AdapterView.INVALID_POSITION && account != null) {
            accountUID = account.getParentUID();
            position = accountNameAdapter.getPosition(accountUID);
            account = accountNameAdapter.getAccount(position);
            this.account = account;
        }
        binding.toolbarLayout.toolbarSpinner.setSelection(position);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        if (account == null) {
            return super.onPrepareOptionsMenu(menu);
        }
        MenuItem favoriteAccountMenuItem = menu.findItem(R.id.menu_favorite);

        if (favoriteAccountMenuItem == null) //when the activity is used to edit a transaction
            return super.onPrepareOptionsMenu(menu);

        boolean isFavoriteAccount = account.isFavorite();
        @DrawableRes int favoriteIcon = isFavoriteAccount ? R.drawable.ic_favorite : R.drawable.ic_favorite_border;
        favoriteAccountMenuItem.setIcon(favoriteIcon);

        MenuItem itemHidden = menu.findItem(R.id.menu_hidden);
        if (itemHidden != null) {
            showHiddenAccounts(itemHidden, isShowHiddenAccounts);
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
        showHiddenAccounts(item, !item.isChecked());
    }

    private void showHiddenAccounts(@NonNull MenuItem item, boolean isVisible) {
        item.setChecked(isVisible);
        @DrawableRes int visibilityIcon = isVisible ? R.drawable.ic_visibility_off : R.drawable.ic_visibility;
        item.setIcon(visibilityIcon);
        isShowHiddenAccounts = isVisible;
        // apply to each page
        final int count = mFragmentPageReferenceMap.size();
        for (int i = 0; i < count; i++) {
            Refreshable refreshable = mFragmentPageReferenceMap.valueAt(i);
            if (refreshable instanceof AccountsListFragment) {
                AccountsListFragment fragment = (AccountsListFragment) refreshable;
                fragment.setShowHiddenAccounts(isVisible);
            }
        }
    }

    private String getAccountUID() {
        return (account != null) ? account.getUID() : getIntent().getStringExtra(UxArgument.SELECTED_ACCOUNT_UID);
    }

    private void swapAccount(@Nullable Account account) {
        final ActivityTransactionsBinding binding = mBinding;
        if (account != null) {
            this.account = account;
            String accountUID = account.getUID();
            //update the intent in case the account gets rotated
            getIntent().putExtra(UxArgument.SELECTED_ACCOUNT_UID, accountUID);
            if (account.isPlaceholder()) {
                if (binding.tabLayout.getTabCount() > 1) {
                    binding.tabLayout.removeTabAt(INDEX_TRANSACTIONS_FRAGMENT);
                }
            } else {
                if (binding.tabLayout.getTabCount() < 2) {
                    binding.tabLayout.addTab(binding.tabLayout.newTab().setText(R.string.section_header_transactions));
                }
            }
            mPagerAdapter.notifyDataSetChanged();

            //if there are no transactions, and there are sub-accounts, show the sub-accounts
            long txCount = transactionsDbAdapter.getTransactionsCount(accountUID);
            if (txCount == 0) {
                long subCount = mAccountsDbAdapter.getSubAccountCount(accountUID);
                if ((subCount > 0) || (binding.tabLayout.getTabCount() < 2)) {
                    binding.pager.setCurrentItem(INDEX_SUB_ACCOUNTS_FRAGMENT);
                } else {
                    binding.pager.setCurrentItem(INDEX_TRANSACTIONS_FRAGMENT);
                }
            } else {
                binding.pager.setCurrentItem(INDEX_TRANSACTIONS_FRAGMENT);
            }

            //refresh any fragments in the tab with the new account UID
            refresh(accountUID);
        } else {
            //refresh any fragments in the tab with the new account UID
            refresh();
        }
        supportInvalidateOptionsMenu();
    }
}
