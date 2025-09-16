/*
 * Copyright (c) 2018 Semyannikov Gleb <nightdevgame@gmail.com>
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
package org.gnucash.android.export.csv

import android.content.Context
import com.opencsv.CSVWriterBuilder
import com.opencsv.ICSVWriter
import org.gnucash.android.R
import org.gnucash.android.db.DatabaseSchema
import org.gnucash.android.export.ExportParams
import org.gnucash.android.export.Exporter
import org.gnucash.android.gnc.GncProgressListener
import org.gnucash.android.model.Account
import org.gnucash.android.util.formatRGB
import java.io.IOException
import java.io.Writer

/**
 * Creates a GnuCash CSV account representation of the accounts and transactions
 *
 * @author Semyannikov Gleb <nightdevgame@gmail.com>
 */
class CsvAccountExporter(
    context: Context,
    params: ExportParams,
    bookUID: String,
    listener: GncProgressListener? = null
) : Exporter(context, params, bookUID, listener) {
    @Throws(ExporterException::class, IOException::class)
    override fun writeExport(writer: Writer, exportParams: ExportParams) {
        val csvWriter = CSVWriterBuilder(writer)
            .withSeparator(exportParams.csvSeparator)
            .withLineEnd(ICSVWriter.RFC4180_LINE_END)
            .build()
        writeExport(csvWriter)
        csvWriter.close()
    }

    /**
     * Writes out all the accounts in the system as CSV to the provided writer
     *
     * @param writer Destination for the CSV export
     */
    fun writeExport(writer: ICSVWriter) {
        val where = DatabaseSchema.AccountEntry.COLUMN_TEMPLATE + " = 0"
        val accounts = accountsDbAdapter.getSimpleAccounts(where, null, null)
        listener?.onAccountCount(accounts.size.toLong())

        val names = context.resources.getStringArray(R.array.csv_account_headers)
        writer.writeNext(names)

        val fields = arrayOfNulls<String>(names.size)
        for (account in accounts) {
            if (account.isRoot) continue
            if (account.isTemplate) continue
            cancellationSignal.throwIfCanceled()

            writeAccount(fields, account)
            writer.writeNext(fields)
        }
    }

    private fun writeAccount(fields: Array<String?>, account: Account) {
        fields[0] = account.accountType.name
        fields[1] = account.fullName
        fields[2] = account.name

        fields[3] = "" //Account code
        fields[4] = account.description
        fields[5] = formatRGB(account.color)
        fields[6] = account.note.orEmpty()

        fields[7] = account.commodity.currencyCode
        fields[8] = account.commodity.namespace
        fields[9] = format(account.isHidden)
        fields[10] = format(false) //Tax
        fields[11] = format(account.isPlaceholder)

        listener?.onAccount(account)
    }

    private fun format(value: Boolean): String {
        return if (value) "T" else "F"
    }
}
