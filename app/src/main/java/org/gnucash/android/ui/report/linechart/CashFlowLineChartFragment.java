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
import org.gnucash.android.db.adapter.TransactionsDbAdapter;
import org.gnucash.android.model.Account;
import org.gnucash.android.model.AccountType;
import org.gnucash.android.ui.report.BaseReportFragment;
import org.gnucash.android.ui.report.ReportType;
import org.gnucash.android.ui.report.ReportsActivity.GroupInterval;
import org.joda.time.LocalDate;
import org.joda.time.LocalDateTime;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import timber.log.Timber;

/**
 * Fragment for line chart reports
 *
 * @author Oleksandr Tyshkovets <olexandr.tyshkovets@gmail.com>
 * @author Ngewi Fet <ngewif@gmail.com>
 */
public class CashFlowLineChartFragment extends BaseReportFragment {

    private static final String X_AXIS_PATTERN = "MMM YY";
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

    private Map<AccountType, Long> mEarliestTimestampsMap = new HashMap<>();
    private Map<AccountType, Long> mLatestTimestampsMap = new HashMap<>();
    private long mEarliestTransactionTimestamp;
    private long mLatestTransactionTimestamp;
    private boolean mChartDataPresent = true;
    private final List<AccountType> accountTypes = new ArrayList<>(2);

    private FragmentLineChartBinding mBinding;

    public CashFlowLineChartFragment() {
        accountTypes.add(AccountType.INCOME);
        accountTypes.add(AccountType.EXPENSE);
    }

    @Override
    public View inflateView(LayoutInflater inflater, ViewGroup container) {
        mBinding = FragmentLineChartBinding.inflate(inflater, container, false);
        return mBinding.getRoot();
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        final Context context = mBinding.lineChart.getContext();

        @ColorInt int textColorPrimary = getTextColor(context);

        mBinding.lineChart.setOnChartValueSelectedListener(this);
        mBinding.lineChart.setDescription("");
        mBinding.lineChart.getXAxis().setDrawGridLines(false);
        mBinding.lineChart.getXAxis().setTextColor(textColorPrimary);
        mBinding.lineChart.getAxisRight().setEnabled(false);
        mBinding.lineChart.getAxisLeft().enableGridDashedLine(4.0f, 4.0f, 0);
        mBinding.lineChart.getAxisLeft().setValueFormatter(new LargeValueFormatter(mCommodity.getSymbol()));
        mBinding.lineChart.getAxisLeft().setTextColor(textColorPrimary);

        Legend legend = mBinding.lineChart.getLegend();
        legend.setPosition(Legend.LegendPosition.BELOW_CHART_CENTER);
        legend.setTextSize(16);
        legend.setForm(Legend.LegendForm.CIRCLE);
        legend.setTextColor(textColorPrimary);
    }

    @Override
    public ReportType getReportType() {
        return ReportType.LINE_CHART;
    }

