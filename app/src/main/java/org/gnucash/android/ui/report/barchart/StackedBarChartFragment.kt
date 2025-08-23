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
package org.gnucash.android.ui.report.barchart

import android.content.Context
import android.os.Bundle
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.annotation.ColorInt
import androidx.annotation.StringRes
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.formatter.LargeValueFormatter
import com.github.mikephil.charting.highlight.Highlight
import org.gnucash.android.R
import org.gnucash.android.databinding.FragmentBarChartBinding
import org.gnucash.android.db.DatabaseSchema.AccountEntry
import org.gnucash.android.model.isNullOrZero
import org.gnucash.android.ui.report.IntervalReportFragment
import org.gnucash.android.ui.report.ReportType
import org.gnucash.android.ui.report.ReportsActivity.GroupInterval
import org.gnucash.android.util.getFirstQuarterMonth
import org.gnucash.android.util.toMillis
import org.joda.time.LocalDateTime
import timber.log.Timber

/**
 * Activity used for drawing a bar chart
 *
 * @author Oleksandr Tyshkovets <olexandr.tyshkovets@gmail.com>
 * @author Ngewi Fet <ngewif@gmail.com>
 */
class StackedBarChartFragment : IntervalReportFragment() {
    private var totalPercentageMode = true

    private var binding: FragmentBarChartBinding? = null

    override fun inflateView(inflater: LayoutInflater, container: ViewGroup?): View {
        val binding = FragmentBarChartBinding.inflate(inflater, container, false)
        this.binding = binding
        return binding.root
    }

    override val reportType: ReportType = ReportType.BAR_CHART

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val context = view.context
        val binding = binding!!

        @ColorInt val textColorPrimary = getTextColor(context)

