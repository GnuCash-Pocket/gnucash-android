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
package org.gnucash.android.ui.report

import android.app.DatePickerDialog
import android.content.Context
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.DatePicker
import androidx.appcompat.app.ActionBar
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import org.gnucash.android.R
import org.gnucash.android.app.getSerializableCompat
import org.gnucash.android.databinding.ActivityReportsBinding
import org.gnucash.android.db.adapter.TransactionsDbAdapter
import org.gnucash.android.model.AccountType
import org.gnucash.android.model.Commodity
import org.gnucash.android.ui.adapter.AccountTypesAdapter.Companion.expenseAndIncome
import org.gnucash.android.ui.common.BaseDrawerActivity
import org.gnucash.android.ui.common.Refreshable
import org.gnucash.android.ui.get
import org.gnucash.android.ui.report.ReportType.Companion.getReportNames
import org.gnucash.android.ui.util.dialog.DateRangePickerDialogFragment
import org.gnucash.android.ui.util.dialog.DateRangePickerDialogFragment.OnDateRangeSetListener
import org.gnucash.android.util.toLocalDateTime
import org.gnucash.android.util.toMillis
import org.joda.time.LocalDate
import org.joda.time.LocalDateTime

/**
 * Activity for displaying report fragments (which must implement [BaseReportFragment])
 *
 * In order to add new reports, extend the [BaseReportFragment] class to provide the view
 * for the report. Then add the report mapping in [ReportType] constructor depending on what
 * kind of report it is. The report will be dynamically included at runtime.
 *
 * @author Oleksandr Tyshkovets <olexandr.tyshkovets@gmail.com>
 * @author Ngewi Fet <ngewif@gmail.com>
 */
