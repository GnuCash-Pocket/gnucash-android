/*
 * Copyright (c) 2014 Ngewi Fet <ngewif@gmail.com>
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

package org.gnucash.android.db.adapter;

import static org.gnucash.android.db.DatabaseSchema.AccountEntry;
import static org.gnucash.android.db.DatabaseSchema.CommonColumns;
import static org.gnucash.android.db.DatabaseSchema.SplitEntry;
import static org.gnucash.android.db.DatabaseSchema.TransactionEntry;
import static org.gnucash.android.math.MathExtKt.toBigDecimal;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteQueryBuilder;
import android.database.sqlite.SQLiteStatement;
import android.text.TextUtils;

import androidx.annotation.NonNull;

import org.gnucash.android.app.GnuCashApplication;
import org.gnucash.android.db.DatabaseHolder;
import org.gnucash.android.model.Account;
import org.gnucash.android.model.Commodity;
import org.gnucash.android.model.Money;
import org.gnucash.android.model.Split;
import org.gnucash.android.model.TransactionType;
import org.gnucash.android.util.TimestampHelper;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import timber.log.Timber;

/**
 * Database adapter for managing transaction splits in the database
 *
 * @author Ngewi Fet <ngewif@gmail.com>
 * @author Yongxin Wang <fefe.wyx@gmail.com>
 * @author Oleksandr Tyshkovets <olexandr.tyshkovets@gmail.com>
 */
public class SplitsDbAdapter extends DatabaseAdapter<Split> {

    @NonNull
    final CommoditiesDbAdapter commoditiesDbAdapter;
    private final Map<String, Commodity> accountCommodities = new HashMap<>();

    private static final String credit = TransactionType.CREDIT.value;

    public SplitsDbAdapter(@NonNull DatabaseHolder holder) {
        this(new CommoditiesDbAdapter(holder));
    }

    public SplitsDbAdapter(@NonNull CommoditiesDbAdapter commoditiesDbAdapter) {
        super(commoditiesDbAdapter.holder, SplitEntry.TABLE_NAME, new String[]{
            SplitEntry.COLUMN_MEMO,
            SplitEntry.COLUMN_TYPE,
            SplitEntry.COLUMN_VALUE_NUM,
            SplitEntry.COLUMN_VALUE_DENOM,
            SplitEntry.COLUMN_QUANTITY_NUM,
            SplitEntry.COLUMN_QUANTITY_DENOM,
            SplitEntry.COLUMN_CREATED_AT,
            SplitEntry.COLUMN_RECONCILE_STATE,
            SplitEntry.COLUMN_RECONCILE_DATE,
            SplitEntry.COLUMN_ACCOUNT_UID,
            SplitEntry.COLUMN_TRANSACTION_UID,
            SplitEntry.COLUMN_SCHEDX_ACTION_ACCOUNT_UID
        });
        this.commoditiesDbAdapter = commoditiesDbAdapter;
    }

    /**
     * Returns application-wide instance of the database adapter
     *
     * @return SplitsDbAdapter instance
     */
    public static SplitsDbAdapter getInstance() {
        return GnuCashApplication.getSplitsDbAdapter();
    }

    @Override
    public void close() throws IOException {
        commoditiesDbAdapter.close();
        super.close();
    }

    /**
     * Adds a split to the database.
     * The transactions belonging to the split are marked as exported
     *
     * @param split {@link org.gnucash.android.model.Split} to be recorded in DB
     */
    public void addRecord(@NonNull final Split split, UpdateMethod updateMethod) {
        super.addRecord(split, updateMethod);

        if (updateMethod != UpdateMethod.insert) {
            long transactionId = getTransactionID(split.getTransactionUID());
            //when a split is updated, we want mark the transaction as not exported
            updateRecord(TransactionEntry.TABLE_NAME, transactionId, TransactionEntry.COLUMN_EXPORTED, "0");

            //modifying a split means modifying the accompanying transaction as well
            updateRecord(TransactionEntry.TABLE_NAME, transactionId, TransactionEntry.COLUMN_MODIFIED_AT, TimestampHelper.getUtcStringFromTimestamp(TimestampHelper.getTimestampFromNow()));
        }
    }

    @Override
    protected @NonNull SQLiteStatement bind(@NonNull SQLiteStatement stmt, @NonNull final Split split) {
        bindBaseModel(stmt, split);
        if (split.getMemo() != null) {
            stmt.bindString(1, split.getMemo());
        }
        stmt.bindString(2, split.getType().name());
        stmt.bindLong(3, split.getValue().getNumerator());
        stmt.bindLong(4, split.getValue().getDenominator());
        stmt.bindLong(5, split.getQuantity().getNumerator());
        stmt.bindLong(6, split.getQuantity().getDenominator());
        stmt.bindString(7, TimestampHelper.getUtcStringFromTimestamp(split.getCreatedTimestamp()));
        stmt.bindString(8, String.valueOf(split.getReconcileState()));
        stmt.bindString(9, TimestampHelper.getUtcStringFromTimestamp(split.getReconcileDate()));
        stmt.bindString(10, split.getAccountUID());
        stmt.bindString(11, split.getTransactionUID());
        if (split.getScheduledActionAccountUID() != null) {
            stmt.bindString(12, split.getScheduledActionAccountUID());
        }

        return stmt;
    }

