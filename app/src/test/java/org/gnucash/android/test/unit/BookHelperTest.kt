package org.gnucash.android.test.unit

import android.net.Uri
import org.assertj.core.api.Assertions.assertThat
import org.gnucash.android.app.GnuCashApplication
import org.gnucash.android.db.DatabaseHelper
import org.gnucash.android.db.DatabaseHolder
import org.gnucash.android.db.DatabaseSchema.TransactionEntry
import org.gnucash.android.db.adapter.AccountsDbAdapter
import org.gnucash.android.db.adapter.BooksDbAdapter
import org.gnucash.android.db.adapter.BudgetsDbAdapter
import org.gnucash.android.db.adapter.CommoditiesDbAdapter
import org.gnucash.android.db.adapter.PricesDbAdapter
import org.gnucash.android.db.adapter.RecurrenceDbAdapter
import org.gnucash.android.db.adapter.ScheduledActionDbAdapter
import org.gnucash.android.db.adapter.TransactionsDbAdapter
import org.gnucash.android.gnc.GncProgressListener
import org.gnucash.android.importer.ImporterFactory.getInputStream
import org.gnucash.android.importer.sql.SqliteImporter
import org.gnucash.android.importer.xml.GncXmlImporter
import org.gnucash.android.net.toUri
import org.junit.After
import org.junit.Before
import java.io.InputStream
import java.nio.charset.StandardCharsets

abstract class BookHelperTest : GnuCashTest() {
    protected lateinit var dbHolder: DatabaseHolder
    protected lateinit var booksDbAdapter: BooksDbAdapter

    protected lateinit var transactionsDbAdapter: TransactionsDbAdapter
    protected lateinit var accountsDbAdapter: AccountsDbAdapter
    protected lateinit var recurrenceDbAdapter: RecurrenceDbAdapter
    protected lateinit var scheduledActionDbAdapter: ScheduledActionDbAdapter
    protected lateinit var commoditiesDbAdapter: CommoditiesDbAdapter
    protected lateinit var budgetsDbAdapter: BudgetsDbAdapter
    protected lateinit var pricesDbAdapter: PricesDbAdapter

    protected fun importGnuCashXml(filename: String): String {
        val uri = javaClass.classLoader!!.getResource(filename).toUri()
        val inputStream = getInputStream(openResourceStream(filename))
        val bookUID = GncXmlImporter.parse(context, uri, inputStream)
        setUpDbAdapters(bookUID)
        return bookUID
    }

    protected fun importGnuCashSqlite(
        filename: String,
        listener: GncProgressListener? = null
    ): String {
        val uri = javaClass.classLoader!!.getResource(filename).toUri()
        val inputStream = getInputStream(openResourceStream(filename))
        return importGnuCashSqlite(uri, inputStream, listener)
    }

    protected fun importGnuCashSqlite(
        uri: Uri,
        inputStream: InputStream,
        listener: GncProgressListener? = null
    ): String {
        val importer = SqliteImporter(context, inputStream, listener)
        val books = importer.parse(uri)
        val book = books[books.lastIndex]
        val bookUID = book.uid
        setUpDbAdapters(bookUID)
        return bookUID
    }

    protected fun setUpDbAdapters(bookUID: String) {
        close()
        val databaseHelper = DatabaseHelper(context, bookUID)
        val mainHolder = databaseHelper.holder
        commoditiesDbAdapter = CommoditiesDbAdapter(mainHolder)
        transactionsDbAdapter = TransactionsDbAdapter(commoditiesDbAdapter)
        accountsDbAdapter = AccountsDbAdapter(transactionsDbAdapter)
        recurrenceDbAdapter = RecurrenceDbAdapter(mainHolder)
        scheduledActionDbAdapter =
            ScheduledActionDbAdapter(recurrenceDbAdapter, transactionsDbAdapter)
        budgetsDbAdapter = BudgetsDbAdapter(recurrenceDbAdapter)
        pricesDbAdapter = PricesDbAdapter(commoditiesDbAdapter)
        dbHolder = mainHolder
    }

    @Before
    fun setUpHelper() {
        val bookUID = GnuCashApplication.activeBookUID
        assertThat(bookUID).isNotEmpty()
        booksDbAdapter = BooksDbAdapter.instance
        assertThat(booksDbAdapter.recordsCount).isOne()
        val book = booksDbAdapter.allRecords.first()
        assertThat(book.uid).isEqualTo(bookUID)
        setUpDbAdapters(bookUID)
    }

    @After
    fun tearDownHelper() {
        close()
        DatabaseHelper.deleteFiles(context)
    }

    protected fun close() {
        if (::dbHolder.isInitialized) dbHolder.close()
    }

    protected fun readFile(name: String): String {
        val stream = openResourceStream(name)
        val bytes = stream.readAllBytes()
        return String(bytes, StandardCharsets.UTF_8)
    }

    // Mark the transactions as ready to be exported.
    protected fun markForExport(where: String? = null) {
        var sql = "UPDATE ${TransactionEntry.TABLE_NAME} SET ${TransactionEntry.COLUMN_EXPORTED} = 0"
        if (where != null) {
            sql += " WHERE $where"
        }
        val db = dbHolder.db
        db.execSQL(sql)
    }
}
