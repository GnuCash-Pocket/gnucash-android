package org.gnucash.android.test.unit.importer

import org.gnucash.android.test.unit.BookHelperTest
import org.gnucash.android.test.unit.importer.GncXmlHandlerTest.Companion.testCommon1
import org.junit.Test

class SqliteImporterTest : BookHelperTest() {
    @Test
    fun `import sqlite3 - common_1`() {
        val bookUID = importGnuCashSqlite("common_1.sqlite3.gnucash")
        testCommon1(
            bookUID,
            accountsDbAdapter,
            booksDbAdapter,
            budgetsDbAdapter,
            commoditiesDbAdapter,
            pricesDbAdapter,
            recurrenceDbAdapter,
            scheduledActionDbAdapter,
            transactionsDbAdapter
        )
    }
}