    /**
     * Builds a split instance from the data pointed to by the cursor provided
     * <p>This method will not move the cursor in any way. So the cursor should already by pointing to the correct entry</p>
     *
     * @param cursor Cursor pointing to transaction record in database
     * @return {@link org.gnucash.android.model.Split} instance
     */
    public Split buildModelInstance(@NonNull final Cursor cursor) {
        long valueNum = cursor.getLong(cursor.getColumnIndexOrThrow(SplitEntry.COLUMN_VALUE_NUM));
        long valueDenom = cursor.getLong(cursor.getColumnIndexOrThrow(SplitEntry.COLUMN_VALUE_DENOM));
        long quantityNum = cursor.getLong(cursor.getColumnIndexOrThrow(SplitEntry.COLUMN_QUANTITY_NUM));
        long quantityDenom = cursor.getLong(cursor.getColumnIndexOrThrow(SplitEntry.COLUMN_QUANTITY_DENOM));
        String typeName = cursor.getString(cursor.getColumnIndexOrThrow(SplitEntry.COLUMN_TYPE));
        String accountUID = cursor.getString(cursor.getColumnIndexOrThrow(SplitEntry.COLUMN_ACCOUNT_UID));
        String transxUID = cursor.getString(cursor.getColumnIndexOrThrow(SplitEntry.COLUMN_TRANSACTION_UID));
        String memo = cursor.getString(cursor.getColumnIndexOrThrow(SplitEntry.COLUMN_MEMO));
        String reconcileState = cursor.getString(cursor.getColumnIndexOrThrow(SplitEntry.COLUMN_RECONCILE_STATE));
        String reconcileDate = cursor.getString(cursor.getColumnIndexOrThrow(SplitEntry.COLUMN_RECONCILE_DATE));
        String schedxAccountUID = cursor.getString(cursor.getColumnIndexOrThrow(SplitEntry.COLUMN_SCHEDX_ACTION_ACCOUNT_UID));

        String transactionCurrencyUID = getAttribute(TransactionEntry.TABLE_NAME, transxUID, TransactionEntry.COLUMN_COMMODITY_UID);
        Commodity transactionCurrency = commoditiesDbAdapter.getRecord(transactionCurrencyUID);
        Money value = new Money(valueNum, valueDenom, transactionCurrency);
        Commodity commodity = TextUtils.isEmpty(schedxAccountUID) ? getAccountCommodity(accountUID) : getAccountCommodity(schedxAccountUID);
        Money quantity = new Money(quantityNum, quantityDenom, commodity);

        Split split = new Split(value, accountUID);
        populateBaseModelAttributes(cursor, split);
        split.setQuantity(quantity);
        split.setTransactionUID(transxUID);
        split.setType(TransactionType.valueOf(typeName));
        split.setMemo(memo);
        split.setReconcileState(reconcileState.charAt(0));
        if (!TextUtils.isEmpty(reconcileDate)) {
            split.setReconcileDate(TimestampHelper.getTimestampFromUtcString(reconcileDate));
        }
        split.setScheduledActionAccountUID(schedxAccountUID);

        return split;
    }

