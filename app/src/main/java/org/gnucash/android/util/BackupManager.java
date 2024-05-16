/* Copyright (c) 2018 Àlex Magaz Graça <alexandre.magaz@gmail.com>
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

package org.gnucash.android.util;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.SystemClock;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import org.gnucash.android.R;
import org.gnucash.android.app.GnuCashApplication;
import org.gnucash.android.db.adapter.BooksDbAdapter;
import org.gnucash.android.export.ExportFormat;
import org.gnucash.android.export.ExportParams;
import org.gnucash.android.export.Exporter;
import org.gnucash.android.export.xml.GncXmlExporter;
import org.gnucash.android.model.Book;
import org.gnucash.android.receivers.PeriodicJobReceiver;
import org.gnucash.android.ui.common.GnucashProgressDialog;
import org.gnucash.android.ui.settings.PreferenceActivity;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.zip.GZIPOutputStream;

import kotlin.Unit;
import kotlin.jvm.functions.Function1;
import timber.log.Timber;

/**
 * Deals with all backup-related tasks.
 */
public class BackupManager {

    public static final String KEY_BACKUP_FILE = "book_backup_file_key";
    public static final String MIME_TYPE = "application/gzip";

    /**
     * Perform an automatic backup of all books in the database.
     * This method is run every time the service is executed
     */
    @WorkerThread
    static boolean backupAllBooks() {
        Context context = GnuCashApplication.getAppContext();
        return backupAllBooks(context);
    }

