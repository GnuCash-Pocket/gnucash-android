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
package org.gnucash.android.ui.report.linechart

import android.content.Context
import android.graphics.Color
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.annotation.ColorInt
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.LimitLine
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.formatter.LargeValueFormatter
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet
import org.gnucash.android.R
import org.gnucash.android.databinding.FragmentChartBinding
import org.gnucash.android.db.DatabaseSchema.AccountEntry
import org.gnucash.android.model.AccountType
import org.gnucash.android.model.Commodity
import org.gnucash.android.model.Money.Companion.createZeroInstance
import org.gnucash.android.ui.report.IntervalReportFragment
import org.gnucash.android.ui.report.ReportType
import org.gnucash.android.ui.report.ReportsActivity.GroupInterval
import org.gnucash.android.util.endOfDay
import org.gnucash.android.util.getQuarter
import org.gnucash.android.util.lastDayOfMonth
import org.gnucash.android.util.lastDayOfYear
import org.gnucash.android.util.toMillis
import org.joda.time.LocalDateTime
import timber.log.Timber
import java.text.SimpleDateFormat

/**
 * Fragment for line chart reports
 *
 * @author Oleksandr Tyshkovets <olexandr.tyshkovets@gmail.com>
 * @author Ngewi Fet <ngewif@gmail.com>
 */
class CashFlowLineChartFragment : IntervalReportFragment<LineData>() {
    private var binding: FragmentChartBinding? = null
    private var chart: LineChart? = null
    private val lineColors = mapOf(
        AccountType.INCOME to Color.GREEN,
        AccountType.EXPENSE to Color.RED
    )
    private val fillColors = mapOf(
        AccountType.INCOME to Color.GREEN,
        AccountType.EXPENSE to Color.RED
    )

    override fun inflateView(inflater: LayoutInflater, container: ViewGroup?): View {
        val binding = FragmentChartBinding.inflate(inflater, container, false)
        this.binding = binding
        this.selectedValueTextView = binding.selectedChartSlice
        return binding.root
    }

    override val reportType: ReportType = ReportType.LINE_CHART

    /**
     * Returns a data object that represents a user data of the specified account types
     *
     * @param accountTypes account's types which will be displayed
     * @return a `LineData` instance that represents a user data
     */
    private fun getData(context: Context, accountTypes: List<AccountType>): LineData {
        Timber.i("getData for %s", accountTypes)
        val groupInterval = this.groupInterval
        val commodity = this.commodity

        val dates = calculateDateRange(accountTypes, groupInterval)
        if (dates == null) {
            isChartDataPresent = false
            return getEmptyData(context)
        }
        val startDate = dates.start
        val endDate = dates.endInclusive

        val dataSets = mutableListOf<ILineDataSet>()
        for (accountType in accountTypes) {
            val entries = getEntryList(accountType, groupInterval, startDate, endDate, commodity)
            val dataSet = LineDataSet(entries, getLabel(context, accountType)).apply {
                setDrawFilled(true)
                lineWidth = 2f
                color = lineColors[accountType] ?: Color.BLUE
                fillColor = fillColors[accountType] ?: Color.BLUE
            }
            dataSets.add(dataSet)
        }

        val lineData = LineData(dataSets)
        lineData.setValueTextColor(getTextColor(context))
        return lineData
    }

    /**
     * Returns a data object that represents situation when no user data available
     *
     * @return a `LineData` instance for situation when no user data available
     */
    private fun getEmptyData(context: Context): LineData {
        val yValues = mutableListOf<Entry>()
        var isEven = true
        for (i in 0 until NO_DATA_BAR_COUNTS) {
            yValues.add(Entry(i.toFloat(), if (isEven) 5f else 4.5f))
            isEven = !isEven
        }
        val dataSet = LineDataSet(yValues, context.getString(R.string.label_chart_no_data)).apply {
            setDrawFilled(true)
            setDrawValues(false)
            color = NO_DATA_COLOR
            fillColor = NO_DATA_COLOR
        }

        return LineData(dataSet)
    }

