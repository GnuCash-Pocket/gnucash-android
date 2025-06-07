/*
 * Copyright (c) 2012 - 2015 Ngewi Fet <ngewif@gmail.com>
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

import static org.gnucash.android.db.DatabaseHelper.sqlEscapeLike;
import static org.gnucash.android.db.DatabaseSchema.AccountEntry;
import static org.gnucash.android.db.DatabaseSchema.ScheduledActionEntry;
import static org.gnucash.android.db.DatabaseSchema.SplitEntry;
import static org.gnucash.android.db.DatabaseSchema.TransactionEntry;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteQueryBuilder;
import android.database.sqlite.SQLiteStatement;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.gnucash.android.app.GnuCashApplication;
import org.gnucash.android.model.AccountType;
import org.gnucash.android.model.Money;
import org.gnucash.android.model.Split;
import org.gnucash.android.model.Transaction;
import org.gnucash.android.util.TimestampHelper;

import java.io.IOException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

import timber.log.Timber;

/**
 * Manages persistence of {@link Transaction}s in the database
 * Handles adding, modifying and deleting of transaction records.
 *
 * @author Ngewi Fet <ngewif@gmail.com>
 * @author Yongxin Wang <fefe.wyx@gmail.com>
 * @author Oleksandr Tyshkovets <olexandr.tyshkovets@gmail.com>
 */
public class TransactionsDbAdapter extends DatabaseAdapter<Transaction> {

    @NonNull
    public final SplitsDbAdapter splitsDbAdapter;

    @NonNull
    public final CommoditiesDbAdapter commoditiesDbAdapter;

    /**
     * Overloaded constructor. Creates adapter for already open db
     *
     * @param db SQlite db instance
     */
    public TransactionsDbAdapter(@NonNull SQLiteDatabase db) {
        this(new SplitsDbAdapter(db));
    }

    /**
     * Overloaded constructor. Creates adapter for already open db
     */
    public TransactionsDbAdapter(@NonNull SplitsDbAdapter splitsDbAdapter) {
        super(splitsDbAdapter.mDb, TransactionEntry.TABLE_NAME, new String[]{
            TransactionEntry.COLUMN_DESCRIPTION,
            TransactionEntry.COLUMN_NOTES,
            TransactionEntry.COLUMN_TIMESTAMP,
            TransactionEntry.COLUMN_EXPORTED,
            TransactionEntry.COLUMN_CURRENCY,
            TransactionEntry.COLUMN_COMMODITY_UID,
            TransactionEntry.COLUMN_CREATED_AT,
            TransactionEntry.COLUMN_SCHEDX_ACTION_UID,
            TransactionEntry.COLUMN_TEMPLATE
        });
        this.splitsDbAdapter = splitsDbAdapter;
        this.commoditiesDbAdapter = splitsDbAdapter.commoditiesDbAdapter;
    }

    public TransactionsDbAdapter(@NonNull CommoditiesDbAdapter commoditiesDbAdapter) {
        this(new SplitsDbAdapter(commoditiesDbAdapter));
    }

    /**
     * Returns an application-wide instance of the database adapter
     *
     * @return Transaction database adapter
     */
    public static TransactionsDbAdapter getInstance() {
        return GnuCashApplication.getTransactionDbAdapter();
    }

    /**
     * Adds an transaction to the database.
     * If a transaction already exists in the database with the same unique ID,
     * then the record will just be updated instead
     *
     * @param transaction {@link Transaction} to be inserted to database
     */
    @Override
    public void addRecord(@NonNull Transaction transaction, UpdateMethod updateMethod) throws SQLException {
        // Did the transaction have any splits before?
        final boolean didChange = transaction.id != 0;
        try {
            beginTransaction();
            Split imbalanceSplit = transaction.createAutoBalanceSplit();
            if (imbalanceSplit != null) {
                Context context = GnuCashApplication.getAppContext();
                String imbalanceAccountUID = new AccountsDbAdapter(this)
                    .getOrCreateImbalanceAccountUID(context, transaction.getCommodity());
                imbalanceSplit.setAccountUID(imbalanceAccountUID);
            }
            super.addRecord(transaction, updateMethod);

            List<Split> splits = transaction.getSplits();
            Timber.d("Adding %d splits for transaction", splits.size());
            List<String> splitUIDs = new ArrayList<>(splits.size());
            for (Split split : splits) {
                if (imbalanceSplit == split) {
                    splitsDbAdapter.addRecord(split, UpdateMethod.insert);
                } else {
                    splitsDbAdapter.addRecord(split, updateMethod);
                }
                splitUIDs.add(split.getUID());
            }

            if (didChange) {
                long deleted = mDb.delete(SplitEntry.TABLE_NAME,
                    SplitEntry.COLUMN_TRANSACTION_UID + " = ? AND "
                        + SplitEntry.COLUMN_UID + " NOT IN ('" + TextUtils.join("','", splitUIDs) + "')",
                    new String[]{transaction.getUID()});
                Timber.d("%d splits deleted", deleted);
            }

            setTransactionSuccessful();
        } finally {
            endTransaction();
        }
    }