    public Money computeSplitBalance(Account account, long startTimestamp, long endTimestamp) {
        Commodity currency = account.getCommodity();
        BigDecimal total = BigDecimal.ZERO;

        String selection = "a." + CommonColumns.COLUMN_UID + " = ?"
            + " AND t." + TransactionEntry.COLUMN_TEMPLATE + " = 0"
            + " AND s." + SplitEntry.COLUMN_QUANTITY_DENOM + " >= 1";

        boolean validStart = startTimestamp != -1;
        boolean validEnd = endTimestamp != -1;
        if (validStart && validEnd) {
            selection += " AND t." + TransactionEntry.COLUMN_TIMESTAMP + " BETWEEN " + startTimestamp + " AND " + endTimestamp;
        } else if (validEnd) {
            selection += " AND t." + TransactionEntry.COLUMN_TIMESTAMP + " <= " + endTimestamp;
        } else if (validStart) {
            selection += " AND t." + TransactionEntry.COLUMN_TIMESTAMP + " >= " + startTimestamp;
        }
        String[] selectionArgs = new String[]{account.getUID()};

        String sql = "SELECT SUM(s." + SplitEntry.COLUMN_QUANTITY_NUM + ")"
            + ", s." + SplitEntry.COLUMN_QUANTITY_DENOM
            + ", s." + SplitEntry.COLUMN_TYPE
            + " FROM " + TransactionEntry.TABLE_NAME + " t, "
            + SplitEntry.TABLE_NAME + " s ON t." + TransactionEntry.COLUMN_UID + " = s." + SplitEntry.COLUMN_TRANSACTION_UID + ", "
            + AccountEntry.TABLE_NAME + " a ON s." + SplitEntry.COLUMN_ACCOUNT_UID + " = a." + AccountEntry.COLUMN_UID
            + " WHERE " + selection
            + " GROUP BY a.commodity_uid, s.type, s.quantity_denom";
        Cursor cursor = mDb.rawQuery(sql, selectionArgs);

        try {
            while (cursor.moveToNext()) {
                //FIXME beware of 64-bit overflow - get as BigInteger
                long amount_num = cursor.getLong(0);
                long amount_denom = cursor.getLong(1);
                String splitType = cursor.getString(2);

                if (credit.equals(splitType)) {
                    amount_num = -amount_num;
                }
                BigDecimal amount = toBigDecimal(amount_num, amount_denom);
                total = total.add(amount);
            }
            return new Money(total, currency);
        } finally {
            cursor.close();
        }
    }

    /**
     * Returns the list of splits for a transaction
     *
     * @param transactionUID String unique ID of transaction
     * @return List of {@link org.gnucash.android.model.Split}s
     */
    public List<Split> getSplitsForTransaction(String transactionUID) {
        Cursor cursor = fetchSplitsForTransaction(transactionUID);
        return getRecords(cursor);
    }

    /**
     * Returns the list of splits for a transaction
     *
     * @param transactionID DB record ID of the transaction
     * @return List of {@link org.gnucash.android.model.Split}s
     * @see #getSplitsForTransaction(String)
     * @see #getTransactionUID(long)
     */
    public List<Split> getSplitsForTransaction(long transactionID) {
        return getSplitsForTransaction(getTransactionUID(transactionID));
    }

    /**
     * Fetch splits for a given transaction within a specific account
     *
     * @param transactionUID String unique ID of transaction
     * @param accountUID     String unique ID of account
     * @return List of splits
     */
    public List<Split> getSplitsForTransactionInAccount(String transactionUID, String accountUID) {
        Cursor cursor = fetchSplitsForTransactionAndAccount(transactionUID, accountUID);
        return getRecords(cursor);
    }

    /**
     * Fetches a collection of splits for a given condition and sorted by <code>sortOrder</code>
     *
     * @param where     String condition, formatted as SQL WHERE clause
     * @param whereArgs where args
     * @param sortOrder Sort order for the returned records
     * @return Cursor to split records
     */
    public Cursor fetchSplits(String where, String[] whereArgs, String sortOrder) {
        return mDb.query(SplitEntry.TABLE_NAME, null, where, whereArgs, null, null, sortOrder);
    }

    /**
     * Returns a Cursor to a dataset of splits belonging to a specific transaction
     *
     * @param transactionUID Unique idendtifier of the transaction
     * @return Cursor to splits
     */
    public Cursor fetchSplitsForTransaction(String transactionUID) {
        Timber.v("Fetching all splits for transaction UID %s", transactionUID);
        String where = SplitEntry.COLUMN_TRANSACTION_UID + " = ?";
        String[] whereArgs = new String[]{transactionUID};
        String orderBy = SplitEntry.COLUMN_ID + " ASC";
        return mDb.query(mTableName, null, where, whereArgs, null, null, orderBy);
    }

    /**
     * Fetches splits for a given account
     *
     * @param accountUID String unique ID of account
     * @return Cursor containing splits dataset
     */
    public Cursor fetchSplitsForAccount(String accountUID) {
        Timber.d("Fetching all splits for account UID %s", accountUID);

        //This is more complicated than a simple "where account_uid=?" query because
        // we need to *not* return any splits which belong to recurring transactions
        SQLiteQueryBuilder queryBuilder = new SQLiteQueryBuilder();
        queryBuilder.setTables(TransactionEntry.TABLE_NAME
            + " INNER JOIN " + SplitEntry.TABLE_NAME + " ON "
            + TransactionEntry.TABLE_NAME + "." + TransactionEntry.COLUMN_UID + " = "
            + SplitEntry.TABLE_NAME + "." + SplitEntry.COLUMN_TRANSACTION_UID);
        queryBuilder.setDistinct(true);
        String[] projectionIn = new String[]{SplitEntry.TABLE_NAME + ".*"};
        String selection = SplitEntry.TABLE_NAME + "." + SplitEntry.COLUMN_ACCOUNT_UID + " = ?"
            + " AND " + TransactionEntry.TABLE_NAME + "." + TransactionEntry.COLUMN_TEMPLATE + " = 0";
        String[] selectionArgs = new String[]{accountUID};
        String sortOrder = TransactionEntry.TABLE_NAME + "." + TransactionEntry.COLUMN_TIMESTAMP + " DESC";

        return queryBuilder.query(mDb, projectionIn, selection, selectionArgs, null, null, sortOrder);

    }

