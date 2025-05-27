/*
 * Copyright (c) 2012 - 2015 Ngewi Fet <ngewif@gmail.com>
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

import android.content.ContentValues;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.gnucash.android.db.DatabaseSchema;
import org.gnucash.android.db.DatabaseSchema.AccountEntry;
import org.gnucash.android.db.DatabaseSchema.CommonColumns;
import org.gnucash.android.db.DatabaseSchema.SplitEntry;
import org.gnucash.android.db.DatabaseSchema.TransactionEntry;
import org.gnucash.android.model.BaseModel;
import org.gnucash.android.util.TimestampHelper;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import timber.log.Timber;

/**
 * Adapter to be used for creating and opening the database for read/write operations.
 * The adapter abstracts several methods for database access and should be subclassed
 * by any other adapters to database-backed data models.
 *
 * @author Ngewi Fet <ngewif@gmail.com>
 */
public abstract class DatabaseAdapter<Model extends BaseModel> implements Closeable {

    /**
     * SQLite database
     */
    protected final SQLiteDatabase mDb;

    protected final String mTableName;

    protected final String[] mColumns;

    protected volatile SQLiteStatement mReplaceStatement;

    protected volatile SQLiteStatement mUpdateStatement;

    protected volatile SQLiteStatement mInsertStatement;

    protected final Map<String, Model> cache = new ConcurrentHashMap<>();
    protected final boolean isCached;

    public enum UpdateMethod {
        insert, update, replace
    }

    /**
     * Opens the database adapter with an existing database
     *
     * @param db SQLiteDatabase object
     */
    public DatabaseAdapter(@NonNull SQLiteDatabase db, @NonNull String tableName, @NonNull String[] columns) {
        this(db, tableName, columns, false);
    }

    /**
     * Opens the database adapter with an existing database
     *
     * @param db SQLiteDatabase object
     */
    public DatabaseAdapter(SQLiteDatabase db, @NonNull String tableName, @NonNull String[] columns, boolean isCached) {
        this.mTableName = tableName;
        this.mDb = db;
        this.mColumns = columns;
        this.isCached = isCached;
        if (!db.isOpen()) {
            throw new IllegalArgumentException("Database not open.");
        }
        if (db.isReadOnly()) {
            throw new IllegalArgumentException("Database read-only. Writeable database required!");
        }

        if (db.getVersion() >= 9) {
            createTempView();
        }
    }

