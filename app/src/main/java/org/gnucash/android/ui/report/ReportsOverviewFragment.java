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
package org.gnucash.android.ui.report;

import static org.gnucash.android.ui.util.TextViewExtKt.displayBalance;

import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;

import org.gnucash.android.R;
import org.gnucash.android.databinding.FragmentReportSummaryBinding;
import org.gnucash.android.db.DatabaseSchema.AccountEntry;
import org.gnucash.android.model.Account;
import org.gnucash.android.model.AccountType;
import org.gnucash.android.model.Commodity;
import org.gnucash.android.model.Money;
import org.gnucash.android.model.Price;
import org.gnucash.android.ui.report.piechart.PieChartFragment;
import org.gnucash.android.util.DateExtKt;
import org.joda.time.LocalDateTime;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Shows a summary of reports
 *
 * @author Ngewi Fet <ngewif@gmail.com>
 */
public class ReportsOverviewFragment extends BaseReportFragment {

    private Money mAssetsBalance;
    private Money mLiabilitiesBalance;

    private boolean mChartHasData = false;

    private FragmentReportSummaryBinding mBinding;
    @ColorInt
    private int colorBalanceZero;

    @Override
    public View inflateView(LayoutInflater inflater, ViewGroup container) {
        mBinding = FragmentReportSummaryBinding.inflate(inflater, container, false);
        mBinding.btnBarChart.setOnClickListener(this::onClickChartTypeButton);
        mBinding.btnPieChart.setOnClickListener(this::onClickChartTypeButton);
        mBinding.btnLineChart.setOnClickListener(this::onClickChartTypeButton);
        mBinding.btnBalanceSheet.setOnClickListener(this::onClickChartTypeButton);
        colorBalanceZero = mBinding.totalAssets.getCurrentTextColor();
        return mBinding.getRoot();
    }

    @Override
    public ReportType getReportType() {
        return ReportType.NONE;
    }

    @Override
    public boolean requiresAccountTypeOptions() {
        return false;
    }

    @Override
    public boolean requiresTimeRangeOptions() {
        return false;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        final Context context = view.getContext();

        setHasOptionsMenu(false);

        @ColorInt int textColorPrimary = getTextColor(context);
        mBinding.pieChart.setCenterTextSize(PieChartFragment.CENTER_TEXT_SIZE);
        mBinding.pieChart.setDrawSliceText(false);
        mBinding.pieChart.setCenterTextColor(textColorPrimary);
        mBinding.pieChart.setHoleColor(Color.TRANSPARENT);
        Legend legend = mBinding.pieChart.getLegend();
        legend.setWordWrapEnabled(true);
        legend.setTextColor(textColorPrimary);
    }

    @Override
    public void onPrepareOptionsMenu(@NonNull Menu menu) {
        super.onPrepareOptionsMenu(menu);
        menu.findItem(R.id.menu_group_reports_by).setVisible(false);
    }

    @Override
    protected void generateReport(@NonNull Context context) {
        PieData pieData = PieChartFragment.groupSmallerSlices(context, getData());
        if (pieData.getDataSetCount() > 0 && pieData.getDataSet().getEntryCount() > 0) {
            mBinding.pieChart.setData(pieData);
            float sum = mBinding.pieChart.getData().getYValueSum();
            mBinding.pieChart.setCenterText(formatTotalValue(sum));
            mChartHasData = true;
        } else {
            mBinding.pieChart.setData(getEmptyData(context));
            mBinding.pieChart.setCenterText(context.getString(R.string.label_chart_no_data));
            mBinding.pieChart.getLegend().setEnabled(false);
            mChartHasData = false;
        }

        List<AccountType> accountTypes = new ArrayList<>();
        accountTypes.add(AccountType.ASSET);
        accountTypes.add(AccountType.CASH);
        accountTypes.add(AccountType.BANK);
        mAssetsBalance = mAccountsDbAdapter.getCurrentAccountsBalance(accountTypes, mCommodity);

        accountTypes.clear();
        accountTypes.add(AccountType.LIABILITY);
        accountTypes.add(AccountType.CREDIT);
        mLiabilitiesBalance = mAccountsDbAdapter.getCurrentAccountsBalance(accountTypes, mCommodity);
    }