    /**
     * Adds an several transactions to the database.
     * If a transaction already exists in the database with the same unique ID,
     * then the record will just be updated instead. Recurrence Transactions will not
     * be inserted, instead schedule Transaction would be called. If an exception
     * occurs, no transaction would be inserted.
     *
     * @param transactionList {@link Transaction} transactions to be inserted to database
     * @return Number of transactions inserted
     */
    @Override
    public long bulkAddRecords(@NonNull List<Transaction> transactionList, UpdateMethod updateMethod) throws SQLException {
        long start = System.nanoTime();
        long rowInserted = super.bulkAddRecords(transactionList, updateMethod);
        long end = System.nanoTime();
        Timber.d("bulk add transaction time %d", end - start);
        List<Split> splitList = new ArrayList<>(transactionList.size() * 3);
        for (Transaction transaction : transactionList) {
            splitList.addAll(transaction.getSplits());
        }
        if (rowInserted != 0 && !splitList.isEmpty()) {
            try {
                start = System.nanoTime();
                long nSplits = splitsDbAdapter.bulkAddRecords(splitList, updateMethod);
                Timber.d("%d splits inserted in %d ns", nSplits, System.nanoTime() - start);
            } finally {
                SQLiteStatement deleteEmptyTransaction = mDb.compileStatement("DELETE FROM " +
                    TransactionEntry.TABLE_NAME + " WHERE NOT EXISTS ( SELECT * FROM " +
                    SplitEntry.TABLE_NAME +
                    " WHERE " + TransactionEntry.TABLE_NAME + "." + TransactionEntry.COLUMN_UID +
                    " = " + SplitEntry.TABLE_NAME + "." + SplitEntry.COLUMN_TRANSACTION_UID + " ) ");
                deleteEmptyTransaction.execute();
            }
        }
        return rowInserted;
    }

    @Override
    protected @NonNull SQLiteStatement bind(@NonNull SQLiteStatement stmt, @NonNull Transaction transaction) {
        bindBaseModel(stmt, transaction);
        stmt.bindString(1, transaction.getDescription());
        stmt.bindString(2, transaction.getNote());
        stmt.bindLong(3, transaction.getTimeMillis());
        stmt.bindLong(4, transaction.isExported() ? 1 : 0);
        stmt.bindString(5, transaction.getCurrencyCode());
        stmt.bindString(6, transaction.getCommodity().getUID());
        stmt.bindString(7, TimestampHelper.getUtcStringFromTimestamp(transaction.getCreatedTimestamp()));
        if (transaction.getScheduledActionUID() != null) {
            stmt.bindString(8, transaction.getScheduledActionUID());
        }
        stmt.bindLong(9, transaction.isTemplate() ? 1 : 0);

        return stmt;
    }