    private void createTempView() {
        //the multiplication by 1.0 is to cause sqlite to handle the value as REAL and not to round off

        // Create some temporary views. Temporary views only exists in one DB session, and will not
        // be saved in the DB
        //
        // TODO: Useful views should be add to the DB
        //
        // create a temporary view, combining accounts, transactions and splits, as this is often used
        // in the queries

        //todo: would it be useful to add the split reconciled_state and reconciled_date to this view?
        mDb.execSQL("CREATE TEMP VIEW IF NOT EXISTS trans_split_acct AS SELECT "
            + TransactionEntry.TABLE_NAME + "." + CommonColumns.COLUMN_MODIFIED_AT + " AS "
            + TransactionEntry.TABLE_NAME + "_" + CommonColumns.COLUMN_MODIFIED_AT + " , "
            + TransactionEntry.TABLE_NAME + "." + TransactionEntry.COLUMN_UID + " AS "
            + TransactionEntry.TABLE_NAME + "_" + TransactionEntry.COLUMN_UID + " , "
            + TransactionEntry.TABLE_NAME + "." + TransactionEntry.COLUMN_DESCRIPTION + " AS "
            + TransactionEntry.TABLE_NAME + "_" + TransactionEntry.COLUMN_DESCRIPTION + " , "
            + TransactionEntry.TABLE_NAME + "." + TransactionEntry.COLUMN_NOTES + " AS "
            + TransactionEntry.TABLE_NAME + "_" + TransactionEntry.COLUMN_NOTES + " , "
            + TransactionEntry.TABLE_NAME + "." + TransactionEntry.COLUMN_TIMESTAMP + " AS "
            + TransactionEntry.TABLE_NAME + "_" + TransactionEntry.COLUMN_TIMESTAMP + " , "
            + TransactionEntry.TABLE_NAME + "." + TransactionEntry.COLUMN_EXPORTED + " AS "
            + TransactionEntry.TABLE_NAME + "_" + TransactionEntry.COLUMN_EXPORTED + " , "
            + TransactionEntry.TABLE_NAME + "." + TransactionEntry.COLUMN_TEMPLATE + " AS "
            + TransactionEntry.TABLE_NAME + "_" + TransactionEntry.COLUMN_TEMPLATE + " , "
            + SplitEntry.TABLE_NAME + "." + SplitEntry.COLUMN_UID + " AS "
            + SplitEntry.TABLE_NAME + "_" + SplitEntry.COLUMN_UID + " , "
            + SplitEntry.TABLE_NAME + "." + SplitEntry.COLUMN_TYPE + " AS "
            + SplitEntry.TABLE_NAME + "_" + SplitEntry.COLUMN_TYPE + " , "
            + SplitEntry.TABLE_NAME + "." + SplitEntry.COLUMN_VALUE_NUM + " AS "
            + SplitEntry.TABLE_NAME + "_" + SplitEntry.COLUMN_VALUE_NUM + " , "
            + SplitEntry.TABLE_NAME + "." + SplitEntry.COLUMN_VALUE_DENOM + " AS "
            + SplitEntry.TABLE_NAME + "_" + SplitEntry.COLUMN_VALUE_DENOM + " , "
            + SplitEntry.TABLE_NAME + "." + SplitEntry.COLUMN_QUANTITY_NUM + " AS "
            + SplitEntry.TABLE_NAME + "_" + SplitEntry.COLUMN_QUANTITY_NUM + " , "
            + SplitEntry.TABLE_NAME + "." + SplitEntry.COLUMN_QUANTITY_DENOM + " AS "
            + SplitEntry.TABLE_NAME + "_" + SplitEntry.COLUMN_QUANTITY_DENOM + " , "
            + SplitEntry.TABLE_NAME + "." + SplitEntry.COLUMN_MEMO + " AS "
            + SplitEntry.TABLE_NAME + "_" + SplitEntry.COLUMN_MEMO + " , "
            + AccountEntry.TABLE_NAME + "." + AccountEntry.COLUMN_UID + " AS "
            + AccountEntry.TABLE_NAME + "_" + AccountEntry.COLUMN_UID + " , "
            + AccountEntry.TABLE_NAME + "." + AccountEntry.COLUMN_NAME + " AS "
            + AccountEntry.TABLE_NAME + "_" + AccountEntry.COLUMN_NAME + " , "
            + AccountEntry.TABLE_NAME + "." + AccountEntry.COLUMN_COMMODITY_UID + " AS "
            + AccountEntry.TABLE_NAME + "_" + AccountEntry.COLUMN_COMMODITY_UID + " , "
            + AccountEntry.TABLE_NAME + "." + AccountEntry.COLUMN_PARENT_ACCOUNT_UID + " AS "
            + AccountEntry.TABLE_NAME + "_" + AccountEntry.COLUMN_PARENT_ACCOUNT_UID + " , "
            + AccountEntry.TABLE_NAME + "." + AccountEntry.COLUMN_PLACEHOLDER + " AS "
            + AccountEntry.TABLE_NAME + "_" + AccountEntry.COLUMN_PLACEHOLDER + " , "
            + AccountEntry.TABLE_NAME + "." + AccountEntry.COLUMN_COLOR_CODE + " AS "
            + AccountEntry.TABLE_NAME + "_" + AccountEntry.COLUMN_COLOR_CODE + " , "
            + AccountEntry.TABLE_NAME + "." + AccountEntry.COLUMN_FAVORITE + " AS "
            + AccountEntry.TABLE_NAME + "_" + AccountEntry.COLUMN_FAVORITE + " , "
            + AccountEntry.TABLE_NAME + "." + AccountEntry.COLUMN_FULL_NAME + " AS "
            + AccountEntry.TABLE_NAME + "_" + AccountEntry.COLUMN_FULL_NAME + " , "
            + AccountEntry.TABLE_NAME + "." + AccountEntry.COLUMN_TYPE + " AS "
            + AccountEntry.TABLE_NAME + "_" + AccountEntry.COLUMN_TYPE + " , "
            + AccountEntry.TABLE_NAME + "." + AccountEntry.COLUMN_DEFAULT_TRANSFER_ACCOUNT_UID + " AS "
            + AccountEntry.TABLE_NAME + "_" + AccountEntry.COLUMN_DEFAULT_TRANSFER_ACCOUNT_UID
            + " FROM " + TransactionEntry.TABLE_NAME + " , " + SplitEntry.TABLE_NAME + " ON "
            + TransactionEntry.TABLE_NAME + "." + TransactionEntry.COLUMN_UID + "=" + SplitEntry.TABLE_NAME + "." + SplitEntry.COLUMN_TRANSACTION_UID
            + " , " + AccountEntry.TABLE_NAME + " ON "
            + SplitEntry.TABLE_NAME + "." + SplitEntry.COLUMN_ACCOUNT_UID + "=" + AccountEntry.TABLE_NAME + "." + AccountEntry.COLUMN_UID
        );

        mDb.execSQL("CREATE TEMP VIEW IF NOT EXISTS trans_extra_info AS SELECT " + TransactionEntry.TABLE_NAME + "_" + TransactionEntry.COLUMN_UID +
            " AS trans_acct_t_uid , SUBSTR ( MIN ( ( CASE WHEN IFNULL ( " + SplitEntry.TABLE_NAME + "_" +
            SplitEntry.COLUMN_MEMO + " , '' ) == '' THEN 'a' ELSE 'b' END ) || " +
            AccountEntry.TABLE_NAME + "_" + AccountEntry.COLUMN_UID +
            " ) , 2 ) AS trans_acct_a_uid , TOTAL ( CASE WHEN " + SplitEntry.TABLE_NAME + "_" +
            SplitEntry.COLUMN_TYPE + " = 'DEBIT' THEN " + SplitEntry.TABLE_NAME + "_" +
            SplitEntry.COLUMN_VALUE_NUM + " ELSE - " + SplitEntry.TABLE_NAME + "_" +
            SplitEntry.COLUMN_VALUE_NUM + " END ) * 1.0 / " + SplitEntry.TABLE_NAME + "_" +
            SplitEntry.COLUMN_VALUE_DENOM + " AS trans_acct_balance , COUNT ( DISTINCT " +
            AccountEntry.TABLE_NAME + "_" + AccountEntry.COLUMN_COMMODITY_UID +
            " ) AS trans_currency_count , COUNT (*) AS trans_split_count FROM trans_split_acct " +
            " GROUP BY " + TransactionEntry.TABLE_NAME + "_" + TransactionEntry.COLUMN_UID
        );
    }

