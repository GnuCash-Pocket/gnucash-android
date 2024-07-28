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
import org.gnucash.android.model.Book;
import org.gnucash.android.ui.common.GnucashProgressDialog;
import org.gnucash.android.ui.util.TaskDelegate;
import org.gnucash.android.util.BackupManager;
import org.gnucash.android.util.BookUtils;
import org.gnucash.android.util.ContentExtKt;

import java.io.InputStream;

import timber.log.Timber;

/**
 * Imports a GnuCash (desktop) account file and displays a progress dialog.
 * The AccountsActivity is opened when importing is done.
 */
public class ImportAsyncTask extends AsyncTask<Uri, Void, String> {
    private final Activity mContext;
    private final TaskDelegate mDelegate;
    private final boolean mBackup;
    private ProgressDialog mProgressDialog;

    public ImportAsyncTask(@NonNull Activity context) {
        this(context, null);
    }

    public ImportAsyncTask(@NonNull Activity context, @Nullable TaskDelegate delegate) {
        this(context, delegate, false);
    }

    public ImportAsyncTask(@NonNull Activity context, @Nullable TaskDelegate delegate, boolean backup) {
        this.mContext = context;
        this.mDelegate = delegate;
        this.mBackup = backup;
    }

    @Override
    protected void onPreExecute() {
        super.onPreExecute();
        mProgressDialog = new GnucashProgressDialog(mContext);
        mProgressDialog.setTitle(R.string.title_progress_importing_accounts);
        mProgressDialog.setCancelable(true);
        mProgressDialog.setOnCancelListener(dialogInterface -> cancel(true));
        mProgressDialog.show();
    }

    @Override
    protected String doInBackground(Uri... uris) {
        if (mBackup) {
            BackupManager.backupActiveBook();
        }

        Uri uri = uris[0];
        ContentResolver contentResolver = mContext.getContentResolver();
        Book book;
        String bookUID;
        try {
            InputStream accountInputStream = contentResolver.openInputStream(uri);
            book = GncXmlImporter.parseBook(accountInputStream);
            book.setSourceUri(uri);
            bookUID = book.getUID();
        } catch (final Throwable e) {
            Timber.e(e, "Error importing: %s", uri);
            //TODO delete the partial book at `uri`
            return null;
        }

        ContentValues contentValues = new ContentValues();
        contentValues.put(DatabaseSchema.BookEntry.COLUMN_SOURCE_URI, uri.toString());

        String displayName = book.getDisplayName();
        if (TextUtils.isEmpty(displayName)) {
            displayName = ContentExtKt.getDocumentName(uri, mContext);
            // Remove short file type extension, e.g. ".xml" or ".gnca".
            int indexFileType = displayName.lastIndexOf('.');
            if ((indexFileType > 0) && (indexFileType + 5 >= displayName.length())) {
                displayName = displayName.substring(0, indexFileType);
            }
            contentValues.put(DatabaseSchema.BookEntry.COLUMN_DISPLAY_NAME, displayName);
        }
        BooksDbAdapter.getInstance().updateRecord(bookUID, contentValues);

        //set the preferences to their default values
        mContext.getSharedPreferences(bookUID, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(mContext.getString(R.string.key_use_double_entry), true)
            .apply();

        return bookUID;
    }

    @Override
    protected void onPostExecute(String bookUID) {
        try {
            if (mProgressDialog != null && mProgressDialog.isShowing()) {
                mProgressDialog.dismiss();
            }
        } catch (IllegalArgumentException ex) {
            //TODO: This is a hack to catch "View not attached to window" exceptions
            //FIXME by moving the creation and display of the progress dialog to the Fragment
        } finally {
            mProgressDialog = null;
        }

        if (!TextUtils.isEmpty(bookUID)) {
            int message = R.string.toast_success_importing_accounts;
            Toast.makeText(mContext, message, Toast.LENGTH_SHORT).show();
            BookUtils.loadBook(mContext, bookUID);
        } else {
            int message = R.string.toast_error_importing_accounts;
            Toast.makeText(mContext, message, Toast.LENGTH_SHORT).show();
        }

        if (mDelegate != null)
            mDelegate.onTaskComplete();
    }
}
