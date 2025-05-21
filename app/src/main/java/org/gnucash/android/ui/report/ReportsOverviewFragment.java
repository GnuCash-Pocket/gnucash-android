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
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.util.StateSet;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;

import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;

import org.gnucash.android.R;
import org.gnucash.android.databinding.FragmentReportSummaryBinding;
import org.gnucash.android.db.DatabaseSchema;
import org.gnucash.android.model.Account;
import org.gnucash.android.model.AccountType;
import org.gnucash.android.model.Money;
import org.gnucash.android.ui.report.piechart.PieChartFragment;
import org.joda.time.LocalDateTime;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
        mBinding.pieChart.setDescription("");
        mBinding.pieChart.setDrawSliceText(false);
        mBinding.pieChart.setCenterTextColor(textColorPrimary);
        mBinding.pieChart.setHoleColor(Color.TRANSPARENT);
        Legend legend = mBinding.pieChart.getLegend();
        legend.setWordWrapEnabled(true);
        legend.setTextColor(textColorPrimary);

        ColorStateList csl = new ColorStateList(new int[][]{StateSet.WILD_CARD}, new int[]{ContextCompat.getColor(context, R.color.account_green)});
        setButtonTint(mBinding.btnPieChart, csl);
        csl = new ColorStateList(new int[][]{StateSet.WILD_CARD}, new int[]{ContextCompat.getColor(context, R.color.account_red)});
        setButtonTint(mBinding.btnBarChart, csl);
        csl = new ColorStateList(new int[][]{StateSet.WILD_CARD}, new int[]{ContextCompat.getColor(context, R.color.account_blue)});
        setButtonTint(mBinding.btnLineChart, csl);
        csl = new ColorStateList(new int[][]{StateSet.WILD_CARD}, new int[]{ContextCompat.getColor(context, R.color.account_purple)});
        setButtonTint(mBinding.btnBalanceSheet, csl);
    }

    @Override
    public void onPrepareOptionsMenu(@NonNull Menu menu) {
        super.onPrepareOptionsMenu(menu);
        menu.findItem(R.id.menu_group_reports_by).setVisible(false);
    }

    @Override
    protected void generateReport(@NonNull Context context) {
        PieData pieData = PieChartFragment.groupSmallerSlices(context, getData());
        if (pieData.getYValCount() != 0) {
            mBinding.pieChart.setData(pieData);
            float sum = mBinding.pieChart.getData().getYValueSum();
            String total = context.getString(R.string.label_chart_total);
            String currencySymbol = mCommodity.getSymbol();
            mBinding.pieChart.setCenterText(String.format(PieChartFragment.TOTAL_VALUE_LABEL_PATTERN, total, sum, currencySymbol));
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
        mAssetsBalance = mAccountsDbAdapter.getCurrentAccountsBalance(accountTypes);

        accountTypes.clear();
        accountTypes.add(AccountType.LIABILITY);
        accountTypes.add(AccountType.CREDIT);
        mLiabilitiesBalance = mAccountsDbAdapter.getCurrentAccountsBalance(accountTypes);
    }

    /**
     * Returns {@code PieData} instance with data entries, colors and labels
     *
     * @return {@code PieData} instance
     */
    private PieData getData() {
        PieDataSet dataSet = new PieDataSet(null, "");
        List<String> labels = new ArrayList<>();
        List<Integer> colors = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        long start = now.minusMonths(2).dayOfMonth().withMinimumValue().toDateTime().getMillis();
        long end = now.toDateTime().getMillis();

        List<Account> accounts = mAccountsDbAdapter.getSimpleAccountList(
            DatabaseSchema.AccountEntry.COLUMN_PLACEHOLDER + "=0 AND " + DatabaseSchema.AccountEntry.COLUMN_COMMODITY_UID + "=? AND " + DatabaseSchema.AccountEntry.COLUMN_TYPE + "=?",
            new String[]{mCommodity.getUID(), mAccountType.name()},
            DatabaseSchema.AccountEntry.COLUMN_FULL_NAME + " ASC"
        );
        for (Account account : accounts) {
            float balance = mAccountsDbAdapter.getAccountBalance(account.getUID(), start, end, false).toFloat();
            if (balance > 0f) {
                dataSet.addEntry(new Entry(balance, dataSet.getEntryCount()));
                colors.add(account.getColor() != Account.DEFAULT_COLOR
                    ? account.getColor()
                    : COLORS[(dataSet.getEntryCount() - 1) % COLORS.length]);
                labels.add(account.getName());
            }
        }
        dataSet.setColors(colors);
        dataSet.setSliceSpace(PieChartFragment.SPACE_BETWEEN_SLICES);
        return new PieData(labels, dataSet);
    }

    @Override
    protected void displayReport() {
        if (mChartHasData) {
            mBinding.pieChart.animateXY(1800, 1800);
            mBinding.pieChart.setTouchEnabled(true);
        } else {
            mBinding.pieChart.setTouchEnabled(false);
        }
        mBinding.pieChart.highlightValues(null);
        mBinding.pieChart.invalidate();

        displayBalance(mBinding.totalAssets, mAssetsBalance, colorBalanceZero);
        displayBalance(mBinding.totalLiabilities, mLiabilitiesBalance, colorBalanceZero);
        displayBalance(mBinding.netWorth, mAssetsBalance.minus(mLiabilitiesBalance), colorBalanceZero);
    }

    /**
     * Returns a data object that represents situation when no user data available
     *
     * @return a {@code PieData} instance for situation when no user data available
     */
    private PieData getEmptyData(@NonNull Context context) {
        PieDataSet dataSet = new PieDataSet(null, context.getString(R.string.label_chart_no_data));
        dataSet.addEntry(new Entry(1, 0));
        dataSet.setColor(PieChartFragment.NO_DATA_COLOR);
        dataSet.setDrawValues(false);
        return new PieData(Collections.singletonList(""), dataSet);
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

    public void setButtonTint(Button button, ColorStateList tint) {
        ViewCompat.setBackgroundTintList(button, tint);
        button.setTextColor(Color.WHITE);
    }

}
