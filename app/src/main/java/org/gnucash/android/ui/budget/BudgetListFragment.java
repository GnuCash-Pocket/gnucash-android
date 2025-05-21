/*
 * Copyright (c) 2015 Ngewi Fet <ngewif@gmail.com>
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

package org.gnucash.android.ui.budget;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.database.Cursor;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.PopupMenu;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.loader.app.LoaderManager;
import androidx.loader.content.Loader;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.gnucash.android.R;
import org.gnucash.android.databinding.CardviewBudgetBinding;
import org.gnucash.android.databinding.FragmentBudgetListBinding;
import org.gnucash.android.db.DatabaseCursorLoader;
import org.gnucash.android.db.DatabaseSchema;
import org.gnucash.android.db.adapter.AccountsDbAdapter;
import org.gnucash.android.db.adapter.BudgetsDbAdapter;
import org.gnucash.android.model.Budget;
import org.gnucash.android.model.BudgetAmount;
import org.gnucash.android.model.Commodity;
import org.gnucash.android.model.Money;
import org.gnucash.android.ui.common.FormActivity;
import org.gnucash.android.ui.common.Refreshable;
import org.gnucash.android.ui.common.UxArgument;
import org.gnucash.android.ui.util.CursorRecyclerAdapter;

import java.math.BigDecimal;
import java.math.RoundingMode;

import timber.log.Timber;

/**
 * Budget list fragment
 */
