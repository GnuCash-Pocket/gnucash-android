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
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.annotation.ColorInt
import com.github.mikephil.charting.components.LimitLine
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.LargeValueFormatter
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet
import org.gnucash.android.R
import org.gnucash.android.databinding.FragmentLineChartBinding
import org.gnucash.android.db.DatabaseSchema.AccountEntry
import org.gnucash.android.model.AccountType
import org.gnucash.android.model.Money.Companion.createZeroInstance
import org.gnucash.android.ui.report.IntervalReportFragment
import org.gnucash.android.ui.report.ReportType
import org.gnucash.android.ui.report.ReportsActivity.GroupInterval
import org.gnucash.android.util.getFirstQuarterMonth
import org.gnucash.android.util.parseColor
import org.gnucash.android.util.toMillis
import org.joda.time.LocalDateTime
import timber.log.Timber

/**
 * Fragment for line chart reports
 *
 * @author Oleksandr Tyshkovets <olexandr.tyshkovets@gmail.com>
 * @author Ngewi Fet <ngewif@gmail.com>
 */
class CashFlowLineChartFragment : IntervalReportFragment() {
    private var binding: FragmentLineChartBinding? = null

    override fun inflateView(inflater: LayoutInflater, container: ViewGroup?): View {
        val binding = FragmentLineChartBinding.inflate(inflater, container, false)
        this.binding = binding
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val context = view.context
        val binding = binding!!

        @ColorInt val textColorPrimary = getTextColor(context)

        binding.lineChart.apply {
            setOnChartValueSelectedListener(this@CashFlowLineChartFragment)
            xAxis.setDrawGridLines(false)
            xAxis.textColor = textColorPrimary
            axisRight.isEnabled = false
            axisLeft.enableGridDashedLine(4.0f, 4.0f, 0f)
            axisLeft.valueFormatter = LargeValueFormatter(commodity.symbol)
            axisLeft.textColor = textColorPrimary
            legend.textColor = textColorPrimary
        }
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
        calculateEarliestAndLatestTimestamps(accountTypes)
        val groupInterval = this.groupInterval
        val startDate = reportPeriodStart
        val endDate = reportPeriodEnd

        val dataSets = mutableListOf<ILineDataSet>()
        for (accountType in accountTypes) {
            val entries = getEntryList(accountType, groupInterval, startDate, endDate)
            val dataSet = LineDataSet(entries, getLabel(context, accountType))
            dataSet.setDrawFilled(true)
            dataSet.lineWidth = 2f
            dataSet.color = LINE_COLORS[dataSets.size]
            dataSet.fillColor = FILL_COLORS[dataSets.size]
            dataSets.add(dataSet)
        }

        val lineData = LineData(dataSets)
        if (getYValueSum<Entry, ILineDataSet>(lineData) == 0f) {
            isChartDataPresent = false
            return getEmptyData(context)
        }
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
        val dataSet = LineDataSet(yValues, context.getString(R.string.label_chart_no_data))
        dataSet.setDrawFilled(true)
        dataSet.setDrawValues(false)
        dataSet.color = NO_DATA_COLOR
        dataSet.fillColor = NO_DATA_COLOR

        return LineData(dataSet)
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
        startEntries: LocalDateTime?,
        endEntries: LocalDateTime?
    ): List<Entry> {
        val commodity = this.commodity
        val entries = mutableListOf<Entry>()

        var startDate = startEntries
        if (startDate == null) {
            val startTime = earliestTimestamps[accountType]
            if (startTime != null) {
                startDate = LocalDateTime(startTime)
            } else {
                return entries
            }
        }
        var endDate = endEntries
        if (endDate == null) {
            val endTime = latestTimestamps[accountType]
            endDate = if (endTime != null) {
                LocalDateTime(endTime)
            } else {
                LocalDateTime.now()
            }
        }
        val earliestDate = earliestTransactionTimestamp!!
        val xAxisOffset = getDateDiff(groupInterval, earliestDate, startDate)
        val count = getDateDiff(groupInterval, startDate, endDate)
        var startPeriod: LocalDateTime = startDate
        var endPeriod = endDate!!
        when (groupInterval) {
            GroupInterval.MONTH -> endPeriod = startPeriod.plusMonths(1)
            GroupInterval.QUARTER -> {
                startPeriod = startPeriod.withMonthOfYear(startPeriod.getFirstQuarterMonth())
                    .dayOfMonth()
                    .withMinimumValue()
                endPeriod = startPeriod.plusMonths(3)
            }

            GroupInterval.YEAR -> endPeriod = startPeriod.plusYears(1)
            else -> Unit
        }

        val where = (AccountEntry.COLUMN_TYPE + "=?"
                + " AND " + AccountEntry.COLUMN_PLACEHOLDER + " = 0"
                + " AND " + AccountEntry.COLUMN_TEMPLATE + " = 0")
        val whereArgs = arrayOf<String?>(accountType.name)
        val accounts = accountsDbAdapter.getSimpleAccounts(where, whereArgs, null)

        var i = 0
        var x = xAxisOffset
        while (i < count) {
            val startTime = startPeriod.toMillis()
            val endTime = endPeriod.toMillis()
            var balance = createZeroInstance(commodity)
            val balances = accountsDbAdapter.getAccountsBalances(accounts, startTime, endTime)
            for (accountBalance in balances.values) {
                var accountBalance = accountBalance
                val price = pricesDbAdapter.getPrice(accountBalance.commodity, commodity)
                if (price == null) continue
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

            startPeriod = endPeriod
            when (groupInterval) {
                GroupInterval.MONTH -> endPeriod = endPeriod.plusMonths(1)
                GroupInterval.QUARTER -> endPeriod = endPeriod.plusMonths(3)
                GroupInterval.YEAR -> endPeriod = endPeriod.plusYears(1)
                else -> Unit
            }

            if (balance.isAmountZero) {
                i++
                x++
                continue
            }
            val value = balance.toFloat()
            entries.add(Entry(x.toFloat(), value))
            i++
            x++
        }

        return entries
    }

    override fun requiresAccountTypeOptions(): Boolean {
        return false
    }

    override fun generateReport(context: Context) {
        val binding = binding ?: return
        binding.lineChart.data = getData(context, accountTypes)
        isChartDataPresent = true
    }

    override fun displayReport() {
        val binding = binding ?: return
        binding.lineChart.apply {
            if (isChartDataPresent) {
                animateX(ANIMATION_DURATION)
            } else {
                axisLeft.setAxisMaxValue(10f)
                axisLeft.setDrawLabels(false)
                xAxis.setDrawLabels(false)
                setTouchEnabled(false)
            }
            invalidate()
        }
        if (isChartDataPresent) {
            selectedValueTextView?.text = null
        } else {
            selectedValueTextView?.setText(R.string.label_chart_no_data)
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
        when (item.itemId) {
            R.id.menu_toggle_legend -> {
                item.isChecked = !item.isChecked
                showLegend(item.isChecked)
                return true
            }

            R.id.menu_toggle_average_lines -> {
                item.isChecked = !item.isChecked
                showAverageLines(item.isChecked)
                return true
            }

            else -> return super.onOptionsItemSelected(item)
        }
    }

    override fun onValueSelected(e: Entry?, h: Highlight) {
        val binding = binding ?: return
        if (e == null) return
        val value = e.y
        val dataSetIndex = h.dataSetIndex
        val data = binding.lineChart.data
        val dataSet = data.getDataSetByIndex(dataSetIndex)
        if (dataSet == null) return
        val label = dataSet.label
        val total = getYValueSum<Entry>(dataSet)
        val percent = if (total != 0f) ((value * 100) / total) else 0f
        selectedValueTextView?.text = formatSelectedValue(label, value, percent)
    }

    private fun showLegend(isVisible: Boolean) {
        val binding = binding ?: return
        binding.lineChart.legend.isEnabled = isVisible
        binding.lineChart.invalidate()
    }

    private fun showAverageLines(isVisible: Boolean) {
        val binding = binding ?: return
        binding.lineChart.axisLeft.removeAllLimitLines()
        if (isVisible) {
            for (dataSet in binding.lineChart.data.dataSets) {
                val entryCount = dataSet.entryCount
                var limit = 0f
                if (entryCount > 0) {
                    limit = dataSet.yMin + (getYValueSum<Entry>(dataSet) / entryCount)
                }
                val line = LimitLine(limit, dataSet.label)
                line.enableDashedLine(10f, 5f, 0f)
                line.lineColor = dataSet.color
                binding.lineChart.axisLeft.addLimitLine(line)
            }
        }
        binding.lineChart.invalidate()
    }

    companion object {
        private const val ANIMATION_DURATION = 3000
        private const val NO_DATA_BAR_COUNTS = 5
        private val LINE_COLORS = intArrayOf(
            parseColor("#68F1AF")!!, parseColor("#cc1f09")!!, parseColor("#EE8600")!!,
            parseColor("#1469EB")!!, parseColor("#B304AD")!!,
        )
        private val FILL_COLORS = intArrayOf(
            parseColor("#008000")!!, parseColor("#FF0000")!!, parseColor("#BE6B00")!!,
            parseColor("#0065FF")!!, parseColor("#8F038A")!!,
        )
    }
}
