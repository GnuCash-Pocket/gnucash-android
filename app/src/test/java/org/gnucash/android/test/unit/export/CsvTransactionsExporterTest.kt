package org.gnucash.android.test.unit.export

import org.assertj.core.api.Assertions.assertThat
import org.gnucash.android.app.GnuCashApplication
import org.gnucash.android.export.ExportFormat
import org.gnucash.android.export.ExportParams
import org.gnucash.android.export.csv.CsvTransactionsExporter
import org.gnucash.android.test.unit.BookHelperTest
import org.gnucash.android.util.TimestampHelper
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.Locale

class CsvTransactionsExporterTest : BookHelperTest() {
    private lateinit var originalDefaultLocale: Locale
    private val lineSeparator = "\n"

    @Before
    fun `save original default locale`() {
        originalDefaultLocale = Locale.getDefault()
    }

    @After
    fun `restore original default locale`() {
        Locale.setDefault(originalDefaultLocale)
    }

    @Test
    fun `generate export in US locale`() {
        Locale.setDefault(Locale.US)

        val context = GnuCashApplication.getAppContext()
        val bookUID = importGnuCashXml("multipleTransactionImport.xml")
        val exportParameters = ExportParams(ExportFormat.CSVT).apply {
            exportStartTime = TimestampHelper.getTimestampFromEpochZero()
            exportTarget = ExportParams.ExportTarget.SD_CARD
            setDeleteTransactionsAfterExport(false)
        }

        val exportedFiles = CsvTransactionsExporter(context, exportParameters, bookUID)
            .generateExport()

        assertThat(exportedFiles).hasSize(1)
        val file = File(exportedFiles[0])
        val lines = file.readLines()
        assertThat("\"Date\",\"Transaction ID\",\"Number\",\"Description\",\"Notes\",\"Commodity/Currency\",\"Void Reason\",\"Action\",\"Memo\",\"Full Account Name\",\"Account Name\",\"Amount With Sym\",\"Amount Num.\",\"Value With Sym\",\"Value Num.\",\"Reconcile\",\"Reconcile Date\",\"Rate/Price\"")
            .isEqualTo(lines[0])
        assertThat("\"8/23/16\",\"b33c8a6160494417558fd143731fc26a\",,\"Kahuna Burger\",,\"CURRENCY::USD\",,,,\"Expenses:Dining\",\"Dining\",\"\$10.00\",\"10.00\",\"\$10.00\",\"10.00\",\"n\",,\"1.00\"")
            .isEqualTo(lines[1])
        assertThat("\"8/23/16\",\"b33c8a6160494417558fd143731fc26a\",,\"Kahuna Burger\",,\"CURRENCY::USD\",,,,\"Assets:Cash in Wallet\",\"Cash in Wallet\",\"-\$10.00\",\"-10.00\",\"-\$10.00\",\"-10.00\",\"n\",,\"1.00\"")
            .isEqualTo(lines[2])
        assertThat("\"8/24/16\",\"64bbc3a03816427f9f82b2a2aa858f91\",,\"Kahuna Comma Vendors (,)\",,\"CURRENCY::USD\",,,,\"Expenses:Dining\",\"Dining\",\"\$23.45\",\"23.45\",\"\$23.45\",\"23.45\",\"n\",,\"1.00\"")
            .isEqualTo(lines[3])
        assertThat("\"8/24/16\",\"64bbc3a03816427f9f82b2a2aa858f91\",,\"Kahuna Comma Vendors (,)\",,\"CURRENCY::USD\",,,,\"Assets:Cash in Wallet\",\"Cash in Wallet\",\"-\$23.45\",\"-23.45\",\"-\$23.45\",\"-23.45\",\"n\",,\"1.00\"")
            .isEqualTo(lines[4])
    }

    @Test
    fun `generate export in German locale`() {
        Locale.setDefault(Locale.GERMANY)

        val context = GnuCashApplication.getAppContext()
        val bookUID = importGnuCashXml("multipleTransactionImport.xml")
        val exportParameters = ExportParams(ExportFormat.CSVT).apply {
            exportStartTime = TimestampHelper.getTimestampFromEpochZero()
            exportTarget = ExportParams.ExportTarget.SD_CARD
            setDeleteTransactionsAfterExport(false)
        }

        val exportedFiles = CsvTransactionsExporter(context, exportParameters, bookUID)
            .generateExport()

        assertThat(exportedFiles).hasSize(1)
        val file = File(exportedFiles[0])
        val lines = file.readLines()
        assertThat("\"Date\",\"Transaction ID\",\"Number\",\"Description\",\"Notes\",\"Commodity/Currency\",\"Void Reason\",\"Action\",\"Memo\",\"Full Account Name\",\"Account Name\",\"Amount With Sym\",\"Amount Num.\",\"Value With Sym\",\"Value Num.\",\"Reconcile\",\"Reconcile Date\",\"Rate/Price\"")
            .isEqualTo(lines[0])
        assertThat("\"23.08.16\",\"b33c8a6160494417558fd143731fc26a\",,\"Kahuna Burger\",,\"CURRENCY::USD\",,,,\"Expenses:Dining\",\"Dining\",\"10,00\u00a0US\$\",\"10,00\",\"10,00\u00a0US\$\",\"10,00\",\"n\",,\"1,00\"")
            .isEqualTo(lines[1])
        assertThat("\"23.08.16\",\"b33c8a6160494417558fd143731fc26a\",,\"Kahuna Burger\",,\"CURRENCY::USD\",,,,\"Assets:Cash in Wallet\",\"Cash in Wallet\",\"-10,00\u00a0US\$\",\"-10,00\",\"-10,00\u00a0US\$\",\"-10,00\",\"n\",,\"1,00\"")
            .isEqualTo(lines[2])
        assertThat("\"24.08.16\",\"64bbc3a03816427f9f82b2a2aa858f91\",,\"Kahuna Comma Vendors (,)\",,\"CURRENCY::USD\",,,,\"Expenses:Dining\",\"Dining\",\"23,45\u00a0US\$\",\"23,45\",\"23,45\u00a0US\$\",\"23,45\",\"n\",,\"1,00\"")
            .isEqualTo(lines[3])
        assertThat("\"24.08.16\",\"64bbc3a03816427f9f82b2a2aa858f91\",,\"Kahuna Comma Vendors (,)\",,\"CURRENCY::USD\",,,,\"Assets:Cash in Wallet\",\"Cash in Wallet\",\"-23,45\u00a0US\$\",\"-23,45\",\"-23,45\u00a0US\$\",\"-23,45\",\"n\",,\"1,00\"")
            .isEqualTo(lines[4])
    }

    @Test
    fun `export multiple currencies`() {
        Locale.setDefault(Locale.US)

        val context = GnuCashApplication.getAppContext()
        val bookUID = importGnuCashXml("common_1.gnucash")
        val exportParameters = ExportParams(ExportFormat.CSVT).apply {
            exportTarget = ExportParams.ExportTarget.SD_CARD
        }

        val exportedFiles = CsvTransactionsExporter(context, exportParameters, bookUID)
            .generateExport()

        assertThat(exportedFiles).hasSize(1)
        val file = File(exportedFiles[0])
        val actual = file.readText()

        val expectedBytes =
            javaClass.classLoader.getResourceAsStream("expected.common_1.csv").readAllBytes()
        val expected = String(expectedBytes, StandardCharsets.UTF_8)
        assertThat(actual).isEqualTo(expected)
    }
}