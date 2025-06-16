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

import android.content.Context;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.gnucash.android.app.GnuCashApplication;
import org.gnucash.android.db.DatabaseSchema.BookEntry;
import org.gnucash.android.db.adapter.AccountsDbAdapter;
import org.gnucash.android.db.adapter.BooksDbAdapter;
import org.gnucash.android.db.adapter.CommoditiesDbAdapter;
import org.gnucash.android.model.Book;
import org.gnucash.android.model.Commodity;

import java.io.File;

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
        + ");"
        + DatabaseHelper.createUpdatedAtTrigger(BookEntry.TABLE_NAME);

    @Nullable
    private Context context;
    @Nullable
    private DatabaseHolder holder;

    public BookDbHelper(@NonNull Context context) {
        super(context, DatabaseSchema.BOOK_DATABASE_NAME, null, DatabaseSchema.BOOK_DATABASE_VERSION);
        this.context = context;
    }

    @NonNull
    public DatabaseHolder getHolder() {
        DatabaseHolder holder = this.holder;
        if (holder == null) {
            this.holder = holder = new DatabaseHolder(context, getWritableDatabase());
            this.context = null;
        }
        return holder;
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(BOOKS_TABLE_CREATE);
        Context context = GnuCashApplication.getAppContext();
        insertBlankBook(context, db);
    }

    @NonNull
    public Book insertBlankBook(@NonNull Context context) {
        DatabaseHolder holder = getHolder();
        return insertBlankBook(holder.context, holder.db);
    }

    @NonNull
    public Book insertBlankBook(@NonNull Context context, @NonNull SQLiteDatabase db) {
        DatabaseHolder bookHolder = new DatabaseHolder(context, db);
        if (this.holder == null) {
            this.holder = bookHolder;
        }
        Book book = new Book();
        DatabaseHelper dbHelper = new DatabaseHelper(context, book.getUID());
        DatabaseHolder dbHolder = dbHelper.getHolder();
        CommoditiesDbAdapter commoditiesDbAdapter = new CommoditiesDbAdapter(dbHolder);
        Commodity.DEFAULT_COMMODITY = commoditiesDbAdapter.getDefaultCommodity();

        AccountsDbAdapter accountsDbAdapter = new AccountsDbAdapter(dbHolder);
        String rootAccountUID = accountsDbAdapter.getOrCreateGnuCashRootAccountUID();
        try {
            dbHelper.close();
        } catch (Exception ignore) {
        }
        book.setRootAccountUID(rootAccountUID);
        book.setActive(true);
        insertBook(bookHolder, book);
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
     * @param holder Database holder
     * @param book       Book to insert
     */
    private void insertBook(@NonNull DatabaseHolder holder, Book book) {
        BooksDbAdapter booksDbAdapter = new BooksDbAdapter(holder);
        String name = book.getDisplayName();
        if (TextUtils.isEmpty(name)) {
            name = booksDbAdapter.generateDefaultBookName();
            book.setDisplayName(name);
        }
        booksDbAdapter.addRecord(book);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        //nothing to see here yet, move along
    }

    public static String getBookUID(@NonNull SQLiteDatabase db) {
        String path = db.getPath();
        File file = new File(path);
        return file.getName();
    }

    /**
     * Return the {@link SharedPreferences} for a specific book
     *
     * @param context the application context.
     * @param db      the book database.
     * @return Shared preferences
     */
    public static SharedPreferences getBookPreferences(@NonNull Context context, @NonNull SQLiteDatabase db) {
        String bookUID = getBookUID(db);
        return context.getSharedPreferences(bookUID, Context.MODE_PRIVATE);
    }

    /**
     * Return the {@link SharedPreferences} for a specific book
     *
     * @param holder Database holder
     * @return Shared preferences
     */
    public static SharedPreferences getBookPreferences(@NonNull DatabaseHolder holder) {
        Context context = holder.context;
        String bookUID = holder.name;
        return context.getSharedPreferences(bookUID, Context.MODE_PRIVATE);
    }
}
