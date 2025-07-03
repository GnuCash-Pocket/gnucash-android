package org.gnucash.android.ui.report

import org.gnucash.android.db.adapter.TransactionsDbAdapter
import org.gnucash.android.model.AccountType
import org.joda.time.LocalDateTime

abstract class IntervalReportFragment : BaseReportFragment()  {
    @JvmField
    protected val earliestTimestamps = mutableMapOf<AccountType, Long>()
    @JvmField
    protected val latestTimestamps= mutableMapOf<AccountType, Long>()
    @JvmField
    protected var earliestTransactionTimestamp: LocalDateTime? = null
    @JvmField
    protected var isChartDataPresent = false
    @JvmField
    protected val accountTypes = listOf(AccountType.INCOME, AccountType.EXPENSE)
    @JvmField
    protected var transactionsDbAdapter: TransactionsDbAdapter = TransactionsDbAdapter.getInstance()

    override fun onStart() {
        super.onStart()
        transactionsDbAdapter = TransactionsDbAdapter.getInstance()
    }

    /**
     * Calculates the earliest and latest transaction's timestamps of the specified account types
     *
     * @param accountTypes account's types which will be processed
     */
    protected fun calculateEarliestAndLatestTimestamps(accountTypes: List<AccountType>) {
        earliestTimestamps.clear()
        latestTimestamps.clear()
        earliestTransactionTimestamp = mReportPeriodStart
        if (earliestTransactionTimestamp != null) {
            return
        }

        val commodityUID = mCommodity.getUID()
        for (type in accountTypes) {
            val earliest = transactionsDbAdapter.getTimestampOfEarliestTransaction(type, commodityUID)
            if (earliest > TransactionsDbAdapter.INVALID_DATE) {
                earliestTimestamps.put(type, earliest)
            }
            val latest = transactionsDbAdapter.getTimestampOfLatestTransaction(type, commodityUID)
            if (latest >= earliest) {
                latestTimestamps.put(type, latest)
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