    /**
     * Checks if the database is open
     *
     * @return <code>true</code> if the database is open, <code>false</code> otherwise
     */
    public boolean isOpen() {
        return mDb.isOpen();
    }

    /**
     * Adds a record to the database with the data contained in the model.
     * <p>This method uses the SQL REPLACE instructions to replace any record with a matching GUID.
     * So beware of any foreign keys with cascade dependencies which might need to be re-added</p>
     *
     * @param model Model to be saved to the database
     */
    public void addRecord(@NonNull final Model model) throws SQLException {
        addRecord(model, UpdateMethod.replace);
    }

    /**
     * Add a model record to the database.
     * <p>If unsure about which {@code updateMethod} to use, use {@link UpdateMethod#replace}</p>
     *
     * @param model        Subclass of {@link BaseModel} to be added
     * @param updateMethod Method to use for adding the record
     */
    public void addRecord(@NonNull final Model model, UpdateMethod updateMethod) throws SQLException {
        Timber.d("Adding record to database: %s %s", model.getClass().getSimpleName(), model.getUID());
        final SQLiteStatement statement;
        switch (updateMethod) {
            case insert:
                statement = getInsertStatement();
                synchronized (statement) {
                    model.id = bind(statement, model).executeInsert();
                }
                break;
            case update:
                statement = getUpdateStatement();
                synchronized (statement) {
                    bind(statement, model).execute();
                }
                break;
            default:
                statement = getReplaceStatement();
                synchronized (statement) {
                    model.id = bind(statement, model).executeInsert();
                }
                break;
        }
        if (isCached) cache.put(model.getUID(), model);
    }

