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
import com.github.mikephil.charting.data.PieEntry;
import com.github.mikephil.charting.highlight.Highlight;

import org.gnucash.android.R;
import org.gnucash.android.databinding.FragmentPieChartBinding;
import org.gnucash.android.db.DatabaseSchema;
import org.gnucash.android.model.Account;
import org.gnucash.android.model.AccountType;
import org.gnucash.android.model.Money;
import org.gnucash.android.model.Price;
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
        mBinding.pieChart.setOnChartValueSelectedListener(this);
        mBinding.pieChart.setDrawHoleEnabled(false);
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
        if (pieData.getDataSetCount() > 0 && pieData.getDataSet().getEntryCount() > 0) {
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
    @NonNull
    private PieData getData() {
        PieDataSet dataSet = new PieDataSet(null, "");
        List<Integer> colors = new ArrayList<>();
        AccountType accountType = mAccountType;
        String where = DatabaseSchema.AccountEntry.COLUMN_PLACEHOLDER + "=0 AND "
            + DatabaseSchema.AccountEntry.COLUMN_TYPE + "=?";
        String[] whereArgs = new String[]{accountType.name()};
        String orderBy = DatabaseSchema.AccountEntry.COLUMN_FULL_NAME + " ASC";
        List<Account> accounts = mAccountsDbAdapter.getSimpleAccountList(where, whereArgs, orderBy);
        for (Account account : accounts) {
            Money balance = mAccountsDbAdapter.getAccountBalance(account, mReportPeriodStart, mReportPeriodEnd, false);
            if (balance.isAmountZero()) continue;
            Price price = pricesDbAdapter.getPrice(balance.getCommodity(), mCommodity);
            if (price == null) continue;
            balance = balance.times(price);
            float value = balance.toFloat();
            if (value > 0f) {
                int count = dataSet.getEntryCount();
                @ColorInt int color = getAccountColor(account, count);
                dataSet.addEntry(new PieEntry(value, account.getName()));
                colors.add(color);
            }
        }
        dataSet.setColors(colors);
        dataSet.setSliceSpace(SPACE_BETWEEN_SLICES);
        return new PieData(dataSet);
    }

    /**
     * Returns a data object that represents situation when no user data available
     *
     * @return a {@code PieData} instance for situation when no user data available
     */
    private PieData getEmptyData(@NonNull Context context) {
        PieDataSet dataSet = new PieDataSet(null, context.getString(R.string.label_chart_no_data));
        dataSet.addEntry(new PieEntry(1, 0));
        dataSet.setColor(NO_DATA_COLOR);
        dataSet.setDrawValues(false);
        return new PieData(dataSet);
    }

    /**
     * Sorts the pie's slices in ascending order
     */
    private void sort() {
        PieData data = mBinding.pieChart.getData();
        PieDataSet dataSet = (PieDataSet) data.getDataSetByIndex(0);
        final int size = dataSet.getEntryCount();
        List<PieChartEntry> entries = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            entries.add(new PieChartEntry(dataSet.getEntryForIndex(i), dataSet.getColor(i)));
        }
        Collections.sort(entries, new PieChartComparator());
        List<Integer> colors = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            PieChartEntry entry = entries.get(i);
            dataSet.removeFirst();
            dataSet.addEntry(entry.getEntry());
            colors.add(entry.getColor());
        }
        dataSet.setColors(colors);

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
                sort();
                return true;
            }
            case R.id.menu_toggle_legend: {
                mBinding.pieChart.getLegend().setEnabled(!mBinding.pieChart.getLegend().isEnabled());
                mBinding.pieChart.notifyDataSetChanged();
                mBinding.pieChart.invalidate();
                return true;
            }
            case R.id.menu_toggle_labels: {
                boolean draw = !mBinding.pieChart.isDrawEntryLabelsEnabled();
                mBinding.pieChart.getData().setDrawValues(draw);
                mBinding.pieChart.setDrawEntryLabels(draw);
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
        PieDataSet dataSet = (PieDataSet) data.getDataSetByIndex(0);
        final int size = dataSet.getEntryCount();
        if (size == 0) return data;
        float range = data.getYValueSum();
        if (range <= 0) return data;

        float otherSlice = 0f;
        List<PieEntry> entriesSmaller = new ArrayList<>();
        List<Integer> colorsSmaller = new ArrayList<>();

        for (int i = 0; i < size; i++) {
            PieEntry entry = dataSet.getEntryForIndex(i);
            float value = entry.getValue();
            if ((value * 100) / range > GROUPING_SMALLER_SLICES_THRESHOLD) {
                entriesSmaller.add(entry);
                colorsSmaller.add(dataSet.getColor(i));
            } else {
                otherSlice += value;
            }
        }

        if (otherSlice > 0) {
            entriesSmaller.add(new PieEntry(otherSlice, context.getString(R.string.label_other_slice)));
            colorsSmaller.add(Color.LTGRAY);
        }

        PieDataSet dataSetSmaller = new PieDataSet(entriesSmaller, "");
        dataSetSmaller.setSliceSpace(SPACE_BETWEEN_SLICES);
        dataSetSmaller.setColors(colorsSmaller);

        return new PieData(dataSetSmaller);
    }

    @Override
    public void onValueSelected(Entry e, Highlight h) {
        if (e == null) return;
        PieEntry entry = (PieEntry) e;
        String label = entry.getLabel();
        float value = entry.getValue();
        PieData data = mBinding.pieChart.getData();
        float total = data.getYValueSum();
        float percent = (total != 0f) ? ((value * 100) / total) : 0f;
        mSelectedValueTextView.setText(String.format(SELECTED_VALUE_PATTERN, label, value, percent));
    }
}
