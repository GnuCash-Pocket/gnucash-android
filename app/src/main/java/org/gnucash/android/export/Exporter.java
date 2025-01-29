/*
 * Copyright (c) 2014 - 2015 Ngewi Fet <ngewif@gmail.com>
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

package org.gnucash.android.export;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.os.Environment;

import androidx.annotation.NonNull;

import org.gnucash.android.BuildConfig;
import org.gnucash.android.app.GnuCashApplication;
import org.gnucash.android.db.DatabaseHelper;
import org.gnucash.android.db.DatabaseSchema;
import org.gnucash.android.db.adapter.AccountsDbAdapter;
import org.gnucash.android.db.adapter.BooksDbAdapter;
import org.gnucash.android.db.adapter.BudgetAmountsDbAdapter;
import org.gnucash.android.db.adapter.BudgetsDbAdapter;
import org.gnucash.android.db.adapter.CommoditiesDbAdapter;
import org.gnucash.android.db.adapter.PricesDbAdapter;
import org.gnucash.android.db.adapter.RecurrenceDbAdapter;
import org.gnucash.android.db.adapter.ScheduledActionDbAdapter;
import org.gnucash.android.db.adapter.SplitsDbAdapter;
import org.gnucash.android.db.adapter.TransactionsDbAdapter;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import java.io.File;
import java.io.IOException;
import java.util.List;

import timber.log.Timber;

/**
 * Base class for the different exporters
 *
 * @author Ngewi Fet <ngewif@gmail.com>
 * @author Yongxin Wang <fefe.wyx@gmail.com>
 */
public abstract class Exporter {

    /**
     * Application folder on external storage
     *
     * @deprecated Use {@link #BASE_FOLDER_PATH} instead
     */
    @Deprecated
    public static final String LEGACY_BASE_FOLDER_PATH = Environment.getExternalStorageDirectory() + File.separator + BuildConfig.APPLICATION_ID;

    /**
     * Application folder on external storage
     */
    public static final String BASE_FOLDER_PATH = GnuCashApplication.getAppContext().getExternalFilesDir(null).getAbsolutePath();

    /**
     * Export options
     */
    @NonNull
    protected final ExportParams mExportParams;

    /**
     * Cache directory to which files will be first exported before moved to final destination.
     * <p>There is a different cache dir per export format, which has the name of the export format.<br/>
     * The cache dir is cleared every time a new {@link Exporter} is instantiated.
     * The files created here are only accessible within this application, and should be copied to SD card before they can be shared
     * </p>
     */
    private final File mCacheDir;

    private static final String EXPORT_FILENAME_DATE_PATTERN = "yyyyMMdd_HHmmss";

    protected final BooksDbAdapter mBooksDbADapter;
    /**
     * Adapter for retrieving accounts to export
     * Subclasses should close this object when they are done with exporting
     */
    protected final AccountsDbAdapter mAccountsDbAdapter;
    protected final TransactionsDbAdapter mTransactionsDbAdapter;
    protected final SplitsDbAdapter mSplitsDbAdapter;
    protected final ScheduledActionDbAdapter mScheduledActionDbAdapter;
    protected final PricesDbAdapter mPricesDbAdapter;
    protected final CommoditiesDbAdapter mCommoditiesDbAdapter;
    protected final BudgetsDbAdapter mBudgetsDbAdapter;
    protected final Context mContext;
    private String mExportCacheFilePath;

    /**
     * Database being currently exported
     */
    protected final SQLiteDatabase mDb;

    /**
     * GUID of the book being exported
     */
    @NonNull
    private final String mBookUID;

    protected Exporter(@NonNull Context context,
                       @NonNull ExportParams params,
                       @NonNull String bookUID) {
        super();
        mContext = context;
        mExportParams = params;
        mBookUID = bookUID;
        mBooksDbADapter = BooksDbAdapter.getInstance();

        DatabaseHelper dbHelper = new DatabaseHelper(context, bookUID);
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        mDb = db;
        mCommoditiesDbAdapter = new CommoditiesDbAdapter(db);
        mSplitsDbAdapter = new SplitsDbAdapter(db, mCommoditiesDbAdapter);
        mTransactionsDbAdapter = new TransactionsDbAdapter(db, mSplitsDbAdapter);
        mAccountsDbAdapter = new AccountsDbAdapter(db, mTransactionsDbAdapter);
        mPricesDbAdapter = new PricesDbAdapter(db, mCommoditiesDbAdapter);
        RecurrenceDbAdapter recurrenceDbAdapter = new RecurrenceDbAdapter(db);
        mBudgetsDbAdapter = new BudgetsDbAdapter(db, new BudgetAmountsDbAdapter(db), recurrenceDbAdapter);
        mScheduledActionDbAdapter = new ScheduledActionDbAdapter(db, recurrenceDbAdapter);

        mExportCacheFilePath = null;
        mCacheDir = new File(context.getCacheDir(), params.getExportFormat().name());
        mCacheDir.mkdirs();
        purgeDirectory(mCacheDir);
    }

