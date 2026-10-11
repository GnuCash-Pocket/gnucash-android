/*
 * Copyright (c) 2015 Ngewi Fet <ngewif@gmail.com>
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

import android.content.Context
import android.database.Cursor
import android.database.DatabaseUtils
import android.database.sqlite.SQLiteStatement
import androidx.annotation.VisibleForTesting
import androidx.core.content.edit
import androidx.core.net.toUri
import org.gnucash.android.R
import org.gnucash.android.app.GnuCashApplication
import org.gnucash.android.app.GnuCashApplication.Companion.getBookPreferences
import org.gnucash.android.db.BookDbHelper
import org.gnucash.android.db.DatabaseHelper
import org.gnucash.android.db.DatabaseHolder
import org.gnucash.android.db.DatabaseSchema.BookEntry
import org.gnucash.android.db.NoActiveBookException
import org.gnucash.android.db.bindStringOrNull
import org.gnucash.android.db.bindTimestamp
import org.gnucash.android.db.forEach
import org.gnucash.android.db.getTimestamp
import org.gnucash.android.model.Book
import org.gnucash.android.util.BookUtils
import org.gnucash.android.util.TimestampHelper.timestampFromEpochZero
import timber.log.Timber

/**
 * Database adapter for creating/modifying book entries
 */