    /**
     * Returns a cursor to a set of all transactions which have a split belonging to the account with unique ID
     * <code>accountUID</code>.
     *
     * @param accountUID UID of the account whose transactions are to be retrieved
     * @return Cursor holding set of transactions for particular account
     * @throws java.lang.IllegalArgumentException if the accountUID is null
     */
    public Cursor fetchAllTransactionsForAccount(String accountUID) {
        SQLiteQueryBuilder queryBuilder = new SQLiteQueryBuilder();
        queryBuilder.setTables(TransactionEntry.TABLE_NAME
            + " INNER JOIN " + SplitEntry.TABLE_NAME + " ON "
            + TransactionEntry.TABLE_NAME + "." + TransactionEntry.COLUMN_UID + " = "
            + SplitEntry.TABLE_NAME + "." + SplitEntry.COLUMN_TRANSACTION_UID);
        queryBuilder.setDistinct(true);
        String[] projectionIn = new String[]{TransactionEntry.TABLE_NAME + ".*"};
        String selection = SplitEntry.TABLE_NAME + "." + SplitEntry.COLUMN_ACCOUNT_UID + " = ?"
            + " AND " + TransactionEntry.TABLE_NAME + "." + TransactionEntry.COLUMN_TEMPLATE + " = 0";
        String[] selectionArgs = new String[]{accountUID};
        String sortOrder = TransactionEntry.TABLE_NAME + "." + TransactionEntry.COLUMN_TIMESTAMP + " DESC";

        return queryBuilder.query(mDb, projectionIn, selection, selectionArgs, null, null, sortOrder);
    }

    /**
     * Returns a cursor to all scheduled transactions which have at least one split in the account
     * <p>This is basically a set of all template transactions for this account</p>
     *
     * @param accountUID GUID of account
     * @return Cursor with set of transactions
     */
    public Cursor fetchScheduledTransactionsForAccount(String accountUID) {
        SQLiteQueryBuilder queryBuilder = new SQLiteQueryBuilder();
        queryBuilder.setTables(TransactionEntry.TABLE_NAME
            + " INNER JOIN " + SplitEntry.TABLE_NAME + " ON "
            + TransactionEntry.TABLE_NAME + "." + TransactionEntry.COLUMN_UID + " = "
            + SplitEntry.TABLE_NAME + "." + SplitEntry.COLUMN_TRANSACTION_UID);
        queryBuilder.setDistinct(true);
        String[] projectionIn = new String[]{TransactionEntry.TABLE_NAME + ".*"};
        String selection = SplitEntry.TABLE_NAME + "." + SplitEntry.COLUMN_ACCOUNT_UID + " = ?"
            + " AND " + TransactionEntry.TABLE_NAME + "." + TransactionEntry.COLUMN_TEMPLATE + " = 1";
        String[] selectionArgs = new String[]{accountUID};
        String sortOrder = TransactionEntry.TABLE_NAME + "." + TransactionEntry.COLUMN_TIMESTAMP + " DESC";

        return queryBuilder.query(mDb, projectionIn, selection, selectionArgs, null, null, sortOrder);
    }

    /**
     * Deletes all transactions which contain a split in the account.
     * <p><b>Note:</b>As long as the transaction has one split which belongs to the account {@code accountUID},
     * it will be deleted. The other splits belonging to the transaction will also go away</p>
     *
     * @param accountUID GUID of the account
     */
    public void deleteTransactionsForAccount(String accountUID) {
        String rawDeleteQuery = "DELETE FROM " + TransactionEntry.TABLE_NAME + " WHERE " + TransactionEntry.COLUMN_UID + " IN "
            + " (SELECT " + SplitEntry.COLUMN_TRANSACTION_UID + " FROM " + SplitEntry.TABLE_NAME + " WHERE "
            + SplitEntry.COLUMN_ACCOUNT_UID + " = ?)";
        mDb.execSQL(rawDeleteQuery, new String[]{accountUID});
    }

    /**
     * Deletes all transactions which have no splits associated with them
     *
     * @return Number of records deleted
     */
    public int deleteTransactionsWithNoSplits() {
        return mDb.delete(
            TransactionEntry.TABLE_NAME,
            "NOT EXISTS ( SELECT * FROM " + SplitEntry.TABLE_NAME +
                " WHERE " + TransactionEntry.TABLE_NAME + "." + TransactionEntry.COLUMN_UID +
                " = " + SplitEntry.TABLE_NAME + "." + SplitEntry.COLUMN_TRANSACTION_UID + " ) ",
            null
        );
    }

