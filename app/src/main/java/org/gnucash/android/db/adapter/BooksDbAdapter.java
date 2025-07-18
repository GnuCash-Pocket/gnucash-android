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

package org.gnucash.android.db.adapter;

import static java.lang.Math.max;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteStatement;
import android.net.Uri;
import android.provider.BaseColumns;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import org.gnucash.android.R;
import org.gnucash.android.app.GnuCashApplication;
import org.gnucash.android.db.DatabaseHelper;
import org.gnucash.android.db.DatabaseHolder;
import org.gnucash.android.db.DatabaseSchema.BookEntry;
import org.gnucash.android.model.Book;
import org.gnucash.android.util.TimestampHelper;

import java.util.ArrayList;
import java.util.List;

import timber.log.Timber;

/**
 * Database adapter for creating/modifying book entries
 */
public class BooksDbAdapter extends DatabaseAdapter<Book> {

    /**
     * Opens the database adapter with an existing database
     *
     * @param holder Database holder
     */
    public BooksDbAdapter(@NonNull DatabaseHolder holder) {
        super(holder, BookEntry.TABLE_NAME, new String[]{
            BookEntry.COLUMN_DISPLAY_NAME,
            BookEntry.COLUMN_ROOT_GUID,
            BookEntry.COLUMN_TEMPLATE_GUID,
            BookEntry.COLUMN_SOURCE_URI,
            BookEntry.COLUMN_ACTIVE,
            BookEntry.COLUMN_LAST_SYNC
        });
    }

    /**
     * Return the application instance of the books database adapter
     *
     * @return Books database adapter
     */
    public static BooksDbAdapter getInstance() {
        return GnuCashApplication.getBooksDbAdapter();
    }

    @Override
    public Book buildModelInstance(@NonNull Cursor cursor) {
        String rootAccountGUID = cursor.getString(cursor.getColumnIndexOrThrow(BookEntry.COLUMN_ROOT_GUID));
        String rootTemplateGUID = cursor.getString(cursor.getColumnIndexOrThrow(BookEntry.COLUMN_TEMPLATE_GUID));
        String uriString = cursor.getString(cursor.getColumnIndexOrThrow(BookEntry.COLUMN_SOURCE_URI));
        String displayName = cursor.getString(cursor.getColumnIndexOrThrow(BookEntry.COLUMN_DISPLAY_NAME));
        int active = cursor.getInt(cursor.getColumnIndexOrThrow(BookEntry.COLUMN_ACTIVE));
        String lastSync = cursor.getString(cursor.getColumnIndexOrThrow(BookEntry.COLUMN_LAST_SYNC));

        Book book = new Book(rootAccountGUID);
        populateBaseModelAttributes(cursor, book);
        book.setDisplayName(displayName);
        book.setRootTemplateUID(rootTemplateGUID);
        book.setSourceUri(uriString == null ? null : Uri.parse(uriString));
        book.setActive(active != 0);
        book.setLastSync(TimestampHelper.getTimestampFromUtcString(lastSync));

        return book;
    }

    @Override
    protected @NonNull SQLiteStatement bind(@NonNull SQLiteStatement stmt, @NonNull final Book book) {
        if (TextUtils.isEmpty(book.getDisplayName())) {
            book.setDisplayName(generateDefaultBookName());
        }
        bindBaseModel(stmt, book);
        stmt.bindString(1, book.getDisplayName());
        stmt.bindString(2, book.getRootAccountUID());
        stmt.bindString(3, book.getRootTemplateUID());
        if (book.getSourceUri() != null) {
            stmt.bindString(4, book.getSourceUri().toString());
        }
        stmt.bindLong(5, book.isActive() ? 1 : 0);
        stmt.bindString(6, TimestampHelper.getUtcStringFromTimestamp(book.getLastSync()));

        return stmt;
    }


    /**
     * Deletes a book - removes the book record from the database and deletes the database file from the disk
     *
     * @param bookUID GUID of the book
     * @return <code>true</code> if deletion was successful, <code>false</code> otherwise
     * @see #deleteRecord(String)
     */
    public boolean deleteBook(@NonNull Context context, @NonNull String bookUID) {
        boolean result = context.deleteDatabase(bookUID);
        if (result) //delete the db entry only if the file deletion was successful
            result &= deleteRecord(bookUID);

        GnuCashApplication.getBookPreferences(context, bookUID).edit().clear().apply();

        return result;
    }

    /**
     * Sets the book with unique identifier {@code uid} as active and all others as inactive
     * <p>If the parameter is null, then the currently active book is not changed</p>
     *
     * @param bookUID Unique identifier of the book
     * @return GUID of the currently active book
     */
    public String setActive(@NonNull String bookUID) {
        if (bookUID == null)
            return getActiveBookUID();

        ContentValues contentValues = new ContentValues();
        contentValues.put(BookEntry.COLUMN_ACTIVE, 0);
        mDb.update(mTableName, contentValues, null, null); //disable all

        contentValues.clear();
        contentValues.put(BookEntry.COLUMN_ACTIVE, 1);
        mDb.update(mTableName, contentValues, BookEntry.COLUMN_UID + " = ?", new String[]{bookUID});

        return bookUID;
    }

    /**
     * Checks if the book is active or not
     *
     * @param bookUID GUID of the book
     * @return {@code true} if the book is active, {@code false} otherwise
     */
    public boolean isActive(String bookUID) {
        String isActive = getAttribute(bookUID, BookEntry.COLUMN_ACTIVE);
        return Integer.parseInt(isActive) > 0;
    }

