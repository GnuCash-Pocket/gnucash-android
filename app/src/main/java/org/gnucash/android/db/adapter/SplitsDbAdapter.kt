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
package org.gnucash.android.db.adapter

import android.database.Cursor
import android.database.sqlite.SQLiteQueryBuilder
import android.database.sqlite.SQLiteStatement
import org.gnucash.android.app.GnuCashApplication
import org.gnucash.android.db.DatabaseHolder
import org.gnucash.android.db.DatabaseSchema.AccountEntry
import org.gnucash.android.db.DatabaseSchema.SplitEntry
import org.gnucash.android.db.DatabaseSchema.TransactionEntry
import org.gnucash.android.db.getLong
import org.gnucash.android.db.getString
import org.gnucash.android.db.joinIn
import org.gnucash.android.math.toBigDecimal
import org.gnucash.android.model.Account
import org.gnucash.android.model.Commodity
import org.gnucash.android.model.Money
import org.gnucash.android.model.Money.Companion.createZeroInstance
import org.gnucash.android.model.Split
import org.gnucash.android.model.Split.Companion.FLAG_NOT_RECONCILED
import org.gnucash.android.model.TransactionType
import org.gnucash.android.util.TimestampHelper.getTimestampFromUtcString
import org.gnucash.android.util.TimestampHelper.getUtcStringFromTimestamp
import org.gnucash.android.util.TimestampHelper.timestampFromNow
import timber.log.Timber
import java.io.IOException

/**
 * Database adapter for managing transaction splits in the database
 *
 * @author Ngewi Fet <ngewif@gmail.com>
 * @author Yongxin Wang <fefe.wyx@gmail.com>
 * @author Oleksandr Tyshkovets <olexandr.tyshkovets@gmail.com>
 */
