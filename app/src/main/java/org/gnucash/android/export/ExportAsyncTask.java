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
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ResolveInfo;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.AsyncTask;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.FileProvider;

import com.dropbox.core.v2.DbxClientV2;
import com.dropbox.core.v2.files.FileMetadata;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.drive.Drive;
import com.google.android.gms.drive.DriveApi;
import com.google.android.gms.drive.DriveContents;
import com.google.android.gms.drive.DriveFolder;
import com.google.android.gms.drive.DriveId;
import com.google.android.gms.drive.MetadataChangeSet;
import com.google.firebase.crashlytics.FirebaseCrashlytics;
import com.owncloud.android.lib.common.OwnCloudClient;
import com.owncloud.android.lib.common.OwnCloudClientFactory;
import com.owncloud.android.lib.common.OwnCloudCredentialsFactory;
import com.owncloud.android.lib.common.operations.RemoteOperationResult;
import com.owncloud.android.lib.resources.files.CreateRemoteFolderOperation;
import com.owncloud.android.lib.resources.files.FileUtils;
import com.owncloud.android.lib.resources.files.UploadRemoteFileOperation;

import org.gnucash.android.R;
import org.gnucash.android.app.GnuCashApplication;
import org.gnucash.android.db.adapter.AccountsDbAdapter;
import org.gnucash.android.db.adapter.DatabaseAdapter;
import org.gnucash.android.db.adapter.SplitsDbAdapter;
import org.gnucash.android.db.adapter.TransactionsDbAdapter;
import org.gnucash.android.export.csv.CsvAccountExporter;
import org.gnucash.android.export.csv.CsvTransactionsExporter;
import org.gnucash.android.export.ofx.OfxExporter;
import org.gnucash.android.export.qif.QifExporter;
import org.gnucash.android.export.xml.GncXmlExporter;
import org.gnucash.android.model.Transaction;
import org.gnucash.android.ui.account.AccountsActivity;
import org.gnucash.android.ui.account.AccountsListFragment;
import org.gnucash.android.ui.settings.BackupPreferenceFragment;
import org.gnucash.android.ui.transaction.TransactionsActivity;
import org.gnucash.android.util.BackupManager;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Asynchronous task for exporting transactions.
 *
 * @author Ngewi Fet <ngewif@gmail.com>
 */
public class ExportAsyncTask extends AsyncTask<ExportParams, Void, Integer> {

    /**
     * App context
     */
    private final Context mContext;

    private ProgressDialog mProgressDialog;

    private final SQLiteDatabase mDb;

    /**
     * Log tag
     */
    public static final String TAG = "ExportAsyncTask";

    /**
     * Export parameters
     */
    private ExportParams mExportParams;

    public ExportAsyncTask(Context context, SQLiteDatabase db) {
        this.mContext = context;
        this.mDb = db;
    }

    @Override
    protected void onPreExecute() {
        super.onPreExecute();
        if (mContext instanceof Activity) {
            mProgressDialog = new ProgressDialog(mContext);
            mProgressDialog.setTitle(R.string.title_progress_exporting_transactions);
            mProgressDialog.setIndeterminate(true);
            mProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
            mProgressDialog.setProgressNumberFormat(null);
            mProgressDialog.setProgressPercentFormat(null);

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
    protected Integer doInBackground(ExportParams... params) {
        final ExportParams exportParams = params[0];
        mExportParams = exportParams;
        Exporter exporter = getExporter(exportParams);
        List<String> exportedFiles;

        try {
            exportedFiles = exporter.generateExport();
        } catch (final Exception e) {
            Log.e(TAG, "Error exporting: " + e.getMessage());
            FirebaseCrashlytics.getInstance().recordException(e);
            e.printStackTrace();
            if (mContext instanceof Activity) {
                ((Activity) mContext).runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(mContext,
                                mContext.getString(R.string.toast_export_error, exportParams.getExportFormat().name())
                                        + "\n" + e.getMessage(),
                                Toast.LENGTH_SHORT).show();
                    }
                });
            }
            return 0;
        }

