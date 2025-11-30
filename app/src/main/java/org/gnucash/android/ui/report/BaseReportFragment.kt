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

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.os.AsyncTask
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.annotation.StringRes
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.ActionBar
import androidx.core.view.isVisible
import androidx.preference.PreferenceManager
import com.github.mikephil.charting.data.ChartData
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.interfaces.datasets.IDataSet
import com.github.mikephil.charting.listener.OnChartValueSelectedListener
import org.gnucash.android.R
import org.gnucash.android.app.MenuFragment
import org.gnucash.android.app.actionBar
import org.gnucash.android.db.adapter.AccountsDbAdapter
import org.gnucash.android.db.adapter.CommoditiesDbAdapter
import org.gnucash.android.db.adapter.PricesDbAdapter
import org.gnucash.android.model.Account
import org.gnucash.android.model.AccountType
import org.gnucash.android.model.Commodity
import org.gnucash.android.ui.common.BaseDrawerActivity
import org.gnucash.android.ui.common.Refreshable
import org.gnucash.android.ui.report.ReportsActivity.GroupInterval
import org.gnucash.android.util.getFirstQuarterMonth
import org.gnucash.android.util.parseColor
import org.gnucash.android.util.textColorPrimary
import org.joda.time.LocalDateTime
import org.joda.time.Months
import org.joda.time.Years
import java.lang.ref.WeakReference
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.max

/**
 * Base class for report fragments.
 *
 * All report fragments should extend this class. At the minimum, reports must implement
 * [.getReportType], [.generateReport], [.displayReport] and [.getTitle]
 *
 * Implementing classes should create their own XML layouts and inflate it in [.inflateView].
 *
 *
 * Any custom information to be initialized for the report should be done in [.onActivityCreated] in implementing classes.
 * The report is then generated in [.onStart]
 *
 *
 * @author Ngewi Fet <ngewif@gmail.com>
 */
