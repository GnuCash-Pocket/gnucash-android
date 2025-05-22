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

import static org.gnucash.android.util.BackupManager.getBackupFolder;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ResolveInfo;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;

import com.dropbox.core.DbxException;
import com.dropbox.core.v2.DbxClientV2;
import com.dropbox.core.v2.files.FileMetadata;
import com.owncloud.android.lib.common.OwnCloudClient;
import com.owncloud.android.lib.common.OwnCloudClientFactory;
import com.owncloud.android.lib.common.OwnCloudCredentialsFactory;
import com.owncloud.android.lib.common.operations.RemoteOperationResult;
import com.owncloud.android.lib.resources.files.CreateRemoteFolderOperation;
import com.owncloud.android.lib.resources.files.UploadRemoteFileOperation;

import org.gnucash.android.BuildConfig;
import org.gnucash.android.R;
import org.gnucash.android.app.GnuCashApplication;
import org.gnucash.android.db.DatabaseHelper;
import org.gnucash.android.db.DatabaseSchema;
import org.gnucash.android.db.adapter.AccountsDbAdapter;
import org.gnucash.android.db.adapter.BooksDbAdapter;
import org.gnucash.android.db.adapter.BudgetsDbAdapter;
import org.gnucash.android.db.adapter.CommoditiesDbAdapter;
import org.gnucash.android.db.adapter.DatabaseAdapter;
import org.gnucash.android.db.adapter.PricesDbAdapter;
import org.gnucash.android.db.adapter.RecurrenceDbAdapter;
import org.gnucash.android.db.adapter.ScheduledActionDbAdapter;
import org.gnucash.android.db.adapter.SplitsDbAdapter;
import org.gnucash.android.db.adapter.TransactionsDbAdapter;
import org.gnucash.android.model.Transaction;
import org.gnucash.android.util.BackupManager;
import org.gnucash.android.util.DateExtKt;
import org.gnucash.android.util.FileUtils;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPOutputStream;

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
     */
    @Deprecated
    public static final String LEGACY_BASE_FOLDER_PATH = Environment.getExternalStorageDirectory() + File.separator + BuildConfig.APPLICATION_ID;

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

    private static final String EXPORT_FILENAME_DATE_PATTERN = "yyyyMMddHHmmss";

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
    private File exportCacheFile;

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
        mPricesDbAdapter = new PricesDbAdapter(mCommoditiesDbAdapter);
        mSplitsDbAdapter = new SplitsDbAdapter(mCommoditiesDbAdapter);
        mTransactionsDbAdapter = new TransactionsDbAdapter(mSplitsDbAdapter);
        mAccountsDbAdapter = new AccountsDbAdapter(mTransactionsDbAdapter, mPricesDbAdapter);
        RecurrenceDbAdapter recurrenceDbAdapter = new RecurrenceDbAdapter(db);
        mBudgetsDbAdapter = new BudgetsDbAdapter(recurrenceDbAdapter);
        mScheduledActionDbAdapter = new ScheduledActionDbAdapter(recurrenceDbAdapter);

        exportCacheFile = null;
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
     * @param exportParams Parameters to use when exporting
     * @param bookName     Name of the book being exported. This name will be included in the generated file name
     * @return String containing the file name
     */
    @NonNull
    public static String buildExportFilename(@Nullable ExportParams exportParams, String bookName) {
        ExportFormat format = ExportFormat.XML;
        boolean isCompressed = false;
        if (exportParams != null) {
            format = exportParams.getExportFormat();
            isCompressed = exportParams.isCompressed;
        }
        return buildExportFilename(format, isCompressed, bookName);
    }

    /**
     * Builds a file name based on the current time stamp for the exported file
     *
     * @param format       Format to use when exporting
     * @param isCompressed is the file compressed?
     * @param bookName     Name of the book being exported. This name will be included in the generated file name
     * @return String containing the file name
     */
    @NonNull
    public static String buildExportFilename(@NonNull ExportFormat format, boolean isCompressed, String bookName) {
        DateTimeFormatter formatter = DateTimeFormat.forPattern(EXPORT_FILENAME_DATE_PATTERN);
        StringBuilder name = new StringBuilder(sanitizeFilename(bookName));
        if (format == ExportFormat.CSVA) name.append(".accounts");
        if (format == ExportFormat.CSVT) name.append(".transactions");
        name.append(".")
            .append(formatter.print(System.currentTimeMillis()))
            .append(format.extension);
        if (isCompressed && format != ExportFormat.XML) name.append(".gz");
        return name.toString();
    }

    /**
     * Parses the name of an export file and returns the date of export
     *
     * @param filename Export file name generated by {@link #buildExportFilename(ExportFormat, boolean, String)}
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
     * @return the export location.
     * @throws ExporterException if an error occurs during export
     */
    @Nullable
    public Uri generateExport() throws ExporterException {
        Timber.i("generate export");
        final ExportParams exportParams = mExportParams;
        final Uri result;
        try {
            File file = writeToFile(exportParams);
            if (file == null) return null;
            result = moveToTarget(exportParams, file);
        } catch (ExporterException ee) {
            throw ee;
        } catch (Throwable e) {
            throw new ExporterException(exportParams, e);
        }

        if (result != null && exportParams.shouldDeleteTransactionsAfterExport()) {
            // Avoid recursion - Don't do a backup if just did a backup already!
            Context context = mContext;
            String bookUID = GnuCashApplication.getActiveBookUID();
            File backupFolder = getBackupFolder(context, bookUID);
            Uri backupUri = exportParams.getExportLocation();
            File backupFile = new File(backupUri.getPath());
            File backupFileParent = backupFile.getParentFile();
            final boolean isBackupParams = exportParams.getExportFormat() == ExportFormat.XML
                && exportParams.getExportTarget() == ExportParams.ExportTarget.URI
                && exportParams.isCompressed
                && backupFolder.equals(backupFileParent);

            if (!isBackupParams) {
                BackupManager.backupBook(context, bookUID); //create backup before deleting everything
            }

            deleteTransactions();
        }
        try {
            close();
        } catch (Exception ignore) {
        }
        return result;
    }

    @Nullable
    protected File writeToFile(@NonNull ExportParams exportParams) throws ExporterException, IOException {
        File cacheFile = getExportCacheFile(exportParams);
        try (Writer writer = createWriter(cacheFile)) {
            writeExport(writer, exportParams);
        } catch (ExporterException ee) {
            throw ee;
        } catch (Exception e) {
            throw new ExporterException(exportParams, e);
        }
        return cacheFile;
    }

    protected abstract void writeExport(@NonNull Writer writer, @NonNull ExportParams exportParams) throws ExporterException, IOException;

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
    protected File getExportCacheFile(ExportParams exportParams) {
        // The file name contains a timestamp, so ensure it doesn't change with multiple calls to
        // avoid issues like #448
        if (exportCacheFile == null) {
            String bookName = mBooksDbADapter.getAttribute(mBookUID, DatabaseSchema.BookEntry.COLUMN_DISPLAY_NAME);
            exportCacheFile = new File(mCacheDir, buildExportFilename(exportParams.getExportFormat(), exportParams.isCompressed, bookName));
        }
        return exportCacheFile;
    }

    /**
     * Returns that path to the export folder for the book with GUID {@code bookUID}.
     * This is the folder where exports like QIF and OFX will be saved for access by external programs
     *
     * @param bookUID GUID of the book being exported. Each book has its own export path
     * @return Absolute path to export folder for active book
     */
    @NonNull
    public static String getExportFolderPath(Context context, String bookUID) {
        File external = context.getExternalFilesDir(null);
        File file = new File(new File(external, bookUID), "exports");
        if (!file.exists()) {
            file.mkdirs();
        }
        return file.getAbsolutePath();
    }

    /**
     * Returns the MIME type for this exporter.
     *
     * @return MIME type as string
     */
    @NonNull
    public String getExportMimeType() {
        return mExportParams.getExportFormat().mimeType;
    }

    protected void close() throws IOException, SQLException {
        mAccountsDbAdapter.close();
        mBudgetsDbAdapter.close();
        mCommoditiesDbAdapter.close();
        mPricesDbAdapter.close();
        mScheduledActionDbAdapter.close();
        mSplitsDbAdapter.close();
        mTransactionsDbAdapter.close();
        mDb.close();
    }

    protected Writer createWriter(@NonNull File file) throws IOException {
        OutputStream output = new FileOutputStream(file);
        if (mExportParams.isCompressed) {
            output = new GZIPOutputStream(output);
        }
        return new BufferedWriter(new OutputStreamWriter(output, StandardCharsets.UTF_8));
    }

    /**
     * Moves the generated export files to the target specified by the user
     *
     * @param cacheFile the cached file to read from.
     * @throws Exporter.ExporterException if the move fails
     */
    private Uri moveToTarget(@NonNull ExportParams exportParams, @NonNull File cacheFile) throws Exporter.ExporterException {
        switch (exportParams.getExportTarget()) {
            case SHARING:
                return shareFiles(exportParams, cacheFile);

            case DROPBOX:
                return moveExportToDropbox(exportParams, cacheFile);

            case OWNCLOUD:
                return moveExportToOwnCloud(exportParams, cacheFile);

            case SD_CARD:
                return moveExportToSDCard(exportParams, cacheFile);

            case URI:
                return moveExportToUri(exportParams, cacheFile);

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
    private Uri moveExportToUri(ExportParams exportParams, File exportedFile) throws Exporter.ExporterException {
        Context context = mContext;
        Uri exportUri = exportParams.getExportLocation();
        if (exportUri == null) {
            Timber.w("No URI found for export destination");
            return null;
        }

        try {
            OutputStream outputStream = context.getContentResolver().openOutputStream(exportUri);
            // Now we always get just one file exported (multi-currency QIFs are zipped)
            FileUtils.moveFile(exportedFile, outputStream);
            return exportUri;
        } catch (IOException e) {
            throw new Exporter.ExporterException(exportParams, e);
        }
    }

    /**
     * Move the exported files (in the cache directory) to Dropbox
     */
    private Uri moveExportToDropbox(ExportParams exportParams, File exportedFile) {
        Timber.i("Uploading exported files to DropBox");
        Context context = mContext;
        DbxClientV2 dbxClient = DropboxHelper.getClient(context);
        if (dbxClient == null) {
            throw new Exporter.ExporterException(exportParams, "Dropbox client required");
        }

        try {
            FileInputStream inputStream = new FileInputStream(exportedFile);
            FileMetadata metadata = dbxClient.files()
                .uploadBuilder("/" + exportedFile.getName())
                .uploadAndFinish(inputStream);
            Timber.i("Successfully uploaded file " + metadata.getName() + " to DropBox");
            inputStream.close();
            exportedFile.delete(); //delete file to prevent cache accumulation

            return new Uri.Builder()
                .scheme("dropbox")
                .authority(BuildConfig.APPLICATION_ID)
                .appendPath("Apps")
                .appendPath("GnuCash Pocket")
                .appendPath(metadata.getName())
                .build();
        } catch (IOException | DbxException e) {
            Timber.e(e);
            throw new ExporterException(exportParams, e);
        }
    }

    private Uri moveExportToOwnCloud(ExportParams exportParams, File exportedFile) throws Exporter.ExporterException {
        Timber.i("Copying exported file to ownCloud");
        final Context context = mContext;
        SharedPreferences preferences = context.getSharedPreferences(context.getString(R.string.owncloud_pref), Context.MODE_PRIVATE);

        boolean ocSync = preferences.getBoolean(context.getString(R.string.owncloud_sync), false);

        if (!ocSync) {
            throw new Exporter.ExporterException(exportParams, "ownCloud not enabled.");
        }

        String ocServer = preferences.getString(context.getString(R.string.key_owncloud_server), null);
        String ocUsername = preferences.getString(context.getString(R.string.key_owncloud_username), null);
        String ocPassword = preferences.getString(context.getString(R.string.key_owncloud_password), null);
        String ocDir = preferences.getString(context.getString(R.string.key_owncloud_dir), null);

        Uri serverUri = Uri.parse(ocServer);
        OwnCloudClient client = OwnCloudClientFactory.createOwnCloudClient(serverUri, context, true);
        client.setCredentials(
            OwnCloudCredentialsFactory.newBasicCredentials(ocUsername, ocPassword)
        );

        if (!TextUtils.isEmpty(ocDir)) {
            RemoteOperationResult dirResult = new CreateRemoteFolderOperation(
                ocDir, true).execute(client);
            if (!dirResult.isSuccess()) {
                Timber.w("Error creating folder (it may happen if it already exists): %s", dirResult.getLogMessage());
            }
        }

        String remotePath = ocDir + com.owncloud.android.lib.resources.files.FileUtils.PATH_SEPARATOR + exportedFile.getName();
        String mimeType = getExportMimeType();

        RemoteOperationResult result = new UploadRemoteFileOperation(
            exportedFile.getAbsolutePath(),
            remotePath,
            mimeType,
            getFileLastModifiedTimestamp(exportedFile)
        ).execute(client);
        if (!result.isSuccess()) {
            throw new Exporter.ExporterException(exportParams, result.getLogMessage());
        }

        exportedFile.delete();

        return serverUri.buildUpon()
            .appendPath(ocDir)
            .appendPath(exportedFile.getName())
            .build();
    }

    private static String getFileLastModifiedTimestamp(File file) {
        long timeStampLong = file.lastModified() / 1000;
        return Long.toString(timeStampLong);
    }

    /**
     * Moves the exported files from the internal storage where they are generated to
     * external storage, which is accessible to the user.
     *
     * @return The list of files moved to the SD card.
     * @deprecated Use the Storage Access Framework to save to SD card. See {@link #moveExportToUri(ExportParams, File)}
     */
    private Uri moveExportToSDCard(ExportParams exportParams, File exportedFile) throws Exporter.ExporterException {
        Timber.i("Moving exported file to external storage");
        Context context = mContext;
        File dst = new File(Exporter.getExportFolderPath(context, getBookUID()), exportedFile.getName());
        try {
            FileUtils.moveFile(exportedFile, dst);
            return Uri.fromFile(dst);
        } catch (IOException e) {
            throw new Exporter.ExporterException(exportParams, e);
        }
    }

    // "/some/path/filename.ext" -> "filename.ext"
    private String stripPathPart(String fullPathName) {
        return (new File(fullPathName)).getName();
    }

    /**
     * Starts an intent chooser to allow the user to select an activity to receive
     * the exported files.
     *
     * @param exportedFile the file to share.
     */
    private Uri shareFiles(ExportParams exportParams, File exportedFile) {
        Context context = mContext;
        Uri exportFile = FileProvider.getUriForFile(context, GnuCashApplication.FILE_PROVIDER_AUTHORITY, exportedFile);
        Intent shareIntent = new Intent(Intent.ACTION_SEND)
            .setType(exportParams.getExportFormat().mimeType)
            .putExtra(Intent.EXTRA_STREAM, exportFile)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            .putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.title_export_email,
                exportParams.getExportFormat().name()));

        String defaultEmail = PreferenceManager.getDefaultSharedPreferences(context)
            .getString(context.getString(R.string.key_default_export_email), null);
        if (!TextUtils.isEmpty(defaultEmail))
            shareIntent.putExtra(Intent.EXTRA_EMAIL, new String[]{defaultEmail});

        String extraText = context.getString(R.string.description_export_email)
            + " " + DateExtKt.formatFullDateTime(System.currentTimeMillis());
        shareIntent.putExtra(Intent.EXTRA_TEXT, extraText);

        List<ResolveInfo> activities = context.getPackageManager().queryIntentActivities(shareIntent, 0);
        if (activities != null && !activities.isEmpty()) {
            context.startActivity(Intent.createChooser(shareIntent,
                context.getString(R.string.title_select_export_destination)));
            return exportFile;
        } else {
            Toast.makeText(context, R.string.toast_no_compatible_apps_to_receive_export,
                Toast.LENGTH_LONG).show();
        }

        return null;
    }

    /**
     * Saves opening balances (if necessary)
     * and deletes all non-template transactions in the database.
     */
    private void deleteTransactions() {
        Timber.i("Deleting transactions after export");
        List<Transaction> openingBalances = new ArrayList<>();
        boolean preserveOpeningBalances = GnuCashApplication.shouldSaveOpeningBalances(false);

        TransactionsDbAdapter transactionsDbAdapter = mTransactionsDbAdapter;
        if (preserveOpeningBalances) {
            openingBalances = mAccountsDbAdapter.getAllOpeningBalanceTransactions();
        }
        transactionsDbAdapter.deleteAllNonTemplateTransactions();

        if (preserveOpeningBalances && !openingBalances.isEmpty()) {
            transactionsDbAdapter.bulkAddRecords(openingBalances, DatabaseAdapter.UpdateMethod.insert);
        }
    }

    public static class ExporterException extends RuntimeException {

        public ExporterException(@NonNull ExportParams params) {
            super("Failed to generate export with parameters: " + params);
        }

        public ExporterException(@NonNull ExportParams params, @NonNull String msg) {
            super("Failed to generate export with parameters: " + params + " - " + msg);
        }

        public ExporterException(@NonNull ExportParams params, @NonNull Throwable throwable) {
            super("Failed to generate export " + params.getExportFormat() + " - " + throwable.getMessage(),
                throwable);
        }
    }
}
