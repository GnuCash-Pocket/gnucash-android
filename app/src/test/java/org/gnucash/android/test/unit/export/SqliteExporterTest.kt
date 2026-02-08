package org.gnucash.android.test.unit.export

import androidx.core.net.toFile
import org.assertj.core.api.Assertions.assertThat
import org.gnucash.android.export.ExportFormat
import org.gnucash.android.export.ExportParams
import org.gnucash.android.export.sql.SqliteExporter
import org.gnucash.android.test.unit.BookHelperTest
import org.gnucash.android.test.unit.importer.GncXmlHandlerTest.Companion.testCommon1
import org.junit.Test

class SqliteExporterTest : BookHelperTest() {
    /**
     * Import some book, then export it as SQLite, and then re-import it.
     */
    @Test
    fun `export sqlite3 - common_1`() {
        val bookUID = importGnuCashXml("common_1.gnucash")
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

        val exportParams = ExportParams(ExportFormat.SQLITE)
        val exporter = SqliteExporter(context, exportParams, bookUID)
        val uri = exporter.export()
        assertThat(uri).isNotNull()
        val file = uri!!.toFile()
        assertThat(file).exists().hasExtension("xac")
        assertThat(file.length()).isGreaterThan(0L)

        close()
        val inputStream = file.inputStream()
        val bookUID2 = importGnuCashSqlite(inputStream)
        assertThat(bookUID).isEqualTo(bookUID2)
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