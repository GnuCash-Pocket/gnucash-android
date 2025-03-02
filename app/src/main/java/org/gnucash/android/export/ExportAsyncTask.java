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
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.gnucash.android.R;
import org.gnucash.android.export.csv.CsvAccountExporter;
import org.gnucash.android.export.csv.CsvTransactionsExporter;
import org.gnucash.android.export.ofx.OfxExporter;
import org.gnucash.android.export.qif.QifExporter;
import org.gnucash.android.export.xml.GncXmlExporter;
import org.gnucash.android.ui.common.GnucashProgressDialog;
import org.gnucash.android.ui.common.Refreshable;

import timber.log.Timber;

/**
 * Asynchronous task for exporting transactions.
 *
 * @author Ngewi Fet <ngewif@gmail.com>
 */
public class ExportAsyncTask extends AsyncTask<ExportParams, Void, Uri> {

    private final Context mContext;

    private ProgressDialog mProgressDialog;

    private final String mBookUID;

    /**
     * Export parameters
     */
    private ExportParams mExportParams;

    public ExportAsyncTask(Context context, String bookUID) {
        super();
        this.mContext = context;
        this.mBookUID = bookUID;
    }

    @Override
    protected void onPreExecute() {
        super.onPreExecute();
        if (mContext instanceof Activity) {
            mProgressDialog = new GnucashProgressDialog((Activity) mContext);
            mProgressDialog.setTitle(R.string.title_progress_exporting_transactions);
            mProgressDialog.setCancelable(true);
            mProgressDialog.setOnCancelListener(dialogInterface -> cancel(true));
            mProgressDialog.show();
        }
    }

    /**
     * Generates the appropriate exported transactions file for the given parameters
     *
     * @param params Export parameters
     * @return <code>true</code> if export was successful, <code>false</code> otherwise
     */
    @Override
    protected Uri doInBackground(ExportParams... params) {
        final ExportParams exportParams = params[0];
        mExportParams = exportParams;
        Exporter exporter = createExporter(mContext, exportParams, mBookUID);
        final Uri exportedFile;

        try {
            exportedFile = exporter.generateExport();
        } catch (Throwable e) {
            Timber.e(e, "Error exporting: %s", e.getMessage());
            return null;
        }
        if (exportedFile == null) {
            Timber.e("Nothing exported");
            return null;
        }
        return exportedFile;
    }

    /**
     * Transmits the exported transactions to the designated location, either SD card or third-party application
     * Finishes the activity if the export was starting  in the context of an activity
     *
     * @param exportSuccessful Result of background export execution
     */
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
        @NonNull String bookUID
    ) {
        switch (exportParams.getExportFormat()) {
            case QIF:
                return new QifExporter(context, exportParams, bookUID);
            case OFX:
                return new OfxExporter(context, exportParams, bookUID);
            case CSVA:
                return new CsvAccountExporter(context, exportParams, bookUID);
            case CSVT:
                return new CsvTransactionsExporter(context, exportParams, bookUID);
            case XML:
            default:
                return new GncXmlExporter(context, exportParams, bookUID);
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
            case OWNCLOUD:
                targetLocation = mContext.getSharedPreferences(
                    mContext.getString(R.string.owncloud_pref),
                    Context.MODE_PRIVATE).getBoolean(
                    mContext.getString(R.string.owncloud_sync), false) ?

                    "ownCloud -> " +
                        mContext.getSharedPreferences(
                            mContext.getString(R.string.owncloud_pref),
                            Context.MODE_PRIVATE).getString(
                            mContext.getString(R.string.key_owncloud_dir), null) :
                    "ownCloud sync not enabled";
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
}
