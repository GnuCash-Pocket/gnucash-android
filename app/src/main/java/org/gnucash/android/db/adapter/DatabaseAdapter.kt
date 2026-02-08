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
package org.gnucash.android.db.adapter

import android.content.ContentValues
import android.content.SharedPreferences
import android.database.Cursor
import android.database.DatabaseUtils
import android.database.SQLException
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteStatement
import org.gnucash.android.db.BookDbHelper.Companion.getBookPreferences
import org.gnucash.android.db.DatabaseHolder
import org.gnucash.android.db.DatabaseSchema.CommonColumns
import org.gnucash.android.db.forEach
import org.gnucash.android.db.getTimestamp
import org.gnucash.android.model.BaseModel
import org.gnucash.android.util.set
import timber.log.Timber
import java.io.Closeable
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/**
 * Adapter to be used for creating and opening the database for read/write operations.
 * The adapter abstracts several methods for database access and should be subclassed
 * by any other adapters to database-backed data models.
 *
 * @author Ngewi Fet <ngewif@gmail.com>
 */
abstract class DatabaseAdapter<Model : BaseModel>(
    /**
     * SQLite database
     */
    val holder: DatabaseHolder,
    protected val tableName: String,
    protected val columns: Array<String>,
    protected val isCached: Boolean = false
) : Closeable {
    protected val db: SQLiteDatabase

    @Volatile
    private var _replaceStatement: SQLiteStatement? = null

    @Volatile
    private var _updateStatement: SQLiteStatement? = null

    @Volatile
    private var _insertStatement: SQLiteStatement? = null

    protected val cache: MutableMap<String, Model> = ConcurrentHashMap<String, Model>()

    enum class UpdateMethod {
        Insert,
        Update,
        Replace
    }

    /**
     * Opens the database adapter with an existing database
     *
     * @param holder Database holder
     */
    init {
        val db = holder.db
        this.db = db
        require(db.isOpen) { "Database not open." }
        require(!db.isReadOnly) { "Database read-only. Writeable database required!" }
    }

    /**
     * Checks if the database is open
     *
     * @return `true` if the database is open, `false` otherwise
     */
    val isOpen: Boolean
        get() = db.isOpen

    internal val allColumns: Array<String> = columns +
            CommonColumns.COLUMN_UID +
            CommonColumns.COLUMN_ID +
            CommonColumns.COLUMN_CREATED_AT +
            CommonColumns.COLUMN_MODIFIED_AT


    internal val INDEX_COLUMN_UID = columns.size
    internal val INDEX_COLUMN_ID = INDEX_COLUMN_UID + 1
    internal val INDEX_COLUMN_CREATED_AT = INDEX_COLUMN_ID + 1
    internal val INDEX_COLUMN_MODIFIED_AT = INDEX_COLUMN_CREATED_AT + 1

    /**
     * Adds a record to the database with the data contained in the model.
     *
     * This method uses the SQL REPLACE instructions to replace any record with a matching GUID.
     * So beware of any foreign keys with cascade dependencies which might need to be re-added
     *
     * @param model Model to be saved to the database
     */
    @Throws(SQLException::class)
    fun addRecord(model: Model): Model {
        return addRecord(model, UpdateMethod.Replace)
    }

    /**
     * Add a model record to the database.
     *
     * If unsure about which `updateMethod` to use, use [UpdateMethod.Replace]
     *
     * @param model        Subclass of [BaseModel] to be added
     * @param updateMethod Method to use for adding the record
     */
    @Throws(SQLException::class)
    open fun addRecord(model: Model, updateMethod: UpdateMethod): Model {
        Timber.d(
            "Adding record to database: %s %s",
            model.javaClass.getSimpleName(),
            model.toString()
        )
        val statement: SQLiteStatement
        when (updateMethod) {
            UpdateMethod.Insert -> {
                statement = insertStatement
                synchronized(statement) {
                    model.id = bind(statement, model).executeInsert()
                }
            }

            UpdateMethod.Update -> {
                statement = updateStatement
                synchronized(statement) {
                    bind(statement, model).execute()
                }
            }

            else -> {
                statement = replaceStatement
                synchronized(statement) {
                    model.id = bind(statement, model).executeInsert()
                }
            }
        }
        if (isCached) cache[model.uid] = model

        return model
    }

    /**
     * Persist the model object to the database as records using the `updateMethod`
     *
     * @param models       List of records
     * @param updateMethod Method to use when persisting them
     * @return Number of rows affected in the database
     */
    @Throws(SQLException::class)
    private fun doAddModels(models: List<Model>, updateMethod: UpdateMethod): Long {
        var nRow: Long = 0
        val statement: SQLiteStatement
        when (updateMethod) {
            UpdateMethod.Update -> {
                statement = updateStatement
                synchronized(statement) {
                    for (model in models) {
                        bind(statement, model).execute()
                        nRow++
                    }
                }
            }

            UpdateMethod.Insert -> {
                statement = insertStatement
                synchronized(statement) {
                    for (model in models) {
                        model.id = bind(statement, model).executeInsert()
                        nRow++
                    }
                }
            }

            else -> {
                statement = replaceStatement
                synchronized(statement) {
                    for (model in models) {
                        model.id = bind(statement, model).executeInsert()
                        nRow++
                    }
                }
            }
        }
        return nRow
    }

    /**
     * Add multiple records to the database at once
     *
     * Either all or none of the records will be inserted/updated into the database.
     *
     * @param models List of model records
     * @return Number of rows inserted
     */
    fun bulkAddRecords(models: List<Model>): Long {
        return bulkAddRecords(models, UpdateMethod.Replace)
    }

    @Throws(SQLException::class)
    open fun bulkAddRecords(models: List<Model>, updateMethod: UpdateMethod): Long {
        if (models.isEmpty()) {
            Timber.w("Empty model list. Cannot bulk add records, returning 0")
            return 0
        }

        Timber.i(
            "Bulk adding %d %s records to the database",
            models.size,
            models[0].javaClass.getSimpleName()
        )
        var nRow: Long
        try {
            beginTransaction()
            nRow = doAddModels(models, updateMethod)
            setTransactionSuccessful()
            if (isCached) {
                cache.clear()
            }
        } finally {
            endTransaction()
        }

        return nRow
    }

    /**
     * Builds an instance of the model from the database record entry
     *
     * When implementing this method, remember to call [.populateBaseModelAttributes]
     *
     * @param cursor Cursor pointing to the record
     * @return New instance of the model from database record
     */
    abstract fun buildModelInstance(cursor: Cursor): Model

    /**
     * Generates an [SQLiteStatement] with values from the `model`.
     * This statement can be executed to replace a record in the database.
     *
     * If the [.mReplaceStatement] is null, subclasses should create a new statement and return.<br></br>
     * If it is not null, the previous bindings will be cleared and replaced with those from the model
     *
     * @return SQLiteStatement for replacing a record in the database
     */
    protected val replaceStatement: SQLiteStatement
        get() {
            var stmt = _replaceStatement
            if (stmt == null) {
                synchronized(this) {
                    stmt = _replaceStatement
                    if (stmt == null) {
                        val sql = buildReplaceStatement()
                        stmt = db.compileStatement(sql)
                        _replaceStatement = stmt
                    }
                }
            }
            return stmt!!
        }

    private fun buildReplaceStatement(): String {
        val columnsCount = columns.size
        val sql = StringBuilder("REPLACE INTO ").append(tableName).append(" (")
        for (i in 0 until columnsCount) {
            sql.append(columns[i]).append(',')
        }
        sql.append(CommonColumns.COLUMN_UID)
            .append(") VALUES (")
        for (i in 0 until columnsCount) {
            sql.append("?,")
        }
        sql.append("?)")
        return sql.toString()
    }

    protected val updateStatement: SQLiteStatement
        get() {
            var stmt = _updateStatement
            if (stmt == null) {
                synchronized(this) {
                    stmt = _updateStatement
                    if (stmt == null) {
                        val sql = buildUpdateStatement()
                        stmt = db.compileStatement(sql)
                        _updateStatement = stmt
                    }
                }
            }
            return stmt!!
        }

    private fun buildUpdateStatement(): String {
        val columnsCount = columns.size
        val sql = StringBuilder()
            .append("UPDATE ").append(tableName).append(" SET ")
        for (i in 0 until columnsCount) {
            sql.append(columns[i]).append("=?,")
        }
        sql.deleteCharAt(sql.lastIndex) //delete the last ","
        sql.append(" WHERE ").append(CommonColumns.COLUMN_UID).append("=?")
        return sql.toString()
    }

    protected val insertStatement: SQLiteStatement
        get() {
            var stmt = _insertStatement
            if (stmt == null) {
                synchronized(this) {
                    stmt = _insertStatement
                    if (stmt == null) {
                        val sql = buildInsertStatement()
                        stmt = db.compileStatement(sql)
                        _insertStatement = stmt
                    }
                }
            }
            return stmt!!
        }

    private fun buildInsertStatement(): String {
        val columnsCount = columns.size
        val sql = StringBuilder("INSERT INTO ").append(tableName).append(" (")
        for (i in 0 until columnsCount) {
            sql.append(columns[i]).append(',')
        }
        sql.append(CommonColumns.COLUMN_UID)
            .append(") VALUES (")
        for (i in 0 until columnsCount) {
            sql.append("?,")
        }
        sql.append("?)")
        return sql.toString()
    }

    /**
     * Binds the values from the model the the SQL statement
     *
     * @param stmt  SQL statement with placeholders
     * @param model Model from which to read bind attributes
     * @return SQL statement ready for execution
     */
    @Throws(SQLException::class)
    protected abstract fun bind(stmt: SQLiteStatement, model: Model): SQLiteStatement

    protected fun bindBaseModel(stmt: SQLiteStatement, model: Model) {
        stmt.clearBindings()
        stmt.bindString(1 + INDEX_COLUMN_UID, model.uid)
    }

    fun getRecordOrNull(uid: String): Model? {
        if (isCached) {
            val model = cache[uid]
            if (model != null) return model
        }
        Timber.v("Fetching record from %s with UID %s", tableName, uid)
        val cursor = fetchRecord(uid) ?: return null
        try {
            if (cursor.moveToFirst()) {
                val model = try {
                    buildModelInstance(cursor)
                } catch (_: IllegalArgumentException) {
                    return null
                }
                if (isCached) {
                    cache[uid] = model
                }
                return model
            }
        } finally {
            cursor.close()
        }
        return null
    }

    /**
     * Returns a model instance populated with data from the record with GUID `uid`
     *
     * Sub-classes which require special handling should override this method
     *
     * @param uid GUID of the record
     * @return BaseModel instance of the record
     * @throws IllegalArgumentException if the record UID does not exist in thd database
     */
    @Throws(IllegalArgumentException::class)
    fun getRecord(uid: String): Model {
        val model = getRecordOrNull(uid)
        requireNotNull(model) { "Record not found in $tableName" }
        return model
    }

    /**
     * Overload of [.getRecord]
     * Simply converts the record ID to a GUID and calls [.getRecord]
     *
     * @param id Database record ID
     * @return Subclass of [BaseModel] containing record info
     */
    @Throws(IllegalArgumentException::class)
    fun getRecord(id: Long): Model {
        return getRecord(getUID(id))
    }

    /**
     * Returns all the records in the database
     *
     * @return List of records in the database
     */
    open val allRecords: List<Model>
        get() = getAllRecords(null, null)

    fun getAllRecords(
        where: String?,
        whereArgs: Array<String?>?,
        orderBy: String? = null
    ): List<Model> {
        val cursor = fetchAllRecords(where, whereArgs, orderBy)
        return getRecords(cursor)
    }

    protected fun getRecords(cursor: Cursor?): List<Model> {
        if (cursor == null) return emptyList()

        val records = mutableListOf<Model>()
        cursor.forEach { cursor ->
            val model = buildModelInstance(cursor)
            records.add(model)
            if (isCached) {
                cache[model.uid] = model
            }
        }
        return records
    }

    /**
     * Extracts the attributes of the base model and adds them to the ContentValues object provided
     *
     * @param contentValues Content values to which to add attributes
     * @param model         [BaseModel] from which to extract values
     * @return [ContentValues] with the data to be inserted into the db
     */
    protected fun extractBaseModelAttributes(
        contentValues: ContentValues,
        model: Model
    ): ContentValues {
        contentValues[CommonColumns.COLUMN_UID] = model.uid
        contentValues[CommonColumns.COLUMN_CREATED_AT] = model.createdTimestamp
        //there is a trigger in the database for updated the modified_at column
        /* Due to the use of SQL REPLACE syntax, we insert the created_at values each time
         * (maintain the original creation time and not the time of creation of the replacement)
         * The updated_at column has a trigger in the database which will update the column
         */
        return contentValues
    }

    /**
     * Initializes the model with values from the database record common to all models (i.e. in the BaseModel)
     *
     * @param cursor Cursor pointing to database record
     * @param model  Model instance to be initialized
     */
    protected fun populateBaseModelAttributes(
        cursor: Cursor,
        model: BaseModel,
        columnOffset: Int = 0
    ) {
        val id = cursor.getLong(INDEX_COLUMN_ID + columnOffset)
        val uid = cursor.getString(INDEX_COLUMN_UID + columnOffset)!!
        val created = cursor.getTimestamp(INDEX_COLUMN_CREATED_AT + columnOffset)!!
        val modified = cursor.getTimestamp(INDEX_COLUMN_MODIFIED_AT + columnOffset)!!

        model.id = id
        model.setUID(uid)
        model.createdTimestamp = created
        model.modifiedTimestamp = modified
    }

    /**
     * Retrieves record with GUID `uid` from database table
     *
     * @param uid GUID of record to be retrieved
     * @return [Cursor] to record retrieved
     */
    fun fetchRecord(uid: String): Cursor? {
        require(!uid.isEmpty()) { "UID required" }
        val where = CommonColumns.COLUMN_UID + "=?"
        val whereArgs = arrayOf<String?>(uid)
        return db.query(tableName, allColumns, where, whereArgs, null, null, null)
    }

    /**
     * Retrieves all records from database table
     *
     * @return [Cursor] to all records in table `tableName`
     */
    open fun fetchAllRecords(): Cursor {
        return fetchAllRecords(null, null, null)
    }

    /**
     * Fetch all records from database matching conditions
     *
     * @param where     SQL where clause
     * @param whereArgs String arguments for where clause
     * @param orderBy   SQL orderby clause
     * @return Cursor to records matching conditions
     */
    open fun fetchAllRecords(where: String?, whereArgs: Array<String?>?, orderBy: String?): Cursor {
        Timber.v(
            "Fetching all accounts from db where %s/%s order by %s",
            where,
            whereArgs.contentToString(),
            orderBy
        )
        return db.query(tableName, allColumns, where, whereArgs, null, null, orderBy)
    }

    /**
     * Deletes record with ID `rowID` from database table.
     *
     * @param rowId ID of record to be deleted
     * @return `true` if deletion was successful, `false` otherwise
     */
    @Throws(SQLException::class)
    open fun deleteRecord(rowId: Long): Boolean {
        Timber.d("Deleting record with id %d from %s", rowId, tableName)
        val where = CommonColumns.COLUMN_ID + "=" + rowId
        return db.delete(tableName, where, null) > 0
    }

    /**
     * Deletes all records in the database
     *
     * @return Number of deleted records
     */
    open fun deleteAllRecords(): Int {
        cache.clear()
        return db.delete(tableName, null, null)
    }

    /**
     * Returns the string unique ID (GUID) of a record in the database
     *
     * @param uid GUID of the record
     * @return Long record ID
     * @throws IllegalArgumentException if the GUID does not exist in the database
     */
    @Throws(IllegalArgumentException::class)
    fun getID(uid: String): Long {
        if (isCached) {
            val model = cache[uid]
            if (model != null) return model.id
        }
        val cursor = db.query(
            tableName,
            arrayOf<String?>(CommonColumns.COLUMN_ID),
            CommonColumns.COLUMN_UID + " = ?",
            arrayOf<String?>(uid),
            null, null, null
        )
        val result: Long
        try {
            if (cursor.moveToFirst()) {
                result = cursor.getLong(0)
            } else {
                throw IllegalArgumentException("Record not found in $tableName")
            }
        } finally {
            cursor.close()
        }
        return result
    }

    /**
     * Returns the string unique ID (GUID) of a record in the database
     *
     * @param id long database record ID
     * @return GUID of the record
     * @throws IllegalArgumentException if the record ID does not exist in the database
     */
    @Throws(IllegalArgumentException::class)
    fun getUID(id: Long): String {
        if (isCached) {
            for (model in cache.values) {
                if (model.id == id) {
                    return model.uid
                }
            }
        }
        val cursor = db.query(
            tableName,
            arrayOf<String?>(CommonColumns.COLUMN_UID),
            CommonColumns.COLUMN_ID + " = " + id,
            null, null, null, null
        )

        try {
            if (cursor.moveToFirst()) {
                return cursor.getString(0)
            }
            throw IllegalArgumentException("Record not found in $tableName")
        } finally {
            cursor.close()
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
    protected fun updateRecord(
        tableName: String,
        recordId: Long,
        columnKey: String,
        newValue: String?
    ): Int {
        if (isCached) {
            var uid: String? = null
            for (model in cache.values) {
                if (model.id == recordId) {
                    uid = model.uid
                    break
                }
            }
            if (uid != null) cache.remove(uid)
        }
        val contentValues = ContentValues()
        contentValues[columnKey] = newValue
        return db.update(
            tableName,
            contentValues,
            CommonColumns.COLUMN_ID + "=" + recordId,
            null
        )
    }

    /**
     * Updates a record in the table
     *
     * @param uid       GUID of the record
     * @param columnKey Name of column to be updated
     * @param newValue  New value to be assigned to the columnKey
     * @return Number of records affected
     */
    fun updateRecord(uid: String, columnKey: String, newValue: String?): Int {
        val contentValues = ContentValues()
        contentValues[columnKey] = newValue
        return updateRecord(uid, contentValues)
    }

    /**
     * Overloaded method. Updates the record with GUID `uid` with the content values
     *
     * @param uid           GUID of the record
     * @param contentValues Content values to update
     * @return Number of records updated
     */
    fun updateRecord(uid: String, contentValues: ContentValues): Int {
        if (isCached) cache.remove(uid)
        return updateRecord(tableName, uid, contentValues)
    }

    /**
     * Overloaded method. Updates the record with GUID `uid` with the content values
     *
     * @param tableName     The table name.
     * @param uid           GUID of the record
     * @param contentValues Content values to update
     * @return Number of records updated
     */
    protected fun updateRecord(tableName: String, uid: String, contentValues: ContentValues): Int {
        return db.update(
            tableName,
            contentValues,
            CommonColumns.COLUMN_UID + "=?",
            arrayOf<String?>(uid)
        )
    }

    @Throws(SQLException::class)
    fun insert(model: Model): Model {
        return addRecord(model, UpdateMethod.Insert)
    }

    @Throws(SQLException::class)
    fun replace(model: Model): Model {
        return addRecord(model, UpdateMethod.Replace)
    }

    @Throws(SQLException::class)
    fun update(model: Model): Model {
        return addRecord(model, UpdateMethod.Update)
    }

    /**
     * Updates all records which match the `where` clause with the `newValue` for the column
     *
     * @param where     SQL where clause
     * @param whereArgs String arguments for where clause
     * @param columnKey Name of column to be updated
     * @param newValue  New value to be assigned to the columnKey
     * @return Number of records affected
     */
    fun updateRecords(
        where: String?,
        whereArgs: Array<String?>?,
        columnKey: String,
        newValue: String?
    ): Int {
        val contentValues = ContentValues()
        contentValues[columnKey] = newValue
        return db.update(tableName, contentValues, where, whereArgs)
    }

    /**
     * Deletes a record from the database given its unique identifier.
     *
     * Overload of the method [.deleteRecord]
     *
     * @param uid GUID of the record
     * @return `true` if deletion was successful, `false` otherwise
     * @see .deleteRecord
     */
    @Throws(SQLException::class)
    open fun deleteRecord(uid: String): Boolean {
        if (isCached) cache.remove(uid)
        try {
            return deleteRecord(getID(uid))
        } catch (e: IllegalArgumentException) {
            Timber.e(e)
            return false
        }
    }

    @Throws(SQLException::class)
    fun deleteRecord(model: Model): Boolean {
        if (deleteRecord(model.id)) {
            if (isCached) cache.remove(model.uid)
            model.id = 0L
            return true
        }
        return false
    }

    /**
     * Returns an attribute from a specific column in the database for a specific record.
     *
     * The attribute is returned as a string which can then be converted to another type if
     * the caller was expecting something other type
     *
     * @param recordUID  GUID of the record
     * @param columnName Name of the column to be retrieved
     * @return String value of the column entry
     * @throws IllegalArgumentException if either the `recordUID` or `columnName` do not exist in the database
     */
    fun getAttribute(recordUID: String, columnName: String): String {
        return getAttribute(tableName, recordUID, columnName)
    }

    /**
     * Returns an attribute from a specific column in the database for a specific record.
     *
     * The attribute is returned as a string which can then be converted to another type if
     * the caller was expecting something other type
     *
     * @param model      the record with a GUID.
     * @param columnName Name of the column to be retrieved
     * @return String value of the column entry
     * @throws IllegalArgumentException if either the `recordUID` or `columnName` do not exist in the database
     */
    fun getAttribute(model: Model, columnName: String): String {
        return getAttribute(tableName, getUID(model), columnName)
    }

    /**
     * Returns an attribute from a specific column in the database for a specific record and specific table.
     *
     * The attribute is returned as a string which can then be converted to another type if
     * the caller was expecting something other type
     *
     * This method is an override of [.getAttribute] which allows to select a value from a
     * different table than the one of current adapter instance
     *
     *
     * @param tableName  Database table name. See [DatabaseSchema]
     * @param recordUID  GUID of the record
     * @param columnName Name of the column to be retrieved
     * @return String value of the column entry
     * @throws IllegalArgumentException if either the `recordUID` or `columnName` do not exist in the database
     */
    @Throws(IllegalArgumentException::class)
    protected fun getAttribute(tableName: String, recordUID: String, columnName: String): String {
        val cursor = db.query(
            tableName,
            arrayOf<String?>(columnName),
            CommonColumns.COLUMN_UID + " = ?",
            arrayOf<String?>(recordUID), null, null, null
        )

        try {
            if (cursor.moveToFirst()) {
                return cursor.getString(0)
            }
            throw IllegalArgumentException("Record not found in $tableName with column '$columnName'")
        } finally {
            cursor.close()
        }
    }

    /**
     * Returns the number of records in the database table backed by this adapter
     *
     * @return Total number of records in the database
     */
    open val recordsCount: Long
        get() = getRecordsCount(null, null)

    /**
     * Returns the number of transactions in the database which fulfill the conditions
     *
     * @param where     SQL WHERE clause without the "WHERE" itself
     * @param whereArgs Arguments to substitute question marks for
     * @return Number of records in the databases
     */
    open fun getRecordsCount(where: String?, whereArgs: Array<String?>?): Long {
        return DatabaseUtils.queryNumEntries(db, tableName, where, whereArgs)
    }

    /**
     * Expose mDb.beginTransaction()
     */
    fun beginTransaction() {
        db.beginTransaction()
    }

    /**
     * Expose mDb.setTransactionSuccessful()
     */
    fun setTransactionSuccessful() {
        db.setTransactionSuccessful()
    }

    /**
     * Expose mDb.endTransaction()
     */
    fun endTransaction() {
        try {
            db.endTransaction()
        } catch (e: Exception) {
            Timber.e(e)
        }
    }

    @Throws(IOException::class)
    override fun close() {
        if (_insertStatement != null) {
            _insertStatement!!.close()
            _insertStatement = null
        }
        if (_replaceStatement != null) {
            _replaceStatement!!.close()
            _replaceStatement = null
        }
        if (_updateStatement != null) {
            _updateStatement!!.close()
            _updateStatement = null
        }
        if (db.isOpen) {
            db.close()
        }
        cache.clear()
    }

    fun closeQuietly() {
        try {
            close()
        } catch (_: Exception) {
        }
    }

    fun getUID(model: Model): String {
        return model.uid
    }

    /**
     * Return the [SharedPreferences] for a specific book
     *
     * @return Shared preferences
     */
    val bookPreferences: SharedPreferences
        get() = getBookPreferences(holder)

    internal fun allColumnsPrefix(prefix: String): Array<String> {
        return allColumns.map { prefix + it }.toTypedArray()
    }
}
