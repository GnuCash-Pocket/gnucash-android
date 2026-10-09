package org.gnucash.android.ui.report

import android.text.format.DateFormat
import com.github.mikephil.charting.data.ChartData
import org.gnucash.android.db.adapter.TransactionsDbAdapter
import org.gnucash.android.model.AccountType
import org.gnucash.android.ui.report.ReportsActivity.GroupInterval
import org.gnucash.android.util.endOfDay
import org.gnucash.android.util.firstDayOfMonth
import org.gnucash.android.util.firstDayOfYear
import org.gnucash.android.util.getFirstQuarterMonth
import org.gnucash.android.util.lastDayOfMonth
import org.gnucash.android.util.lastDayOfYear
import org.gnucash.android.util.startOfDay
import org.joda.time.LocalDateTime
import java.util.Locale

abstract class IntervalReportFragment<D : ChartData<*>> : BaseReportFragment<D>() {
    protected val earliestTimestamps = mutableMapOf<AccountType, Long>()

    protected val latestTimestamps = mutableMapOf<AccountType, Long>()

    protected var earliestTransactionTimestamp: LocalDateTime? = null

    protected var isChartDataPresent = false

    protected val accountTypes = listOf(AccountType.INCOME, AccountType.EXPENSE)

    protected var transactionsDbAdapter: TransactionsDbAdapter = TransactionsDbAdapter.instance

    override fun onStart() {
        super.onStart()
        transactionsDbAdapter = TransactionsDbAdapter.instance
    }

    /**
     * Calculates the earliest and latest transaction's timestamps of the specified account types
     *
     * @param accountTypes account's types which will be processed
     */
    protected fun calculateEarliestAndLatestTimestamps(accountTypes: List<AccountType>) {
        earliestTimestamps.clear()
        latestTimestamps.clear()
        earliestTransactionTimestamp = reportPeriodStart
        if (earliestTransactionTimestamp != null) {
            return
        }

        val commodityUID = commodity.uid
        for (type in accountTypes) {
            val earliest =
                transactionsDbAdapter.getTimestampOfEarliestTransaction(type, commodityUID)
            if (earliest > TransactionsDbAdapter.INVALID_DATE) {
                earliestTimestamps[type] = earliest
            }
            val latest = transactionsDbAdapter.getTimestampOfLatestTransaction(type, commodityUID)
            if (latest >= earliest) {
                latestTimestamps[type] = latest
            }
        }

        if (earliestTimestamps.isEmpty() || latestTimestamps.isEmpty()) {
            return
        }

        val timestamps = mutableListOf<Long>()
        timestamps.addAll(earliestTimestamps.values)
        timestamps.addAll(latestTimestamps.values)
        timestamps.sort()
        earliestTransactionTimestamp = LocalDateTime(timestamps[0])
    }

    protected fun calculateDateRange(
        accountTypes: List<AccountType>,
        groupInterval: GroupInterval
    ): ClosedRange<LocalDateTime>? {
        calculateEarliestAndLatestTimestamps(accountTypes)
        var startDate = reportPeriodStart
        if (startDate == null) {
            val startTime = earliestTimestamps[accountType]
            if (startTime != null) {
                startDate = LocalDateTime(startTime)
            } else {
                return null
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

        when (groupInterval) {
            GroupInterval.MONTH -> {
                startDate = startDate.firstDayOfMonth().startOfDay()
                endDate = endDate.lastDayOfMonth().endOfDay()
            }

            GroupInterval.QUARTER -> {
                startDate = startDate.withMonthOfYear(startDate.getFirstQuarterMonth())
                    .firstDayOfMonth()
                    .startOfDay()
                endDate = endDate.lastDayOfMonth().endOfDay()
            }

            GroupInterval.YEAR -> {
                startDate = startDate.firstDayOfYear().startOfDay()
                endDate = endDate.lastDayOfYear().endOfDay()
            }

            else -> return null
        }

        return startDate..endDate
    }

    protected fun getXAxisPattern(groupInterval: GroupInterval): String {
        val locale = Locale.getDefault()
        return when (groupInterval) {
            GroupInterval.MONTH -> DateFormat.getBestDateTimePattern(locale, "yyMMM")

            GroupInterval.QUARTER -> DateFormat.getBestDateTimePattern(locale, "yy")

            GroupInterval.YEAR -> DateFormat.getBestDateTimePattern(locale, "y")

            else -> DateFormat.getBestDateTimePattern(locale, "yM")
        }
    }
}