    /**
     * Fetches all recurring transactions from the database.
     * <p>Recurring transactions are the transaction templates which have an entry in the scheduled events table</p>
     *
     * @return Cursor holding set of all recurring transactions
     */
    public Cursor fetchAllScheduledTransactions() {
        SQLiteQueryBuilder queryBuilder = new SQLiteQueryBuilder();
        queryBuilder.setTables(TransactionEntry.TABLE_NAME + " INNER JOIN " + ScheduledActionEntry.TABLE_NAME + " ON "
            + TransactionEntry.TABLE_NAME + "." + TransactionEntry.COLUMN_UID + " = "
            + ScheduledActionEntry.TABLE_NAME + "." + ScheduledActionEntry.COLUMN_ACTION_UID);

        String[] projectionIn = new String[]{TransactionEntry.TABLE_NAME + ".*",
            ScheduledActionEntry.TABLE_NAME + "." + ScheduledActionEntry.COLUMN_UID + " AS " + "origin_scheduled_action_uid"};
        String sortOrder = TransactionEntry.TABLE_NAME + "." + TransactionEntry.COLUMN_DESCRIPTION + " ASC";

        return queryBuilder.query(mDb, projectionIn, null, null, null, null, sortOrder);
    }

    /**
     * Returns list of all transactions for account with UID <code>accountUID</code>
     *
     * @param accountUID UID of account whose transactions are to be retrieved
     * @return List of {@link Transaction}s for account with UID <code>accountUID</code>
     */
    public List<Transaction> getAllTransactionsForAccount(String accountUID) {
        Cursor cursor = fetchAllTransactionsForAccount(accountUID);
        return getRecords(cursor);
    }

    /**
     * Returns all transaction instances in the database.
     *
     * @return List of all transactions
     */
    @Deprecated
    public List<Transaction> getAllTransactions() {
        return getAllRecords();
    }

    public Cursor fetchTransactionsWithSplits(String[] columns, @Nullable String where, @Nullable String[] whereArgs, @Nullable String orderBy) {
        return mDb.query(TransactionEntry.TABLE_NAME + " , " + SplitEntry.TABLE_NAME +
                " ON " + TransactionEntry.TABLE_NAME + "." + TransactionEntry.COLUMN_UID +
                " = " + SplitEntry.TABLE_NAME + "." + SplitEntry.COLUMN_TRANSACTION_UID +
                " , trans_extra_info ON trans_extra_info.trans_acct_t_uid = " + TransactionEntry.TABLE_NAME + "." + TransactionEntry.COLUMN_UID,
            columns, where, whereArgs, null, null,
            orderBy);
    }

    /**
     * Fetch all transactions modified since a given timestamp
     *
     * @param timestamp Timestamp in milliseconds (since Epoch)
     * @return Cursor to the results
     */
    public Cursor fetchTransactionsModifiedSince(Timestamp timestamp) {
        SQLiteQueryBuilder queryBuilder = new SQLiteQueryBuilder();
        queryBuilder.setTables(TransactionEntry.TABLE_NAME);
        String where = TransactionEntry.COLUMN_TEMPLATE + "=0 AND " + TransactionEntry.COLUMN_TIMESTAMP + " >= ?";
        String[] whereArgs = new String[]{Long.toString(timestamp.getTime())};
        String orderBy = TransactionEntry.COLUMN_TIMESTAMP + " ASC, " + TransactionEntry.COLUMN_ID + " ASC";
        return queryBuilder.query(mDb, null, where, whereArgs, null, null, orderBy, null);
    }

    public Cursor fetchTransactionsWithSplitsWithTransactionAccount(String[] columns, String where, String[] whereArgs, String orderBy) {
        // table is :
        // trans_split_acct , trans_extra_info ON trans_extra_info.trans_acct_t_uid = transactions_uid ,
        // accounts AS account1 ON account1.uid = trans_extra_info.trans_acct_a_uid
        //
        // views effectively simplified this query
        //
        // account1 provides information for the grouped account. Splits from the grouped account
        // can be eliminated with a WHERE clause. Transactions in QIF can be auto balanced.
        //
        // Account, transaction and split Information can be retrieve in a single query.
        return mDb.query(
            "trans_split_acct , trans_extra_info ON trans_extra_info.trans_acct_t_uid = trans_split_acct." +
                TransactionEntry.TABLE_NAME + "_" + TransactionEntry.COLUMN_UID + " , " +
                AccountEntry.TABLE_NAME + " AS account1 ON account1." + AccountEntry.COLUMN_UID +
                " = trans_extra_info.trans_acct_a_uid",
            columns, where, whereArgs, null, null, orderBy);
    }

    @Override
    public long getRecordsCount() {
        return DatabaseUtils.queryNumEntries(mDb, TransactionEntry.TABLE_NAME, TransactionEntry.COLUMN_TEMPLATE + "=0");
    }

