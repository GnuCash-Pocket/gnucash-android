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
package org.gnucash.android.export

import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo
import android.database.SQLException
import android.net.Uri
import android.os.CancellationSignal
import android.text.format.DateUtils
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.preference.PreferenceManager
import com.dropbox.core.DbxException
import com.owncloud.android.lib.common.OwnCloudClientFactory
import com.owncloud.android.lib.common.OwnCloudCredentialsFactory
import com.owncloud.android.lib.resources.files.CreateRemoteFolderOperation
import com.owncloud.android.lib.resources.files.FileUtils
import com.owncloud.android.lib.resources.files.UploadRemoteFileOperation
import org.gnucash.android.BuildConfig
import org.gnucash.android.R
import org.gnucash.android.app.GnuCashApplication
import org.gnucash.android.app.GnuCashApplication.Companion.activeBookUID
import org.gnucash.android.app.GnuCashApplication.Companion.shouldSaveOpeningBalances
import org.gnucash.android.db.DatabaseHelper
import org.gnucash.android.db.DatabaseHolder
import org.gnucash.android.db.DatabaseSchema.BookEntry
import org.gnucash.android.db.adapter.AccountsDbAdapter
import org.gnucash.android.db.adapter.BooksDbAdapter
import org.gnucash.android.db.adapter.BudgetsDbAdapter
import org.gnucash.android.db.adapter.CommoditiesDbAdapter
import org.gnucash.android.db.adapter.DatabaseAdapter
import org.gnucash.android.db.adapter.PricesDbAdapter
import org.gnucash.android.db.adapter.RecurrenceDbAdapter
import org.gnucash.android.db.adapter.ScheduledActionDbAdapter
import org.gnucash.android.db.adapter.SplitsDbAdapter
import org.gnucash.android.db.adapter.TransactionsDbAdapter
import org.gnucash.android.export.DropboxHelper.getClient
import org.gnucash.android.export.ExportParams.ExportTarget
import org.gnucash.android.gnc.GncProgressListener
import org.gnucash.android.model.Transaction
import org.gnucash.android.ui.settings.OwnCloudPreferences
import org.gnucash.android.ui.snackLong
import org.gnucash.android.util.BackupManager
import org.gnucash.android.util.BackupManager.getBackupFolder
import org.gnucash.android.util.FileUtils.moveFile
import org.gnucash.android.util.formatFullDateTime
import org.joda.time.format.DateTimeFormat
import timber.log.Timber
import java.io.BufferedWriter
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.io.Writer
import java.nio.charset.StandardCharsets
import java.util.zip.GZIPOutputStream

/**
 * Base class for the different exporters
 *
 * @author Ngewi Fet <ngewif@gmail.com>
 * @author Yongxin Wang <fefe.wyx@gmail.com>
 */