    /**
     * Returns a cursor to splits for a given transaction and account
     *
     * @param transactionUID Unique idendtifier of the transaction
     * @param accountUID     String unique ID of account
     * @return Cursor to splits data set
     */
    public Cursor fetchSplitsForTransactionAndAccount(String transactionUID, String accountUID) {
        if (transactionUID == null || accountUID == null)
            return null;

        Timber.v("Fetching all splits for transaction ID " + transactionUID
            + "and account ID " + accountUID);
        return mDb.query(SplitEntry.TABLE_NAME,
            null, SplitEntry.COLUMN_TRANSACTION_UID + " = ? AND "
                + SplitEntry.COLUMN_ACCOUNT_UID + " = ?",
            new String[]{transactionUID, accountUID},
            null, null, SplitEntry.COLUMN_VALUE_NUM + " ASC");
    }

    /**
     * Returns the unique ID of a transaction given the database record ID of same
     *
     * @param transactionId Database record ID of the transaction
     * @return String unique ID of the transaction or null if transaction with the ID cannot be found.
     */
    public String getTransactionUID(long transactionId) {
        Cursor cursor = mDb.query(TransactionEntry.TABLE_NAME,
            new String[]{TransactionEntry.COLUMN_UID},
            TransactionEntry._ID + " = " + transactionId,
            null, null, null, null);

        try {
            if (cursor.moveToFirst()) {
                return cursor.getString(cursor.getColumnIndexOrThrow(TransactionEntry.COLUMN_UID));
            } else {
                throw new IllegalArgumentException("transaction " + transactionId + " does not exist");
            }
        } finally {
            cursor.close();
        }
    }

    @Override
    public boolean deleteRecord(long rowId) {
        Split split = getRecord(rowId);
        String transactionUID = split.getTransactionUID();
        boolean result = super.deleteRecord(rowId);

        if (!result) //we didn't delete for whatever reason, invalid rowId etc
            return false;

        //if we just deleted the last split, then remove the transaction from db
        Cursor cursor = fetchSplitsForTransaction(transactionUID);
        try {
            if (cursor.getCount() > 0) {
                long transactionID = getTransactionID(transactionUID);
                result = mDb.delete(TransactionEntry.TABLE_NAME,
                    TransactionEntry._ID + "=" + transactionID, null) > 0;
            }
        } finally {
            cursor.close();
        }
        return result;
    }

    /**
     * Returns the database record ID for the specified transaction UID
     *
     * @param transactionUID Unique idendtifier of the transaction
     * @return Database record ID for the transaction
     */
    public long getTransactionID(String transactionUID) {
        Cursor c = mDb.query(TransactionEntry.TABLE_NAME,
            new String[]{TransactionEntry._ID},
            TransactionEntry.COLUMN_UID + "=?",
            new String[]{transactionUID}, null, null, null);
        try {
            if (c.moveToFirst()) {
                return c.getLong(0);
            } else {
                throw new IllegalArgumentException("transaction " + transactionUID + " does not exist");
            }
        } finally {
            c.close();
        }
    }

    public void reassignAccount(@NonNull String oldAccountUID, @NonNull String newAccountUID) {
        updateRecords(
            SplitEntry.COLUMN_ACCOUNT_UID + " = ?",
            new String[]{oldAccountUID},
            SplitEntry.COLUMN_ACCOUNT_UID,
            newAccountUID
        );
    }

    /**
     * Returns the commodity of the account
     * with unique Identifier <code>accountUID</code>
     *
     * @param accountUID Unique Identifier of the account
     * @return Commodity of the account.
     */
    public Commodity getAccountCommodity(@NonNull String accountUID) {
        Commodity commodity = accountCommodities.get(accountUID);
        if (commodity != null) {
            return commodity;
        }
        Cursor cursor = mDb.query(
            AccountEntry.TABLE_NAME,
            new String[]{AccountEntry.COLUMN_COMMODITY_UID},
            AccountEntry.COLUMN_UID + "= ?",
            new String[]{accountUID}, null, null, null);
        try {
            if (cursor.moveToFirst()) {
                String commodityUID = cursor.getString(0);
                commodity = commoditiesDbAdapter.getRecord(commodityUID);
                accountCommodities.put(accountUID, commodity);
                return commodity;
            } else {
                throw new IllegalArgumentException("Account " + accountUID + " does not exist");
            }
        } finally {
            cursor.close();
        }
    }
}
