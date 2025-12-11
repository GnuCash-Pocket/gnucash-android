package org.gnucash.android.export

import android.content.Context
import org.gnucash.android.export.csv.CsvAccountExporter
import org.gnucash.android.export.csv.CsvTransactionsExporter
import org.gnucash.android.export.ofx.OfxExporter
import org.gnucash.android.export.qif.QifExporter
import org.gnucash.android.export.sql.SqliteExporter
import org.gnucash.android.export.xml.GncXmlExporter
import org.gnucash.android.gnc.GncProgressListener

object ExporterFactory {
    fun create(
        context: Context,
        exportParams: ExportParams,
        bookUID: String,
        listener: GncProgressListener?
    ): Exporter {
        return when (exportParams.exportFormat) {
            ExportFormat.CSVA -> CsvAccountExporter(
                context,
                exportParams,
                bookUID,
                listener
            )

            ExportFormat.CSVT -> CsvTransactionsExporter(
                context,
                exportParams,
                bookUID,
                listener
            )

            ExportFormat.OFX -> OfxExporter(context, exportParams, bookUID, listener)

            ExportFormat.QIF -> QifExporter(context, exportParams, bookUID, listener)

            ExportFormat.SQLITE -> SqliteExporter(context, exportParams, bookUID, listener)

            else -> GncXmlExporter(context, exportParams, bookUID, listener)
        }
    }
}