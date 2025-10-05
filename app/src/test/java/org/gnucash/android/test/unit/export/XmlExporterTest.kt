package org.gnucash.android.test.unit.export

import org.assertj.core.api.Assertions.assertThat
import org.gnucash.android.export.ExportFormat
import org.gnucash.android.export.ExportParams
import org.gnucash.android.export.xml.GncXmlExporter
import org.gnucash.android.test.unit.BookHelperTest
import org.junit.Ignore
import org.junit.Test
import java.io.StringWriter
import java.nio.charset.StandardCharsets

class XmlExporterTest : BookHelperTest() {

    private fun readFile(name: String): String {
        val stream = javaClass.classLoader!!.getResourceAsStream(name)
        val bytes = stream.readAllBytes()
        return String(bytes, StandardCharsets.UTF_8)
    }

    @Test
    fun `the exported file is exactly like the imported file - common accounts`() {
        val bookUID = importGnuCashXml("acctchrt_common.gnucash")
        assertThat(bookUID).isEqualTo("a7682e5d878e43cea216611401f08463")
        assertThat(accountsDbAdapter.recordsCount).isEqualTo(64)
        assertThat(transactionsDbAdapter.recordsCount).isZero()

        val exportParams = ExportParams(ExportFormat.XML).apply {
            isCompressed = false
        }
        val exporter = GncXmlExporter(context, exportParams, bookUID)

        val writer = StringWriter()
        exporter.export(bookUID, writer)
        val actual = writer.toString().replace("\r\n", "\n")
        val actualIgnoreRoot = actual.substring(actual.indexOf('>', actual.indexOf("<gnc-v2")))

        val expected = readFile("expected.acctchrt_common.gnucash").replace("\r\n", "\n")
        val expectedIgnoreRoot = expected.substring(expected.indexOf('>', expected.indexOf("<gnc-v2")))
        assertThat(actualIgnoreRoot).isEqualTo(expectedIgnoreRoot)
    }

    @Test
    @Ignore("FIXME - the UIDs for templates and scheduled actions are generated")
    fun `the exported file is exactly like the imported file - common accounts with 1 of each type`() {
        val bookUID = importGnuCashXml("common_1.gnucash")
        assertThat(bookUID).isEqualTo("a7682e5d878e43cea216611401f08463")

        val exportParams = ExportParams(ExportFormat.XML).apply {
            isCompressed = false
        }
        val exporter = GncXmlExporter(context, exportParams, bookUID)

        val writer = StringWriter()
        exporter.export(bookUID, writer)
        val actual = writer.toString()
        val actualIgnoreRoot = actual.substring(actual.indexOf('>', actual.indexOf("<gnc-v2")))
            .replace("\r\n", "\n")

        val expected = readFile("expected.common_1.gnucash")
        val expectedIgnoreRoot = expected.trim()
            .substring(expected.indexOf('>', expected.indexOf("<gnc-v2")))
            .replace("\r\n", "\n")
        assertThat(actualIgnoreRoot).isEqualTo(expectedIgnoreRoot)
    }
}