class ReportsActivity : BaseDrawerActivity(),
    DatePickerDialog.OnDateSetListener,
    OnDateRangeSetListener,
    Refreshable {
    private var transactionsDbAdapter: TransactionsDbAdapter = TransactionsDbAdapter.instance
    var accountType: AccountType = AccountType.EXPENSE
        private set
    private var reportType: ReportType = ReportType.NONE

    enum class GroupInterval {
        WEEK, MONTH, QUARTER, YEAR, ALL
    }

    /**
     * Return the start time of the reporting period
     *
     * @return Time in millis
     */
    // default time range is the last 3 months
    var reportPeriodStart: LocalDateTime? = LocalDateTime.now().minusMonths(3)
        private set

    /**
     * Return the end time of the reporting period
     *
     * @return Time in millis
     */
    var reportPeriodEnd: LocalDateTime? = LocalDateTime.now()
        private set

    private var reportGroupInterval = GroupInterval.MONTH

    private var binding: ActivityReportsBinding? = null

    var reportTypeSelectedListener: AdapterView.OnItemSelectedListener =
        object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>,
                view: View?,
                position: Int,
                id: Long
            ) {
                if (view == null) return
                val reportType = ReportType.values()[position]
                if (this@ReportsActivity.reportType != reportType) {
                    showReport(reportType)
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>) = Unit
        }

    override fun inflateView() {
        val binding = ActivityReportsBinding.inflate(layoutInflater)
        this.binding = binding
        setContentView(binding.root)
        drawerLayout = binding.drawerLayout
        navigationView = binding.navView
        toolbar = binding.toolbarLayout.toolbar
        toolbarProgress = binding.toolbarLayout.toolbarProgress.progress
    }

    override val titleRes: Int = R.string.title_reports

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val context: Context = this
        transactionsDbAdapter = TransactionsDbAdapter.instance
        val binding = this.binding!!

        val actionBar: ActionBar = supportActionBar!!
        val typesAdapter = ArrayAdapter<String?>(
            actionBar.themedContext,
            android.R.layout.simple_list_item_1,
            getReportNames(context)
        )
        binding.toolbarLayout.toolbarSpinner.adapter = typesAdapter
        binding.toolbarLayout.toolbarSpinner.onItemSelectedListener = reportTypeSelectedListener

        val adapter = ArrayAdapter.createFromResource(
            context, R.array.report_time_range,
            android.R.layout.simple_spinner_item
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.timeRangeSpinner.adapter = adapter
        binding.timeRangeSpinner.onItemSelectedListener = object :
            AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>,
                view: View?,
                position: Int,
                id: Long
            ) {
                if (view == null) return
                val now = LocalDateTime.now()
                reportPeriodEnd = now
                when (position) {
                    0 -> reportPeriodStart = now.dayOfMonth().withMinimumValue()
                    1 -> reportPeriodStart = now.minusMonths(3)
                    2 -> reportPeriodStart = now.minusMonths(6)
                    3 -> reportPeriodStart = now.minusYears(1)
                    4 -> {
                        reportPeriodStart = null
                        reportPeriodEnd = null
                    }

                    5 -> {
                        val commodityUID = Commodity.DEFAULT_COMMODITY.uid
                        val earliest = transactionsDbAdapter!!.getTimestampOfEarliestTransaction(
                            accountType,
                            commodityUID
                        )
                        DateRangePickerDialogFragment.newInstance(
                            LocalDate(earliest),
                            now.toLocalDate(),
                            this@ReportsActivity
                        ).show(supportFragmentManager, "range_dialog")
                        return
                    }
                }
                //the date picker will trigger the update itself
                updateDateRangeOnFragment()
            }

            override fun onNothingSelected(parent: AdapterView<*>) = Unit
        }
        binding.timeRangeSpinner.setSelection(1)

        val accountTypeAdapter = expenseAndIncome(context)
        binding.reportAccountTypeSpinner.adapter = accountTypeAdapter
        binding.reportAccountTypeSpinner.onItemSelectedListener = object :
            AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>,
                view: View?,
                position: Int,
                id: Long
            ) {
                if (view == null) return
                if (position < 0) return
                val label = accountTypeAdapter[position]
                updateAccountTypeOnFragments(label.value)
            }

            override fun onNothingSelected(parent: AdapterView<*>) = Unit
        }

        if (savedInstanceState == null) {
            showOverview()
        } else {
            reportType =
                savedInstanceState.getSerializableCompat(STATE_REPORT_TYPE, ReportType::class.java)
                    ?: ReportType.NONE
            reportPeriodStart = savedInstanceState.getLong(STATE_REPORT_START).toLocalDateTime()
            reportPeriodEnd = savedInstanceState.getLong(STATE_REPORT_END).toLocalDateTime()
        }
    }

    fun onFragmentResumed(fragment: Fragment) {
        val binding = this.binding!!
        var reportType = ReportType.NONE
        if (fragment is BaseReportFragment) {
            reportType = fragment.reportType
            binding.reportAccountTypeSpinner.isVisible = fragment.requiresAccountTypeOptions()
        }

        setTitlesColor(ContextCompat.getColor(this, reportType.colorId))
        updateReportTypeSpinner(binding, reportType)
        toggleToolbarTitleVisibility(binding, reportType)
    }

    /**
     * Show the overview.
     */
    private fun showOverview() {
        showReport(ReportType.NONE)
    }

    /**
     * Show the report.
     *
     * @param reportType the report type.
     */
    fun showReport(reportType: ReportType) {
        val fragmentManager = supportFragmentManager
        // First, remove the current report to replace it.
        if (fragmentManager.backStackEntryCount > 0) {
            fragmentManager.popBackStack()
        }
        val fragment = reportType.fragment
        val tx = fragmentManager
            .beginTransaction()
            .replace(R.id.fragment_container, fragment)
        if (reportType != ReportType.NONE) {
            val stackName = getString(reportType.titleId)
            tx.addToBackStack(stackName)
        }
        tx.commit()
    }

    /**
     * Update the report type spinner
     */
    fun updateReportTypeSpinner(binding: ActivityReportsBinding, reportType: ReportType) {
        this.reportType = reportType
        binding.toolbarLayout.toolbarSpinner.setSelection(reportType.ordinal)
    }

    private fun toggleToolbarTitleVisibility(
        binding: ActivityReportsBinding,
        reportType: ReportType
    ) {
        val actionBar: ActionBar = supportActionBar!!
        actionBar.setDisplayShowTitleEnabled(reportType == ReportType.NONE)
        binding.toolbarLayout.toolbarSpinner.isVisible = reportType != ReportType.NONE
    }

    /**
     * Updates the reporting time range for all listening fragments
     */
    private fun updateDateRangeOnFragment() {
        val fragments = supportFragmentManager.fragments
        for (fragment in fragments) {
            if (fragment is ReportOptionsListener) {
                fragment.onTimeRangeUpdated(reportPeriodStart, reportPeriodEnd)
            }
        }
    }

    /**
     * Updates the account type for all attached fragments which are listening
     */
    private fun updateAccountTypeOnFragments(accountType: AccountType) {
        this.accountType = accountType
        val fragments = supportFragmentManager.fragments
        for (fragment in fragments) {
            if (fragment is ReportOptionsListener) {
                fragment.onAccountTypeUpdated(accountType)
            }
        }
    }

    /**
     * Updates the report grouping interval on all attached fragments which are listening
     */
    private fun updateGroupingOnFragments() {
        val fragments = supportFragmentManager.fragments
        for (fragment in fragments) {
            if (fragment is ReportOptionsListener) {
                fragment.onGroupingUpdated(reportGroupInterval)
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        super.onCreateOptionsMenu(menu)
        menuInflater.inflate(R.menu.report_actions, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_group_reports_by -> return true

            R.id.group_by_month -> {
                item.isChecked = true
                reportGroupInterval = GroupInterval.MONTH
                updateGroupingOnFragments()
                return true
            }

            R.id.group_by_quarter -> {
                item.isChecked = true
                reportGroupInterval = GroupInterval.QUARTER
                updateGroupingOnFragments()
                return true
            }

            R.id.group_by_year -> {
                item.isChecked = true
                reportGroupInterval = GroupInterval.YEAR
                updateGroupingOnFragments()
                return true
            }

            else -> return super.onOptionsItemSelected(item)
        }
    }

    override fun onDateSet(view: DatePicker, year: Int, monthOfYear: Int, dayOfMonth: Int) {
        reportPeriodStart = LocalDateTime(year, monthOfYear, dayOfMonth, 0, 0)
        updateDateRangeOnFragment()
    }

    override fun onDateRangeSet(startDate: LocalDate, endDate: LocalDate) {
        reportPeriodStart = startDate.toDateTimeAtStartOfDay().toLocalDateTime()
        reportPeriodEnd = endDate.toDateTimeAtCurrentTime().toLocalDateTime()
        updateDateRangeOnFragment()
    }

    override fun refresh() {
        val fragments = supportFragmentManager.fragments
        for (fragment in fragments) {
            if (fragment is Refreshable) {
                fragment.refresh()
            }
        }
    }

    /**
     * Just another call to refresh
     */
    override fun refresh(uid: String?) {
        refresh()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putSerializable(STATE_REPORT_TYPE, reportType)
        outState.putLong(STATE_REPORT_START, reportPeriodStart.toMillis())
        outState.putLong(STATE_REPORT_END, reportPeriodEnd.toMillis())
    }

    companion object {
        private const val STATE_REPORT_TYPE = "report_type"
        private const val STATE_REPORT_START = "report_start"
        private const val STATE_REPORT_END = "report_end"
    }
}
