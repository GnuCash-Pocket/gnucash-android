package org.gnucash.android.test.unit.export

import androidx.core.net.toFile
import com.opencsv.ICSVWriter.RFC4180_LINE_END
import org.assertj.core.api.Assertions.assertThat
import org.gnucash.android.export.ExportFormat
import org.gnucash.android.export.ExportParams
import org.gnucash.android.export.csv.CsvTransactionsExporter
import org.gnucash.android.test.unit.BookHelperTest
import org.gnucash.android.util.TimestampHelper
import org.gnucash.android.util.applyLocale
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.nio.charset.StandardCharsets
import java.util.Locale

class CsvTransactionsExporterTest : BookHelperTest() {
    private lateinit var originalDefaultLocale: Locale

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
        val context = context.applyLocale(Locale.US)

        val bookUID = importGnuCashXml("multipleTransactionImport.xml")
        val exportParameters = ExportParams(ExportFormat.CSVT).apply {
            exportStartTime = TimestampHelper.getTimestampFromEpochZero()
            exportTarget = ExportParams.ExportTarget.SD_CARD
            setDeleteTransactionsAfterExport(false)
        }

        val exportedFile = CsvTransactionsExporter(context, exportParameters, bookUID).export()

        assertThat(exportedFile).isNotNull()
        val file = exportedFile!!.toFile()
        val lines = file.readLines()
        assertThat(lines[0])
            .isEqualTo("\"Date\",\"Transaction ID\",\"Number\",\"Description\",\"Notes\",\"Commodity/Currency\",\"Void Reason\",\"Action\",\"Memo\",\"Full Account Name\",\"Account Name\",\"Amount With Sym\",\"Amount Num.\",\"Value With Sym\",\"Value Num.\",\"Reconcile\",\"Reconcile Date\",\"Rate/Price\"")
        assertThat(lines[1])
            .isEqualTo("\"2016-08-23\",\"b33c8a6160494417558fd143731fc26a\",\"\",\"Kahuna Burger\",\"\",\"CURRENCY::USD\",\"\",\"\",\"\",\"Assets:Cash in Wallet\",\"Cash in Wallet\",\"-\$10.00\",\"-10.00\",\"-\$10.00\",\"-10.00\",\"n\",\"\",\"1.0000\"")
        assertThat(lines[2])
            .isEqualTo("\"2016-08-23\",\"b33c8a6160494417558fd143731fc26a\",\"\",\"Kahuna Burger\",\"\",\"CURRENCY::USD\",\"\",\"\",\"\",\"Expenses:Dining\",\"Dining\",\"\$10.00\",\"10.00\",\"\$10.00\",\"10.00\",\"n\",\"\",\"1.0000\"")
        assertThat(lines[3])
            .isEqualTo("\"2016-08-24\",\"64bbc3a03816427f9f82b2a2aa858f91\",\"\",\"Kahuna Comma Vendors (,)\",\"\",\"CURRENCY::USD\",\"\",\"\",\"\",\"Assets:Cash in Wallet\",\"Cash in Wallet\",\"-\$23.45\",\"-23.45\",\"-\$23.45\",\"-23.45\",\"n\",\"\",\"1.0000\"")
        assertThat(lines[4])
            .isEqualTo("\"2016-08-24\",\"64bbc3a03816427f9f82b2a2aa858f91\",\"\",\"Kahuna Comma Vendors (,)\",\"\",\"CURRENCY::USD\",\"\",\"\",\"\",\"Expenses:Dining\",\"Dining\",\"\$23.45\",\"23.45\",\"\$23.45\",\"23.45\",\"n\",\"\",\"1.0000\"")
    }

    @Test
    fun `generate export in Italian locale`() {
        val context = context.applyLocale(Locale.ITALY)

        val bookUID = importGnuCashXml("multipleTransactionImport.xml")
        val exportParameters = ExportParams(ExportFormat.CSVT).apply {
            exportStartTime = TimestampHelper.getTimestampFromEpochZero()
            exportTarget = ExportParams.ExportTarget.SD_CARD
            setDeleteTransactionsAfterExport(false)
        }

        val exportedFile = CsvTransactionsExporter(context, exportParameters, bookUID)
            .export()

        assertThat(exportedFile).isNotNull()
        val file = exportedFile!!.toFile()
        val lines = file.readLines()
        assertThat(lines[0])
            .isEqualTo("\"Data\",\"ID transazione\",\"Numero\",\"Descrizione\",\"Note\",\"Commodity/Valuta\",\"Motivo annullamento\",\"Operazione\",\"Promemoria\",\"Nome completo dell'account\",\"Nome del conto\",\"Importo con Simb\",\"Importo Num.\",\"Valore con Simb\",\"Valore Num.\",\"Riconcilia\",\"Data di riconciliazione\",\"Tasso/Prezzo\"")
        assertThat(lines[1])
            .isEqualTo("\"2016-08-23\",\"b33c8a6160494417558fd143731fc26a\",\"\",\"Kahuna Burger\",\"\",\"CURRENCY::USD\",\"\",\"\",\"\",\"Assets:Cash in Wallet\",\"Cash in Wallet\",\"-10,00 \$\",\"-10,00\",\"-10,00 \$\",\"-10,00\",\"n\",\"\",\"1,0000\"")
        assertThat(lines[2])
            .isEqualTo("\"2016-08-23\",\"b33c8a6160494417558fd143731fc26a\",\"\",\"Kahuna Burger\",\"\",\"CURRENCY::USD\",\"\",\"\",\"\",\"Expenses:Dining\",\"Dining\",\"10,00 \$\",\"10,00\",\"10,00 \$\",\"10,00\",\"n\",\"\",\"1,0000\"")
        assertThat(lines[3])
            .isEqualTo("\"2016-08-24\",\"64bbc3a03816427f9f82b2a2aa858f91\",\"\",\"Kahuna Comma Vendors (,)\",\"\",\"CURRENCY::USD\",\"\",\"\",\"\",\"Assets:Cash in Wallet\",\"Cash in Wallet\",\"-23,45 \$\",\"-23,45\",\"-23,45 \$\",\"-23,45\",\"n\",\"\",\"1,0000\"")
        assertThat(lines[4])
            .isEqualTo("\"2016-08-24\",\"64bbc3a03816427f9f82b2a2aa858f91\",\"\",\"Kahuna Comma Vendors (,)\",\"\",\"CURRENCY::USD\",\"\",\"\",\"\",\"Expenses:Dining\",\"Dining\",\"23,45 \$\",\"23,45\",\"23,45 \$\",\"23,45\",\"n\",\"\",\"1,0000\"")
    }

    @Test
    fun `export multiple currencies`() {
        val context = context.applyLocale(Locale.GERMANY)

        val bookUID = importGnuCashXml("common_1.gnucash")
        val exportParameters = ExportParams(ExportFormat.CSVT).apply {
            exportTarget = ExportParams.ExportTarget.SD_CARD
        }

        val exportedFile = CsvTransactionsExporter(context, exportParameters, bookUID)
            .export()

        assertThat(exportedFile).isNotNull()
        val file = exportedFile!!.toFile()
        val actual = file.readText()
            .replace(RFC4180_LINE_END, System.lineSeparator())
            .replace(' ', ' ')
            .replace("1,0753", "1 + 7/93")
            .replace("0,0067", "888/133253")

        val expectedBytes = openResourceStream("expected.common_1.de.csv").readAllBytes()
        val expected = String(expectedBytes, StandardCharsets.UTF_8)
        assertThat(actual).isEqualTo(expected)
    }
}