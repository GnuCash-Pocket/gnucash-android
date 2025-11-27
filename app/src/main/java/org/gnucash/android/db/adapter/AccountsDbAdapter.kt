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
import android.content.Context
import android.database.Cursor
import android.database.DatabaseUtils
import android.database.SQLException
import android.database.sqlite.SQLiteStatement
import androidx.annotation.ColorInt
import androidx.core.content.ContextCompat
import org.gnucash.android.R
import org.gnucash.android.app.GnuCashApplication
import org.gnucash.android.app.GnuCashApplication.Companion.appContext
import org.gnucash.android.app.GnuCashApplication.Companion.defaultCurrencyCode
import org.gnucash.android.app.GnuCashApplication.Companion.isDoubleEntryEnabled
import org.gnucash.android.db.DatabaseHelper.Companion.sqlEscapeLike
import org.gnucash.android.db.DatabaseHolder
import org.gnucash.android.db.DatabaseSchema.AccountEntry
import org.gnucash.android.db.DatabaseSchema.BudgetAmountEntry
import org.gnucash.android.db.DatabaseSchema.BudgetEntry
import org.gnucash.android.db.DatabaseSchema.CommodityEntry
import org.gnucash.android.db.DatabaseSchema.PriceEntry
import org.gnucash.android.db.DatabaseSchema.RecurrenceEntry
import org.gnucash.android.db.DatabaseSchema.ScheduledActionEntry
import org.gnucash.android.db.DatabaseSchema.SplitEntry
import org.gnucash.android.db.DatabaseSchema.TransactionEntry
import org.gnucash.android.db.bindBoolean
import org.gnucash.android.db.forEach
import org.gnucash.android.db.getBigDecimal
import org.gnucash.android.db.getBoolean
import org.gnucash.android.db.getString
import org.gnucash.android.db.joinIn
import org.gnucash.android.model.Account
import org.gnucash.android.model.AccountType
import org.gnucash.android.model.Commodity
import org.gnucash.android.model.Money
import org.gnucash.android.model.Money.Companion.createZeroInstance
import org.gnucash.android.model.Split
import org.gnucash.android.model.Transaction
import org.gnucash.android.model.Transaction.Companion.getTypeForBalance
import org.gnucash.android.util.TimestampHelper.getUtcStringFromTimestamp
import org.gnucash.android.util.set
import timber.log.Timber
import java.io.IOException
import java.sql.Timestamp

/**
 * Manages persistence of [Account]s in the database
 * Handles adding, modifying and deleting of account records.
 *
 * @author Ngewi Fet <ngewif@gmail.com>
 * @author Yongxin Wang <fefe.wyx@gmail.com>
 * @author Oleksandr Tyshkovets <olexandr.tyshkovets@gmail.com>
 */
