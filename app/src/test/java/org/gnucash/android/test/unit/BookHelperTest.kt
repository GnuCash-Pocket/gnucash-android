package org.gnucash.android.test.unit

import junit.framework.TestCase.fail
import org.assertj.core.api.Assertions.assertThat
import org.gnucash.android.BuildConfig
import org.gnucash.android.db.DatabaseHelper
import org.gnucash.android.db.DatabaseHolder
import org.gnucash.android.db.adapter.AccountsDbAdapter
import org.gnucash.android.db.adapter.BooksDbAdapter
import org.gnucash.android.db.adapter.BudgetsDbAdapter
import org.gnucash.android.db.adapter.CommoditiesDbAdapter
import org.gnucash.android.db.adapter.RecurrenceDbAdapter
import org.gnucash.android.db.adapter.ScheduledActionDbAdapter
import org.gnucash.android.db.adapter.TransactionsDbAdapter
import org.gnucash.android.export.xml.GncXmlHelper.TAG_ROOT
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
    private var importedHolder: DatabaseHolder? = null
    protected lateinit var booksDbAdapter: BooksDbAdapter

        protected lateinit var transactionsDbAdapter: TransactionsDbAdapter
    protected lateinit var accountsDbAdapter: AccountsDbAdapter
    protected lateinit var scheduledActionDbAdapter: ScheduledActionDbAdapter
    protected lateinit var commoditiesDbAdapter: CommoditiesDbAdapter
    protected lateinit var budgetsDbAdapter: BudgetsDbAdapter

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
        val databaseHelper = DatabaseHelper(context, bookUID)
        val mainHolder = databaseHelper.holder
        commoditiesDbAdapter = CommoditiesDbAdapter(mainHolder)
        transactionsDbAdapter = TransactionsDbAdapter(commoditiesDbAdapter)
        accountsDbAdapter = AccountsDbAdapter(transactionsDbAdapter)
        val recurrenceDbAdapter = RecurrenceDbAdapter(mainHolder)
        scheduledActionDbAdapter = ScheduledActionDbAdapter(recurrenceDbAdapter)
        budgetsDbAdapter = BudgetsDbAdapter(recurrenceDbAdapter)
        importedHolder = mainHolder
    }

    @Before
    open fun setUp() {
        booksDbAdapter = BooksDbAdapter.getInstance()
        booksDbAdapter.deleteAllRecords()
        assertThat(booksDbAdapter.recordsCount).isZero()
    }

    @After
    open fun tearDown() {
        transactionsDbAdapter.close()
        accountsDbAdapter.close()
        scheduledActionDbAdapter.close()
        importedHolder?.close()
    }

    private fun removeTag(xml: String, tag: String): String {
        val tagStart1 = "<$tag>\n"
        val tagStart2 = "<$tag>"
        val tagStart3 = "<$tag\n"
        val tagStart4 = "<$tag "
        val tagEnd1 = "</$tag>\n"
        val tagEnd2 = "</$tag>"
        var indexStart = xml.indexOf(tagStart1)
        if (indexStart < 0) {
            indexStart = xml.indexOf(tagStart2)
            if (indexStart < 0) {
                indexStart = xml.indexOf(tagStart3)
                if (indexStart < 0) {
                    indexStart = xml.indexOf(tagStart4)
                }
            }
        }
        while (indexStart > 0) {
            if (Character.isSpaceChar(xml[indexStart - 1])) {
                indexStart--
            } else {
                break
            }
        }
        var tagEnd = tagEnd1
        var indexEnd = xml.indexOf(tagEnd, indexStart + 1)
        if (indexEnd < 0) {
            tagEnd = tagEnd2
            indexEnd = xml.indexOf(tagEnd, indexStart + 1)
        }
        return xml.substring(0, indexStart) + xml.substring(indexEnd + tagEnd.length)
    }

    private fun insideTag(xml: String, tag: String): String {
        val tagStart = "<$tag>"
        val tagStartLF = "<$tag\n"
        val tagStartSP = "<$tag "
        val tagEnd = "</$tag>"
        var indexStart = xml.indexOf(tagStart)
        if (indexStart < 0) {
            indexStart = xml.indexOf(tagStartLF)
            if (indexStart < 0) {
                indexStart = xml.indexOf(tagStartSP)
            }
        }
        indexStart = xml.indexOf('>', indexStart + 1)
        val indexEnd = xml.indexOf(tagEnd, indexStart + 1)
        return xml.substring(indexStart, indexEnd)
    }

    protected fun insideRoot(xml: String): String {
        return insideTag(xml, TAG_ROOT)
    }

    companion object {
        init {
            Timber.plant(ConsoleTree(BuildConfig.DEBUG) as Timber.Tree)
        }
    }
}
