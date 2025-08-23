/*
 * Copyright (c) 2015 Oleksandr Tyshkovets <olexandr.tyshkovets@gmail.com>
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

package org.gnucash.android.ui.report.barchart;

import android.content.Context;
import android.os.Bundle;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.formatter.LargeValueFormatter;
import com.github.mikephil.charting.highlight.Highlight;
import com.github.mikephil.charting.interfaces.datasets.IBarDataSet;

import org.gnucash.android.R;
import org.gnucash.android.databinding.FragmentBarChartBinding;
import org.gnucash.android.db.DatabaseSchema.AccountEntry;
import org.gnucash.android.model.Account;
import org.gnucash.android.model.AccountType;
import org.gnucash.android.model.Money;
import org.gnucash.android.model.Price;
import org.gnucash.android.ui.report.IntervalReportFragment;
import org.gnucash.android.ui.report.ReportType;
import org.gnucash.android.ui.report.ReportsActivity;
import org.gnucash.android.util.DateExtKt;
import org.joda.time.LocalDateTime;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import timber.log.Timber;

/**
 * Activity used for drawing a bar chart
 *
 * @author Oleksandr Tyshkovets <olexandr.tyshkovets@gmail.com>
 * @author Ngewi Fet <ngewif@gmail.com>
 */
public class StackedBarChartFragment extends IntervalReportFragment {

    private static final int ANIMATION_DURATION = (int) DateUtils.SECOND_IN_MILLIS;
    private static final int NO_DATA_BAR_COUNTS = 3;

    private boolean mTotalPercentageMode = true;

    private FragmentBarChartBinding mBinding;

    @Override
    public View inflateView(LayoutInflater inflater, ViewGroup container) {
        mBinding = FragmentBarChartBinding.inflate(inflater, container, false);
        return mBinding.getRoot();
    }

    @Override
    public ReportType getReportType() {
        return ReportType.BAR_CHART;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        final Context context = view.getContext();

        @ColorInt int textColorPrimary = getTextColor(context);

        mBinding.barChart.setOnChartValueSelectedListener(this);
        mBinding.barChart.getXAxis().setDrawGridLines(false);
        mBinding.barChart.getXAxis().setTextColor(textColorPrimary);
        mBinding.barChart.getAxisRight().setEnabled(false);
        mBinding.barChart.getAxisLeft().setStartAtZero(false);
        mBinding.barChart.getAxisLeft().enableGridDashedLine(4.0f, 4.0f, 0);
        mBinding.barChart.getAxisLeft().setValueFormatter(new LargeValueFormatter(mCommodity.getSymbol()));
        mBinding.barChart.getAxisLeft().setTextColor(textColorPrimary);
        Legend legend = mBinding.barChart.getLegend();
        legend.setTextColor(textColorPrimary);
        legend.setWordWrapEnabled(true);
    }


