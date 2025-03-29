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

import static org.gnucash.android.db.DatabaseExtKt.getBigDecimal;
import static org.gnucash.android.db.DatabaseHelper.escapeForLike;
import static org.gnucash.android.db.DatabaseSchema.AccountEntry;
import static org.gnucash.android.db.DatabaseSchema.BudgetAmountEntry;
import static org.gnucash.android.db.DatabaseSchema.BudgetEntry;
import static org.gnucash.android.db.DatabaseSchema.PriceEntry;
import static org.gnucash.android.db.DatabaseSchema.RecurrenceEntry;
import static org.gnucash.android.db.DatabaseSchema.ScheduledActionEntry;
import static org.gnucash.android.db.DatabaseSchema.SplitEntry;
import static org.gnucash.android.db.DatabaseSchema.TransactionEntry;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;
import android.text.TextUtils;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import org.gnucash.android.R;
import org.gnucash.android.app.GnuCashApplication;
import org.gnucash.android.model.Account;
import org.gnucash.android.model.AccountType;
import org.gnucash.android.model.Commodity;
import org.gnucash.android.model.Money;
import org.gnucash.android.model.Split;
import org.gnucash.android.model.Transaction;
import org.gnucash.android.model.TransactionType;
import org.gnucash.android.util.TimestampHelper;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import timber.log.Timber;

/**
 * Manages persistence of {@link Account}s in the database
 * Handles adding, modifying and deleting of account records.
 *
 * @author Ngewi Fet <ngewif@gmail.com>
 * @author Yongxin Wang <fefe.wyx@gmail.com>
 * @author Oleksandr Tyshkovets <olexandr.tyshkovets@gmail.com>
 */
public class AccountsDbAdapter extends DatabaseAdapter<Account> {
    /**
     * Separator used for account name hierarchies between parent and child accounts
     */
    public static final String ACCOUNT_NAME_SEPARATOR = ":";

    /**
     * ROOT account full name.
     * should ensure the ROOT account's full name will always sort before any other
     * account's full name.
     */
    public static final String ROOT_ACCOUNT_FULL_NAME = " ";
    public static final String ROOT_ACCOUNT_NAME = "Root Account";

    /**
     * Transactions database adapter for manipulating transactions associated with accounts
     */
    @NonNull
    public final TransactionsDbAdapter transactionsDbAdapter;

    /**
     * Commodities database adapter for commodity manipulation
     */
    @NonNull
    public final CommoditiesDbAdapter commoditiesDbAdapter;

    @Nullable
    private String rootUID = null;

    /**
     * Overloaded constructor. Creates an adapter for an already open database
     */
    public AccountsDbAdapter(@NonNull TransactionsDbAdapter transactionsDbAdapter) {
        super(transactionsDbAdapter.mDb, AccountEntry.TABLE_NAME, new String[]{
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
            AccountEntry.COLUMN_NOTES
        }, true);
        this.transactionsDbAdapter = transactionsDbAdapter;
        this.commoditiesDbAdapter = transactionsDbAdapter.commoditiesDbAdapter;
    }

    /**
     * Convenience overloaded constructor.
     * This is used when an AccountsDbAdapter object is needed quickly. Otherwise, the other
     * constructor {@link #AccountsDbAdapter(TransactionsDbAdapter)}
     * should be used whenever possible
     *
     * @param db Database to create an adapter for
     */
    public AccountsDbAdapter(@NonNull SQLiteDatabase db) {
        this(new TransactionsDbAdapter(db));
    }

    /**
     * Returns an application-wide instance of this database adapter
     *
     * @return Instance of Accounts db adapter
     */
    public static AccountsDbAdapter getInstance() {
        return GnuCashApplication.getAccountsDbAdapter();
    }

    @Override
    public void close() throws IOException {
        commoditiesDbAdapter.close();
        transactionsDbAdapter.close();
        super.close();
    }

    /**
     * Adds an account to the database.
     * If an account already exists in the database with the same GUID, it is replaced.
     *
     * @param account {@link Account} to be inserted to database
     */
    @Override
    public void addRecord(@NonNull Account account, UpdateMethod updateMethod) throws SQLException {
        Timber.d("Replace account to db");
        if (account.getAccountType() == AccountType.ROOT) {
            rootUID = account.getUID();
        }
        //in-case the account already existed, we want to update the templates based on it as well
        super.addRecord(account, updateMethod);
        //now add transactions if there are any
        // NB! Beware of transactions that reference accounts not yet in the db,
        if (account.getAccountType() != AccountType.ROOT) {
            for (Transaction t : account.getTransactions()) {
                t.setCommodity(account.getCommodity());
                transactionsDbAdapter.addRecord(t, updateMethod);
            }
            List<Transaction> scheduledTransactions = transactionsDbAdapter.getScheduledTransactionsForAccount(account.getUID());
            for (Transaction transaction : scheduledTransactions) {
                transactionsDbAdapter.addRecord(transaction, UpdateMethod.update);
            }
        }
    }

    /**
     * Adds some accounts and their transactions to the database in bulk.
     * <p>If an account already exists in the database with the same GUID, it is replaced.
     * This function will NOT try to determine the full name
     * of the accounts inserted, full names should be generated prior to the insert.
     * <br>All or none of the accounts will be inserted;</p>
     *
     * @param accountList {@link Account} to be inserted to database
     * @return number of rows inserted
     */
    @Override
    public long bulkAddRecords(@NonNull List<Account> accountList, UpdateMethod updateMethod) {
        //scheduled transactions are not fetched from the database when getting account transactions
        //so we retrieve those which affect this account and then re-save them later
        //this is necessary because the database has ON DELETE CASCADE between accounts and splits
        //and all accounts are editing via SQL REPLACE

        //// TODO: 20.04.2016 Investigate if we can safely remove updating the transactions when bulk updating accounts
        List<Transaction> transactionList = new ArrayList<>(accountList.size() * 2);
        for (Account account : accountList) {
            transactionList.addAll(account.getTransactions());
            transactionList.addAll(transactionsDbAdapter.getScheduledTransactionsForAccount(account.getUID()));
        }
        long nRow = super.bulkAddRecords(accountList, updateMethod);

        if (nRow > 0 && !transactionList.isEmpty()) {
            transactionsDbAdapter.bulkAddRecords(transactionList, updateMethod);
        }
        return nRow;
    }

    @Override
    protected @NonNull SQLiteStatement bind(@NonNull SQLiteStatement stmt, @NonNull final Account account) throws SQLException {
        String parentAccountUID = account.getParentUID();
        if (account.getAccountType() != AccountType.ROOT) {
            if (TextUtils.isEmpty(parentAccountUID)) {
                parentAccountUID = getOrCreateGnuCashRootAccountUID();
                account.setParentUID(parentAccountUID);
            }
            //update the fully qualified account name
            account.setFullName(getFullyQualifiedAccountName(account));
        }

        bindBaseModel(stmt, account);
        stmt.bindString(1, account.getName());
        stmt.bindString(2, account.getDescription());
        stmt.bindString(3, account.getAccountType().name());
        stmt.bindString(4, account.getCommodity().getCurrencyCode());
        if (account.getColor() != Account.DEFAULT_COLOR) {
            stmt.bindString(5, account.getColorHexString());
        }
        stmt.bindLong(6, account.isFavorite() ? 1 : 0);
        stmt.bindString(7, account.getFullName());
        stmt.bindLong(8, account.isPlaceholder() ? 1 : 0);
        stmt.bindString(9, TimestampHelper.getUtcStringFromTimestamp(account.getCreatedTimestamp()));
        stmt.bindLong(10, account.isHidden() ? 1 : 0);
        stmt.bindString(11, account.getCommodity().getUID());
        if (parentAccountUID != null) {
            stmt.bindString(12, parentAccountUID);
        }
        if (account.getDefaultTransferAccountUID() != null) {
            stmt.bindString(13, account.getDefaultTransferAccountUID());
        }
        if (account.getNote() != null) {
            stmt.bindString(14, account.getNote());
        }

        return stmt;
    }

