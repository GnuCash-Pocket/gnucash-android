/*
 * Copyright (c) 2012 - 2014 Ngewi Fet <ngewif@gmail.com>
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

import android.app.Activity;
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
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.PopupMenu;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentResultListener;
import androidx.loader.app.LoaderManager;
import androidx.loader.content.Loader;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.gnucash.android.R;
import org.gnucash.android.app.GnuCashApplication;
import org.gnucash.android.databinding.CardviewCompactTransactionBinding;
import org.gnucash.android.databinding.CardviewTransactionBinding;
import org.gnucash.android.databinding.FragmentTransactionsListBinding;
import org.gnucash.android.db.DatabaseCursorLoader;
import org.gnucash.android.db.DatabaseSchema;
import org.gnucash.android.db.adapter.AccountsDbAdapter;
import org.gnucash.android.db.adapter.DatabaseAdapter;
import org.gnucash.android.db.adapter.SplitsDbAdapter;
import org.gnucash.android.db.adapter.TransactionsDbAdapter;
import org.gnucash.android.model.Money;
import org.gnucash.android.model.Split;
import org.gnucash.android.model.Transaction;
import org.gnucash.android.ui.common.FormActivity;
import org.gnucash.android.ui.common.Refreshable;
import org.gnucash.android.ui.common.UxArgument;
import org.gnucash.android.ui.homescreen.WidgetConfigurationActivity;
import org.gnucash.android.ui.settings.PreferenceActivity;
import org.gnucash.android.ui.transaction.dialog.BulkMoveDialogFragment;
import org.gnucash.android.ui.util.CursorRecyclerAdapter;
import org.gnucash.android.util.BackupManager;

import java.util.List;

import timber.log.Timber;

/**
 * List Fragment for displaying list of transactions for an account
 *
 * @author Ngewi Fet <ngewif@gmail.com>
 */