    @Override
    public long getRecordsCount(@Nullable String where, @Nullable String[] whereArgs) {
        String table = mTableName + ", trans_extra_info ON "
            + TransactionEntry.TABLE_NAME + "." + TransactionEntry.COLUMN_UID
            + " = trans_extra_info.trans_acct_t_uid";
        return DatabaseUtils.queryNumEntries(mDb, table, where, whereArgs);
    }

    /**
     * Builds a transaction instance with the provided cursor.
     * The cursor should already be pointing to the transaction record in the database
     *
     * @param c Cursor pointing to transaction record in database
     * @return {@link Transaction} object constructed from database record
     */
    @Override
    public Transaction buildModelInstance(@NonNull final Cursor c) {
        String name = c.getString(c.getColumnIndexOrThrow(TransactionEntry.COLUMN_DESCRIPTION));
        Transaction transaction = new Transaction(name);
        populateBaseModelAttributes(c, transaction);

        transaction.setTime(c.getLong(c.getColumnIndexOrThrow(TransactionEntry.COLUMN_TIMESTAMP)));
        transaction.setNote(c.getString(c.getColumnIndexOrThrow(TransactionEntry.COLUMN_NOTES)));
        transaction.setExported(c.getInt(c.getColumnIndexOrThrow(TransactionEntry.COLUMN_EXPORTED)) != 0);
        transaction.setTemplate(c.getInt(c.getColumnIndexOrThrow(TransactionEntry.COLUMN_TEMPLATE)) != 0);
        String commodityUID = c.getString(c.getColumnIndexOrThrow(TransactionEntry.COLUMN_COMMODITY_UID));
        transaction.setCommodity(commoditiesDbAdapter.getRecord(commodityUID));
        transaction.setScheduledActionUID(c.getString(c.getColumnIndexOrThrow(TransactionEntry.COLUMN_SCHEDX_ACTION_UID)));
        transaction.setSplits(splitsDbAdapter.getSplitsForTransaction(transaction.getUID()));

        return transaction;
    }

    /**
     * Returns the transaction balance for the transaction for the specified account.
     * <p>We consider only those splits which belong to this account</p>
     *
     * @param transactionUID GUID of the transaction
     * @param accountUID     GUID of the account
     * @return {@link org.gnucash.android.model.Money} balance of the transaction for that account
     */
    public Money getBalance(String transactionUID, String accountUID) {
        List<Split> splitList = splitsDbAdapter.getSplitsForTransactionInAccount(
            transactionUID, accountUID);

        return Transaction.computeBalance(accountUID, splitList);
    }

    /**
     * Assigns transaction with id <code>rowId</code> to account with id <code>accountId</code>
     *
     * @param transactionUID GUID of the transaction
     * @param srcAccountUID  GUID of the account from which the transaction is to be moved
     * @param dstAccountUID  GUID of the account to which the transaction will be assigned
     * @return Number of transactions splits affected
     */
    public int moveTransaction(String transactionUID, String srcAccountUID, String dstAccountUID) {
        Timber.i("Moving transaction ID " + transactionUID
            + " splits from " + srcAccountUID + " to account " + dstAccountUID);

        List<Split> splits = splitsDbAdapter.getSplitsForTransactionInAccount(transactionUID, srcAccountUID);
        for (Split split : splits) {
            split.setAccountUID(dstAccountUID);
        }
        splitsDbAdapter.bulkAddRecords(splits, UpdateMethod.update);
        return splits.size();
    }

    /**
     * Returns the number of transactions belonging to an account
     *
     * @param accountUID GUID of the account
     * @return Number of transactions with splits in the account
     */
    public int getTransactionsCount(String accountUID) {
        Cursor cursor = fetchAllTransactionsForAccount(accountUID);
        int count = 0;
        if (cursor == null)
            return count;
        else {
            count = cursor.getCount();
            cursor.close();
        }
        return count;
    }

    /**
     * Returns the number of template transactions in the database
     *
     * @return Number of template transactions
     */
    public long getTemplateTransactionsCount() {
        return DatabaseUtils.queryNumEntries(mDb, TransactionEntry.TABLE_NAME, TransactionEntry.COLUMN_TEMPLATE + "=1");
    }