    /**
     * Marks all transactions for a given account as exported
     *
     * @param accountUID Unique ID of the record to be marked as exported
     * @return Number of records marked as exported
     */
    public int markAsExported(String accountUID) {
        if (isCached) cache.clear();
        ContentValues contentValues = new ContentValues();
        contentValues.put(TransactionEntry.COLUMN_EXPORTED, 1);
        return mDb.update(
            TransactionEntry.TABLE_NAME,
            contentValues,
            TransactionEntry.COLUMN_UID + " IN ( " +
                "SELECT DISTINCT " + TransactionEntry.TABLE_NAME + "." + TransactionEntry.COLUMN_UID +
                " FROM " + TransactionEntry.TABLE_NAME + " , " + SplitEntry.TABLE_NAME + " ON " +
                TransactionEntry.TABLE_NAME + "." + TransactionEntry.COLUMN_UID + " = " +
                SplitEntry.TABLE_NAME + "." + SplitEntry.COLUMN_TRANSACTION_UID + " , " +
                AccountEntry.TABLE_NAME + " ON " + SplitEntry.TABLE_NAME + "." +
                SplitEntry.COLUMN_ACCOUNT_UID + " = " + AccountEntry.TABLE_NAME + "." +
                AccountEntry.COLUMN_UID + " WHERE " + AccountEntry.TABLE_NAME + "." +
                AccountEntry.COLUMN_UID + " = ? "
                + " ) ",
            new String[]{accountUID}
        );
    }

    /**
     * This feature goes through all the rows in the accounts and changes value for <code>columnKey</code> to <code>newValue</code><br/>
     * The <code>newValue</code> parameter is taken as string since SQLite typically stores everything as text.
     * <p><b>This method affects all rows, exercise caution when using it</b></p>
     *
     * @param columnKey Name of column to be updated
     * @param newValue  New value to be assigned to the columnKey
     * @return Number of records affected
     */
    public int updateAllAccounts(String columnKey, String newValue) {
        if (isCached) cache.clear();
        ContentValues contentValues = new ContentValues();
        if (newValue == null) {
            contentValues.putNull(columnKey);
        } else {
            contentValues.put(columnKey, newValue);
        }
        return mDb.update(AccountEntry.TABLE_NAME, contentValues, null, null);
    }

    /**
     * Updates a specific entry of an account
     *
     * @param accountId Database record ID of the account to be updated
     * @param columnKey Name of column to be updated
     * @param newValue  New value to be assigned to the columnKey
     * @return Number of records affected
     */
    public int updateAccount(long accountId, String columnKey, String newValue) {
        return updateRecord(mTableName, accountId, columnKey, newValue);
    }

    /**
     * This method goes through all the children of {@code accountUID} and updates the parent account
     * to {@code newParentAccountUID}. The fully qualified account names for all descendant accounts will also be updated.
     *
     * @param parentAccountUID    GUID of the account
     * @param newParentAccountUID GUID of the new parent account
     */
    public void reassignDescendantAccounts(@NonNull String parentAccountUID, @NonNull String newParentAccountUID) {
        if (isCached) cache.clear();
        List<String> descendantAccountUIDs = getDescendantAccountUIDs(parentAccountUID, null, null);
        if (descendantAccountUIDs.isEmpty()) return;
        List<Account> descendantAccounts = getSimpleAccountList(
            AccountEntry.COLUMN_UID + " IN ('" + TextUtils.join("','", descendantAccountUIDs) + "')",
            null,
            null
        );
        Map<String, Account> accountsByUID = new HashMap<>();
        for (Account account : descendantAccounts) {
            accountsByUID.put(account.getUID(), account);
        }
        String parentAccountFullName;
        if (getAccountType(newParentAccountUID) == AccountType.ROOT) {
            parentAccountFullName = "";
        } else {
            parentAccountFullName = getAccountFullName(newParentAccountUID);
        }
        ContentValues contentValues = new ContentValues();
        for (Account account : descendantAccounts) {
            contentValues.clear();

            if (parentAccountUID.equals(account.getParentUID())) {
                // direct descendant
                account.setParentUID(newParentAccountUID);
                if (TextUtils.isEmpty(parentAccountFullName)) {
                    account.setFullName(account.getName());
                } else {
                    account.setFullName(parentAccountFullName + ACCOUNT_NAME_SEPARATOR + account.getName());
                }
                contentValues.put(AccountEntry.COLUMN_PARENT_ACCOUNT_UID, newParentAccountUID);
            } else {
                // indirect descendant
                Account parentAccount = accountsByUID.get(account.getParentUID());
                account.setFullName(parentAccount.getFullName() + ACCOUNT_NAME_SEPARATOR + account.getName());
            }
            // update DB
            contentValues.put(AccountEntry.COLUMN_FULL_NAME, account.getFullName());
            mDb.update(
                mTableName,
                contentValues,
                AccountEntry.COLUMN_UID + " = ?",
                new String[]{account.getUID()}
            );
        }
    }

    /**
     * Deletes an account and its transactions, and all its sub-accounts and their transactions.
     * <p>Not only the splits belonging to the account and its descendants will be deleted, rather,
     * the complete transactions associated with this account and its descendants
     * (i.e. as long as the transaction has at least one split belonging to one of the accounts).
     * This prevents an split imbalance from being caused.</p>
     * <p>If you want to preserve transactions, make sure to first reassign the children accounts (see {@link #reassignDescendantAccounts(String, String)}
     * before calling this method. This method will however not delete a root account. </p>
     * <p><b>This method does a thorough delete, use with caution!!!</b></p>
     *
     * @param accountUID Database UID of account
     * @return <code>true</code> if the account and sub-accounts were all successfully deleted, <code>false</code> if
     * even one was not deleted
     * @see #reassignDescendantAccounts(String, String)
     */
    public boolean recursiveDeleteAccount(String accountUID) {
        if (getAccountType(accountUID) == AccountType.ROOT) {
            // refuse to delete ROOT
            return false;
        }

        Timber.d("Delete account with rowId with its transactions and sub-accounts: %s", accountUID);
        if (isCached) cache.clear();

        List<String> descendantAccountUIDs = getDescendantAccountUIDs(accountUID, null, null);
        try {
            beginTransaction();
            descendantAccountUIDs.add(accountUID); //add account to descendants list just for convenience
            for (String descendantAccountUID : descendantAccountUIDs) {
                transactionsDbAdapter.deleteTransactionsForAccount(descendantAccountUID);
            }

            String accountUIDList = "'" + TextUtils.join("','", descendantAccountUIDs) + "'";

            // delete accounts
            long deletedCount = mDb.delete(
                mTableName,
                AccountEntry.COLUMN_UID + " IN (" + accountUIDList + ")",
                null
            );

            //if we delete some accounts, reset the default transfer account to NULL
            //there is also a database trigger from db version > 12
            if (deletedCount > 0) {
                ContentValues contentValues = new ContentValues();
                contentValues.putNull(AccountEntry.COLUMN_DEFAULT_TRANSFER_ACCOUNT_UID);
                mDb.update(mTableName, contentValues,
                    AccountEntry.COLUMN_DEFAULT_TRANSFER_ACCOUNT_UID + " IN (" + accountUIDList + ")",
                    null);
            }

            setTransactionSuccessful();
            return true;
        } finally {
            endTransaction();
        }
    }

