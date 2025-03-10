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
package org.gnucash.android.export.qif;

import static org.gnucash.android.db.DatabaseSchema.AccountEntry;
import static org.gnucash.android.db.DatabaseSchema.SplitEntry;
import static org.gnucash.android.db.DatabaseSchema.TransactionEntry;
import static org.gnucash.android.export.qif.QifHelper.ACCOUNT_DESCRIPTION_PREFIX;
import static org.gnucash.android.export.qif.QifHelper.ACCOUNT_SECTION;
import static org.gnucash.android.export.qif.QifHelper.ACCOUNT_NAME_PREFIX;
import static org.gnucash.android.export.qif.QifHelper.CATEGORY_PREFIX;
import static org.gnucash.android.export.qif.QifHelper.TRANSACTION_TYPE_PREFIX;
import static org.gnucash.android.export.qif.QifHelper.TOTAL_AMOUNT_PREFIX;
import static org.gnucash.android.export.qif.QifHelper.DATE_PREFIX;
import static org.gnucash.android.export.qif.QifHelper.ENTRY_TERMINATOR;
import static org.gnucash.android.export.qif.QifHelper.INTERNAL_CURRENCY_PREFIX;
import static org.gnucash.android.export.qif.QifHelper.MEMO_PREFIX;
import static org.gnucash.android.export.qif.QifHelper.NEW_LINE;
import static org.gnucash.android.export.qif.QifHelper.PAYEE_PREFIX;
import static org.gnucash.android.export.qif.QifHelper.SPLIT_AMOUNT_PREFIX;
import static org.gnucash.android.export.qif.QifHelper.SPLIT_CATEGORY_PREFIX;
import static org.gnucash.android.export.qif.QifHelper.SPLIT_MEMO_PREFIX;
import static org.gnucash.android.export.qif.QifHelper.TYPE_PREFIX;
import static org.gnucash.android.export.qif.QifHelper.formatDate;
import static org.gnucash.android.export.qif.QifHelper.getQifAccountType;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.text.TextUtils;

import androidx.annotation.NonNull;

import org.gnucash.android.db.adapter.AccountsDbAdapter;
import org.gnucash.android.db.adapter.TransactionsDbAdapter;
import org.gnucash.android.export.ExportParams;
import org.gnucash.android.export.Exporter;
import org.gnucash.android.model.Account;
import org.gnucash.android.model.AccountType;
import org.gnucash.android.model.Commodity;
import org.gnucash.android.model.TransactionType;
import org.gnucash.android.util.FileUtils;
import org.gnucash.android.util.PreferencesHelper;
import org.gnucash.android.util.TimestampHelper;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Exports the accounts and transactions in the database to the QIF format
 *
 * @author Ngewi Fet <ngewif@gmail.com>
 * @author Yongxin Wang <fefe.wyx@gmail.com>
 */
public class QifExporter extends Exporter {

    /**
     * Initialize the exporter
     *
     * @param context The context.
     * @param params  Parameters for the export
     * @param bookUID The book UID.
     */
    public QifExporter(@NonNull Context context,
                       @NonNull ExportParams params,
                       @NonNull String bookUID) {
        super(context, params, bookUID);
    }

    @Override
    public List<String> generateExport() throws ExporterException {
        String lastExportTimeStamp = Long.toString(mExportParams.getExportStartTime().getTime());
        TransactionsDbAdapter transactionsDbAdapter = mTransactionsDbAdapter;

        final List<Account> accountsList = mAccountsDbAdapter.getSimpleAccountList();
        final Map<String, Account> accounts = new HashMap<>();
        for (Account account : accountsList) {
            accounts.put(account.getUID(), account);
        }

        DecimalFormat quantityFormatter = (DecimalFormat) NumberFormat.getNumberInstance(Locale.ROOT);
        quantityFormatter.setGroupingUsed(false);

        final String[] projection = new String[]{
            TransactionEntry.TABLE_NAME + "_" + TransactionEntry.COLUMN_UID + " AS trans_uid",
            TransactionEntry.TABLE_NAME + "_" + TransactionEntry.COLUMN_TIMESTAMP + " AS trans_time",
            TransactionEntry.TABLE_NAME + "_" + TransactionEntry.COLUMN_DESCRIPTION + " AS trans_desc",
            TransactionEntry.TABLE_NAME + "_" + TransactionEntry.COLUMN_NOTES + " AS trans_notes",
            SplitEntry.TABLE_NAME + "_" + SplitEntry.COLUMN_QUANTITY_NUM + " AS split_quantity_num",
            SplitEntry.TABLE_NAME + "_" + SplitEntry.COLUMN_QUANTITY_DENOM + " AS split_quantity_denom",
            SplitEntry.TABLE_NAME + "_" + SplitEntry.COLUMN_TYPE + " AS split_type",
            SplitEntry.TABLE_NAME + "_" + SplitEntry.COLUMN_MEMO + " AS split_memo",
            "trans_extra_info.trans_acct_balance AS trans_acct_balance",
            "trans_extra_info.trans_split_count AS trans_split_count",
            "account1." + AccountEntry.COLUMN_UID + " AS acct1_uid",
            AccountEntry.TABLE_NAME + "_" + AccountEntry.COLUMN_UID + " AS acct2_uid"
        };
        // no recurrence transactions
        final String where =
            TransactionEntry.TABLE_NAME + "_" + TransactionEntry.COLUMN_TEMPLATE + " == 0 AND " +
                // in qif, split from the one account entry is not recorded (will be auto balanced)
                "( " + AccountEntry.TABLE_NAME + "_" + AccountEntry.COLUMN_UID + " != account1." + AccountEntry.COLUMN_UID + " OR " +
                // or if the transaction has only one split (the whole transaction would be lost if it is not selected)
                "trans_split_count == 1 )" +
                " AND " + TransactionEntry.TABLE_NAME + "_" + TransactionEntry.COLUMN_TIMESTAMP + " >= ?";
        // trans_time ASC : put transactions in time order
        // trans_uid ASC  : put splits from the same transaction together
        final String orderBy = "acct1_uid ASC, trans_uid ASC, trans_time ASC";

        Cursor cursor = null;
        try {
            cursor = transactionsDbAdapter.fetchTransactionsWithSplitsWithTransactionAccount(
                projection,
                where,
                new String[]{lastExportTimeStamp},
                orderBy
            );

            // TODO write each commodity to separate file here, instead of splitting the file afterwards.
            File file = new File(getExportCacheFilePath());
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8));