    /**
     * Perform an automatic backup of all books in the database.
     * This method is run every time the service is executed
     * @return `true` when all books were successfully backed-up.
     */
    @WorkerThread
    static boolean backupAllBooks(Context context) {
        BooksDbAdapter booksDbAdapter = BooksDbAdapter.getInstance();
        List<String> bookUIDs = booksDbAdapter.getAllBookUIDs();

        for (String bookUID : bookUIDs) {
            if (!backupBook(context, bookUID)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Backs up the active book to the directory {@link #getBackupFolder(String)}.
     *
     * @return {@code true} if backup was successful, {@code false} otherwise
     */
    @WorkerThread
    public static boolean backupActiveBook() {
        return backupActiveBook(GnuCashApplication.getAppContext());
    }

    /**
     * Backs up the active book to the directory {@link #getBackupFolder(String)}.
     *
     * @return {@code true} if backup was successful, {@code false} otherwise
     */
    @WorkerThread
    public static boolean backupActiveBook(Context context) {
        return backupBook(context, BooksDbAdapter.getInstance().getActiveBookUID());
    }

    /**
     * Backs up the book with UID {@code bookUID} to the directory
     * {@link #getBackupFolder(String)}.
     *
     * @param bookUID Unique ID of the book
     * @return {@code true} if backup was successful, {@code false} otherwise
     */
    @WorkerThread
    public static boolean backupBook(Context context, String bookUID) {
        OutputStream outputStream;
        try {
            Uri backupUri = getBookBackupFileUri(bookUID);
            if (backupUri != null) {
                outputStream = context.getContentResolver().openOutputStream(backupUri);
            } else { //no Uri set by user, use default location on SD card
                File backupFile = getBackupFile(bookUID);
                outputStream = new FileOutputStream(backupFile);
            }

            BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(outputStream);
            GZIPOutputStream gzipOutputStream = new GZIPOutputStream(bufferedOutputStream);
            OutputStreamWriter writer = new OutputStreamWriter(gzipOutputStream);

            ExportParams params = new ExportParams(ExportFormat.XML);
            new GncXmlExporter(params).generateExport(writer);
            writer.close();
            return true;
        } catch (Throwable e) {
            Timber.e(e, "Error creating XML backup");
        }
        return false;
    }

    /**
     * Returns the full path of a file to make database backup of the specified book.
     * Backups are done in XML format and are Gzipped (with ".gnca" extension).
     *
     * @param bookUID GUID of the book
     * @return the file for backups of the database.
     * @see #getBackupFolder(String)
     */
    private static File getBackupFile(String bookUID) {
        Book book = BooksDbAdapter.getInstance().getRecord(bookUID);
        return new File(getBackupFolder(book.getUID()),
                Exporter.buildExportFilename(ExportFormat.XML, book.getDisplayName()));
    }

    /**
     * Returns the path to the backups folder for the book with GUID {@code bookUID}.
     *
     * <p>Each book has its own backup folder.</p>
     *
     * @return The backup folder for the book
     */
    private static File getBackupFolder(String bookUID) {
        File baseFolder = GnuCashApplication.getAppContext().getExternalFilesDir(null);
        File folder = new File(baseFolder, bookUID + File.separator + "backups");
        if (!folder.exists())
            folder.mkdirs();
        return folder;
    }

    /**
     * Return the user-set backup file URI for the book with UID {@code bookUID}.
     *
     * @param bookUID Unique ID of the book
     * @return DocumentFile for book backups, or null if the user hasn't set any.
     */
    @Nullable
    public static Uri getBookBackupFileUri(String bookUID) {
        SharedPreferences sharedPreferences = PreferenceActivity.getBookSharedPreferences(bookUID);
        String path = sharedPreferences.getString(KEY_BACKUP_FILE, null);
        if (TextUtils.isEmpty(path)) {
            return null;
        }
        return Uri.parse(path);
    }

    public static List<File> getBackupList(String bookUID) {
        File[] backupFiles = getBackupFolder(bookUID).listFiles();
        Arrays.sort(backupFiles);
        List<File> backupFilesList = Arrays.asList(backupFiles);
        Collections.reverse(backupFilesList);
        return backupFilesList;
    }

    public static void schedulePeriodicBackups(Context context) {
        Timber.i("Scheduling backup job");
        Intent intent = new Intent(context, PeriodicJobReceiver.class);
        intent.setAction(PeriodicJobReceiver.ACTION_BACKUP);
        PendingIntent alarmIntent = PendingIntent.getBroadcast(context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        alarmManager.setInexactRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + AlarmManager.INTERVAL_FIFTEEN_MINUTES,
                AlarmManager.INTERVAL_DAY, alarmIntent);
    }

    public static void backupBookAsync(@Nullable final Activity activity, final String bookUID, @NonNull final Function1<Boolean, Unit> after) {
        new AsyncTask<Object, Void, Boolean>() {
            private ProgressDialog mProgressDialog;

            @Override
            protected void onPreExecute() {
                if (activity != null) {
                    mProgressDialog = new GnucashProgressDialog(activity);
                    mProgressDialog.setTitle(R.string.title_create_backup_pref);
                    mProgressDialog.setCancelable(true);
                    mProgressDialog.setOnCancelListener(dialogInterface -> cancel(true));
                    mProgressDialog.show();
                }
            }

            @Override
            protected Boolean doInBackground(Object... objects) {
                return backupBook(activity, bookUID);
            }

            @Override
            protected void onPostExecute(Boolean result) {
                if (mProgressDialog != null) {
                    mProgressDialog.hide();
                }
                after.invoke(result);
            }
        }.execute();
    }

    static void backupAllBooksAsync(@Nullable final Activity activity, @NonNull final Function1<Boolean, Unit> after) {
        new AsyncTask<Object, Void, Boolean>() {
            private ProgressDialog mProgressDialog;

            @Override
            protected void onPreExecute() {
                if (activity != null) {
                    mProgressDialog = new GnucashProgressDialog(activity);
                    mProgressDialog.setTitle(R.string.title_create_backup_pref);
                    mProgressDialog.setCancelable(true);
                    mProgressDialog.setOnCancelListener(dialogInterface -> cancel(true));
                    mProgressDialog.show();
                }
            }

            @Override
            protected Boolean doInBackground(Object... objects) {
                return backupAllBooks(activity);
            }

            @Override
            protected void onPostExecute(Boolean result) {
                if (mProgressDialog != null) {
                    mProgressDialog.hide();
                }
                after.invoke(result);
            }
        }.execute();
    }

    public static void backupActiveBookAsync(@Nullable Activity activity, @NonNull final Function1<Boolean, Unit> after) {
        backupBookAsync(activity, BooksDbAdapter.getInstance().getActiveBookUID(), after);
    }
}
