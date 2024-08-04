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
import org.gnucash.android.R
import org.gnucash.android.export.ExportParams
import org.gnucash.android.export.Exporter
import org.gnucash.android.model.Account
import org.gnucash.android.model.Money.CurrencyMismatchException
import org.gnucash.android.model.Split
import org.gnucash.android.model.TransactionType
import org.gnucash.android.util.PreferencesHelper
import org.gnucash.android.util.TimestampHelper
import org.joda.time.format.DateTimeFormat
import timber.log.Timber
import java.io.FileWriter
import java.io.IOException
import java.util.Arrays

/**
 * Creates a GnuCash CSV transactions representation of the accounts and transactions
 *
 * @author Semyannikov Gleb <nightdevgame></nightdevgame>@gmail.com>
 */
class CsvTransactionsExporter(context: Context,
                              params: ExportParams,
                              bookUID: String) : Exporter(context, params, bookUID) {
    private val mCsvSeparator = params.csvSeparator
    private val dateFormat = DateTimeFormat.forPattern("yyyy-MM-dd")

    @Throws(ExporterException::class)
    override fun generateExport(): List<String> {
        val outputFile = getExportCacheFilePath()
        var csvWriter: CsvWriter? = null

        try {
            csvWriter = CsvWriter(FileWriter(outputFile), mCsvSeparator.toString())
            generateExport(csvWriter)
            return listOf(outputFile)
        } catch (ex: IOException) {
            Timber.e(ex, "Error exporting CSV")
            throw ExporterException(mExportParams, ex)
        } finally {
            csvWriter?.close()
            close()
        }
    }

    @Throws(IOException::class, CurrencyMismatchException::class)
    private fun writeSplitsToCsv(splits: List<Split>, writer: CsvWriter) {
        val accountCache: MutableMap<String, Account> = HashMap()
        for ((index, split) in splits.withIndex()) {
            if (index > 0) {
                // The first split is on the same line as the transactions. But after that, the
                // transaction-specific fields are empty.
                writer.write("" // Date
                        + mCsvSeparator // Transaction ID
                        + mCsvSeparator // Number
                        + mCsvSeparator // Description
                        + mCsvSeparator // Notes
                        + mCsvSeparator // Commodity/Currency
                        + mCsvSeparator // Void Reason
                        + mCsvSeparator // Action
                        + mCsvSeparator // Memo
                )
            }
            writer.writeToken(split.memo)
            val accountUID = split.accountUID!!
            val account = accountCache.getOrPut(accountUID) {
                mAccountsDbAdapter.getRecord(accountUID)
            }
            writer.writeToken(account.fullName)
            writer.writeToken(account.name)
            val sign = if (split.type == TransactionType.CREDIT) "-" else ""
            writer.writeToken(sign + split.quantity!!.formattedString())
            writer.writeToken(sign + split.quantity!!.formattedStringWithoutSymbol())
            writer.writeToken(split.reconcileState.toString())
            if (split.reconcileState == Split.FLAG_RECONCILED) {
                val recDateString = dateFormat.print(split.reconcileDate.getTime())
                writer.writeToken(recDateString)
            } else {
                writer.writeToken(null)
            }
            writer.writeEndToken((split.quantity!! / split.value!!).formattedStringWithoutSymbol())
        }
    }

    @Throws(ExporterException::class)
    private fun generateExport(csvWriter: CsvWriter) {
        try {
            mContext.resources.getStringArray(R.array.csv_transaction_headers).forEach {
               csvWriter.writeToken(it)
            }
            csvWriter.newLine()
            val cursor = mTransactionsDbAdapter.fetchTransactionsModifiedSince(mExportParams.exportStartTime)
            Timber.d("Exporting %d transactions to CSV", cursor.count)
            while (cursor.moveToNext()) {
                val transaction = mTransactionsDbAdapter.buildModelInstance(cursor)
                csvWriter.writeToken(dateFormat.print(transaction.timeMillis))
                csvWriter.writeToken(transaction.uID)
                csvWriter.writeToken(null)  // Transaction number
                csvWriter.writeToken(transaction.description)
                csvWriter.writeToken(transaction.note)
                csvWriter.writeToken("CURRENCY::${transaction.currencyCode}")
                csvWriter.writeToken(null)  // Void Reason
                csvWriter.writeToken(null)  // Action
                writeSplitsToCsv(transaction.splits, csvWriter)
            }
            cursor.close()
            PreferencesHelper.setLastExportTime(TimestampHelper.getTimestampFromNow())
        } catch (e: Exception) {
            Timber.e(e, "Error while exporting transactions to CSV")
            throw ExporterException(mExportParams, e)
        }
    }
}
