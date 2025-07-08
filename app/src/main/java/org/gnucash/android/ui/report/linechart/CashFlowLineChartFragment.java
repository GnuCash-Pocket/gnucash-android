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

package org.gnucash.android.ui.report.linechart;

import static org.gnucash.android.util.ColorExtKt.parseColor;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.LimitLine;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.LargeValueFormatter;
import com.github.mikephil.charting.highlight.Highlight;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;

import org.gnucash.android.R;
import org.gnucash.android.databinding.FragmentLineChartBinding;
import org.gnucash.android.db.DatabaseSchema.AccountEntry;
import org.gnucash.android.model.Account;
import org.gnucash.android.model.AccountType;
import org.gnucash.android.model.Commodity;
import org.gnucash.android.model.Money;
import org.gnucash.android.model.Price;
import org.gnucash.android.ui.report.IntervalReportFragment;
import org.gnucash.android.ui.report.ReportType;
import org.gnucash.android.ui.report.ReportsActivity;
import org.gnucash.android.util.DateExtKt;
import org.joda.time.LocalDateTime;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import timber.log.Timber;

/**
 * Fragment for line chart reports
 *
 * @author Oleksandr Tyshkovets <olexandr.tyshkovets@gmail.com>
 * @author Ngewi Fet <ngewif@gmail.com>
 */
public class CashFlowLineChartFragment extends IntervalReportFragment {

    private static final int ANIMATION_DURATION = 3000;
    private static final int NO_DATA_BAR_COUNTS = 5;
    private static final int[] LINE_COLORS = {
        parseColor("#68F1AF"), parseColor("#cc1f09"), parseColor("#EE8600"),
        parseColor("#1469EB"), parseColor("#B304AD"),
    };
    private static final int[] FILL_COLORS = {
        parseColor("#008000"), parseColor("#FF0000"), parseColor("#BE6B00"),
        parseColor("#0065FF"), parseColor("#8F038A"),
    };

    private FragmentLineChartBinding mBinding;

    @Override
    public View inflateView(LayoutInflater inflater, ViewGroup container) {
        mBinding = FragmentLineChartBinding.inflate(inflater, container, false);
        return mBinding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        final Context context = view.getContext();

        @ColorInt int textColorPrimary = getTextColor(context);

        mBinding.lineChart.setOnChartValueSelectedListener(this);
        mBinding.lineChart.getXAxis().setDrawGridLines(false);
        mBinding.lineChart.getXAxis().setTextColor(textColorPrimary);
        mBinding.lineChart.getAxisRight().setEnabled(false);
        mBinding.lineChart.getAxisLeft().enableGridDashedLine(4.0f, 4.0f, 0);
        mBinding.lineChart.getAxisLeft().setValueFormatter(new LargeValueFormatter(mCommodity.getSymbol()));
        mBinding.lineChart.getAxisLeft().setTextColor(textColorPrimary);
        Legend legend = mBinding.lineChart.getLegend();
        legend.setTextColor(textColorPrimary);
    }

    @Override
    public ReportType getReportType() {
        return ReportType.LINE_CHART;
    }

    /**
     * Returns a data object that represents a user data of the specified account types
     *
     * @param accountTypes account's types which will be displayed
     * @return a {@code LineData} instance that represents a user data
     */
    @NonNull
    private LineData getData(@NonNull Context context, List<AccountType> accountTypes) {
        Timber.i("getData for %s", accountTypes);
        calculateEarliestAndLatestTimestamps(accountTypes);
        ReportsActivity.GroupInterval groupInterval = mGroupInterval;
        LocalDateTime startDate = mReportPeriodStart;
        LocalDateTime endDate = mReportPeriodEnd;

        List<ILineDataSet> dataSets = new ArrayList<>();
        for (AccountType accountType : accountTypes) {
            List<Entry> entries = getEntryList(accountType, groupInterval, startDate, endDate);
            LineDataSet dataSet = new LineDataSet(entries, getLabel(context, accountType));
            dataSet.setDrawFilled(true);
            dataSet.setLineWidth(2);
            dataSet.setColor(LINE_COLORS[dataSets.size()]);
            dataSet.setFillColor(FILL_COLORS[dataSets.size()]);

            dataSets.add(dataSet);
        }

        LineData lineData = new LineData(dataSets);
        if (getYValueSum(lineData) == 0) {
            isChartDataPresent = false;
            return getEmptyData(context);
        }
        lineData.setValueTextColor(getTextColor(context));
        return lineData;
    }

    /**
     * Returns a data object that represents situation when no user data available
     *
     * @return a {@code LineData} instance for situation when no user data available
     */
    private LineData getEmptyData(@NonNull Context context) {
        List<Entry> yValues = new ArrayList<>();
        boolean isEven = true;
        for (int i = 0; i < NO_DATA_BAR_COUNTS; i++) {
            yValues.add(new Entry(i, isEven ? 5f : 4.5f));
            isEven = !isEven;
        }
        LineDataSet dataSet = new LineDataSet(yValues, context.getString(R.string.label_chart_no_data));
        dataSet.setDrawFilled(true);
        dataSet.setDrawValues(false);
        dataSet.setColor(NO_DATA_COLOR);
        dataSet.setFillColor(NO_DATA_COLOR);

        return new LineData(dataSet);
    }

