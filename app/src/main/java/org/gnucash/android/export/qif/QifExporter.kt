/*
 * Copyright (c) 2013 - 2014 Ngewi Fet <ngewif@gmail.com>
 * Copyright (c) 2014 Yongxin Wang <fefe.wyx@gmail.com>
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
package org.gnucash.android.export.qif

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.os.OperationCanceledException
import org.gnucash.android.db.DatabaseSchema.AccountEntry
import org.gnucash.android.db.DatabaseSchema.SplitEntry
import org.gnucash.android.db.DatabaseSchema.TransactionEntry
import org.gnucash.android.db.adapter.AccountsDbAdapter
import org.gnucash.android.db.getDouble
import org.gnucash.android.db.getInt
import org.gnucash.android.db.getLong
import org.gnucash.android.db.getString
import org.gnucash.android.export.ExportParams
import org.gnucash.android.export.Exporter
import org.gnucash.android.export.qif.QifHelper.ACCOUNT_DESCRIPTION_PREFIX
import org.gnucash.android.export.qif.QifHelper.ACCOUNT_NAME_PREFIX
import org.gnucash.android.export.qif.QifHelper.ACCOUNT_SECTION
import org.gnucash.android.export.qif.QifHelper.CATEGORY_PREFIX
import org.gnucash.android.export.qif.QifHelper.DATE_PREFIX
import org.gnucash.android.export.qif.QifHelper.ENTRY_TERMINATOR
import org.gnucash.android.export.qif.QifHelper.INTERNAL_CURRENCY_PREFIX
import org.gnucash.android.export.qif.QifHelper.MEMO_PREFIX
import org.gnucash.android.export.qif.QifHelper.NEW_LINE
import org.gnucash.android.export.qif.QifHelper.PAYEE_PREFIX
import org.gnucash.android.export.qif.QifHelper.SPLIT_AMOUNT_PREFIX
import org.gnucash.android.export.qif.QifHelper.SPLIT_CATEGORY_PREFIX
import org.gnucash.android.export.qif.QifHelper.SPLIT_MEMO_PREFIX
import org.gnucash.android.export.qif.QifHelper.TOTAL_AMOUNT_PREFIX
import org.gnucash.android.export.qif.QifHelper.TRANSACTION_TYPE_PREFIX
import org.gnucash.android.export.qif.QifHelper.TYPE_PREFIX
import org.gnucash.android.export.qif.QifHelper.formatDate
import org.gnucash.android.export.qif.QifHelper.getQifAccountType
import org.gnucash.android.gnc.GncProgressListener
import org.gnucash.android.math.isZero
import org.gnucash.android.model.Account
import org.gnucash.android.model.Money
import org.gnucash.android.model.TransactionType
import org.gnucash.android.util.FileUtils
import org.gnucash.android.util.PreferencesHelper
import org.gnucash.android.util.TimestampHelper
import org.gnucash.android.util.set
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import java.io.IOException
import java.io.Writer
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.NumberFormat
import java.util.Locale

/**
 * Exports the accounts and transactions in the database to the QIF format
 *
 * @author Ngewi Fet <ngewif@gmail.com>
 * @author Yongxin Wang <fefe.wyx@gmail.com>
 */