            String currentCommodityUID = "";
            String currentAccountUID = "";
            String currentTransactionUID = "";
            BigDecimal txTotal = BigDecimal.ZERO;

            while (cursor.moveToNext()) {
                String accountUID = cursor.getString(cursor.getColumnIndexOrThrow("acct1_uid"));
                Account account1 = accounts.get(accountUID);
                assert account1 != null;
                String transactionUID = cursor.getString(cursor.getColumnIndexOrThrow("trans_uid"));
                long time = cursor.getLong(cursor.getColumnIndexOrThrow("trans_time"));
                String description = cursor.getString(cursor.getColumnIndexOrThrow("trans_desc"));
                String notes = cursor.getString(cursor.getColumnIndexOrThrow("trans_notes"));
                double imbalance = cursor.getDouble(cursor.getColumnIndexOrThrow("trans_acct_balance"));
                int splitCount = cursor.getInt(cursor.getColumnIndexOrThrow("trans_split_count"));

                String accountFullName = account1.getFullName();
                String accountDescription = account1.getDescription();
                AccountType accountType = account1.getAccountType();
                Commodity commodity = account1.getCommodity();
                String commodityUID = commodity.getUID();
                quantityFormatter.setMaximumFractionDigits(commodity.getSmallestFractionDigits());
                quantityFormatter.setMinimumFractionDigits(commodity.getSmallestFractionDigits());

                // Starting new transaction - finished with splits from previous transaction.
                if (!transactionUID.equals(currentTransactionUID)) {
                    if (!TextUtils.isEmpty(currentTransactionUID)) {
                        // end last transaction
                        writer.append(TOTAL_AMOUNT_PREFIX)
                            .append(quantityFormatter.format(txTotal))
                            .append(NEW_LINE)
                            .append(ENTRY_TERMINATOR)
                            .append(NEW_LINE);
                        txTotal = BigDecimal.ZERO;
                    }
                    if (!accountUID.equals(currentAccountUID)) {
                        if (!commodityUID.equals(currentCommodityUID)) {
                            currentCommodityUID = commodityUID;
                            writer.append(INTERNAL_CURRENCY_PREFIX)
                                .append(commodity.getCurrencyCode())
                                .append(NEW_LINE);
                        }
                        // start new account
                        currentAccountUID = accountUID;
                        writer.append(ACCOUNT_SECTION)
                            .append(NEW_LINE)
                            .append(ACCOUNT_NAME_PREFIX)
                            .append(accountFullName)
                            .append(NEW_LINE)
                            .append(TYPE_PREFIX)
                            .append(getQifAccountType(accountType))
                            .append(NEW_LINE);
                        if (!TextUtils.isEmpty(accountDescription)) {
                            writer.append(ACCOUNT_DESCRIPTION_PREFIX)
                                .append(accountDescription)
                                .append(NEW_LINE);
                        }
                        writer.append(ENTRY_TERMINATOR)
                            .append(NEW_LINE);
                    }
                    // start new transaction
                    currentTransactionUID = transactionUID;
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
                        .append(NEW_LINE);
                    // Payee / description
                    writer.append(PAYEE_PREFIX)
                        .append(description.trim())
                        .append(NEW_LINE);
                    // Notes, memo
                    if (!TextUtils.isEmpty(notes)) {
                        writer.append(MEMO_PREFIX)
                            .append(notes.replace('\n', ' ').trim())
                            .append(NEW_LINE);
                    }
                    // deal with imbalance first
                    BigDecimal decimalImbalance = BigDecimal.valueOf(imbalance).setScale(2, BigDecimal.ROUND_HALF_UP);
                    if (decimalImbalance.compareTo(BigDecimal.ZERO) != 0) {
                        writer.append(SPLIT_CATEGORY_PREFIX)
                            .append('[')
                            .append(AccountsDbAdapter.getImbalanceAccountName(commodity))
                            .append(']')
                            .append(NEW_LINE)
                            .append(SPLIT_AMOUNT_PREFIX)
                            .append(decimalImbalance.toPlainString())
                            .append(NEW_LINE);
                        txTotal = txTotal.add(decimalImbalance);
                    }
                }
                if (splitCount == 1) {
                    // No other splits should be recorded if this is the only split.
                    continue;
                }
                // all splits
                String account2UID = cursor.getString(cursor.getColumnIndexOrThrow("acct2_uid"));
                Account account2 = accounts.get(account2UID);
                assert account2 != null;
                String account2FullName = account2.getFullName();
                String splitMemo = cursor.getString(cursor.getColumnIndexOrThrow("split_memo"));
                String splitType = cursor.getString(cursor.getColumnIndexOrThrow("split_type"));
                double quantity_num = cursor.getDouble(cursor.getColumnIndexOrThrow("split_quantity_num"));
                double quantity_denom = cursor.getDouble(cursor.getColumnIndexOrThrow("split_quantity_denom"));
                // amount associated with the header account will not be exported.
                // It can be auto balanced when importing to GnuCash
                writer.append(SPLIT_CATEGORY_PREFIX)
                    .append('[')
                    .append(account2FullName)
                    .append(']')
                    .append(NEW_LINE);
                if (!TextUtils.isEmpty(splitMemo)) {
                    writer.append(SPLIT_MEMO_PREFIX)
                        .append(splitMemo.replace('\n', ' ').trim())
                        .append(NEW_LINE);
                }
                BigDecimal quantity = (quantity_denom != 0) ? (BigDecimal.valueOf(quantity_num).divide(BigDecimal.valueOf(quantity_denom))) : BigDecimal.ZERO;
                if (splitType.equals(TransactionType.DEBIT.value)) {
                    quantity = quantity.negate();
                }
                writer.append(SPLIT_AMOUNT_PREFIX)
                    .append(quantityFormatter.format(quantity))
                    .append(NEW_LINE);
                txTotal = txTotal.add(quantity);
            }
            if (!TextUtils.isEmpty(currentTransactionUID)) {
                // end last transaction
                writer.append(TOTAL_AMOUNT_PREFIX)
                    .append(quantityFormatter.format(txTotal))
                    .append(NEW_LINE)
                    .append(ENTRY_TERMINATOR)
                    .append(NEW_LINE);
            }
            writer.flush();
            writer.close();

