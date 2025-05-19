/*
 * Copyright (c) 2014-2015 Oleksandr Tyshkovets <olexandr.tyshkovets@gmail.com>
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

package org.gnucash.android.ui.report.piechart;

import static org.gnucash.android.util.ColorExtKt.getTextColorPrimary;

import android.content.Context;
import android.graphics.Color;
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
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.highlight.Highlight;

import org.gnucash.android.R;
import org.gnucash.android.databinding.FragmentPieChartBinding;
import org.gnucash.android.db.DatabaseSchema;
import org.gnucash.android.model.Account;
import org.gnucash.android.model.AccountType;
import org.gnucash.android.model.Money;
import org.gnucash.android.ui.report.BaseReportFragment;
import org.gnucash.android.ui.report.ReportType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Activity used for drawing a pie chart
 *
 * @author Oleksandr Tyshkovets <olexandr.tyshkovets@gmail.com>
 * @author Ngewi Fet <ngewif@gmail.com>
 */
public class PieChartFragment extends BaseReportFragment {

    public static final String TOTAL_VALUE_LABEL_PATTERN = "%s\n%.2f %s";
    private static final int ANIMATION_DURATION = 1800;
    public static final int CENTER_TEXT_SIZE = 18;
    /**
     * The space in degrees between the chart slices
     */
    public static final float SPACE_BETWEEN_SLICES = 2f;
    /**
     * All pie slices less than this threshold will be group in "other" slice. Using percents not absolute values.
     */
    private static final double GROUPING_SMALLER_SLICES_THRESHOLD = 5;

    private boolean mChartDataPresent = true;

    private boolean mGroupSmallerSlices = true;

    private FragmentPieChartBinding mBinding;

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        final Context context = view.getContext();
        @ColorInt int textColorPrimary = getTextColor(context);

