package org.gnucash.android.gnc

import org.gnucash.android.model.Account
import org.gnucash.android.model.Book
import org.gnucash.android.model.Budget
import org.gnucash.android.model.Commodity
import org.gnucash.android.model.Price
import org.gnucash.android.model.ScheduledAction
import org.gnucash.android.model.Transaction

interface GncProgressListener {
    fun onAccountCount(count: Long) = Unit
    fun onAccount(account: Account) = Unit
    fun onBookCount(count: Long) = Unit
    fun onBook(book: Book) = Unit
    fun onBudgetCount(count: Long) = Unit
    fun onBudget(budget: Budget) = Unit
    fun onCommodityCount(count: Long) = Unit
    fun onCommodity(commodity: Commodity) = Unit
    fun onPriceCount(count: Long) = Unit
    fun onPrice(price: Price) = Unit
    fun onScheduleCount(count: Long) = Unit
    fun onSchedule(scheduledAction: ScheduledAction) = Unit
    fun onTransactionCount(count: Long) = Unit
    fun onTransaction(transaction: Transaction) = Unit
    fun onTransaction(description: String) = Unit
}