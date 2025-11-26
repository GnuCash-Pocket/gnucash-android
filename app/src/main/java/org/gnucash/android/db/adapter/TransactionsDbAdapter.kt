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
package org.gnucash.android.db.adapter

import android.content.ContentValues
import android.database.Cursor
import android.database.DatabaseUtils
import android.database.SQLException
import android.database.sqlite.SQLiteQueryBuilder
import android.database.sqlite.SQLiteStatement
import org.gnucash.android.app.GnuCashApplication
import org.gnucash.android.app.GnuCashApplication.Companion.appContext
import org.gnucash.android.db.DatabaseHelper.Companion.sqlEscapeLike
import org.gnucash.android.db.DatabaseHolder
import org.gnucash.android.db.DatabaseSchema.AccountEntry
import org.gnucash.android.db.DatabaseSchema.ScheduledActionEntry
import org.gnucash.android.db.DatabaseSchema.SplitEntry
import org.gnucash.android.db.DatabaseSchema.TransactionEntry
import org.gnucash.android.db.bindBoolean
import org.gnucash.android.db.getBoolean
import org.gnucash.android.db.getLong
import org.gnucash.android.db.getString
import org.gnucash.android.db.joinIn
import org.gnucash.android.model.AccountType
import org.gnucash.android.model.Money
import org.gnucash.android.model.Transaction
import org.gnucash.android.model.Transaction.Companion.computeBalance
import org.gnucash.android.util.TimestampHelper.getTimestampFromUtcString
import org.gnucash.android.util.TimestampHelper.getUtcStringFromTimestamp
import org.gnucash.android.util.TimestampHelper.timestampFromNow
import org.gnucash.android.util.set
import timber.log.Timber
import java.io.IOException
import java.sql.Timestamp

/**
 * Manages persistence of [Transaction]s in the database
 * Handles adding, modifying and deleting of transaction records.
 *
 * @author Ngewi Fet <ngewif@gmail.com>
 * @author Yongxin Wang <fefe.wyx@gmail.com>
 * @author Oleksandr Tyshkovets <olexandr.tyshkovets@gmail.com>
 */
