package org.gnucash.android.test.unit.export

import androidx.core.net.toFile
import org.assertj.core.api.Assertions.assertThat
import org.gnucash.android.app.GnuCashApplication
import org.gnucash.android.export.ExportFormat
import org.gnucash.android.export.ExportParams
import org.gnucash.android.export.csv.CsvAccountExporter
import org.gnucash.android.test.unit.BookHelperTest
import org.junit.Test
import java.nio.charset.StandardCharsets
import java.util.Locale

class CsvAccountsTest : BookHelperTest() {
    @Test
    fun `export common accounts with multiple currencies`() {
        Locale.setDefault(Locale.US)

        val context = GnuCashApplication.getAppContext()
        val bookUID = importGnuCashXml("common_1.gnucash")
        val exportParameters = ExportParams(ExportFormat.CSVA).apply {
            exportTarget = ExportParams.ExportTarget.SD_CARD
        }

        val exportedFile = CsvAccountExporter(context, exportParameters, bookUID)
            .export()

        assertThat(exportedFile).isNotNull()
        val file = exportedFile!!.toFile()
        val actual = file.readText()

        val expectedBytes =
            openResourceStream("expected.acctchrt_common.accounts.csv").readAllBytes()
        val expected = String(expectedBytes, StandardCharsets.UTF_8)
        assertThat(actual).isEqualTo(expected)
    }
}