abstract class BaseReportFragment : MenuFragment(),
    OnChartValueSelectedListener,
    ReportOptionsListener,
    Refreshable {
    /**
     * Reporting period start time
     */
    protected var reportPeriodStart: LocalDateTime? = null

    /**
     * Reporting period end time
     */
    protected var reportPeriodEnd: LocalDateTime? = null

    /**
     * Account type for which to display reports
     */
    protected var accountType: AccountType = AccountType.EXPENSE
    protected var commoditiesDbAdapter: CommoditiesDbAdapter = CommoditiesDbAdapter.instance
    protected var accountsDbAdapter: AccountsDbAdapter = AccountsDbAdapter.instance
    protected var pricesDbAdapter: PricesDbAdapter = PricesDbAdapter.instance
    protected var useAccountColor: Boolean = true

    /**
     * Commodity for which to display reports
     */
    protected var commodity: Commodity = commoditiesDbAdapter.defaultCommodity

    /**
     * Intervals in which to group reports
     */
    protected var groupInterval: GroupInterval = GroupInterval.MONTH

    protected lateinit var reportsActivity: ReportsActivity

    protected var selectedValueTextView: TextView? = null

    private var generatorTask: GeneratorTask? = null

    /**
     * Returns what kind of report this is
     *
     * @return Type of report
     */
    abstract val reportType: ReportType

    /**
     * Return the title of this report
     *
     * @return Title string identifier
     */
    @get:StringRes
    val title: Int
        get() = this.reportType.titleId

    /**
     * Return `true` if this report fragment requires account type options.
     *
     * Sub-classes should implement this method. The base implementation returns `true`
     *
     * @return `true` if the fragment makes use of account type options, `false` otherwise
     */
    open fun requiresAccountTypeOptions(): Boolean {
        return true
    }

    /**
     * Return `true` if this report fragment requires time range options.
     *
     * Base implementation returns true
     *
     * @return `true` if the report fragment requires time range options, `false` otherwise
     */
    open fun requiresTimeRangeOptions(): Boolean {
        return true
    }

    /**
     * Generates the data for the report
     *
     * This method should not call any methods which modify the UI as it will be run in a background thread
     * <br></br>Put any code to update the UI in [.displayReport]
     *
     */
    protected abstract fun generateReport(context: Context)

    /**
     * Update the view after the report chart has been generated <br></br>
     * Sub-classes should call to the base method
     */
    protected abstract fun displayReport()

    protected abstract fun inflateView(inflater: LayoutInflater, container: ViewGroup?): View

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflateView(inflater, container)
        selectedValueTextView = view.findViewById<TextView>(R.id.selected_chart_slice)
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val actionBar: ActionBar? = this.actionBar
        actionBar?.setTitle(this.title)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        accountsDbAdapter = AccountsDbAdapter.instance
        useAccountColor = PreferenceManager.getDefaultSharedPreferences(requireContext())
            .getBoolean(getString(R.string.key_use_account_color), false)
    }

    override fun onStart() {
        super.onStart()
        accountsDbAdapter = AccountsDbAdapter.instance
        commoditiesDbAdapter = accountsDbAdapter.commoditiesDbAdapter
        pricesDbAdapter = PricesDbAdapter.instance
        commodity = commoditiesDbAdapter.defaultCommodity
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        val reportsActivity = requireActivity() as ReportsActivity
        reportPeriodStart = reportsActivity.reportPeriodStart
        reportPeriodEnd = reportsActivity.reportPeriodEnd
        accountType = reportsActivity.accountType
    }

    override fun onResume() {
        super.onResume()

        val activity: Activity? = activity
        if (activity is ReportsActivity) {
            reportsActivity = activity
        } else {
            throw RuntimeException("Report fragments can only be used with the ReportsActivity")
        }
        reportsActivity.onFragmentResumed(this)
        toggleBaseReportingOptionsVisibility(reportsActivity)
        refresh()
    }

    override fun onDetach() {
        super.onDetach()
        if (generatorTask != null) generatorTask!!.cancel(true)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (generatorTask != null) {
            generatorTask!!.cancel(true)
            generatorTask = null
        }
    }

    private fun toggleBaseReportingOptionsVisibility(activity: ReportsActivity) {
        val timeRangeLayout = activity.findViewById<View?>(R.id.time_range_layout)
        timeRangeLayout?.isVisible = requiresTimeRangeOptions()
    }

    /**
     * Calculates difference between two date values accordingly to `mGroupInterval`
     *
     * @param start start date
     * @param end   end date
     * @return difference between two dates or `-1`
     */
    protected fun getDateDiff(
        groupInterval: GroupInterval,
        start: LocalDateTime,
        end: LocalDateTime
    ): Int {
        var start = start
        var end = end
        start = start.withMillisOfDay(0)
        end = end.withMillisOfDay(0)
        when (groupInterval) {
            GroupInterval.MONTH -> return max(1, Months.monthsBetween(start, end).months)

            GroupInterval.QUARTER -> {
                start = start.withMonthOfYear(start.getFirstQuarterMonth())
                    .dayOfMonth().withMinimumValue()
                val m = Months.monthsBetween(start, end).months
                var q = m / 3
                if (m % 3 > 0) q++
                return max(1, q)
            }

            GroupInterval.YEAR -> return max(1, Years.yearsBetween(start, end).years)

            else -> return -1
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.chart_actions, menu)
    }

    override fun refresh() {
        generatorTask?.cancel(true)
        val task = GeneratorTask(reportsActivity)
        generatorTask = task
        task.execute()
    }

    /**
     * Charts do not support account specific refreshes in general.
     * So we provide a base implementation which just calls [.refresh]
     *
     * @param uid GUID of relevant item to be refreshed
     */
    override fun refresh(uid: String?) {
        refresh()
    }

    override fun onGroupingUpdated(groupInterval: GroupInterval) {
        if (this.groupInterval != groupInterval) {
            this.groupInterval = groupInterval
            refresh()
        }
    }

    override fun onTimeRangeUpdated(start: LocalDateTime?, end: LocalDateTime?) {
        reportPeriodStart = start
        reportPeriodEnd = end
        refresh()
    }

    override fun onAccountTypeUpdated(accountType: AccountType) {
        if (this.accountType != accountType) {
            this.accountType = accountType
            refresh()
        }
    }

    override fun onValueSelected(e: Entry?, h: Highlight) {
        //nothing to see here, move along
    }

    override fun onNothingSelected() {
        selectedValueTextView?.setText(R.string.select_chart_to_view_details)
    }

    protected fun formatSelectedValue(label: String, value: Float, percentage: Float): String {
        return formatSelectedValue(
            Locale.getDefault(),
            label.trim(),
            value,
            commodity,
            percentage
        )
    }

    protected fun formatTotalValue(value: Float): String {
        return formatTotalValue(requireContext(), Locale.getDefault(), value, commodity)
    }

    protected fun getLabel(context: Context, accountType: AccountType): String? {
        val labels = context.resources.getStringArray(R.array.account_type_entry_values)
        return labels[accountType.labelIndex]
    }

    @ColorInt
    protected fun getTextColor(context: Context): Int {
        return context.textColorPrimary
    }

    @ColorInt
    protected fun getAccountColor(account: Account, count: Int): Int {
        @ColorInt val color: Int = if (useAccountColor) {
            if (account.color != Account.DEFAULT_COLOR)
                account.color
            else
                COLORS[count % COLORS.size]
        } else {
            COLORS[count % COLORS.size]
        }
        return color
    }

    private inner class GeneratorTask(activity: ReportsActivity) :
        AsyncTask<Any, Any, Any>() {
        private val activityRef: WeakReference<ReportsActivity> =
            WeakReference<ReportsActivity>(activity)

        override fun onPreExecute() {
            val activity: BaseDrawerActivity = activityRef.get() ?: return
            activity.showProgressBar(true)
        }

        override fun doInBackground(vararg params: Any?): Any? {
            val activity: BaseDrawerActivity = activityRef.get() ?: return null
            // FIXME return data to be displayed.
            generateReport(activity)
            return Unit
        }

        override fun onPostExecute(result: Any?) {
            val activity: BaseDrawerActivity = activityRef.get() ?: return
            // FIXME display the result data that was generated.
            displayReport()
            activity.showProgressBar(false)
        }
    }

    companion object {
        /**
         * Color for chart with no data
         */
        const val NO_DATA_COLOR: Int = Color.LTGRAY

        protected val COLORS: IntArray = intArrayOf(
            parseColor("#17ee4e")!!, parseColor("#cc1f09")!!, parseColor("#3940f7")!!,
            parseColor("#f9cd04")!!, parseColor("#5f33a8")!!, parseColor("#e005b6")!!,
            parseColor("#17d6ed")!!, parseColor("#e4a9a2")!!, parseColor("#8fe6cd")!!,
            parseColor("#8b48fb")!!, parseColor("#343a36")!!, parseColor("#6decb1")!!,
            parseColor("#f0f8ff")!!, parseColor("#5c3378")!!, parseColor("#a6dcfd")!!,
            parseColor("#ba037c")!!, parseColor("#708809")!!, parseColor("#32072c")!!,
            parseColor("#fddef8")!!, parseColor("#fa0e6e")!!, parseColor("#d9e7b5")!!
        )

        /**
         * Pattern to use to display selected chart values
         */
        private const val SELECTED_VALUE_PATTERN = "%s — %s %s (%.2f%%)"
        private const val TOTAL_VALUE_LABEL_PATTERN = "%s\n%s %s"

        @VisibleForTesting
        fun formatSelectedValue(
            locale: Locale,
            label: String,
            value: Float,
            commodity: Commodity,
            percentage: Float
        ): String {
            val formatter = NumberFormat.getNumberInstance(locale)
            formatter.setMinimumFractionDigits(0)
            formatter.setMaximumFractionDigits(commodity.smallestFractionDigits)
            val currencySymbol = commodity.symbol
            return String.format(
                locale,
                SELECTED_VALUE_PATTERN,
                label.trim(),
                formatter.format(value.toDouble()),
                currencySymbol,
                percentage
            )
        }

        @VisibleForTesting //TODO get locale from context.
        fun formatTotalValue(
            context: Context,
            locale: Locale,
            value: Float,
            commodity: Commodity
        ): String {
            val label = context.getString(R.string.label_chart_total)
            val formatter = NumberFormat.getNumberInstance(locale)
            formatter.setMinimumFractionDigits(0)
            formatter.setMaximumFractionDigits(commodity.smallestFractionDigits)
            val currencySymbol = commodity.symbol
            return String.format(
                locale,
                TOTAL_VALUE_LABEL_PATTERN,
                label,
                formatter.format(value.toDouble()),
                currencySymbol
            )
        }

        fun <E : Entry, T : IDataSet<E>> getYValueSum(data: ChartData<T>): Float {
            return data.yMax - data.yMin
        }

        fun <E : Entry> getYValueSum(dataSet: IDataSet<E>): Float {
            return dataSet.yMax - dataSet.yMin
        }
    }
}