class TransactionsDbAdapter(
    val splitsDbAdapter: SplitsDbAdapter
) : DatabaseAdapter<Transaction>(
    splitsDbAdapter.holder,
    TransactionEntry.TABLE_NAME,
    arrayOf(
        TransactionEntry.COLUMN_DESCRIPTION,
        TransactionEntry.COLUMN_NOTES,
        TransactionEntry.COLUMN_TIMESTAMP,
        TransactionEntry.COLUMN_EXPORTED,
        TransactionEntry.COLUMN_CURRENCY,
        TransactionEntry.COLUMN_COMMODITY_UID,
        TransactionEntry.COLUMN_CREATED_AT,
        TransactionEntry.COLUMN_SCHEDX_ACTION_UID,
        TransactionEntry.COLUMN_TEMPLATE,
        TransactionEntry.COLUMN_NUMBER
    )
) {
    val commoditiesDbAdapter: CommoditiesDbAdapter = splitsDbAdapter.commoditiesDbAdapter

    /**
     * Overloaded constructor. Creates adapter for already open db
     *
     * @param holder Database holder
     */
    constructor(holder: DatabaseHolder, initCommodity: Boolean = false) :
            this(SplitsDbAdapter(holder, initCommodity))

    constructor(commoditiesDbAdapter: CommoditiesDbAdapter) :
            this(SplitsDbAdapter(commoditiesDbAdapter))

    /**
     * Adds an transaction to the database.
     * If a transaction already exists in the database with the same unique ID,
     * then the record will just be updated instead
     *
     * @param transaction [Transaction] to be inserted to database
     */
    @Throws(SQLException::class)
    override fun addRecord(transaction: Transaction, updateMethod: UpdateMethod) {
        // Did the transaction have any splits before?
        val didChange = transaction.id != 0L
        try {
            beginTransaction()
            val imbalanceSplit = transaction.createAutoBalanceSplit()
            if (imbalanceSplit != null) {
                val context = appContext
                val imbalanceAccountUID = AccountsDbAdapter(this)
                    .getOrCreateImbalanceAccountUID(context, transaction.commodity)
                imbalanceSplit.accountUID = imbalanceAccountUID
            }
            super.addRecord(transaction, updateMethod)

            val splits = transaction.splits
            Timber.d("Adding %d splits for transaction", splits.size)
            val splitUIDs = mutableListOf<String>()
            for (split in splits) {
                if (imbalanceSplit === split) {
                    splitsDbAdapter.addRecord(split, UpdateMethod.Insert)
                } else {
                    splitsDbAdapter.addRecord(split, updateMethod)
                }
                splitUIDs.add(split.uid)
            }

            if (didChange) {
                val deleteWhere = (SplitEntry.COLUMN_TRANSACTION_UID + " = ? AND "
                        + SplitEntry.COLUMN_UID + " NOT IN " + splitUIDs.joinIn())
                val deleteArgs = arrayOf<String?>(transaction.uid)
                val deleted = db.delete(SplitEntry.TABLE_NAME, deleteWhere, deleteArgs)
                Timber.d("%d splits deleted", deleted)
            }

            setTransactionSuccessful()
        } finally {
            endTransaction()
        }
    }

    /**
     * Adds an several transactions to the database.
     * If a transaction already exists in the database with the same unique ID,
     * then the record will just be updated instead. Recurrence Transactions will not
     * be inserted, instead schedule Transaction would be called. If an exception
     * occurs, no transaction would be inserted.
     *
     * @param transactions [Transaction] transactions to be inserted to database
     * @return Number of transactions inserted
     */
    @Throws(SQLException::class)
    override fun bulkAddRecords(transactions: List<Transaction>, updateMethod: UpdateMethod): Long {
        var start = System.nanoTime()
        val rowInserted = super.bulkAddRecords(transactions, updateMethod)
        val end = System.nanoTime()
        Timber.d("bulk add transaction time %d", end - start)
        val splits = transactions.flatMap { it.splits }
        if (rowInserted != 0L && !splits.isEmpty()) {
            try {
                start = System.nanoTime()
                val nSplits = splitsDbAdapter.bulkAddRecords(splits, updateMethod)
                Timber.d("%d splits inserted in %d ns", nSplits, System.nanoTime() - start)
            } finally {
                val deleteEmptyTransaction = db.compileStatement(
                    "DELETE FROM " +
                            TransactionEntry.TABLE_NAME + " WHERE NOT EXISTS ( SELECT * FROM " +
                            SplitEntry.TABLE_NAME +
                            " WHERE " + TransactionEntry.TABLE_NAME + "." + TransactionEntry.COLUMN_UID +
                            " = " + SplitEntry.TABLE_NAME + "." + SplitEntry.COLUMN_TRANSACTION_UID + " ) "
                )
                deleteEmptyTransaction.execute()
            }
        }
        return rowInserted
    }

    override fun bind(stmt: SQLiteStatement, transaction: Transaction): SQLiteStatement {
        bindBaseModel(stmt, transaction)
        stmt.bindString(1, transaction.description)
        stmt.bindString(2, transaction.note)
        stmt.bindLong(3, transaction.time)
        stmt.bindBoolean(4, transaction.isExported)
        stmt.bindString(5, transaction.currencyCode)
        stmt.bindString(6, transaction.commodity.uid)
        stmt.bindString(7, getUtcStringFromTimestamp(transaction.createdTimestamp))
        if (transaction.scheduledActionUID != null) {
            stmt.bindString(8, transaction.scheduledActionUID)
        }
        stmt.bindBoolean(9, transaction.isTemplate)
        if (transaction.number != null) {
            stmt.bindString(10, transaction.number)
        }

        return stmt
    }

    /**
     * Returns a cursor to a set of all transactions which have a split belonging to the account with unique ID
     * `accountUID`.
     *
     * @param accountUID UID of the account whose transactions are to be retrieved
     * @return Cursor holding set of transactions for particular account
     * @throws java.lang.IllegalArgumentException if the accountUID is null
     */
    fun fetchAllTransactionsForAccount(accountUID: String): Cursor? {
        val queryBuilder = SQLiteQueryBuilder()
        queryBuilder.setTables(
            (TransactionEntry.TABLE_NAME + " t"
                    + " INNER JOIN " + SplitEntry.TABLE_NAME + " s ON "
                    + "t." + TransactionEntry.COLUMN_UID + " = "
                    + "s." + SplitEntry.COLUMN_TRANSACTION_UID)
        )
        queryBuilder.isDistinct = true
        val projectionIn = arrayOf<String?>("t.*")
        val selection = ("s." + SplitEntry.COLUMN_ACCOUNT_UID + " = ?"
                + " AND t." + TransactionEntry.COLUMN_TEMPLATE + " = 0")
        val selectionArgs = arrayOf<String?>(accountUID)
        val sortOrder = "t." + TransactionEntry.COLUMN_TIMESTAMP + " DESC, " +
                "t." + TransactionEntry.COLUMN_NUMBER + " DESC, " +
                "t." + TransactionEntry.COLUMN_ID + " DESC"

        return queryBuilder.query(
            db,
            projectionIn,
            selection,
            selectionArgs,
            null,
            null,
            sortOrder
        )
    }

    /**
     * Returns a cursor to all scheduled transactions which have at least one split in the account
     *
     * This is basically a set of all template transactions for this account
     *
     * @param accountUID GUID of account
     * @return Cursor with set of transactions
     */
    fun fetchScheduledTransactionsForAccount(accountUID: String): Cursor? {
        val queryBuilder = SQLiteQueryBuilder()
        queryBuilder.setTables(
            (TransactionEntry.TABLE_NAME
                    + " INNER JOIN " + SplitEntry.TABLE_NAME + " ON "
                    + TransactionEntry.TABLE_NAME + "." + TransactionEntry.COLUMN_UID + " = "
                    + SplitEntry.TABLE_NAME + "." + SplitEntry.COLUMN_TRANSACTION_UID)
        )
        queryBuilder.isDistinct = true
        val projectionIn = arrayOf<String?>(TransactionEntry.TABLE_NAME + ".*")
        val selection = (SplitEntry.TABLE_NAME + "." + SplitEntry.COLUMN_ACCOUNT_UID + " = ?"
                + " AND " + TransactionEntry.TABLE_NAME + "." + TransactionEntry.COLUMN_TEMPLATE + " = 1")
        val selectionArgs = arrayOf<String?>(accountUID)
        val sortOrder =
            TransactionEntry.TABLE_NAME + "." + TransactionEntry.COLUMN_TIMESTAMP + " DESC"

        return queryBuilder.query(
            db,
            projectionIn,
            selection,
            selectionArgs,
            null,
            null,
            sortOrder
        )
    }

    /**
     * Deletes all transactions which contain a split in the account.
     *
     * **Note:**As long as the transaction has one split which belongs to the account `accountUID`,
     * it will be deleted. The other splits belonging to the transaction will also go away
     *
     * @param accountUID GUID of the account
     */
    fun deleteTransactionsForAccount(accountUID: String) {
        val rawDeleteQuery =
            ("DELETE FROM " + TransactionEntry.TABLE_NAME + " WHERE " + TransactionEntry.COLUMN_UID + " IN "
                    + " (SELECT " + SplitEntry.COLUMN_TRANSACTION_UID + " FROM " + SplitEntry.TABLE_NAME + " WHERE "
                    + SplitEntry.COLUMN_ACCOUNT_UID + " = ?)")
        db.execSQL(rawDeleteQuery, arrayOf<String?>(accountUID))
    }

    /**
     * Deletes all transactions which have no splits associated with them
     *
     * @return Number of records deleted
     */
    fun deleteTransactionsWithNoSplits(): Int {
        return db.delete(
            tableName,
            "NOT EXISTS ( SELECT * FROM " + SplitEntry.TABLE_NAME +
                    " WHERE " + TransactionEntry.TABLE_NAME + "." + TransactionEntry.COLUMN_UID +
                    " = " + SplitEntry.TABLE_NAME + "." + SplitEntry.COLUMN_TRANSACTION_UID + " ) ",
            null
        )
    }

    /**
     * Fetches all recurring transactions from the database.
     *
     * Recurring transactions are the transaction templates which have an entry in the scheduled events table
     *
     * @return Cursor holding set of all recurring transactions
     */
    fun fetchAllScheduledTransactions(): Cursor? {
        val queryBuilder = SQLiteQueryBuilder()
        queryBuilder.setTables(
            (TransactionEntry.TABLE_NAME + " INNER JOIN " + ScheduledActionEntry.TABLE_NAME + " ON "
                    + TransactionEntry.TABLE_NAME + "." + TransactionEntry.COLUMN_UID + " = "
                    + ScheduledActionEntry.TABLE_NAME + "." + ScheduledActionEntry.COLUMN_ACTION_UID)
        )

        val projectionIn = arrayOf<String?>(
            TransactionEntry.TABLE_NAME + ".*",
            ScheduledActionEntry.TABLE_NAME + "." + ScheduledActionEntry.COLUMN_UID + " AS " + "origin_scheduled_action_uid"
        )
        val sortOrder =
            TransactionEntry.TABLE_NAME + "." + TransactionEntry.COLUMN_DESCRIPTION + " ASC"

        return queryBuilder.query(db, projectionIn, null, null, null, null, sortOrder)
    }

    /**
     * Returns list of all transactions for account with UID `accountUID`
     *
     * @param accountUID UID of account whose transactions are to be retrieved
     * @return List of [Transaction]s for account with UID `accountUID`
     */
    fun getAllTransactionsForAccount(accountUID: String): List<Transaction> {
        val cursor = fetchAllTransactionsForAccount(accountUID)
        return getRecords(cursor)
    }

    /**
     * Returns all transaction instances in the database.
     *
     * @return List of all transactions
     */
    @get:Deprecated("")
    val allTransactions: List<Transaction>
        get() = allRecords

    fun fetchTransactionsWithSplits(
        columns: Array<String?>?,
        where: String?,
        whereArgs: Array<String?>?,
        orderBy: String?
    ): Cursor {
        val table = TransactionEntry.TABLE_NAME + " t, " + SplitEntry.TABLE_NAME + " s" +
                " ON t." + TransactionEntry.COLUMN_UID +
                " = s." + SplitEntry.COLUMN_TRANSACTION_UID +
                ", trans_extra_info ON trans_extra_info.trans_acct_t_uid = t." + TransactionEntry.COLUMN_UID
        return db.query(table, columns, where, whereArgs, null, null, orderBy)
    }

    /**
     * Fetch all transactions modified since a given timestamp
     *
     * @param timestamp Timestamp in milliseconds (since Epoch)
     * @return Cursor to the results
     */
    fun fetchTransactionsToExportSince(timestamp: Timestamp): Cursor {
        val where = TransactionEntry.COLUMN_TEMPLATE + " = 0 AND " +
                TransactionEntry.COLUMN_EXPORTED + " = 0 AND " +
                TransactionEntry.COLUMN_MODIFIED_AT + " >= ?"
        val whereArgs = arrayOf<String?>(getUtcStringFromTimestamp(timestamp))
        val orderBy = TransactionEntry.COLUMN_TIMESTAMP + " ASC, " +
                    TransactionEntry.COLUMN_NUMBER + " ASC, " +
                    TransactionEntry.COLUMN_ID + " ASC"
        return fetchAllRecords(where, whereArgs, orderBy)
    }

    fun markTransactionsExported(timestamp: Timestamp, exported: Boolean = true) {
        val values = ContentValues()
        values[TransactionEntry.COLUMN_EXPORTED] = exported
        val where = TransactionEntry.COLUMN_TEMPLATE + " = 0 AND " +
                TransactionEntry.COLUMN_MODIFIED_AT + " >= ?"
        val whereArgs = arrayOf<String?>(getUtcStringFromTimestamp(timestamp))
        db.update(tableName, values, where, whereArgs)
    }

    fun fetchTransactionsWithSplitsWithTransactionAccount(
        columns: Array<String?>?,
        where: String?,
        whereArgs: Array<String?>?,
        orderBy: String?
    ): Cursor {
        // table is :
        // trans_split_acct, trans_extra_info ON trans_extra_info.trans_acct_t_uid = transactions_uid ,
        // accounts AS account1 ON account1.uid = trans_extra_info.trans_acct_a_uid
        //
        // views effectively simplified this query
        //
        // account1 provides information for the grouped account. Splits from the grouped account
        // can be eliminated with a WHERE clause. Transactions in QIF can be auto balanced.
        //
        // Account, transaction and split Information can be retrieve in a single query.
        val table =
            "trans_split_acct, trans_extra_info ON trans_extra_info.trans_acct_t_uid = trans_split_acct." +
                    TransactionEntry.TABLE_NAME + "_" + TransactionEntry.COLUMN_UID + ", " +
                    AccountEntry.TABLE_NAME + " AS account1 ON account1." + AccountEntry.COLUMN_UID +
                    " = trans_extra_info.trans_acct_a_uid"
        return db.query(table, columns, where, whereArgs, null, null, orderBy)
    }

    override val recordsCount: Long
        get() = DatabaseUtils.queryNumEntries(
            db,
            tableName,
            TransactionEntry.COLUMN_TEMPLATE + "=0"
        )

    override fun getRecordsCount(where: String?, whereArgs: Array<String?>?): Long {
        val table = (tableName + ", trans_extra_info ON "
                + TransactionEntry.TABLE_NAME + "." + TransactionEntry.COLUMN_UID
                + " = trans_extra_info.trans_acct_t_uid")
        return DatabaseUtils.queryNumEntries(db, table, where, whereArgs)
    }

    /**
     * Builds a transaction instance with the provided cursor.
     * The cursor should already be pointing to the transaction record in the database
     *
     * @param cursor Cursor pointing to transaction record in database
     * @return [Transaction] object constructed from database record
     */
    override fun buildModelInstance(cursor: Cursor): Transaction {
        val name = cursor.getString(TransactionEntry.COLUMN_DESCRIPTION)!!
        val transaction = Transaction(name)
        populateBaseModelAttributes(cursor, transaction)

        transaction.time = cursor.getLong(TransactionEntry.COLUMN_TIMESTAMP)
        transaction.note = cursor.getString(TransactionEntry.COLUMN_NOTES)
        transaction.isExported = cursor.getBoolean(TransactionEntry.COLUMN_EXPORTED)
        transaction.isTemplate = cursor.getBoolean(TransactionEntry.COLUMN_TEMPLATE)
        val commodityUID = cursor.getString(TransactionEntry.COLUMN_COMMODITY_UID)!!
        transaction.commodity = commoditiesDbAdapter.getRecord(commodityUID)
        transaction.scheduledActionUID = cursor.getString(TransactionEntry.COLUMN_SCHEDX_ACTION_UID)
        transaction.number = cursor.getString(TransactionEntry.COLUMN_NUMBER)
        transaction.splits = splitsDbAdapter.getSplitsForTransaction(transaction.uid)

        return transaction
    }

    /**
     * Returns the transaction balance for the transaction for the specified account.
     *
     * We consider only those splits which belong to this account
     *
     * @param transactionUID GUID of the transaction
     * @param accountUID     GUID of the account
     * @return [Money] balance of the transaction for that account
     */
    fun getBalance(transactionUID: String, accountUID: String): Money {
        val splits = splitsDbAdapter.getSplitsForTransactionInAccount(transactionUID, accountUID)
        return computeBalance(accountUID, splits)
    }

    /**
     * Assigns transaction with id `rowId` to account with id `accountId`
     *
     * @param transactionUID GUID of the transaction
     * @param srcAccountUID  GUID of the account from which the transaction is to be moved
     * @param dstAccountUID  GUID of the account to which the transaction will be assigned
     * @return Number of transactions splits affected
     */
    fun moveTransaction(
        transactionUID: String,
        srcAccountUID: String,
        dstAccountUID: String?
    ): Int {
        Timber.i(
            ("Moving transaction ID " + transactionUID
                    + " splits from " + srcAccountUID + " to account " + dstAccountUID)
        )

        val splits =
            splitsDbAdapter.getSplitsForTransactionInAccount(transactionUID, srcAccountUID)
        for (split in splits) {
            split.accountUID = dstAccountUID
        }
        splitsDbAdapter.bulkAddRecords(splits, UpdateMethod.Update)
        return splits.size
    }

    /**
     * Returns the number of transactions belonging to an account
     *
     * @param accountUID GUID of the account
     * @return Number of transactions with splits in the account
     */
    fun getTransactionsCount(accountUID: String): Int {
        val cursor = fetchAllTransactionsForAccount(accountUID) ?: return 0
        var count = cursor.count
        cursor.close()
        return count
    }

    /**
     * Returns the number of template transactions in the database
     *
     * @return Number of template transactions
     */
    val templateTransactionsCount: Long
        get() = DatabaseUtils.queryNumEntries(
            db,
            tableName,
            TransactionEntry.COLUMN_TEMPLATE + "=1"
        )

    /**
     * Returns a list of all scheduled transactions in the database
     *
     * @return List of all scheduled transactions
     */
    fun getScheduledTransactionsForAccount(accountUID: String): List<Transaction> {
        val cursor = fetchScheduledTransactionsForAccount(accountUID)
        return getRecords(cursor)
    }

    /**
     * Returns the number of splits for the transaction in the database
     *
     * @param transactionUID GUID of the transaction
     * @return Number of splits belonging to the transaction
     */
    fun getSplitCount(transactionUID: String): Long {
        if (transactionUID.isEmpty()) return 0
        return DatabaseUtils.queryNumEntries(
            db,
            SplitEntry.TABLE_NAME,
            SplitEntry.COLUMN_TRANSACTION_UID + "=?",
            arrayOf<String?>(transactionUID)
        )
    }

    /**
     * Returns a cursor to transactions whose name (UI: description) start with the `prefix`
     *
     * This method is used for autocomplete suggestions when creating new transactions. <br></br>
     * The suggestions are either transactions which have at least one split with `accountUID` or templates.
     *
     * @param prefix     Starting characters of the transaction name
     * @param accountUID GUID of account within which to search for transactions
     * @return Cursor to the data set containing all matching transactions
     */
    fun fetchTransactionSuggestions(prefix: String, accountUID: String): Cursor? {
        val queryBuilder = SQLiteQueryBuilder()
        queryBuilder.setTables(
            (TransactionEntry.TABLE_NAME + " t"
                    + " INNER JOIN " + SplitEntry.TABLE_NAME + " s ON "
                    + "t." + TransactionEntry.COLUMN_UID + " = s." + SplitEntry.COLUMN_TRANSACTION_UID)
        )
        val projectionIn =
            arrayOf<String?>("t.*", "MAX(t." + TransactionEntry.COLUMN_TIMESTAMP + ")")
        val selection = ("s." + SplitEntry.COLUMN_ACCOUNT_UID + " = ?"
                + " AND t." + TransactionEntry.COLUMN_TEMPLATE + " = 0"
                + " AND t." + TransactionEntry.COLUMN_DESCRIPTION + " LIKE " + sqlEscapeLike(prefix))
        val selectionArgs = arrayOf<String?>(accountUID)
        val groupBy = TransactionEntry.COLUMN_DESCRIPTION
        val sortOrder = "t." + TransactionEntry.COLUMN_TIMESTAMP + " DESC"
        val limit = 10.toString()
        return queryBuilder.query(
            db,
            projectionIn,
            selection,
            selectionArgs,
            groupBy,
            null,
            sortOrder,
            limit
        )
    }

    /**
     * Updates a specific entry of an transaction
     *
     * @param contentValues Values with which to update the record
     * @param whereClause   Conditions for updating formatted as SQL where statement
     * @param whereArgs     Arguments for the SQL wehere statement
     * @return Number of records affected
     */
    fun updateTransaction(
        contentValues: ContentValues,
        whereClause: String?,
        whereArgs: Array<String?>?
    ): Int {
        return db.update(tableName, contentValues, whereClause, whereArgs)
    }

    /**
     * Deletes all transactions except those which are marked as templates.
     *
     * If you want to delete really all transaction records, use [.deleteAllRecords]
     *
     * @return Number of records deleted
     */
    fun deleteAllNonTemplateTransactions(): Int {
        val where = TransactionEntry.COLUMN_TEMPLATE + "=0"
        return db.delete(tableName, where, null)
    }

    /**
     * Returns a timestamp of the earliest transaction for a specified account type and currency
     *
     * @param type         the account type
     * @param commodityUID the currency UID
     * @return the earliest transaction's timestamp. Returns `#INVALID_DATE` if no transaction found
     */
    fun getTimestampOfEarliestTransaction(type: AccountType, commodityUID: String): Long {
        return getTimestamp("MIN", type, commodityUID)
    }

    /**
     * Returns a timestamp of the latest transaction for a specified account type and currency
     *
     * @param type         the account type
     * @param commodityUID the currency UID
     * @return the latest transaction's timestamp. Returns `#INVALID_DATE` if no transaction found
     */
    fun getTimestampOfLatestTransaction(type: AccountType, commodityUID: String): Long {
        return getTimestamp("MAX", type, commodityUID)
    }

    val timestampOfFirstModification: Timestamp get() = getTimestampModification("MIN")

    /**
     * Returns the most recent `modified_at` timestamp of non-template transactions in the database
     *
     * @return Last modified time in milliseconds or current time if there is none in the database
     */
    val timestampOfLastModification: Timestamp get() = getTimestampModification("MAX")

    private fun getTimestampModification(mod: String): Timestamp {
        val cursor = db.query(
            tableName,
            arrayOf<String?>(mod + "(" + TransactionEntry.COLUMN_MODIFIED_AT + ")"),
            null, null, null, null, null
        )

        var timestamp = timestampFromNow
        try {
            if (cursor.moveToFirst()) {
                val timeString = cursor.getString(0)
                if (!timeString.isNullOrEmpty()) { //in case there were no transactions in the XML file (account structure only)
                    timestamp = getTimestampFromUtcString(timeString)
                }
            }
        } finally {
            cursor.close()
        }
        return timestamp
    }

    /**
     * Returns the earliest or latest timestamp of transactions for a specific account type and currency
     *
     * @param mod          Mode (either MAX or MIN)
     * @param type         AccountType
     * @param commodityUID the currency UID
     * @return earliest or latest timestamp of transactions - `#INVALID_DATE` otherwise.
     * @see .getTimestampOfLatestTransaction
     * @see .getTimestampOfEarliestTransaction
     */
    private fun getTimestamp(mod: String, type: AccountType, commodityUID: String): Long {
        val sql = ("SELECT " + mod + "(t." + TransactionEntry.COLUMN_TIMESTAMP + ")"
                + " FROM " + TransactionEntry.TABLE_NAME + " t"
                + " INNER JOIN " + SplitEntry.TABLE_NAME + " s ON"
                + " s." + SplitEntry.COLUMN_TRANSACTION_UID + " = t." + TransactionEntry.COLUMN_UID
                + " INNER JOIN " + AccountEntry.TABLE_NAME + " a ON"
                + " a." + AccountEntry.COLUMN_UID + " = s." + SplitEntry.COLUMN_ACCOUNT_UID
                + " WHERE a." + AccountEntry.COLUMN_TYPE + " = ?"
                + " AND a." + AccountEntry.COLUMN_COMMODITY_UID + " = ?"
                + " AND t." + TransactionEntry.COLUMN_TEMPLATE + " = 0")
        val args = arrayOf<String?>(type.name, commodityUID)
        val cursor = db.rawQuery(sql, args)
        var timestamp: Long = INVALID_DATE
        try {
            if (cursor.moveToFirst()) {
                timestamp = cursor.getLong(0)
            }
        } finally {
            cursor.close()
        }
        return timestamp
    }

    fun getTransactionsCountForAccount(accountUID: String): Long {
        val queryBuilder = SQLiteQueryBuilder()
        queryBuilder.setTables(
            (TransactionEntry.TABLE_NAME + " t "
                    + " INNER JOIN " + SplitEntry.TABLE_NAME + " s ON"
                    + " t." + TransactionEntry.COLUMN_UID + " ="
                    + " s." + SplitEntry.COLUMN_TRANSACTION_UID)
        )
        val projectionIn = arrayOf<String?>("COUNT(*)")
        val selection = "s." + SplitEntry.COLUMN_ACCOUNT_UID + " = ?"
        val selectionArgs = arrayOf<String?>(accountUID)

        val cursor =
            queryBuilder.query(db, projectionIn, selection, selectionArgs, null, null, null)
        try {
            if (cursor != null && cursor.moveToFirst()) {
                return cursor.getLong(0)
            }
        } finally {
            cursor!!.close()
        }
        return 0L
    }

    @Throws(IOException::class)
    override fun close() {
        commoditiesDbAdapter.close()
        splitsDbAdapter.close()
        super.close()
    }

    fun fetchSearch(where: String): Cursor? {
        if (where.isEmpty()) {
            val orderBy = TransactionEntry.COLUMN_TIMESTAMP + " DESC, " +
                    TransactionEntry.COLUMN_NUMBER + " DESC, " +
                    TransactionEntry.COLUMN_ID + " DESC"
            return fetchAllRecords(null, null, orderBy)
        }
        val table = (TransactionEntry.TABLE_NAME + " t"
                + " INNER JOIN " + SplitEntry.TABLE_NAME + " s1" + " ON t." + TransactionEntry.COLUMN_UID
                + " = s1." + SplitEntry.COLUMN_TRANSACTION_UID
                + " INNER JOIN " + SplitEntry.TABLE_NAME + " s2" + " ON t." + TransactionEntry.COLUMN_UID
                + " = s2." + SplitEntry.COLUMN_TRANSACTION_UID
                )
        val columns = arrayOf<String?>("t.*")
        val selection = "(s1.${SplitEntry.COLUMN_ID} < s2.${SplitEntry.COLUMN_ID}) AND $where"
        val orderBy = "t." + TransactionEntry.COLUMN_TIMESTAMP + " DESC, " +
                "t." + TransactionEntry.COLUMN_NUMBER + " DESC, " +
                "t." + TransactionEntry.COLUMN_ID + " DESC"
        return db.query(true, table, columns, selection, null, null, null, orderBy, null)
    }

    companion object {
        const val INVALID_DATE: Long = Long.MIN_VALUE

        /**
         * Returns an application-wide instance of the database adapter
         *
         * @return Transaction database adapter
         */
        val instance: TransactionsDbAdapter get() = GnuCashApplication.transactionDbAdapter!!
    }
}
