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
package org.gnucash.android.ui.report.piechart

import android.content.Context
import android.graphics.Color
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.annotation.ColorInt
import androidx.core.view.get
import androidx.core.view.isEmpty
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.highlight.Highlight
import org.gnucash.android.R
import org.gnucash.android.databinding.FragmentChartBinding
import org.gnucash.android.db.DatabaseSchema.AccountEntry
import org.gnucash.android.db.adapter.AccountsDbAdapter
import org.gnucash.android.model.isNullOrZero
import org.gnucash.android.ui.report.BaseReportFragment
import org.gnucash.android.ui.report.ReportType
import org.gnucash.android.util.toMillis

/**
 * Activity used for drawing a pie chart
 *
 * @author Oleksandr Tyshkovets <olexandr.tyshkovets@gmail.com>
 * @author Ngewi Fet <ngewif@gmail.com>
 */
class PieChartFragment : BaseReportFragment<PieData>() {

    private var groupSmallerSlices = true

    private var binding: FragmentChartBinding? = null
    private var chart: PieChart? = null

    override val reportType: ReportType = ReportType.PIE_CHART

    override fun inflateView(inflater: LayoutInflater, container: ViewGroup?): View {
        val binding = FragmentChartBinding.inflate(inflater, container, false)
        this.binding = binding
        this.selectedValueTextView = binding.selectedChartSlice
        return binding.root
    }

    override fun generateReport(context: Context): PieData {
        val data = getData(context)
        if (isEmpty(data)) {
            return getEmptyData(context)
        }
        return data
    }

    private fun getData(context: Context): PieData {
        val dataSet = PieDataSet(null, "")
        val colors = mutableListOf<Int>()
        val startTime = reportPeriodStart?.toMillis() ?: AccountsDbAdapter.Companion.ALWAYS
        val endTime = reportPeriodEnd?.toMillis() ?: AccountsDbAdapter.Companion.ALWAYS
        val commodity = this.commodity

        val where = (AccountEntry.COLUMN_TYPE + "=?"
                + " AND " + AccountEntry.COLUMN_PLACEHOLDER + " = 0"
                + " AND " + AccountEntry.COLUMN_TEMPLATE + " = 0")
        val whereArgs = arrayOf<String?>(accountType.name)
        val orderBy = AccountEntry.COLUMN_FULL_NAME + " ASC"
        val accounts = accountsDbAdapter.getAllRecords(where, whereArgs, orderBy)
        val balances = accountsDbAdapter.getAccountsBalances(accounts, startTime, endTime)

        for (account in accounts) {
            var balance = balances[account.uid]
            if (balance.isNullOrZero()) continue
            val price = pricesDbAdapter.getPrice(balance.commodity, commodity) ?: continue
            balance *= price
            val value = balance.toFloat()
            if (value > 0f) {
                val count = dataSet.entryCount
                @ColorInt val color = getAccountColor(account, count)
                dataSet.addEntry(PieEntry(value, account.name))
                colors.add(color)
            }
        }
        dataSet.colors = colors
        dataSet.setSliceSpace(SPACE_BETWEEN_SLICES)
        val data = PieData(dataSet)

        return if (groupSmallerSlices) {
            groupSmallerSlices(context, data)
        } else {
            data
        }
    }