    /**
     * Builds an account instance with the provided cursor and loads its corresponding transactions.
     *
     * @param c Cursor pointing to account record in database
     * @return {@link Account} object constructed from database record
     */
    @Override
    public Account buildModelInstance(@NonNull final Cursor c) {
        Account account = buildSimpleAccountInstance(c);
        account.setTransactions(transactionsDbAdapter.getAllTransactionsForAccount(account.getUID()));
        return account;
    }

    /**
     * Builds an account instance with the provided cursor and loads its corresponding transactions.
     * <p>The method will not move the cursor position, so the cursor should already be pointing
     * to the account record in the database<br/>
     * <b>Note</b> Unlike {@link  #buildModelInstance(android.database.Cursor)} this method will not load transactions</p>
     *
     * @param c Cursor pointing to account record in database
     * @return {@link Account} object constructed from database record
     */
    public Account buildSimpleAccountInstance(Cursor c) {
        Account account = new Account(c.getString(c.getColumnIndexOrThrow(AccountEntry.COLUMN_NAME)));
        populateBaseModelAttributes(c, account);

        account.setDescription(c.getString(c.getColumnIndexOrThrow(AccountEntry.COLUMN_DESCRIPTION)));
        account.setParentUID(c.getString(c.getColumnIndexOrThrow(AccountEntry.COLUMN_PARENT_ACCOUNT_UID)));
        account.setAccountType(AccountType.valueOf(c.getString(c.getColumnIndexOrThrow(AccountEntry.COLUMN_TYPE))));
        String commodityUID = c.getString(c.getColumnIndexOrThrow(AccountEntry.COLUMN_COMMODITY_UID));
        account.setCommodity(commoditiesDbAdapter.getRecord(commodityUID));
        account.setPlaceholder(c.getInt(c.getColumnIndexOrThrow(AccountEntry.COLUMN_PLACEHOLDER)) != 0);
        account.setDefaultTransferAccountUID(c.getString(c.getColumnIndexOrThrow(AccountEntry.COLUMN_DEFAULT_TRANSFER_ACCOUNT_UID)));
        String color = c.getString(c.getColumnIndexOrThrow(AccountEntry.COLUMN_COLOR_CODE));
        if (!TextUtils.isEmpty(color))
            account.setColor(color);
        account.setFavorite(c.getInt(c.getColumnIndexOrThrow(AccountEntry.COLUMN_FAVORITE)) != 0);
        account.setFullName(c.getString(c.getColumnIndexOrThrow(AccountEntry.COLUMN_FULL_NAME)));
        account.setHidden(c.getInt(c.getColumnIndexOrThrow(AccountEntry.COLUMN_HIDDEN)) != 0);
        account.setNote(c.getString(c.getColumnIndexOrThrow(AccountEntry.COLUMN_NOTES)));
        return account;
    }

    /**
     * Returns the  unique ID of the parent account of the account with unique ID <code>uid</code>
     * If the account has no parent, null is returned
     *
     * @param uid Unique Identifier of account whose parent is to be returned. Should not be null
     * @return DB record UID of the parent account, null if the account has no parent
     */
    public String getParentAccountUID(@NonNull String uid) {
        if (isCached) {
            Account account = cache.get(uid);
            if (account != null) return account.getParentUID();
        }
        Cursor cursor = mDb.query(
            mTableName,
            new String[]{AccountEntry.COLUMN_PARENT_ACCOUNT_UID},
            AccountEntry.COLUMN_UID + " = ?",
            new String[]{uid},
            null, null, null, null);
        try {
            if (cursor.moveToFirst()) {
                Timber.v("Found parent account UID, returning value");
                return cursor.getString(cursor.getColumnIndexOrThrow(AccountEntry.COLUMN_PARENT_ACCOUNT_UID));
            } else {
                return null;
            }
        } finally {
            cursor.close();
        }
    }

    /**
     * Returns the color code for the account in format #rrggbb
     *
     * @param accountUID UID of the account
     * @return String color code of account or null if none
     */
    public int getAccountColor(String accountUID) {
        Account account = getSimpleRecord(accountUID);
        return (account != null) ? account.getColor() : Account.DEFAULT_COLOR;
    }

    /**
     * Overloaded method. Resolves the account unique ID from the row ID and makes a call to {@link #getAccountType(String)}
     *
     * @param accountId Database row ID of the account
     * @return {@link AccountType} of the account
     */
    public AccountType getAccountType(long accountId) {
        return getAccountType(getUID(accountId));
    }

    /**
     * Returns a list of all account entries in the system (includes root account)
     * No transactions are loaded, just the accounts
     *
     * @return List of {@link Account}s in the database
     */
    public List<Account> getSimpleAccountList() {
        return getSimpleAccountList(null, null, AccountEntry.COLUMN_FULL_NAME + " ASC");
    }

    /**
     * Returns a list of all account entries in the system (includes root account)
     * No transactions are loaded, just the accounts
     *
     * @return List of {@link Account}s in the database
     */
    public List<Account> getSimpleAccountList(@Nullable String where, @Nullable String[] whereArgs, @Nullable String orderBy) {
        List<Account> accounts = new ArrayList<>();
        Cursor cursor = fetchAccounts(where, whereArgs, orderBy);
        if (cursor == null) return accounts;

        try {
            if (cursor.moveToFirst()) {
                do {
                    Account account = buildSimpleAccountInstance(cursor);
                    accounts.add(account);
                    if (isCached) {
                        cache.put(account.getUID(), account);
                    }
                } while (cursor.moveToNext());
            }
        } finally {
            cursor.close();
        }
        return accounts;
    }

    /**
     * Returns a list of accounts which have transactions that have not been exported yet
     *
     * @param lastExportTimeStamp Timestamp after which to any transactions created/modified should be exported
     * @return List of {@link Account}s with unexported transactions
     */
    public List<Account> getExportableAccounts(Timestamp lastExportTimeStamp) {
        Cursor cursor = mDb.query(
            TransactionEntry.TABLE_NAME + " , " + SplitEntry.TABLE_NAME +
                " ON " + TransactionEntry.TABLE_NAME + "." + TransactionEntry.COLUMN_UID + " = " +
                SplitEntry.TABLE_NAME + "." + SplitEntry.COLUMN_TRANSACTION_UID + " , " +
                AccountEntry.TABLE_NAME + " ON " + AccountEntry.TABLE_NAME + "." +
                AccountEntry.COLUMN_UID + " = " + SplitEntry.TABLE_NAME + "." +
                SplitEntry.COLUMN_ACCOUNT_UID,
            new String[]{AccountEntry.TABLE_NAME + ".*"},
            TransactionEntry.TABLE_NAME + "." + TransactionEntry.COLUMN_MODIFIED_AT + " > ?",
            new String[]{TimestampHelper.getUtcStringFromTimestamp(lastExportTimeStamp)},
            AccountEntry.TABLE_NAME + "." + AccountEntry.COLUMN_UID,
            null,
            null
        );
        return getRecords(cursor);
    }