    /**
     * Returns a data object that represents a user data of the specified account types
     *
     * @return a {@code BarData} instance that represents a user data
     */
    protected BarData getData(@NonNull Context context) {
        List<BarEntry> entries = new ArrayList<>();
        List<String> stackLabels = new ArrayList<>();
        List<Integer> colors = new ArrayList<>();
        Map<String, Integer> accountToColorMap = new LinkedHashMap<>();
        ReportsActivity.GroupInterval groupInterval = mGroupInterval;
        AccountType accountType = mAccountType;

        calculateEarliestAndLatestTimestamps(accountTypes);
        LocalDateTime startDate = mReportPeriodStart;
        if (startDate == null) {
            Long startTime = earliestTimestamps.get(accountType);
            if (startTime != null) {
                startDate = new LocalDateTime(startTime);
            } else {
                isChartDataPresent = false;
                return getEmptyData(context);
            }
        }
        LocalDateTime endDate = mReportPeriodEnd;
        if (endDate == null) {
            Long endTime = latestTimestamps.get(accountType);
            if (endTime != null) {
                endDate = new LocalDateTime(endTime);
            } else {
                endDate = LocalDateTime.now();
            }
        }

        LocalDateTime startPeriod = startDate;
        LocalDateTime endPeriod = endDate;
        switch (groupInterval) {
            case MONTH:
                endPeriod = startPeriod.plusMonths(1);
                break;
            case QUARTER:
                startPeriod = startPeriod.withMonthOfYear(DateExtKt.getFirstQuarterMonth(startPeriod)).dayOfMonth().withMinimumValue();
                endPeriod = startPeriod.plusMonths(3);
                break;
            case YEAR:
                endPeriod = startPeriod.plusYears(1);
                break;
        }
        int count = getDateDiff(groupInterval, startDate, endDate);

        final String where = AccountEntry.COLUMN_TYPE + "=?"
            + " AND " + AccountEntry.COLUMN_PLACEHOLDER + " = 0"
            + " AND " + AccountEntry.COLUMN_TEMPLATE + " = 0";
        final String[] whereArgs = new String[]{accountType.name()};
        final String orderBy = AccountEntry.COLUMN_FULL_NAME + " ASC";
        List<Account> accounts = mAccountsDbAdapter.getSimpleAccounts(where, whereArgs, orderBy);

        for (int i = 0; i < count; i++) {
            long startTime = DateExtKt.toMillis(startPeriod);
            long endTime = DateExtKt.toMillis(endPeriod);
            List<Float> stack = new ArrayList<>();
            List<String> labels = new ArrayList<>();
            Map<String, Money> balances = mAccountsDbAdapter.getAccountsBalances(accounts, startTime, endTime);

            for (Account account : accounts) {
                Money balance = balances.get(account.getUID());
                if ((balance == null) || balance.isAmountZero()) continue;
                Timber.d("%s %s [%s] %s - %s %s", accountType, groupInterval, account, startPeriod, endPeriod, balance);
                Price price = pricesDbAdapter.getPrice(balance.getCommodity(), mCommodity);
                if (price == null) continue;
                balance = balance.times(price);
                float value = balance.toFloat();
                if (value > 0f) {
                    stack.add(value);

                    String accountName = account.getName();
                    labels.add(accountName);

                    String accountUID = account.getUID();
                    @ColorInt final int color;
                    if (accountToColorMap.containsKey(accountUID)) {
                        color = accountToColorMap.get(accountUID);
                    } else {
                        color = getAccountColor(account, colors.size());
                        accountToColorMap.put(accountUID, color);
                    }
                    colors.add(color);
                }
            }

            startPeriod = endPeriod;
            switch (groupInterval) {
                case MONTH:
                    endPeriod = endPeriod.plusMonths(1);
                    break;
                case QUARTER:
                    endPeriod = endPeriod.plusMonths(3);
                    break;
                case YEAR:
                    endPeriod = endPeriod.plusYears(1);
                    break;
            }

            if (stack.isEmpty()) {
                stack.add(0f);
            }
            if (labels.isEmpty()) {
                labels.add("");
            }
            entries.add(new BarEntry(i, toFloatArray(stack), labels));
            stackLabels.addAll(labels);
        }

        BarDataSet dataSet = new BarDataSet(entries, getLabel(context, accountType));
        dataSet.setDrawValues(false);
        dataSet.setStackLabels(stackLabels.toArray(new String[0]));
        dataSet.setColors(colors);

        if ((dataSet.getEntryCount() == 0) || (getYValueSum(dataSet) == 0)) {
            isChartDataPresent = false;
            return getEmptyData(context);
        }
        isChartDataPresent = true;
        return new BarData(dataSet);
    }

    /**
     * Returns a data object that represents situation when no user data available
     *
     * @return a {@code BarData} instance for situation when no user data available
     */
    private BarData getEmptyData(@NonNull Context context) {
        List<BarEntry> yValues = new ArrayList<>();
        for (int i = 0; i < NO_DATA_BAR_COUNTS; i++) {
            yValues.add(new BarEntry(i, i + 1));
        }
        BarDataSet dataSet = new BarDataSet(yValues, context.getString(R.string.label_chart_no_data));
        dataSet.setDrawValues(false);
        dataSet.setColor(NO_DATA_COLOR);

        return new BarData(dataSet);
    }