class SplitsDbAdapter(
    val commoditiesDbAdapter: CommoditiesDbAdapter
) : DatabaseAdapter<Split>(
    commoditiesDbAdapter.holder,
    SplitEntry.TABLE_NAME,
    arrayOf(
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
    )
) {
    private val accountCommodities = mutableMapOf<String, Commodity>()

    constructor(holder: DatabaseHolder) : this(CommoditiesDbAdapter(holder))

    @Throws(IOException::class)
    override fun close() {
        commoditiesDbAdapter.close()
        super.close()
    }

    /**
     * Adds a split to the database.
     * The transactions belonging to the split are marked as exported
     *
     * @param split [Split] to be recorded in DB
     */
    override fun addRecord(split: Split, updateMethod: UpdateMethod) {
        super.addRecord(split, updateMethod)

        if (updateMethod != UpdateMethod.Insert) {
            val transactionUID = split.transactionUID ?: return
            val transactionId = getTransactionID(transactionUID)
            //when a split is updated, we want mark the transaction as not exported
            updateRecord(
                TransactionEntry.TABLE_NAME,
                transactionId,
                TransactionEntry.COLUMN_EXPORTED,
                "0"
            )

            //modifying a split means modifying the accompanying transaction as well
            updateRecord(
                TransactionEntry.TABLE_NAME,
                transactionId,
                TransactionEntry.COLUMN_MODIFIED_AT,
                getUtcStringFromTimestamp(
                    timestampFromNow
                )
            )
        }
    }

    override fun bind(stmt: SQLiteStatement, split: Split): SQLiteStatement {
        bindBaseModel(stmt, split)
        if (split.memo != null) {
            stmt.bindString(1, split.memo)
        }
        stmt.bindString(2, split.type.value)
        stmt.bindLong(3, split.value.numerator)
        stmt.bindLong(4, split.value.denominator)
        stmt.bindLong(5, split.quantity.numerator)
        stmt.bindLong(6, split.quantity.denominator)
        stmt.bindString(7, getUtcStringFromTimestamp(split.createdTimestamp))
        stmt.bindString(8, split.reconcileState.toString())
        stmt.bindString(9, getUtcStringFromTimestamp(split.reconcileDate))
        stmt.bindString(10, split.accountUID)
        stmt.bindString(11, split.transactionUID)
        if (split.scheduledActionAccountUID != null) {
            stmt.bindString(12, split.scheduledActionAccountUID)
        }

        return stmt
    }

    /**
     * Builds a split instance from the data pointed to by the cursor provided
     *
     * This method will not move the cursor in any way. So the cursor should already by pointing to the correct entry
     *
     * @param cursor Cursor pointing to transaction record in database
     * @return [Split] instance
     */
    override fun buildModelInstance(cursor: Cursor): Split {
        val valueNum = cursor.getLong(SplitEntry.COLUMN_VALUE_NUM)
        val valueDenom = cursor.getLong(SplitEntry.COLUMN_VALUE_DENOM)
        val quantityNum = cursor.getLong(SplitEntry.COLUMN_QUANTITY_NUM)
        val quantityDenom = cursor.getLong(SplitEntry.COLUMN_QUANTITY_DENOM)
        val typeName = cursor.getString(SplitEntry.COLUMN_TYPE)!!
        val accountUID = cursor.getString(SplitEntry.COLUMN_ACCOUNT_UID)!!
        val transxUID = cursor.getString(SplitEntry.COLUMN_TRANSACTION_UID)!!
        val memo = cursor.getString(SplitEntry.COLUMN_MEMO)
        val reconcileState = cursor.getString(SplitEntry.COLUMN_RECONCILE_STATE)
        val reconcileDate = cursor.getString(SplitEntry.COLUMN_RECONCILE_DATE)
        val schedxAccountUID = cursor.getString(SplitEntry.COLUMN_SCHEDX_ACTION_ACCOUNT_UID)

        val transactionCurrencyUID = getAttribute(
            TransactionEntry.TABLE_NAME,
            transxUID,
            TransactionEntry.COLUMN_COMMODITY_UID
        )
        val transactionCurrency = commoditiesDbAdapter.getRecord(transactionCurrencyUID)
        val value = Money(valueNum, valueDenom, transactionCurrency)
        val commodity = if (schedxAccountUID.isNullOrEmpty()) {
            getAccountCommodity(accountUID)
        } else {
            getAccountCommodity(schedxAccountUID)
        }
        val quantity = Money(quantityNum, quantityDenom, commodity)

        val split = Split(value, accountUID)
        populateBaseModelAttributes(cursor, split)
        split.quantity = quantity
        split.transactionUID = transxUID
        split.type = TransactionType.of(typeName)
        split.memo = memo
        split.reconcileState = reconcileState?.get(0) ?: FLAG_NOT_RECONCILED
        if (!reconcileDate.isNullOrEmpty()) {
            split.reconcileDate = getTimestampFromUtcString(reconcileDate).getTime()
        }
        split.scheduledActionAccountUID = schedxAccountUID

        return split
    }

    fun computeSplitBalance(account: Account, startTimestamp: Long, endTimestamp: Long): Money {
        val accounts = mutableListOf(account)
        val balances = computeSplitBalances(accounts, startTimestamp, endTimestamp)
        val balance = balances[account.uid]
        return balance ?: createZeroInstance(account.commodity)
    }

    fun computeSplitBalances(
        accounts: List<Account>,
        startTimestamp: Long,
        endTimestamp: Long
    ): Map<String, Money> {
        val accountUIDs = accounts.map { it.uid }
        val selection = "a." + AccountEntry.COLUMN_UID + " IN " + accountUIDs.joinIn()

        return computeSplitBalances(selection, null, startTimestamp, endTimestamp)
    }

    fun computeSplitBalances(
        accountsWhere: String?,
        accountsWhereArgs: Array<String?>?,
        startTimestamp: Long,
        endTimestamp: Long
    ): Map<String, Money> {
        var selection = ("t." + TransactionEntry.COLUMN_TEMPLATE + " = 0"
                + " AND s." + SplitEntry.COLUMN_QUANTITY_DENOM + " > 0")

        if (!accountsWhere.isNullOrEmpty()) {
            selection += " AND ($accountsWhere)"
        }

        val validStart = startTimestamp != AccountsDbAdapter.ALWAYS
        val validEnd = endTimestamp != AccountsDbAdapter.ALWAYS
        if (validStart && validEnd) {
            selection += " AND t." + TransactionEntry.COLUMN_TIMESTAMP + " BETWEEN " + startTimestamp + " AND " + endTimestamp
        } else if (validEnd) {
            selection += " AND t." + TransactionEntry.COLUMN_TIMESTAMP + " <= " + endTimestamp
        } else if (validStart) {
            selection += " AND t." + TransactionEntry.COLUMN_TIMESTAMP + " >= " + startTimestamp
        }

        val sql = ("SELECT SUM(s." + SplitEntry.COLUMN_QUANTITY_NUM + ")"
                + ", s." + SplitEntry.COLUMN_QUANTITY_DENOM
                + ", s." + SplitEntry.COLUMN_TYPE
                + ", a." + AccountEntry.COLUMN_UID
                + ", a." + AccountEntry.COLUMN_COMMODITY_UID
                + " FROM " + TransactionEntry.TABLE_NAME + " t"
                + " INNER JOIN " + SplitEntry.TABLE_NAME + " s ON t." + TransactionEntry.COLUMN_UID + " = s." + SplitEntry.COLUMN_TRANSACTION_UID
                + " INNER JOIN " + AccountEntry.TABLE_NAME + " a ON s." + SplitEntry.COLUMN_ACCOUNT_UID + " = a." + AccountEntry.COLUMN_UID
                + " WHERE " + selection
                + " GROUP BY a." + AccountEntry.COLUMN_UID
                + ", s." + SplitEntry.COLUMN_TYPE
                + ", s." + SplitEntry.COLUMN_QUANTITY_DENOM)
        val cursor = db.rawQuery(sql, accountsWhereArgs)

        val totals = mutableMapOf<String, Money>()
        try {
            if (!cursor.moveToFirst()) {
                return totals
            }
            do {
                //FIXME beware of 64-bit overflow - get as BigInteger
                var amount_num = cursor.getLong(0)
                val amount_denom = cursor.getLong(1)
                val splitType = cursor.getString(2)
                val accountUID = cursor.getString(3)
                val commodityUID = cursor.getString(4)

                if (credit == splitType) {
                    amount_num = -amount_num
                }
                val amount = toBigDecimal(amount_num, amount_denom)
                val commodity = commoditiesDbAdapter.getRecord(commodityUID)
                val balance = Money(amount, commodity)
                var total = totals[accountUID]
                if (total == null) {
                    total = balance
                } else {
                    total += balance
                }
                totals[accountUID] = total
            } while (cursor.moveToNext())
        } finally {
            cursor.close()
        }

        return totals
    }

    /**
     * Returns the list of splits for a transaction
     *
     * @param transactionUID String unique ID of transaction
     * @return List of [Split]s
     */
    fun getSplitsForTransaction(transactionUID: String): List<Split> {
        val cursor = fetchSplitsForTransaction(transactionUID)
        return getRecords(cursor)
    }

    /**
     * Returns the list of splits for a transaction
     *
     * @param transactionID DB record ID of the transaction
     * @return List of [Split]s
     * @see .getSplitsForTransaction
     * @see .getTransactionUID
     */
    fun getSplitsForTransaction(transactionID: Long): List<Split> {
        return getSplitsForTransaction(getTransactionUID(transactionID))
    }

    /**
     * Fetch splits for a given transaction within a specific account
     *
     * @param transactionUID String unique ID of transaction
     * @param accountUID     String unique ID of account
     * @return List of splits
     */
    fun getSplitsForTransactionInAccount(
        transactionUID: String,
        accountUID: String
    ): List<Split> {
        val cursor = fetchSplitsForTransactionAndAccount(transactionUID, accountUID)
        return getRecords(cursor)
    }

    /**
     * Fetches a collection of splits for a given condition and sorted by `sortOrder`
     *
     * @param where     String condition, formatted as SQL WHERE clause
     * @param whereArgs where args
     * @param sortOrder Sort order for the returned records
     * @return Cursor to split records
     */
    fun fetchSplits(where: String?, whereArgs: Array<String?>?, sortOrder: String?): Cursor? {
        return db.query(tableName, null, where, whereArgs, null, null, sortOrder)
    }

    /**
     * Returns a Cursor to a dataset of splits belonging to a specific transaction
     *
     * @param transactionUID Unique idendtifier of the transaction
     * @return Cursor to splits
     */
    fun fetchSplitsForTransaction(transactionUID: String): Cursor? {
        Timber.v("Fetching all splits for transaction UID %s", transactionUID)
        val where = SplitEntry.COLUMN_TRANSACTION_UID + " = ?"
        val whereArgs = arrayOf<String?>(transactionUID)
        val orderBy = SplitEntry.COLUMN_ID + " ASC"
        return db.query(tableName, null, where, whereArgs, null, null, orderBy)
    }

    /**
     * Fetches splits for a given account
     *
     * @param accountUID String unique ID of account
     * @return Cursor containing splits dataset
     */
    fun fetchSplitsForAccount(accountUID: String): Cursor? {
        Timber.d("Fetching all splits for account UID %s", accountUID)

        //This is more complicated than a simple "where account_uid=?" query because
        // we need to *not* return any splits which belong to recurring transactions
        val queryBuilder = SQLiteQueryBuilder()
        queryBuilder.setTables(
            (TransactionEntry.TABLE_NAME
                    + " INNER JOIN " + SplitEntry.TABLE_NAME + " ON "
                    + TransactionEntry.TABLE_NAME + "." + TransactionEntry.COLUMN_UID + " = "
                    + SplitEntry.TABLE_NAME + "." + SplitEntry.COLUMN_TRANSACTION_UID)
        )
        queryBuilder.isDistinct = true
        val projectionIn = arrayOf<String?>(SplitEntry.TABLE_NAME + ".*")
        val selection = (SplitEntry.TABLE_NAME + "." + SplitEntry.COLUMN_ACCOUNT_UID + " = ?"
                + " AND " + TransactionEntry.TABLE_NAME + "." + TransactionEntry.COLUMN_TEMPLATE + " = 0")
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
     * Returns a cursor to splits for a given transaction and account
     *
     * @param transactionUID Unique idendtifier of the transaction
     * @param accountUID     String unique ID of account
     * @return Cursor to splits data set
     */
    fun fetchSplitsForTransactionAndAccount(transactionUID: String?, accountUID: String?): Cursor? {
        if (transactionUID.isNullOrEmpty() || accountUID.isNullOrEmpty()) return null

        Timber.v(
            "Fetching all splits for transaction ID %s and account ID %s",
            transactionUID, accountUID
        )
        return db.query(
            tableName,
            null, (SplitEntry.COLUMN_TRANSACTION_UID + " = ? AND "
                    + SplitEntry.COLUMN_ACCOUNT_UID + " = ?"),
            arrayOf<String?>(transactionUID, accountUID),
            null, null, SplitEntry.COLUMN_VALUE_NUM + " ASC"
        )
    }

    /**
     * Returns the unique ID of a transaction given the database record ID of same
     *
     * @param transactionId Database record ID of the transaction
     * @return String unique ID of the transaction or null if transaction with the ID cannot be found.
     */
    @Throws(IllegalArgumentException::class)
    fun getTransactionUID(transactionId: Long): String {
        val cursor = db.query(
            TransactionEntry.TABLE_NAME,
            arrayOf(TransactionEntry.COLUMN_UID),
            TransactionEntry.COLUMN_ID + " = " + transactionId,
            null, null, null, null
        )

        try {
            if (cursor.moveToFirst()) {
                return cursor.getString(0)
            }
            throw IllegalArgumentException("Transaction not found")
        } finally {
            cursor.close()
        }
    }

    override fun deleteRecord(rowId: Long): Boolean {
        val split = getRecord(rowId)
        val transactionUID = split.transactionUID
        var result = super.deleteRecord(rowId)

        if (!result)  //we didn't delete for whatever reason, invalid rowId etc
            return false

        //if we just deleted the last split, then remove the transaction from db
        if (!transactionUID.isNullOrEmpty()) {
            val cursor = fetchSplitsForTransaction(transactionUID) ?: return false
            try {
                if (cursor.count > 0) {
                    val transactionID = getTransactionID(transactionUID)
                    result = db.delete(
                        TransactionEntry.TABLE_NAME,
                        TransactionEntry.COLUMN_ID + "=" + transactionID, null
                    ) > 0
                }
            } finally {
                cursor.close()
            }
        }
        return result
    }

    /**
     * Returns the database record ID for the specified transaction UID
     *
     * @param transactionUID Unique identifier of the transaction
     * @return Database record ID for the transaction
     */
    @Throws(IllegalArgumentException::class)
    fun getTransactionID(transactionUID: String): Long {
        val c = db.query(
            TransactionEntry.TABLE_NAME,
            arrayOf(TransactionEntry.COLUMN_ID),
            TransactionEntry.COLUMN_UID + "=?",
            arrayOf<String?>(transactionUID), null, null, null
        )
        try {
            if (c.moveToFirst()) {
                return c.getLong(0)
            }
            throw IllegalArgumentException("Transaction not found")
        } finally {
            c.close()
        }
    }

    fun reassignAccount(oldAccountUID: String, newAccountUID: String) {
        updateRecords(
            SplitEntry.COLUMN_ACCOUNT_UID + " = ?",
            arrayOf<String?>(oldAccountUID),
            SplitEntry.COLUMN_ACCOUNT_UID,
            newAccountUID
        )
    }

    /**
     * Returns the commodity of the account
     * with unique Identifier `accountUID`
     *
     * @param accountUID Unique Identifier of the account
     * @return Commodity of the account.
     */
    @Throws(IllegalArgumentException::class)
    fun getAccountCommodity(accountUID: String): Commodity {
        var commodity = accountCommodities[accountUID]
        if (commodity != null) {
            return commodity
        }
        val cursor = db.query(
            AccountEntry.TABLE_NAME,
            arrayOf<String?>(AccountEntry.COLUMN_COMMODITY_UID),
            AccountEntry.COLUMN_UID + "= ?",
            arrayOf<String?>(accountUID),
            null, null, null
        )
        try {
            if (cursor.moveToFirst()) {
                val commodityUID = cursor.getString(0)
                commodity = commoditiesDbAdapter.getRecord(commodityUID)
                accountCommodities[accountUID] = commodity
                return commodity
            }
            throw IllegalArgumentException("Account not found")
        } finally {
            cursor.close()
        }
    }

    companion object {
        private val credit = TransactionType.CREDIT.value

        /**
         * Returns application-wide instance of the database adapter
         *
         * @return SplitsDbAdapter instance
         */
        val instance: SplitsDbAdapter get() = GnuCashApplication.splitsDbAdapter!!
    }
}