    /**
     * Returns a list of all scheduled transactions in the database
     *
     * @return List of all scheduled transactions
     */
    public List<Transaction> getScheduledTransactionsForAccount(String accountUID) {
        Cursor cursor = fetchScheduledTransactionsForAccount(accountUID);
        return getRecords(cursor);
    }

    /**
     * Returns the number of splits for the transaction in the database
     *
     * @param transactionUID GUID of the transaction
     * @return Number of splits belonging to the transaction
     */
    public long getSplitCount(@NonNull String transactionUID) {
        if (TextUtils.isEmpty(transactionUID))
            return 0;
        return DatabaseUtils.queryNumEntries(
            mDb,
            SplitEntry.TABLE_NAME,
            SplitEntry.COLUMN_TRANSACTION_UID + "=?",
            new String[]{transactionUID}
        );
    }

    /**
     * Returns a cursor to transactions whose name (UI: description) start with the <code>prefix</code>
     * <p>This method is used for autocomplete suggestions when creating new transactions. <br/>
     * The suggestions are either transactions which have at least one split with {@code accountUID} or templates.</p>
     *
     * @param prefix     Starting characters of the transaction name
     * @param accountUID GUID of account within which to search for transactions
     * @return Cursor to the data set containing all matching transactions
     */
    public Cursor fetchTransactionSuggestions(String prefix, String accountUID) {
        SQLiteQueryBuilder queryBuilder = new SQLiteQueryBuilder();
        queryBuilder.setTables(TransactionEntry.TABLE_NAME
            + " INNER JOIN " + SplitEntry.TABLE_NAME + " ON "
            + TransactionEntry.TABLE_NAME + "." + TransactionEntry.COLUMN_UID + " = "
            + SplitEntry.TABLE_NAME + "." + SplitEntry.COLUMN_TRANSACTION_UID);
        queryBuilder.setDistinct(true);
        String[] projectionIn = new String[]{TransactionEntry.TABLE_NAME + ".*"};
        String selection = "(" + SplitEntry.TABLE_NAME + "." + SplitEntry.COLUMN_ACCOUNT_UID + " = ?"
            + " OR " + TransactionEntry.TABLE_NAME + "." + TransactionEntry.COLUMN_TEMPLATE + " = 1)"
            + " AND " + TransactionEntry.TABLE_NAME + "." + TransactionEntry.COLUMN_DESCRIPTION + " LIKE " + sqlEscapeLike(prefix);
        String[] selectionArgs = new String[]{accountUID};
        String sortOrder = TransactionEntry.TABLE_NAME + "." + TransactionEntry.COLUMN_TIMESTAMP + " DESC";
        String subquery = queryBuilder.buildQuery(projectionIn, selection, null, null, sortOrder, null);

        // Need to use inner subquery because ORDER BY must be before GROUP BY!
        SQLiteQueryBuilder queryBuilder2 = new SQLiteQueryBuilder();
        queryBuilder2.setTables("(" + subquery + ")");
        String groupBy = TransactionEntry.COLUMN_DESCRIPTION;
        String limit = Integer.toString(5);
        return queryBuilder2.query(mDb, null, null, selectionArgs, groupBy, null, null, limit);
    }

    /**
     * Updates a specific entry of an transaction
     *
     * @param contentValues Values with which to update the record
     * @param whereClause   Conditions for updating formatted as SQL where statement
     * @param whereArgs     Arguments for the SQL wehere statement
     * @return Number of records affected
     */
    public int updateTransaction(ContentValues contentValues, String whereClause, String[] whereArgs) {
        return mDb.update(TransactionEntry.TABLE_NAME, contentValues, whereClause, whereArgs);
    }

    /**
     * Deletes all transactions except those which are marked as templates.
     * <p>If you want to delete really all transaction records, use {@link #deleteAllRecords()}</p>
     *
     * @return Number of records deleted
     */
    public int deleteAllNonTemplateTransactions() {
        String where = TransactionEntry.COLUMN_TEMPLATE + "=0";
        return mDb.delete(mTableName, where, null);
    }

    /**
     * Returns a timestamp of the earliest transaction for a specified account type and currency
     *
     * @param type         the account type
     * @param commodityUID the currency UID
     * @return the earliest transaction's timestamp. Returns 1970-01-01 00:00:00.000 if no transaction found
     */
    public long getTimestampOfEarliestTransaction(AccountType type, String commodityUID) {
        return getTimestamp("MIN", type, commodityUID);
    }