    /**
     * Returns entries which represent a user data of the specified account type
     *
     * @param accountType   account's type which user data will be processed
     * @param groupInterval
     * @return entries which represent a user data
     */
    private List<Entry> getEntryList(
        @NonNull AccountType accountType,
        @NonNull ReportsActivity.GroupInterval groupInterval,
        @Nullable LocalDateTime startEntries,
        @Nullable LocalDateTime endEntries
    ) {
        final Commodity commodity = mCommodity;
        List<Entry> entries = new ArrayList<>();

        LocalDateTime startDate = startEntries;
        if (startDate == null) {
            Long startTime = earliestTimestamps.get(accountType);
            if (startTime != null) {
                startDate = new LocalDateTime(startTime);
            } else {
                return entries;
            }
        }
        LocalDateTime endDate = endEntries;
        if (endDate == null) {
            Long endTime = latestTimestamps.get(accountType);
            if (endTime != null) {
                endDate = new LocalDateTime(endTime);
            } else {
                endDate = LocalDateTime.now();
            }
        }
        final LocalDateTime earliestDate = earliestTransactionTimestamp;
        int xAxisOffset = getDateDiff(groupInterval, earliestDate, startDate);
        int count = getDateDiff(groupInterval, startDate, endDate);
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

        String where = AccountEntry.COLUMN_TYPE + "=?"
            + " AND " + AccountEntry.COLUMN_PLACEHOLDER + " = 0"
            + " AND " + AccountEntry.COLUMN_TEMPLATE + " = 0";
        String[] whereArgs = new String[]{accountType.name()};
        List<Account> accounts = mAccountsDbAdapter.getSimpleAccounts(where, whereArgs, null);

        for (int i = 0, x = xAxisOffset; i < count; i++, x++) {
            long startTime = DateExtKt.toMillis(startPeriod);
            long endTime = DateExtKt.toMillis(endPeriod);
            Money balance = Money.createZeroInstance(commodity);
            Map<String, Money> balances = mAccountsDbAdapter.getAccountsBalances(accounts, startTime, endTime);
            for (Money accountBalance : balances.values()) {
                Price price = pricesDbAdapter.getPrice(accountBalance.getCommodity(), commodity);
                if (price == null) continue;
                accountBalance = accountBalance.times(price);
                balance = balance.plus(accountBalance);
            }
            Timber.d("%s %s %s - %s %s", accountType, groupInterval, startPeriod, endPeriod, balance);

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

            if (balance.isAmountZero()) continue;
            float value = balance.toFloat();
            entries.add(new Entry(x, value));
        }

        return entries;
    }

    @Override
    public boolean requiresAccountTypeOptions() {
        return false;
    }

    @Override
    protected void generateReport(@NonNull Context context) {
        LineData lineData = getData(context, accountTypes);
        mBinding.lineChart.setData(lineData);
        isChartDataPresent = true;
    }

    @Override
    protected void displayReport() {
        if (!isChartDataPresent) {
            final Context context = mBinding.lineChart.getContext();
            mBinding.lineChart.getAxisLeft().setAxisMaxValue(10);
            mBinding.lineChart.getAxisLeft().setDrawLabels(false);
            mBinding.lineChart.getXAxis().setDrawLabels(false);
            mBinding.lineChart.setTouchEnabled(false);
            mSelectedValueTextView.setText(context.getString(R.string.label_chart_no_data));
        } else {
            mBinding.lineChart.animateX(ANIMATION_DURATION);
        }
        mBinding.lineChart.invalidate();
    }

    @Override
    public void onPrepareOptionsMenu(@NonNull Menu menu) {
        super.onPrepareOptionsMenu(menu);
        menu.findItem(R.id.menu_toggle_average_lines).setVisible(isChartDataPresent);
        showLegend(menu.findItem(R.id.menu_toggle_legend).isChecked());
        showAverageLines(menu.findItem(R.id.menu_toggle_average_lines).isChecked());
        // hide pie/bar chart specific menu items
        menu.findItem(R.id.menu_order_by_size).setVisible(false);
        menu.findItem(R.id.menu_toggle_labels).setVisible(false);
        menu.findItem(R.id.menu_percentage_mode).setVisible(false);
        menu.findItem(R.id.menu_group_other_slice).setVisible(false);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_toggle_legend: {
                item.setChecked(!item.isChecked());
                showLegend(item.isChecked());
                return true;
            }

            case R.id.menu_toggle_average_lines:
                item.setChecked(!item.isChecked());
                showAverageLines(item.isChecked());
                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onValueSelected(Entry e, Highlight h) {
        if (e == null) return;
        float value = e.getY();
        int dataSetIndex = h.getDataSetIndex();
        LineData data = mBinding.lineChart.getData();
        ILineDataSet dataSet = data.getDataSetByIndex(dataSetIndex);
        if (dataSet == null) return;
        String label = dataSet.getLabel();
        float total = getYValueSum(dataSet);
        float percent = (total != 0f) ? ((value * 100) / total) : 0f;
        mSelectedValueTextView.setText(formatSelectedValue(label, value, percent));
    }

    private void showLegend(boolean isVisible) {
        mBinding.lineChart.getLegend().setEnabled(isVisible);
        mBinding.lineChart.invalidate();
    }

    private void showAverageLines(boolean isVisible) {
        mBinding.lineChart.getAxisLeft().removeAllLimitLines();
        if (isVisible) {
            for (ILineDataSet dataSet : mBinding.lineChart.getData().getDataSets()) {
                int entryCount = dataSet.getEntryCount();
                float limit = 0f;
                if (entryCount > 0) {
                    limit = dataSet.getYMin() + (getYValueSum(dataSet) / entryCount);
                }
                LimitLine line = new LimitLine(limit, dataSet.getLabel());
                line.enableDashedLine(10, 5, 0);
                line.setLineColor(dataSet.getColor());
                mBinding.lineChart.getAxisLeft().addLimitLine(line);
            }
        }
        mBinding.lineChart.invalidate();
    }

}