    /**
     * Persist the model object to the database as records using the {@code updateMethod}
     *
     * @param modelList    List of records
     * @param updateMethod Method to use when persisting them
     * @return Number of rows affected in the database
     */
    private long doAddModels(@NonNull final List<Model> modelList, UpdateMethod updateMethod) throws SQLException {
        long nRow = 0;
        final SQLiteStatement statement;
        switch (updateMethod) {
            case update:
                statement = getUpdateStatement();
                synchronized (statement) {
                    for (Model model : modelList) {
                        bind(statement, model).execute();
                        nRow++;
                    }
                }
                break;
            case insert:
                statement = getInsertStatement();
                synchronized (statement) {
                    for (Model model : modelList) {
                        bind(statement, model).execute();
                        nRow++;
                    }
                }
                break;
            default:
                statement = getReplaceStatement();
                synchronized (statement) {
                    for (Model model : modelList) {
                        bind(statement, model).execute();
                        nRow++;
                    }
                }
                break;
        }
        return nRow;
    }

    /**
     * Add multiple records to the database at once
     * <p>Either all or none of the records will be inserted/updated into the database.</p>
     *
     * @param modelList List of model records
     * @return Number of rows inserted
     */
    public long bulkAddRecords(@NonNull List<Model> modelList) {
        return bulkAddRecords(modelList, UpdateMethod.replace);
    }

    public long bulkAddRecords(@NonNull List<Model> modelList, UpdateMethod updateMethod) throws SQLException {
        if (modelList.isEmpty()) {
            Timber.w("Empty model list. Cannot bulk add records, returning 0");
            return 0;
        }

        Timber.i("Bulk adding %d %s records to the database", modelList.size(),
            modelList.get(0).getClass().getSimpleName());
        long nRow;
        try {
            beginTransaction();
            nRow = doAddModels(modelList, updateMethod);
            setTransactionSuccessful();
        } finally {
            endTransaction();
        }

        return nRow;
    }

    /**
     * Builds an instance of the model from the database record entry
     * <p>When implementing this method, remember to call {@link #populateBaseModelAttributes(Cursor, BaseModel)}</p>
     *
     * @param cursor Cursor pointing to the record
     * @return New instance of the model from database record
     */
    public abstract Model buildModelInstance(@NonNull final Cursor cursor);

    /**
     * Generates an {@link SQLiteStatement} with values from the {@code model}.
     * This statement can be executed to replace a record in the database.
     * <p>If the {@link #mReplaceStatement} is null, subclasses should create a new statement and return.<br/>
     * If it is not null, the previous bindings will be cleared and replaced with those from the model</p>
     *
     * @return SQLiteStatement for replacing a record in the database
     */
    protected final @NonNull SQLiteStatement getReplaceStatement() {
        SQLiteStatement stmt = mReplaceStatement;
        if (stmt == null) {
            synchronized (this) {
                stmt = mReplaceStatement;
                if (stmt == null) {
                    String sql = buildReplaceStatement();
                    mReplaceStatement = stmt = mDb.compileStatement(sql);
                }
            }
        }
        return stmt;
    }

    private String buildReplaceStatement() {
        final int columnsCount = mColumns.length;
        StringBuilder sql = new StringBuilder()
            .append("REPLACE INTO ").append(mTableName).append(" (");
        for (int i = 0; i < columnsCount; i++) {
            sql.append(mColumns[i]).append(",");
        }
        sql.append(CommonColumns.COLUMN_UID)
            .append(") VALUES (");
        for (int i = 0; i < columnsCount; i++) {
            sql.append("?,");
        }
        sql.append("?)");
        return sql.toString();
    }

    protected final @NonNull SQLiteStatement getUpdateStatement() {
        SQLiteStatement stmt = mUpdateStatement;
        if (stmt == null) {
            synchronized (this) {
                stmt = mUpdateStatement;
                if (stmt == null) {
                    String sql = buildUpdateStatement();
                    mUpdateStatement = stmt = mDb.compileStatement(sql);
                }
            }
        }
        return stmt;
    }