abstract class Exporter protected constructor(
    protected val context: Context,
    /**
     * Export options
     */
    protected val exportParams: ExportParams,
    /**
     * GUID of the book being exported
     */
    val bookUID: String,
    protected val listener: GncProgressListener?
) {
    /**
     * Cache directory to which files will be first exported before moved to final destination.
     *
     * There is a different cache dir per export format, which has the name of the export format.<br></br>
     * The cache dir is cleared every time a new [Exporter] is instantiated.
     * The files created here are only accessible within this application, and should be copied to SD card before they can be shared
     *
     */
    private val cacheDir: File

    protected val booksDbAdapter: BooksDbAdapter = BooksDbAdapter.instance

    /**
     * Adapter for retrieving accounts to export
     * Subclasses should close this object when they are done with exporting
     */
    protected val accountsDbAdapter: AccountsDbAdapter
    protected val transactionsDbAdapter: TransactionsDbAdapter
    protected val splitsDbAdapter: SplitsDbAdapter
    protected val scheduledActionDbAdapter: ScheduledActionDbAdapter
    protected val pricesDbAdapter: PricesDbAdapter
    protected val commoditiesDbAdapter: CommoditiesDbAdapter
    protected val budgetsDbAdapter: BudgetsDbAdapter
    private var exportCacheFile: File?

    /**
     * Database being currently exported
     */
    protected val holder: DatabaseHolder

    protected val cancellationSignal: CancellationSignal = CancellationSignal()

    init {
        val dbHelper = DatabaseHelper(context, bookUID)
        holder = dbHelper.readableHolder
        commoditiesDbAdapter = CommoditiesDbAdapter(holder)
        pricesDbAdapter = PricesDbAdapter(commoditiesDbAdapter)
        splitsDbAdapter = SplitsDbAdapter(commoditiesDbAdapter)
        transactionsDbAdapter = TransactionsDbAdapter(splitsDbAdapter)
        accountsDbAdapter = AccountsDbAdapter(transactionsDbAdapter, pricesDbAdapter)
        val recurrenceDbAdapter = RecurrenceDbAdapter(holder)
        budgetsDbAdapter = BudgetsDbAdapter(recurrenceDbAdapter)
        scheduledActionDbAdapter = ScheduledActionDbAdapter(recurrenceDbAdapter, transactionsDbAdapter)

        exportCacheFile = null
        cacheDir = File(context.cacheDir, exportParams.exportFormat.name)
        cacheDir.mkdirs()
        purgeDirectory(cacheDir)
    }

    /**
     * Generates the export output
     *
     * @return the export location.
     * @throws ExporterException if an error occurs during export
     */
    @Throws(ExporterException::class)
    fun export(): Uri? {
        Timber.i("generate export")
        val exportParams = this.exportParams
        val result: Uri?
        try {
            val file = writeToFile(exportParams)
            if (file == null) return null
            result = moveToTarget(exportParams, file)
        } catch (ee: ExporterException) {
            throw ee
        } catch (e: Throwable) {
            throw ExporterException(exportParams, e)
        }

        if (result != null && exportParams.deleteTransactionsAfterExport) {
            // Avoid recursion - Don't do a backup if just did a backup already!
            val context = this.context
            val bookUID = activeBookUID
            val backupFolder = getBackupFolder(context, bookUID!!)
            val backupUri = exportParams.exportLocation
            val backupFile = File(backupUri!!.path!!)
            val backupFileParent = backupFile.parentFile
            val isBackupParams = exportParams.exportFormat == ExportFormat.XML
                    && exportParams.exportTarget == ExportTarget.URI
                    && exportParams.isCompressed
                    && backupFolder == backupFileParent

            if (!isBackupParams) {
                //create backup before deleting everything
                BackupManager.backupBook(context, bookUID)
            }

            deleteTransactions()
        }
        try {
            close()
        } catch (_: Exception) {
        }
        return result
    }

    @Throws(ExporterException::class)
    protected open fun writeToFile(exportParams: ExportParams): File? {
        val cacheFile = getExportCacheFile(exportParams)
        try {
            createWriter(cacheFile).use { writer ->
                writeExport(writer, exportParams)
            }
        } catch (ee: ExporterException) {
            throw ee
        } catch (e: Exception) {
            throw ExporterException(exportParams, e)
        }
        return cacheFile
    }

    @Throws(ExporterException::class, IOException::class)
    protected abstract fun writeExport(writer: Writer, exportParams: ExportParams)

    /**
     * Recursively delete all files in a directory
     *
     * @param directory File descriptor for directory
     */
    private fun purgeDirectory(directory: File) {
        val files = directory.listFiles() ?: return
        for (file in files) {
            if (file.isDirectory) {
                purgeDirectory(file)
            } else {
                file.delete()
            }
        }
    }

    /**
     * Returns the path to the file where the exporter should save the export during generation
     *
     * This path is a temporary cache file whose file extension matches the export format.<br></br>
     * This file is deleted every time a new export is started
     *
     * @return Absolute path to file
     */
    protected fun getExportCacheFile(exportParams: ExportParams): File {
        // The file name contains a timestamp, so ensure it doesn't change with multiple calls to
        // avoid issues like #448
        var exportCacheFile = exportCacheFile
        if (exportCacheFile == null) {
            val bookName =
                booksDbAdapter.getAttribute(bookUID, BookEntry.COLUMN_DISPLAY_NAME)
            val name =
                buildExportFilename(exportParams.exportFormat, exportParams.isCompressed, bookName)
            exportCacheFile = File(cacheDir, name)
        }
        return exportCacheFile
    }

    /**
     * Returns the MIME type for this exporter.
     *
     * @return MIME type as string
     */
    val exportMimeType: String
        get() = exportParams.exportFormat.mimeType

    @Throws(IOException::class, SQLException::class)
    protected fun close() {
        accountsDbAdapter.close()
        budgetsDbAdapter.close()
        commoditiesDbAdapter.close()
        pricesDbAdapter.close()
        scheduledActionDbAdapter.close()
        splitsDbAdapter.close()
        transactionsDbAdapter.close()
        holder.close()
    }

    @Throws(IOException::class)
    protected fun createWriter(file: File): Writer {
        var output: OutputStream = FileOutputStream(file)
        if (exportParams.isCompressed) {
            output = GZIPOutputStream(output)
        }
        return BufferedWriter(OutputStreamWriter(output, StandardCharsets.UTF_8))
    }

    /**
     * Moves the generated export files to the target specified by the user
     *
     * @param cacheFile the cached file to read from.
     * @throws Exporter.ExporterException if the move fails
     */
    @Throws(ExporterException::class)
    private fun moveToTarget(exportParams: ExportParams, cacheFile: File): Uri? {
        when (exportParams.exportTarget) {
            ExportTarget.SHARING -> return shareFiles(exportParams, cacheFile)

            ExportTarget.DROPBOX -> return moveExportToDropbox(exportParams, cacheFile)

            ExportTarget.OWNCLOUD -> return moveExportToOwnCloud(exportParams, cacheFile)

            ExportTarget.SD_CARD -> return moveExportToSDCard(exportParams, cacheFile)

            ExportTarget.URI -> return moveExportToUri(exportParams, cacheFile)
        }
    }

    /**
     * Move the exported files to a specified URI.
     * This URI could be a Storage Access Framework file
     *
     * @throws Exporter.ExporterException if something failed while moving the exported file
     */
    @Throws(ExporterException::class)
    private fun moveExportToUri(exportParams: ExportParams, exportedFile: File): Uri? {
        val context = this.context
        val exportUri = exportParams.exportLocation
        if (exportUri == null) {
            Timber.w("No URI found for export destination")
            return null
        }

        try {
            val outputStream: OutputStream =
                context.contentResolver.openOutputStream(exportUri)!!
            // Now we always get just one file exported (multi-currency QIFs are zipped)
            moveFile(exportedFile, outputStream)
            return exportUri
        } catch (e: IOException) {
            throw ExporterException(exportParams, e)
        }
    }

    /**
     * Move the exported files (in the cache directory) to Dropbox
     */
    private fun moveExportToDropbox(exportParams: ExportParams, exportedFile: File): Uri? {
        Timber.i("Uploading exported files to DropBox")
        val context = this.context
        val dbxClient = getClient(context)
        if (dbxClient == null) {
            throw ExporterException(exportParams, "Dropbox client required")
        }

        try {
            val inputStream = FileInputStream(exportedFile)
            val metadata = dbxClient.files()
                .uploadBuilder("/" + exportedFile.getName())
                .uploadAndFinish(inputStream)
            Timber.i("Successfully uploaded file %s to DropBox", metadata.getName())
            inputStream.close()
            exportedFile.delete() //delete file to prevent cache accumulation

            return Uri.Builder()
                .scheme("dropbox")
                .authority(BuildConfig.APPLICATION_ID)
                .appendPath("Apps")
                .appendPath("GnuCash Pocket")
                .appendPath(metadata.getName())
                .build()
        } catch (e: IOException) {
            Timber.e(e)
            throw ExporterException(exportParams, e)
        } catch (e: DbxException) {
            Timber.e(e)
            throw ExporterException(exportParams, e)
        }
    }

    @Throws(ExporterException::class)
    private fun moveExportToOwnCloud(exportParams: ExportParams, exportedFile: File): Uri? {
        Timber.i("Copying exported file to ownCloud")
        val context = this.context
        val preferences = OwnCloudPreferences(context)

        val ocSync = preferences.isSync

        if (!ocSync) {
            throw ExporterException(exportParams, "ownCloud not enabled.")
        }

        val ocServer = preferences.server!!
        val ocUsername = preferences.username
        val ocPassword = preferences.password
        val ocDir = preferences.dir

        val serverUri = ocServer.toUri()
        val client = OwnCloudClientFactory.createOwnCloudClient(serverUri, context, true)
        client.credentials = OwnCloudCredentialsFactory.newBasicCredentials(ocUsername, ocPassword)

        if (!ocDir.isNullOrEmpty()) {
            val dirResult = CreateRemoteFolderOperation(ocDir, true).execute(client)
            if (!dirResult.isSuccess) {
                Timber.w(
                    "Error creating folder (it may happen if it already exists): %s",
                    dirResult.logMessage
                )
            }
        }

        val remotePath = ocDir + FileUtils.PATH_SEPARATOR + exportedFile.name
        val mimeType = this.exportMimeType

        val result = UploadRemoteFileOperation(
            exportedFile.absolutePath,
            remotePath,
            mimeType,
            getFileLastModifiedTimestamp(exportedFile)
        ).execute(client)
        if (!result.isSuccess) {
            throw ExporterException(exportParams, result.logMessage)
        }

        exportedFile.delete()

        return serverUri.buildUpon()
            .appendPath(ocDir)
            .appendPath(exportedFile.name)
            .build()
    }

    /**
     * Moves the exported files from the internal storage where they are generated to
     * external storage, which is accessible to the user.
     *
     * @return The list of files moved to the SD card.
     */
    @Deprecated("Use the Storage Access Framework to save to SD card. See {@link #moveExportToUri(ExportParams, File)}")
    @Throws(ExporterException::class)
    private fun moveExportToSDCard(exportParams: ExportParams, exportedFile: File): Uri? {
        Timber.i("Moving exported file to external storage")
        val context = this.context
        val dst = File(
            getExportFolderPath(
                context,
                this.bookUID
            ), exportedFile.getName()
        )
        try {
            moveFile(exportedFile, dst)
            return dst.toUri()
        } catch (e: IOException) {
            throw ExporterException(exportParams, e)
        }
    }

    /**
     * Starts an intent chooser to allow the user to select an activity to receive
     * the exported files.
     *
     * @param exportedFile the file to share.
     */
    private fun shareFiles(exportParams: ExportParams, exportedFile: File): Uri? {
        val context = this.context
        val exportFile = FileProvider.getUriForFile(
            context,
            GnuCashApplication.FILE_PROVIDER_AUTHORITY,
            exportedFile
        )
        val shareIntent = Intent(Intent.ACTION_SEND)
            .setType(exportParams.exportFormat.mimeType)
            .putExtra(Intent.EXTRA_STREAM, exportFile)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            .putExtra(
                Intent.EXTRA_SUBJECT, context.getString(
                    R.string.title_export_email,
                    exportParams.exportFormat.name
                )
            )

        val defaultEmail = PreferenceManager.getDefaultSharedPreferences(context)
            .getString(context.getString(R.string.key_default_export_email), null)
        if (!defaultEmail.isNullOrEmpty()) {
            shareIntent.putExtra(Intent.EXTRA_EMAIL, arrayOf(defaultEmail))
        }

        val extraText = (context.getString(R.string.description_export_email)
                + " " + formatFullDateTime(System.currentTimeMillis()))
        shareIntent.putExtra(Intent.EXTRA_TEXT, extraText)

        val activities: List<ResolveInfo?>? =
            context.packageManager.queryIntentActivities(shareIntent, 0)
        if (activities != null && !activities.isEmpty()) {
            context.startActivity(
                Intent.createChooser(
                    shareIntent,
                    context.getString(R.string.title_select_export_destination)
                )
            )
            return exportFile
        } else {
            context.snackLong(R.string.toast_no_compatible_apps_to_receive_export)
        }

        return null
    }

    /**
     * Saves opening balances (if necessary)
     * and deletes all non-template transactions in the database.
     */
    private fun deleteTransactions() {
        Timber.i("Deleting transactions after export")
        var openingBalances = emptyList<Transaction>()
        val preserveOpeningBalances = shouldSaveOpeningBalances(false)

        val transactionsDbAdapter = this.transactionsDbAdapter
        if (preserveOpeningBalances) {
            openingBalances = accountsDbAdapter.allOpeningBalanceTransactions
        }
        transactionsDbAdapter.deleteAllNonTemplateTransactions()

        if (preserveOpeningBalances && !openingBalances.isEmpty()) {
            transactionsDbAdapter.bulkAddRecords(
                openingBalances,
                DatabaseAdapter.UpdateMethod.Insert
            )
        }
    }

    fun cancel() {
        cancellationSignal.cancel()
    }

    class ExporterException : RuntimeException {
        constructor(params: ExportParams, msg: String) :
                super("Failed to generate export with parameters: $params - $msg")

        constructor(params: ExportParams, throwable: Throwable) :
                super(
                    "Failed to generate export ${params.exportFormat} - ${throwable.message}",
                    throwable
                )
    }

    companion object {
        private const val EXPORT_FILENAME_DATE_PATTERN = "yyyyMMddHHmmss"

        /**
         * Strings a string of any characters not allowed in a file name.
         * All unallowed characters are replaced with an underscore
         *
         * @param inputName Raw file name input
         * @return Sanitized file name
         */
        fun sanitizeFilename(inputName: String): String {
            return inputName.replace("[^a-zA-Z0-9-_\\.]".toRegex(), "_")
        }

        /**
         * Builds a file name based on the current time stamp for the exported file
         *
         * @param exportParams Parameters to use when exporting
         * @param bookName     Name of the book being exported. This name will be included in the generated file name
         * @return String containing the file name
         */
        fun buildExportFilename(exportParams: ExportParams?, bookName: String): String {
            var format = ExportFormat.XML
            var isCompressed = false
            if (exportParams != null) {
                format = exportParams.exportFormat
                isCompressed = exportParams.isCompressed
            }
            return buildExportFilename(format, isCompressed, bookName)
        }

        /**
         * Builds a file name based on the current time stamp for the exported file
         *
         * @param format       Format to use when exporting
         * @param isCompressed is the file compressed?
         * @param bookName     Name of the book being exported. This name will be included in the generated file name
         * @return String containing the file name
         */
        fun buildExportFilename(
            format: ExportFormat,
            isCompressed: Boolean,
            bookName: String
        ): String {
            val formatter = DateTimeFormat.forPattern(EXPORT_FILENAME_DATE_PATTERN)
            val name: StringBuilder = StringBuilder(sanitizeFilename(bookName))
            if (format == ExportFormat.CSVA) name.append(".accounts")
            if (format == ExportFormat.CSVT) name.append(".transactions")
            name.append(".")
                .append(formatter.print(System.currentTimeMillis()))
                .append(format.extension)
            if (isCompressed && format != ExportFormat.XML) name.append(".gz")
            return name.toString()
        }

        /**
         * Parses the name of an export file and returns the date of export
         *
         * @param filename Export file name generated by [.buildExportFilename]
         * @return Date in milliseconds
         */
        fun getExportTime(filename: String): Long {
            val tokens =
                filename.split("_".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            if (tokens.size < 2) {
                return 0
            }
            var timeMillis: Long = 0
            try {
                val formatter = DateTimeFormat.forPattern(EXPORT_FILENAME_DATE_PATTERN)
                timeMillis = formatter.parseMillis(tokens[0] + "_" + tokens[1])
            } catch (e: IllegalArgumentException) {
                Timber.e(e, "Error parsing time from file name: %s", e.message)
            }
            return timeMillis
        }

        /**
         * Returns that path to the export folder for the book with GUID `bookUID`.
         * This is the folder where exports like QIF and OFX will be saved for access by external programs
         *
         * @param bookUID GUID of the book being exported. Each book has its own export path
         * @return Absolute path to export folder for active book
         */
        fun getExportFolderPath(context: Context, bookUID: String): String {
            val external = context.getExternalFilesDir(null)
            val file = File(File(external, bookUID), "exports")
            if (!file.exists()) {
                file.mkdirs()
            }
            return file.absolutePath
        }

        private fun getFileLastModifiedTimestamp(file: File): String {
            val timeStampLong = file.lastModified() / DateUtils.SECOND_IN_MILLIS
            return timeStampLong.toString()
        }
    }
}