        if (exportedFiles.isEmpty())
            return 0;

        try {
            moveToTarget(exportParams, exporter, exportedFiles);
        } catch (Exporter.ExporterException e) {
            FirebaseCrashlytics.getInstance().log("Error sending exported files to target");
            FirebaseCrashlytics.getInstance().recordException(e);
            return -exportedFiles.size();
        }

        if (exportParams.shouldDeleteTransactionsAfterExport()) {
            backupAndDeleteTransactions();
        }

        return exportedFiles.size();
    }

    /**
     * Transmits the exported transactions to the designated location, either SD card or third-party application
     * Finishes the activity if the export was starting  in the context of an activity
     *
     * @param exportSuccessful Result of background export execution
     */
    @Override
    protected void onPostExecute(Integer exportSuccessful) {
        final ExportParams exportParams = mExportParams;
        if (exportSuccessful > 0) {
            if (mContext instanceof Activity)
                reportSuccess(exportParams);

            if (exportParams.shouldDeleteTransactionsAfterExport()) {
                refreshViews();
            }
        } else {
            if (mContext instanceof Activity) {
                dismissProgressDialog();
                if (exportSuccessful == 0) {
                    Toast.makeText(mContext,
                            R.string.toast_no_transactions_to_export,
                            Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(mContext,
                            mContext.getString(R.string.toast_export_error, exportParams.getExportFormat().name()),
                            Toast.LENGTH_LONG).show();
                }
            }
        }

        dismissProgressDialog();
    }

    private void dismissProgressDialog() {
        if (mContext instanceof Activity) {
            if (mProgressDialog != null && mProgressDialog.isShowing())
                mProgressDialog.dismiss();
            ((Activity) mContext).finish();
        }
    }

    /**
     * Returns an exporter corresponding to the user settings.
     *
     * @return Object of one of {@link QifExporter}, {@link OfxExporter} or {@link GncXmlExporter}, {@Link CsvAccountExporter} or {@Link CsvTransactionsExporter}
     */
    private Exporter getExporter(ExportParams exportParams) {
        switch (exportParams.getExportFormat()) {
            case QIF:
                return new QifExporter(exportParams, mDb);
            case OFX:
                return new OfxExporter(exportParams, mDb);
            case CSVA:
                return new CsvAccountExporter(exportParams, mDb);
            case CSVT:
                return new CsvTransactionsExporter(exportParams, mDb);
            case XML:
            default:
                return new GncXmlExporter(exportParams, mDb);
        }
    }

    /**
     * Moves the generated export files to the target specified by the user
     *
     * @throws Exporter.ExporterException if the move fails
     */
    private void moveToTarget(ExportParams exportParams, Exporter exporter, List<String> exportedFiles) throws Exporter.ExporterException {
        switch (exportParams.getExportTarget()) {
            case SHARING:
                shareFiles(exportParams, exportedFiles);
                break;

            case DROPBOX:
                moveExportToDropbox(exportParams, exportedFiles);
                break;

            case GOOGLE_DRIVE:
                moveExportToGoogleDrive(exportParams, exporter, exportedFiles);
                break;

            case OWNCLOUD:
                moveExportToOwnCloud(exportParams, exporter, exportedFiles);
                break;

            case SD_CARD:
                moveExportToSDCard(exportParams, exporter, exportedFiles);
                break;

            case URI:
                moveExportToUri(exportParams, exportedFiles);
                break;

            default:
                throw new Exporter.ExporterException(exportParams, "Invalid target");
        }
    }

    /**
     * Move the exported files to a specified URI.
     * This URI could be a Storage Access Framework file
     *
     * @throws Exporter.ExporterException if something failed while moving the exported file
     */
    private void moveExportToUri(ExportParams exportParams, List<String> exportedFiles) throws Exporter.ExporterException {
        Uri exportUri = exportParams.getExportLocation();
        if (exportUri == null) {
            Log.w(TAG, "No URI found for export destination");
            return;
        }

        if (exportedFiles.size() > 0) {
            try {
                OutputStream outputStream = mContext.getContentResolver().openOutputStream(exportUri);
                // Now we always get just one file exported (multi-currency QIFs are zipped)
                org.gnucash.android.util.FileUtils.moveFile(exportedFiles.get(0), outputStream);
            } catch (IOException ex) {
                throw new Exporter.ExporterException(exportParams, ex);
            }
        }
    }

    /**
     * Move the exported files to a GnuCash folder on Google Drive
     *
     * @throws Exporter.ExporterException if something failed while moving the exported file
     * @deprecated Explicit Google Drive integration is deprecated, use Storage Access Framework. See {@link #moveExportToUri()}
     */
    @Deprecated
    private void moveExportToGoogleDrive(ExportParams exportParams, Exporter exporter, List<String> exportedFiles) throws Exporter.ExporterException {
        Log.i(TAG, "Moving exported file to Google Drive");
        final GoogleApiClient googleApiClient = BackupPreferenceFragment.getGoogleApiClient(GnuCashApplication.getAppContext());
        googleApiClient.blockingConnect();

        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(mContext);
        String folderId = sharedPreferences.getString(mContext.getString(R.string.key_google_drive_app_folder_id), "");
        DriveFolder folder = DriveId.decodeFromString(folderId).asDriveFolder();
        try {
            for (String exportedFilePath : exportedFiles) {
                DriveApi.DriveContentsResult driveContentsResult =
                        Drive.DriveApi.newDriveContents(googleApiClient).await(1, TimeUnit.MINUTES);
                if (!driveContentsResult.getStatus().isSuccess()) {
                    throw new Exporter.ExporterException(exportParams,
                            "Error while trying to create new file contents");
                }
                final DriveContents driveContents = driveContentsResult.getDriveContents();
                OutputStream outputStream = driveContents.getOutputStream();
                File exportedFile = new File(exportedFilePath);
                FileInputStream fileInputStream = new FileInputStream(exportedFile);
                byte[] buffer = new byte[1024];
                int count;

                while ((count = fileInputStream.read(buffer)) >= 0) {
                    outputStream.write(buffer, 0, count);
                }
                fileInputStream.close();
                outputStream.flush();
                exportedFile.delete();

                MetadataChangeSet changeSet = new MetadataChangeSet.Builder()
                        .setTitle(exportedFile.getName())
                        .setMimeType(exporter.getExportMimeType())
                        .build();
                // create a file on root folder
                DriveFolder.DriveFileResult driveFileResult =
                        folder.createFile(googleApiClient, changeSet, driveContents)
                                .await(1, TimeUnit.MINUTES);
                if (!driveFileResult.getStatus().isSuccess())
                    throw new Exporter.ExporterException(exportParams, "Error creating file in Google Drive");

                Log.i(TAG, "Created file with id: " + driveFileResult.getDriveFile().getDriveId());
            }
        } catch (IOException e) {
            throw new Exporter.ExporterException(exportParams, e);
        }
    }

    /**
     * Move the exported files (in the cache directory) to Dropbox
     */
    private void moveExportToDropbox(ExportParams exportParams, List<String> exportedFiles) {
        Log.i(TAG, "Uploading exported files to DropBox");

        DbxClientV2 dbxClient = DropboxHelper.getClient();

        for (String exportedFilePath : exportedFiles) {
            File exportedFile = new File(exportedFilePath);
            try {
                FileInputStream inputStream = new FileInputStream(exportedFile);
                FileMetadata metadata = dbxClient.files()
                        .uploadBuilder("/" + exportedFile.getName())
                        .uploadAndFinish(inputStream);
                Log.i(TAG, "Successfully uploaded file " + metadata.getName() + " to DropBox");
                inputStream.close();
                exportedFile.delete(); //delete file to prevent cache accumulation
            } catch (IOException e) {
                FirebaseCrashlytics.getInstance().recordException(e);
                Log.e(TAG, e.getMessage());
            } catch (com.dropbox.core.DbxException e) {
                e.printStackTrace();
            }
        }
    }

    private void moveExportToOwnCloud(ExportParams exportParams, Exporter exporter, List<String> exportedFiles) throws Exporter.ExporterException {
        Log.i(TAG, "Copying exported file to ownCloud");

        SharedPreferences mPrefs = mContext.getSharedPreferences(mContext.getString(R.string.owncloud_pref), Context.MODE_PRIVATE);

        Boolean mOC_sync = mPrefs.getBoolean(mContext.getString(R.string.owncloud_sync), false);

        if (!mOC_sync) {
            throw new Exporter.ExporterException(exportParams, "ownCloud not enabled.");
        }

        String mOC_server = mPrefs.getString(mContext.getString(R.string.key_owncloud_server), null);
        String mOC_username = mPrefs.getString(mContext.getString(R.string.key_owncloud_username), null);
        String mOC_password = mPrefs.getString(mContext.getString(R.string.key_owncloud_password), null);
        String mOC_dir = mPrefs.getString(mContext.getString(R.string.key_owncloud_dir), null);

        Uri serverUri = Uri.parse(mOC_server);
        OwnCloudClient mClient = OwnCloudClientFactory.createOwnCloudClient(serverUri, this.mContext, true);
        mClient.setCredentials(
                OwnCloudCredentialsFactory.newBasicCredentials(mOC_username, mOC_password)
        );

        if (mOC_dir.length() != 0) {
            RemoteOperationResult dirResult = new CreateRemoteFolderOperation(
                    mOC_dir, true).execute(mClient);
            if (!dirResult.isSuccess()) {
                Log.w(TAG, "Error creating folder (it may happen if it already exists): "
                        + dirResult.getLogMessage());
            }
        }
        for (String exportedFilePath : exportedFiles) {
            String remotePath = mOC_dir + FileUtils.PATH_SEPARATOR + stripPathPart(exportedFilePath);
            String mimeType = exporter.getExportMimeType();

            RemoteOperationResult result = new UploadRemoteFileOperation(
                    exportedFilePath, remotePath, mimeType,
                    getFileLastModifiedTimestamp(exportedFilePath))
                    .execute(mClient);
            if (!result.isSuccess())
                throw new Exporter.ExporterException(exportParams, result.getLogMessage());

            new File(exportedFilePath).delete();
        }
    }

    private static String getFileLastModifiedTimestamp(String path) {
        Long timeStampLong = new File(path).lastModified() / 1000;
        return timeStampLong.toString();
    }

    /**
     * Moves the exported files from the internal storage where they are generated to
     * external storage, which is accessible to the user.
     *
     * @return The list of files moved to the SD card.
     * @deprecated Use the Storage Access Framework to save to SD card. See {@link #moveExportToUri()}
     */
    @Deprecated
    private List<String> moveExportToSDCard(ExportParams exportParams, Exporter exporter, List<String> exportedFiles) throws Exporter.ExporterException {
        Log.i(TAG, "Moving exported file to external storage");
        new File(Exporter.getExportFolderPath(exporter.getBookUID()));
        List<String> dstFiles = new ArrayList<>();

        for (String src : exportedFiles) {
            String dst = Exporter.getExportFolderPath(exporter.getBookUID()) + stripPathPart(src);
            try {
                org.gnucash.android.util.FileUtils.moveFile(src, dst);
                dstFiles.add(dst);
            } catch (IOException e) {
                throw new Exporter.ExporterException(exportParams, e);
            }
        }

        return dstFiles;
    }

    // "/some/path/filename.ext" -> "filename.ext"
    private String stripPathPart(String fullPathName) {
        return (new File(fullPathName)).getName();
    }

    /**
     * Backups of the database, saves opening balances (if necessary)
     * and deletes all non-template transactions in the database.
     */
    private void backupAndDeleteTransactions() {
        Log.i(TAG, "Backup and deleting transactions after export");
        BackupManager.backupActiveBook(); //create backup before deleting everything
        List<Transaction> openingBalances = new ArrayList<>();
        boolean preserveOpeningBalances = GnuCashApplication.shouldSaveOpeningBalances(false);

        TransactionsDbAdapter transactionsDbAdapter = new TransactionsDbAdapter(mDb, new SplitsDbAdapter(mDb));
        if (preserveOpeningBalances) {
            openingBalances = new AccountsDbAdapter(mDb, transactionsDbAdapter).getAllOpeningBalanceTransactions();
        }
        transactionsDbAdapter.deleteAllNonTemplateTransactions();

        if (preserveOpeningBalances) {
            transactionsDbAdapter.bulkAddRecords(openingBalances, DatabaseAdapter.UpdateMethod.insert);
        }
    }

    /**
     * Starts an intent chooser to allow the user to select an activity to receive
     * the exported files.
     *
     * @param paths list of full paths of the files to send to the activity.
     */
    private void shareFiles(ExportParams exportParams, List<String> paths) {
        Intent shareIntent = new Intent(Intent.ACTION_SEND_MULTIPLE);
        shareIntent.setType("text/xml");

        ArrayList<Uri> exportFiles = convertFilePathsToUris(paths);
        shareIntent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, exportFiles);
        shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        shareIntent.putExtra(Intent.EXTRA_SUBJECT, mContext.getString(R.string.title_export_email,
            exportParams.getExportFormat().name()));

        String defaultEmail = PreferenceManager.getDefaultSharedPreferences(mContext)
                .getString(mContext.getString(R.string.key_default_export_email), null);
        if (defaultEmail != null && defaultEmail.trim().length() > 0)
            shareIntent.putExtra(Intent.EXTRA_EMAIL, new String[]{defaultEmail});

        SimpleDateFormat formatter = (SimpleDateFormat) SimpleDateFormat.getDateTimeInstance();
        String extraText = mContext.getString(R.string.description_export_email)
                + " " + formatter.format(new Date(System.currentTimeMillis()));
        shareIntent.putExtra(Intent.EXTRA_TEXT, extraText);

        if (mContext instanceof Activity) {
            List<ResolveInfo> activities = mContext.getPackageManager().queryIntentActivities(shareIntent, 0);
            if (activities != null && !activities.isEmpty()) {
                mContext.startActivity(Intent.createChooser(shareIntent,
                        mContext.getString(R.string.title_select_export_destination)));
            } else {
                Toast.makeText(mContext, R.string.toast_no_compatible_apps_to_receive_export,
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    /**
     * Convert file paths to URIs by adding the file// prefix
     * <p>e.g. /some/path/file.ext --> file:///some/path/file.ext</p>
     *
     * @param paths List of file paths to convert
     * @return List of file URIs
     */
    @NonNull
    private ArrayList<Uri> convertFilePathsToUris(List<String> paths) {
        ArrayList<Uri> exportFiles = new ArrayList<>();

        for (String path : paths) {
            File file = new File(path);
            Uri contentUri = FileProvider.getUriForFile(GnuCashApplication.getAppContext(), GnuCashApplication.FILE_PROVIDER_AUTHORITY, file);
            exportFiles.add(contentUri);
        }
        return exportFiles;
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
            case GOOGLE_DRIVE:
                targetLocation = "Google Drive -> " + mContext.getString(R.string.app_name);
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
        if (mContext instanceof AccountsActivity) {
            AccountsListFragment fragment =
                    ((AccountsActivity) mContext).getCurrentAccountListFragment();
            if (fragment != null)
                fragment.refresh();
        }
        if (mContext instanceof TransactionsActivity) {
            ((TransactionsActivity) mContext).refresh();
        }
    }
}