    /**
     * Converts the specified list of floats to an array
     *
     * @param list a list of floats
     * @return a float array
     */
    private float[] toFloatArray(List<Float> list) {
        final int size = list.size();
        float[] array = new float[size];
        for (int i = 0; i < size; i++) {
            array[i] = list.get(i);
        }
        return array;
    }

    @Override
    public void generateReport(@NonNull Context context) {
        mBinding.barChart.setData(getData(context));
        mBinding.barChart.getAxisLeft().setDrawLabels(isChartDataPresent);
        mBinding.barChart.getXAxis().setDrawLabels(isChartDataPresent);
        mBinding.barChart.setTouchEnabled(isChartDataPresent);
    }

    @Override
    protected void displayReport() {
        mBinding.barChart.notifyDataSetChanged();
        mBinding.barChart.highlightValues(null);
        if (isChartDataPresent) {
            mBinding.barChart.animateY(ANIMATION_DURATION);
        } else {
            mBinding.barChart.clearAnimation();
            mSelectedValueTextView.setText(R.string.label_chart_no_data);
        }

        mBinding.barChart.invalidate();
    }

    @Override
    public void onPrepareOptionsMenu(@NonNull Menu menu) {
        super.onPrepareOptionsMenu(menu);
        menu.findItem(R.id.menu_percentage_mode).setVisible(isChartDataPresent);
        // hide pie/line chart specific menu items
        menu.findItem(R.id.menu_order_by_size).setVisible(false);
        menu.findItem(R.id.menu_toggle_labels).setVisible(false);
        menu.findItem(R.id.menu_toggle_average_lines).setVisible(false);
        menu.findItem(R.id.menu_group_other_slice).setVisible(false);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.isCheckable()) {
            item.setChecked(!item.isChecked());
        }
        final Context context = mBinding.barChart.getContext();
        switch (item.getItemId()) {
            case R.id.menu_toggle_legend:
                Legend legend = mBinding.barChart.getLegend();
                if (!legend.isLegendCustom()) {
                    Toast.makeText(context, R.string.toast_legend_too_long, Toast.LENGTH_LONG).show();
                    item.setChecked(false);
                } else {
                    item.setChecked(!mBinding.barChart.getLegend().isEnabled());
                    legend.setEnabled(!mBinding.barChart.getLegend().isEnabled());
                    mBinding.barChart.invalidate();
                }
                return true;

            case R.id.menu_percentage_mode:
                mTotalPercentageMode = !mTotalPercentageMode;
                @StringRes int msgId = mTotalPercentageMode ? R.string.toast_chart_percentage_mode_total
                    : R.string.toast_chart_percentage_mode_current_bar;
                Toast.makeText(context, msgId, Toast.LENGTH_LONG).show();
                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onValueSelected(Entry e, Highlight h) {
        if (e == null) return;
        BarEntry entry = (BarEntry) e;
        int index = h.getStackIndex();
        if ((index < 0) && (entry.getYVals().length > 0)) {
            index = 0;
        }
        float value = entry.getYVals()[index];
        List<String> labels = (List<String>) entry.getData();
        if (labels.isEmpty()) return;
        String label = labels.get(index);

        final float total;
        if (mTotalPercentageMode) {
            BarData data = mBinding.barChart.getData();
            int dataSetIndex = h.getDataSetIndex();
            IBarDataSet dataSet = data.getDataSetByIndex(dataSetIndex);
            total = getYValueSum(dataSet);
        } else {
            total = entry.getNegativeSum() + entry.getPositiveSum();
        }
        final float percentage = (total != 0f) ? ((value * 100) / total) : 0f;
        mSelectedValueTextView.setText(formatSelectedValue(label, value, percentage));
    }
}