    /**
     * Retrieves the unique ID of the imbalance account for a particular currency (creates the imbalance account
     * on demand if necessary)
     *
     * @param commodity Commodity for the imbalance account
     * @return String unique ID of the account
     */
    public String getOrCreateImbalanceAccountUID(@NonNull Context context, @NonNull Commodity commodity) {
        String imbalanceAccountName = getImbalanceAccountName(context, commodity);
        String uid = findAccountUidByFullName(imbalanceAccountName);
        if (uid == null) {
            Account account = new Account(imbalanceAccountName, commodity);
            account.setAccountType(AccountType.BANK);
            account.setParentUID(getOrCreateGnuCashRootAccountUID());
            account.setHidden(!GnuCashApplication.isDoubleEntryEnabled());
            addRecord(account, UpdateMethod.insert);
            uid = account.getUID();
        }
        return uid;
    }

    /**
     * Returns the GUID of the imbalance account for the commodity
     *
     * <p>This method will not create the imbalance account if it doesn't exist</p>
     *
     * @param commodity Commodity for the imbalance account
     * @return GUID of the account or null if the account doesn't exist yet
     * @see #getOrCreateImbalanceAccountUID(Context, Commodity)
     */
    public String getImbalanceAccountUID(@NonNull Context context, @NonNull Commodity commodity) {
        String imbalanceAccountName = getImbalanceAccountName(context, commodity);
        return findAccountUidByFullName(imbalanceAccountName);
    }

    /**
     * Creates the account with the specified name and returns its unique identifier.
     * <p>If a full hierarchical account name is provided, then the whole hierarchy is created and the
     * unique ID of the last account (at bottom) of the hierarchy is returned</p>
     *
     * @param fullName    Fully qualified name of the account
     * @param accountType Type to assign to all accounts created
     * @return String unique ID of the account at bottom of hierarchy
     */
    public String createAccountHierarchy(String fullName, AccountType accountType) {
        if (TextUtils.isEmpty(fullName)) {
            throw new IllegalArgumentException("fullName cannot be empty");
        }
        String[] tokens = fullName.trim().split(ACCOUNT_NAME_SEPARATOR);
        String uid = getOrCreateGnuCashRootAccountUID();
        String parentName = "";
        ArrayList<Account> accountsList = new ArrayList<>();
        Commodity commodity = commoditiesDbAdapter.getDefaultCommodity();
        for (String token : tokens) {
            parentName += token;
            String parentUID = findAccountUidByFullName(parentName);
            if (parentUID != null) { //the parent account exists, don't recreate
                uid = parentUID;
            } else {
                Account account = new Account(token, commodity);
                account.setAccountType(accountType);
                account.setParentUID(uid); //set its parent
                account.setFullName(parentName);
                accountsList.add(account);
                uid = account.getUID();
            }
            parentName += ACCOUNT_NAME_SEPARATOR;
        }
        if (accountsList.size() > 0) {
            bulkAddRecords(accountsList, UpdateMethod.insert);
        }
        // if fullName is not empty, loop will be entered and then uid will never be null
        return uid;
    }

    /**
     * Returns the unique ID of the opening balance account or creates one if necessary
     *
     * @return String unique ID of the opening balance account
     */
    public String getOrCreateOpeningBalanceAccountUID() {
        String openingBalanceAccountName = getOpeningBalanceAccountFullName();
        String uid = findAccountUidByFullName(openingBalanceAccountName);
        if (uid == null) {
            uid = createAccountHierarchy(openingBalanceAccountName, AccountType.EQUITY);
        }
        return uid;
    }

    /**
     * Finds an account unique ID by its full name
     *
     * @param fullName Fully qualified name of the account
     * @return String unique ID of the account or null if no match is found
     */
    public String findAccountUidByFullName(String fullName) {
        if (isCached) {
            for (Account account : cache.values()) {
                if (account.getFullName().equals(fullName)) return account.getFullName();
            }
        }
        Cursor c = mDb.query(AccountEntry.TABLE_NAME, new String[]{AccountEntry.COLUMN_UID},
            AccountEntry.COLUMN_FULL_NAME + "= ?", new String[]{fullName},
            null, null, null, "1");
        try {
            if (c.moveToNext()) {
                return c.getString(c.getColumnIndexOrThrow(AccountEntry.COLUMN_UID));
            } else {
                return null;
            }
        } finally {
            c.close();
        }
    }

    /**
     * Returns a cursor to all account records in the database.
     * GnuCash ROOT accounts and hidden accounts will <b>not</b> be included in the result set
     *
     * @return {@link Cursor} to all account records
     */
    @Override
    public Cursor fetchAllRecords() {
        Timber.v("Fetching all accounts from db");
        String selection = AccountEntry.COLUMN_HIDDEN + " = 0 AND " + AccountEntry.COLUMN_TYPE + " != ?";
        return fetchAccounts(selection, new String[]{AccountType.ROOT.name()}, AccountEntry.COLUMN_NAME + " ASC");
    }

    /**
     * Returns a cursor to all account records in the database ordered by full name.
     * GnuCash ROOT accounts and hidden accounts will not be included in the result set.
     *
     * @return {@link Cursor} to all account records
     */
    public Cursor fetchAllRecordsOrderedByFullName() {
        Timber.v("Fetching all accounts from db");
        String selection = AccountEntry.COLUMN_HIDDEN + " = 0 AND " + AccountEntry.COLUMN_TYPE + " != ?";
        return fetchAccounts(selection, new String[]{AccountType.ROOT.name()}, AccountEntry.COLUMN_FULL_NAME + " ASC");
    }

    /**
     * Returns a Cursor set of accounts which fulfill <code>where</code>
     * and ordered by <code>orderBy</code>
     *
     * @param where     SQL WHERE statement without the 'WHERE' itself
     * @param whereArgs args to where clause
     * @param orderBy   orderBy clause
     * @return Cursor set of accounts which fulfill <code>where</code>
     */
    public Cursor fetchAccounts(@Nullable String where, @Nullable String[] whereArgs, @Nullable String orderBy) {
        if (TextUtils.isEmpty(orderBy)) {
            orderBy = AccountEntry.COLUMN_NAME + " ASC";
        }
        Timber.v("Fetching all accounts from db where " + where + "/" + Arrays.toString(whereArgs) + " order by " + orderBy);

        return mDb.query(mTableName, null, where, whereArgs, null, null, orderBy);
    }

    /**
     * Returns a Cursor set of accounts which fulfill <code>where</code>
     * <p>This method returns the accounts list sorted by the full account name</p>
     *
     * @param where     SQL WHERE statement without the 'WHERE' itself
     * @param whereArgs where args
     * @return Cursor set of accounts which fulfill <code>where</code>
     */
    public Cursor fetchAccountsOrderedByFullName(String where, String[] whereArgs) {
        Timber.v("Fetching all accounts from db where %s", where);
        return fetchAccounts(where, whereArgs, AccountEntry.COLUMN_FULL_NAME + " ASC");
    }