    private fun isEmpty(data: LineData): Boolean {
        return (data.dataSetCount == 0) ||
                (data.entryCount == 0) ||
                ((data.yMin <= DATA_EMPTY) && (data.yMax <= DATA_EMPTY))
    }

    /**
     * Returns entries which represent a user data of the specified account type
     *
     * @param accountType   account's type which user data will be processed
     * @param groupInterval
     * @return entries which represent a user data
     */
    private fun getEntryList(
        accountType: AccountType,
        groupInterval: GroupInterval,
        startDate: LocalDateTime,
        endDate: LocalDateTime,
        commodity: Commodity
    ): List<Entry> {
        val entries = mutableListOf<Entry>()

        var startPeriod: LocalDateTime = startDate
        var endPeriod: LocalDateTime = endDate
        when (groupInterval) {
            GroupInterval.MONTH -> endPeriod = startPeriod.lastDayOfMonth().endOfDay()

            GroupInterval.QUARTER -> endPeriod = startPeriod.plusMonths(2)
                .lastDayOfMonth().endOfDay()

            GroupInterval.YEAR -> endPeriod = startPeriod.lastDayOfYear().endOfDay()

            else -> Unit
        }

        val pattern = getXAxisPattern(groupInterval)
        val dateFormatter = SimpleDateFormat(pattern)

        val where = (AccountEntry.COLUMN_TYPE + "=?"
                + " AND " + AccountEntry.COLUMN_PLACEHOLDER + " = 0"
                + " AND " + AccountEntry.COLUMN_TEMPLATE + " = 0")
        val whereArgs = arrayOf<String?>(accountType.name)
        val accounts = accountsDbAdapter.getAllRecords(where, whereArgs)

        var x = 0f
        while (startPeriod <= endDate) {
            val startTime = startPeriod.toMillis()
            val endTime = endPeriod.toMillis()
            var balance = createZeroInstance(commodity)
            val balances = accountsDbAdapter.getAccountsBalances(accounts, startTime, endTime)
            for (accountBalance in balances.values) {
                var accountBalance = accountBalance
                val price = pricesDbAdapter.getPrice(accountBalance.commodity, commodity)
                    ?: continue
                accountBalance *= price
                balance += accountBalance
            }
            Timber.d(
                "%s %s %s - %s %s",
                accountType,
                groupInterval,
                startPeriod,
                endPeriod,
                balance
            )

            var datePretty = ""
            when (groupInterval) {
                GroupInterval.MONTH -> {
                    datePretty = dateFormatter.format(startTime)
                    startPeriod = startPeriod.plusMonths(1)
                    endPeriod = endPeriod.plusMonths(1)
                }

                GroupInterval.QUARTER -> {
                    val quarter = startPeriod.getQuarter()
                    datePretty = "Q" + quarter + " " + dateFormatter.format(startTime)
                    startPeriod = startPeriod.plusMonths(3)
                    endPeriod = endPeriod.plusMonths(3)
                }

                GroupInterval.YEAR -> {
                    datePretty = dateFormatter.format(startTime)
                    startPeriod = startPeriod.plusYears(1)
                    endPeriod = endPeriod.plusYears(1)
                }

                else -> Unit
            }

            val value = balance.toFloat()
            entries.add(Entry(x, value, datePretty))
            x++
        }

        return entries
    }

    override fun requiresAccountTypeOptions(): Boolean {
        return false
    }

    override fun generateReport(context: Context): LineData {
        val data = getData(context, accountTypes)
        isChartDataPresent = !isEmpty(data)
        return data
    }