    private String buildUpdateStatement() {
        final int columnsCount = mColumns.length;
        StringBuilder sql = new StringBuilder()
            .append("UPDATE ").append(mTableName).append(" SET ");
        for (int i = 0; i < columnsCount; i++) {
            sql.append(mColumns[i]).append("=?,");
        }
        sql.deleteCharAt(sql.length() - 1);//delete the last ","
        sql.append(" WHERE ").append(CommonColumns.COLUMN_UID).append("=?");
        return sql.toString();
    }

    protected final @NonNull SQLiteStatement getInsertStatement() {
        SQLiteStatement stmt = mInsertStatement;
        if (stmt == null) {
            synchronized (this) {
                stmt = mInsertStatement;
                if (stmt == null) {
                    String sql = buildInsertStatement();
                    mInsertStatement = stmt = mDb.compileStatement(sql);
                }
            }
        }
        return stmt;
    }

    private String buildInsertStatement() {
        final int columnsCount = mColumns.length;
        StringBuilder sql = new StringBuilder()
            .append("INSERT INTO ").append(mTableName).append(" (");
        for (int i = 0; i < columnsCount; i++) {
            sql.append(mColumns[i]).append(",");
        }
        sql.append(CommonColumns.COLUMN_UID)
            .append(") VALUES (");
        for (int i = 0; i < columnsCount; i++) {
            sql.append("?,");
        }
        sql.append("?)");
        return sql.toString();
    }

    /**
     * Binds the values from the model the the SQL statement
     *
     * @param stmt  SQL statement with placeholders
     * @param model Model from which to read bind attributes
     * @return SQL statement ready for execution
     */
    protected abstract @NonNull SQLiteStatement bind(@NonNull SQLiteStatement stmt, @NonNull final Model model) throws SQLException;

    protected void bindBaseModel(@NonNull SQLiteStatement stmt, @NonNull final Model model) {
        stmt.clearBindings();
        stmt.bindString(1 + mColumns.length, model.getUID());
    }

    @Nullable
    public Model getRecordOrNull(String uid) {
        if (isCached) {
            Model model = cache.get(uid);
            if (model != null) return model;
        }
        Timber.v("Fetching record from %s with UID %s", mTableName, uid);
        Cursor cursor = fetchRecord(uid);
        try {
            if (cursor.moveToFirst()) {
                Model model = buildModelInstance(cursor);
                if (isCached) {
                    cache.put(uid, model);
                }
                return model;
            }
        } finally {
            cursor.close();
        }
        return null;
    }

    /**
     * Returns a model instance populated with data from the record with GUID {@code uid}
     * <p>Sub-classes which require special handling should override this method</p>
     *
     * @param uid GUID of the record
     * @return BaseModel instance of the record
     * @throws IllegalArgumentException if the record UID does not exist in thd database
     */
    @NonNull
    public Model getRecord(@NonNull String uid) throws IllegalArgumentException {
        Model model = getRecordOrNull(uid);
        if (model == null) {
            throw new IllegalArgumentException("Record with " + uid + " does not exist");
        }
        return model;
    }

    /**
     * Overload of {@link #getRecord(String)}
     * Simply converts the record ID to a GUID and calls {@link #getRecord(String)}
     *
     * @param id Database record ID
     * @return Subclass of {@link BaseModel} containing record info
     */
    @NonNull
    public Model getRecord(long id) throws IllegalArgumentException {
        return getRecord(getUID(id));
    }

    /**
     * Returns all the records in the database
     *
     * @return List of records in the database
     */
    @NonNull
    public List<Model> getAllRecords() {
        return getRecords(fetchAllRecords());
    }

    @NonNull
    protected List<Model> getRecords(@Nullable Cursor cursor) {
        List<Model> records = new ArrayList<>();
        if (cursor == null) return records;

        try {
            if (cursor.moveToFirst()) {
                do {
                    Model model = buildModelInstance(cursor);
                    records.add(model);
                    if (isCached) {
                        cache.put(model.getUID(), model);
                    }
                } while (cursor.moveToNext());
            }
        } finally {
            cursor.close();
        }
        return records;
    }