        mBinding.pieChart.setCenterTextSize(CENTER_TEXT_SIZE);
        mBinding.pieChart.setDescription("");
        mBinding.pieChart.setOnChartValueSelectedListener(this);
        mBinding.pieChart.setHoleColor(Color.TRANSPARENT);
        mBinding.pieChart.setCenterTextColor(textColorPrimary);
        Legend legend = mBinding.pieChart.getLegend();
        legend.setTextColor(textColorPrimary);
        legend.setWordWrapEnabled(true);
    }

    @Override
    public ReportType getReportType() {
        return ReportType.PIE_CHART;
    }

    @Override
    public View inflateView(LayoutInflater inflater, ViewGroup container) {
        mBinding = FragmentPieChartBinding.inflate(inflater, container, false);
        return mBinding.getRoot();
    }

    @Override
    protected void generateReport(@NonNull Context context) {
        PieData pieData = getData();
        if (pieData != null && pieData.getYValCount() != 0) {
            mChartDataPresent = true;
            mBinding.pieChart.setData(mGroupSmallerSlices ? groupSmallerSlices(context, pieData) : pieData);
            float sum = mBinding.pieChart.getData().getYValueSum();
            String total = context.getString(R.string.label_chart_total);
            String currencySymbol = mCommodity.getSymbol();
            mBinding.pieChart.setCenterText(String.format(TOTAL_VALUE_LABEL_PATTERN, total, sum, currencySymbol));
        } else {
            mChartDataPresent = false;
            mBinding.pieChart.setCenterText(context.getString(R.string.label_chart_no_data));
            mBinding.pieChart.setData(getEmptyData(context));
        }
    }

    @Override
    protected void displayReport() {
        if (mChartDataPresent) {
            mBinding.pieChart.animateXY(ANIMATION_DURATION, ANIMATION_DURATION);
        }

        mSelectedValueTextView.setText(R.string.label_select_pie_slice_to_see_details);
        mBinding.pieChart.setTouchEnabled(mChartDataPresent);
        mBinding.pieChart.highlightValues(null);
        mBinding.pieChart.invalidate();
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
        AccountType accountType = mAccountType;
        String where = DatabaseSchema.AccountEntry.COLUMN_PLACEHOLDER + "=0 AND "
            + DatabaseSchema.AccountEntry.COLUMN_COMMODITY_UID + "=? AND "
            + DatabaseSchema.AccountEntry.COLUMN_TYPE + "=?";
        String[] whereArgs = new String[]{mCommodity.getUID(), accountType.name()};
        String orderBy = DatabaseSchema.AccountEntry.COLUMN_FULL_NAME + " ASC";
        List<Account> accounts = mAccountsDbAdapter.getSimpleAccountList(where, whereArgs, orderBy);
        for (Account account : accounts) {
            Money balance = mAccountsDbAdapter.getAccountBalance(account.getUID(), mReportPeriodStart, mReportPeriodEnd, false);
            float value = balance.toFloat();
            if (value > 0f) {
                dataSet.addEntry(new Entry(value, dataSet.getEntryCount()));
                @ColorInt int color;
                if (mUseAccountColor) {
                    color = (account.getColor() != Account.DEFAULT_COLOR)
                            ? account.getColor()
                            : COLORS[(dataSet.getEntryCount() - 1) % COLORS.length];
                } else {
                    color = COLORS[(dataSet.getEntryCount() - 1) % COLORS.length];
                }
                colors.add(color);
                labels.add(account.getName());
            }
        }
        dataSet.setColors(colors);
        dataSet.setSliceSpace(SPACE_BETWEEN_SLICES);
        return new PieData(labels, dataSet);
    }

    /**
     * Returns a data object that represents situation when no user data available
     *
     * @return a {@code PieData} instance for situation when no user data available
     */
    private PieData getEmptyData(@NonNull Context context) {
        PieDataSet dataSet = new PieDataSet(null, context.getString(R.string.label_chart_no_data));
        dataSet.addEntry(new Entry(1, 0));
        dataSet.setColor(NO_DATA_COLOR);
        dataSet.setDrawValues(false);
        return new PieData(Collections.singletonList(""), dataSet);
    }

    /**
     * Sorts the pie's slices in ascending order
     */
    private void bubbleSort() {
        List<String> labels = mBinding.pieChart.getData().getXVals();
        List<Entry> values = getYVals(mBinding.pieChart.getData().getDataSet());
        List<Integer> colors = mBinding.pieChart.getData().getDataSet().getColors();
        float tmp1;
        String tmp2;
        Integer tmp3;
        final int size = values.size();
        for (int i = 0; i < size - 1; i++) {
            for (int j = 1; j < size - i; j++) {
                int j1 = j - 1;
                if (values.get(j1).getVal() > values.get(j).getVal()) {
                    tmp1 = values.get(j1).getVal();
                    values.get(j1).setVal(values.get(j).getVal());
                    values.get(j).setVal(tmp1);

                    tmp2 = labels.get(j1);
                    labels.set(j1, labels.get(j));
                    labels.set(j, tmp2);

                    tmp3 = colors.get(j1);
                    colors.set(j1, colors.get(j));
                    colors.set(j, tmp3);
                }
            }
        }

        mBinding.pieChart.notifyDataSetChanged();
        mBinding.pieChart.highlightValues(null);
        mBinding.pieChart.invalidate();
    }

    @Override
    public void onPrepareOptionsMenu(@NonNull Menu menu) {
        super.onPrepareOptionsMenu(menu);
        menu.findItem(R.id.menu_order_by_size).setVisible(mChartDataPresent);
        menu.findItem(R.id.menu_toggle_labels).setVisible(mChartDataPresent);
        menu.findItem(R.id.menu_group_other_slice).setVisible(mChartDataPresent);
        // hide line/bar chart specific menu items
        menu.findItem(R.id.menu_percentage_mode).setVisible(false);
        menu.findItem(R.id.menu_toggle_average_lines).setVisible(false);
        menu.findItem(R.id.menu_group_reports_by).setVisible(false);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.isCheckable())
            item.setChecked(!item.isChecked());
        switch (item.getItemId()) {
            case R.id.menu_order_by_size: {
                bubbleSort();
                return true;
            }
            case R.id.menu_toggle_legend: {
                mBinding.pieChart.getLegend().setEnabled(!mBinding.pieChart.getLegend().isEnabled());
                mBinding.pieChart.notifyDataSetChanged();
                mBinding.pieChart.invalidate();
                return true;
            }
            case R.id.menu_toggle_labels: {
                mBinding.pieChart.getData().setDrawValues(!mBinding.pieChart.isDrawSliceTextEnabled());
                mBinding.pieChart.setDrawSliceText(!mBinding.pieChart.isDrawSliceTextEnabled());
                mBinding.pieChart.invalidate();
                return true;
            }
            case R.id.menu_group_other_slice: {
                mGroupSmallerSlices = !mGroupSmallerSlices;
                refresh();
                return true;
            }

            default:
                return super.onOptionsItemSelected(item);
        }
    }

    /**
     * Groups smaller slices. All smaller slices will be combined and displayed as a single "Other".
     *
     * @param context Context for retrieving resources
     * @param data    the pie data which smaller slices will be grouped
     * @return a {@code PieData} instance with combined smaller slices
     */
    @NonNull
    public static PieData groupSmallerSlices(@NonNull Context context, @NonNull PieData data) {
        float otherSlice = 0f;
        List<Entry> newEntries = new ArrayList<>();
        List<String> newLabels = new ArrayList<>();
        List<Integer> newColors = new ArrayList<>();
        List<Entry> entries = getYVals(data.getDataSet());
        for (int i = 0; i < entries.size(); i++) {
            float val = entries.get(i).getVal();
            if ((val * 100) / data.getYValueSum() > GROUPING_SMALLER_SLICES_THRESHOLD) {
                newEntries.add(new Entry(val, newEntries.size()));
                newLabels.add(data.getXVals().get(i));
                newColors.add(data.getDataSet().getColors().get(i));
            } else {
                otherSlice += val;
            }
        }

        if (otherSlice > 0) {
            newEntries.add(new Entry(otherSlice, newEntries.size()));
            newLabels.add(context.getString(R.string.label_other_slice));
            newColors.add(Color.LTGRAY);
        }

        PieDataSet dataSet = new PieDataSet(newEntries, "");
        dataSet.setSliceSpace(SPACE_BETWEEN_SLICES);
        dataSet.setColors(newColors);

        PieData dataSmaller = new PieData(newLabels, dataSet);
        dataSmaller.setValueTextColor(getTextColorPrimary(context));
        return dataSmaller;
    }

    @Override
    public void onValueSelected(Entry e, int dataSetIndex, Highlight h) {
        if (e == null) return;
        String label = mBinding.pieChart.getData().getXVals().get(e.getXIndex());
        float value = e.getVal();
        float percent = (value * 100) / mBinding.pieChart.getData().getYValueSum();
        mSelectedValueTextView.setText(String.format(SELECTED_VALUE_PATTERN, label, value, percent));
    }
}
