/*
 * Copyright (c) 2014 Ngewi Fet <ngewif@gmail.com>
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
package org.gnucash.android.importer;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.AsyncTask;
import android.text.TextUtils;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.gnucash.android.R;
import org.gnucash.android.db.DatabaseSchema;
import org.gnucash.android.db.adapter.BooksDbAdapter;
import org.gnucash.android.gnc.AsyncTaskProgressListener;
import org.gnucash.android.model.Book;
import org.gnucash.android.service.ScheduledActionService;
import org.gnucash.android.ui.common.GnucashProgressDialog;
import org.gnucash.android.util.BackupManager;
import org.gnucash.android.util.BookUtils;
import org.gnucash.android.util.ContentExtKt;

import java.io.InputStream;

import timber.log.Timber;

/**
 * Imports a GnuCash (desktop) account file and displays a progress dialog.
 * The AccountsActivity is opened when importing is done.
 */
public class ImportAsyncTask extends AsyncTask<Uri, Object, String> {
    @Nullable
    private final ImportBookCallback bookCallback;
    private final boolean mBackup;
    @NonNull
    private final ProgressDialog progressDialog;
    @NonNull
    private final AsyncTaskProgressListener listener;

    public ImportAsyncTask(@NonNull Activity context) {
        this(context, null);
    }

    public ImportAsyncTask(@NonNull Activity context, @Nullable ImportBookCallback callback) {
        this(context, callback, false);
    }

    public ImportAsyncTask(@NonNull Activity context, @Nullable ImportBookCallback callback, boolean backup) {
        this.bookCallback = callback;
        this.mBackup = backup;
        progressDialog = new GnucashProgressDialog(context);
        progressDialog.setTitle(R.string.title_import_accounts);
        progressDialog.setCancelable(true);
        progressDialog.setOnCancelListener(dialogInterface -> cancel(true));
        this.listener = new ProgressListener(context);
    }

    private class ProgressListener extends AsyncTaskProgressListener {

        ProgressListener(Context context) {
            super(context);
        }

        @Override
        protected void publishProgress(@NonNull String label, long progress, long total) {
            ImportAsyncTask.this.publishProgress(label, progress, total);
        }
    }

    @Override
    protected void onPreExecute() {
        super.onPreExecute();
        progressDialog.show();
    }

    @Override
    protected String doInBackground(Uri... uris) {
        if (mBackup) {
            BackupManager.backupActiveBook();
        }
        if (isCancelled()) {
            return null;
        }

        Uri uri = uris[0];
        final Context context = progressDialog.getContext();
        Book book;
        String bookUID;
        try {
            ContentResolver contentResolver = context.getContentResolver();
            InputStream accountInputStream = contentResolver.openInputStream(uri);
            book = GncXmlImporter.parseBook(context, accountInputStream, listener);
            book.setSourceUri(uri);
            bookUID = book.getUID();
        } catch (final Throwable e) {
            Timber.e(e, "Error importing: %s", uri);
            //TODO delete the partial book at `uri`
            return null;
        }

        BooksDbAdapter booksDbAdapter = BooksDbAdapter.getInstance();
        ContentValues contentValues = new ContentValues();
        contentValues.put(DatabaseSchema.BookEntry.COLUMN_SOURCE_URI, uri.toString());

        String displayName = book.getDisplayName();
        String name = ContentExtKt.getDocumentName(uri, context);
        if (!TextUtils.isEmpty(name)) {
            // Remove short file type extension, e.g. ".xml" or ".gnucash" or ".gnca.gz"
            int indexFileType = name.indexOf('.');
            if (indexFileType > 0) {
                name = name.substring(0, indexFileType);
            }
            displayName = name;
            book.setDisplayName(displayName);
        }
        if (TextUtils.isEmpty(displayName)) {
            displayName = booksDbAdapter.generateDefaultBookName();
            book.setDisplayName(displayName);
        }
        contentValues.put(DatabaseSchema.BookEntry.COLUMN_DISPLAY_NAME, displayName);
        booksDbAdapter.updateRecord(bookUID, contentValues);

        //set the preferences to their default values
        context.getSharedPreferences(bookUID, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(context.getString(R.string.key_use_double_entry), true)
            .apply();

        return bookUID;
    }

    @Override
    protected void onProgressUpdate(Object... values) {
        listener.showProgress(progressDialog, values);
    }

    @Override
    protected void onPostExecute(String bookUID) {
        final Context context = progressDialog.getContext();
        dismissProgressDialog();

        if (!TextUtils.isEmpty(bookUID)) {
            int message = R.string.toast_success_importing_accounts;
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
            BookUtils.loadBook(context, bookUID);
        } else {
            int message = R.string.toast_error_importing_accounts;
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
        }

        ScheduledActionService.schedulePeriodic(context);

        if (bookCallback != null) {
            bookCallback.onBookImported(bookUID);
        }
    }

    private void dismissProgressDialog() {
        final ProgressDialog progressDialog = this.progressDialog;
        try {
            if (progressDialog.isShowing()) {
                progressDialog.dismiss();
            }
        } catch (IllegalArgumentException ex) {
            //TODO: This is a hack to catch "View not attached to window" exceptions
            //FIXME by moving the creation and display of the progress dialog to the Fragment
        }
    }
}
