package org.gnucash.android.test.unit

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
        GnuCashApplication.getBooksDbAdapter().setActive(bookUID)
        val exportParameters = ExportParams(ExportFormat.CSVA).apply {
            exportStartTime = TimestampHelper.getTimestampFromEpochZero()
            exportTarget = ExportParams.ExportTarget.SD_CARD
            setDeleteTransactionsAfterExport(false)
        }

        val exportedFiles = CsvTransactionsExporter(context, exportParameters, bookUID).generateExport()

        assertThat(exportedFiles).hasSize(1)
        val file = File(exportedFiles[0])
        assertThat(file.readText()).isEqualTo("""
            Date,Transaction ID,Number,Description,Notes,Commodity/Currency,Void Reason,Action,Memo,Full Account Name,Account Name,Amount With Sym.,Amount Num,Reconcile,Reconcile Date,Rate/Price,
            2016-08-23,b33c8a6160494417558fd143731fc26a,,Kahuna Burger,,CURRENCY::USD,,,,Expenses:Dining,Dining,${'$'}10.00,10.00,n,,1.00
            ,,,,,,,,,Assets:Cash in Wallet,Cash in Wallet,-${'$'}10.00,-10.00,n,,1.00
            2016-08-24,64bbc3a03816427f9f82b2a2aa858f91,,"Kahuna Comma Vendors (,)",,CURRENCY::USD,,,,Expenses:Dining,Dining,${'$'}23.45,23.45,n,,1.00
            ,,,,,,,,,Assets:Cash in Wallet,Cash in Wallet,-${'$'}23.45,-23.45,n,,1.00${"\n"}
        """.trimIndent())
    }

    @Test
    fun `generate export in German locale`() {
        Locale.setDefault(Locale.GERMANY)

        val context = GnuCashApplication.getAppContext()
        val bookUID = importGnuCashXml("multipleTransactionImport.xml")
        GnuCashApplication.getBooksDbAdapter().setActive(bookUID)
        val exportParameters = ExportParams(ExportFormat.CSVA).apply {
            exportStartTime = TimestampHelper.getTimestampFromEpochZero()
            exportTarget = ExportParams.ExportTarget.SD_CARD
            setDeleteTransactionsAfterExport(false)
        }

        val exportedFiles = CsvTransactionsExporter(context, exportParameters, bookUID).generateExport()

        assertThat(exportedFiles).hasSize(1)
        val file = File(exportedFiles[0])
        assertThat(file.readText()).isEqualTo("""
            Date,Transaction ID,Number,Description,Notes,Commodity/Currency,Void Reason,Action,Memo,Full Account Name,Account Name,Amount With Sym.,Amount Num,Reconcile,Reconcile Date,Rate/Price,
            2016-08-23,b33c8a6160494417558fd143731fc26a,,Kahuna Burger,,CURRENCY::USD,,,,Expenses:Dining,Dining,"10,00${"\u00a0"}US${'$'}","10,00",n,,"1,00"
            ,,,,,,,,,Assets:Cash in Wallet,Cash in Wallet,"-10,00${"\u00a0"}US${'$'}","-10,00",n,,"1,00"
            2016-08-24,64bbc3a03816427f9f82b2a2aa858f91,,"Kahuna Comma Vendors (,)",,CURRENCY::USD,,,,Expenses:Dining,Dining,"23,45${"\u00a0"}US${'$'}","23,45",n,,"1,00"
            ,,,,,,,,,Assets:Cash in Wallet,Cash in Wallet,"-23,45${"\u00a0"}US${'$'}","-23,45",n,,"1,00"${"\n"}
        """.trimIndent())
    }
}