    /**
     * Returns a timestamp of the latest transaction for a specified account type and currency
     *
     * @param type         the account type
     * @param commodityUID the currency UID
     * @return the latest transaction's timestamp. Returns 1970-01-01 00:00:00.000 if no transaction found
     */
    public long getTimestampOfLatestTransaction(AccountType type, String commodityUID) {
        return getTimestamp("MAX", type, commodityUID);
    }

    /**
     * Returns the most recent `modified_at` timestamp of non-template transactions in the database
     *
     * @return Last modified time in milliseconds or current time if there is none in the database
     */
    public Timestamp getTimestampOfLastModification() {
        Cursor cursor = mDb.query(TransactionEntry.TABLE_NAME,
            new String[]{"MAX(" + TransactionEntry.COLUMN_MODIFIED_AT + ")"},
            null, null, null, null, null);

        Timestamp timestamp = TimestampHelper.getTimestampFromNow();
        if (cursor.moveToFirst()) {
            String timeString = cursor.getString(0);
            if (timeString != null) { //in case there were no transactions in the XML file (account structure only)
                timestamp = TimestampHelper.getTimestampFromUtcString(timeString);
            }
        }
        cursor.close();
        return timestamp;
    }

    /**
     * Returns the earliest or latest timestamp of transactions for a specific account type and currency
     *
     * @param mod          Mode (either MAX or MIN)
     * @param type         AccountType
     * @param commodityUID the currency UID
     * @return earliest or latest timestamp of transactions
     * @see #getTimestampOfLatestTransaction(AccountType, String)
     * @see #getTimestampOfEarliestTransaction(AccountType, String)
     */
    private long getTimestamp(String mod, AccountType type, String commodityUID) {
        String sql = "SELECT " + mod + "(" + TransactionEntry.COLUMN_TIMESTAMP + ")"
            + " FROM " + TransactionEntry.TABLE_NAME
            + " INNER JOIN " + SplitEntry.TABLE_NAME + " ON "
            + SplitEntry.TABLE_NAME + "." + SplitEntry.COLUMN_TRANSACTION_UID + " = "
            + TransactionEntry.TABLE_NAME + "." + TransactionEntry.COLUMN_UID
            + " INNER JOIN " + AccountEntry.TABLE_NAME + " ON "
            + AccountEntry.TABLE_NAME + "." + AccountEntry.COLUMN_UID + " = "
            + SplitEntry.TABLE_NAME + "." + SplitEntry.COLUMN_ACCOUNT_UID
            + " WHERE " + AccountEntry.TABLE_NAME + "." + AccountEntry.COLUMN_TYPE + " = ? AND "
            + TransactionEntry.TABLE_NAME + "." + TransactionEntry.COLUMN_COMMODITY_UID + " = ? AND "
            + TransactionEntry.TABLE_NAME + "." + TransactionEntry.COLUMN_TEMPLATE + " = 0";
        Cursor cursor = mDb.rawQuery(sql, new String[]{type.name(), commodityUID});
        long timestamp = 0;
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                timestamp = cursor.getLong(0);
            }
            cursor.close();
        }
        return timestamp;
    }

    public long getTransactionsCountForAccount(String accountUID) {
        SQLiteQueryBuilder queryBuilder = new SQLiteQueryBuilder();
        queryBuilder.setTables(
            TransactionEntry.TABLE_NAME + " t "
                + " INNER JOIN " + SplitEntry.TABLE_NAME + " s ON "
                + "t." + TransactionEntry.COLUMN_UID + " = "
                + "s." + SplitEntry.COLUMN_TRANSACTION_UID
        );
        String[] projectionIn = new String[]{"COUNT(*)"};
        String selection = "s." + SplitEntry.COLUMN_ACCOUNT_UID + " = ?";
        String[] selectionArgs = new String[]{accountUID};

        Cursor cursor = queryBuilder.query(mDb, projectionIn, selection, selectionArgs, null, null, null);
        try {
            if (cursor != null && cursor.moveToFirst()) {
                return cursor.getLong(0);
            }
        } finally {
            cursor.close();
        }
        return 0L;
    }

    @Override
    public void close() throws IOException {
        commoditiesDbAdapter.close();
        splitsDbAdapter.close();
        super.close();
    }
}
