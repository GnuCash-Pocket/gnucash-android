/*
 * Copyright (c) 2016 Àlex Magaz Graça <alexandre.magaz@gmail.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gnucash.android.test.unit.export

import androidx.core.net.toFile
import org.assertj.core.api.Assertions.assertThat
import org.gnucash.android.app.GnuCashApplication
import org.gnucash.android.export.ExportFormat
import org.gnucash.android.export.ExportParams
import org.gnucash.android.export.Exporter.ExporterException
import org.gnucash.android.export.ofx.OfxExporter
import org.gnucash.android.export.ofx.OfxHelper
import org.gnucash.android.export.ofx.OfxHelper.APP_ID
import org.gnucash.android.model.Account
import org.gnucash.android.model.Money
import org.gnucash.android.model.Money.Companion.createZeroInstance
import org.gnucash.android.model.Split
import org.gnucash.android.model.Transaction
import org.gnucash.android.test.unit.BookHelperTest
import org.gnucash.android.util.toMillis
import org.joda.time.LocalDate
import org.junit.Assert.assertThrows
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class OfxExporterTest : BookHelperTest() {
    /**
     * When there aren't new or modified transactions, the OFX exporter
     * shouldn't create any file.
     */
    @Test
    fun testWithNoTransactionsToExport_shouldNotCreateAnyFile() {
        val exportParameters = ExportParams(ExportFormat.OFX)
        val exporter = OfxExporter(
            context,
            exportParameters,
            GnuCashApplication.activeBookUID!!
        )
        assertThrows(ExporterException::class.java) { exporter.export() }
    }

    /**
     * Test that OFX files are generated
     */
    @Test
    fun testGenerateOFXExport() {
        val account = Account("Basic Account")
        val transaction = Transaction("One transaction")
        transaction.addSplit(Split(createZeroInstance("EUR"), account))
        account.addTransaction(transaction)

        accountsDbAdapter.addRecord(account)

        val exportParameters = ExportParams(ExportFormat.OFX)
        val exporter = OfxExporter(
            context,
            exportParameters,
            GnuCashApplication.activeBookUID!!
        )
        val exportedFile = exporter.export()

        assertThat(exportedFile).isNotNull()
        val file = exportedFile!!.toFile()
        assertThat(file).exists().hasExtension("ofx")
        assertThat(file.length()).isGreaterThan(0L)
        val actual = file.readText()
        file.delete()
        assertThat(actual).startsWith("ENCODING:UTF-8")
    }

    @Test
    fun `the exported file is exactly as expected`() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        val date = LocalDate(2025, 11, 26).toMillis()

        val account = Account("Basic Account")
        val transaction = Transaction("One transaction")
        transaction.setUID("9dabf93ab0444ffabab513329286b691")
        transaction.time = date
        transaction.addSplit(Split(Money(123.45, "EUR"), account))

        accountsDbAdapter.addRecord(account)
        transactionsDbAdapter.addRecord(transaction)

        val exportParameters = ExportParams(ExportFormat.OFX)
        val exporter = OfxExporter(
            context,
            exportParameters,
            GnuCashApplication.activeBookUID!!
        )
        val exportedFile = exporter.export()

        assertThat(exportedFile).isNotNull()
        val file = exportedFile!!.toFile()
        assertThat(file).exists().hasExtension("ofx")
        assertThat(file.length()).isGreaterThan(0L)
        val actual = file.readText().expect("20251126")
        file.delete()

        val expected = readFile("expected.one.ofx").trimEnd().replace("\r\n", "\n")
        assertThat(actual).isEqualTo(expected)
    }

    @Test
    fun testDateTime() {
        val tz = TimeZone.getTimeZone("EST")
        val cal = Calendar.getInstance()
        cal.timeZone = tz
        cal[Calendar.YEAR] = 1996
        cal[Calendar.MONTH] = Calendar.DECEMBER
        cal[Calendar.DAY_OF_MONTH] = 5
        cal[Calendar.HOUR_OF_DAY] = 13
        cal[Calendar.MINUTE] = 22
        cal[Calendar.SECOND] = 0
        cal[Calendar.MILLISECOND] = 124

        var formatted = OfxHelper.formatTime(cal.timeInMillis, tz)
        assertThat(formatted).isEqualTo("19961205132200.124[-5:EST]")

        cal[Calendar.MONTH] = Calendar.OCTOBER
        formatted = OfxHelper.formatTime(cal.timeInMillis, tz)
        assertThat(formatted).isEqualTo("19961005142200.124[-4:EDT]")
    }

    private fun String.expect(date: String): String {
        return trimEnd().replace("\r\n", "\n")
            .replace(Regex("<BANKID>${APP_ID}</BANKID>"), "<BANKID>org.gnucash.pocket</BANKID>")
            .replace(Regex("<DTASOF>\\d+\\.\\d\\d\\d\\[0:UTC\\]</DTASOF>"), "<DTASOF>${date}</DTASOF>")
            .replace(Regex("<DTEND>\\d+\\.\\d\\d\\d\\[0:UTC\\]</DTEND>"), "<DTEND>${date}</DTEND>")
            .replace(Regex("<DTUSER>\\d+\\.\\d\\d\\d\\[0:UTC\\]</DTUSER>"), "<DTUSER>${date}</DTUSER>")
    }
}