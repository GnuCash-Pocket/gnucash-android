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

package org.gnucash.android.ui.account;

import static org.gnucash.android.db.DatabaseHelper.escapeForLike;
import static org.gnucash.android.util.ColorExtKt.parseColor;

import android.app.Activity;
import android.app.SearchManager;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.database.Cursor;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.PopupMenu;
import androidx.appcompat.widget.SearchView;
import androidx.core.view.MenuItemCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentResultListener;
import androidx.lifecycle.Lifecycle;
import androidx.loader.app.LoaderManager;
import androidx.loader.content.Loader;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.gnucash.android.R;
import org.gnucash.android.app.GnuCashApplication;
import org.gnucash.android.databinding.CardviewAccountBinding;
import org.gnucash.android.databinding.FragmentAccountsListBinding;
import org.gnucash.android.db.DatabaseCursorLoader;
import org.gnucash.android.db.DatabaseSchema;
import org.gnucash.android.db.adapter.AccountsDbAdapter;
import org.gnucash.android.db.adapter.BudgetsDbAdapter;
import org.gnucash.android.model.Account;
import org.gnucash.android.model.Budget;
import org.gnucash.android.model.Money;
import org.gnucash.android.ui.common.FormActivity;
import org.gnucash.android.ui.common.Refreshable;
import org.gnucash.android.ui.common.UxArgument;
import org.gnucash.android.ui.util.AccountBalanceTask;
import org.gnucash.android.ui.util.CursorRecyclerAdapter;
import org.gnucash.android.util.BackupManager;

import java.util.List;

import timber.log.Timber;

/**
 * Fragment for displaying the list of accounts in the database
 *
 * @author Ngewi Fet <ngewif@gmail.com>
 */
