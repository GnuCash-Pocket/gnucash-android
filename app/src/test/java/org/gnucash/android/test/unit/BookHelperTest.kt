package org.gnucash.android.test.unit

import android.database.sqlite.SQLiteDatabase
import junit.framework.TestCase.fail
import org.assertj.core.api.Assertions.assertThat
import org.gnucash.android.BuildConfig
import org.gnucash.android.app.GnuCashApplication
import org.gnucash.android.db.DatabaseHelper
import org.gnucash.android.db.adapter.AccountsDbAdapter
import org.gnucash.android.db.adapter.BooksDbAdapter
import org.gnucash.android.db.adapter.BudgetsDbAdapter
import org.gnucash.android.db.adapter.CommoditiesDbAdapter
import org.gnucash.android.db.adapter.RecurrenceDbAdapter
import org.gnucash.android.db.adapter.ScheduledActionDbAdapter
import org.gnucash.android.db.adapter.TransactionsDbAdapter
import org.gnucash.android.importer.GncXmlHandler
import org.gnucash.android.util.ConsoleTree
import org.junit.After
import org.junit.Before
import org.xml.sax.InputSource
import timber.log.Timber
import java.io.BufferedInputStream
import javax.xml.parsers.SAXParser
import javax.xml.parsers.SAXParserFactory

abstract class BookHelperTest : GnuCashTest() {
    @JvmField
    protected var importedDb: SQLiteDatabase? = null
    protected lateinit var booksDbAdapter: BooksDbAdapter
    @JvmField
    protected var transactionsDbAdapter: TransactionsDbAdapter? = null
    @JvmField
    protected var accountsDbAdapter: AccountsDbAdapter? = null
    @JvmField
    protected var scheduledActionDbAdapter: ScheduledActionDbAdapter? = null
    @JvmField
    protected var commoditiesDbAdapter: CommoditiesDbAdapter? = null
    @JvmField
    protected var budgetsDbAdapter: BudgetsDbAdapter? = null

    protected fun importGnuCashXml(filename: String): String {
        val handler = GncXmlHandler()
        try {
            val parser: SAXParser = SAXParserFactory.newInstance().newSAXParser()
            val reader = parser.xmlReader
            reader.contentHandler = handler
            val inputStream = openResourceStream(filename)
            val inputSource = InputSource(BufferedInputStream(inputStream))
            reader.parse(inputSource)
        } catch (e: Exception) {
            Timber.e(e)
            fail()
        }
        val bookUID = handler.importedBookUID
        setUpDbAdapters(bookUID)
        return bookUID
    }

    private fun setUpDbAdapters(bookUID: String) {
        val databaseHelper = DatabaseHelper(GnuCashApplication.getAppContext(), bookUID)
        val mainDb = databaseHelper.readableDatabase
        commoditiesDbAdapter = CommoditiesDbAdapter(mainDb)
        transactionsDbAdapter = TransactionsDbAdapter(commoditiesDbAdapter!!)
        accountsDbAdapter = AccountsDbAdapter(transactionsDbAdapter!!)
        val recurrenceDbAdapter = RecurrenceDbAdapter(mainDb)
        scheduledActionDbAdapter = ScheduledActionDbAdapter(recurrenceDbAdapter)
        budgetsDbAdapter = BudgetsDbAdapter(recurrenceDbAdapter)
        importedDb = mainDb
    }

    @Before
    open fun setUp() {
        booksDbAdapter = BooksDbAdapter.getInstance()
        booksDbAdapter.deleteAllRecords()
        assertThat(booksDbAdapter.getRecordsCount()).isZero()
    }

    @After
    open fun tearDown() {
        if (transactionsDbAdapter != null) {
            transactionsDbAdapter!!.close()
            transactionsDbAdapter = null
        }
        if (accountsDbAdapter != null) {
            accountsDbAdapter!!.close()
            accountsDbAdapter = null
        }
        if (scheduledActionDbAdapter != null) {
            scheduledActionDbAdapter!!.close()
            scheduledActionDbAdapter = null
        }
        if (importedDb != null) {
            importedDb!!.close()
            importedDb = null
        }
    }

    companion object {
        init {
            Timber.plant(ConsoleTree(BuildConfig.DEBUG) as Timber.Tree)
        }
    }
}