    /**
     * Extracts the attributes of the base model and adds them to the ContentValues object provided
     *
     * @param contentValues Content values to which to add attributes
     * @param model         {@link org.gnucash.android.model.BaseModel} from which to extract values
     * @return {@link android.content.ContentValues} with the data to be inserted into the db
     */
    protected ContentValues extractBaseModelAttributes(@NonNull ContentValues contentValues, @NonNull Model model) {
        contentValues.put(CommonColumns.COLUMN_UID, model.getUID());
        contentValues.put(CommonColumns.COLUMN_CREATED_AT, TimestampHelper.getUtcStringFromTimestamp(model.getCreatedTimestamp()));
        //there is a trigger in the database for updated the modified_at column
        /* Due to the use of SQL REPLACE syntax, we insert the created_at values each time
         * (maintain the original creation time and not the time of creation of the replacement)
         * The updated_at column has a trigger in the database which will update the column
         */
        return contentValues;
    }

    /**
     * Initializes the model with values from the database record common to all models (i.e. in the BaseModel)
     *
     * @param cursor Cursor pointing to database record
     * @param model  Model instance to be initialized
     */
    protected void populateBaseModelAttributes(Cursor cursor, BaseModel model) {
        long id = cursor.getLong(cursor.getColumnIndexOrThrow(CommonColumns.COLUMN_ID));
        String uid = cursor.getString(cursor.getColumnIndexOrThrow(CommonColumns.COLUMN_UID));
        String created = cursor.getString(cursor.getColumnIndexOrThrow(CommonColumns.COLUMN_CREATED_AT));
        String modified = cursor.getString(cursor.getColumnIndexOrThrow(CommonColumns.COLUMN_MODIFIED_AT));

        model.id = id;
        model.setUID(uid);
        model.setCreatedTimestamp(TimestampHelper.getTimestampFromUtcString(created));
        model.setModifiedTimestamp(TimestampHelper.getTimestampFromUtcString(modified));
    }

    /**
     * Retrieves record with GUID {@code uid} from database table
     *
     * @param uid GUID of record to be retrieved
     * @return {@link Cursor} to record retrieved
     */
    public Cursor fetchRecord(@NonNull String uid) {
        if (TextUtils.isEmpty(uid)) {
            throw new IllegalArgumentException("UID required");
        }
        return mDb.query(mTableName, null, CommonColumns.COLUMN_UID + "=?",
            new String[]{uid}, null, null, null);
    }

    /**
     * Retrieves all records from database table
     *
     * @return {@link Cursor} to all records in table <code>tableName</code>
     */
    public Cursor fetchAllRecords() {
        return fetchAllRecords(null, null, null);
    }

    /**
     * Fetch all records from database matching conditions
     *
     * @param where     SQL where clause
     * @param whereArgs String arguments for where clause
     * @param orderBy   SQL orderby clause
     * @return Cursor to records matching conditions
     */
    public Cursor fetchAllRecords(String where, String[] whereArgs, String orderBy) {
        return mDb.query(mTableName, null, where, whereArgs, null, null, orderBy);
    }

    /**
     * Deletes record with ID <code>rowID</code> from database table.
     *
     * @param rowId ID of record to be deleted
     * @return <code>true</code> if deletion was successful, <code>false</code> otherwise
     */
    public boolean deleteRecord(long rowId) throws SQLException {
        Timber.d("Deleting record with id " + rowId + " from " + mTableName);
        return mDb.delete(mTableName, DatabaseSchema.CommonColumns._ID + "=" + rowId, null) > 0;
    }

    /**
     * Deletes all records in the database
     *
     * @return Number of deleted records
     */
    public int deleteAllRecords() {
        cache.clear();
        return mDb.delete(mTableName, null, null);
    }

    /**
     * Returns the string unique ID (GUID) of a record in the database
     *
     * @param uid GUID of the record
     * @return Long record ID
     * @throws IllegalArgumentException if the GUID does not exist in the database
     */
    public long getID(@NonNull String uid) {
        if (isCached) {
            Model model = cache.get(uid);
            if (model != null) return model.id;
        }
        Cursor cursor = mDb.query(mTableName,
            new String[]{DatabaseSchema.CommonColumns._ID},
            DatabaseSchema.CommonColumns.COLUMN_UID + " = ?",
            new String[]{uid},
            null, null, null);
        final long result;
        try {
            if (cursor.moveToFirst()) {
                result = cursor.getLong(0);
            } else {
                throw new IllegalArgumentException(mTableName + " with GUID " + uid + " does not exist in the db");
            }
        } finally {
            cursor.close();
        }
        return result;
    }

