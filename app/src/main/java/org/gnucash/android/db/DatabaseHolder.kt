package org.gnucash.android.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import org.gnucash.android.db.BookDbHelper.Companion.getBookUID
import org.gnucash.android.db.adapter.AccountsDbAdapter
import org.gnucash.android.db.adapter.BooksDbAdapter
import org.gnucash.android.db.adapter.BudgetAmountsDbAdapter
import org.gnucash.android.db.adapter.BudgetsDbAdapter
import org.gnucash.android.db.adapter.CommoditiesDbAdapter
import org.gnucash.android.db.adapter.PricesDbAdapter
import org.gnucash.android.db.adapter.RecurrenceDbAdapter
import org.gnucash.android.db.adapter.ScheduledActionDbAdapter
import org.gnucash.android.db.adapter.SplitsDbAdapter
import org.gnucash.android.db.adapter.TransactionsDbAdapter
import java.io.Closeable

data class DatabaseHolder(
    val context: Context,
    val db: SQLiteDatabase,
    val name: String = getBookUID(db)
) : Closeable {
    private var _accountsDbAdapter: AccountsDbAdapter? = null
    val accountsDbAdapter: AccountsDbAdapter
        get() {
            var adapter = _accountsDbAdapter
            if (adapter == null) {
                adapter = AccountsDbAdapter(transactionsDbAdapter, pricesDbAdapter)
                _accountsDbAdapter = adapter
            }
            return adapter
        }

    private var _transactionsDbAdapter: TransactionsDbAdapter? = null
    val transactionsDbAdapter: TransactionsDbAdapter
        get() {
            var adapter = _transactionsDbAdapter
            if (adapter == null) {
                adapter = TransactionsDbAdapter(splitsDbAdapter)
                _transactionsDbAdapter = adapter
            }
            return adapter
        }

    private var _splitsDbAdapter: SplitsDbAdapter? = null
    val splitsDbAdapter: SplitsDbAdapter
        get() {
            var adapter = _splitsDbAdapter
            if (adapter == null) {
                adapter = SplitsDbAdapter(commoditiesDbAdapter)
                _splitsDbAdapter = adapter
            }
            return adapter
        }

    private var _scheduledActionDbAdapter: ScheduledActionDbAdapter? = null
    val scheduledActionDbAdapter: ScheduledActionDbAdapter
        get() {
            var adapter = _scheduledActionDbAdapter
            if (adapter == null) {
                adapter = ScheduledActionDbAdapter(recurrenceDbAdapter, transactionsDbAdapter)
                _scheduledActionDbAdapter = adapter
            }
            return adapter
        }

    private var _commoditiesDbAdapter: CommoditiesDbAdapter? = null
    val commoditiesDbAdapter: CommoditiesDbAdapter
        get() {
            var adapter = _commoditiesDbAdapter
            if (adapter == null) {
                adapter = CommoditiesDbAdapter(this, true)
                _commoditiesDbAdapter = adapter
            }
            return adapter
        }

    private var _pricesDbAdapter: PricesDbAdapter? = null
    val pricesDbAdapter: PricesDbAdapter
        get() {
            var adapter = _pricesDbAdapter
            if (adapter == null) {
                adapter = PricesDbAdapter(commoditiesDbAdapter)
                _pricesDbAdapter = adapter
            }
            return adapter
        }

    private var _budgetDbAdapter: BudgetsDbAdapter? = null
    val budgetDbAdapter: BudgetsDbAdapter
        get() {
            var adapter = _budgetDbAdapter
            if (adapter == null) {
                adapter = BudgetsDbAdapter(budgetAmountsDbAdapter, recurrenceDbAdapter)
                _budgetDbAdapter = adapter
            }
            return adapter
        }

    private var _budgetAmountsDbAdapter: BudgetAmountsDbAdapter? = null
    val budgetAmountsDbAdapter: BudgetAmountsDbAdapter
        get() {
            var adapter = _budgetAmountsDbAdapter
            if (adapter == null) {
                adapter = BudgetAmountsDbAdapter(commoditiesDbAdapter)
                _budgetAmountsDbAdapter = adapter
            }
            return adapter
        }

    private var _recurrenceDbAdapter: RecurrenceDbAdapter? = null
    val recurrenceDbAdapter: RecurrenceDbAdapter
        get() {
            var adapter = _recurrenceDbAdapter
            if (adapter == null) {
                adapter = RecurrenceDbAdapter(this)
                _recurrenceDbAdapter = adapter
            }
            return adapter
        }

    private var _booksDbAdapter: BooksDbAdapter? = null
    val booksDbAdapter: BooksDbAdapter
        get() {
            var adapter = _booksDbAdapter
            if (adapter == null) {
                adapter = BooksDbAdapter(this)
                _booksDbAdapter = adapter
            }
            return adapter
        }

    override fun close() {
        _accountsDbAdapter?.close()
        _booksDbAdapter?.close()
        _budgetAmountsDbAdapter?.close()
        _budgetDbAdapter?.close()
        _commoditiesDbAdapter?.close()
        _pricesDbAdapter?.close()
        _recurrenceDbAdapter?.close()
        _scheduledActionDbAdapter?.close()
        _splitsDbAdapter?.close()
        _transactionsDbAdapter?.close()
        db.close()
    }

    fun clearCache() {
        _accountsDbAdapter?.clearCache()
        _booksDbAdapter?.clearCache()
        _budgetAmountsDbAdapter?.clearCache()
        _budgetDbAdapter?.clearCache()
        _commoditiesDbAdapter?.clearCache()
        _pricesDbAdapter?.clearCache()
        _recurrenceDbAdapter?.clearCache()
        _scheduledActionDbAdapter?.clearCache()
        _splitsDbAdapter?.clearCache()
        _transactionsDbAdapter?.clearCache()
    }
}