package org.gnucash.android.gnc

import android.app.ProgressDialog
import android.content.Context
import android.os.SystemClock
import org.gnucash.android.R
import org.gnucash.android.model.Account
import org.gnucash.android.model.Book
import org.gnucash.android.model.Budget
import org.gnucash.android.model.Commodity
import org.gnucash.android.model.Price
import org.gnucash.android.model.ScheduledAction
import org.gnucash.android.model.Transaction
import timber.log.Timber

abstract class AsyncTaskProgressListener(context: Context) : DefaultProgressListener() {

    private data class PublishItem(
        val label: String,
        val progress: Long = 0,
        val total: Long = 0,
        val timestamp: Long
    )

    private var labelAccounts: String? = null
    private var labelBook: String? = null
    private var labelBudgets: String? = null
    private var labelCommodities: String? = null
    private var labelPrices: String? = null
    private var labelSchedules: String? = null
    private var labelTransactions: String? = null
    private var totalBudgets: Long = 0
    private var countBudgets: Long = 0
    private var totalCommodities: Long = 0
    private var countCommodities: Long = 0
    private var totalAccounts: Long = 0
    private var countAccounts: Long = 0
    private var totalTransactions: Long = 0
    private var countTransactions: Long = 0
    private var totalPrices: Long = 0
    private var countPrices: Long = 0
    private var totalScheduled: Long = 0
    private var countScheduled: Long = 0
    private var itemPublished: PublishItem? = null

    init {
        labelAccounts = context.getString(R.string.title_progress_processing_accounts)
        labelBook = context.getString(R.string.title_progress_processing_books)
        labelBudgets = context.getString(R.string.title_progress_processing_budgets)
        labelCommodities = context.getString(R.string.title_progress_processing_commodities)
        labelPrices = context.getString(R.string.title_progress_processing_prices)
        labelSchedules = context.getString(R.string.title_progress_processing_schedules)
        labelTransactions = context.getString(R.string.title_progress_processing_transactions)
    }

    override fun onAccountCount(count: Long) {
        totalAccounts = count
        publishProgressDebounce(labelAccounts!!, countAccounts, totalAccounts)
    }

    override fun onAccount(account: Account) {
        Timber.v("%s: %s", labelAccounts, account)
        publishProgressDebounce(labelAccounts!!, ++countAccounts, totalAccounts)
    }

    override fun onBookCount(count: Long) {
        publishProgressDebounce(labelBook!!)
    }

    override fun onBook(book: Book) {
        Timber.v("%s: %s", labelBook, book.displayName)
        publishProgressDebounce(labelBook!!)
    }

    override fun onBudgetCount(count: Long) {
        totalBudgets = count
        publishProgressDebounce(labelBudgets!!, countBudgets, totalBudgets)
    }

    override fun onBudget(budget: Budget) {
        Timber.v("%s: %s", labelBudgets, budget)
        publishProgressDebounce(labelBudgets!!, ++countBudgets, totalBudgets)
    }

    override fun onCommodityCount(count: Long) {
        totalCommodities = count
        publishProgressDebounce(labelCommodities!!, countCommodities, totalCommodities)
    }

    override fun onCommodity(commodity: Commodity) {
        if (commodity.isTemplate) return
        Timber.v("%s: %s", labelCommodities, commodity)
        publishProgressDebounce(labelCommodities!!, ++countCommodities, totalCommodities)
    }

    override fun onPriceCount(count: Long) {
        totalPrices = count
        publishProgressDebounce(labelPrices!!, countPrices, totalPrices)
    }

    override fun onPrice(price: Price) {
        Timber.v("%s: %s", labelPrices, price)
        publishProgressDebounce(labelPrices!!, ++countPrices, totalPrices)
    }

    override fun onScheduleCount(count: Long) {
        totalScheduled = count
        publishProgressDebounce(labelSchedules!!, countScheduled, totalScheduled)
    }

    override fun onSchedule(scheduledAction: ScheduledAction) {
        Timber.v("%s: %s", labelSchedules, scheduledAction)
        publishProgressDebounce(labelSchedules!!, ++countScheduled, totalScheduled)
    }

    override fun onTransactionCount(count: Long) {
        totalTransactions = count
        publishProgressDebounce(
            labelTransactions!!,
            countTransactions,
            totalTransactions
        )
    }

    override fun onTransaction(transaction: Transaction) {
        if (transaction.isTemplate) return
        Timber.v("%s: %s", labelTransactions, transaction)
        publishProgressDebounce(
            labelTransactions!!,
            ++countTransactions,
            totalTransactions
        )
    }

    private fun publishProgressDebounce(label: String, progress: Long = 0, total: Long = 0) {
        val item = itemPublished
        val labelPublished = item?.label
        val timestampDelta =
            if (item != null) SystemClock.elapsedRealtime() - item.timestamp else PUBLISH_TIMEOUT
        if (timestampDelta >= PUBLISH_TIMEOUT || label != labelPublished) {
            // Publish straight away, or if we waited enough time, or label changed.
            itemPublished = PublishItem(label, progress, total, SystemClock.elapsedRealtime())
            publishProgress(label, progress, total)
        }
    }

    protected abstract fun publishProgress(label: String, progress: Long = 0, total: Long = 0)

    fun showProgress(dialog: ProgressDialog, vararg values: Any) {
        val length = values.size
        if (length == 0) return
        val value = values[0] as String
        dialog.setTitle(value)

        if (length >= 3) {
            val count = (values[1] as Number).toLong()
            val total = (values[2] as Number).toLong()
            if (total > 0) {
                val progress = (count * 100) / total
                dialog.setIndeterminate(false)
                dialog.setProgress(progress.toInt())
            } else {
                dialog.setIndeterminate(true)
            }
        } else {
            dialog.setIndeterminate(true)
        }
    }

    companion object {
        private const val PUBLISH_TIMEOUT: Long = 100
    }
}