public class TransactionsListFragment extends Fragment implements
    Refreshable, LoaderManager.LoaderCallbacks<Cursor>, FragmentResultListener {

    private TransactionsDbAdapter mTransactionsDbAdapter;
    private String mAccountUID;

    private boolean mUseCompactView = false;

    private TransactionRecyclerAdapter mTransactionRecyclerAdapter;

    private FragmentTransactionsListBinding mBinding;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        Bundle args = getArguments();
        mAccountUID = args.getString(UxArgument.SELECTED_ACCOUNT_UID);

        mUseCompactView = PreferenceActivity.getActiveBookSharedPreferences()
                .getBoolean(getActivity().getString(R.string.key_use_compact_list), !GnuCashApplication.isDoubleEntryEnabled());
        //if there was a local override of the global setting, respect it
        if (savedInstanceState != null)
            mUseCompactView = savedInstanceState.getBoolean(getString(R.string.key_use_compact_list), mUseCompactView);

        mTransactionsDbAdapter = TransactionsDbAdapter.getInstance();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(getString(R.string.key_use_compact_list), mUseCompactView);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        mBinding = FragmentTransactionsListBinding.inflate(inflater, container, false);
        View view = mBinding.getRoot();

        mBinding.transactionRecyclerView.setHasFixedSize(true);
        if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
            GridLayoutManager gridLayoutManager = new GridLayoutManager(getActivity(), 2);
            mBinding.transactionRecyclerView.setLayoutManager(gridLayoutManager);
        } else {
            LinearLayoutManager mLayoutManager = new LinearLayoutManager(getActivity());
            mBinding.transactionRecyclerView.setLayoutManager(mLayoutManager);
        }
        mBinding.transactionRecyclerView.setEmptyView(view.findViewById(R.id.empty_view));

        return view;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        ActionBar aBar = ((AppCompatActivity) getActivity()).getSupportActionBar();
        aBar.setDisplayShowTitleEnabled(false);
        aBar.setDisplayHomeAsUpEnabled(true);

        mTransactionRecyclerAdapter = new TransactionRecyclerAdapter(null);
        mBinding.transactionRecyclerView.setAdapter(mTransactionRecyclerAdapter);

        setHasOptionsMenu(true);
    }

    /**
     * Refresh the list with transactions from account with ID <code>accountId</code>
     *
     * @param accountUID GUID of account to load transactions from
     */
    @Override
    public void refresh(String accountUID) {
        mAccountUID = accountUID;
        refresh();
    }

    /**
     * Reload the list of transactions and recompute account balances
     */
    @Override
    public void refresh() {
        getLoaderManager().restartLoader(0, null, this);
    }

    @Override
    public void onResume() {
        super.onResume();
        ((TransactionsActivity) getActivity()).updateNavigationSelection();
        refresh();
    }

    public void onListItemClick(long id) {
        Intent intent = new Intent(getActivity(), TransactionDetailActivity.class);
        intent.putExtra(UxArgument.SELECTED_TRANSACTION_UID, mTransactionsDbAdapter.getUID(id));
        intent.putExtra(UxArgument.SELECTED_ACCOUNT_UID, mAccountUID);
        startActivity(intent);
//		mTransactionEditListener.editTransaction(mTransactionsDbAdapter.getUID(id));
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.transactions_list_actions, menu);
    }

    @Override
    public void onPrepareOptionsMenu(@NonNull Menu menu) {
        super.onPrepareOptionsMenu(menu);
        MenuItem item = menu.findItem(R.id.menu_compact_trn_view);
        item.setChecked(mUseCompactView);
        item.setEnabled(GnuCashApplication.isDoubleEntryEnabled()); //always compact for single-entry
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_compact_trn_view:
                item.setChecked(!item.isChecked());
                mUseCompactView = !mUseCompactView;
                refresh();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public Loader<Cursor> onCreateLoader(int arg0, Bundle arg1) {
        Timber.d("Creating transactions loader");
        return new TransactionsCursorLoader(getActivity(), mAccountUID);
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor cursor) {
        Timber.d("Transactions loader finished. Swapping in cursor");
        mTransactionRecyclerAdapter.swapCursor(cursor);
        mTransactionRecyclerAdapter.notifyDataSetChanged();
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
        Timber.d("Resetting transactions loader");
        mTransactionRecyclerAdapter.swapCursor(null);
    }

    @Override
    public void onFragmentResult(@NonNull String requestKey, @NonNull Bundle result) {
        if (BulkMoveDialogFragment.TAG.equals(requestKey)) {
            boolean refresh = result.getBoolean(Refreshable.EXTRA_REFRESH);
            if (refresh) refresh();
        }
    }

    /**
     * {@link DatabaseCursorLoader} for loading transactions asynchronously from the database
     *
     * @author Ngewi Fet <ngewif@gmail.com>
     */
    protected static class TransactionsCursorLoader extends DatabaseCursorLoader {
        private final String accountUID;

        public TransactionsCursorLoader(Context context, String accountUID) {
            super(context);
            this.accountUID = accountUID;
        }

        @Override
        public Cursor loadInBackground() {
            mDatabaseAdapter = TransactionsDbAdapter.getInstance();
            Cursor c = ((TransactionsDbAdapter) mDatabaseAdapter).fetchAllTransactionsForAccount(accountUID);
            if (c != null)
                registerContentObserver(c);
            return c;
        }
    }

    public class TransactionRecyclerAdapter extends CursorRecyclerAdapter<TransactionRecyclerAdapter.TransactionViewHolder> {

        public static final int ITEM_TYPE_COMPACT = 0x111;
        public static final int ITEM_TYPE_FULL = 0x100;

        public TransactionRecyclerAdapter(Cursor cursor) {
            super(cursor);
        }

        @Override
        public TransactionViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            if (viewType == ITEM_TYPE_COMPACT) {
                CardviewCompactTransactionBinding binding = CardviewCompactTransactionBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
                return new TransactionViewHolder(binding);
            } else {
                CardviewTransactionBinding binding = CardviewTransactionBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
                return new TransactionViewHolder(binding);
            }
        }

        @Override
        public int getItemViewType(int position) {
            return mUseCompactView ? ITEM_TYPE_COMPACT : ITEM_TYPE_FULL;
        }

        @Override
        public void onBindViewHolderCursor(TransactionViewHolder holder, Cursor cursor) {
            holder.bind(cursor);
        }

        public class TransactionViewHolder extends RecyclerView.ViewHolder implements PopupMenu.OnMenuItemClickListener {
            private final TextView primaryText;
            private final TextView secondaryText;
            private final TextView transactionAmount;
            private final ImageView optionsMenu;

            //these views are not used in the compact view, hence the nullability
            @Nullable
            public final TextView transactionDate;
            @Nullable
            public final ImageView editTransaction;

            private long transactionId;

            public TransactionViewHolder(CardviewCompactTransactionBinding binding) {
                super(binding.getRoot());
                primaryText = binding.listItem2Lines.primaryText;
                secondaryText = binding.listItem2Lines.secondaryText;
                transactionAmount = binding.transactionAmount;
                optionsMenu = binding.optionsMenu;
                transactionDate = null;
                editTransaction = null;
                setup();
            }

            public TransactionViewHolder(CardviewTransactionBinding binding) {
                super(binding.getRoot());
                primaryText = binding.listItem2Lines.primaryText;
                secondaryText = binding.listItem2Lines.secondaryText;
                transactionAmount = binding.transactionAmount;
                optionsMenu = binding.optionsMenu;
                transactionDate = binding.transactionDate;
                editTransaction = binding.editTransaction;
                setup();
            }

            private void setup() {
                primaryText.setTextSize(18);
                optionsMenu.setOnClickListener(v -> {
                    PopupMenu popup = new PopupMenu(getActivity(), v);
                    popup.setOnMenuItemClickListener(TransactionViewHolder.this);
                    MenuInflater inflater = popup.getMenuInflater();
                    inflater.inflate(R.menu.transactions_context_menu, popup.getMenu());
                    popup.show();
                });

                itemView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        onListItemClick(transactionId);
                    }
                });
            }

            @Override
            public boolean onMenuItemClick(@NonNull MenuItem item) {
                switch (item.getItemId()) {
                    case R.id.context_menu_delete:
                        deleteTransaction(transactionId);
                        return true;

                    case R.id.context_menu_duplicate_transaction:
                        duplicateTransaction(transactionId);
                        return true;

                    case R.id.context_menu_move_transaction:
                        moveTransaction(transactionId);
                        return true;

                    default:
                        return false;
                }
            }

            public void bind(@NonNull Cursor cursor) {
                transactionId = cursor.getLong(cursor.getColumnIndexOrThrow(DatabaseSchema.TransactionEntry._ID));

                String description = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseSchema.TransactionEntry.COLUMN_DESCRIPTION));
                primaryText.setText(description);

                final String transactionUID = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseSchema.TransactionEntry.COLUMN_UID));
                Money amount = mTransactionsDbAdapter.getBalance(transactionUID, mAccountUID);
                TransactionsActivity.displayBalance(transactionAmount, amount);

                long dateMillis = cursor.getLong(cursor.getColumnIndexOrThrow(DatabaseSchema.TransactionEntry.COLUMN_TIMESTAMP));
                String dateText = TransactionsActivity.getPrettyDateFormat(getActivity(), dateMillis);

                if (mUseCompactView) {
                    secondaryText.setText(dateText);
                } else {
                    List<Split> splits = SplitsDbAdapter.getInstance().getSplitsForTransaction(transactionUID);
                    String text = "";
                    String error = null;

                    if (splits.size() == 2) {
                        if (splits.get(0).isPairOf(splits.get(1))) {
                            for (Split split : splits) {
                                if (!split.getAccountUID().equals(mAccountUID)) {
                                    text = AccountsDbAdapter.getInstance().getFullyQualifiedAccountName(split.getAccountUID());
                                    break;
                                }
                            }
                        }
                        if (TextUtils.isEmpty(text)) {
                            text = getString(R.string.label_split_count, splits.size());
                            error = getString(R.string.imbalance_account_name);
                        }
                    }
                    if (splits.size() > 2) {
                        text = getString(R.string.label_split_count, splits.size());
                    }
                    secondaryText.setText(text);
                    secondaryText.setError(error);
                    transactionDate.setText(dateText);

                    editTransaction.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            Intent intent = new Intent(getActivity(), FormActivity.class);
                            intent.putExtra(UxArgument.FORM_TYPE, FormActivity.FormType.TRANSACTION.name());
                            intent.putExtra(UxArgument.SELECTED_TRANSACTION_UID, transactionUID);
                            intent.putExtra(UxArgument.SELECTED_ACCOUNT_UID, mAccountUID);
                            startActivity(intent);
                        }
                    });
                }
            }
        }
    }

    private void deleteTransaction(long transactionId) {
        final Activity activity = requireActivity();
        if (GnuCashApplication.shouldBackupTransactions(activity)) {
            BackupManager.backupActiveBookAsync(activity, result -> {
                mTransactionsDbAdapter.deleteRecord(transactionId);
                WidgetConfigurationActivity.updateAllWidgets(activity);
                refresh();
                return null;
            });
        } else {
            mTransactionsDbAdapter.deleteRecord(transactionId);
            WidgetConfigurationActivity.updateAllWidgets(activity);
            refresh();
        }
    }

    private void duplicateTransaction(long transactionId) {
        Transaction transaction = mTransactionsDbAdapter.getRecord(transactionId);
        Transaction duplicate = new Transaction(transaction, true);
        duplicate.setTime(System.currentTimeMillis());
        mTransactionsDbAdapter.addRecord(duplicate, DatabaseAdapter.UpdateMethod.insert);
        refresh();
    }

    private void moveTransaction(long transactionId) {
        long[] ids = new long[]{transactionId};
        FragmentManager fm = getParentFragmentManager();
        fm.setFragmentResultListener(BulkMoveDialogFragment.TAG, TransactionsListFragment.this, TransactionsListFragment.this);
        BulkMoveDialogFragment fragment = BulkMoveDialogFragment.newInstance(ids, mAccountUID);
        fragment.show(fm, BulkMoveDialogFragment.TAG);
    }
}