    /**
     * Returns the string unique ID (GUID) of a record in the database
     *
     * @param id long database record ID
     * @return GUID of the record
     * @throws IllegalArgumentException if the record ID does not exist in the database
     */
    public String getUID(long id) {
        if (isCached) {
            for (Model model : cache.values()) {
                if (model.id == id) {
                    return model.getUID();
                }
            }
        }
        Cursor cursor = mDb.query(mTableName,
            new String[]{DatabaseSchema.CommonColumns.COLUMN_UID},
            DatabaseSchema.CommonColumns._ID + " = " + id,
            null, null, null, null);

        try {
            if (cursor.moveToFirst()) {
                return cursor.getString(0);
            } else {
                throw new IllegalArgumentException(mTableName + " Record ID " + id + " does not exist in the db");
            }
        } finally {
            cursor.close();
        }
    }

    /**
     * Updates a record in the table
     *
     * @param recordId  Database ID of the record to be updated
     * @param columnKey Name of column to be updated
     * @param newValue  New value to be assigned to the columnKey
     * @return Number of records affected
     */
    protected int updateRecord(String tableName, long recordId, String columnKey, String newValue) {
        if (isCached) {
            String uid = null;
            for (Model model : cache.values()) {
                if (model.id == recordId) {
                    uid = model.getUID();
                    break;
                }
            }
            if (uid != null) cache.remove(uid);
        }
        ContentValues contentValues = new ContentValues();
        if (newValue == null) {
            contentValues.putNull(columnKey);
        } else {
            contentValues.put(columnKey, newValue);
        }
        return mDb.update(tableName, contentValues,
            DatabaseSchema.CommonColumns._ID + "=" + recordId, null);
    }

    /**
     * Updates a record in the table
     *
     * @param uid       GUID of the record
     * @param columnKey Name of column to be updated
     * @param newValue  New value to be assigned to the columnKey
     * @return Number of records affected
     */
    public int updateRecord(@NonNull String uid, @NonNull String columnKey, String newValue) {
        if (isCached) cache.remove(uid);
        return updateRecords(CommonColumns.COLUMN_UID + "=?", new String[]{uid}, columnKey, newValue);
    }

    /**
     * Overloaded method. Updates the record with GUID {@code uid} with the content values
     *
     * @param uid           GUID of the record
     * @param contentValues Content values to update
     * @return Number of records updated
     */
    public int updateRecord(@NonNull String uid, @NonNull ContentValues contentValues) {
        if (isCached) cache.remove(uid);
        return mDb.update(mTableName, contentValues, CommonColumns.COLUMN_UID + "=?", new String[]{uid});
    }

    public void updateRecord(Model model) throws SQLException {
        addRecord(model, UpdateMethod.update);
    }

    /**
     * Updates all records which match the {@code where} clause with the {@code newValue} for the column
     *
     * @param where     SQL where clause
     * @param whereArgs String arguments for where clause
     * @param columnKey Name of column to be updated
     * @param newValue  New value to be assigned to the columnKey
     * @return Number of records affected
     */
    public int updateRecords(String where, String[] whereArgs, @NonNull String columnKey, String newValue) {
        ContentValues contentValues = new ContentValues();
        if (newValue == null) {
            contentValues.putNull(columnKey);
        } else {
            contentValues.put(columnKey, newValue);
        }
        return mDb.update(mTableName, contentValues, where, whereArgs);
    }

    /**
     * Deletes a record from the database given its unique identifier.
     * <p>Overload of the method {@link #deleteRecord(long)}</p>
     *
     * @param uid GUID of the record
     * @return <code>true</code> if deletion was successful, <code>false</code> otherwise
     * @see #deleteRecord(long)
     */
    public boolean deleteRecord(@NonNull String uid) throws SQLException {
        if (isCached) cache.remove(uid);
        return deleteRecord(getID(uid));
    }

    public boolean deleteRecord(@NonNull Model model) throws SQLException {
        if (deleteRecord(model.id)) {
            if (isCached) cache.remove(model.getUID());
            model.id = 0L;
            return true;
        }
        return false;
    }