    /**
     * Returns a Cursor set of accounts which fulfill <code>where</code>
     * <p>This method returns the favorite accounts first, sorted by name, and then the other accounts,
     * sorted by name.</p>
     *
     * @param where     SQL WHERE statement without the 'WHERE' itself
     * @param whereArgs where args
     * @return Cursor set of accounts which fulfill <code>where</code>
     */
    public Cursor fetchAccountsOrderedByFavoriteAndFullName(String where, String[] whereArgs) {
        Timber.v("Fetching all accounts from db where " + where + " order by Favorite then Name");
        return fetchAccounts(where, whereArgs, AccountEntry.COLUMN_FAVORITE + " DESC, " + AccountEntry.COLUMN_FULL_NAME + " ASC");
    }

    /**
     * Returns the balance of an account while taking sub-accounts into consideration
     *
     * @return Account Balance of an account including sub-accounts
     */
    public Money getAccountBalance(String accountUID) {
        return computeBalance(accountUID, -1, -1, true);
    }

    /**
     * Returns the current balance of an account while taking sub-accounts into consideration
     *
     * @return Account Balance of an account including sub-accounts
     */
    public Money getCurrentAccountBalance(String accountUID) {
        return getAccountBalance(accountUID, -1, System.currentTimeMillis());
    }