    override fun displayReport(data: LineData) {
        val binding = binding ?: return
        val context = binding.root.context
        val isChartDataPresent = isChartDataPresent
        val selectedValueTextView = binding.selectedChartSlice
        @ColorInt val textColorPrimary = getTextColor(context)

        val chart = LineChart(context).apply {
            id = R.id.chart
            setOnChartValueSelectedListener(this@CashFlowLineChartFragment)
            xAxis.setDrawGridLines(false)
            xAxis.textColor = textColorPrimary
            xAxis.valueFormatter = IndexAxisValueFormatter(getXAxisLabels(data))
            axisRight.isEnabled = false
            axisLeft.enableGridDashedLine(4.0f, 4.0f, 0f)
            axisLeft.valueFormatter = LargeValueFormatter(commodity.symbol)
            axisLeft.textColor = textColorPrimary
            legend.textColor = textColorPrimary
            description.isEnabled = false

            this.data = data

            if (isChartDataPresent) {
                selectedValueTextView.text = null
                animateX(ANIMATION_DURATION)
            } else {
                selectedValueTextView.setText(R.string.label_chart_no_data)
                axisLeft.setAxisMaxValue(10f)
                axisLeft.setDrawLabels(false)
                xAxis.setDrawLabels(false)
                setTouchEnabled(false)
                clearAnimation()
            }
            highlightValues(null)
        }
        this.chart = chart

        binding.chartContainer.apply {
            removeAllViews()
            addView(
                chart,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
        }
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        super.onPrepareOptionsMenu(menu)
        menu.findItem(R.id.menu_toggle_average_lines).isVisible = isChartDataPresent
        showLegend(menu.findItem(R.id.menu_toggle_legend).isChecked)
        showAverageLines(menu.findItem(R.id.menu_toggle_average_lines).isChecked)
        // hide pie/bar chart specific menu items
        menu.findItem(R.id.menu_order_by_size).isVisible = false
        menu.findItem(R.id.menu_toggle_labels).isVisible = false
        menu.findItem(R.id.menu_percentage_mode).isVisible = false
        menu.findItem(R.id.menu_group_other_slice).isVisible = false
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_toggle_legend -> {
                item.isChecked = !item.isChecked
                showLegend(item.isChecked)
                true
            }

            R.id.menu_toggle_average_lines -> {
                item.isChecked = !item.isChecked
                showAverageLines(item.isChecked)
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onValueSelected(e: Entry?, h: Highlight) {
        val chart = chart ?: return
        if (e == null) return
        val value = e.y
        val dataSetIndex = h.dataSetIndex
        val data = chart.data
        val dataSet = data.getDataSetByIndex(dataSetIndex) ?: return
        val label = dataSet.label ?: return
        val total = getYValueSum<Entry>(dataSet)
        val percent = if (total != 0f) ((value * 100) / total) else 0f
        selectedValueTextView?.text = formatSelectedValue(label, value, percent)
    }

    private fun showLegend(isVisible: Boolean) {
        val lineChart = chart ?: return
        lineChart.legend.isEnabled = isVisible
        lineChart.invalidate()
    }

    private fun showAverageLines(isVisible: Boolean) {
        val lineChart = chart ?: return
        lineChart.axisLeft.removeAllLimitLines()
        if (isVisible) {
            for (dataSet in lineChart.data.dataSets) {
                val entryCount = dataSet.entryCount
                var limit = 0f
                if (entryCount > 0) {
                    limit = dataSet.yMin + (getYValueSum<Entry>(dataSet) / entryCount)
                }
                val line = LimitLine(limit, dataSet.label)
                line.enableDashedLine(10f, 5f, 0f)
                line.lineColor = dataSet.color
                lineChart.axisLeft.addLimitLine(line)
            }
        }
        lineChart.invalidate()
    }

    private fun getXAxisLabels(data: LineData): List<String> {
        val labels = sortedMapOf<Int, String>()

        for (i in 0 until data.dataSetCount) {
            val dataSet = data.getDataSetByIndex(i)
            for (e in 0 until dataSet.entryCount) {
                val entry = dataSet.getEntryForIndex(e)
                val entryData = entry.data as? String ?: continue
                labels[e] = entryData
            }
        }

        return labels.values.toList()
    }

    companion object {
        private const val ANIMATION_DURATION = 1500
        private const val NO_DATA_BAR_COUNTS = 5
    }
}
