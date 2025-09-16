/*
 * Copyright (c) 2018-2024 GnuCash Android developers
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
import com.opencsv.ICSVWriter.RFC4180_LINE_END
import org.gnucash.android.R
import org.gnucash.android.export.ExportParams
import org.gnucash.android.export.Exporter
import org.gnucash.android.gnc.GncProgressListener
import org.gnucash.android.model.Account
import org.gnucash.android.model.Money
import org.gnucash.android.model.Split
import org.gnucash.android.model.Transaction
import org.gnucash.android.model.TransactionType
import org.gnucash.android.util.PreferencesHelper
import org.gnucash.android.util.PreferencesHelper.setLastExportTime
import org.gnucash.android.util.TimestampHelper
import org.joda.time.format.ISODateTimeFormat
import timber.log.Timber
import java.io.Writer
import java.text.DecimalFormat
import kotlin.math.max

/**
 * Creates a GnuCash CSV transactions representation of the accounts and transactions
 *
 * @author Semyannikov Gleb <nightdevgame@gmail.com>
 */
class CsvTransactionsExporter(
    context: Context,
    params: ExportParams,
    bookUID: String,
    listener: GncProgressListener? = null
) : Exporter(context, params, bookUID, listener) {
    // TODO add option in export form for date format: US, UK, Europe, ISO, Locale
    private val dateFormat = ISODateTimeFormat.date()
    private val accountCache = mutableMapOf<String, Account>()
    private val rateFormat = DecimalFormat.getNumberInstance().apply {
        minimumFractionDigits = 4
        maximumFractionDigits = 4
    }

    override fun writeExport(writer: Writer, exportParams: ExportParams) {
        val csvWriter = CSVWriterBuilder(writer)
            .withSeparator(exportParams.csvSeparator)
            .withLineEnd(RFC4180_LINE_END)
            .build()
        writeExport(csvWriter)
        csvWriter.close()
    }

    private fun writeSplitsToCsv(writer: ICSVWriter, fields: Array<String>, splits: List<Split>) {
        // Sort splits by account name.
        val splitToAccount = splits.associate {
            it.uid to accountsDbAdapter.getAccountFullName(it.accountUID!!)
        }
        val splitsByAccount = splits.sortedBy { splitToAccount[it.uid] }

        for (split in splitsByAccount) {
            fields[8] = split.memo.orEmpty()
            val accountUID = split.accountUID!!
            val account = accountCache.getOrPut(accountUID) {
                accountsDbAdapter.getSimpleRecord(accountUID)!!
            }
            fields[9] = account.fullName.orEmpty()
            fields[10] = account.name

            val sign = if (split.type == TransactionType.CREDIT) "-" else ""
            val quantity = split.quantity
            fields[11] = sign + quantity.formattedString()
            fields[12] = sign + quantity.formattedStringWithoutSymbol(withGrouping = false)
            val value = split.value
            fields[13] = sign + value.formattedString()
            fields[14] = sign + value.formattedStringWithoutSymbol(withGrouping = false)

            fields[15] = split.reconcileState.toString()
            if (split.reconcileState == Split.FLAG_RECONCILED) {
                fields[16] = dateFormat.print(split.reconcileDate)
            } else {
                fields[16] = ""
            }
            fields[17] = formatRate(value, quantity)

            writer.writeNext(fields)
        }
    }

    @Throws(ExporterException::class)
    private fun writeExport(writer: ICSVWriter) {
        val headers = context.resources.getStringArray(R.array.csv_transaction_headers)
        writer.writeNext(headers)

        val cursor =
            transactionsDbAdapter.fetchTransactionsModifiedSince(exportParams.exportStartTime)!!
        Timber.d("Exporting %d transactions to CSV", cursor.count)
        val fields = Array<String>(headers.size) { "" }
        try {
            if (cursor.moveToFirst()) {
                do {
                    cancellationSignal.throwIfCanceled()
                    val transaction = transactionsDbAdapter.buildModelInstance(cursor)
                    writeTransaction(writer, fields, transaction)
                } while (cursor.moveToNext());
            }
            setLastExportTime(context, TimestampHelper.timestampFromNow, bookUID)
        } finally {
            cursor.close()
        }
    }

    private fun writeTransaction(
        writer: ICSVWriter,
        fields: Array<String>,
        transaction: Transaction
    ) {
        val commodity = transaction.commodity

        fields[0] = dateFormat.print(transaction.time)
        fields[1] = transaction.uid
        fields[2] = ""  // Transaction number
        fields[3] = transaction.description.orEmpty()
        fields[4] = transaction.note.orEmpty()
        fields[5] = "${commodity.namespace}::${commodity.currencyCode}"
        fields[6] = ""  // Void Reason
        fields[7] = ""  // Action
        writeSplitsToCsv(writer, fields, transaction.splits)

        listener?.onTransaction(transaction)
    }

    private fun formatRate(value: Money, quantity: Money): String {
        if (quantity.isAmountZero) {
            return formatRate(1)
        }
        val precision = max(4, value.commodity.smallestFractionDigits)
        val numerator = value.toBigDecimal().setScale(precision)
        val denominator = quantity.toBigDecimal().setScale(precision)
        return formatRate(numerator / denominator)
    }

    private fun formatRate(rate: Number): String {
        return rateFormat.format(rate)
    }
}
