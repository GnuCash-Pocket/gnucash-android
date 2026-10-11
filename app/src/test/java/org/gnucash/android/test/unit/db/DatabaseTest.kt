package org.gnucash.android.test.unit.db

import org.gnucash.android.app.GnuCashApplication
import org.gnucash.android.db.DatabaseHelper
import org.gnucash.android.db.adapter.AccountsDbAdapter
import org.gnucash.android.db.adapter.BudgetAmountsDbAdapter
import org.gnucash.android.db.adapter.BudgetsDbAdapter
import org.gnucash.android.db.adapter.CommoditiesDbAdapter
import org.gnucash.android.db.adapter.PricesDbAdapter
import org.gnucash.android.db.adapter.RecurrenceDbAdapter
import org.gnucash.android.db.adapter.ScheduledActionDbAdapter
import org.gnucash.android.db.adapter.SplitsDbAdapter
import org.gnucash.android.db.adapter.TransactionsDbAdapter
import org.gnucash.android.test.unit.GnuCashTest
import org.junit.After
import org.junit.Before

abstract class DatabaseTest : GnuCashTest() {
    private lateinit var dbHelper: DatabaseHelper
    protected lateinit var accountsDbAdapter: AccountsDbAdapter
        private set
    protected lateinit var budgetAmountsDbAdapter: BudgetAmountsDbAdapter
        private set
    protected lateinit var budgetsDbAdapter: BudgetsDbAdapter
        private set
    protected lateinit var commoditiesDbAdapter: CommoditiesDbAdapter
        private set
    protected lateinit var pricesDbAdapter: PricesDbAdapter
        private set
    protected lateinit var recurrenceDbAdapter: RecurrenceDbAdapter
        private set
    protected lateinit var scheduledActionDbAdapter: ScheduledActionDbAdapter
        private set
    protected lateinit var splitsDbAdapter: SplitsDbAdapter
        private set
    protected lateinit var transactionsDbAdapter: TransactionsDbAdapter
        private set

    @Before
    fun setUpDb() {
        initAdapters(null)
    }

    @After
    fun tearDownDb() {
        accountsDbAdapter.deleteAllRecords()
        dbHelper.close()
        DatabaseHelper.deleteFiles(context)
    }

    /**
     * Initialize database adapters for a specific book.
     * This method should be called everytime a new book is loaded into the database
     *
     * @param bookUID GUID of the GnuCash book
     */
    protected open fun initAdapters(bookUID: String?) {
        val bookUID = bookUID ?: GnuCashApplication.activeBookUID
        val dbHelper = DatabaseHelper(context, bookUID)
        this.dbHelper = dbHelper
        val dbHolder = dbHelper.holder
        accountsDbAdapter = dbHolder.accountsDbAdapter
        budgetAmountsDbAdapter = dbHolder.budgetAmountsDbAdapter
        budgetsDbAdapter = dbHolder.budgetDbAdapter
        commoditiesDbAdapter = dbHolder.commoditiesDbAdapter
        pricesDbAdapter = dbHolder.pricesDbAdapter
        recurrenceDbAdapter = dbHolder.recurrenceDbAdapter
        scheduledActionDbAdapter = dbHolder.scheduledActionDbAdapter
        splitsDbAdapter = dbHolder.splitsDbAdapter
        transactionsDbAdapter = dbHolder.transactionsDbAdapter
    }
}