    override fun displayReport(data: PieData) {
        val binding = binding ?: return
        val context = binding.root.context
        val selectedValueTextView = binding.selectedChartSlice
        @ColorInt val textColorPrimary = getTextColor(context)

        val chart = PieChart(context).apply {
            id = R.id.chart
            setCenterTextSize(CENTER_TEXT_SIZE.toFloat())
            setCenterTextColor(textColorPrimary)
            setOnChartValueSelectedListener(this@PieChartFragment)
            isDrawHoleEnabled = false
            legend.apply {
                isWordWrapEnabled = true
                textColor = textColorPrimary
            }
            this.data = data

            if (isEmpty(data)) {
                selectedValueTextView.text = null
                centerText = context.getString(R.string.label_chart_no_data)
                setTouchEnabled(false)
                clearAnimation()
            } else {
                selectedValueTextView.setText(R.string.label_select_pie_slice_to_see_details)
                centerText = formatTotalValue(data.yValueSum)
                setTouchEnabled(true)
                animateXY(ANIMATION_DURATION, ANIMATION_DURATION)
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

    /**
     * Returns a data object that represents situation when no user data available
     *
     * @return a `PieData` instance for situation when no user data available
     */
    private fun getEmptyData(context: Context): PieData {
        val dataSet = PieDataSet(null, context.getString(R.string.label_chart_no_data))
        dataSet.addEntry(PieEntry(DATA_EMPTY, 0))
        dataSet.setColor(NO_DATA_COLOR)
        dataSet.setDrawValues(false)
        return PieData(dataSet)
    }

    private fun isEmpty(data: PieData): Boolean {
        return (data.dataSetCount == 0) ||
                (data.entryCount == 0) ||
                (data.dataSet.entryCount == 0) ||
                ((data.yMin <= DATA_EMPTY) && (data.yMax <= DATA_EMPTY))
    }

    /**
     * Sorts the pie's slices in ascending order
     */
    private fun sort() {
        val binding = binding ?: return
        val chartContainer = binding.chartContainer
        if (chartContainer.isEmpty()) return
        val pieChart = chartContainer[0] as PieChart
        val data = pieChart.data
        val dataSet = data.getDataSetByIndex(0) as PieDataSet
        val size = dataSet.entryCount
        val entries = mutableListOf<PieChartEntry>()
        for (i in 0 until size) {
            entries.add(PieChartEntry(dataSet.getEntryForIndex(i), dataSet.getColor(i)))
        }
        entries.sortWith(PieChartComparator())
        val colors = mutableListOf<Int>()
        for (i in 0 until size) {
            val entry = entries[i]
            dataSet.removeFirst()
            dataSet.addEntry(entry.entry)
            colors.add(entry.color)
        }
        dataSet.colors = colors

        pieChart.notifyDataSetChanged()
        pieChart.highlightValues(null)
        pieChart.invalidate()
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        super.onPrepareOptionsMenu(menu)

        val pieChart = chart
        val chartDataPresent = (pieChart != null) &&
                (pieChart.data.dataSetCount > 0) &&
                (pieChart.data.dataSet.entryCount > 0)
        menu.findItem(R.id.menu_order_by_size).isVisible = chartDataPresent
        menu.findItem(R.id.menu_toggle_labels).isVisible = chartDataPresent
        menu.findItem(R.id.menu_group_other_slice).isVisible = chartDataPresent
        menu.findItem(R.id.menu_group_other_slice).isChecked = groupSmallerSlices
        // hide line/bar chart specific menu items
        menu.findItem(R.id.menu_percentage_mode).isVisible = false
        menu.findItem(R.id.menu_toggle_average_lines).isVisible = false
        menu.findItem(R.id.menu_group_reports_by).isVisible = false
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.isCheckable) item.isChecked = !item.isChecked
        when (item.itemId) {
            R.id.menu_order_by_size -> {
                sort()
                return true
            }

            R.id.menu_toggle_legend -> {
                val pieChart = chart ?: return false
                pieChart.legend.isEnabled = !pieChart.legend.isEnabled
                pieChart.notifyDataSetChanged()
                pieChart.invalidate()
                return true
            }

            R.id.menu_toggle_labels -> {
                val pieChart = chart ?: return false
                val draw = !pieChart.isDrawEntryLabelsEnabled
                pieChart.data.setDrawValues(draw)
                pieChart.setDrawEntryLabels(draw)
                pieChart.invalidate()
                return true
            }

            R.id.menu_group_other_slice -> {
                groupSmallerSlices = !groupSmallerSlices
                refresh()
                return true
            }

            else -> return super.onOptionsItemSelected(item)
        }
    }

    override fun onValueSelected(e: Entry?, h: Highlight) {
        val chart = chart ?: return
        if (e == null) return
        val entry = e as PieEntry
        val label = entry.label ?: return
        val value = entry.value
        val data = chart.data
        val total = data.getYValueSum()
        val percent = if (total != 0f) ((value * 100) / total) else 0f
        selectedValueTextView?.text = formatSelectedValue(label, value, percent)
    }

    companion object {
        private const val ANIMATION_DURATION = 1800
        const val CENTER_TEXT_SIZE: Int = 18

        /**
         * The space in degrees between the chart slices
         */
        const val SPACE_BETWEEN_SLICES: Float = 2f

        /**
         * All pie slices less than this threshold will be group in "other" slice. Using percents not absolute values.
         */
        private const val GROUPING_SMALLER_SLICES_THRESHOLD = 5.0


        /**
         * Groups smaller slices. All smaller slices will be combined and displayed as a single "Other".
         *
         * @param context Context for retrieving resources
         * @param data    the pie data which smaller slices will be grouped
         * @return a `PieData` instance with combined smaller slices
         */
        fun groupSmallerSlices(context: Context, data: PieData): PieData {
            val dataSet = data.getDataSetByIndex(0) as PieDataSet
            val size = dataSet.entryCount
            if (size == 0) return data
            val range = data.getYValueSum()
            if (range <= 0) return data

            var otherSlice = 0f
            val entriesSmaller = mutableListOf<PieEntry>()
            val colorsSmaller = mutableListOf<Int>()

            for (i in 0 until size) {
                val entry = dataSet.getEntryForIndex(i)
                val value = entry.value
                if ((value * 100) / range > GROUPING_SMALLER_SLICES_THRESHOLD) {
                    entriesSmaller.add(entry)
                    colorsSmaller.add(dataSet.getColor(i))
                } else {
                    otherSlice += value
                }
            }

            if (otherSlice > 0) {
                entriesSmaller.add(
                    PieEntry(otherSlice, context.getString(R.string.label_other_slice))
                )
                colorsSmaller.add(Color.LTGRAY)
            }

            val dataSetSmaller = PieDataSet(entriesSmaller, "")
            dataSetSmaller.setSliceSpace(SPACE_BETWEEN_SLICES)
            dataSetSmaller.colors = colorsSmaller

            return PieData(dataSetSmaller)
        }
    }
}
