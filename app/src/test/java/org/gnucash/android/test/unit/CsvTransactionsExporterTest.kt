package org.gnucash.android.test.unit

import androidx.core.net.toFile
import org.assertj.core.api.Assertions.assertThat
import org.gnucash.android.app.GnuCashApplication
import org.gnucash.android.export.ExportFormat
import org.gnucash.android.export.ExportParams
import org.gnucash.android.export.csv.CsvTransactionsExporter
import org.gnucash.android.util.TimestampHelper
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.File
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

        val exportedFile = CsvTransactionsExporter(context, exportParameters, bookUID).generateExport()

        assertThat(exportedFile).isNotNull()
        val file = exportedFile!!.toFile()
        assertThat(file.readText()).isEqualTo(
            "\"Date\",\"Transaction ID\",\"Number\",\"Description\",\"Notes\",\"Commodity/Currency\",\"Void Reason\",\"Action\",\"Memo\",\"Full Account Name\",\"Account Name\",\"Amount With Sym\",\"Amount Num.\",\"Value With Sym\",\"Value Num.\",\"Reconcile\",\"Reconcile Date\",\"Rate/Price\"$lineSeparator"
            + "\"2016-08-23\",\"b33c8a6160494417558fd143731fc26a\",,\"Kahuna Burger\",,\"CURRENCY::USD\",,,,\"Expenses:Dining\",\"Dining\",\"\$10.00\",\"10.00\",\"\$10.00\",\"10.00\",\"n\",,\"1.00\"$lineSeparator"
            + "\"2016-08-23\",\"b33c8a6160494417558fd143731fc26a\",,\"Kahuna Burger\",,\"CURRENCY::USD\",,,,\"Assets:Cash in Wallet\",\"Cash in Wallet\",\"-\$10.00\",\"-10.00\",\"-\$10.00\",\"-10.00\",\"n\",,\"1.00\"$lineSeparator"
            + "\"2016-08-24\",\"64bbc3a03816427f9f82b2a2aa858f91\",,\"Kahuna Comma Vendors (,)\",,\"CURRENCY::USD\",,,,\"Expenses:Dining\",\"Dining\",\"\$23.45\",\"23.45\",\"\$23.45\",\"23.45\",\"n\",,\"1.00\"$lineSeparator"
            + "\"2016-08-24\",\"64bbc3a03816427f9f82b2a2aa858f91\",,\"Kahuna Comma Vendors (,)\",,\"CURRENCY::USD\",,,,\"Assets:Cash in Wallet\",\"Cash in Wallet\",\"-\$23.45\",\"-23.45\",\"-\$23.45\",\"-23.45\",\"n\",,\"1.00\"$lineSeparator"
        )
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

        val exportedFile = CsvTransactionsExporter(context, exportParameters, bookUID).generateExport()

        assertThat(exportedFile).isNotNull()
        val file = exportedFile!!.toFile()
        assertThat(file.readText()).isEqualTo(
            "\"Date\",\"Transaction ID\",\"Number\",\"Description\",\"Notes\",\"Commodity/Currency\",\"Void Reason\",\"Action\",\"Memo\",\"Full Account Name\",\"Account Name\",\"Amount With Sym\",\"Amount Num.\",\"Value With Sym\",\"Value Num.\",\"Reconcile\",\"Reconcile Date\",\"Rate/Price\"$lineSeparator"
            + "\"2016-08-23\",\"b33c8a6160494417558fd143731fc26a\",,\"Kahuna Burger\",,\"CURRENCY::USD\",,,,\"Expenses:Dining\",\"Dining\",\"10,00\u00a0US\$\",\"10,00\",\"10,00\u00a0US\$\",\"10,00\",\"n\",,\"1,00\"$lineSeparator"
            + "\"2016-08-23\",\"b33c8a6160494417558fd143731fc26a\",,\"Kahuna Burger\",,\"CURRENCY::USD\",,,,\"Assets:Cash in Wallet\",\"Cash in Wallet\",\"-10,00\u00a0US\$\",\"-10,00\",\"-10,00\u00a0US\$\",\"-10,00\",\"n\",,\"1,00\"$lineSeparator"
            + "\"2016-08-24\",\"64bbc3a03816427f9f82b2a2aa858f91\",,\"Kahuna Comma Vendors (,)\",,\"CURRENCY::USD\",,,,\"Expenses:Dining\",\"Dining\",\"23,45\u00a0US\$\",\"23,45\",\"23,45\u00a0US\$\",\"23,45\",\"n\",,\"1,00\"$lineSeparator"
            + "\"2016-08-24\",\"64bbc3a03816427f9f82b2a2aa858f91\",,\"Kahuna Comma Vendors (,)\",,\"CURRENCY::USD\",,,,\"Assets:Cash in Wallet\",\"Cash in Wallet\",\"-23,45\u00a0US\$\",\"-23,45\",\"-23,45\u00a0US\$\",\"-23,45\",\"n\",,\"1,00\"$lineSeparator"
        )
    }
}