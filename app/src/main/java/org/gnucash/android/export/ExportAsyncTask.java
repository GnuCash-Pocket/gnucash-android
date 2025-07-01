/*
 * Copyright (c) 2013 - 2015 Ngewi Fet <ngewif@gmail.com>
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

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.OperationCanceledException;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.gnucash.android.R;
import org.gnucash.android.export.csv.CsvAccountExporter;
import org.gnucash.android.export.csv.CsvTransactionsExporter;
import org.gnucash.android.export.ofx.OfxExporter;
import org.gnucash.android.export.qif.QifExporter;
import org.gnucash.android.export.xml.GncXmlExporter;
import org.gnucash.android.gnc.AsyncTaskProgressListener;
import org.gnucash.android.gnc.GncProgressListener;
import org.gnucash.android.ui.common.GnucashProgressDialog;
import org.gnucash.android.ui.common.Refreshable;
import org.gnucash.android.ui.settings.OwnCloudPreferences;

import timber.log.Timber;

/**
 * Asynchronous task for exporting transactions.
 *
 * @author Ngewi Fet <ngewif@gmail.com>
 */
public class ExportAsyncTask extends AsyncTask<ExportParams, Object, Uri> {
    @NonNull
    private final Context mContext;
    @Nullable
    private final ProgressDialog progressDialog;
    @NonNull
    private final String mBookUID;
    @NonNull
    private ExportParams mExportParams;
    @Nullable
    private Exporter exporter;
    @Nullable
    private final AsyncTaskProgressListener listener;

    public ExportAsyncTask(@NonNull Context context, @NonNull String bookUID) {
        super();
        this.mContext = context;
        this.mBookUID = bookUID;
        if (context instanceof Activity) {
            this.listener = new ProgressListener(context);
            ProgressDialog progressDialog = new GnucashProgressDialog((Activity) context);
            progressDialog.setTitle(R.string.nav_menu_export);
            progressDialog.setCancelable(true);
            progressDialog.setOnCancelListener(dialog -> {
                cancel(true);
                if (exporter != null) {
                    exporter.cancel();
                }
            });
            this.progressDialog = progressDialog;
        } else {
            progressDialog = null;
            this.listener = null;
        }
    }

    @Override
    protected void onPreExecute() {
        super.onPreExecute();
        if (progressDialog != null) {
            progressDialog.show();
        }
    }

    @Override
    protected Uri doInBackground(ExportParams... params) {
        final ExportParams exportParams = params[0];
        mExportParams = exportParams;
        Exporter exporter = createExporter(mContext, exportParams, mBookUID, listener);
        this.exporter = exporter;
        final Uri exportedFile;

        try {
            exportedFile = exporter.export();
        } catch (OperationCanceledException ce) {
            Timber.i(ce);
            return null;
        } catch (final Throwable e) {
            if (e.getCause() instanceof OperationCanceledException) {
                Timber.i(e.getCause());
                return null;
            }
            Timber.e(e, "Error exporting: %s", e.getMessage());
            return null;
        }
        if (exportedFile == null) {
            Timber.e("Nothing exported");
            return null;
        }
        return exportedFile;
    }

    @Override
    protected void onProgressUpdate(Object... values) {
        if (progressDialog != null && progressDialog.isShowing()) {
            listener.showProgress(progressDialog, values);
        }
    }

    @Override
    protected void onPostExecute(@Nullable Uri exportSuccessful) {
        dismissProgressDialog();

        final ExportParams exportParams = mExportParams;
        if (exportSuccessful != null) {
            if (mContext instanceof Activity) {
                reportSuccess(exportParams);
            }
            if (exportParams.shouldDeleteTransactionsAfterExport()) {
                refreshViews();
            }
        } else {
            if (mContext instanceof Activity) {
                Toast.makeText(mContext,
                    mContext.getString(R.string.toast_export_error, exportParams.getExportFormat().name()),
                    Toast.LENGTH_LONG).show();
            }
        }
    }

    private void dismissProgressDialog() {
        final ProgressDialog progressDialog = this.progressDialog;
        try {
            if (progressDialog != null && progressDialog.isShowing()) {
                progressDialog.dismiss();
            }
        } catch (IllegalArgumentException ex) {
            //TODO: This is a hack to catch "View not attached to window" exceptions
            //FIXME by moving the creation and display of the progress dialog to the Fragment
        }
        if (mContext instanceof Activity) {
            ((Activity) mContext).finish();
        }
    }

    /**
     * Returns an exporter corresponding to the user settings.
     *
     * @return Object of one of {@link QifExporter}, {@link OfxExporter} or {@link GncXmlExporter}, {@Link CsvAccountExporter} or {@Link CsvTransactionsExporter}
     */
    private Exporter createExporter(
        @NonNull Context context,
        @NonNull ExportParams exportParams,
        @NonNull String bookUID,
        @Nullable GncProgressListener listener
    ) {
        switch (exportParams.getExportFormat()) {
            case QIF:
                return new QifExporter(context, exportParams, bookUID, listener);
            case OFX:
                return new OfxExporter(context, exportParams, bookUID, listener);
            case CSVA:
                return new CsvAccountExporter(context, exportParams, bookUID, listener);
            case CSVT:
                return new CsvTransactionsExporter(context, exportParams, bookUID, listener);
            case XML:
            default:
                return new GncXmlExporter(context, exportParams, bookUID, listener);
        }
    }

    private void reportSuccess(ExportParams exportParams) {
        String targetLocation;
        switch (exportParams.getExportTarget()) {
            case SD_CARD:
                targetLocation = "SD card";
                break;
            case DROPBOX:
                targetLocation = "DropBox -> Apps -> GnuCash";
                break;
            case OWNCLOUD: {
                final OwnCloudPreferences preferences = new OwnCloudPreferences(mContext);
                targetLocation = preferences.isSync() ?
                    "ownCloud -> " + preferences.getDir() : "ownCloud sync not enabled";
            }
            break;
            default:
                targetLocation = mContext.getString(R.string.label_export_target_external_service);
        }
        Toast.makeText(mContext,
            String.format(mContext.getString(R.string.toast_exported_to), targetLocation),
            Toast.LENGTH_LONG).show();
    }

    private void refreshViews() {
        if (mContext instanceof Refreshable) {
            ((Refreshable) mContext).refresh();
        }
    }

    private class ProgressListener extends AsyncTaskProgressListener {

        ProgressListener(Context context) {
            super(context);
        }

        @Override
        protected void publishProgress(@NonNull String label, long progress, long total) {
            ExportAsyncTask.this.publishProgress(label, progress, total);
        }
    }
}
