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

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteStatement
import android.provider.BaseColumns
import androidx.annotation.VisibleForTesting
import androidx.core.content.edit
import androidx.core.net.toUri
import org.gnucash.android.R
import org.gnucash.android.app.GnuCashApplication
import org.gnucash.android.app.GnuCashApplication.Companion.appContext
import org.gnucash.android.app.GnuCashApplication.Companion.getBookPreferences
import org.gnucash.android.db.DatabaseHelper
import org.gnucash.android.db.DatabaseHolder
import org.gnucash.android.db.DatabaseSchema.BookEntry
import org.gnucash.android.db.bindBoolean
import org.gnucash.android.db.forEach
import org.gnucash.android.db.getInt
import org.gnucash.android.db.getString
import org.gnucash.android.model.Book
import org.gnucash.android.util.TimestampHelper
import org.gnucash.android.util.TimestampHelper.getTimestampFromUtcString
import org.gnucash.android.util.set
import timber.log.Timber
import kotlin.math.max

/**
 * Database adapter for creating/modifying book entries
 */
class BooksDbAdapter(holder: DatabaseHolder) : DatabaseAdapter<Book>(
    holder,
    BookEntry.TABLE_NAME,
    arrayOf(
        BookEntry.COLUMN_DISPLAY_NAME,
        BookEntry.COLUMN_ROOT_GUID,
        BookEntry.COLUMN_TEMPLATE_GUID,
        BookEntry.COLUMN_SOURCE_URI,
        BookEntry.COLUMN_ACTIVE,
        BookEntry.COLUMN_LAST_SYNC
    )
) {
    override fun buildModelInstance(cursor: Cursor): Book {
        val rootAccountGUID = cursor.getString(BookEntry.COLUMN_ROOT_GUID)
        val rootTemplateGUID = cursor.getString(BookEntry.COLUMN_TEMPLATE_GUID)
        val uriString = cursor.getString(BookEntry.COLUMN_SOURCE_URI)
        val displayName = cursor.getString(BookEntry.COLUMN_DISPLAY_NAME)
        val active = cursor.getInt(BookEntry.COLUMN_ACTIVE)
        val lastSync = cursor.getString(BookEntry.COLUMN_LAST_SYNC)!!

        val book = Book(rootAccountGUID)
        populateBaseModelAttributes(cursor, book)
        book.displayName = displayName
        book.rootTemplateUID = rootTemplateGUID
        book.sourceUri = uriString?.toUri()
        book.isActive = active != 0
        book.lastSync = getTimestampFromUtcString(lastSync)

        return book
    }

    override fun bind(stmt: SQLiteStatement, book: Book): SQLiteStatement {
        if (book.displayName.isNullOrEmpty()) {
            book.displayName = generateDefaultBookName()
        }
        bindBaseModel(stmt, book)
        stmt.bindString(1, book.displayName)
        stmt.bindString(2, book.rootAccountUID)
        stmt.bindString(3, book.rootTemplateUID)
        if (book.sourceUri != null) {
            stmt.bindString(4, book.sourceUri.toString())
        }
        stmt.bindBoolean(5, book.isActive)
        stmt.bindString(6, TimestampHelper.getUtcStringFromTimestamp(book.lastSync!!))

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
     * Sets the book with unique identifier `uid` as active and all others as inactive
     *
     * If the parameter is null, then the currently active book is not changed
     *
     * @param bookUID Unique identifier of the book
     * @return GUID of the currently active book
     */
    fun setActive(bookUID: String): String {
        if (bookUID.isEmpty()) return this.activeBookUID

        val contentValues = ContentValues()
        contentValues[BookEntry.COLUMN_ACTIVE] = 0
        db.update(tableName, contentValues, null, null) //disable all

        contentValues.clear()
        contentValues[BookEntry.COLUMN_ACTIVE] = 1
        db.update(
            tableName,
            contentValues,
            BookEntry.COLUMN_UID + " = ?",
            arrayOf<String?>(bookUID)
        )

        return bookUID
    }

    /**
     * Checks if the book is active or not
     *
     * @param bookUID GUID of the book
     * @return `true` if the book is active, `false` otherwise
     */
    fun isActive(bookUID: String): Boolean {
        val isActive = getAttribute(bookUID, BookEntry.COLUMN_ACTIVE)
        return isActive.toInt() > 0
    }

    /**
     * Returns the GUID of the current active book
     *
     * @return GUID of the active book
     * @throws NoActiveBookFoundException
     */
    @get:Throws(NoActiveBookFoundException::class)
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
                val e = NoActiveBookFoundException(
                    ("There is no active book in the app.\n"
                            + "This should NEVER happen - fix your bugs!\n"
                            + this.noActiveBookFoundExceptionInfo)
                )
                // Timber.e(e);
                throw e
            }
        }

    private val noActiveBookFoundExceptionInfo: String
        get() {
            val info = StringBuilder("UID, created, source\n")
            for (book in allRecords) {
                info.append(
                    String.format(
                        "%s, %s, %s\n",
                        book.uid,
                        book.createdTimestamp,
                        book.sourceUri
                    )
                )
            }
            return info.toString()
        }

    val activeBook: Book
        get() = getRecord(this.activeBookUID)

    inner class NoActiveBookFoundException(message: String?) : RuntimeException(message)

    /**
     * Tries to fix the books database.
     *
     * @return the active book UID.
     */
    fun fixBooksDatabase(): String? {
        Timber.v("Looking for books to set as active...")
        if (recordsCount <= 0) {
            Timber.w("No books found in the database. Recovering books records...")
            recoverBookRecords()
            if (recordsCount <= 0) {
                return null
            }
        }
        return setFirstBookAsActive()
    }

    /**
     * Restores the records in the book database.
     *
     *
     * Does so by looking for database files from books.
     */
    private fun recoverBookRecords() {
        for (dbName in this.bookDatabases) {
            val book = Book(getRootAccountUID(dbName))
            book.setUID(dbName)
            book.displayName = generateDefaultBookName()
            addRecord(book)
            Timber.i("Recovered book record: %s", book.uid)
        }
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
     * Sets the first book in the database as active.
     *
     * @return the book UID.
     */
    private fun setFirstBookAsActive(): String? {
        val books = allRecords
        if (books.isEmpty()) {
            Timber.w("No books.")
            return null
        }
        val firstBook = books[0]
        firstBook.isActive = true
        addRecord(firstBook)
        Timber.i("Book %s set as active.", firstBook.uid)
        return firstBook.uid
    }

    /**
     * Returns a list of database names corresponding to book databases.
     */
    private val bookDatabases: List<String>
        get() {
            val bookDatabases = mutableListOf<String>()
            for (database in appContext.databaseList()) {
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
     * Return the name of the currently active book.
     * Or a generic name if there is no active book (should never happen)
     *
     * @return Display name of the book
     */
    val activeBookDisplayName: String
        get() {
            val cursor = db.query(
                tableName,
                arrayOf<String?>(BookEntry.COLUMN_DISPLAY_NAME),
                BookEntry.COLUMN_ACTIVE + " = 1",
                null,
                null,
                null,
                null
            )
            try {
                if (cursor.moveToFirst()) {
                    return cursor.getString(0)
                }
            } finally {
                cursor.close()
            }
            return "Book1"
        }

    /**
     * Generates a new default name for a new book
     *
     * @return String with default name
     */
    fun generateDefaultBookName(): String {
        val sqlMax = "SELECT MAX(" + BaseColumns._ID + ") FROM " + tableName
        val statementMax = db.compileStatement(sqlMax)
        var bookCount = max(statementMax.simpleQueryForLong(), 1L)

        val sql =
            "SELECT COUNT(*) FROM " + tableName + " WHERE " + BookEntry.COLUMN_DISPLAY_NAME + " = ?"
        val statement = db.compileStatement(sql)
        val context = appContext

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
        /**
         * Return the application instance of the books database adapter
         *
         * @return Books database adapter
         */
        val instance: BooksDbAdapter get() = GnuCashApplication.booksDbAdapter!!

        @VisibleForTesting
        fun isBookDatabase(databaseName: String): Boolean {
            return databaseName.matches("[a-z0-9]{32}".toRegex()) // UID regex
        }
    }
}