    /**
     * Returns the balance of an account within the specified time range while taking sub-accounts into consideration
     *
     * @param accountUID     the account's UUID
     * @param startTimestamp the start timestamp of the time range
     * @param endTimestamp   the end timestamp of the time range
     * @return the balance of an account within the specified range including sub-accounts
     */
    public Money getAccountBalance(String accountUID, long startTimestamp, long endTimestamp) {
        return getAccountBalance(accountUID, startTimestamp, endTimestamp, true);
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
    public Money getAccountBalance(String accountUID, long startTimestamp, long endTimestamp, boolean includeSubAccounts) {
        return computeBalance(accountUID, startTimestamp, endTimestamp, includeSubAccounts);
    }

    /**
     * Compute the account balance for all accounts with the specified type within a specific duration
     *
     * @param accountType    Account Type for which to compute balance
     * @param startTimestamp Begin time for the duration in milliseconds
     * @param endTimestamp   End time for duration in milliseconds
     * @return Account balance
     */
    public Money getAccountBalance(AccountType accountType, String currencyCode, long startTimestamp, long endTimestamp) {
        String where = AccountEntry.COLUMN_TYPE + "= ?";
        String[] whereArgs = new String[]{accountType.name()};
        Cursor cursor = fetchAccounts(where, whereArgs, null);
        final int columnIndexUID = cursor.getColumnIndexOrThrow(AccountEntry.COLUMN_UID);
        List<String> accountUidList = new ArrayList<>();
        while (cursor.moveToNext()) {
            String accountUID = cursor.getString(columnIndexUID);
            accountUidList.add(accountUID);
        }
        cursor.close();

        Timber.d("all account list : %d", accountUidList.size());
        SplitsDbAdapter splitsDbAdapter = transactionsDbAdapter.splitsDbAdapter;
        return splitsDbAdapter.computeSplitBalance(accountUidList, currencyCode, startTimestamp, endTimestamp);
    }

    /**
     * Returns the account balance for all accounts types specified
     *
     * @param accountTypes List of account types
     * @param start        Begin timestamp for transactions
     * @param end          End timestamp of transactions
     * @return Money balance of the account types
     */
    public Money getAccountBalance(List<AccountType> accountTypes, long start, long end) {
        String currencyCode = GnuCashApplication.getDefaultCurrencyCode();
        Money balance = Money.createZeroInstance(currencyCode);
        for (AccountType accountType : accountTypes) {
            balance = balance.plus(getAccountBalance(accountType, currencyCode, start, end));
        }
        return balance;
    }

    /**
     * Returns the current account balance for the accounts type.
     *
     * @param accountTypes The account type
     * @return Money balance of the account type
     */
    public Money getCurrentAccountsBalance(List<AccountType> accountTypes) {
        return getAccountBalance(accountTypes, -1, System.currentTimeMillis());
    }

    private Money computeBalance(String accountUID, long startTimestamp, long endTimestamp, boolean includeSubAccounts) {
        Account account = getSimpleRecord(accountUID);

        // Is the value cached?
        String[] columns = new String[]{AccountEntry.COLUMN_BALANCE};
        String selection = AccountEntry.COLUMN_UID + "=?";
        String[] selectionArgs = new String[]{accountUID};
        Cursor cursor = mDb.query(mTableName, columns, selection, selectionArgs, null, null, null);
        try {
            if (cursor.moveToFirst()) {
                BigDecimal amount = getBigDecimal(cursor, 0);
                if (amount != null) {
                    return new Money(amount, account.getCommodity());
                }
            }
        } finally {
            cursor.close();
        }

        Money balance = computeBalance(account, startTimestamp, endTimestamp, includeSubAccounts);

        // Cache for next read.
        ContentValues values = new ContentValues();
        values.put(AccountEntry.COLUMN_BALANCE, balance.toBigDecimal().toString());
        mDb.update(mTableName, values, selection, selectionArgs);

        return balance;
    }

    private Money computeBalance(Account account, long startTimestamp, long endTimestamp, boolean includeSubAccounts) {
        Timber.d("Computing account balance for account %s", account);
        String accountUID = account.getUID();
        AccountType accountType = account.getAccountType();
        String currencyCode = account.getCommodity().getCurrencyCode();

        List<String> accountsList = includeSubAccounts ? getDescendantAccountUIDs(accountUID,
            null, null) : new ArrayList<>();

        accountsList.add(0, accountUID);
        Timber.d("compute account list : %d", accountsList.size());

        SplitsDbAdapter splitsDbAdapter = transactionsDbAdapter.splitsDbAdapter;
        Money balance = splitsDbAdapter.computeSplitBalance(accountsList, currencyCode, startTimestamp, endTimestamp);
        return accountType.hasDebitDisplayBalance ? balance : balance.unaryMinus();
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
    public Money getAccountsBalance(@NonNull List<String> accountUIDList, long startTimestamp, long endTimestamp) {
        String currencyCode = GnuCashApplication.getDefaultCurrencyCode();
        if (accountUIDList.isEmpty()) {
            return Money.createZeroInstance(currencyCode);
        }

        SplitsDbAdapter splitsDbAdapter = transactionsDbAdapter.splitsDbAdapter;

        return splitsDbAdapter.computeSplitBalance(accountUIDList, currencyCode, startTimestamp, endTimestamp);
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
    public List<String> getDescendantAccountUIDs(String accountUID, String where, String[] whereArgs) {
        // holds accountUID with all descendant accounts.
        List<String> accounts = new ArrayList<>();
        // holds descendant accounts of the same level
        List<String> accountsLevel = new ArrayList<>();
        final String[] projection = new String[]{AccountEntry.COLUMN_UID};
        final String whereAnd = (TextUtils.isEmpty(where) ? "" : " AND " + where);
        final int columnIndexUID = 0;

        accountsLevel.add(accountUID);
        do {
            Cursor cursor = mDb.query(
                mTableName,
                projection,
                AccountEntry.COLUMN_PARENT_ACCOUNT_UID + " IN ('" + TextUtils.join("','", accountsLevel) + "')" + whereAnd,
                whereArgs,
                null,
                null,
                AccountEntry.COLUMN_FULL_NAME
            );
            accountsLevel.clear();
            if (cursor != null) {
                try {
                    while (cursor.moveToNext()) {
                        accountsLevel.add(cursor.getString(columnIndexUID));
                    }
                } finally {
                    cursor.close();
                }
            }
            accounts.addAll(accountsLevel);
        } while (!accountsLevel.isEmpty());
        return accounts;
    }

    /**
     * Returns a cursor to the dataset containing sub-accounts of the account with record ID <code>accountUID</code>
     *
     * @param accountUID GUID of the parent account
     * @return {@link Cursor} to the sub accounts data set
     */
    public Cursor fetchSubAccounts(String accountUID) {
        Timber.v("Fetching sub accounts for account id %s", accountUID);
        String selection = AccountEntry.COLUMN_HIDDEN + " = 0 AND "
            + AccountEntry.COLUMN_PARENT_ACCOUNT_UID + " = ?";
        final String[] selectionArgs =
            new String[]{accountUID};
        return fetchAccounts(selection, selectionArgs, null);
    }

    /**
     * Returns the top level accounts i.e. accounts with no parent or with the GnuCash ROOT account as parent
     *
     * @return Cursor to the top level accounts
     */
    public Cursor fetchTopLevelAccounts(@Nullable String filterName) {
        //condition which selects accounts with no parent, whose UID is not ROOT and whose type is not ROOT
        final String[] selectionArgs;
        String selection = AccountEntry.COLUMN_HIDDEN + " = 0 AND "
            + AccountEntry.COLUMN_TYPE + " != ?";
        if (TextUtils.isEmpty(filterName)) {
            selection += " AND (" + AccountEntry.COLUMN_PARENT_ACCOUNT_UID + " IS NULL OR "
                + AccountEntry.COLUMN_PARENT_ACCOUNT_UID + " = ?)";
            selectionArgs = new String[]{AccountType.ROOT.name(), getOrCreateGnuCashRootAccountUID()};
        } else {
            selection += " AND (" + AccountEntry.COLUMN_NAME + " LIKE '%" + escapeForLike(filterName) + "%')";
            selectionArgs = new String[]{AccountType.ROOT.name()};
        }
        return fetchAccounts(selection, selectionArgs, null);
    }

    /**
     * Returns a cursor to accounts which have recently had transactions added to them
     *
     * @return Cursor to recently used accounts
     */
    public Cursor fetchRecentAccounts(int numberOfRecent, @Nullable String filterName) {
        String selection = AccountEntry.TABLE_NAME + "." + AccountEntry.COLUMN_HIDDEN + " = 0";
        if (!TextUtils.isEmpty(filterName)) {
            selection += " AND (" + AccountEntry.TABLE_NAME + "." + AccountEntry.COLUMN_NAME + " LIKE '%" + escapeForLike(filterName) + "%')";
        }
        return mDb.query(TransactionEntry.TABLE_NAME
                + " LEFT OUTER JOIN " + SplitEntry.TABLE_NAME + " ON "
                + TransactionEntry.TABLE_NAME + "." + TransactionEntry.COLUMN_UID + " = "
                + SplitEntry.TABLE_NAME + "." + SplitEntry.COLUMN_TRANSACTION_UID
                + " , " + AccountEntry.TABLE_NAME + " ON " + SplitEntry.TABLE_NAME + "." + SplitEntry.COLUMN_ACCOUNT_UID
                + " = " + AccountEntry.TABLE_NAME + "." + AccountEntry.COLUMN_UID,
            new String[]{AccountEntry.TABLE_NAME + ".*"},
            selection,
            null,
            SplitEntry.TABLE_NAME + "." + SplitEntry.COLUMN_ACCOUNT_UID, //groupby
            null, //having
            "MAX ( " + TransactionEntry.TABLE_NAME + "." + TransactionEntry.COLUMN_TIMESTAMP + " ) DESC", // order
            Integer.toString(numberOfRecent) // limit;
        );
    }

    /**
     * Fetches favorite accounts from the database
     *
     * @return Cursor holding set of favorite accounts
     */
    public Cursor fetchFavoriteAccounts(@Nullable String filterName) {
        Timber.v("Fetching favorite accounts from db");
        String selection = AccountEntry.COLUMN_HIDDEN + " = 0 AND "
            + AccountEntry.COLUMN_FAVORITE + "=1";
        if (!TextUtils.isEmpty(filterName)) {
            selection += " AND (" + AccountEntry.COLUMN_NAME + " LIKE '%" + escapeForLike(filterName) + "%')";
        }
        return fetchAccounts(selection, null, null);
    }

    /**
     * Returns the GnuCash ROOT account UID if one exists (or creates one if necessary).
     * <p>In GnuCash desktop account structure, there is a root account (which is not visible in the UI) from which
     * other top level accounts derive. GnuCash Android also enforces a ROOT account now</p>
     *
     * @return Unique ID of the GnuCash root account.
     */
    public String getOrCreateGnuCashRootAccountUID() {
        if (rootUID != null) {
            return rootUID;
        }
        String where = AccountEntry.COLUMN_TYPE + "=?"
            + " AND " + AccountEntry.COLUMN_CURRENCY + "!=?";
        String[] whereArgs = new String[]{AccountType.ROOT.name(), Commodity.TEMPLATE};
        Cursor cursor = fetchAccounts(where, whereArgs, null);
        try {
            if (cursor.moveToFirst()) {
                String uid = cursor.getString(cursor.getColumnIndexOrThrow(AccountEntry.COLUMN_UID));
                rootUID = uid;
                return uid;
            }
        } finally {
            cursor.close();
        }
        // No ROOT exits, create a new one
        Commodity commodity = commoditiesDbAdapter.getDefaultCommodity();
        Account rootAccount = new Account(ROOT_ACCOUNT_NAME, commodity);
        rootAccount.setAccountType(AccountType.ROOT);
        rootAccount.setFullName(ROOT_ACCOUNT_FULL_NAME);
        rootAccount.setHidden(true);
        rootAccount.setPlaceholder(true);
        ContentValues contentValues = new ContentValues();
        contentValues.put(AccountEntry.COLUMN_UID, rootAccount.getUID());
        contentValues.put(AccountEntry.COLUMN_NAME, rootAccount.getName());
        contentValues.put(AccountEntry.COLUMN_FULL_NAME, rootAccount.getFullName());
        contentValues.put(AccountEntry.COLUMN_TYPE, rootAccount.getAccountType().name());
        contentValues.put(AccountEntry.COLUMN_HIDDEN, rootAccount.isHidden());
        contentValues.put(AccountEntry.COLUMN_CURRENCY, rootAccount.getCommodity().getCurrencyCode());
        contentValues.put(AccountEntry.COLUMN_COMMODITY_UID, rootAccount.getCommodity().getUID());
        contentValues.put(AccountEntry.COLUMN_PLACEHOLDER, rootAccount.isPlaceholder());
        Timber.i("Creating ROOT account");
        mDb.insert(mTableName, null, contentValues);
        rootUID = rootAccount.getUID();
        return rootUID;
    }

    /**
     * Returns the number of accounts for which the account with ID <code>accountUID</code> is a first level parent
     *
     * @param accountUID String Unique ID (GUID) of the account
     * @return Number of sub accounts
     */
    public int getSubAccountCount(String accountUID) {
        return (int) DatabaseUtils.queryNumEntries(
            mDb,
            mTableName,
            AccountEntry.COLUMN_PARENT_ACCOUNT_UID + " = ?",
            new String[]{accountUID}
        );
    }

    /**
     * Returns the commodity of the account
     * with unique Identifier <code>accountUID</code>
     *
     * @param accountUID Unique Identifier of the account
     * @return Commodity of the account.
     */
    public Commodity getCommodity(@NonNull String accountUID) {
        Account account = getSimpleRecord(accountUID);
        if (account != null) return account.getCommodity();
        throw new IllegalArgumentException("Account " + accountUID + " does not exist");
    }

    /**
     * Returns the simple name of the account with unique ID <code>accountUID</code>.
     *
     * @param accountUID Unique identifier of the account
     * @return Name of the account as String
     * @throws java.lang.IllegalArgumentException if accountUID does not exist
     * @see #getFullyQualifiedAccountName(String)
     */
    public String getAccountName(String accountUID) {
        Account account = getSimpleRecord(accountUID);
        if (account != null) return account.getName();
        return getAttribute(accountUID, AccountEntry.COLUMN_NAME);
    }

    /**
     * Returns the default transfer account record ID for the account with UID <code>accountUID</code>
     *
     * @param accountID Database ID of the account record
     * @return Record ID of default transfer account
     */
    public long getDefaultTransferAccountID(long accountID) {
        if (isCached) {
            for (Account account : cache.values()) {
                if (account.id == accountID) {
                    String uid = account.getDefaultTransferAccountUID();
                    return TextUtils.isEmpty(uid) ? 0 : getID(uid);
                }
            }
        }
        Cursor cursor = mDb.query(
            mTableName,
            new String[]{AccountEntry.COLUMN_DEFAULT_TRANSFER_ACCOUNT_UID},
            AccountEntry._ID + " = " + accountID,
            null, null, null, null);
        try {
            if (cursor.moveToNext()) {
                String uid = cursor.getString(
                    cursor.getColumnIndexOrThrow(AccountEntry.COLUMN_DEFAULT_TRANSFER_ACCOUNT_UID));
                return TextUtils.isEmpty(uid) ? 0 : getID(uid);
            }
        } finally {
            cursor.close();
        }
        return 0;
    }

    /**
     * Returns the full account name including the account hierarchy (parent accounts)
     *
     * @param accountUID Unique ID of account
     * @return Fully qualified (with parent hierarchy) account name
     */
    public String getFullyQualifiedAccountName(String accountUID) {
        String accountName = getAccountName(accountUID);
        String parentAccountUID = getParentAccountUID(accountUID);

        if (parentAccountUID == null || parentAccountUID.equals(accountUID) || parentAccountUID.equals(getOrCreateGnuCashRootAccountUID())) {
            return accountName;
        }

        String parentAccountName = getFullyQualifiedAccountName(parentAccountUID);

        return parentAccountName + ACCOUNT_NAME_SEPARATOR + accountName;
    }

    /**
     * Returns the full account name including the account hierarchy (parent accounts)
     *
     * @param account The account
     * @return Fully qualified (with parent hierarchy) account name
     */
    public String getFullyQualifiedAccountName(@NonNull Account account) {
        String accountName = account.getName();
        String parentAccountUID = account.getParentUID();

        if (TextUtils.isEmpty(parentAccountUID) || parentAccountUID.equalsIgnoreCase(getOrCreateGnuCashRootAccountUID())) {
            return accountName;
        }

        String parentAccountName = getFullyQualifiedAccountName(parentAccountUID);

        return parentAccountName + ACCOUNT_NAME_SEPARATOR + accountName;
    }

    /**
     * get account's full name directly from DB
     *
     * @param accountUID the account to retrieve full name
     * @return full name registered in DB
     */
    public String getAccountFullName(String accountUID) {
        Account account = getSimpleRecord(accountUID);
        if (account != null) return account.getFullName();
        throw new IllegalArgumentException("account UID: " + accountUID + " does not exist");
    }


    /**
     * Returns <code>true</code> if the account with unique ID <code>accountUID</code> is a placeholder account.
     *
     * @param accountUID Unique identifier of the account
     * @return <code>true</code> if the account is a placeholder account, <code>false</code> otherwise
     */
    public boolean isPlaceholderAccount(String accountUID) {
        Account account = getSimpleRecord(accountUID);
        if (account != null) return account.isPlaceholder();
        String isPlaceholder = getAttribute(accountUID, AccountEntry.COLUMN_PLACEHOLDER);
        return Integer.parseInt(isPlaceholder) != 0;
    }

    /**
     * Convenience method, resolves the account unique ID and calls {@link #isPlaceholderAccount(String)}
     *
     * @param accountUID GUID of the account
     * @return <code>true</code> if the account is hidden, <code>false</code> otherwise
     */
    public boolean isHiddenAccount(String accountUID) {
        Account account = getSimpleRecord(accountUID);
        if (account != null) return account.isHidden();
        String isHidden = getAttribute(accountUID, AccountEntry.COLUMN_HIDDEN);
        return Integer.parseInt(isHidden) != 0;
    }

    /**
     * Returns true if the account is a favorite account, false otherwise
     *
     * @param accountUID GUID of the account
     * @return <code>true</code> if the account is a favorite account, <code>false</code> otherwise
     */
    public boolean isFavoriteAccount(String accountUID) {
        Account account = getSimpleRecord(accountUID);
        if (account != null) return account.isFavorite();
        String isFavorite = getAttribute(accountUID, AccountEntry.COLUMN_FAVORITE);
        return Integer.parseInt(isFavorite) != 0;
    }

    /**
     * Updates all opening balances to the current account balances
     */
    public List<Transaction> getAllOpeningBalanceTransactions() {
        List<Account> accounts = getSimpleAccountList();
        List<Transaction> openingTransactions = new ArrayList<>();
        SplitsDbAdapter splitsDbAdapter = transactionsDbAdapter.splitsDbAdapter;
        for (Account account : accounts) {
            String currencyCode = account.getCommodity().getCurrencyCode();
            List<String> accountList = new ArrayList<>();
            accountList.add(account.getUID());
            Money balance = splitsDbAdapter.computeSplitBalance(accountList, currencyCode);
            if (balance.isAmountZero())
                continue;

            Transaction transaction = new Transaction(GnuCashApplication.getAppContext().getString(R.string.account_name_opening_balances));
            transaction.setNote(account.getName());
            transaction.setCommodity(account.getCommodity());
            TransactionType transactionType = Transaction.getTypeForBalance(account.getAccountType(),
                balance.isNegative());
            Split split = new Split(balance, account.getUID());
            split.setType(transactionType);
            transaction.addSplit(split);
            transaction.addSplit(split.createPair(getOrCreateOpeningBalanceAccountUID()));
            transaction.setExported(true);
            openingTransactions.add(transaction);
        }
        return openingTransactions;
    }

    public static String getImbalanceAccountPrefix(@NonNull Context context) {
        return context.getString(R.string.imbalance_account_name) + "-";
    }

    /**
     * Returns the imbalance account where to store transactions which are not double entry.
     *
     * @param commodity Commodity of the transaction
     * @return Imbalance account name
     */
    public static String getImbalanceAccountName(@NonNull Context context, @NonNull Commodity commodity) {
        return getImbalanceAccountPrefix(context) + commodity.getCurrencyCode();
    }

    /**
     * Get the name of the default account for opening balances for the current locale.
     * For the English locale, it will be "Equity:Opening Balances"
     *
     * @return Fully qualified account name of the opening balances account
     */
    public static String getOpeningBalanceAccountFullName() {
        Context context = GnuCashApplication.getAppContext();
        String parentEquity = context.getString(R.string.account_name_equity).trim();
        //German locale has no parent Equity account
        if (parentEquity.length() > 0) {
            return parentEquity + ACCOUNT_NAME_SEPARATOR
                + context.getString(R.string.account_name_opening_balances);
        } else
            return context.getString(R.string.account_name_opening_balances);
    }

    /**
     * Returns the account color for the active account as an Android resource ID.
     * <p>
     * Basically, if we are in a top level account, use the default title color.
     * but propagate a parent account's title color to children who don't have own color
     * </p>
     *
     * @param context    the context
     * @param accountUID GUID of the account
     * @return Android resource ID representing the color which can be directly set to a view
     */
    @ColorInt
    public static int getActiveAccountColorResource(@NonNull Context context, @NonNull String accountUID) {
        return AccountsDbAdapter.getInstance().getActiveAccountColor(context, accountUID);
    }

    /**
     * Returns the account color for the account as an Android resource ID.
     * <p>
     * Basically, if we are in a top level account, use the default title color.
     * but propagate a parent account's title color to children who don't have own color
     * </p>
     *
     * @param context    the context
     * @param accountUID GUID of the account
     * @return Android resource ID representing the color which can be directly set to a view
     */
    @ColorInt
    public int getActiveAccountColor(@NonNull Context context, @Nullable String accountUID) {
        while (!TextUtils.isEmpty(accountUID)) {
            int color = getAccountColor(accountUID);
            if (color != Account.DEFAULT_COLOR) {
                return color;
            }
            accountUID = getParentAccountUID(accountUID);
        }

        return ContextCompat.getColor(context, R.color.theme_primary);
    }

    /**
     * Returns the list of commodities in use in the database.
     *
     * <p>This is not the same as the list of all available commodities.</p>
     *
     * @return List of commodities in use
     */
    public List<Commodity> getCommoditiesInUse() {
        Cursor cursor = mDb.query(true, mTableName, new String[]{AccountEntry.COLUMN_COMMODITY_UID},
            null, null, null, null, null, null);
        List<Commodity> commodities = new ArrayList<>();
        try {
            while (cursor.moveToNext()) {
                String currencyUID = cursor.getString(0);
                commodities.add(commoditiesDbAdapter.getRecord(currencyUID));
            }
        } finally {
            cursor.close();
        }
        return commodities;
    }

    /**
     * Deletes all accounts, transactions (and their splits) from the database.
     * Basically empties all 3 tables, so use with care ;)
     */
    @Override
    public int deleteAllRecords() {
        // Relies "ON DELETE CASCADE" takes too much time
        // It take more than 300s to complete the deletion on my dataset without
        // clearing the split table first, but only needs a little more that 1s
        // if the split table is cleared first.
        mDb.delete(PriceEntry.TABLE_NAME, null, null);
        mDb.delete(SplitEntry.TABLE_NAME, null, null);
        mDb.delete(TransactionEntry.TABLE_NAME, null, null);
        mDb.delete(ScheduledActionEntry.TABLE_NAME, null, null);
        mDb.delete(BudgetAmountEntry.TABLE_NAME, null, null);
        mDb.delete(BudgetEntry.TABLE_NAME, null, null);
        mDb.delete(RecurrenceEntry.TABLE_NAME, null, null);
        rootUID = null;
        cache.clear();

        return super.deleteAllRecords();
    }

    @Override
    public boolean deleteRecord(@NonNull String uid) throws SQLException {
        boolean result = super.deleteRecord(uid);
        if (result) {
            if (uid.equals(rootUID)) rootUID = null;
            ContentValues contentValues = new ContentValues();
            contentValues.putNull(AccountEntry.COLUMN_DEFAULT_TRANSFER_ACCOUNT_UID);
            mDb.update(mTableName, contentValues,
                AccountEntry.COLUMN_DEFAULT_TRANSFER_ACCOUNT_UID + "=?",
                new String[]{uid});

            if (isCached) {
                for (Account account : cache.values()) {
                    if (uid.equals(account.getDefaultTransferAccountUID())) {
                        account.setDefaultTransferAccountUID(null);
                    }
                    if (uid.equals(account.getParentUID())) {
                        account.setParentUID(getOrCreateGnuCashRootAccountUID());
                    }
                }
            }
        }
        return result;
    }

    public int getTransactionMaxSplitNum(@NonNull String accountUID) {
        Cursor cursor = mDb.query("trans_extra_info",
            new String[]{"MAX(trans_split_count)"},
            "trans_acct_t_uid IN ( SELECT DISTINCT " + TransactionEntry.TABLE_NAME + "_" + TransactionEntry.COLUMN_UID +
                " FROM trans_split_acct WHERE " + AccountEntry.TABLE_NAME + "_" + AccountEntry.COLUMN_UID +
                " = ? )",
            new String[]{accountUID},
            null,
            null,
            null
        );
        try {
            if (cursor.moveToFirst()) {
                return (int) cursor.getLong(0);
            } else {
                return 0;
            }
        } finally {
            cursor.close();
        }
    }

    @Nullable
    public Account getSimpleRecord(@NonNull String uid) {
        if (TextUtils.isEmpty(uid)) return null;
        if (isCached) {
            Account account = cache.get(uid);
            if (account != null) return account;
            // TODO avoid race-condition when multiple simultaneous calls for same record.
        }

        Timber.v("Fetching simple account %s", uid);
        Cursor cursor = fetchRecord(uid);
        try {
            if (cursor.moveToFirst()) {
                Account account = buildSimpleAccountInstance(cursor);
                if (isCached) cache.put(uid, account);
                return account;
            } else {
                throw new IllegalArgumentException("Record with " + uid + " does not exist");
            }
        } finally {
            cursor.close();
        }
    }

    public long getTransactionCount(@NonNull String uid) {
        return transactionsDbAdapter.getTransactionsCountForAccount(uid);
    }

    /**
     * Returns the {@link org.gnucash.android.model.AccountType} of the account with unique ID <code>uid</code>
     *
     * @param accountUID Unique ID of the account
     * @return {@link org.gnucash.android.model.AccountType} of the account.
     * @throws java.lang.IllegalArgumentException if accountUID does not exist in DB,
     */
    public AccountType getAccountType(@NonNull String accountUID) {
        Account account = getSimpleRecord(accountUID);
        if (account != null) return account.getAccountType();
        throw new IllegalArgumentException("account " + accountUID + " does not exist in DB");
    }
}
