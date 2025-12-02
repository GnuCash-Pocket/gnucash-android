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
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import androidx.annotation.ColorInt
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import org.gnucash.android.R
import org.gnucash.android.databinding.FragmentReportSummaryBinding
import org.gnucash.android.db.DatabaseSchema.AccountEntry
import org.gnucash.android.model.AccountType
import org.gnucash.android.model.Money
import org.gnucash.android.model.isNullOrZero
import org.gnucash.android.ui.report.piechart.PieChartFragment
import org.gnucash.android.ui.util.displayBalance
import org.gnucash.android.util.toMillis
import org.joda.time.LocalDateTime

/**
 * Shows a summary of reports
 *
 * @author Ngewi Fet <ngewif@gmail.com>
 */
class ReportsOverviewFragment : BaseReportFragment<PieData>() {
    private var assetsBalance: Money = Money.createZeroInstance(commodity)
    private var liabilitiesBalance: Money = Money.createZeroInstance(commodity)

    private var binding: FragmentReportSummaryBinding? = null

    @ColorInt
    private var colorBalanceZero = Color.TRANSPARENT

    override fun inflateView(inflater: LayoutInflater, container: ViewGroup?): View {
        val binding = FragmentReportSummaryBinding.inflate(inflater, container, false)
        this.binding = binding
        return binding.root
    }

    override val reportType: ReportType = ReportType.NONE

    override fun requiresAccountTypeOptions(): Boolean {
        return false
    }

    override fun requiresTimeRangeOptions(): Boolean {
        return false
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = binding!!

        setHasOptionsMenu(false)

        colorBalanceZero = binding.totalAssets.currentTextColor

        binding.btnBarChart.setOnClickListener(this::onClickChartTypeButton)
        binding.btnPieChart.setOnClickListener(this::onClickChartTypeButton)
        binding.btnLineChart.setOnClickListener(this::onClickChartTypeButton)
        binding.btnBalanceSheet.setOnClickListener(this::onClickChartTypeButton)

        val chartLayout = binding.chartLayout!!
        chartLayout.selectedChartSlice.setText(R.string.label_last_3_months_expenses)
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        super.onPrepareOptionsMenu(menu)
        menu.findItem(R.id.menu_group_reports_by).isVisible = false
    }

    override fun generateReport(context: Context): PieData {
        assetsBalance = accountsDbAdapter.getCurrentAccountsBalance(assetTypes, commodity)
        liabilitiesBalance = accountsDbAdapter.getCurrentAccountsBalance(liabilityTypes, commodity)

        val data = getData()
        if (isEmpty(data)) {
            return getEmptyData(context)
        }
        return PieChartFragment.groupSmallerSlices(context, data)
    }

    private fun getData(): PieData {
        val dataSet = PieDataSet(null, "")
        val colors = mutableListOf<Int>()
        val now = LocalDateTime.now()
        val startTime = now.minusMonths(3).toMillis()
        val endTime = now.toMillis()
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
                dataSet.addEntry(PieEntry(value, account.name))
                @ColorInt val color = getAccountColor(account, count)
                colors.add(color)
            }
        }
        dataSet.colors = colors
        dataSet.setSliceSpace(PieChartFragment.SPACE_BETWEEN_SLICES)
        return PieData(dataSet)
    }

    override fun displayReport(data: PieData) {
        val binding = binding ?: return
        val context = binding.root.context
        @ColorInt val textColorPrimary = getTextColor(context)

        val chart = PieChart(context).apply {
            setCenterTextSize(PieChartFragment.CENTER_TEXT_SIZE.toFloat())
            setDrawSliceText(false)
            setCenterTextColor(textColorPrimary)
            setHoleColor(Color.TRANSPARENT)
            legend.apply {
                isWordWrapEnabled = true
                textColor = textColorPrimary
            }
            this.data = data

            if (isEmpty(data)) {
                centerText = context.getString(R.string.label_chart_no_data)
                legend.isEnabled = false
                setTouchEnabled(false)
                clearAnimation()
            } else {
                centerText = formatTotalValue(data.yValueSum)
                legend.isEnabled = true
                setTouchEnabled(true)
                animateXY(ANIMATION_DURATION, ANIMATION_DURATION)
            }
            highlightValues(null)
        }

        val chartLayout = binding.chartLayout
        chartLayout.chartContainer.removeAllViews()
        chartLayout.chartContainer.addView(
            chart,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        val totalAssets = assetsBalance
        val totalLiabilities = -liabilitiesBalance
        val netWorth = totalAssets + totalLiabilities
        binding.totalAssets.displayBalance(totalAssets, colorBalanceZero)
        binding.totalLiabilities.displayBalance(totalLiabilities, colorBalanceZero)
        binding.netWorth.displayBalance(netWorth, colorBalanceZero)
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

    fun onClickChartTypeButton(view: View) {
        val reportType = when (view.id) {
            R.id.btn_pie_chart -> ReportType.PIE_CHART
            R.id.btn_bar_chart -> ReportType.BAR_CHART
            R.id.btn_line_chart -> ReportType.LINE_CHART
            R.id.btn_balance_sheet -> ReportType.SHEET
            else -> ReportType.NONE
        }

        reportsActivity.showReport(reportType)
    }

    companion object {
        private const val ANIMATION_DURATION = 1500
        private val assetTypes = listOf<AccountType>(
            AccountType.ASSET,
            AccountType.CASH,
            AccountType.BANK
        )
        private val liabilityTypes = listOf<AccountType>(
            AccountType.LIABILITY,
            AccountType.CREDIT
        )
    }
}