            ContentValues contentValues = new ContentValues();
            contentValues.put(TransactionEntry.COLUMN_EXPORTED, 1);
            transactionsDbAdapter.updateTransaction(contentValues, null, null);

            /// export successful
            PreferencesHelper.setLastExportTime(TimestampHelper.getTimestampFromNow());
            close();

            List<String> exportedFiles = splitQIF(file);
            if (exportedFiles.isEmpty())
                return Collections.emptyList();
            else if (exportedFiles.size() > 1)
                return zipQifs(exportedFiles);
            else
                return exportedFiles;
        } catch (IOException e) {
            throw new ExporterException(mExportParams, e);
        } finally {
            if (cursor != null) cursor.close();
        }
    }

    @NonNull
    private List<String> zipQifs(List<String> exportedFiles) throws IOException {
        String zipFileName = getExportCacheFilePath() + ".zip";
        FileUtils.zipFiles(exportedFiles, zipFileName);
        return Collections.singletonList(zipFileName);
    }

    /**
     * Splits a Qif file into several ones for each currency.
     *
     * @param file File object of the Qif file to split.
     * @return a list of paths of the newly created Qif files.
     * @throws IOException if something went wrong while splitting the file.
     */
    private List<String> splitQIF(File file) throws IOException {
        // split only at the last dot
        String[] pathParts = file.getPath().split("(?=\\.[^\\.]+$)");
        List<String> splitFiles = new ArrayList<>();
        String line;
        BufferedReader in = new BufferedReader(new FileReader(file));
        BufferedWriter out = null;
        try {
            while ((line = in.readLine()) != null) {
                if (line.startsWith(INTERNAL_CURRENCY_PREFIX)) {
                    String currencyCode = line.substring(1);
                    if (out != null) {
                        out.close();
                    }
                    String newFileName = pathParts[0] + "_" + currencyCode + pathParts[1];
                    splitFiles.add(newFileName);
                    out = new BufferedWriter(new FileWriter(newFileName));
                } else {
                    if (out == null) {
                        throw new IllegalArgumentException(file.getPath() + " format is not correct");
                    }
                    out.append(line).append(NEW_LINE);
                }
            }
        } finally {
            in.close();
            if (out != null) {
                out.close();
            }
        }
        return splitFiles;
    }

    /**
     * Returns the mime type for this Exporter.
     *
     * @return MIME type as string
     */
    @NonNull
    public String getExportMimeType() {
        return "text/plain";
    }
}
