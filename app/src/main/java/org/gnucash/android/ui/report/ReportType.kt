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
package org.gnucash.android.ui.report

import android.content.Context
import androidx.annotation.ColorRes
import androidx.annotation.StringRes
import org.gnucash.android.R
import org.gnucash.android.ui.report.barchart.StackedBarChartFragment
import org.gnucash.android.ui.report.linechart.CashFlowLineChartFragment
import org.gnucash.android.ui.report.piechart.PieChartFragment
import org.gnucash.android.ui.report.sheet.BalanceSheetFragment

/**
 * Different types of reports
 *
 * This class also contains mappings for the reports of the different types which are available
 * in the system. When adding a new report, make sure to add a mapping in the constructor
 */
enum class ReportType(
    @StringRes val titleId: Int,
    @ColorRes val colorId: Int,
    val fragmentClass: Class<out BaseReportFragment<*>>
) {
    PIE_CHART(
        R.string.title_pie_chart,
        R.color.report_orange,
        PieChartFragment::class.java
    ),
    BAR_CHART(
        R.string.title_bar_chart,
        R.color.report_red,
        StackedBarChartFragment::class.java
    ),
    LINE_CHART(
        R.string.title_line_chart,
        R.color.report_blue,
        CashFlowLineChartFragment::class.java
    ),
    SHEET(
        R.string.title_balance_sheet_report,
        R.color.report_purple,
        BalanceSheetFragment::class.java
    ),
    NONE(
        R.string.title_reports,
        R.color.report_green,
        ReportsOverviewFragment::class.java
    );

    val fragment: BaseReportFragment<*>
        get() = fragmentClass.newInstance()

    companion object {
        private val _values = values()

        fun getReportNames(context: Context): List<String> {
            val names = mutableListOf<String>()
            for (value in _values) {
                if (value == NONE) continue
                names.add(context.getString(value.titleId))
            }
            return names
        }
    }
}
