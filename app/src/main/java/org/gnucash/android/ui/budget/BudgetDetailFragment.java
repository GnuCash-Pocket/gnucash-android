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
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.components.LimitLine;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;

import org.gnucash.android.R;
import org.gnucash.android.databinding.CardviewBudgetAmountBinding;
import org.gnucash.android.databinding.FragmentBudgetDetailBinding;
import org.gnucash.android.db.DatabaseSchema;
import org.gnucash.android.db.adapter.AccountsDbAdapter;
import org.gnucash.android.db.adapter.BudgetsDbAdapter;
import org.gnucash.android.model.Budget;
import org.gnucash.android.model.BudgetAmount;
import org.gnucash.android.model.Money;
import org.gnucash.android.ui.common.FormActivity;
import org.gnucash.android.ui.common.Refreshable;
import org.gnucash.android.ui.common.UxArgument;
import org.gnucash.android.ui.transaction.TransactionsActivity;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;


/**
 * Fragment for displaying budget details
 */
public class BudgetDetailFragment extends Fragment implements Refreshable {
    private String mBudgetUID;
    private BudgetsDbAdapter mBudgetsDbAdapter;

    private FragmentBudgetDetailBinding mBinding;

    public static BudgetDetailFragment newInstance(String budgetUID) {
        BudgetDetailFragment fragment = new BudgetDetailFragment();
        Bundle args = new Bundle();
        args.putString(UxArgument.BUDGET_UID, budgetUID);
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        mBinding = FragmentBudgetDetailBinding.inflate(inflater, container, false);
        View view = mBinding.getRoot();
        mBinding.listItem2Lines.secondaryText.setMaxLines(3);

        mBinding.budgetAmountRecycler.setHasFixedSize(true);

        if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
            GridLayoutManager gridLayoutManager = new GridLayoutManager(getActivity(), 2);
            mBinding.budgetAmountRecycler.setLayoutManager(gridLayoutManager);
        } else {
            LinearLayoutManager mLayoutManager = new LinearLayoutManager(getActivity());
            mBinding.budgetAmountRecycler.setLayoutManager(mLayoutManager);
        }
        return view;
    }


    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        mBudgetsDbAdapter = BudgetsDbAdapter.getInstance();
        mBudgetUID = getArguments().getString(UxArgument.BUDGET_UID);
        bindViews();

        setHasOptionsMenu(true);
    }

    private void bindViews() {
        Context context = mBinding.budgetRecurrence.getContext();
        Budget budget = mBudgetsDbAdapter.getRecord(mBudgetUID);
        mBinding.listItem2Lines.primaryText.setText(budget.getName());

        String description = budget.getDescription();
        if (description != null && !description.isEmpty())
            mBinding.listItem2Lines.secondaryText.setText(description);
        else {
            mBinding.listItem2Lines.secondaryText.setVisibility(View.GONE);
        }
        mBinding.budgetRecurrence.setText(budget.getRecurrence().getRepeatString(context));

        mBinding.budgetAmountRecycler.setAdapter(new BudgetAmountAdapter());
    }

    @Override
    public void onResume() {
        super.onResume();
        refresh();

        View view = getActivity().findViewById(R.id.fab_create_budget);
        if (view != null) {
            view.setVisibility(View.GONE);
        }
    }

    @Override
    public void refresh() {
        bindViews();
        String budgetName = mBudgetsDbAdapter.getAttribute(mBudgetUID, DatabaseSchema.BudgetEntry.COLUMN_NAME);
        ActionBar actionBar = ((AppCompatActivity) getActivity()).getSupportActionBar();
        assert actionBar != null;
        actionBar.setTitle("Budget: " + budgetName);
    }

    @Override
    public void refresh(String budgetUID) {
        mBudgetUID = budgetUID;
        refresh();
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.budget_actions, menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_edit:
                Intent addAccountIntent = new Intent(getActivity(), FormActivity.class);
                addAccountIntent.setAction(Intent.ACTION_INSERT_OR_EDIT);
                addAccountIntent.putExtra(UxArgument.FORM_TYPE, FormActivity.FormType.BUDGET.name());
                addAccountIntent.putExtra(UxArgument.BUDGET_UID, mBudgetUID);
                startActivityForResult(addAccountIntent, 0x11);
                return true;

            default:
                return false;
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == Activity.RESULT_OK) {
            refresh();
        }
    }


    public class BudgetAmountAdapter extends RecyclerView.Adapter<BudgetAmountAdapter.BudgetAmountViewHolder> {
        private List<BudgetAmount> mBudgetAmounts;
        private Budget mBudget;

        public BudgetAmountAdapter() {
            mBudget = mBudgetsDbAdapter.getRecord(mBudgetUID);
            mBudgetAmounts = mBudget.getCompactedBudgetAmounts();
        }

        @Override
        public BudgetAmountViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            CardviewBudgetAmountBinding binding = CardviewBudgetAmountBinding.inflate(LayoutInflater.from(getActivity()), parent, false);
            return new BudgetAmountViewHolder(binding);
        }

        @Override
        public void onBindViewHolder(BudgetAmountViewHolder holder, final int position) {
            BudgetAmount budgetAmount = mBudgetAmounts.get(position);
            holder.bind(mBudget, budgetAmount);
        }

        /**
         * Generate the chart data for the chart
         *
         * @param barChart     View where to display the chart
         * @param budgetAmount BudgetAmount to visualize
         */
        public void generateChartData(BarChart barChart, BudgetAmount budgetAmount) {
            // FIXME: 25.10.15 chart is broken

            AccountsDbAdapter accountsDbAdapter = AccountsDbAdapter.getInstance();

            List<BarEntry> barEntries = new ArrayList<>();
            List<String> xVals = new ArrayList<>();

            //todo: refactor getNumberOfPeriods into budget
            int budgetPeriods = (int) mBudget.getNumberOfPeriods();
            budgetPeriods = budgetPeriods == 0 ? 12 : budgetPeriods;
            int periods = mBudget.getRecurrence().getNumberOfPeriods(budgetPeriods); //// FIXME: 15.08.2016 why do we need number of periods

            for (int periodNum = 1; periodNum <= periods; periodNum++) {
                BigDecimal amount = accountsDbAdapter.getAccountBalance(budgetAmount.getAccountUID(),
                                mBudget.getStartOfPeriod(periodNum), mBudget.getEndOfPeriod(periodNum))
                        .asBigDecimal();

                if (amount.equals(BigDecimal.ZERO))
                    continue;

                barEntries.add(new BarEntry(amount.floatValue(), periodNum));
                xVals.add(mBudget.getRecurrence().getTextOfCurrentPeriod(periodNum));
            }

            String label = accountsDbAdapter.getAccountName(budgetAmount.getAccountUID());
            BarDataSet barDataSet = new BarDataSet(barEntries, label);

            BarData barData = new BarData(xVals, barDataSet);
            LimitLine limitLine = new LimitLine(budgetAmount.getAmount().asBigDecimal().floatValue());
            limitLine.setLineWidth(2f);
            limitLine.setLineColor(Color.RED);


            barChart.setData(barData);
            barChart.getAxisLeft().addLimitLine(limitLine);
            BigDecimal maxValue = budgetAmount.getAmount().plus(budgetAmount.getAmount().times(0.2)).asBigDecimal();
            barChart.getAxisLeft().setAxisMaxValue(maxValue.floatValue());
            barChart.animateX(1000);
            barChart.setAutoScaleMinMaxEnabled(true);
            barChart.setDrawValueAboveBar(true);
            barChart.invalidate();
        }

        @Override
        public int getItemCount() {
            return mBudgetAmounts.size();
        }

        class BudgetAmountViewHolder extends RecyclerView.ViewHolder {
            private final TextView budgetAccount;
            private final TextView budgetAmount;
            private final TextView budgetSpent;
            private final TextView budgetLeft;
            private final ProgressBar budgetIndicator;
            private final BarChart budgetChart;

            public BudgetAmountViewHolder(CardviewBudgetAmountBinding binding) {
                super(binding.getRoot());
                this.budgetAccount = binding.budgetAccount;
                this.budgetAmount = binding.budgetAmount;
                this.budgetSpent = binding.budgetSpent;
                this.budgetLeft = binding.budgetLeft;
                this.budgetIndicator = binding.budgetIndicator;
                this.budgetChart = binding.budgetChart;
            }

            public void bind(Budget budget, final BudgetAmount budgetAmount) {
                Money projectedAmount = budgetAmount.getAmount();
                AccountsDbAdapter accountsDbAdapter = AccountsDbAdapter.getInstance();
                Money spentAmount = accountsDbAdapter.getAccountBalance(budgetAmount.getAccountUID(),
                    budget.getStartOfCurrentPeriod(), budget.getEndOfCurrentPeriod());

                budgetAccount.setText(accountsDbAdapter.getAccountFullName(budgetAmount.getAccountUID()));
                this.budgetAmount.setText(projectedAmount.formattedString());

                budgetSpent.setText(spentAmount.abs().formattedString());
                budgetLeft.setText(projectedAmount.minus(spentAmount.abs()).formattedString());

                double budgetProgress = 0;
                if (projectedAmount.toDouble() != 0) {
                    budgetProgress = spentAmount.asBigDecimal().divide(projectedAmount.asBigDecimal(),
                        spentAmount.getCommodity().getSmallestFractionDigits(),
                        RoundingMode.HALF_EVEN).doubleValue();
                }

                budgetIndicator.setProgress((int) (budgetProgress * 100));
                budgetSpent.setTextColor(BudgetsActivity.getBudgetProgressColor(1 - budgetProgress));
                budgetLeft.setTextColor(BudgetsActivity.getBudgetProgressColor(1 - budgetProgress));

                generateChartData(budgetChart, budgetAmount);

                itemView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        String accountUID = budgetAmount.getAccountUID();
                        Intent intent = new Intent(getActivity(), TransactionsActivity.class);
                        intent.putExtra(UxArgument.SELECTED_ACCOUNT_UID, accountUID);
                        startActivityForResult(intent, 0x10);
                    }
                });
            }
        }
    }
}
