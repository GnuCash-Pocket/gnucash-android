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
package org.gnucash.android.db

import android.content.Context
import android.content.SharedPreferences
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.gnucash.android.app.GnuCashApplication.Companion.appContext
import org.gnucash.android.db.DatabaseSchema.BookEntry
import org.gnucash.android.db.adapter.AccountsDbAdapter
import org.gnucash.android.db.adapter.BooksDbAdapter
import org.gnucash.android.db.adapter.CommoditiesDbAdapter
import org.gnucash.android.model.Book
import org.gnucash.android.model.Commodity
import java.io.File

/**
 * Database helper for managing database which stores information about the books in the application
 * This is a different database from the one which contains the accounts and transaction data because
 * there are multiple accounts/transactions databases in the system and this one will be used to
 * switch between them.
 */
class BookDbHelper(private val context: Context) : SQLiteOpenHelper(
    context,
    DatabaseSchema.BOOK_DATABASE_NAME,
    null,
    DatabaseSchema.BOOK_DATABASE_VERSION
) {
    private var holder: DatabaseHolder? = null

    fun getHolder(): DatabaseHolder {
        var holder: DatabaseHolder? = this.holder
        if (holder == null) {
            holder = DatabaseHolder(context, writableDatabase)
            this.holder = holder
        }
        return holder
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(BOOKS_TABLE_CREATE)
        insertBlankBook(appContext, db)
    }

    fun insertBlankBook(): Book {
        return insertBlankBook(getHolder())
    }

    fun insertBlankBook(context: Context, db: SQLiteDatabase): Book {
        return insertBlankBook(DatabaseHolder(context, db))
    }

    fun insertBlankBook(bookHolder : DatabaseHolder): Book {
        if (this.holder == null) {
            this.holder = bookHolder
        }
        val book = Book()
        val dbHelper = DatabaseHelper(context, book.uid)
        val dbHolder = dbHelper.holder
        val commoditiesDbAdapter = CommoditiesDbAdapter(dbHolder)
        Commodity.DEFAULT_COMMODITY = commoditiesDbAdapter.getDefaultCommodity()

        val accountsDbAdapter = AccountsDbAdapter(dbHolder)
        val rootAccountUID = accountsDbAdapter.rootAccountUID
        try {
            dbHelper.close()
        } catch (_: Exception) {
        }
        book.rootAccountUID = rootAccountUID
        book.isActive = true
        insertBook(bookHolder, book)
        return book
    }

    /**
     * Inserts the book into the database
     *
     * @param holder Database holder
     * @param book       Book to insert
     */
    private fun insertBook(holder: DatabaseHolder, book: Book) {
        val booksDbAdapter = BooksDbAdapter(holder)
        var name = book.displayName
        if (name.isNullOrEmpty()) {
            name = booksDbAdapter.generateDefaultBookName()
            book.displayName = name
        }
        booksDbAdapter.addRecord(book)
    }

    override fun onUpgrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {
        //nothing to see here yet, move along
    }

    companion object {
        /**
         * Create the books table
         */
        private val BOOKS_TABLE_CREATE = ("CREATE TABLE " + BookEntry.TABLE_NAME + " ("
                + BookEntry.COLUMN_ID + " integer primary key autoincrement, "
                + BookEntry.COLUMN_UID + " varchar(255) not null UNIQUE, "
                + BookEntry.COLUMN_DISPLAY_NAME + " varchar(255) not null, "
                + BookEntry.COLUMN_ROOT_GUID + " varchar(255) not null, "
                + BookEntry.COLUMN_TEMPLATE_GUID + " varchar(255), "
                + BookEntry.COLUMN_ACTIVE + " tinyint default 0, "
                + BookEntry.COLUMN_SOURCE_URI + " varchar(255), "
                + BookEntry.COLUMN_LAST_SYNC + " TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, "
                + BookEntry.COLUMN_CREATED_AT + " TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, "
                + BookEntry.COLUMN_MODIFIED_AT + " TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP "
                + ");"
                + DatabaseHelper.createUpdatedAtTrigger(BookEntry.TABLE_NAME))

        /**
         * Returns the database for the book
         *
         * @param bookUID GUID of the book
         * @return SQLiteDatabase of the book
         */
        fun getDatabase(bookUID: String): SQLiteDatabase {
            return getDatabase(appContext, bookUID)
        }

        /**
         * Returns the database for the book
         *
         * @param context The application context.
         * @param bookUID GUID of the book
         * @return SQLiteDatabase of the book
         */
        fun getDatabase(context: Context, bookUID: String): SQLiteDatabase {
            val dbHelper = DatabaseHelper(context, bookUID)
            return dbHelper.writableDatabase
        }

        fun getBookUID(db: SQLiteDatabase): String {
            val file = File(db.path)
            return file.name
        }

        /**
         * Return the [SharedPreferences] for a specific book
         *
         * @param context the application context.
         * @param db      the book database.
         * @return Shared preferences
         */
        fun getBookPreferences(context: Context, db: SQLiteDatabase): SharedPreferences {
            val bookUID: String = getBookUID(db)
            return context.getSharedPreferences(bookUID, Context.MODE_PRIVATE)
        }

        /**
         * Return the [SharedPreferences] for a specific book
         *
         * @param holder Database holder
         * @return Shared preferences
         */
        fun getBookPreferences(holder: DatabaseHolder): SharedPreferences {
            val context = holder.context
            val bookUID = holder.name
            return context.getSharedPreferences(bookUID, Context.MODE_PRIVATE)
        }
    }
}
