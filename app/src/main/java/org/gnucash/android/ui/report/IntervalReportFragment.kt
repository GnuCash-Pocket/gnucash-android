package org.gnucash.android.ui.report

import com.github.mikephil.charting.data.ChartData
import org.gnucash.android.db.adapter.TransactionsDbAdapter
import org.gnucash.android.model.AccountType
import org.joda.time.LocalDateTime

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

}