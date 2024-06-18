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

import android.content.Context;

import androidx.annotation.ColorRes;
import androidx.annotation.LayoutRes;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import org.gnucash.android.R;
import org.gnucash.android.ui.report.barchart.StackedBarChartFragment;
import org.gnucash.android.ui.report.linechart.CashFlowLineChartFragment;
import org.gnucash.android.ui.report.piechart.PieChartFragment;
import org.gnucash.android.ui.report.sheet.BalanceSheetFragment;

import java.util.ArrayList;
import java.util.List;

import timber.log.Timber;

/**
 * Different types of reports
 * <p>This class also contains mappings for the reports of the different types which are available
 * in the system. When adding a new report, make sure to add a mapping in the constructor</p>
 */
public enum ReportType {
    PIE_CHART(R.string.title_pie_chart, R.color.account_green, R.layout.fragment_pie_chart, PieChartFragment.class),
    BAR_CHART(R.string.title_bar_chart, R.color.account_red, R.layout.fragment_bar_chart, StackedBarChartFragment.class),
    LINE_CHART(R.string.title_cash_flow_report, R.color.account_blue, R.layout.fragment_line_chart, CashFlowLineChartFragment.class),
    TEXT(R.string.title_balance_sheet_report, R.color.account_purple, R.layout.fragment_text_report, BalanceSheetFragment.class),
    NONE(R.string.title_reports, R.color.theme_primary, R.layout.fragment_report_summary, ReportsOverviewFragment.class);

    @StringRes
    final int titleId;
    @ColorRes
    final int colorId;
    @LayoutRes
    final int layoutId;
    @Nullable
    final Class<? extends BaseReportFragment> fragmentClass;

    ReportType(@StringRes int titleId,
               @ColorRes int colorId,
               @LayoutRes int layoutId,
               @Nullable Class<? extends BaseReportFragment> fragmentClass) {
        this.titleId = titleId;
        this.colorId = colorId;
        this.layoutId = layoutId;
        this.fragmentClass = fragmentClass;
    }

    @Nullable
    public BaseReportFragment getFragment() {
        BaseReportFragment fragment = null;
        try {
            fragment = fragmentClass.newInstance();
        } catch (InstantiationException | IllegalAccessException e) {
            Timber.e(e);
        }
        return fragment;
    }

    public static List<String> getReportNames(Context context) {
        ReportType[] values = ReportType.values();
        List<String> names = new ArrayList<>(values.length);
        for (ReportType value : values) {
            if (value == ReportType.NONE) continue;
            names.add(context.getString(value.titleId));
        }
        return names;
    }
}