public class BudgetListFragment extends Fragment implements Refreshable,
    LoaderManager.LoaderCallbacks<Cursor> {

    private static final int REQUEST_EDIT_BUDGET = 0xB;
    private static final int REQUEST_OPEN_ACCOUNT = 0xC;

    private BudgetRecyclerAdapter mBudgetRecyclerAdapter;

    private BudgetsDbAdapter mBudgetsDbAdapter;

    private FragmentBudgetListBinding mBinding;

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        mBinding = FragmentBudgetListBinding.inflate(inflater, container, false);
        View view = mBinding.getRoot();

        mBinding.list.setHasFixedSize(true);
        mBinding.list.setEmptyView(mBinding.emptyView);

        if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
            GridLayoutManager gridLayoutManager = new GridLayoutManager(getActivity(), 2);
            mBinding.list.setLayoutManager(gridLayoutManager);
        } else {
            LinearLayoutManager mLayoutManager = new LinearLayoutManager(getActivity());
            mBinding.list.setLayoutManager(mLayoutManager);
        }
        return view;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        mBudgetsDbAdapter = BudgetsDbAdapter.getInstance();
        mBudgetRecyclerAdapter = new BudgetRecyclerAdapter(null);

        mBinding.list.setAdapter(mBudgetRecyclerAdapter);

        getLoaderManager().initLoader(0, null, this);
    }

    @NonNull
    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        Timber.d("Creating the accounts loader");
        return new BudgetsCursorLoader(getActivity());
    }

    @Override
    public void onLoadFinished(@NonNull Loader<Cursor> loaderCursor, Cursor cursor) {
        Timber.d("Budget loader finished. Swapping in cursor");
        mBudgetRecyclerAdapter.changeCursor(cursor);
    }

    @Override
    public void onLoaderReset(@NonNull Loader<Cursor> arg0) {
        Timber.d("Resetting the accounts loader");
        mBudgetRecyclerAdapter.changeCursor(null);
    }

    @Override
    public void onResume() {
        super.onResume();
        refresh();
        requireActivity().findViewById(R.id.fab_create_budget).setVisibility(View.VISIBLE);
        ActionBar actionbar = ((AppCompatActivity) requireActivity()).getSupportActionBar();
        actionbar.setTitle("Budgets");
    }

    @Override
    public void refresh() {
        if (isDetached() || getFragmentManager() == null) return;
        getLoaderManager().restartLoader(0, null, this);
    }

    /**
     * This method does nothing with the GUID.
     * Is equivalent to calling {@link #refresh()}
     *
     * @param uid GUID of relevant item to be refreshed
     */
    @Override
    public void refresh(String uid) {
        refresh();
    }

    /**
     * Opens the budget detail fragment
     *
     * @param budgetUID GUID of budget
     */
    public void onClickBudget(String budgetUID) {
        FragmentManager fragmentManager = getParentFragmentManager();
        fragmentManager.beginTransaction()
            .replace(R.id.fragment_container, BudgetDetailFragment.newInstance(budgetUID))
            .addToBackStack(null)
            .commit();
    }

    /**
     * Launches the FormActivity for editing the budget
     *
     * @param budgetUID Db record UID of the budget
     */
    private void editBudget(String budgetUID) {
        Intent addAccountIntent = new Intent(getActivity(), FormActivity.class);
        addAccountIntent.setAction(Intent.ACTION_INSERT_OR_EDIT);
        addAccountIntent.putExtra(UxArgument.FORM_TYPE, FormActivity.FormType.BUDGET.name());
        addAccountIntent.putExtra(UxArgument.BUDGET_UID, budgetUID);
        startActivityForResult(addAccountIntent, REQUEST_EDIT_BUDGET);
    }

    /**
     * Delete the budget from the database
     *
     * @param budgetUID Database record UID
     */
    private void deleteBudget(String budgetUID) {
        BudgetsDbAdapter.getInstance().deleteRecord(budgetUID);
        refresh();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == Activity.RESULT_OK) {
            refresh();
        }
    }

    class BudgetRecyclerAdapter extends CursorRecyclerAdapter<BudgetRecyclerAdapter.BudgetViewHolder> {

        public BudgetRecyclerAdapter(Cursor cursor) {
            super(cursor);
        }

        @Override
        public void onBindViewHolderCursor(BudgetViewHolder holder, Cursor cursor) {
            Context context = holder.itemView.getContext();
            final Budget budget = mBudgetsDbAdapter.buildModelInstance(cursor);
            holder.bind(budget);
        }

        @Override
        public BudgetViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            CardviewBudgetBinding binding = CardviewBudgetBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
            return new BudgetViewHolder(binding);
        }

        class BudgetViewHolder extends RecyclerView.ViewHolder implements PopupMenu.OnMenuItemClickListener {
            private final TextView budgetName;
            private final TextView accountName;
            private final TextView budgetAmount;
            private final ImageView optionsMenu;
            private final ProgressBar budgetIndicator;
            private final TextView budgetRecurrence;
            private String budgetUID;

            public BudgetViewHolder(CardviewBudgetBinding binding) {
                super(binding.getRoot());
                this.budgetName = binding.listItem2Lines.primaryText;
                this.accountName = binding.listItem2Lines.secondaryText;
                this.budgetAmount = binding.budgetAmount;
                this.optionsMenu = binding.optionsMenu;
                this.budgetIndicator = binding.budgetIndicator;
                this.budgetRecurrence = binding.budgetRecurrence;

                optionsMenu.setOnClickListener(v -> {
                    PopupMenu popup = new PopupMenu(getActivity(), v);
                    popup.setOnMenuItemClickListener(BudgetViewHolder.this);
                    MenuInflater inflater = popup.getMenuInflater();
                    inflater.inflate(R.menu.budget_context_menu, popup.getMenu());
                    popup.show();
                });
            }

            @Override
            public boolean onMenuItemClick(@NonNull MenuItem item) {
                switch (item.getItemId()) {
                    case R.id.menu_edit:
                        editBudget(budgetUID);
                        return true;

                    case R.id.menu_delete:
                        deleteBudget(budgetUID);
                        return true;

                    default:
                        return false;
                }
            }

            public void bind(final Budget budget) {
                Context context = itemView.getContext();
                this.budgetUID = budget.getUID();

                budgetName.setText(budget.getName());

                AccountsDbAdapter accountsDbAdapter = AccountsDbAdapter.getInstance();
                String accountString;
                int numberOfAccounts = budget.getNumberOfAccounts();
                if (numberOfAccounts == 1) {
                    accountString = accountsDbAdapter.getAccountFullName(budget.getBudgetAmounts().get(0).getAccountUID());
                } else {
                    accountString = numberOfAccounts + " budgeted accounts";
                }
                accountName.setText(accountString);

                budgetRecurrence.setText(budget.getRecurrence().getRepeatString(context) + " - "
                    + budget.getRecurrence().getDaysLeftInCurrentPeriod() + " days left");

                BigDecimal spentAmountValue = BigDecimal.ZERO;
                for (BudgetAmount budgetAmount : budget.getCompactedBudgetAmounts()) {
                    Money balance = accountsDbAdapter.getAccountBalance(budgetAmount.getAccountUID(),
                        budget.getStartOfCurrentPeriod(), budget.getEndOfCurrentPeriod());
                    spentAmountValue = spentAmountValue.add(balance.asBigDecimal());
                }

                Money budgetTotal = budget.getAmountSum();
                Commodity commodity = budgetTotal.getCommodity();
                String usedAmount = commodity.getSymbol() + spentAmountValue + " of "
                    + budgetTotal.formattedString();
                budgetAmount.setText(usedAmount);

                double budgetProgress = budgetTotal.isAmountZero() ? 0.0 : spentAmountValue.divide(budgetTotal.asBigDecimal(),
                        commodity.getSmallestFractionDigits(), RoundingMode.HALF_EVEN)
                    .doubleValue();
                budgetIndicator.setProgress((int) (budgetProgress * 100));

                budgetAmount.setTextColor(BudgetsActivity.getBudgetProgressColor(1 - budgetProgress));

                itemView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        onClickBudget(budget.getUID());
                    }
                });
            }
        }
    }

    /**
     * Loads Budgets asynchronously from the database
     */
    private static class BudgetsCursorLoader extends DatabaseCursorLoader<BudgetsDbAdapter> {

        /**
         * Constructor
         * Initializes the content observer
         *
         * @param context Application context
         */
        public BudgetsCursorLoader(Context context) {
            super(context);
        }

        @Override
        public Cursor loadInBackground() {
            databaseAdapter = BudgetsDbAdapter.getInstance();
            if (databaseAdapter == null) return null;
            return databaseAdapter.fetchAllRecords(null, null, DatabaseSchema.BudgetEntry.COLUMN_NAME + " ASC");
        }
    }
}