class AccountsDbAdapter(
    /**
     * Transactions database adapter for manipulating transactions associated with accounts
     */
    val transactionsDbAdapter: TransactionsDbAdapter,
    val pricesDbAdapter: PricesDbAdapter = PricesDbAdapter(transactionsDbAdapter.commoditiesDbAdapter)
) : DatabaseAdapter<Account>(
    transactionsDbAdapter.holder,
    AccountEntry.TABLE_NAME,
    arrayOf(
        AccountEntry.COLUMN_NAME,
        AccountEntry.COLUMN_DESCRIPTION,
        AccountEntry.COLUMN_TYPE,
        AccountEntry.COLUMN_CURRENCY,
        AccountEntry.COLUMN_COLOR_CODE,
        AccountEntry.COLUMN_FAVORITE,
        AccountEntry.COLUMN_FULL_NAME,
        AccountEntry.COLUMN_PLACEHOLDER,
        AccountEntry.COLUMN_CREATED_AT,
        AccountEntry.COLUMN_HIDDEN,
        AccountEntry.COLUMN_COMMODITY_UID,
        AccountEntry.COLUMN_PARENT_ACCOUNT_UID,
        AccountEntry.COLUMN_DEFAULT_TRANSFER_ACCOUNT_UID,
        AccountEntry.COLUMN_NOTES,
        AccountEntry.COLUMN_TEMPLATE
    ), true
) {
    /**
     * Commodities database adapter for commodity manipulation
     */
    val commoditiesDbAdapter: CommoditiesDbAdapter = transactionsDbAdapter.commoditiesDbAdapter

    private var rootUID: String? = null

    /**
     * Convenience overloaded constructor.
     * This is used when an AccountsDbAdapter object is needed quickly. Otherwise, the other
     * constructor [.AccountsDbAdapter]
     * should be used whenever possible
     *
     * @param holder Database holder
     */
    constructor(holder: DatabaseHolder, initCommodity: Boolean = false) :
            this(TransactionsDbAdapter(holder, initCommodity))

    @Throws(IOException::class)
    override fun close() {
        commoditiesDbAdapter.close()
        transactionsDbAdapter.close()
        pricesDbAdapter.close()
        super.close()
    }

    /**
     * Adds an account to the database.
     * If an account already exists in the database with the same GUID, it is replaced.
     *
     * @param account [Account] to be inserted to database
     */
    @Throws(SQLException::class)
    override fun addRecord(account: Account, updateMethod: UpdateMethod) {
        Timber.d("Replace account to db")
        if (account.isRoot && !account.isTemplate) {
            rootUID = account.uid
        }
        //in-case the account already existed, we want to update the templates based on it as well
        super.addRecord(account, updateMethod)
        //now add transactions if there are any
        // NB! Beware of transactions that reference accounts not yet in the db,
        if (!account.isRoot) {
            for (t in account.transactions) {
                t.commodity = account.commodity
                transactionsDbAdapter.addRecord(t, updateMethod)
            }
            val scheduledTransactions =
                transactionsDbAdapter.getScheduledTransactionsForAccount(account.uid)
            for (transaction in scheduledTransactions) {
                transactionsDbAdapter.update(transaction)
            }
        }
    }

    /**
     * Adds some accounts and their transactions to the database in bulk.
     *
     * If an account already exists in the database with the same GUID, it is replaced.
     * This function will NOT try to determine the full name
     * of the accounts inserted, full names should be generated prior to the insert.
     * <br></br>All or none of the accounts will be inserted;
     *
     * @param accounts [Account] to be inserted to database
     * @return number of rows inserted
     */
    @Throws(SQLException::class)
    override fun bulkAddRecords(accounts: List<Account>, updateMethod: UpdateMethod): Long {
        //scheduled transactions are not fetched from the database when getting account transactions
        //so we retrieve those which affect this account and then re-save them later
        //this is necessary because the database has ON DELETE CASCADE between accounts and splits
        //and all accounts are editing via SQL REPLACE
        // TODO: 20.04.2016 Investigate if we can safely remove updating the transactions when bulk updating accounts */

        val transactions = ArrayList<Transaction>(accounts.size * 2)
        for (account in accounts) {
            transactions.addAll(account.transactions)
            transactions.addAll(transactionsDbAdapter.getScheduledTransactionsForAccount(account.uid))
        }
        val nRow = super.bulkAddRecords(accounts, updateMethod)

        if (nRow > 0 && !transactions.isEmpty()) {
            transactionsDbAdapter.bulkAddRecords(transactions, updateMethod)
        }
        return nRow
    }

    @Throws(SQLException::class)
    override fun bind(stmt: SQLiteStatement, account: Account): SQLiteStatement {
        var parentAccountUID = account.parentUID
        if (!account.isRoot) {
            if (parentAccountUID.isNullOrEmpty()) {
                parentAccountUID = rootAccountUID
                account.parentUID = parentAccountUID
            }
            //update the fully qualified account name
            account.fullName = getFullyQualifiedAccountName(account)
        }

        bindBaseModel(stmt, account)
        stmt.bindString(1, account.name)
        stmt.bindString(2, account.description)
        stmt.bindString(3, account.accountType.name)
        stmt.bindString(4, account.commodity.currencyCode)
        if (account.color != Account.DEFAULT_COLOR) {
            stmt.bindString(5, account.colorHexString)
        }
        stmt.bindBoolean(6, account.isFavorite)
        stmt.bindString(7, account.fullName)
        stmt.bindBoolean(8, account.isPlaceholder)
        stmt.bindString(9, getUtcStringFromTimestamp(account.createdTimestamp))
        stmt.bindBoolean(10, account.isHidden)
        stmt.bindString(11, account.commodity.uid)
        if (parentAccountUID != null) {
            stmt.bindString(12, parentAccountUID)
        }
        if (account.defaultTransferAccountUID != null) {
            stmt.bindString(13, account.defaultTransferAccountUID)
        }
        if (account.note != null) {
            stmt.bindString(14, account.note)
        }
        stmt.bindBoolean(15, account.isTemplate)

        return stmt
    }

    /**
     * This feature goes through all the rows in the accounts and changes value for `columnKey` to `newValue`<br></br>
     * The `newValue` parameter is taken as string since SQLite typically stores everything as text.
     *
     * **This method affects all rows, exercise caution when using it**
     *
     * @param columnKey Name of column to be updated
     * @param newValue  New value to be assigned to the columnKey
     * @return Number of records affected
     */
    fun updateAllAccounts(columnKey: String, newValue: String?): Int {
        if (isCached) cache.clear()
        val contentValues = ContentValues()
        contentValues[columnKey] = newValue
        return db.update(AccountEntry.TABLE_NAME, contentValues, null, null)
    }

    /**
     * Updates a specific entry of an account
     *
     * @param accountId Database record ID of the account to be updated
     * @param columnKey Name of column to be updated
     * @param newValue  New value to be assigned to the columnKey
     * @return Number of records affected
     */
    fun updateAccount(accountId: Long, columnKey: String, newValue: String?): Int {
        return updateRecord(tableName, accountId, columnKey, newValue)
    }

    fun updateAccount(accountId: Long, columnKey: String, newValue: Boolean): Int {
        return updateAccount(accountId, columnKey, if (newValue) "1" else "0")
    }

    /**
     * This method goes through all the children of `accountUID` and updates the parent account
     * to `newParentAccountUID`. The fully qualified account names for all descendant accounts will also be updated.
     *
     * @param parentAccountUID    GUID of the account
     * @param newParentAccountUID GUID of the new parent account
     */
    fun reassignDescendantAccounts(parentAccountUID: String, newParentAccountUID: String) {
        if (isCached) cache.clear()
        val descendantAccountUIDs = getDescendantAccountUIDs(parentAccountUID, null, null)
        if (descendantAccountUIDs.isEmpty()) return
        val descendantAccounts = getAllRecords(
            AccountEntry.COLUMN_UID + " IN " + descendantAccountUIDs.joinIn(),
            null
        )
        val accountsByUID = descendantAccounts.associateBy(Account::uid)
        val parentAccountFullName = if (getAccountType(newParentAccountUID) == AccountType.ROOT) {
            ""
        } else {
            getAccountFullName(newParentAccountUID)
        }
        val contentValues = ContentValues()
        for (account in descendantAccounts) {
            contentValues.clear()

            if (parentAccountUID == account.parentUID) {
                // direct descendant
                account.parentUID = newParentAccountUID
                if (parentAccountFullName.isNullOrEmpty()) {
                    account.fullName = account.name
                } else {
                    account.fullName = parentAccountFullName + ACCOUNT_NAME_SEPARATOR + account.name
                }
                contentValues[AccountEntry.COLUMN_PARENT_ACCOUNT_UID] = newParentAccountUID
            } else {
                // indirect descendant
                val parentAccount = accountsByUID[account.parentUID]
                account.fullName = parentAccount!!.fullName + ACCOUNT_NAME_SEPARATOR + account.name
            }
            // update DB
            contentValues[AccountEntry.COLUMN_FULL_NAME] = account.fullName
            db.update(
                tableName,
                contentValues,
                AccountEntry.COLUMN_UID + " = ?",
                arrayOf<String?>(account.uid)
            )
        }
    }

    /**
     * Deletes an account and its transactions, and all its sub-accounts and their transactions.
     *
     * Not only the splits belonging to the account and its descendants will be deleted, rather,
     * the complete transactions associated with this account and its descendants
     * (i.e. as long as the transaction has at least one split belonging to one of the accounts).
     * This prevents an split imbalance from being caused.
     *
     * If you want to preserve transactions, make sure to first reassign the children accounts (see [.reassignDescendantAccounts]
     * before calling this method. This method will however not delete a root account.
     *
     * **This method does a thorough delete, use with caution!!!**
     *
     * @param accountUID Database UID of account
     * @return `true` if the account and sub-accounts were all successfully deleted, `false` if
     * even one was not deleted
     * @see .reassignDescendantAccounts
     */
    fun recursiveDeleteAccount(accountUID: String): Boolean {
        if (getAccountType(accountUID) == AccountType.ROOT) {
            // refuse to delete ROOT
            return false
        }

        Timber.d("Delete account with rowId with its transactions and sub-accounts: %s", accountUID)
        if (isCached) cache.clear()

        val descendantAccountUIDs = getDescendantAccountUIDs(accountUID, null, null).toMutableList()
        try {
            beginTransaction()
            descendantAccountUIDs.add(accountUID) //add account to descendants list just for convenience
            for (descendantAccountUID in descendantAccountUIDs) {
                transactionsDbAdapter.deleteTransactionsForAccount(descendantAccountUID)
            }

            val accountUIDList = descendantAccountUIDs.joinIn()

            // delete accounts
            val deletedCount = db.delete(
                tableName,
                AccountEntry.COLUMN_UID + " IN " + accountUIDList,
                null
            ).toLong()

            //if we delete some accounts, reset the default transfer account to NULL
            //there is also a database trigger from db version > 12
            if (deletedCount > 0) {
                val contentValues = ContentValues()
                contentValues.putNull(AccountEntry.COLUMN_DEFAULT_TRANSFER_ACCOUNT_UID)
                db.update(
                    tableName, contentValues,
                    AccountEntry.COLUMN_DEFAULT_TRANSFER_ACCOUNT_UID + " IN " + accountUIDList,
                    null
                )
            }

            setTransactionSuccessful()
            return true
        } finally {
            endTransaction()
        }
    }

    /**
     * Builds an account instance with the provided cursor and loads its corresponding transactions.
     *
     * @param cursor Cursor pointing to account record in database
     * @return [Account] object constructed from database record
     */
    fun buildFullModelInstance(cursor: Cursor): Account {
        val account = buildModelInstance(cursor)
        account.transactions = transactionsDbAdapter.getAllTransactionsForAccount(account.uid)
        return account
    }

    /**
     * Builds an account instance with the provided cursor and loads its corresponding transactions.
     *
     * The method will not move the cursor position, so the cursor should already be pointing
     * to the account record in the database<br></br>
     * **Note** Unlike [.buildModelInstance] this method will not load transactions
     *
     * @param cursor Cursor pointing to account record in database
     * @return [Account] object constructed from database record
     */
    override fun buildModelInstance(cursor: Cursor): Account {
        val account = Account(cursor.getString(AccountEntry.COLUMN_NAME)!!)
        populateBaseModelAttributes(cursor, account)

        account.description = cursor.getString(AccountEntry.COLUMN_DESCRIPTION)
        account.parentUID = cursor.getString(AccountEntry.COLUMN_PARENT_ACCOUNT_UID)
        account.accountType = AccountType.valueOf(cursor.getString(AccountEntry.COLUMN_TYPE)!!)
        val commodityUID = cursor.getString(AccountEntry.COLUMN_COMMODITY_UID)!!
        account.commodity = commoditiesDbAdapter.getRecord(commodityUID)
        account.isPlaceholder = cursor.getBoolean(AccountEntry.COLUMN_PLACEHOLDER)
        account.defaultTransferAccountUID =
            cursor.getString(AccountEntry.COLUMN_DEFAULT_TRANSFER_ACCOUNT_UID)
        val color = cursor.getString(AccountEntry.COLUMN_COLOR_CODE)
        account.setColor(color)
        account.isFavorite = cursor.getBoolean(AccountEntry.COLUMN_FAVORITE)
        account.fullName = cursor.getString(AccountEntry.COLUMN_FULL_NAME)
        account.isHidden = cursor.getBoolean(AccountEntry.COLUMN_HIDDEN)
        if (account.isRoot) {
            account.isHidden = false
        }
        account.note = cursor.getString(AccountEntry.COLUMN_NOTES)
        account.isTemplate = cursor.getBoolean(AccountEntry.COLUMN_TEMPLATE)
        if (account.isRoot) {
            account.isHidden = false
            account.isPlaceholder = false
        }
        return account
    }

    /**
     * Returns the  unique ID of the parent account of the account with unique ID `uid`
     * If the account has no parent, null is returned
     *
     * @param uid Unique Identifier of account whose parent is to be returned. Should not be null
     * @return DB record UID of the parent account, null if the account has no parent
     */
    fun getParentAccountUID(uid: String): String? {
        if (isCached) {
            val account = cache[uid]
            if (account != null) return account.parentUID
        }
        val cursor = db.query(
            tableName,
            arrayOf<String?>(AccountEntry.COLUMN_PARENT_ACCOUNT_UID),
            AccountEntry.COLUMN_UID + " = ?",
            arrayOf<String?>(uid),
            null, null, null, null
        )
        try {
            if (cursor.moveToFirst()) {
                return cursor.getString(0)
            }
        } finally {
            cursor.close()
        }
        return null
    }

    /**
     * Returns the color code for the account in format #rrggbb
     *
     * @param accountUID UID of the account
     * @return String color code of account or null if none
     */
    @ColorInt
    fun getAccountColor(accountUID: String): Int {
        try {
            val account = getRecord(accountUID)
            return account.color
        } catch (e: IllegalArgumentException) {
            Timber.e(e)
            return Account.DEFAULT_COLOR
        }
    }

    /**
     * Overloaded method. Resolves the account unique ID from the row ID and makes a call to [.getAccountType]
     *
     * @param accountId Database row ID of the account
     * @return [AccountType] of the account
     */
    fun getAccountType(accountId: Long): AccountType {
        return getAccountType(getUID(accountId))
    }

    /**
     * Returns a list of all account entries in the system (includes root account)
     * No transactions are loaded, just the accounts
     *
     * @return List of [Account]s in the database
     */
    val simpleAccounts: List<Account>
        get() = getAllRecords(null, null, AccountEntry.COLUMN_FULL_NAME + " ASC")

    /**
     * Returns a list of accounts which have transactions that have not been exported yet
     *
     * @param lastExportTimeStamp Timestamp after which to any transactions created/modified should be exported
     * @return List of [Account]s with unexported transactions
     */
    fun getExportableAccounts(lastExportTimeStamp: Timestamp): List<Account> {
        val table = TransactionEntry.TABLE_NAME + " t, " + SplitEntry.TABLE_NAME + " s ON " +
                "t." + TransactionEntry.COLUMN_UID + " = " +
                "s." + SplitEntry.COLUMN_TRANSACTION_UID + ", " +
                AccountEntry.TABLE_NAME + " a ON a." + AccountEntry.COLUMN_UID + " = s." + SplitEntry.COLUMN_ACCOUNT_UID
        val columns = arrayOf<String?>("a.*")
        val selection = "t." + TransactionEntry.COLUMN_MODIFIED_AT + " >= ?"
        val selectionArgs = arrayOf<String?>(getUtcStringFromTimestamp(lastExportTimeStamp))
        val groupBy = "a." + AccountEntry.COLUMN_UID
        val orderBy = "a." + AccountEntry.COLUMN_FULL_NAME
        val cursor = db.query(table, columns, selection, selectionArgs, groupBy, null, orderBy)
        return getRecords(cursor)
    }

    /**
     * Retrieves the unique ID of the imbalance account for a particular currency (creates the imbalance account
     * on demand if necessary)
     *
     * @param commodity Commodity for the imbalance account
     * @return String unique ID of the account
     */
    fun getOrCreateImbalanceAccountUID(context: Context, commodity: Commodity): String {
        return getOrCreateImbalanceAccount(context, commodity)!!.uid
    }

    /**
     * Retrieves the unique ID of the imbalance account for a particular currency (creates the imbalance account
     * on demand if necessary)
     *
     * @param commodity Commodity for the imbalance account
     * @return The account
     */
    fun getOrCreateImbalanceAccount(context: Context, commodity: Commodity): Account? {
        val imbalanceAccountName: String = getImbalanceAccountName(context, commodity)
        val uid = findAccountUidByFullName(imbalanceAccountName)
        if (uid.isNullOrEmpty()) {
            val account = Account(imbalanceAccountName, commodity)
            account.accountType = AccountType.BANK
            account.parentUID = rootAccountUID
            account.isHidden = !isDoubleEntryEnabled(context)
            insert(account)
            return account
        }
        return getRecord(uid)
    }

    /**
     * Returns the GUID of the imbalance account for the commodity
     *
     *
     * This method will not create the imbalance account if it doesn't exist
     *
     * @param commodity Commodity for the imbalance account
     * @return GUID of the account or null if the account doesn't exist yet
     * @see .getOrCreateImbalanceAccountUID
     */
    fun getImbalanceAccountUID(context: Context, commodity: Commodity): String? {
        val imbalanceAccountName: String = getImbalanceAccountName(context, commodity)
        return findAccountUidByFullName(imbalanceAccountName)
    }

    /**
     * Creates the account with the specified name and returns its unique identifier.
     *
     * If a full hierarchical account name is provided, then the whole hierarchy is created and the
     * unique ID of the last account (at bottom) of the hierarchy is returned
     *
     * @param fullName    Fully qualified name of the account
     * @param accountType Type to assign to all accounts created
     * @return String unique ID of the account at bottom of hierarchy
     */
    fun createAccountHierarchy(fullName: String?, accountType: AccountType): String {
        require(!fullName.isNullOrEmpty()) { "Full name required" }
        val tokens = fullName.trim().split(ACCOUNT_NAME_SEPARATOR.toRegex())
            .dropLastWhile { it.isEmpty() }.toTypedArray()
        var uid = rootAccountUID
        var parentName: String? = ""
        val accountsList = ArrayList<Account>()
        val commodity = commoditiesDbAdapter.defaultCommodity
        for (token in tokens) {
            parentName += token
            val parentUID = findAccountUidByFullName(parentName)
            if (parentUID != null) { //the parent account exists, don't recreate
                uid = parentUID
            } else {
                val account = Account(token, commodity)
                account.accountType = accountType
                account.parentUID = uid //set its parent
                account.fullName = parentName
                accountsList.add(account)
                uid = account.uid
            }
            parentName += ACCOUNT_NAME_SEPARATOR
        }
        if (accountsList.isNotEmpty()) {
            bulkAddRecords(accountsList, UpdateMethod.Insert)
        }
        // if fullName is not empty, loop will be entered and then uid will never be null
        return uid
    }

    val getOrCreateOpeningBalanceAccountUID: String
        /**
         * Returns the unique ID of the opening balance account or creates one if necessary
         *
         * @return String unique ID of the opening balance account
         */
        get() {
            val openingBalanceAccountName: String? = openingBalanceAccountFullName
            var uid = findAccountUidByFullName(openingBalanceAccountName)
            if (uid == null) {
                uid = createAccountHierarchy(
                    openingBalanceAccountName,
                    AccountType.EQUITY
                )
            }
            return uid
        }

    /**
     * Finds an account unique ID by its full name
     *
     * @param fullName Fully qualified name of the account
     * @return String unique ID of the account or null if no match is found
     */
    fun findAccountUidByFullName(fullName: String?): String? {
        if (isCached) {
            for (account in cache.values) {
                if (account.fullName == fullName) {
                    return account.uid
                }
            }
        }
        val c = db.query(
            tableName, arrayOf<String?>(AccountEntry.COLUMN_UID),
            AccountEntry.COLUMN_FULL_NAME + "= ?", arrayOf<String?>(fullName),
            null, null, null, "1"
        )
        try {
            if (c.moveToNext()) {
                return c.getString(0)
            } else {
                return null
            }
        } finally {
            c.close()
        }
    }

    /**
     * Returns a cursor to all account records in the database.
     * GnuCash ROOT accounts and hidden accounts will **not** be included in the result set
     *
     * @return [Cursor] to all account records
     */
    override fun fetchAllRecords(): Cursor {
        Timber.v("Fetching all accounts from db")
        val where = AccountEntry.COLUMN_HIDDEN + " = 0 AND " + AccountEntry.COLUMN_TYPE + " != ?"
        val whereArgs = arrayOf<String?>(AccountType.ROOT.name)
        val orderBy = AccountEntry.COLUMN_NAME + " ASC"
        return fetchAccounts(where, whereArgs, orderBy)
    }

    /**
     * Returns a Cursor set of accounts which fulfill `where`
     * and ordered by `orderBy`
     *
     * @param where     SQL WHERE statement without the 'WHERE' itself
     * @param whereArgs args to where clause
     * @param orderBy   orderBy clause
     * @return Cursor set of accounts which fulfill `where`
     */
    fun fetchAccounts(
        where: String?,
        whereArgs: Array<String?>?,
        orderBy: String? = null
    ): Cursor {
        var orderBy = orderBy
        if (orderBy.isNullOrEmpty()) {
            orderBy = AccountEntry.COLUMN_NAME + " ASC"
        }
        return fetchAllRecords(where, whereArgs, orderBy)
    }

    /**
     * Returns the balance of an account while taking sub-accounts into consideration
     *
     * @return Account Balance of an account including sub-accounts
     */
    fun getAccountBalance(accountUID: String): Money {
        return computeBalance(accountUID, ALWAYS, ALWAYS, true)
    }

    /**
     * Returns the balance of an account while taking sub-accounts into consideration
     *
     * @return Account Balance of an account including sub-accounts
     */
    fun getAccountBalance(account: Account): Money {
        return getAccountBalance(account, ALWAYS, ALWAYS)
    }

    /**
     * Returns the current balance of an account while taking sub-accounts into consideration
     *
     * @return Account Balance of an account including sub-accounts
     */
    fun getCurrentAccountBalance(account: Account): Money {
        return getAccountBalance(account, ALWAYS, System.currentTimeMillis())
    }

    /**
     * Returns the balance of an account within the specified time range while taking sub-accounts into consideration
     *
     * @param accountUID     the account's UUID
     * @param startTimestamp the start timestamp of the time range
     * @param endTimestamp   the end timestamp of the time range
     * @return the balance of an account within the specified range including sub-accounts
     */
    fun getAccountBalance(accountUID: String, startTimestamp: Long, endTimestamp: Long): Money {
        return getAccountBalance(accountUID, startTimestamp, endTimestamp, true)
    }

    /**
     * Returns the balance of an account within the specified time range while taking sub-accounts into consideration
     *
     * @param account        the account
     * @param startTimestamp the start timestamp of the time range
     * @param endTimestamp   the end timestamp of the time range
     * @return the balance of an account within the specified range including sub-accounts
     */
    fun getAccountBalance(account: Account, startTimestamp: Long, endTimestamp: Long): Money {
        return getAccountBalance(account, startTimestamp, endTimestamp, true)
    }

    /**
     * Returns the balance of an account within the specified time range while taking sub-accounts into consideration
     *
     * @param accountUID         the account's UUID
     * @param startTimestamp     the start timestamp of the time range
     * @param endTimestamp       the end timestamp of the time range
     * @param includeSubAccounts include the sub-accounts' balances?
     * @return the balance of an account within the specified range including sub-accounts
     */
    fun getAccountBalance(
        accountUID: String,
        startTimestamp: Long,
        endTimestamp: Long,
        includeSubAccounts: Boolean
    ): Money {
        return computeBalance(accountUID, startTimestamp, endTimestamp, includeSubAccounts)
    }

    /**
     * Returns the balance of an account within the specified time range while taking sub-accounts into consideration
     *
     * @param account            the account
     * @param startTimestamp     the start timestamp of the time range
     * @param endTimestamp       the end timestamp of the time range
     * @param includeSubAccounts include the sub-accounts' balances?
     * @return the balance of an account within the specified range including sub-accounts
     */
    fun getAccountBalance(
        account: Account,
        startTimestamp: Long,
        endTimestamp: Long,
        includeSubAccounts: Boolean
    ): Money {
        return computeBalance(account, startTimestamp, endTimestamp, includeSubAccounts)
    }

    /**
     * Compute the account balance for all accounts with the specified type within a specific duration
     *
     * @param accountType    Account Type for which to compute balance
     * @param currency       the currency
     * @param startTimestamp Begin time for the duration in milliseconds
     * @param endTimestamp   End time for duration in milliseconds
     * @return Account balance
     */
    fun getAccountsBalance(
        accountType: AccountType,
        currency: Commodity,
        startTimestamp: Long,
        endTimestamp: Long
    ): Money {
        val where = (AccountEntry.COLUMN_TYPE + " = ?"
                + " AND " + AccountEntry.COLUMN_TEMPLATE + " = 0")
        val whereArgs = arrayOf<String?>(accountType.name)
        val accounts = getAllRecords(where, whereArgs)
        return getAccountsBalance(accounts, currency, startTimestamp, endTimestamp)
    }

    /**
     * Returns the account balance for all accounts types specified
     *
     * @param accountTypes List of account types
     * @param currency     The currency
     * @param start        Begin timestamp for transactions
     * @param end          End timestamp of transactions
     * @return Money balance of the account types
     */
    fun getBalancesByType(
        accountTypes: List<AccountType>,
        currency: Commodity,
        start: Long,
        end: Long
    ): Money {
        var balance = createZeroInstance(currency)
        for (accountType in accountTypes) {
            val accountsBalance = getAccountsBalance(accountType, currency, start, end)
            balance += accountsBalance
        }
        return balance
    }

    /**
     * Returns the current account balance for the accounts type.
     *
     * @param accountTypes The account type
     * @param currency     The currency.
     * @return Money balance of the account type
     */
    fun getCurrentAccountsBalance(
        accountTypes: List<AccountType>,
        currency: Commodity
    ): Money {
        return getBalancesByType(accountTypes, currency, ALWAYS, System.currentTimeMillis())
    }

    private fun computeBalance(
        accountUID: String,
        startTimestamp: Long,
        endTimestamp: Long,
        includeSubAccounts: Boolean
    ): Money {
        val account = getRecord(accountUID)
        return computeBalance(account, startTimestamp, endTimestamp, includeSubAccounts)
    }

    private fun computeBalance(
        account: Account,
        startTimestamp: Long,
        endTimestamp: Long,
        includeSubAccounts: Boolean
    ): Money {
        Timber.d("Computing account balance for [%s]", account)
        val accountUID = account.uid
        val columns = arrayOf<String?>(AccountEntry.COLUMN_BALANCE)
        val selection = AccountEntry.COLUMN_UID + "=?"
        val selectionArgs = arrayOf<String?>(accountUID)

        // Is the value cached?
        val useCachedValue = (startTimestamp == ALWAYS) && (endTimestamp == ALWAYS)
        if (useCachedValue) {
            val cursor = db.query(tableName, columns, selection, selectionArgs, null, null, null)
            try {
                if (cursor.moveToFirst()) {
                    val amount = cursor.getBigDecimal(0)
                    if (amount != null) {
                        return Money(amount, account.commodity)
                    }
                }
            } finally {
                cursor.close()
            }
        }

        var balance = computeSplitsBalance(account, startTimestamp, endTimestamp)

        if (includeSubAccounts) {
            val commodity = account.commodity
            val children = getChildren(accountUID)
            Timber.d("compute account children : %d", children.size)
            for (childUID in children) {
                val child = getRecord(childUID)
                val childCommodity = child!!.commodity
                var childBalance = computeBalance(child, startTimestamp, endTimestamp, true)
                if (childBalance.isAmountZero) continue
                val price = pricesDbAdapter.getPrice(childCommodity, commodity) ?: continue
                balance += childBalance * price
            }
        }

        // Cache for next read.
        if (useCachedValue) {
            val values = ContentValues()
            values[AccountEntry.COLUMN_BALANCE] = balance.toBigDecimal()
            db.update(tableName, values, selection, selectionArgs)
        }

        return balance
    }

    private fun computeSplitsBalance(
        account: Account,
        startTimestamp: Long,
        endTimestamp: Long
    ): Money {
        val accountType = account.accountType
        val splitsDbAdapter = transactionsDbAdapter.splitsDbAdapter
        val balance = splitsDbAdapter.computeSplitBalance(account, startTimestamp, endTimestamp)
        return if (accountType.hasDebitNormalBalance) balance else -balance
    }

    /**
     * Returns the balance of account list within the specified time range. The default currency
     * takes as base currency.
     *
     * @param accountUIDList list of account UIDs
     * @param startTimestamp the start timestamp of the time range
     * @param endTimestamp   the end timestamp of the time range
     * @return Money balance of account list
     */
    fun getAccountsBalanceByUID(
        accountUIDList: List<String>,
        startTimestamp: Long,
        endTimestamp: Long
    ): Money {
        val accounts = mutableListOf<Account>()
        for (accountUID in accountUIDList) {
            getRecordOrNull(accountUID)?.let { accounts.add(it) }
        }
        return getAccountsBalance(accounts, startTimestamp, endTimestamp)
    }

    /**
     * Returns the balance of account list within the specified time range. The default currency
     * takes as base currency.
     *
     * @param accounts       list of accounts
     * @param startTimestamp the start timestamp of the time range
     * @param endTimestamp   the end timestamp of the time range
     * @return Money balance of account list
     */
    fun getAccountsBalance(
        accounts: List<Account>,
        startTimestamp: Long,
        endTimestamp: Long
    ): Money {
        val currencyCode = defaultCurrencyCode
        val commodity = commoditiesDbAdapter.getCurrency(currencyCode)
        return getAccountsBalance(accounts, commodity!!, startTimestamp, endTimestamp)
    }

    /**
     * Returns the balance of account list within the specified time range. The default currency
     * takes as base currency.
     *
     * @param accounts       list of accounts
     * @param currency       The target currency
     * @param startTimestamp the start timestamp of the time range
     * @param endTimestamp   the end timestamp of the time range
     * @return Money balance of account list
     */
    fun getAccountsBalance(
        accounts: List<Account>,
        currency: Commodity,
        startTimestamp: Long,
        endTimestamp: Long
    ): Money {
        var balance = createZeroInstance(currency)
        if ((startTimestamp == ALWAYS) && (endTimestamp == ALWAYS)) { // Use cached balances.
            for (account in accounts) {
                var accountBalance = getAccountBalance(account, startTimestamp, endTimestamp, false)
                if (accountBalance.isAmountZero) continue
                val price = pricesDbAdapter.getPrice(accountBalance.commodity, currency) ?: continue
                balance += accountBalance * price
            }
        } else {
            val balances = getAccountsBalances(accounts, startTimestamp, endTimestamp)
            for (account in accounts) {
                var accountBalance = balances[account.uid]
                if ((accountBalance == null) || accountBalance.isAmountZero) continue
                val price = pricesDbAdapter.getPrice(accountBalance.commodity, currency) ?: continue
                balance += accountBalance * price
            }
        }
        return balance
    }

    /**
     * Retrieve all descendant accounts of an account
     * Note, in filtering, once an account is filtered out, all its descendants
     * will also be filtered out, even they don't meet the filter where
     *
     * @param accountUID The account to retrieve descendant accounts
     * @param where      Condition to filter accounts
     * @param whereArgs  Condition args to filter accounts
     * @return The descendant accounts list.
     */
    fun getDescendantAccountUIDs(
        accountUID: String,
        where: String?,
        whereArgs: Array<String?>?
    ): List<String> {
        // holds accountUID with all descendant accounts.
        val accounts = mutableListOf<String>()
        // holds descendant accounts of the same level
        val accountsLevel = mutableListOf<String>()
        val projection = arrayOf<String?>(AccountEntry.COLUMN_UID)
        val whereAnd = (if (where.isNullOrEmpty()) "" else " AND $where")
        val columnIndexUID = 0

        accountsLevel.add(accountUID)
        do {
            val accountsLevelIn = accountsLevel.joinIn()
            accountsLevel.clear()
            db.query(
                tableName,
                projection,
                AccountEntry.COLUMN_PARENT_ACCOUNT_UID + " IN " + accountsLevelIn + whereAnd,
                whereArgs,
                null,
                null,
                AccountEntry.COLUMN_FULL_NAME
            ).forEach { cursor ->
                accountsLevel.add(cursor.getString(columnIndexUID))
            }
            accounts.addAll(accountsLevel)
        } while (!accountsLevel.isEmpty())
        return accounts
    }

    fun getChildren(accountUID: String): List<String> {
        val accounts = mutableListOf<String>()
        val projection = arrayOf<String?>(AccountEntry.COLUMN_UID)
        val columnIndexUID = 0
        val where = AccountEntry.COLUMN_PARENT_ACCOUNT_UID + "=?"
        val whereArgs = arrayOf<String?>(accountUID)
        db.query(
            tableName,
            projection,
            where,
            whereArgs,
            null,
            null,
            AccountEntry.COLUMN_ID
        ).forEach { cursor ->
            accounts.add(cursor.getString(columnIndexUID))
        }
        return accounts
    }

    /**
     * Returns a cursor to the dataset containing sub-accounts of the account with record ID `accountUID`
     *
     * @param accountUID           GUID of the parent account
     * @param isShowHiddenAccounts Show hidden accounts?
     * @return [Cursor] to the sub accounts data set
     */
    fun fetchSubAccounts(accountUID: String?, isShowHiddenAccounts: Boolean): Cursor? {
        Timber.v("Fetching sub accounts for account id %s", accountUID)
        var selection = AccountEntry.COLUMN_PARENT_ACCOUNT_UID + " = ?"
        if (!isShowHiddenAccounts) {
            selection += " AND " + AccountEntry.COLUMN_HIDDEN + " = 0"
        }
        val selectionArgs = arrayOf<String?>(accountUID)
        return fetchAccounts(selection, selectionArgs, null)
    }

    /**
     * Returns the top level accounts i.e. accounts with no parent or with the GnuCash ROOT account as parent
     *
     * @return Cursor to the top level accounts
     */
    fun fetchTopLevelAccounts(filterName: String?, isShowHiddenAccounts: Boolean): Cursor? {
        //condition which selects accounts with no parent, whose UID is not ROOT and whose type is not ROOT
        val selectionArgs: Array<String?>
        var selection = AccountEntry.COLUMN_TYPE + " != ?"
        if (!isShowHiddenAccounts) {
            selection += " AND " + AccountEntry.COLUMN_HIDDEN + " = 0"
        }
        if (filterName.isNullOrEmpty()) {
            selection += (" AND (" + AccountEntry.COLUMN_PARENT_ACCOUNT_UID + " IS NULL OR "
                    + AccountEntry.COLUMN_PARENT_ACCOUNT_UID + " = ?)")
            selectionArgs = arrayOf<String?>(
                AccountType.ROOT.name,
                rootAccountUID
            )
        } else {
            selection += " AND (" + AccountEntry.COLUMN_NAME + " LIKE " + sqlEscapeLike(filterName) + ")"
            selectionArgs = arrayOf<String?>(AccountType.ROOT.name)
        }
        return fetchAccounts(selection, selectionArgs, null)
    }

    /**
     * Returns a cursor to accounts which have recently had transactions added to them
     *
     * @return Cursor to recently used accounts
     */
    fun fetchRecentAccounts(
        numberOfRecent: Int,
        filterName: String?,
        isShowHiddenAccounts: Boolean
    ): Cursor? {
        var selection = ""
        if (!isShowHiddenAccounts) {
            selection = AccountEntry.TABLE_NAME + "." + AccountEntry.COLUMN_HIDDEN + " = 0"
        }
        if (!filterName.isNullOrEmpty()) {
            if (!selection.isEmpty()) {
                selection += " AND "
            }
            selection += "(" + AccountEntry.TABLE_NAME + "." + AccountEntry.COLUMN_NAME + " LIKE " + sqlEscapeLike(
                filterName
            ) + ")"
        }
        return db.query(
            (TransactionEntry.TABLE_NAME
                    + " LEFT OUTER JOIN " + SplitEntry.TABLE_NAME + " ON "
                    + TransactionEntry.TABLE_NAME + "." + TransactionEntry.COLUMN_UID + " = "
                    + SplitEntry.TABLE_NAME + "." + SplitEntry.COLUMN_TRANSACTION_UID
                    + ", " + AccountEntry.TABLE_NAME + " ON " + SplitEntry.TABLE_NAME + "." + SplitEntry.COLUMN_ACCOUNT_UID
                    + " = " + AccountEntry.TABLE_NAME + "." + AccountEntry.COLUMN_UID),
            arrayOf<String?>(AccountEntry.TABLE_NAME + ".*"),
            selection,
            null,
            SplitEntry.TABLE_NAME + "." + SplitEntry.COLUMN_ACCOUNT_UID,  //groupby
            null,  //having
            "MAX ( " + TransactionEntry.TABLE_NAME + "." + TransactionEntry.COLUMN_TIMESTAMP + " ) DESC",  // order
            numberOfRecent.toString() // limit;
        )
    }

    /**
     * Fetches favorite accounts from the database
     *
     * @return Cursor holding set of favorite accounts
     */
    fun fetchFavoriteAccounts(filterName: String?, isShowHiddenAccounts: Boolean): Cursor? {
        Timber.v("Fetching favorite accounts from db")
        var selection = AccountEntry.COLUMN_FAVORITE + " = 1"
        if (!isShowHiddenAccounts) {
            selection += " AND " + AccountEntry.COLUMN_HIDDEN + " = 0"
        }
        if (!filterName.isNullOrEmpty()) {
            selection += " AND (" + AccountEntry.COLUMN_NAME + " LIKE " + sqlEscapeLike(filterName) + ")"
        }
        return fetchAccounts(selection, null, null)
    }

    /**
     * Returns the GnuCash ROOT account UID if one exists (or creates one if necessary).
     *
     * In GnuCash desktop account structure, there is a root account (which is not visible in the UI) from which
     * other top level accounts derive. GnuCash Android also enforces a ROOT account now
     *
     * @return Unique ID of the GnuCash root account.
     */
    val rootAccountUID: String
        get() {
            var uid = rootUID
            if (uid != null) {
                return uid
            }
            val where = AccountEntry.COLUMN_TYPE + "=? AND " + AccountEntry.COLUMN_TEMPLATE + "=0"
            val whereArgs = arrayOf<String?>(AccountType.ROOT.name)
            val cursor = fetchAccounts(where, whereArgs, null)
            if (cursor != null) {
                try {
                    if (cursor.moveToFirst()) {
                        uid = cursor.getString(AccountEntry.COLUMN_UID)!!
                        rootUID = uid
                        return uid
                    }
                } finally {
                    cursor.close()
                }
            }
            // No ROOT exits, create a new one
            val commodity = commoditiesDbAdapter.defaultCommodity
            val rootAccount = Account(ROOT_ACCOUNT_NAME, commodity)
            rootAccount.accountType = AccountType.ROOT
            rootAccount.fullName = ROOT_ACCOUNT_FULL_NAME
            rootAccount.isHidden = false
            rootAccount.isPlaceholder = false
            uid = rootAccount.uid
            val contentValues = ContentValues()
            contentValues[AccountEntry.COLUMN_UID] = uid
            contentValues[AccountEntry.COLUMN_NAME] = rootAccount.name
            contentValues[AccountEntry.COLUMN_FULL_NAME] = rootAccount.fullName
            contentValues[AccountEntry.COLUMN_TYPE] = rootAccount.accountType.name
            contentValues[AccountEntry.COLUMN_HIDDEN] = rootAccount.isHidden
            contentValues[AccountEntry.COLUMN_CURRENCY] = rootAccount.commodity.currencyCode
            contentValues[AccountEntry.COLUMN_COMMODITY_UID] = rootAccount.commodity.uid
            contentValues[AccountEntry.COLUMN_PLACEHOLDER] = rootAccount.isPlaceholder
            Timber.i("Creating ROOT account")
            db.insert(tableName, null, contentValues)
            rootUID = uid
            return uid
        }

    /**
     * Returns the number of accounts for which the account with ID `accountUID` is a first level parent
     *
     * @param accountUID String Unique ID (GUID) of the account
     * @return Number of sub accounts
     */
    fun getSubAccountCount(accountUID: String?): Long {
        return DatabaseUtils.queryNumEntries(
            db,
            tableName,
            AccountEntry.COLUMN_PARENT_ACCOUNT_UID + " = ?",
            arrayOf<String?>(accountUID)
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
    fun getCommodity(accountUID: String): Commodity {
        val account = getRecordOrNull(accountUID)
        if (account != null) return account.commodity
        throw IllegalArgumentException("Account not found")
    }

    /**
     * Returns the simple name of the account with unique ID `accountUID`.
     *
     * @param accountUID Unique identifier of the account
     * @return Name of the account as String
     * @throws IllegalArgumentException if accountUID not found
     * @see .getFullyQualifiedAccountName
     */
    fun getAccountName(accountUID: String): String {
        val account = getRecordOrNull(accountUID)
        if (account != null) return account.name
        return getAttribute(accountUID, AccountEntry.COLUMN_NAME)
    }

    /**
     * Returns the default transfer account record ID for the account with UID `accountUID`
     *
     * @param accountID Database ID of the account record
     * @return Record ID of default transfer account
     */
    fun getDefaultTransferAccountID(accountID: Long): Long {
        if (isCached) {
            for (account in cache.values) {
                if (account.id == accountID) {
                    val uid = account.defaultTransferAccountUID
                    return if (uid.isNullOrEmpty()) 0 else getID(uid)
                }
            }
        }
        val cursor = db.query(
            tableName,
            arrayOf<String?>(AccountEntry.COLUMN_DEFAULT_TRANSFER_ACCOUNT_UID),
            AccountEntry.COLUMN_ID + " = " + accountID,
            null, null, null, null
        )
        try {
            if (cursor.moveToFirst()) {
                val uid = cursor.getString(AccountEntry.COLUMN_DEFAULT_TRANSFER_ACCOUNT_UID)
                return if (uid.isNullOrEmpty()) 0 else getID(uid)
            }
        } finally {
            cursor.close()
        }
        return 0
    }

    /**
     * Returns the full account name including the account hierarchy (parent accounts)
     *
     * @param accountUID Unique ID of account
     * @return Fully qualified (with parent hierarchy) account name
     */
    fun getFullyQualifiedAccountName(accountUID: String): String? {
        val accountName = getAccountName(accountUID)
        val parentAccountUID = getParentAccountUID(accountUID)

        if (parentAccountUID == null || parentAccountUID == accountUID || parentAccountUID == rootAccountUID) {
            return accountName
        }

        val parentAccountName = getFullyQualifiedAccountName(parentAccountUID)

        return parentAccountName + ACCOUNT_NAME_SEPARATOR + accountName
    }

    /**
     * Returns the full account name including the account hierarchy (parent accounts)
     *
     * @param account The account
     * @return Fully qualified (with parent hierarchy) account name
     */
    fun getFullyQualifiedAccountName(account: Account): String {
        val accountName = account.name
        val parentAccountUID = account.parentUID

        if (parentAccountUID.isNullOrEmpty() || parentAccountUID == rootAccountUID) {
            return accountName
        }

        val parentAccountName = getFullyQualifiedAccountName(parentAccountUID)

        return parentAccountName + ACCOUNT_NAME_SEPARATOR + accountName
    }

    /**
     * get account's full name directly from DB
     *
     * @param accountUID the account to retrieve full name
     * @return full name registered in DB
     */
    @Throws(IllegalArgumentException::class)
    fun getAccountFullName(accountUID: String): String? {
        val account = getRecordOrNull(accountUID)
        if (account != null) return account.fullName
        throw IllegalArgumentException("Account not found")
    }


    /**
     * Returns `true` if the account with unique ID `accountUID` is a placeholder account.
     *
     * @param accountUID Unique identifier of the account
     * @return `true` if the account is a placeholder account, `false` otherwise
     */
    fun isPlaceholderAccount(accountUID: String): Boolean {
        val account = getRecordOrNull(accountUID)
        if (account != null) return account.isPlaceholder
        val isPlaceholder = getAttribute(accountUID, AccountEntry.COLUMN_PLACEHOLDER)
        return isPlaceholder.toInt() != 0
    }

    /**
     * Convenience method, resolves the account unique ID and calls [.isPlaceholderAccount]
     *
     * @param accountUID GUID of the account
     * @return `true` if the account is hidden, `false` otherwise
     */
    fun isHiddenAccount(accountUID: String): Boolean {
        val account = getRecordOrNull(accountUID)
        if (account != null) return account.isHidden
        val isHidden = getAttribute(accountUID, AccountEntry.COLUMN_HIDDEN)
        return isHidden.toInt() != 0
    }

    /**
     * Returns true if the account is a favorite account, false otherwise
     *
     * @param accountUID GUID of the account
     * @return `true` if the account is a favorite account, `false` otherwise
     */
    fun isFavoriteAccount(accountUID: String): Boolean {
        val account = getRecordOrNull(accountUID)
        if (account != null) return account.isFavorite
        val isFavorite = getAttribute(accountUID, AccountEntry.COLUMN_FAVORITE)
        return isFavorite.toInt() != 0
    }

    /**
     * Updates all opening balances to the current account balances
     */
    val allOpeningBalanceTransactions: List<Transaction>
        get() {
            val accounts =
                this.simpleAccounts
            val openingTransactions = mutableListOf<Transaction>()
            for (account in accounts) {
                val balance = getAccountBalance(account, ALWAYS, ALWAYS, false)
                if (balance.isAmountZero) continue

                val transaction =
                    Transaction(appContext.getString(R.string.account_name_opening_balances))
                transaction.note = account.name
                transaction.commodity = account.commodity
                val transactionType = getTypeForBalance(account.accountType, balance.isNegative)
                val split = Split(balance, account)
                split.type = transactionType
                transaction.addSplit(split)
                transaction.addSplit(split.createPair(getOrCreateOpeningBalanceAccountUID))
                transaction.isExported = true
                openingTransactions.add(transaction)
            }
            return openingTransactions
        }

    /**
     * Returns the account color for the account as an Android resource ID.
     *
     *
     * Basically, if we are in a top level account, use the default title color.
     * but propagate a parent account's title color to children who don't have own color
     *
     *
     * @param context    the context
     * @param accountUID GUID of the account
     * @return Android resource ID representing the color which can be directly set to a view
     */
    @ColorInt
    fun getActiveAccountColor(context: Context, accountUID: String?): Int {
        var accountUID = accountUID
        while (!accountUID.isNullOrEmpty()) {
            val color = getAccountColor(accountUID)
            if (color != Account.DEFAULT_COLOR) {
                return color
            }
            accountUID = getParentAccountUID(accountUID)
        }

        return ContextCompat.getColor(context, R.color.theme_primary)
    }

    /**
     * Returns the list of commodities in use in the database.
     *
     *
     * This is not the same as the list of all available commodities.
     *
     * @return List of commodities in use
     */
    val commoditiesInUse: List<Commodity>
        get() {
            return allRecords.filter { !it.isTemplate }
                .map { it.commodity }
                .distinctBy { it.uid }
                .sortedBy { it.id }
        }

    val commoditiesInUseCount: Long
        get() {
            val sql = ("SELECT COUNT(DISTINCT " + AccountEntry.COLUMN_COMMODITY_UID + ")"
                    + " FROM " + tableName + " a"
                    + ", " + CommodityEntry.TABLE_NAME + " c"
                    + " WHERE a." + AccountEntry.COLUMN_COMMODITY_UID + " = c." + CommodityEntry.COLUMN_UID
                    + " AND c." + CommodityEntry.COLUMN_NAMESPACE + " != ?")
            val sqlArgs = arrayOf<String?>(Commodity.TEMPLATE)
            return DatabaseUtils.longForQuery(db, sql, sqlArgs)
        }

    /**
     * Deletes all accounts, transactions (and their splits) from the database.
     * Basically empties all 3 tables, so use with care ;)
     */
    override fun deleteAllRecords(): Int {
        // Relies "ON DELETE CASCADE" takes too much time
        // It take more than 300s to complete the deletion on my dataset without
        // clearing the split table first, but only needs a little more that 1s
        // if the split table is cleared first.
        db.delete(PriceEntry.TABLE_NAME, null, null)
        db.delete(SplitEntry.TABLE_NAME, null, null)
        db.delete(TransactionEntry.TABLE_NAME, null, null)
        db.delete(ScheduledActionEntry.TABLE_NAME, null, null)
        db.delete(BudgetAmountEntry.TABLE_NAME, null, null)
        db.delete(BudgetEntry.TABLE_NAME, null, null)
        db.delete(RecurrenceEntry.TABLE_NAME, null, null)
        rootUID = null

        return super.deleteAllRecords()
    }

    @Throws(SQLException::class)
    override fun deleteRecord(uid: String): Boolean {
        val result = super.deleteRecord(uid)
        if (result) {
            if (uid == rootUID) rootUID = null
            val contentValues = ContentValues()
            contentValues.putNull(AccountEntry.COLUMN_DEFAULT_TRANSFER_ACCOUNT_UID)
            db.update(
                tableName, contentValues,
                AccountEntry.COLUMN_DEFAULT_TRANSFER_ACCOUNT_UID + "=?",
                arrayOf<String?>(uid)
            )

            if (isCached) {
                for (account in cache.values) {
                    if (uid == account.defaultTransferAccountUID) {
                        account.defaultTransferAccountUID = null
                    }
                    if (uid == account.parentUID) {
                        account.parentUID = rootAccountUID
                    }
                }
            }
        }
        return result
    }

    fun getTransactionMaxSplitNum(accountUID: String): Int {
        val cursor = db.query(
            "trans_extra_info",
            arrayOf<String?>("MAX(trans_split_count)"),
            "trans_acct_t_uid IN ( SELECT DISTINCT " + TransactionEntry.TABLE_NAME + "_" + TransactionEntry.COLUMN_UID +
                    " FROM trans_split_acct WHERE " + AccountEntry.TABLE_NAME + "_" + AccountEntry.COLUMN_UID +
                    " = ? )",
            arrayOf<String?>(accountUID),
            null,
            null,
            null
        )
        try {
            return if (cursor.moveToFirst()) {
                cursor.getLong(0).toInt()
            } else {
                0
            }
        } finally {
            cursor.close()
        }
    }

    fun getFullRecord(uid: String?): Account? {
        if (uid.isNullOrEmpty()) return null
        Timber.v("Fetching full account %s", uid)
        val cursor = fetchRecord(uid) ?: return null
        try {
            if (cursor.moveToFirst()) {
                return buildFullModelInstance(cursor)
            }
        } finally {
            cursor.close()
        }
        return null
    }

    fun getTransactionCount(uid: String): Long {
        return transactionsDbAdapter.getTransactionsCountForAccount(uid)
    }

    /**
     * Returns the [AccountType] of the account with unique ID `uid`
     *
     * @param accountUID Unique ID of the account
     * @return [AccountType] of the account.
     * @throws IllegalArgumentException if accountUID does not exist in DB,
     */
    @Throws(IllegalArgumentException::class)
    fun getAccountType(accountUID: String): AccountType {
        val account = getRecordOrNull(accountUID)
        if (account != null) return account.accountType
        throw IllegalArgumentException("Account not found")
    }

    override val allRecords: List<Account>
        get() {
            val where = (AccountEntry.COLUMN_TYPE + " != ?"
                    + " AND " + AccountEntry.COLUMN_TEMPLATE + " = 0")
            val whereArgs = arrayOf<String?>(AccountType.ROOT.name)
            return getAllRecords(where, whereArgs)
        }

    fun getAccountsBalances(
        accounts: List<Account>,
        startTime: Long,
        endTime: Long
    ): Map<String, Money> {
        val splitsDbAdapter = transactionsDbAdapter.splitsDbAdapter
        val balances = splitsDbAdapter.computeSplitBalances(accounts, startTime, endTime)
            .toMutableMap()
        for (account in accounts) {
            val balance = balances[account.uid] ?: continue
            if (!account.accountType.hasDebitNormalBalance) {
                balances[account.uid] = -balance
            }
        }
        return balances
    }

    fun getDescendants(account: Account): List<Account> {
        return getDescendants(account.uid)
    }

    fun getDescendants(accountUID: String): List<Account> {
        val result = mutableListOf<Account>()
        populateDescendants(accountUID, result)
        return result
    }

    private fun populateDescendants(accountUID: String, result: MutableList<Account>) {
        val descendantsUIDs = getDescendantAccountUIDs(accountUID, null, null)
        for (descendantsUID in descendantsUIDs) {
            getRecordOrNull(descendantsUID)?.let { result.add(it) }
        }
    }

    companion object {
        /**
         * Separator used for account name hierarchies between parent and child accounts
         */
        const val ACCOUNT_NAME_SEPARATOR: String = ":"

        /**
         * ROOT account full name.
         * should ensure the ROOT account's full name will always sort before any other
         * account's full name.
         */
        const val ROOT_ACCOUNT_FULL_NAME: String = " "
        const val ROOT_ACCOUNT_NAME: String = "Root Account"
        const val TEMPLATE_ACCOUNT_NAME: String = "Template Root"

        const val ALWAYS: Long = -1L

        /**
         * Returns an application-wide instance of this database adapter
         *
         * @return Instance of Accounts db adapter
         */
        val instance: AccountsDbAdapter get() = GnuCashApplication.accountsDbAdapter!!

        fun getImbalanceAccountPrefix(context: Context): String {
            return context.getString(R.string.imbalance_account_name) + "-"
        }

        /**
         * Returns the imbalance account where to store transactions which are not double entry.
         *
         * @param commodity Commodity of the transaction
         * @return Imbalance account name
         */
        fun getImbalanceAccountName(context: Context, commodity: Commodity): String {
            return getImbalanceAccountPrefix(context) + commodity.currencyCode
        }

        /**
         * Get the name of the default account for opening balances for the current locale.
         * For the English locale, it will be "Equity:Opening Balances"
         *
         * @return Fully qualified account name of the opening balances account
         */
        val openingBalanceAccountFullName: String?
            get() {
                val context = appContext
                val parentEquity = context.getString(R.string.account_name_equity).trim()
                //German locale has no parent Equity account
                return if (parentEquity.isNotEmpty()) {
                    (parentEquity + ACCOUNT_NAME_SEPARATOR
                            + context.getString(R.string.account_name_opening_balances))
                } else {
                    context.getString(R.string.account_name_opening_balances)
                }
            }
    }
}