public class AccountsListFragment extends Fragment implements
    Refreshable,
    LoaderManager.LoaderCallbacks<Cursor>,
    SearchView.OnQueryTextListener,
    SearchView.OnCloseListener,
    FragmentResultListener {

    AccountRecyclerAdapter mAccountRecyclerAdapter;

    /**
     * Describes the kinds of accounts that should be loaded in the accounts list.
     * This enhances reuse of the accounts list fragment
     */
    public enum DisplayMode {
        TOP_LEVEL, RECENT, FAVORITES
    }

    /**
     * Field indicating which kind of accounts to load.
     * Default value is {@link DisplayMode#TOP_LEVEL}
     */
    private DisplayMode mDisplayMode = DisplayMode.TOP_LEVEL;

    /**
     * Tag to save {@link AccountsListFragment#mDisplayMode} to fragment state
     */
    private static final String STATE_DISPLAY_MODE = "mDisplayMode";

    /**
     * Database adapter for loading Account records from the database
     */
    private AccountsDbAdapter mAccountsDbAdapter;
    /**
     * Listener to be notified when an account is clicked
     */
    private OnAccountClickedListener mAccountSelectedListener;

    /**
     * GUID of the account whose children will be loaded in the list fragment.
     * If no parent account is specified, then all top-level accounts are loaded.
     */
    private String mParentAccountUID = null;

    /**
     * Filter for which accounts should be displayed. Used by search interface
     */
    private String mCurrentFilter;

    /**
     * Search view for searching accounts
     */
    private SearchView mSearchView;

    private FragmentAccountsListBinding mBinding;

    public static AccountsListFragment newInstance(DisplayMode displayMode) {
        AccountsListFragment fragment = new AccountsListFragment();
        fragment.mDisplayMode = displayMode;
        return fragment;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        mBinding = FragmentAccountsListBinding.inflate(inflater, container, false);

        mBinding.accountRecyclerView.setHasFixedSize(true);
        mBinding.accountRecyclerView.setEmptyView(mBinding.emptyView);

        switch (mDisplayMode) {

            case TOP_LEVEL:
                mBinding.emptyView.setText(R.string.label_no_accounts);
                break;
            case RECENT:
                mBinding.emptyView.setText(R.string.label_no_recent_accounts);
                break;
            case FAVORITES:
                mBinding.emptyView.setText(R.string.label_no_favorite_accounts);
                break;
        }

        if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
            GridLayoutManager gridLayoutManager = new GridLayoutManager(getActivity(), 2);
            mBinding.accountRecyclerView.setLayoutManager(gridLayoutManager);
        } else {
            LinearLayoutManager mLayoutManager = new LinearLayoutManager(getActivity());
            mBinding.accountRecyclerView.setLayoutManager(mLayoutManager);
        }
        return mBinding.getRoot();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Bundle args = getArguments();
        if (args != null)
            mParentAccountUID = args.getString(UxArgument.PARENT_ACCOUNT_UID);

        if (savedInstanceState != null)
            mDisplayMode = (DisplayMode) savedInstanceState.getSerializable(STATE_DISPLAY_MODE);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        ActionBar actionbar = ((AppCompatActivity) getActivity()).getSupportActionBar();
        actionbar.setTitle(R.string.title_accounts);
        actionbar.setDisplayHomeAsUpEnabled(true);
        setHasOptionsMenu(true);

        // specify an adapter (see also next example)
        mAccountRecyclerAdapter = new AccountRecyclerAdapter(null);
        mBinding.accountRecyclerView.setAdapter(mAccountRecyclerAdapter);
    }

    @Override
    public void onStart() {
        super.onStart();
        mAccountsDbAdapter = AccountsDbAdapter.getInstance();
    }

    @Override
    public void onStop() {
        super.onStop();
        mBinding.accountRecyclerView.setAdapter(null);
    }

    @Override
    public void onResume() {
        super.onResume();
        refresh();
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        try {
            mAccountSelectedListener = (OnAccountClickedListener) activity;
        } catch (ClassCastException e) {
            throw new ClassCastException(activity.toString() + " must implement OnAccountSelectedListener");
        }
    }

    public void onListItemClick(String accountUID) {
        mAccountSelectedListener.accountSelected(accountUID);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == Activity.RESULT_CANCELED)
            return;
        refresh();
    }

    /**
     * Delete the account with UID.
     * It shows the delete confirmation dialog if the account has transactions,
     * else deletes the account immediately
     *
     * @param accountUID The UID of the account
     */
    private void tryDeleteAccount(String accountUID) {
        if (mAccountsDbAdapter.getTransactionCount(accountUID) > 0 || mAccountsDbAdapter.getSubAccountCount(accountUID) > 0) {
            showConfirmationDialog(accountUID);
        } else {
            BackupManager.backupActiveBookAsync(requireActivity(), result -> {
                // Avoid calling AccountsDbAdapter.deleteRecord(long). See #654
                mAccountsDbAdapter.deleteRecord(accountUID);
                refresh();
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
        FragmentManager fm = getParentFragmentManager();
        DeleteAccountDialogFragment alertFragment =
            DeleteAccountDialogFragment.newInstance(accountUID);
        fm.setFragmentResultListener(DeleteAccountDialogFragment.TAG, this, this);
        alertFragment.show(fm, DeleteAccountDialogFragment.TAG);
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        if (mParentAccountUID != null)
            inflater.inflate(R.menu.sub_account_actions, menu);
        else {
            inflater.inflate(R.menu.account_actions, menu);
            // Associate searchable configuration with the SearchView

            SearchManager searchManager =
                (SearchManager) GnuCashApplication.getAppContext().getSystemService(Context.SEARCH_SERVICE);
            mSearchView = (SearchView)
                MenuItemCompat.getActionView(menu.findItem(R.id.menu_search));
            if (mSearchView == null)
                return;

            mSearchView.setSearchableInfo(
                searchManager.getSearchableInfo(getActivity().getComponentName()));
            mSearchView.setOnQueryTextListener(this);
            mSearchView.setOnCloseListener(this);
        }
    }


    @Override
    /**
     * Refresh the account list as a sublist of another account
     * @param parentAccountUID GUID of the parent account
     */
    public void refresh(String parentAccountUID) {
        getArguments().putString(UxArgument.PARENT_ACCOUNT_UID, parentAccountUID);
        refresh();
    }

    /**
     * Refreshes the list by restarting the {@link DatabaseCursorLoader} associated
     * with the ListView
     */
    @Override
    public void refresh() {
        getLoaderManager().restartLoader(0, null, this);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putSerializable(STATE_DISPLAY_MODE, mDisplayMode);
    }

    /**
     * Closes any open database adapters used by the list
     */
    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mAccountRecyclerAdapter != null)
            mAccountRecyclerAdapter.swapCursor(null);
    }

    /**
     * Opens a new activity for creating or editing an account.
     * If the <code>accountUID</code> is empty, then create else edit the account.
     *
     * @param accountUID Unique ID of account to be edited. Pass 0 to create a new account.
     */
    public void openCreateOrEditActivity(String accountUID) {
        Intent editAccountIntent = new Intent(AccountsListFragment.this.getActivity(), FormActivity.class);
        editAccountIntent.setAction(Intent.ACTION_INSERT_OR_EDIT);
        editAccountIntent.putExtra(UxArgument.SELECTED_ACCOUNT_UID, accountUID);
        editAccountIntent.putExtra(UxArgument.FORM_TYPE, FormActivity.FormType.ACCOUNT.name());
        startActivity(editAccountIntent);
    }

    @NonNull
    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        Timber.d("Creating the accounts loader");
        Bundle arguments = getArguments();
        String parentAccountUID = arguments == null ? null : arguments.getString(UxArgument.PARENT_ACCOUNT_UID);

        Context context = requireContext();
        if (TextUtils.isEmpty(mCurrentFilter)) {
            return new AccountsCursorLoader(context, parentAccountUID, mDisplayMode);
        } else {
            return new AccountsCursorLoader(context, mCurrentFilter);
        }
    }

    @Override
    public void onLoadFinished(@NonNull Loader<Cursor> loader, Cursor cursor) {
        Timber.d("Accounts loader finished. Swapping in cursor");
        mAccountRecyclerAdapter.swapCursor(cursor);
        mAccountRecyclerAdapter.notifyDataSetChanged();
        if (getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.RESUMED)) {
            if (mBinding.accountRecyclerView.getAdapter() == null) {
                mBinding.accountRecyclerView.setAdapter(mAccountRecyclerAdapter);
            }
        }
    }

    @Override
    public void onLoaderReset(@NonNull Loader<Cursor> loader) {
        Timber.d("Resetting the accounts loader");
        mAccountRecyclerAdapter.swapCursor(null);
    }

    @Override
    public boolean onQueryTextSubmit(String query) {
        //nothing to see here, move along
        return true;
    }

    @Override
    public boolean onQueryTextChange(String newText) {
        String newFilter = !TextUtils.isEmpty(newText) ? newText : null;

        if (mCurrentFilter == null && newFilter == null) {
            return true;
        }
        if (mCurrentFilter != null && mCurrentFilter.equals(newFilter)) {
            return true;
        }
        mCurrentFilter = newFilter;
        getLoaderManager().restartLoader(0, null, this);
        return true;
    }

    @Override
    public boolean onClose() {
        if (!TextUtils.isEmpty(mSearchView.getQuery())) {
            mSearchView.setQuery(null, true);
        }
        return true;
    }

    /**
     * Extends {@link DatabaseCursorLoader} for loading of {@link Account} from the
     * database asynchronously.
     * <p>By default it loads only top-level accounts (accounts which have no parent or have GnuCash ROOT account as parent.
     * By submitting a parent account ID in the constructor parameter, it will load child accounts of that parent.</p>
     * <p>Class must be static because the Android loader framework requires it to be so</p>
     *
     * @author Ngewi Fet <ngewif@gmail.com>
     */
    private static final class AccountsCursorLoader extends DatabaseCursorLoader {
        private String mParentAccountUID = null;
        private String mFilter;
        private DisplayMode mDisplayMode = DisplayMode.TOP_LEVEL;

        /**
         * Initializes the loader to load accounts from the database.
         * If the <code>parentAccountId <= 0</code> then only top-level accounts are loaded.
         * Else only the child accounts of the <code>parentAccountId</code> will be loaded
         *
         * @param context          Application context
         * @param parentAccountUID GUID of the parent account
         */
        public AccountsCursorLoader(Context context, String parentAccountUID, DisplayMode displayMode) {
            super(context);
            this.mParentAccountUID = parentAccountUID;
            this.mDisplayMode = displayMode;
        }

        /**
         * Initializes the loader with a filter for account names.
         * Only accounts whose name match the filter will be loaded.
         *
         * @param context Application context
         * @param filter  Account name filter string
         */
        public AccountsCursorLoader(Context context, String filter) {
            super(context);
            mFilter = filter;
        }

        @Override
        public Cursor loadInBackground() {
            final AccountsDbAdapter adapter = AccountsDbAdapter.getInstance();
            mDatabaseAdapter = adapter;
            Cursor cursor;

            if (mFilter != null) {
                cursor = adapter
                    .fetchAccounts(DatabaseSchema.AccountEntry.COLUMN_HIDDEN + "= 0 AND "
                            + DatabaseSchema.AccountEntry.COLUMN_NAME + " LIKE '%" + escapeForLike(mFilter) + "%'",
                        null, null);
            } else if (!TextUtils.isEmpty(mParentAccountUID))
                cursor = adapter.fetchSubAccounts(mParentAccountUID);
            else {
                switch (mDisplayMode) {
                    case RECENT:
                        cursor = adapter.fetchRecentAccounts(10);
                        break;
                    case FAVORITES:
                        cursor = adapter.fetchFavoriteAccounts();
                        break;
                    case TOP_LEVEL:
                    default:
                        cursor = adapter.fetchTopLevelAccounts();
                        break;
                }
            }

            if (cursor != null)
                registerContentObserver(cursor);
            return cursor;
        }
    }

    @Override
    public void onFragmentResult(@NonNull String requestKey, @NonNull Bundle result) {
        if (DeleteAccountDialogFragment.TAG.equals(requestKey)) {
            boolean refresh = result.getBoolean(Refreshable.EXTRA_REFRESH);
            if (refresh) refresh();
        }
    }

    class AccountRecyclerAdapter extends CursorRecyclerAdapter<AccountRecyclerAdapter.AccountViewHolder> {

        public AccountRecyclerAdapter(Cursor cursor) {
            super(cursor);
        }

        @NonNull
        @Override
        public AccountViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            CardviewAccountBinding binding = CardviewAccountBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
            return new AccountViewHolder(binding);
        }

        @Override
        public void onBindViewHolderCursor(@NonNull final AccountViewHolder holder, @NonNull final Cursor cursor) {
            holder.bind(cursor);
        }

        class AccountViewHolder extends RecyclerView.ViewHolder implements PopupMenu.OnMenuItemClickListener {
            private final TextView accountName;
            private final TextView description;
            private final TextView accountBalance;
            private final ImageView createTransaction;
            private final ImageView favoriteStatus;
            private final ImageView optionsMenu;
            private final View colorStripView;
            private final ProgressBar budgetIndicator;

            private String accountUID;

            public AccountViewHolder(CardviewAccountBinding binding) {
                super(binding.getRoot());
                this.accountName = binding.listItem.primaryText;
                this.description = binding.listItem.secondaryText;
                this.accountBalance = binding.accountBalance;
                this.createTransaction = binding.createTransaction;
                this.favoriteStatus = binding.favoriteStatus;
                this.optionsMenu = binding.optionsMenu;
                this.colorStripView = binding.accountColorStrip;
                this.budgetIndicator = binding.budgetIndicator;

                optionsMenu.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        PopupMenu popup = new PopupMenu(getActivity(), v);
                        popup.setOnMenuItemClickListener(AccountViewHolder.this);
                        MenuInflater inflater = popup.getMenuInflater();
                        inflater.inflate(R.menu.account_context_menu, popup.getMenu());
                        popup.show();
                    }
                });
            }

            public void bind(@NonNull final Cursor cursor) {
                final String accountUID = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseSchema.AccountEntry.COLUMN_UID));
                this.accountUID = accountUID;

                accountName.setText(cursor.getString(cursor.getColumnIndexOrThrow(DatabaseSchema.AccountEntry.COLUMN_NAME)));
                int subAccountCount = mAccountsDbAdapter.getSubAccountCount(accountUID);
                if (subAccountCount > 0) {
                    description.setVisibility(View.VISIBLE);
                    String text = getResources().getQuantityString(R.plurals.label_sub_accounts, subAccountCount, subAccountCount);
                    description.setText(text);
                } else {
                    description.setVisibility(View.GONE);
                }

                // add a summary of transactions to the account view

                // Make sure the balance task is truly multi-thread
                new AccountBalanceTask(accountBalance).execute(accountUID);

                String accountColor = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseSchema.AccountEntry.COLUMN_COLOR_CODE));
                Integer colorValue = parseColor(accountColor);
                int colorCode = (colorValue != null) ? colorValue : Account.DEFAULT_COLOR;
                colorStripView.setBackgroundColor(colorCode);

                boolean isPlaceholderAccount = mAccountsDbAdapter.isPlaceholderAccount(accountUID);
                if (isPlaceholderAccount) {
                    createTransaction.setVisibility(View.GONE);
                } else {
                    createTransaction.setOnClickListener(new View.OnClickListener() {

                        @Override
                        public void onClick(View v) {
                            Context context = v.getContext();
                            Intent intent = new Intent(context, FormActivity.class);
                            intent.setAction(Intent.ACTION_INSERT_OR_EDIT);
                            intent.putExtra(UxArgument.SELECTED_ACCOUNT_UID, accountUID);
                            intent.putExtra(UxArgument.FORM_TYPE, FormActivity.FormType.TRANSACTION.name());
                            context.startActivity(intent);
                        }
                    });
                }

                List<Budget> budgets = BudgetsDbAdapter.getInstance().getAccountBudgets(accountUID);
                //TODO: include fetch only active budgets
                if (budgets.size() == 1) {
                    Budget budget = budgets.get(0);
                    Money balance = mAccountsDbAdapter.getAccountBalance(accountUID, budget.getStartofCurrentPeriod(), budget.getEndOfCurrentPeriod());
                    double budgetProgress = balance.div(budget.getAmount(accountUID)).asBigDecimal().doubleValue() * 100;

                    budgetIndicator.setVisibility(View.VISIBLE);
                    budgetIndicator.setProgress((int) budgetProgress);
                } else {
                    budgetIndicator.setVisibility(View.GONE);
                }

                if (mAccountsDbAdapter.isFavoriteAccount(accountUID)) {
                    favoriteStatus.setImageResource(R.drawable.ic_favorite_black);
                } else {
                    favoriteStatus.setImageResource(R.drawable.ic_favorite_border_black);
                }

                favoriteStatus.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        boolean isFavoriteAccount = mAccountsDbAdapter.isFavoriteAccount(accountUID);

                        ContentValues contentValues = new ContentValues();
                        contentValues.put(DatabaseSchema.AccountEntry.COLUMN_FAVORITE, !isFavoriteAccount);
                        mAccountsDbAdapter.updateRecord(accountUID, contentValues);

                        @DrawableRes int drawableResource = isFavoriteAccount ?
                            R.drawable.ic_favorite_border_black : R.drawable.ic_favorite_black;
                        favoriteStatus.setImageResource(drawableResource);
                        if (mDisplayMode == DisplayMode.FAVORITES) {
                            refresh();
                        }
                    }
                });

                itemView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        onListItemClick(accountUID);
                    }
                });
            }

            @Override
            public boolean onMenuItemClick(@NonNull MenuItem item) {
                switch (item.getItemId()) {
                    case R.id.context_menu_edit_accounts:
                        openCreateOrEditActivity(accountUID);
                        return true;

                    case R.id.context_menu_delete:
                        tryDeleteAccount(accountUID);
                        return true;

                    default:
                        return false;
                }
            }
        }
    }
}
