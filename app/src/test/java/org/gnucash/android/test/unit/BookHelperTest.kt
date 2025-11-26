package org.gnucash.android.test.unit

import org.assertj.core.api.Assertions.assertThat
import org.gnucash.android.BuildConfig
import org.gnucash.android.db.DatabaseHelper
import org.gnucash.android.db.DatabaseHolder
import org.gnucash.android.db.adapter.AccountsDbAdapter
import org.gnucash.android.db.adapter.BooksDbAdapter
import org.gnucash.android.db.adapter.BudgetsDbAdapter
import org.gnucash.android.db.adapter.CommoditiesDbAdapter
import org.gnucash.android.db.adapter.PricesDbAdapter
import org.gnucash.android.db.adapter.RecurrenceDbAdapter
import org.gnucash.android.db.adapter.ScheduledActionDbAdapter
import org.gnucash.android.db.adapter.TransactionsDbAdapter
import org.gnucash.android.importer.GncXmlImporter
import org.gnucash.android.util.ConsoleTree
import org.junit.After
import org.junit.Before
import timber.log.Timber
import java.nio.charset.StandardCharsets

abstract class BookHelperTest : GnuCashTest() {
    protected lateinit var dbHolder: DatabaseHolder
    protected lateinit var booksDbAdapter: BooksDbAdapter

    protected lateinit var transactionsDbAdapter: TransactionsDbAdapter
    protected lateinit var accountsDbAdapter: AccountsDbAdapter
    protected lateinit var scheduledActionDbAdapter: ScheduledActionDbAdapter
    protected lateinit var commoditiesDbAdapter: CommoditiesDbAdapter
    protected lateinit var budgetsDbAdapter: BudgetsDbAdapter
    protected lateinit var pricesDbAdapter: PricesDbAdapter

    protected fun importGnuCashXml(filename: String): String {
        val inputStream = openResourceStream(filename)
        val bookUID = GncXmlImporter.parse(context, inputStream)
        setUpDbAdapters(bookUID)
        return bookUID
    }

    private fun setUpDbAdapters(bookUID: String) {
        close()
        val databaseHelper = DatabaseHelper(context, bookUID)
        val mainHolder = databaseHelper.holder
        commoditiesDbAdapter = CommoditiesDbAdapter(mainHolder)
        transactionsDbAdapter = TransactionsDbAdapter(commoditiesDbAdapter)
        accountsDbAdapter = AccountsDbAdapter(transactionsDbAdapter)
        val recurrenceDbAdapter = RecurrenceDbAdapter(mainHolder)
        scheduledActionDbAdapter =
            ScheduledActionDbAdapter(recurrenceDbAdapter, transactionsDbAdapter)
        budgetsDbAdapter = BudgetsDbAdapter(recurrenceDbAdapter)
        pricesDbAdapter = PricesDbAdapter(commoditiesDbAdapter)
        dbHolder = mainHolder
    }

    @Before
    open fun setUp() {
        System.gc()
        booksDbAdapter = BooksDbAdapter.instance
        assertThat(booksDbAdapter.recordsCount).isOne()
        setUpDbAdapters(booksDbAdapter.activeBookUID)
    }

    @After
    open fun tearDown() {
        close()
    }

    private fun close() {
        if (::accountsDbAdapter.isInitialized) accountsDbAdapter.close()
        if (::budgetsDbAdapter.isInitialized) budgetsDbAdapter.close()
        if (::commoditiesDbAdapter.isInitialized) commoditiesDbAdapter.close()
        if (::pricesDbAdapter.isInitialized) pricesDbAdapter.close()
        if (::scheduledActionDbAdapter.isInitialized) scheduledActionDbAdapter.close()
        if (::transactionsDbAdapter.isInitialized) transactionsDbAdapter.close()
        if (::dbHolder.isInitialized) dbHolder.close()
    }

    protected fun readFile(name: String): String {
        val stream = openResourceStream(name)
        val bytes = stream.readAllBytes()
        return String(bytes, StandardCharsets.UTF_8)
    }

    companion object {
        init {
            Timber.plant(ConsoleTree(BuildConfig.DEBUG) as Timber.Tree)
        }
    }
}
