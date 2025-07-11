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

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.database.Cursor;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.PopupMenu;
import androidx.appcompat.widget.SearchView;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentResultListener;
import androidx.lifecycle.Lifecycle;
import androidx.loader.app.LoaderManager;
import androidx.loader.content.Loader;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.gnucash.android.R;
import org.gnucash.android.app.MenuFragment;
import org.gnucash.android.databinding.CardviewAccountBinding;
import org.gnucash.android.databinding.FragmentAccountsListBinding;
import org.gnucash.android.db.DatabaseCursorLoader;
import org.gnucash.android.db.DatabaseSchema;
import org.gnucash.android.db.adapter.AccountsDbAdapter;
import org.gnucash.android.model.Account;
import org.gnucash.android.ui.common.FormActivity;
import org.gnucash.android.ui.common.Refreshable;
import org.gnucash.android.ui.common.UxArgument;
import org.gnucash.android.ui.util.AccountBalanceTask;
import org.gnucash.android.ui.util.CursorRecyclerAdapter;
import org.gnucash.android.util.BackupManager;

import java.util.ArrayList;
import java.util.List;

import timber.log.Timber;

/**
 * Fragment for displaying the list of accounts in the database
 *
 * @author Ngewi Fet <ngewif@gmail.com>
 */
public class AccountsListFragment extends MenuFragment implements
    Refreshable,
    LoaderManager.LoaderCallbacks<Cursor>,
    SearchView.OnQueryTextListener,
    FragmentResultListener {

    private AccountRecyclerAdapter mAccountRecyclerAdapter;

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
    private static final String STATE_DISPLAY_MODE = "display_mode";

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
    private boolean isShowHiddenAccounts = false;

    private FragmentAccountsListBinding mBinding;
    private final List<AccountBalanceTask> accountBalanceTasks = new ArrayList<>();

    public static AccountsListFragment newInstance(DisplayMode displayMode) {
        Bundle args = new Bundle();
        args.putSerializable(STATE_DISPLAY_MODE, displayMode);
        AccountsListFragment fragment = new AccountsListFragment();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        mBinding = FragmentAccountsListBinding.inflate(inflater, container, false);
        return mBinding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        AppCompatActivity activity = (AppCompatActivity) requireActivity();
        ActionBar actionbar = activity.getSupportActionBar();
        assert actionbar != null;
        actionbar.setTitle(R.string.title_accounts);
        actionbar.setDisplayHomeAsUpEnabled(true);

        mBinding.list.setHasFixedSize(true);
        mBinding.list.setEmptyView(mBinding.emptyView);
        mBinding.list.setAdapter(mAccountRecyclerAdapter);
        mBinding.list.setTag("accounts");

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
            mBinding.list.setLayoutManager(gridLayoutManager);
        } else {
            LinearLayoutManager mLayoutManager = new LinearLayoutManager(getActivity());
            mBinding.list.setLayoutManager(mLayoutManager);
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Bundle args = getArguments();
        if (args != null) {
            mParentAccountUID = args.getString(UxArgument.PARENT_ACCOUNT_UID);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                mDisplayMode = args.getSerializable(STATE_DISPLAY_MODE, DisplayMode.class);
            } else {
                mDisplayMode = (DisplayMode) args.getSerializable(STATE_DISPLAY_MODE);
            }
            isShowHiddenAccounts = args.getBoolean(UxArgument.SHOW_HIDDEN, isShowHiddenAccounts);
        }

        if (savedInstanceState != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                mDisplayMode = savedInstanceState.getSerializable(STATE_DISPLAY_MODE, DisplayMode.class);
            } else {
                mDisplayMode = (DisplayMode) savedInstanceState.getSerializable(STATE_DISPLAY_MODE);
            }
        }
        if (mDisplayMode == null) {
            mDisplayMode = DisplayMode.TOP_LEVEL;
        }

        // specify an adapter (see also next example)
        mAccountRecyclerAdapter = new AccountRecyclerAdapter(null);
    }

    @Override
    public void onStart() {
        super.onStart();
        mAccountsDbAdapter = AccountsDbAdapter.getInstance();
    }

    @Override
    public void onStop() {
        super.onStop();
        mBinding.list.setAdapter(null);
    }

    @Override
    public void onResume() {
        super.onResume();
        refresh();
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        try {
            mAccountSelectedListener = (OnAccountClickedListener) context;
        } catch (ClassCastException e) {
            throw new ClassCastException(context + " must implement OnAccountSelectedListener");
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
     * @param activity   The activity context.
     * @param accountUID The UID of the account
     */
    private void tryDeleteAccount(final Activity activity, final String accountUID) {
        if (mAccountsDbAdapter.getTransactionCount(accountUID) > 0 || mAccountsDbAdapter.getSubAccountCount(accountUID) > 0) {
            showConfirmationDialog(accountUID);
        } else {
            BackupManager.backupActiveBookAsync(activity, result -> {
                if (result) {
                    try {
                        // Avoid calling AccountsDbAdapter.deleteRecord(long). See #654
                        mAccountsDbAdapter.deleteRecord(accountUID);
                        refreshActivity();
                    } catch (Exception e) {
                        Timber.e(e);
                    }
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
        FragmentManager fm = getParentFragmentManager();
        DeleteAccountDialogFragment alertFragment =
            DeleteAccountDialogFragment.newInstance(accountUID);
        fm.setFragmentResultListener(DeleteAccountDialogFragment.TAG, this, this);
        alertFragment.show(fm, DeleteAccountDialogFragment.TAG);
    }

    private void toggleFavorite(@NonNull String accountUID, boolean isFavoriteAccount) {
        ContentValues contentValues = new ContentValues();
        contentValues.put(DatabaseSchema.AccountEntry.COLUMN_FAVORITE, isFavoriteAccount);
        mAccountsDbAdapter.updateRecord(accountUID, contentValues);
        refreshActivity();
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
        if (isDetached() || getFragmentManager() == null) return;
        cancelBalances();
        getLoaderManager().restartLoader(0, null, this);
    }

    private void refreshActivity() {
        // Tell the parent activity to refresh all the lists.
        Activity activity = getActivity();
        if (activity instanceof Refreshable) {
            ((Refreshable) activity).refresh();
        } else {
            refresh();
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putSerializable(STATE_DISPLAY_MODE, mDisplayMode);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (mAccountRecyclerAdapter != null) {
            mAccountRecyclerAdapter.changeCursor(null);
        }
        cancelBalances();
    }

    private void cancelBalances() {
        for (AccountBalanceTask task : accountBalanceTasks) {
            task.cancel(true);
        }
        accountBalanceTasks.clear();
    }

    /**
     * Opens a new activity for creating or editing an account.
     * If the <code>accountUID</code> is empty, then create else edit the account.
     *
     * @param accountUID Unique ID of account to be edited. Pass 0 to create a new account.
     */
    public void openCreateOrEditActivity(Context context, String accountUID) {
        Intent intent = new Intent(context, FormActivity.class)
            .setAction(Intent.ACTION_INSERT_OR_EDIT)
            .putExtra(UxArgument.SELECTED_ACCOUNT_UID, accountUID)
            .putExtra(UxArgument.FORM_TYPE, FormActivity.FormType.ACCOUNT.name());
        context.startActivity(intent);
    }

    @NonNull
    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        Timber.d("Creating the accounts loader");
        Bundle arguments = getArguments();
        String parentAccountUID = arguments == null ? null : arguments.getString(UxArgument.PARENT_ACCOUNT_UID);

        Context context = requireContext();
        return new AccountsCursorLoader(context, parentAccountUID, mDisplayMode, mCurrentFilter, isShowHiddenAccounts);
    }

    @SuppressLint("NotifyDataSetChanged")
    @Override
    public void onLoadFinished(@NonNull Loader<Cursor> loader, Cursor cursor) {
        Timber.d("Accounts loader finished. Swapping in cursor");
        mAccountRecyclerAdapter.changeCursor(cursor);
        if (getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.RESUMED)) {
            if (mBinding.list.getAdapter() == null) {
                mBinding.list.setAdapter(mAccountRecyclerAdapter);
            }
            mAccountRecyclerAdapter.notifyDataSetChanged();
        }
    }

    @Override
    public void onLoaderReset(@NonNull Loader<Cursor> loader) {
        Timber.d("Resetting the accounts loader");
        mAccountRecyclerAdapter.changeCursor(null);
    }

    @Override
    public boolean onQueryTextSubmit(String query) {
        //nothing to see here, move along
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
        mCurrentFilter = newFilter;
        refresh();
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
    private static final class AccountsCursorLoader extends DatabaseCursorLoader<AccountsDbAdapter> {
        private final String mParentAccountUID;
        private final String mFilter;
        private final DisplayMode mDisplayMode;
        private final boolean isShowHiddenAccounts;

        /**
         * Initializes the loader to load accounts from the database.
         * If the <code>parentAccountId <= 0</code> then only top-level accounts are loaded.
         * Else only the child accounts of the <code>parentAccountId</code> will be loaded
         *
         * @param context              Application context
         * @param parentAccountUID     GUID of the parent account
         * @param displayMode          the mode.
         * @param filter               Account name filter string
         * @param isShowHiddenAccounts Hidden accounts are visible?
         */
        public AccountsCursorLoader(Context context, String parentAccountUID, DisplayMode displayMode, @Nullable String filter, boolean isShowHiddenAccounts) {
            super(context);
            this.mParentAccountUID = parentAccountUID;
            this.mDisplayMode = displayMode;
            this.mFilter = filter;
            this.isShowHiddenAccounts = isShowHiddenAccounts;
        }

        @Override
        public Cursor loadInBackground() {
            final AccountsDbAdapter dbAdapter = AccountsDbAdapter.getInstance();
            if (dbAdapter == null) return null;
            databaseAdapter = dbAdapter;
            final Cursor cursor;

            if (!TextUtils.isEmpty(mParentAccountUID)) {
                cursor = dbAdapter.fetchSubAccounts(mParentAccountUID, isShowHiddenAccounts);
            } else {
                switch (mDisplayMode) {
                    case RECENT:
                        cursor = dbAdapter.fetchRecentAccounts(10, mFilter, isShowHiddenAccounts);
                        break;
                    case FAVORITES:
                        cursor = dbAdapter.fetchFavoriteAccounts(mFilter, isShowHiddenAccounts);
                        break;
                    case TOP_LEVEL:
                    default:
                        cursor = dbAdapter.fetchTopLevelAccounts(mFilter, isShowHiddenAccounts);
                        break;
                }
            }

            return cursor;
        }
    }

    @Override
    public void onFragmentResult(@NonNull String requestKey, @NonNull Bundle result) {
        if (DeleteAccountDialogFragment.TAG.equals(requestKey)) {
            boolean refresh = result.getBoolean(Refreshable.EXTRA_REFRESH);
            if (refresh) refreshActivity();
        }
    }

    public void setShowHiddenAccounts(boolean isVisible) {
        boolean wasVisible = isShowHiddenAccounts;
        if (wasVisible != isVisible) {
            isShowHiddenAccounts = isVisible;
            refresh();
        }
    }

    class AccountRecyclerAdapter extends CursorRecyclerAdapter<AccountRecyclerAdapter.AccountViewHolder> {

        public AccountRecyclerAdapter(@Nullable Cursor cursor) {
            super(cursor);
            setHasStableIds(true);
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
            private final CheckBox favoriteStatus;
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
                if (!isResumed()) return;
                AccountsDbAdapter accountsDbAdapter = mAccountsDbAdapter;
                final String accountUID = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseSchema.AccountEntry.COLUMN_UID));
                this.accountUID = accountUID;
                Account account = accountsDbAdapter.getSimpleRecord(accountUID);

                accountName.setText(account.getName());
                int subAccountCount = accountsDbAdapter.getSubAccountCount(accountUID);
                if (subAccountCount > 0) {
                    description.setVisibility(View.VISIBLE);
                    String text = getResources().getQuantityString(R.plurals.label_sub_accounts, subAccountCount, subAccountCount);
                    description.setText(text);
                } else {
                    description.setVisibility(View.GONE);
                }

                // add a summary of transactions to the account view

                // Make sure the balance task is truly multi-thread
                AccountBalanceTask task = new AccountBalanceTask(accountsDbAdapter, accountBalance, description.getCurrentTextColor());
                accountBalanceTasks.add(task);
                task.execute(accountUID);

                @ColorInt int accountColor = getColor(account, accountsDbAdapter);
                colorStripView.setBackgroundColor(accountColor);

                if (account.isPlaceholder()) {
                    createTransaction.setVisibility(View.INVISIBLE);
                } else {
                    createTransaction.setOnClickListener(new View.OnClickListener() {

                        @Override
                        public void onClick(View v) {
                            Context context = v.getContext();
                            Intent intent = new Intent(context, FormActivity.class)
                                .setAction(Intent.ACTION_INSERT_OR_EDIT)
                                .putExtra(UxArgument.SELECTED_ACCOUNT_UID, accountUID)
                                .putExtra(UxArgument.FORM_TYPE, FormActivity.FormType.TRANSACTION.name());
                            context.startActivity(intent);
                        }
                    });
                }

                // TODO budgets is not an official feature yet.
//                List<Budget> budgets = BudgetsDbAdapter.getInstance().getAccountBudgets(accountUID);
//                //TODO: include fetch only active budgets
//                if (!budgets.isEmpty()) {
//                    Budget budget = budgets.get(0);
//                    Money balance = accountsDbAdapter.getAccountBalance(accountUID, budget.getStartOfCurrentPeriod(), budget.getEndOfCurrentPeriod());
//                    Money budgetAmount = budget.getAmount(accountUID);
//
//                    if (budgetAmount != null) {
//                        double budgetProgress = budgetAmount.isAmountZero() ? 0 : balance.div(budgetAmount).toDouble() * 100;
//                        budgetIndicator.setVisibility(View.VISIBLE);
//                        budgetIndicator.setProgress((int) budgetProgress);
//                    } else {
//                        budgetIndicator.setVisibility(View.GONE);
//                    }
//                } else {
//                    budgetIndicator.setVisibility(View.GONE);
//                }

                boolean isFavoriteAccount = accountsDbAdapter.isFavoriteAccount(accountUID);
                favoriteStatus.setOnCheckedChangeListener(null);
                favoriteStatus.setChecked(isFavoriteAccount);
                favoriteStatus.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                        toggleFavorite(accountUID, isChecked);
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
                final Activity activity = getActivity();
                if (activity == null) return false;

                switch (item.getItemId()) {
                    case R.id.menu_edit:
                        openCreateOrEditActivity(activity, accountUID);
                        return true;

                    case R.id.menu_delete:
                        tryDeleteAccount(activity, accountUID);
                        return true;

                    default:
                        return false;
                }
            }

            @ColorInt
            private int getColor(@NonNull Account account, @NonNull AccountsDbAdapter accountsDbAdapter) {
                @ColorInt int color = account.getColor();
                if (color == Account.DEFAULT_COLOR) {
                    color = getParentColor(account, accountsDbAdapter);
                }
                return color;
            }

            @ColorInt
            private int getParentColor(@NonNull Account account, @NonNull AccountsDbAdapter accountsDbAdapter) {
                String parentUID = account.getParentUID();
                if (TextUtils.isEmpty(parentUID)) {
                    return Account.DEFAULT_COLOR;
                }
                Account parentAccount = accountsDbAdapter.getSimpleRecord(parentUID);
                return getColor(parentAccount, accountsDbAdapter);
            }
        }
    }
}