    /**
     * Returns a data object that represents a user data of the specified account types
     *
     * @param accountTypeList account's types which will be displayed
     * @return a {@code LineData} instance that represents a user data
     */
    @NonNull
    private LineData getData(@NonNull Context context, List<AccountType> accountTypeList) {
        Timber.i("getData for %s", accountTypeList);
        calculateEarliestAndLatestTimestamps(accountTypeList);
        // LocalDateTime?
        LocalDate startDate;
        LocalDate endDate;
        if (mReportPeriodStart == -1 && mReportPeriodEnd == -1) {
            startDate = new LocalDate(mEarliestTransactionTimestamp).withDayOfMonth(1);
            endDate = new LocalDate(mLatestTransactionTimestamp).withDayOfMonth(1);
        } else {
            startDate = new LocalDate(mReportPeriodStart).withDayOfMonth(1);
            endDate = new LocalDate(mReportPeriodEnd).withDayOfMonth(1);
        }

        int count = getDateDiff(new LocalDateTime(startDate.toDate().getTime()), new LocalDateTime(endDate.toDate().getTime()));
        Timber.d("X-axis count %d", count);
        List<String> xValues = new ArrayList<>();
        for (int i = 0; i <= count; i++) {
            switch (mGroupInterval) {
                case MONTH:
                    xValues.add(startDate.toString(X_AXIS_PATTERN));
                    Timber.d("X-axis %s", startDate.toString("MM yy"));
                    startDate = startDate.plusMonths(1);
                    break;
                case QUARTER:
                    int quarter = getQuarter(new LocalDateTime(startDate.toDate().getTime()));
                    xValues.add("Q" + quarter + startDate.toString(" yy"));
                    Timber.d("X-axis " + "Q" + quarter + startDate.toString(" MM yy"));
                    startDate = startDate.plusMonths(3);
                    break;
                case YEAR:
                    xValues.add(startDate.toString("yyyy"));
                    Timber.d("X-axis %s", startDate.toString("yyyy"));
                    startDate = startDate.plusYears(1);
                    break;
            }
        }

        List<ILineDataSet> dataSets = new ArrayList<>();
        for (AccountType accountType : accountTypeList) {
            LineDataSet set = new LineDataSet(getEntryList(accountType), accountType.toString());
            set.setDrawFilled(true);
            set.setLineWidth(2);
            set.setColor(LINE_COLORS[dataSets.size()]);
            set.setFillColor(FILL_COLORS[dataSets.size()]);

            dataSets.add(set);
        }

        LineData lineData = new LineData(xValues, dataSets);
        if (getYValueSum(lineData) == 0) {
            mChartDataPresent = false;
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
        List<String> xValues = new ArrayList<>();
        List<Entry> yValues = new ArrayList<>();
        boolean isEven = true;
        for (int i = 0; i < NO_DATA_BAR_COUNTS; i++) {
            xValues.add("");
            yValues.add(new Entry(isEven ? 5f : 4.5f, i));
            isEven = !isEven;
        }
        LineDataSet set = new LineDataSet(yValues, context.getString(R.string.label_chart_no_data));
        set.setDrawFilled(true);
        set.setDrawValues(false);
        set.setColor(NO_DATA_COLOR);
        set.setFillColor(NO_DATA_COLOR);

        return new LineData(xValues, Collections.singletonList(set));
    }

    /**
     * Returns entries which represent a user data of the specified account type
     *
     * @param accountType account's type which user data will be processed
     * @return entries which represent a user data
     */
    private List<Entry> getEntryList(AccountType accountType) {
        List<String> accountUIDList = new ArrayList<>();
        for (Account account : mAccountsDbAdapter.getSimpleAccountList()) {
            if (account.getAccountType() == accountType
                && !account.isPlaceholderAccount()
                && account.getCommodity().equals(mCommodity)) {
                accountUIDList.add(account.getUID());
            }
        }

        LocalDateTime earliest;
        LocalDateTime latest;
        if (mReportPeriodStart == -1 && mReportPeriodEnd == -1) {
            earliest = new LocalDateTime(mEarliestTimestampsMap.get(accountType));
            latest = new LocalDateTime(mLatestTimestampsMap.get(accountType));
        } else {
            earliest = new LocalDateTime(mReportPeriodStart);
            latest = new LocalDateTime(mReportPeriodEnd);
        }
        Timber.d("Earliest " + accountType + " date " + earliest.toString("dd MM yyyy"));
        Timber.d("Latest " + accountType + " date " + latest.toString("dd MM yyyy"));

        int xAxisOffset = getDateDiff(new LocalDateTime(mEarliestTransactionTimestamp), earliest);
        int count = getDateDiff(earliest, latest);
        List<Entry> values = new ArrayList<>(count + 1);
        for (int i = 0; i <= count; i++) {
            long start = 0;
            long end = 0;
            switch (mGroupInterval) {
                case QUARTER:
                    int quarter = getQuarter(earliest);
                    start = earliest.withMonthOfYear(quarter * 3 - 2).dayOfMonth().withMinimumValue().millisOfDay().withMinimumValue().toDateTime().getMillis();
                    end = earliest.withMonthOfYear(quarter * 3).dayOfMonth().withMaximumValue().millisOfDay().withMaximumValue().toDateTime().getMillis();

                    earliest = earliest.plusMonths(3);
                    break;
                case MONTH:
                    start = earliest.dayOfMonth().withMinimumValue().millisOfDay().withMinimumValue().toDateTime().getMillis();
                    end = earliest.dayOfMonth().withMaximumValue().millisOfDay().withMaximumValue().toDateTime().getMillis();

                    earliest = earliest.plusMonths(1);
                    break;
                case YEAR:
                    start = earliest.dayOfYear().withMinimumValue().millisOfDay().withMinimumValue().toDateTime().getMillis();
                    end = earliest.dayOfYear().withMaximumValue().millisOfDay().withMaximumValue().toDateTime().getMillis();

                    earliest = earliest.plusYears(1);
                    break;
            }
            float balance = mAccountsDbAdapter.getAccountsBalance(accountUIDList, start, end).toFloat();
            values.add(new Entry(balance, i + xAxisOffset));
            Timber.d(accountType + earliest.toString(" MMM yyyy") + ", balance = " + balance);
        }

        return values;
    }

    /**
     * Calculates the earliest and latest transaction's timestamps of the specified account types
     *
     * @param accountTypes account's types which will be processed
     */
    private void calculateEarliestAndLatestTimestamps(List<AccountType> accountTypes) {
        if (mReportPeriodStart != -1 && mReportPeriodEnd != -1) {
            mEarliestTransactionTimestamp = mReportPeriodStart;
            mLatestTransactionTimestamp = mReportPeriodEnd;
            return;
        }

        mEarliestTimestampsMap.clear();
        mLatestTimestampsMap.clear();
        TransactionsDbAdapter dbAdapter = TransactionsDbAdapter.getInstance();
        final String currencyCode = mCommodity.getCurrencyCode();
        for (AccountType type : accountTypes) {
            long earliest = dbAdapter.getTimestampOfEarliestTransaction(type, currencyCode);
            long latest = dbAdapter.getTimestampOfLatestTransaction(type, currencyCode);
            if (earliest > 0 && latest > 0) {
                mEarliestTimestampsMap.put(type, earliest);
                mLatestTimestampsMap.put(type, latest);
            }
        }

        if (mEarliestTimestampsMap.isEmpty() || mLatestTimestampsMap.isEmpty()) {
            return;
        }

        List<Long> timestamps = new ArrayList<>(mEarliestTimestampsMap.values());
        timestamps.addAll(mLatestTimestampsMap.values());
        Collections.sort(timestamps);
        mEarliestTransactionTimestamp = timestamps.get(0);
        mLatestTransactionTimestamp = timestamps.get(timestamps.size() - 1);
    }

    @Override
    public boolean requiresAccountTypeOptions() {
        return false;
    }

    @Override
    protected void generateReport(@NonNull Context context) {
        LineData lineData = getData(context, accountTypes);
        mBinding.lineChart.setData(lineData);
        mChartDataPresent = true;
    }

    @Override
    protected void displayReport() {
        if (!mChartDataPresent) {
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
    public void onTimeRangeUpdated(long start, long end) {
        if (mReportPeriodStart != start || mReportPeriodEnd != end) {
            mReportPeriodStart = start;
            mReportPeriodEnd = end;
            mBinding.lineChart.setData(getData(mBinding.lineChart.getContext(), accountTypes));
            mBinding.lineChart.invalidate();
        }
    }

    @Override
    public void onGroupingUpdated(GroupInterval groupInterval) {
        if (mGroupInterval != groupInterval) {
            mGroupInterval = groupInterval;
            mBinding.lineChart.setData(getData(mBinding.lineChart.getContext(), accountTypes));
            mBinding.lineChart.invalidate();
        }
    }

    @Override
    public void onPrepareOptionsMenu(@NonNull Menu menu) {
        menu.findItem(R.id.menu_toggle_average_lines).setVisible(mChartDataPresent);
        // hide pie/bar chart specific menu items
        menu.findItem(R.id.menu_order_by_size).setVisible(false);
        menu.findItem(R.id.menu_toggle_labels).setVisible(false);
        menu.findItem(R.id.menu_percentage_mode).setVisible(false);
        menu.findItem(R.id.menu_group_other_slice).setVisible(false);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.isCheckable())
            item.setChecked(!item.isChecked());
        switch (item.getItemId()) {
            case R.id.menu_toggle_legend:
                mBinding.lineChart.getLegend().setEnabled(!mBinding.lineChart.getLegend().isEnabled());
                mBinding.lineChart.invalidate();
                return true;

            case R.id.menu_toggle_average_lines:
                if (mBinding.lineChart.getAxisLeft().getLimitLines().isEmpty()) {
                    for (ILineDataSet set : mBinding.lineChart.getData().getDataSets()) {
                        LimitLine line = new LimitLine(getYValueSum(set) / set.getEntryCount(), set.getLabel());
                        line.enableDashedLine(10, 5, 0);
                        line.setLineColor(set.getColor());
                        mBinding.lineChart.getAxisLeft().addLimitLine(line);
                    }
                } else {
                    mBinding.lineChart.getAxisLeft().removeAllLimitLines();
                }
                mBinding.lineChart.invalidate();
                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onValueSelected(Entry e, int dataSetIndex, Highlight h) {
        if (e == null) return;
        String label = mBinding.lineChart.getData().getXVals().get(e.getXIndex());
        double value = e.getVal();
        double sum = getYValueSum(mBinding.lineChart.getData().getDataSetByIndex(dataSetIndex));
        mSelectedValueTextView.setText(String.format(SELECTED_VALUE_PATTERN, label, value, (value * 100) / sum));
    }

}