    /**
     * Returns the GUID of the current active book
     *
     * @return GUID of the active book
     * @throws NoActiveBookFoundException
     */
    public @NonNull String getActiveBookUID() throws NoActiveBookFoundException {
        try (Cursor cursor = mDb.query(mTableName,
            new String[]{BookEntry.COLUMN_UID},
            BookEntry.COLUMN_ACTIVE + " = 1",
            null,
            null,
            null,
            null,
            "1")) {
            if (cursor.moveToFirst()) {
                return cursor.getString(0);
            }
            NoActiveBookFoundException e = new NoActiveBookFoundException(
                "There is no active book in the app.\n"
                    + "This should NEVER happen - fix your bugs!\n"
                    + getNoActiveBookFoundExceptionInfo());
            // Timber.e(e);
            throw e;
        }
    }

    private String getNoActiveBookFoundExceptionInfo() {
        StringBuilder info = new StringBuilder("UID, created, source\n");
        for (Book book : getAllRecords()) {
            info.append(String.format("%s, %s, %s\n",
                book.getUID(),
                book.getCreatedTimestamp(),
                book.getSourceUri()));
        }
        return info.toString();
    }

    public Book getActiveBook() {
        return getRecord(getActiveBookUID());
    }

    public class NoActiveBookFoundException extends RuntimeException {
        public NoActiveBookFoundException(String message) {
            super(message);
        }
    }

    /**
     * Tries to fix the books database.
     *
     * @return the active book UID.
     */
    @Nullable
    public String fixBooksDatabase() {
        Timber.v("Looking for books to set as active...");
        if (getRecordsCount() <= 0) {
            Timber.w("No books found in the database. Recovering books records...");
            recoverBookRecords();
            if (getRecordsCount() <= 0) {
                return null;
            }
        }
        return setFirstBookAsActive();
    }

    /**
     * Restores the records in the book database.
     * <p>
     * Does so by looking for database files from books.
     */
    private void recoverBookRecords() {
        for (String dbName : getBookDatabases()) {
            Book book = new Book(getRootAccountUID(dbName));
            book.setUID(dbName);
            book.setDisplayName(generateDefaultBookName());
            addRecord(book);
            Timber.i("Recovered book record: %s", book.getUID());
        }
    }

    /**
     * Returns the root account UID from the database with name dbName.
     */
    private String getRootAccountUID(String dbName) {
        Context context = holder.context;
        DatabaseHelper databaseHelper = new DatabaseHelper(context, dbName);
        DatabaseHolder holder = databaseHelper.getHolder();
        AccountsDbAdapter accountsDbAdapter = new AccountsDbAdapter(holder);
        String uid = accountsDbAdapter.getOrCreateRootAccountUID();
        databaseHelper.close();
        return uid;
    }

    /**
     * Sets the first book in the database as active.
     *
     * @return the book UID.
     */
    @Nullable
    private String setFirstBookAsActive() {
        List<Book> books = getAllRecords();
        if (books.isEmpty()) {
            Timber.w("No books.");
            return null;
        }
        Book firstBook = books.get(0);
        firstBook.setActive(true);
        addRecord(firstBook);
        Timber.i("Book " + firstBook.getUID() + " set as active.");
        return firstBook.getUID();
    }

    /**
     * Returns a list of database names corresponding to book databases.
     */
    private List<String> getBookDatabases() {
        List<String> bookDatabases = new ArrayList<>();
        for (String database : GnuCashApplication.getAppContext().databaseList()) {
            if (isBookDatabase(database)) {
                bookDatabases.add(database);
            }
        }
        return bookDatabases;
    }

    @VisibleForTesting
    public static boolean isBookDatabase(String databaseName) {
        return databaseName.matches("[a-z0-9]{32}"); // UID regex
    }

    public @NonNull List<String> getAllBookUIDs() {
        List<String> bookUIDs = new ArrayList<>();
        try (Cursor cursor = mDb.query(true, mTableName, new String[]{BookEntry.COLUMN_UID},
            null, null, null, null, null, null)) {
            while (cursor.moveToNext()) {
                bookUIDs.add(cursor.getString(cursor.getColumnIndexOrThrow(BookEntry.COLUMN_UID)));
            }
        }

        return bookUIDs;
    }

    /**
     * Return the name of the currently active book.
     * Or a generic name if there is no active book (should never happen)
     *
     * @return Display name of the book
     */
    public @NonNull String getActiveBookDisplayName() {
        Cursor cursor = mDb.query(mTableName,
            new String[]{BookEntry.COLUMN_DISPLAY_NAME}, BookEntry.COLUMN_ACTIVE + " = 1",
            null, null, null, null);
        try {
            if (cursor.moveToFirst()) {
                return cursor.getString(cursor.getColumnIndexOrThrow(BookEntry.COLUMN_DISPLAY_NAME));
            }
        } finally {
            cursor.close();
        }
        return "Book1";
    }

    /**
     * Generates a new default name for a new book
     *
     * @return String with default name
     */
    public @NonNull String generateDefaultBookName() {
        String sqlMax = "SELECT MAX(" + BaseColumns._ID +") FROM " + mTableName;
        SQLiteStatement statementMax = mDb.compileStatement(sqlMax);
        long bookCount = max(statementMax.simpleQueryForLong(), 1);

        String sql = "SELECT COUNT(*) FROM " + mTableName + " WHERE " + BookEntry.COLUMN_DISPLAY_NAME + " = ?";
        SQLiteStatement statement = mDb.compileStatement(sql);
        Context context = GnuCashApplication.getAppContext();

        while (true) {
            String name = context.getString(R.string.book_default_name, bookCount);

            statement.bindString(1, name);
            long nameCount = statement.simpleQueryForLong();

            if (nameCount == 0) {
                statement.close();
                return name;
            }

            bookCount++;
        }
    }
}