        binding.barChart.apply {
            setOnChartValueSelectedListener(this@StackedBarChartFragment)
            xAxis.setDrawGridLines(false)
            xAxis.textColor = textColorPrimary
            axisRight.isEnabled = false
            axisLeft.setStartAtZero(false)
            axisLeft.enableGridDashedLine(4.0f, 4.0f, 0f)
            axisLeft.valueFormatter = LargeValueFormatter(commodity.symbol)
            axisLeft.textColor = textColorPrimary
            legend.apply {
                textColor = textColorPrimary
                isWordWrapEnabled = true
            }
        }
    }

    /**
     * Returns a data object that represents a user data of the specified account types
     *
     * @return a `BarData` instance that represents a user data
     */
    private fun getData(context: Context): BarData {
        val entries = mutableListOf<BarEntry>()
        val stackLabels = mutableListOf<String>()
        val colors = mutableListOf<Int>()
        val accountToColorMap: MutableMap<String, Int> = LinkedHashMap<String, Int>()
        val groupInterval = this.groupInterval
        val accountType = this.accountType

        calculateEarliestAndLatestTimestamps(accountTypes)
        var startDate = reportPeriodStart
        if (startDate == null) {
            val startTime = earliestTimestamps[accountType]
            if (startTime != null) {
                startDate = LocalDateTime(startTime)
            } else {
                isChartDataPresent = false
                return getEmptyData(context)
            }
        }
        var endDate = reportPeriodEnd
        if (endDate == null) {
            val endTime = latestTimestamps[accountType]
            endDate = if (endTime != null) {
                LocalDateTime(endTime)
            } else {
                LocalDateTime.now()
            }
        }

        var startPeriod: LocalDateTime = startDate
        var endPeriod = endDate!!
        when (groupInterval) {
            GroupInterval.MONTH -> endPeriod = startPeriod.plusMonths(1)
            GroupInterval.QUARTER -> {
                startPeriod = startPeriod.withMonthOfYear(startPeriod.getFirstQuarterMonth())
                    .dayOfMonth().withMinimumValue()
                endPeriod = startPeriod.plusMonths(3)
            }

            GroupInterval.YEAR -> endPeriod = startPeriod.plusYears(1)
            else -> Unit
        }
        val count = getDateDiff(groupInterval, startDate, endDate)

        val where = (AccountEntry.COLUMN_TYPE + "=?"
                + " AND " + AccountEntry.COLUMN_PLACEHOLDER + " = 0"
                + " AND " + AccountEntry.COLUMN_TEMPLATE + " = 0")
        val whereArgs = arrayOf<String?>(accountType.name)
        val orderBy = AccountEntry.COLUMN_FULL_NAME + " ASC"
        val accounts = accountsDbAdapter.getSimpleAccounts(where, whereArgs, orderBy)

        for (i in 0 until count) {
            val startTime = startPeriod.toMillis()
            val endTime = endPeriod.toMillis()
            val stack = mutableListOf<Float>()
            val labels = mutableListOf<String>()
            val balances = accountsDbAdapter.getAccountsBalances(accounts, startTime, endTime)

            for (account in accounts) {
                var balance = balances[account.uid]
                if (balance.isNullOrZero()) continue
                Timber.d(
                    "%s %s [%s] %s - %s %s",
                    accountType,
                    groupInterval,
                    account,
                    startPeriod,
                    endPeriod,
                    balance
                )
                val price = pricesDbAdapter.getPrice(balance.commodity, commodity)
                if (price == null) continue
                balance *= price
                val value = balance.toFloat()
                if (value > 0f) {
                    stack.add(value)

                    val accountName = account.name
                    labels.add(accountName)

                    val accountUID = account.uid
                    @ColorInt val color: Int
                    if (accountToColorMap.containsKey(accountUID)) {
                        color = accountToColorMap[accountUID]!!
                    } else {
                        color = getAccountColor(account, colors.size)
                        accountToColorMap.put(accountUID, color)
                    }
                    colors.add(color)
                }
            }

            startPeriod = endPeriod
            when (groupInterval) {
                GroupInterval.MONTH -> endPeriod = endPeriod.plusMonths(1)
                GroupInterval.QUARTER -> endPeriod = endPeriod.plusMonths(3)
                GroupInterval.YEAR -> endPeriod = endPeriod.plusYears(1)
                else -> Unit
            }

            if (stack.isEmpty()) {
                stack.add(0f)
            }
            if (labels.isEmpty()) {
                labels.add("")
            }
            entries.add(BarEntry(i.toFloat(), stack.toFloatArray(), labels))
            stackLabels.addAll(labels)
        }

        val dataSet = BarDataSet(entries, getLabel(context, accountType))
        dataSet.setDrawValues(false)
        dataSet.stackLabels = stackLabels.toTypedArray<String>()
        dataSet.colors = colors

        if ((dataSet.entryCount == 0) || (getYValueSum<BarEntry>(dataSet) == 0f)) {
            isChartDataPresent = false
            return getEmptyData(context)
        }
        isChartDataPresent = true
        return BarData(dataSet)
    }

    /**
     * Returns a data object that represents situation when no user data available
     *
     * @return a `BarData` instance for situation when no user data available
     */
    private fun getEmptyData(context: Context): BarData {
        val yValues = mutableListOf<BarEntry>()
        for (i in 0 until NO_DATA_BAR_COUNTS) {
            yValues.add(BarEntry(i.toFloat(), (i + 1).toFloat()))
        }
        val dataSet = BarDataSet(yValues, context.getString(R.string.label_chart_no_data))
        dataSet.setDrawValues(false)
        dataSet.color = NO_DATA_COLOR

        return BarData(dataSet)
    }

    override fun generateReport(context: Context) {
        val binding = binding ?: return
        binding.barChart.data = getData(context)
        binding.barChart.axisLeft.setDrawLabels(isChartDataPresent)
        binding.barChart.xAxis.setDrawLabels(isChartDataPresent)
        binding.barChart.setTouchEnabled(isChartDataPresent)
    }

    override fun displayReport() {
        val binding = binding ?: return
        binding.barChart.apply {
            notifyDataSetChanged()
            highlightValues(null)
            if (isChartDataPresent) {
                animateY(ANIMATION_DURATION)
            } else {
                clearAnimation()
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
        menu.findItem(R.id.menu_percentage_mode).isVisible = isChartDataPresent
        // hide pie/line chart specific menu items
        menu.findItem(R.id.menu_order_by_size).isVisible = false
        menu.findItem(R.id.menu_toggle_labels).isVisible = false
        menu.findItem(R.id.menu_toggle_average_lines).isVisible = false
        menu.findItem(R.id.menu_group_other_slice).isVisible = false
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.isCheckable) {
            item.isChecked = !item.isChecked
        }
        val binding = binding ?: return false
        val context = binding.barChart.context
        when (item.itemId) {
            R.id.menu_toggle_legend -> {
                val legend = binding.barChart.legend
                if (!legend.isLegendCustom) {
                    Toast.makeText(context, R.string.toast_legend_too_long, Toast.LENGTH_LONG)
                        .show()
                    item.isChecked = false
                } else {
                    item.isChecked = !legend.isEnabled
                    legend.isEnabled = !legend.isEnabled
                    binding.barChart.invalidate()
                }
                return true
            }

            R.id.menu_percentage_mode -> {
                totalPercentageMode = !totalPercentageMode
                @StringRes val msgId = if (totalPercentageMode)
                    R.string.toast_chart_percentage_mode_total
                else
                    R.string.toast_chart_percentage_mode_current_bar
                Toast.makeText(context, msgId, Toast.LENGTH_LONG).show()
                return true
            }

            else -> return super.onOptionsItemSelected(item)
        }
    }

    override fun onValueSelected(e: Entry?, h: Highlight) {
        val binding = binding ?: return
        if (e == null) return
        val entry = e as BarEntry
        var index = h.stackIndex
        if ((index < 0) && (entry.yVals.size > 0)) {
            index = 0
        }
        val value = entry.yVals[index]
        val labels = entry.data as List<String>
        if (labels.isEmpty()) return
        val label = labels[index]

        val total: Float
        if (totalPercentageMode) {
            val data = binding.barChart.data
            val dataSetIndex = h.dataSetIndex
            val dataSet = data.getDataSetByIndex(dataSetIndex)
            total = getYValueSum<BarEntry>(dataSet)
        } else {
            total = entry.negativeSum + entry.positiveSum
        }
        val percentage = if (total != 0f) ((value * 100) / total) else 0f
        selectedValueTextView?.text = formatSelectedValue(label, value, percentage)
    }

    companion object {
        private const val ANIMATION_DURATION = DateUtils.SECOND_IN_MILLIS.toInt()
        private const val NO_DATA_BAR_COUNTS = 3
    }
}
