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

package org.gnucash.android.db;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.text.TextUtils;

import androidx.annotation.NonNull;

import org.gnucash.android.app.GnuCashApplication;
import org.gnucash.android.db.DatabaseSchema.BookEntry;
import org.gnucash.android.db.adapter.AccountsDbAdapter;
import org.gnucash.android.db.adapter.BooksDbAdapter;
import org.gnucash.android.db.adapter.TransactionsDbAdapter;
import org.gnucash.android.model.Book;

import java.io.IOException;

/**
 * Database helper for managing database which stores information about the books in the application
 * This is a different database from the one which contains the accounts and transaction data because
 * there are multiple accounts/transactions databases in the system and this one will be used to
 * switch between them.
 */
public class BookDbHelper extends SQLiteOpenHelper {

    /**
     * Create the books table
     */
    private static final String BOOKS_TABLE_CREATE = "CREATE TABLE " + BookEntry.TABLE_NAME + " ("
            + BookEntry._ID + " integer primary key autoincrement, "
            + BookEntry.COLUMN_UID + " varchar(255) not null UNIQUE, "
            + BookEntry.COLUMN_DISPLAY_NAME + " varchar(255) not null, "
            + BookEntry.COLUMN_ROOT_GUID + " varchar(255) not null, "
            + BookEntry.COLUMN_TEMPLATE_GUID + " varchar(255), "
            + BookEntry.COLUMN_ACTIVE + " tinyint default 0, "
            + BookEntry.COLUMN_SOURCE_URI + " varchar(255), "
            + BookEntry.COLUMN_LAST_SYNC + " TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, "
            + BookEntry.COLUMN_CREATED_AT + " TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, "
            + BookEntry.COLUMN_MODIFIED_AT + " TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP "
            + ");" + DatabaseHelper.createUpdatedAtTrigger(BookEntry.TABLE_NAME);

    @NonNull
    private final Context context;

    public BookDbHelper(@NonNull Context context) {
        super(context, DatabaseSchema.BOOK_DATABASE_NAME, null, DatabaseSchema.BOOK_DATABASE_VERSION);
        this.context = context;
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(BOOKS_TABLE_CREATE);

        insertBlankBook(db);
    }

    @NonNull
    public Book insertBlankBook(@NonNull SQLiteDatabase db) {
        Book book = new Book();
        DatabaseHelper helper = new DatabaseHelper(context, book.getUID());
        SQLiteDatabase mainDb = helper.getWritableDatabase(); //actually create the db
        AccountsDbAdapter accountsDbAdapter = new AccountsDbAdapter(
            mainDb,
            new TransactionsDbAdapter(mainDb)
        );

        String rootAccountUID = accountsDbAdapter.getOrCreateRootAccountUID();
        try {
            accountsDbAdapter.close();
            helper.close();
        } catch (IOException ignore) {
        }
        book.setRootAccountUID(rootAccountUID);
        book.setActive(true);
        insertBook(db, book);
        return book;
    }

    /**
     * Returns the database for the book
     *
     * @param bookUID GUID of the book
     * @return SQLiteDatabase of the book
     */
    public static SQLiteDatabase getDatabase(String bookUID) {
        return getDatabase(GnuCashApplication.getAppContext(), bookUID);
    }

    /**
     * Returns the database for the book
     *
     * @param context The application context.
     * @param bookUID GUID of the book
     * @return SQLiteDatabase of the book
     */
    public static SQLiteDatabase getDatabase(@NonNull Context context, String bookUID) {
        DatabaseHelper dbHelper = new DatabaseHelper(context, bookUID);
        return dbHelper.getWritableDatabase();
    }

    /**
     * Inserts the book into the database
     *
     * @param db   Book database
     * @param book Book to insert
     */
    private void insertBook(SQLiteDatabase db, Book book) {
        String name = book.getDisplayName();
        if (TextUtils.isEmpty(name)) {
            name = new BooksDbAdapter(db).generateDefaultBookName();
        }
        ContentValues contentValues = new ContentValues();
        contentValues.put(BookEntry.COLUMN_UID, book.getUID());
        contentValues.put(BookEntry.COLUMN_ROOT_GUID, book.getRootAccountUID());
        contentValues.put(BookEntry.COLUMN_TEMPLATE_GUID, Book.generateUID());
        contentValues.put(BookEntry.COLUMN_DISPLAY_NAME, name);
        contentValues.put(BookEntry.COLUMN_ACTIVE, book.isActive() ? 1 : 0);

        db.insert(BookEntry.TABLE_NAME, null, contentValues);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        //nothing to see here yet, move along
    }
}