    /**
     * Returns an attribute from a specific column in the database for a specific record.
     * <p>The attribute is returned as a string which can then be converted to another type if
     * the caller was expecting something other type </p>
     *
     * @param recordUID  GUID of the record
     * @param columnName Name of the column to be retrieved
     * @return String value of the column entry
     * @throws IllegalArgumentException if either the {@code recordUID} or {@code columnName} do not exist in the database
     */
    public String getAttribute(@NonNull String recordUID, @NonNull String columnName) {
        return getAttribute(mTableName, recordUID, columnName);
    }

    /**
     * Returns an attribute from a specific column in the database for a specific record.
     * <p>The attribute is returned as a string which can then be converted to another type if
     * the caller was expecting something other type </p>
     *
     * @param model      the record with a GUID.
     * @param columnName Name of the column to be retrieved
     * @return String value of the column entry
     * @throws IllegalArgumentException if either the {@code recordUID} or {@code columnName} do not exist in the database
     */
    public String getAttribute(@NonNull Model model, @NonNull String columnName) {
        return getAttribute(mTableName, getUID(model), columnName);
    }

    /**
     * Returns an attribute from a specific column in the database for a specific record and specific table.
     * <p>The attribute is returned as a string which can then be converted to another type if
     * the caller was expecting something other type </p>
     * <p>This method is an override of {@link #getAttribute(String, String)} which allows to select a value from a
     * different table than the one of current adapter instance
     * </p>
     *
     * @param tableName  Database table name. See {@link DatabaseSchema}
     * @param recordUID  GUID of the record
     * @param columnName Name of the column to be retrieved
     * @return String value of the column entry
     * @throws IllegalArgumentException if either the {@code recordUID} or {@code columnName} do not exist in the database
     */
    protected String getAttribute(@NonNull String tableName, @NonNull String recordUID, @NonNull String columnName) {
        Cursor cursor = mDb.query(tableName,
            new String[]{columnName},
            AccountEntry.COLUMN_UID + " = ?",
            new String[]{recordUID}, null, null, null);

        try {
            if (cursor.moveToFirst())
                return cursor.getString(cursor.getColumnIndexOrThrow(columnName));
            else {
                throw new IllegalArgumentException(String.format("Record with GUID %s does not exist in the db", recordUID));
            }
        } finally {
            cursor.close();
        }
    }

    /**
     * Returns the number of records in the database table backed by this adapter
     *
     * @return Total number of records in the database
     */
    public long getRecordsCount() {
        return DatabaseUtils.queryNumEntries(mDb, mTableName);
    }

    /**
     * Expose mDb.beginTransaction()
     */
    public void beginTransaction() {
        mDb.beginTransaction();
    }

    /**
     * Expose mDb.setTransactionSuccessful()
     */
    public void setTransactionSuccessful() {
        mDb.setTransactionSuccessful();
    }

    /// Foreign key constraits should be enabled in general.
    /// But if it affects speed (check constraints takes time)
    /// and the constrained can be assured by the program,
    /// or if some SQL exec will cause deletion of records
    /// (like use replace in accounts update will delete all transactions)
    /// that need not be deleted, then it can be disabled temporarily
    public void enableForeignKey(boolean enable) {
        if (enable) {
            mDb.execSQL("PRAGMA foreign_keys=ON;");
        } else {
            mDb.execSQL("PRAGMA foreign_keys=OFF;");
        }
    }

    /**
     * Expose mDb.endTransaction()
     */
    public void endTransaction() {
        try {
            mDb.endTransaction();
        } catch (Exception e) {
            Timber.e(e);
        }
    }

    @Override
    public void close() throws IOException {
        if (mInsertStatement != null) {
            mInsertStatement.close();
            mInsertStatement = null;
        }
        if (mReplaceStatement != null) {
            mReplaceStatement.close();
            mReplaceStatement = null;
        }
        if (mUpdateStatement != null) {
            mUpdateStatement.close();
            mUpdateStatement = null;
        }
        if (mDb.isOpen()) {
            mDb.close();
        }
        cache.clear();
    }

    public void closeQuietly() {
        try {
            close();
        } catch (IOException ignore) {
        }
    }

    public String getUID(Model model) {
        return model.getUID();
    }
}