class BooksDbAdapter(holder: DatabaseHolder) : DatabaseAdapter<Book>(
    holder,
    BookEntry.TABLE_NAME,
    entryColumns
) {
    override fun buildModelInstance(cursor: Cursor): Book {
        val rootAccountGUID = cursor.getString(INDEX_COLUMN_ROOT_GUID)!!
        val rootTemplateGUID = cursor.getString(INDEX_COLUMN_TEMPLATE_GUID)
        val uriString = cursor.getString(INDEX_COLUMN_SOURCE_URI)
        val displayName = cursor.getString(INDEX_COLUMN_DISPLAY_NAME)
        val lastSync = cursor.getTimestamp(INDEX_COLUMN_LAST_SYNC)

        val book = Book(rootAccountGUID)
        populateBaseModelAttributes(cursor, book)
        book.displayName = displayName
        book.rootTemplateUID = rootTemplateGUID
        book.sourceUri = uriString?.toUri()
        book.lastSync = lastSync ?: timestampFromEpochZero

        return book
    }

    override fun bind(stmt: SQLiteStatement, book: Book): SQLiteStatement {
        if (book.displayName.isNullOrEmpty()) {
            book.displayName = generateDefaultBookName()
        }
        bindBaseModel(stmt, book)
        stmt.bindString(1 + INDEX_COLUMN_DISPLAY_NAME, book.displayName)
        stmt.bindString(1 + INDEX_COLUMN_ROOT_GUID, book.rootAccountUID)
        stmt.bindString(1 + INDEX_COLUMN_TEMPLATE_GUID, book.rootTemplateUID)
        stmt.bindStringOrNull(1 + INDEX_COLUMN_SOURCE_URI, book.sourceUri?.toString())
        stmt.bindTimestamp(1 + INDEX_COLUMN_LAST_SYNC, book.lastSync)

        return stmt
    }


    /**
     * Deletes a book - removes the book record from the database and deletes the database file from the disk
     *
     * @param bookUID GUID of the book
     * @return `true` if deletion was successful, `false` otherwise
     * @see .deleteRecord
     */
    fun deleteBook(context: Context, bookUID: String): Boolean {
        var result = context.deleteDatabase(bookUID)
        if (result)  //delete the db entry only if the file deletion was successful
            result = result && deleteRecord(bookUID)

        getBookPreferences(context, bookUID).edit { clear() }

        return result
    }

    /**
     * Returns the GUID of the current active book
     *
     * @return GUID of the active book
     * @throws NoActiveBookException
     */
    @get:Throws(NoActiveBookException::class)
    @Deprecated("Book UID in shared preferences")
    val activeBookUID: String
        get() {
            db.query(
                tableName,
                arrayOf<String?>(BookEntry.COLUMN_UID),
                BookEntry.COLUMN_ACTIVE + " = 1",
                null,
                null,
                null,
                null,
                "1"
            ).use { cursor ->
                if (cursor.moveToFirst()) {
                    return cursor.getString(0)
                }
                val e = NoActiveBookException(
                    "There is no active book in the app.\n"
                            + "This should NEVER happen - fix your bugs!\n"
                            + this.noActiveBookFoundExceptionInfo
                )
                throw e
            }
        }

    internal val noActiveBookFoundExceptionInfo: String
        get() {
            val info = StringBuilder("UID, created, source\n")
            for (book in allRecords) {
                info.append(
                    String.format("%s, %s, %s", book.uid, book.createdTimestamp, book.sourceUri)
                ).append('\n')
            }
            return info.toString()
        }

    val activeBook: Book
        get() = getRecord(GnuCashApplication.activeBookUID)

    /**
     * Tries to fix the books database.
     *
     * @return the active book UID.
     */
    fun fixBooksDatabase(): String {
        Timber.v("Looking for books to set as active...")
        var books = allRecords
        if (books.isEmpty()) {
            Timber.w("No books found in the database. Recovering books records...")
            recoverBookRecords()
            books = allRecords
            if (books.isEmpty()) {
                return insertBlankBook().uid
            }
        } else {
            Timber.w("Activating last book...")
            GnuCashApplication.activeBookUID = books.last().uid
        }
        return books.last().uid
    }

    /**
     * Restores the records in the book database.
     *
     *
     * Does so by looking for database files from books.
     */
    private fun recoverBookRecords() {
        val context: Context = holder.context
        var activeUID = ""
        for (dbName in this.bookDatabases) {
            val rootAccountUID = getRootAccountUID(dbName)
            var book = Book(rootAccountUID).apply {
                setUID(dbName)
                BookUtils.populateName(context, this@BooksDbAdapter, this)
            }
            book = addRecord(book)
            activeUID = book.uid
            Timber.i("Recovered book record: %s", book.uid)
        }
        GnuCashApplication.activeBookUID = activeUID
    }

    private fun insertBlankBook(): Book {
        val book = Book()
        //TODO insert root accounts
        return insert(book)
    }

    /**
     * Returns the root account UID from the database with name dbName.
     */
    private fun getRootAccountUID(dbName: String): String {
        val context = holder.context
        val databaseHelper = DatabaseHelper(context, dbName)
        val holder = databaseHelper.holder
        val accountsDbAdapter = AccountsDbAdapter(holder)
        val uid = accountsDbAdapter.rootAccountUID
        databaseHelper.close()
        return uid
    }

    /**
     * Returns a list of database names corresponding to book databases.
     */
    private val bookDatabases: List<String>
        get() {
            val context = holder.context
            val bookDatabases = mutableListOf<String>()
            for (database in context.databaseList()) {
                if (isBookDatabase(database)) {
                    bookDatabases.add(database)
                }
            }
            return bookDatabases
        }

    val allBookUIDs: List<String>
        get() {
            val bookUIDs = mutableListOf<String>()
            db.query(
                true, tableName, arrayOf<String?>(BookEntry.COLUMN_UID),
                null, null, null, null, null, null
            ).forEach { cursor ->
                bookUIDs.add(cursor.getString(0))
            }
            return bookUIDs
        }

    /**
     * Generates a new default name for a new book
     *
     * @return String with default name
     */
    fun generateDefaultBookName(): String {
        var bookCount = DatabaseUtils.queryNumEntries(db, tableName) + 1

        val sql = "SELECT COUNT(*) FROM $tableName WHERE ${BookEntry.COLUMN_DISPLAY_NAME} = ?"
        val statement = db.compileStatement(sql)
        val context = holder.context

        while (true) {
            val name = context.getString(R.string.book_default_name, bookCount)

            statement.bindString(1, name)
            val nameCount = statement.simpleQueryForLong()

            if (nameCount == 0L) {
                statement.close()
                return name
            }

            bookCount++
        }
    }

    companion object {
        private val entryColumns = arrayOf(
            BookEntry.COLUMN_DISPLAY_NAME,
            BookEntry.COLUMN_ROOT_GUID,
            BookEntry.COLUMN_TEMPLATE_GUID,
            BookEntry.COLUMN_SOURCE_URI,
            BookEntry.COLUMN_LAST_SYNC
        )
        private const val INDEX_COLUMN_DISPLAY_NAME = 0
        private const val INDEX_COLUMN_ROOT_GUID = INDEX_COLUMN_DISPLAY_NAME + 1
        private const val INDEX_COLUMN_TEMPLATE_GUID = INDEX_COLUMN_ROOT_GUID + 1
        private const val INDEX_COLUMN_SOURCE_URI = INDEX_COLUMN_TEMPLATE_GUID + 1
        private const val INDEX_COLUMN_LAST_SYNC = INDEX_COLUMN_SOURCE_URI + 1

        lateinit var instance: BooksDbAdapter

        fun init(context: Context): BooksDbAdapter {
            val dbHelper = BookDbHelper(context)
            val holder = dbHelper.getHolder()
            val booksDbAdapter = BooksDbAdapter(holder)
            instance = booksDbAdapter
            return booksDbAdapter
        }

        fun close() {
            try {
                instance.close()
            } catch (_: Exception) {
            }
        }

        @VisibleForTesting
        fun isBookDatabase(databaseName: String): Boolean {
            return databaseName.matches("[a-z0-9]{32}".toRegex()) // UID regex
        }
    }
}