class QifExporter(
    context: Context,
    params: ExportParams,
    bookUID: String,
    listener: GncProgressListener? = null
) : Exporter(context, params, bookUID, listener) {
    @Throws(ExporterException::class, IOException::class)
    override fun writeToFile(exportParams: ExportParams): File? {
        val isCompressed = exportParams.isCompressed
        // Disable compression for files that will be zipped afterwards.
        exportParams.isCompressed = false
        val cacheFile = super.writeToFile(exportParams)!!
        exportParams.isCompressed = isCompressed

        val splitByCurrency = splitByCurrency(cacheFile)
        if (splitByCurrency.isEmpty()) {
            return null
        }
        if (isCompressed || (splitByCurrency.size > 1)) {
            val zipFile = File(cacheFile.path + ".zip")
            return zipFiles(splitByCurrency, zipFile)
        }
        return splitByCurrency[0]
    }

    @Throws(ExporterException::class, IOException::class)
    // TODO write each commodity to separate file here, instead of splitting the file afterwards.
    override fun writeExport(writer: Writer, exportParams: ExportParams) {
        val lastExportTimeStamp = exportParams.exportStartTime.time.toString()
        val transactionsDbAdapter = transactionsDbAdapter

        val accountsList = accountsDbAdapter.simpleAccounts
        val accounts = mutableMapOf<String, Account>()
        for (account in accountsList) {
            accounts[account.uid] = account
        }

        val quantityFormatter = NumberFormat.getNumberInstance(Locale.ROOT) as DecimalFormat
        quantityFormatter.isGroupingUsed = false

        val projection = arrayOf<String?>(
            TransactionEntry.TABLE_NAME + "_" + TransactionEntry.COLUMN_UID + " AS trans_uid",
            TransactionEntry.TABLE_NAME + "_" + TransactionEntry.COLUMN_TIMESTAMP + " AS trans_time",
            TransactionEntry.TABLE_NAME + "_" + TransactionEntry.COLUMN_DESCRIPTION + " AS trans_desc",
            TransactionEntry.TABLE_NAME + "_" + TransactionEntry.COLUMN_NOTES + " AS trans_notes",
            SplitEntry.TABLE_NAME + "_" + SplitEntry.COLUMN_ID + " AS split_id",
            SplitEntry.TABLE_NAME + "_" + SplitEntry.COLUMN_UID + " AS split_uid",
            SplitEntry.TABLE_NAME + "_" + SplitEntry.COLUMN_QUANTITY_NUM + " AS split_quantity_num",
            SplitEntry.TABLE_NAME + "_" + SplitEntry.COLUMN_QUANTITY_DENOM + " AS split_quantity_denom",
            SplitEntry.TABLE_NAME + "_" + SplitEntry.COLUMN_TYPE + " AS split_type",
            SplitEntry.TABLE_NAME + "_" + SplitEntry.COLUMN_MEMO + " AS split_memo",
            "trans_extra_info.trans_acct_balance AS trans_acct_balance",
            "trans_extra_info.trans_split_count AS trans_split_count",
            "account1." + AccountEntry.COLUMN_UID + " AS acct1_uid",
            AccountEntry.TABLE_NAME + "_" + AccountEntry.COLUMN_UID + " AS acct2_uid"
        )
        // no recurrence transactions
        val where =
            TransactionEntry.TABLE_NAME + "_" + TransactionEntry.COLUMN_TEMPLATE + " == 0 AND " +
                    // in qif, split from the one account entry is not recorded (will be auto balanced)
                    "(" + AccountEntry.TABLE_NAME + "_" + AccountEntry.COLUMN_UID + " != account1." + AccountEntry.COLUMN_UID + " OR " +
                    // or if the transaction has only one split (the whole transaction would be lost if it is not selected)
                    "trans_split_count == 1)" +
                    " AND " + TransactionEntry.TABLE_NAME + "_" + TransactionEntry.COLUMN_TIMESTAMP + " >= ?"
        // trans_time ASC : put transactions in time order
        // trans_uid ASC  : put splits from the same transaction together
        val orderBy = "acct1_uid ASC, trans_uid ASC, trans_time ASC, split_id ASC"

        var cursor: Cursor? = null
        try {
            cursor = transactionsDbAdapter.fetchTransactionsWithSplitsWithTransactionAccount(
                projection,
                where,
                arrayOf<String?>(lastExportTimeStamp),
                orderBy
            )
            if ((cursor == null) || !cursor.moveToFirst()) return

            var currentCommodityUID = ""
            var currentAccountUID = ""
            var currentTransactionUID = ""
            var txTotal = BigDecimal.ZERO

            do {
                cancellationSignal.throwIfCanceled()
                val accountUID = cursor.getString("acct1_uid") ?: continue
                val transactionUID = cursor.getString("trans_uid") ?: continue
                val description = cursor.getString("trans_desc") ?: continue
                val time = cursor.getLong("trans_time")
                val notes = cursor.getString("trans_notes")
                val imbalance = cursor.getDouble("trans_acct_balance")
                val splitCount = cursor.getInt("trans_split_count")

                val account1: Account = accounts[accountUID]!!
                val accountFullName = account1.fullName
                val accountDescription = account1.description
                val accountType = account1.accountType
                val commodity = account1.commodity
                val commodityUID = commodity.uid
                quantityFormatter.maximumFractionDigits = commodity.smallestFractionDigits
                quantityFormatter.minimumFractionDigits = commodity.smallestFractionDigits

                // Starting new transaction - finished with splits from previous transaction.
                if (transactionUID != currentTransactionUID) {
                    if (currentTransactionUID.isNotEmpty()) {
                        // end last transaction
                        writer.append(TOTAL_AMOUNT_PREFIX)
                            .append(quantityFormatter.format(txTotal))
                            .append(NEW_LINE)
                            .append(ENTRY_TERMINATOR)
                            .append(NEW_LINE)
                        txTotal = BigDecimal.ZERO
                    }
                    if (accountUID != currentAccountUID) {
                        if (commodityUID != currentCommodityUID) {
                            currentCommodityUID = commodityUID
                            writer.append(INTERNAL_CURRENCY_PREFIX)
                                .append(commodity.currencyCode)
                                .append(NEW_LINE)
                        }
                        // start new account
                        currentAccountUID = accountUID
                        writer.append(ACCOUNT_SECTION)
                            .append(NEW_LINE)
                            .append(ACCOUNT_NAME_PREFIX)
                            .append(accountFullName)
                            .append(NEW_LINE)
                            .append(TYPE_PREFIX)
                            .append(getQifAccountType(accountType))
                            .append(NEW_LINE)
                        if (!accountDescription.isNullOrEmpty()) {
                            writer.append(ACCOUNT_DESCRIPTION_PREFIX)
                                .append(accountDescription)
                                .append(NEW_LINE)
                        }
                        writer.append(ENTRY_TERMINATOR)
                            .append(NEW_LINE)
                    }
                    // start new transaction
                    currentTransactionUID = transactionUID
                    writer.append(TRANSACTION_TYPE_PREFIX)
                        .append(getQifAccountType(accountType))
                        .append(NEW_LINE)
                        .append(DATE_PREFIX)
                        .append(formatDate(time))
                        .append(NEW_LINE)
                        .append(CATEGORY_PREFIX)
                        .append('[')
                        .append(accountFullName)
                        .append(']')
                        .append(NEW_LINE)
                    // Payee / description
                    writer.append(PAYEE_PREFIX)
                        .append(description.trim())
                        .append(NEW_LINE)
                    // Notes, memo
                    if (!notes.isNullOrEmpty()) {
                        writer.append(MEMO_PREFIX)
                            .append(notes.replace('\n', ' ').trim())
                            .append(NEW_LINE)
                    }
                    // deal with imbalance first
                    val decimalImbalance =
                        BigDecimal.valueOf(imbalance).setScale(2, RoundingMode.HALF_UP)
                    if (!decimalImbalance.isZero) {
                        writer.append(SPLIT_CATEGORY_PREFIX)
                            .append('[')
                            .append(
                                AccountsDbAdapter.getImbalanceAccountName(context, commodity)
                            )
                            .append(']')
                            .append(NEW_LINE)
                            .append(SPLIT_AMOUNT_PREFIX)
                            .append(decimalImbalance.toPlainString())
                            .append(NEW_LINE)
                        txTotal += decimalImbalance
                    }
                }
                if (splitCount == 1) {
                    // No other splits should be recorded if this is the only split.
                    continue
                }
                // all splits
                val account2UID = cursor.getString("acct2_uid")
                val account2: Account = accounts[account2UID]!!
                val account2FullName = account2.fullName
                val splitMemo = cursor.getString("split_memo")
                val splitType = cursor.getString("split_type")
                val splitQuantityNum = cursor.getLong("split_quantity_num")
                val splitQuantityDenom = cursor.getLong("split_quantity_denom")
                // amount associated with the header account will not be exported.
                // It can be auto balanced when importing to GnuCash
                writer.append(SPLIT_CATEGORY_PREFIX)
                    .append('[')
                    .append(account2FullName)
                    .append(']')
                    .append(NEW_LINE)
                if (!splitMemo.isNullOrEmpty()) {
                    writer.append(SPLIT_MEMO_PREFIX)
                        .append(splitMemo.replace('\n', ' ').trim())
                        .append(NEW_LINE)
                }
                var quantity = Money(splitQuantityNum, splitQuantityDenom, account2.commodity)
                if (splitType == TransactionType.DEBIT.value) {
                    quantity = -quantity
                }
                writer.append(SPLIT_AMOUNT_PREFIX)
                    .append(quantityFormatter.format(quantity))
                    .append(NEW_LINE)
                txTotal += quantity.toBigDecimal()
            } while (cursor.moveToNext())
            if (!currentTransactionUID.isNullOrEmpty()) {
                // end last transaction
                writer.append(TOTAL_AMOUNT_PREFIX)
                    .append(quantityFormatter.format(txTotal))
                    .append(NEW_LINE)
                    .append(ENTRY_TERMINATOR)
                    .append(NEW_LINE)
            }
            writer.flush()
            writer.close()

            val contentValues = ContentValues()
            contentValues[TransactionEntry.COLUMN_EXPORTED] = 1
            transactionsDbAdapter.updateTransaction(contentValues, null, null)

            /** export successful */
            PreferencesHelper.setLastExportTime(TimestampHelper.timestampFromNow, bookUID)
        } catch (e: IOException) {
            throw ExporterException(exportParams, e)
        } catch (e: OperationCanceledException) {
            throw ExporterException(exportParams, e)
        } finally {
            cursor?.close()
        }
    }

    @Throws(IOException::class)
    private fun zipFiles(exportedFiles: List<File>, zipFile: File): File {
        FileUtils.zipFiles(exportedFiles, zipFile)
        return zipFile
    }

    /**
     * Splits a QIF file into several ones for each currency.
     *
     * @param file File of the QIF file to split.
     * @return a list of paths of the newly created Qif files.
     * @throws IOException if something went wrong while splitting the file.
     */
    @Throws(IOException::class)
    private fun splitByCurrency(file: File): List<File> {
        // split only at the last dot
        val path = file.path
        val pathParts: Array<String> =
            path.split("(?=\\.[^\\.]+$)".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        val splitFiles = mutableListOf<File>()
        var line: String?
        val reader = BufferedReader(FileReader(file))
        var out: BufferedWriter? = null
        try {
            line = reader.readLine()
            while (line != null) {
                if (line.startsWith(INTERNAL_CURRENCY_PREFIX)) {
                    val currencyCode = line.substring(1)
                    out?.close()
                    val newFileName = pathParts[0] + "_" + currencyCode + pathParts[1]
                    val splitFile = File(newFileName)
                    splitFiles.add(splitFile)
                    out = BufferedWriter(FileWriter(splitFile))
                } else {
                    requireNotNull(out) { "Format invalid: $path" }
                    out.append(line).append(NEW_LINE)
                }
                line = reader.readLine()
            }
        } finally {
            reader.close()
            out?.close()
        }
        return splitFiles
    }
}
