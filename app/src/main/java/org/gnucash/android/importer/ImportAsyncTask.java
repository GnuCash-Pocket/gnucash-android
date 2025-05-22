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

import static org.gnucash.android.util.ContentExtKt.openStream;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.SystemClock;
import android.text.TextUtils;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.gnucash.android.R;
import org.gnucash.android.db.DatabaseSchema;
import org.gnucash.android.db.adapter.BooksDbAdapter;
import org.gnucash.android.gnc.DefaultProgressListener;
import org.gnucash.android.gnc.GncProgressListener;
import org.gnucash.android.model.Account;
import org.gnucash.android.model.Book;
import org.gnucash.android.model.Budget;
import org.gnucash.android.model.Commodity;
import org.gnucash.android.model.Price;
import org.gnucash.android.model.ScheduledAction;
import org.gnucash.android.model.Transaction;
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
    private final GncProgressListener listener;

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
        progressDialog.setTitle(R.string.title_progress_processing_books);
        progressDialog.setCancelable(true);
        progressDialog.setOnCancelListener(dialogInterface -> cancel(true));
        this.listener = new ProgressListener(context);
    }

    private class ProgressListener extends DefaultProgressListener {
        private static final long PUBLISH_TIMEOUT = 100;

        private static class PublishItem {
            final Object[] values;
            final long timestamp;

            private PublishItem(Object[] values, long timestamp) {
                this.values = values;
                this.timestamp = timestamp;
            }
        }

        private final String labelAccounts;
        private final String labelBook;
        private final String labelBudgets;
        private final String labelCommodities;
        private final String labelPrices;
        private final String labelSchedules;
        private final String labelTransactions;
        private long countDataBudgetsTotal = 0;
        private long countDataBudgets = 0;
        private long countDataCommodityTotal = 0;
        private long countDataCommodity = 0;
        private long countDataAccountTotal = 0;
        private long countDataAccount = 0;
        private long countDataTransactionTotal = 0;
        private long countDataTransaction = 0;
        private long countDataPriceTotal = 0;
        private long countDataPrice = 0;
        private long countDataScheduledTotal = 0;
        private long countDataScheduled = 0;
        @Nullable
        private PublishItem itemPublished = null;

        ProgressListener(Context context) {
            labelAccounts = context.getString(R.string.title_progress_processing_accounts);
            labelBook = context.getString(R.string.title_progress_processing_books);
            labelBudgets = context.getString(R.string.title_progress_processing_budgets);
            labelCommodities = context.getString(R.string.title_progress_processing_commodities);
            labelPrices = context.getString(R.string.title_progress_processing_prices);
            labelSchedules = context.getString(R.string.title_progress_processing_schedules);
            labelTransactions = context.getString(R.string.title_progress_processing_transactions);
        }

        @Override
        public void onAccountCount(long count) {
            countDataAccountTotal = count;
            publishProgressDebounce(labelAccounts, countDataAccount, countDataAccountTotal);
        }

        @Override
        public void onAccount(@NonNull Account account) {
            Timber.v("%s: %s", labelAccounts, account);
            publishProgressDebounce(labelAccounts, ++countDataAccount, countDataAccountTotal);
        }

        @Override
        public void onBookCount(long count) {
            publishProgressDebounce(labelBook);
        }

        @Override
        public void onBook(@NonNull Book book) {
            Timber.v("%s: %s", labelBook, book.getDisplayName());
            publishProgressDebounce(labelBook);
        }

        @Override
        public void onBudgetCount(long count) {
            countDataBudgetsTotal = count;
            publishProgressDebounce(labelBudgets, countDataBudgets, countDataBudgetsTotal);
        }

        @Override
        public void onBudget(@NonNull Budget budget) {
            Timber.v("%s: %s", labelBudgets, budget);
            publishProgressDebounce(labelBudgets, ++countDataBudgets, countDataBudgetsTotal);
        }

        @Override
        public void onCommodityCount(long count) {
            countDataCommodityTotal = count;
            publishProgressDebounce(labelCommodities, countDataCommodity, countDataCommodityTotal);
        }

        @Override
        public void onCommodity(@NonNull Commodity commodity) {
            if (commodity.isTemplate()) return;
            Timber.v("%s: %s", labelCommodities, commodity);
            publishProgressDebounce(labelCommodities, ++countDataCommodity, countDataCommodityTotal);
        }

        @Override
        public void onPriceCount(long count) {
            countDataPriceTotal = count;
            publishProgressDebounce(labelPrices, countDataPrice, countDataPriceTotal);
        }

        @Override
        public void onPrice(@NonNull Price price) {
            Timber.v("%s: %s", labelPrices, price);
            publishProgressDebounce(labelPrices, ++countDataPrice, countDataPriceTotal);
        }

        @Override
        public void onScheduleCount(long count) {
            countDataScheduledTotal = count;
            publishProgressDebounce(labelSchedules, countDataScheduled, countDataScheduledTotal);
        }

        @Override
        public void onSchedule(@NonNull ScheduledAction scheduledAction) {
            Timber.v("%s: %s", labelSchedules, scheduledAction);
            publishProgressDebounce(labelSchedules, ++countDataScheduled, countDataScheduledTotal);
        }

        @Override
        public void onTransactionCount(long count) {
            countDataTransactionTotal = count;
            publishProgressDebounce(labelTransactions, countDataTransaction, countDataTransactionTotal);
        }

        @Override
        public void onTransaction(@NonNull Transaction transaction) {
            if (transaction.isTemplate()) return;
            Timber.v("%s: %s", labelTransactions, transaction);
            publishProgressDebounce(labelTransactions, ++countDataTransaction, countDataTransactionTotal);
        }

        private void publishProgressDebounce(final Object... values) {
            int length = values.length;
            if (length == 0) {
                return;
            }
            String labelProgress = (String) values[0];
            final PublishItem item = itemPublished;
            String labelPublished = (item != null) ? (String) item.values[0] : null;
            long timestampDelta = (item != null) ? SystemClock.elapsedRealtime() - item.timestamp : PUBLISH_TIMEOUT;
            if (timestampDelta >= PUBLISH_TIMEOUT || !TextUtils.equals(labelProgress, labelPublished)) {
                // Publish straight away, or if we waited enough time, or label changed.
                itemPublished = new PublishItem(values, SystemClock.elapsedRealtime());
                publishProgress(values);
            }
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
            final InputStream accountInputStream = openStream(uri, context);
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
        if (TextUtils.isEmpty(displayName)) {
            String name = ContentExtKt.getDocumentName(uri, context);
            if (!TextUtils.isEmpty(name)) {
                // Remove short file type extension, e.g. ".xml" or ".gnucash" or ".gnca.gz"
                int indexFileType = name.indexOf('.');
                if (indexFileType > 0) {
                    name = name.substring(0, indexFileType);
                }
                displayName = name;
            }
            if (TextUtils.isEmpty(displayName)) {
                displayName = booksDbAdapter.generateDefaultBookName();
            }
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
        final ProgressDialog progressDialog = this.progressDialog;

        int length = values.length;
        if (length > 0) {
            String value = (String) values[0];
            progressDialog.setTitle(value);
            if (length >= 3) {
                float count = ((Number) values[1]).floatValue();
                float total = ((Number) values[2]).floatValue();
                if (total > 0) {
                    float progress = (count * 100) / total;
                    progressDialog.setIndeterminate(false);
                    progressDialog.setProgress((int) progress);
                } else {
                    progressDialog.setIndeterminate(true);
                }
            } else {
                progressDialog.setIndeterminate(true);
            }
        }
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
