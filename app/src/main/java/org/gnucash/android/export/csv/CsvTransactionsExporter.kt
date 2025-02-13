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
import java.io.FileWriter
import java.io.IOException
import org.gnucash.android.R
import org.gnucash.android.export.ExportParams
import org.gnucash.android.export.Exporter
import org.gnucash.android.model.Account
import org.gnucash.android.model.Split
import org.gnucash.android.model.TransactionType
import org.gnucash.android.util.PreferencesHelper
import org.gnucash.android.util.TimestampHelper
import org.joda.time.format.DateTimeFormat
import timber.log.Timber

/**
 * Creates a GnuCash CSV transactions representation of the accounts and transactions
 *
 * @author Semyannikov Gleb <nightdevgame></nightdevgame>@gmail.com>
 */
class CsvTransactionsExporter(
    context: Context,
    params: ExportParams,
    bookUID: String
) : Exporter(context, params, bookUID) {
    private val mCsvSeparator = params.csvSeparator
    private val dateFormat = DateTimeFormat.forPattern("yyyy-MM-dd")
    private val accountCache: MutableMap<String, Account> = HashMap()

    @Throws(ExporterException::class)
    override fun generateExport(): List<String> {
        val outputFile = getExportCacheFilePath()

        try {
            FileWriter(outputFile).use { writer ->
                val csvWriter = CSVWriterBuilder(writer).withSeparator(mCsvSeparator).build()
                generateExport(csvWriter)
                csvWriter.close()
            }
            return listOf(outputFile)
        } catch (e: Exception) {
            Timber.e(e, "Error exporting CSV")
            throw ExporterException(mExportParams, e)
        } finally {
            try {
                close()
            } catch (ignore: java.lang.Exception) {
            }
        }
    }

    @Throws(IOException::class)
    private fun writeSplitsToCsv(splits: List<Split>, fields: Array<String?>, writer: ICSVWriter) {
        for (split in splits) {
            fields[8] = maybeNull(split.memo)
            val accountUID = split.accountUID!!
            val account = accountCache.getOrPut(accountUID) {
                mAccountsDbAdapter.getSimpleRecord(accountUID)!!
            }
            fields[9] = account.fullName
            fields[10] = account.name

            val sign = if (split.type == TransactionType.CREDIT) "-" else ""
            val quantity = split.quantity!!
            fields[11] = sign + quantity.formattedString()
            fields[12] = sign + quantity.formattedStringWithoutSymbol()
            val value = split.value!!
            fields[13] = sign + value.formattedString()
            fields[14] = sign + value.formattedStringWithoutSymbol()

            fields[15] = split.reconcileState.toString()
            if (split.reconcileState == Split.FLAG_RECONCILED) {
                val recDateString = dateFormat.print(split.reconcileDate.getTime())
                fields[16] = recDateString
            } else {
                fields[16] = null
            }
            if (quantity.isAmountZero) {
                fields[17] = "1"
            } else {
                fields[17] = (value / quantity.toBigDecimal()).formattedStringWithoutSymbol()
            }

            writer.writeNext(fields)
        }
    }

    @Throws(ExporterException::class)
    private fun generateExport(writer: ICSVWriter) {
        try {
            val headers = mContext.resources.getStringArray(R.array.csv_transaction_headers)
            writer.writeNext(headers)

            val cursor =
                mTransactionsDbAdapter.fetchTransactionsModifiedSince(mExportParams.exportStartTime)
            Timber.d("Exporting %d transactions to CSV", cursor.count)
            while (cursor.moveToNext()) {
                val transaction = mTransactionsDbAdapter.buildModelInstance(cursor)
                val commodity = transaction.commodity
                val fields = Array<String?>(headers.size) { null }
                fields[0] = dateFormat.print(transaction.timeMillis)
                fields[1] = transaction.uID
                fields[2] = null  // Transaction number
                fields[3] = transaction.description
                fields[4] = maybeNull(transaction.note)
                fields[5] = "${commodity.namespace}::${commodity.currencyCode}"
                fields[6] = null  // Void Reason
                fields[7] = null  // Action
                writeSplitsToCsv(transaction.splits, fields, writer)
            }
            cursor.close()
            PreferencesHelper.setLastExportTime(TimestampHelper.getTimestampFromNow())
        } catch (e: Exception) {
            throw ExporterException(mExportParams, e)
        }
    }

    private fun maybeNull(s: String?): String? {
        if (s.isNullOrEmpty()) return null
        return s
    }
}