    /**
     * Returns {@code PieData} instance with data entries, colors and labels
     *
     * @return {@code PieData} instance
     */
    private PieData getData() {
        PieDataSet dataSet = new PieDataSet(null, "");
        List<Integer> colors = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        long startTime = DateExtKt.toMillis(now.minusMonths(3));
        long endTime = DateExtKt.toMillis(now);
        final Commodity commodity = mCommodity;

        String where = AccountEntry.COLUMN_TYPE + "=?"
            + " AND " + AccountEntry.COLUMN_PLACEHOLDER + " = 0"
            + " AND " + AccountEntry.COLUMN_TEMPLATE + " = 0";
        String[] whereArgs = new String[]{mAccountType.name()};
        String orderBy = AccountEntry.COLUMN_FULL_NAME + " ASC";
        List<Account> accounts = mAccountsDbAdapter.getSimpleAccounts(where, whereArgs, orderBy);
        Map<String, Money> balances = mAccountsDbAdapter.getAccountsBalances(accounts, startTime, endTime);

        for (Account account : accounts) {
            Money balance = balances.get(account.getUID());
            if ((balance == null) || balance.isAmountZero()) continue;
            Price price = pricesDbAdapter.getPrice(balance.getCommodity(), commodity);
            if (price == null) continue;
            balance = balance.times(price);
            float value = balance.toFloat();
            if (value > 0f) {
                int count = dataSet.getEntryCount();
                dataSet.addEntry(new PieEntry(value, account.getName()));
                @ColorInt int color = getAccountColor(account, count);
                colors.add(color);
            }
        }
        dataSet.setColors(colors);
        dataSet.setSliceSpace(PieChartFragment.SPACE_BETWEEN_SLICES);
        return new PieData(dataSet);
    }

    @Override
    protected void displayReport() {
        if (mChartHasData) {
            mBinding.pieChart.animateXY(1500, 1500);
            mBinding.pieChart.setTouchEnabled(true);
        } else {
            mBinding.pieChart.setTouchEnabled(false);
        }
        mBinding.pieChart.highlightValues(null);
        mBinding.pieChart.invalidate();

        Money totalAssets = mAssetsBalance;
        Money totalLiabilities = (mLiabilitiesBalance != null) ? mLiabilitiesBalance.unaryMinus() : null;
        Money netWorth = (totalAssets != null) ? totalAssets.plus(totalLiabilities) : null;
        displayBalance(mBinding.totalAssets, totalAssets, colorBalanceZero);
        displayBalance(mBinding.totalLiabilities, totalLiabilities, colorBalanceZero);
        displayBalance(mBinding.netWorth, netWorth, colorBalanceZero);
    }

    /**
     * Returns a data object that represents situation when no user data available
     *
     * @return a {@code PieData} instance for situation when no user data available
     */
    private PieData getEmptyData(@NonNull Context context) {
        PieDataSet dataSet = new PieDataSet(null, context.getString(R.string.label_chart_no_data));
        dataSet.addEntry(new PieEntry(1, 0));
        dataSet.setColor(PieChartFragment.NO_DATA_COLOR);
        dataSet.setDrawValues(false);
        return new PieData(dataSet);
    }

    public void onClickChartTypeButton(View view) {
        ReportType reportType;
        switch (view.getId()) {
            case R.id.btn_pie_chart:
                reportType = ReportType.PIE_CHART;
                break;
            case R.id.btn_bar_chart:
                reportType = ReportType.BAR_CHART;
                break;
            case R.id.btn_line_chart:
                reportType = ReportType.LINE_CHART;
                break;
            case R.id.btn_balance_sheet:
                reportType = ReportType.SHEET;
                break;
            default:
                reportType = ReportType.NONE;
                break;
        }

        mReportsActivity.showReport(reportType);
    }
}