    @NonNull
    public String getBookUID() {
        return mBookUID;
    }

    /**
     * Strings a string of any characters not allowed in a file name.
     * All unallowed characters are replaced with an underscore
     *
     * @param inputName Raw file name input
     * @return Sanitized file name
     */
    public static String sanitizeFilename(String inputName) {
        return inputName.replaceAll("[^a-zA-Z0-9-_\\.]", "_");
    }

    /**
     * Builds a file name based on the current time stamp for the exported file
     *
     * @param format   Format to use when exporting
     * @param bookName Name of the book being exported. This name will be included in the generated file name
     * @return String containing the file name
     */
    @NonNull
    public static String buildExportFilename(ExportFormat format, String bookName) {
        DateTimeFormatter formatter = DateTimeFormat.forPattern(EXPORT_FILENAME_DATE_PATTERN);
        return formatter.print(System.currentTimeMillis())
            + "_gnucash_export_" + sanitizeFilename(bookName) +
            (format == ExportFormat.CSVA ? "_accounts" : "") +
            (format == ExportFormat.CSVT ? "_transactions" : "") +
            format.extension;
    }

    /**
     * Parses the name of an export file and returns the date of export
     *
     * @param filename Export file name generated by {@link #buildExportFilename(ExportFormat, String)}
     * @return Date in milliseconds
     */
    public static long getExportTime(String filename) {
        String[] tokens = filename.split("_");
        long timeMillis = 0;
        if (tokens.length < 2) {
            return timeMillis;
        }
        try {
            DateTimeFormatter formatter = DateTimeFormat.forPattern(EXPORT_FILENAME_DATE_PATTERN);
            timeMillis = formatter.parseMillis(tokens[0] + "_" + tokens[1]);
        } catch (IllegalArgumentException e) {
            Timber.e(e, "Error parsing time from file name: %s", e.getMessage());
        }
        return timeMillis;
    }

    /**
     * Generates the export output
     *
     * @throws ExporterException if an error occurs during export
     */
    public abstract List<String> generateExport() throws ExporterException;

    /**
     * Recursively delete all files in a directory
     *
     * @param directory File descriptor for directory
     */
    private void purgeDirectory(@NonNull File directory) {
        for (File file : directory.listFiles()) {
            if (file.isDirectory())
                purgeDirectory(file);
            else
                file.delete();
        }
    }

    /**
     * Returns the path to the file where the exporter should save the export during generation
     * <p>This path is a temporary cache file whose file extension matches the export format.<br>
     * This file is deleted every time a new export is started</p>
     *
     * @return Absolute path to file
     */
    protected String getExportCacheFilePath() {
        // The file name contains a timestamp, so ensure it doesn't change with multiple calls to
        // avoid issues like #448
        if (mExportCacheFilePath == null) {
            String cachePath = mCacheDir.getAbsolutePath();
            if (!cachePath.endsWith(File.separator))
                cachePath += File.separator;
            String bookName = mBooksDbADapter.getAttribute(mBookUID, DatabaseSchema.BookEntry.COLUMN_DISPLAY_NAME);
            mExportCacheFilePath = cachePath + buildExportFilename(mExportParams.getExportFormat(), bookName);
        }

        return mExportCacheFilePath;
    }

    /**
     * Returns that path to the export folder for the book with GUID {@code bookUID}.
     * This is the folder where exports like QIF and OFX will be saved for access by external programs
     *
     * @param bookUID GUID of the book being exported. Each book has its own export path
     * @return Absolute path to export folder for active book
     */
    @NonNull
    public static String getExportFolderPath(String bookUID) {
        String path = BASE_FOLDER_PATH + File.separator + bookUID + File.separator + "exports" + File.separator;
        File file = new File(path);
        if (!file.exists())
            file.mkdirs();
        return path;
    }

    /**
     * Returns the MIME type for this exporter.
     *
     * @return MIME type as string
     */
    @NonNull
    public String getExportMimeType() {
        return "text/plain";
    }

    protected void close() throws IOException {
        mAccountsDbAdapter.close();
        mBudgetsDbAdapter.close();
        mCommoditiesDbAdapter.close();
        mPricesDbAdapter.close();
        mScheduledActionDbAdapter.close();
        mSplitsDbAdapter.close();
        mTransactionsDbAdapter.close();
        mDb.close();
    }

    public static class ExporterException extends RuntimeException {

        public ExporterException(ExportParams params) {
            super("Failed to generate export with parameters: " + params);
        }

        public ExporterException(@NonNull ExportParams params, @NonNull String msg) {
            super("Failed to generate export with parameters: " + params + " - " + msg);
        }

        public ExporterException(ExportParams params, Throwable throwable) {
            super("Failed to generate export " + params.getExportFormat() + " - " + throwable.getMessage(),
                throwable);
        }
    }
}
