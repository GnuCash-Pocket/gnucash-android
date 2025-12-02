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
package org.gnucash.android.util

import android.app.Activity
import android.app.ProgressDialog
import android.content.Context
import android.content.DialogInterface
import android.net.Uri
import android.os.AsyncTask
import androidx.annotation.WorkerThread
import androidx.core.net.toUri
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import androidx.work.WorkRequest
import org.gnucash.android.R
import org.gnucash.android.app.GnuCashApplication
import org.gnucash.android.db.DatabaseHelper
import org.gnucash.android.db.adapter.BooksDbAdapter
import org.gnucash.android.export.ExportFormat
import org.gnucash.android.export.ExportParams
import org.gnucash.android.export.Exporter
import org.gnucash.android.lang.BooleanCallback
import org.gnucash.android.ui.common.GnucashProgressDialog
import org.gnucash.android.ui.snackLong
import org.gnucash.android.util.FileUtils.copyFile
import org.gnucash.android.work.BackupWorker
import timber.log.Timber
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPOutputStream

/**
 * Deals with all backup-related tasks.
 */
object BackupManager {
    const val MIME_TYPE: String = "application/x-sqlite3"

    /**
     * Perform an automatic backup of all books in the database.
     * This method is run every time the service is executed
     */
    @WorkerThread
    fun backupAllBooks(): Boolean {
        val context = GnuCashApplication.appContext
        return backupAllBooks(context)
    }

    /**
     * Perform an automatic backup of all books in the database.
     * This method is run every time the service is executed
     *
     * @return `true` when all books were successfully backed-up.
     */
    @WorkerThread
    fun backupAllBooks(context: Context): Boolean {
        Timber.i("Doing backup of all books.")
        val booksDbAdapter = BooksDbAdapter.instance
        val bookUIDs = booksDbAdapter.allBookUIDs

        for (bookUID in bookUIDs) {
            if (!backupBook(context, bookUID)) {
                return false
            }
        }
        return true
    }

    /**
     * Backs up the active book to the directory [.getBackupFolder].
     *
     * @return `true` if backup was successful, `false` otherwise
     */
    @WorkerThread
    fun backupActiveBook(): Boolean {
        return backupActiveBook(GnuCashApplication.appContext)
    }

    /**
     * Backs up the active book to the directory [.getBackupFolder].
     *
     * @return `true` if backup was successful, `false` otherwise
     */
    @WorkerThread
    fun backupActiveBook(context: Context): Boolean {
        return backupBook(context, GnuCashApplication.activeBookUID!!)
    }

    /**
     * Backs up the book with UID `bookUID` to the directory
     * [.getBackupFolder].
     *
     * @param bookUID Unique ID of the book
     * @return `true` if backup was successful, `false` otherwise
     */
    @WorkerThread
    fun backupBook(context: Context, bookUID: String): Boolean {
        return try {
            val helper = DatabaseHelper(context, bookUID)
            val name = helper.databaseName
            val file = context.getDatabasePath(name)
            val backupUri = getBookBackupFileUri(context, bookUID)
            val outputStream = context.contentResolver.openOutputStream(backupUri)!!
            val gzipOutputStream = GZIPOutputStream(outputStream)
            copyFile(file, gzipOutputStream)
            helper.close()
            true
        } catch (e: Throwable) {
            Timber.e(e, "Error creating backup")
            false
        }
    }

    /**
     * Returns the full path of a file to make database backup of the specified book.
     * Backups are done in XML format and are Gzipped (with ".gnucash" extension).
     *
     * @param context      the context
     * @param bookUID      GUID of the book
     * @param exportParams the export parameters.
     * @return the file for backups of the database.
     * @see .getBackupFolder
     */
    private fun getBackupFile(
        context: Context,
        bookUID: String,
        exportParams: ExportParams?
    ): File {
        val name = Exporter.buildExportFilename(exportParams, "backup")
        return File(getBackupFolder(context, bookUID), name)
    }

    /**
     * Returns the path to the backups folder for the book with GUID `bookUID`.
     *
     *
     * Each book has its own backup folder.
     *
     * @return The backup folder for the book
     */
    fun getBackupFolder(context: Context, bookUID: String): File {
        val baseFolder = context.getExternalFilesDir(null)
        val folder = File(File(baseFolder, bookUID), "backups")
        if (!folder.exists()) folder.mkdirs()
        return folder
    }

    /**
     * Return the user-set backup file URI for the book with UID `bookUID`.
     *
     * @param bookUID Unique ID of the book
     * @return DocumentFile for book backups, or null if the user hasn't set any.
     */
    fun getBookBackupFileUri(context: Context, bookUID: String): Uri {
        return getBookBackupFileUri(context, bookUID, null)
    }

    /**
     * Return the user-set backup file URI for the book with UID `bookUID`.
     *
     * @param bookUID Unique ID of the book
     * @return DocumentFile for book backups, or null if the user hasn't set any.
     */
    fun getBookBackupFileUri(context: Context, bookUID: String, exportParams: ExportParams?): Uri {
        val exportParams = exportParams ?: ExportParams(ExportFormat.SQLITE).apply {
            isCompressed = true
        }
        val preferences = GnuCashApplication.getBookPreferences(context, bookUID)
        val key = context.getString(R.string.key_backup_location)
        val path = preferences.getString(key, null)
        if (path.isNullOrEmpty()) {
            val file = getBackupFile(context, bookUID, exportParams)
            return file.toUri()
        }
        return path.toUri()
    }

    fun getBackupList(context: Context, bookUID: String): List<File> {
        val backupFiles = getBackupFolder(context, bookUID).listFiles() ?: return emptyList()
        backupFiles.sortDescending()
        return backupFiles.toList()
    }

    fun schedulePeriodicBackups(context: Context) {
        Timber.i("Scheduling backups")
        val request: WorkRequest =
            PeriodicWorkRequest.Builder(BackupWorker::class.java, 1, TimeUnit.DAYS)
                .setInitialDelay(15, TimeUnit.MINUTES)
                .build()

        WorkManager.getInstance(context)
            .enqueue(request)
    }

    fun backupBookAsync(activity: Activity?, bookUID: String, after: BooleanCallback) {
        object : AsyncTask<Any, Any, Boolean>() {
            private var progressDialog: ProgressDialog? = null

            override fun onPreExecute() {
                if (activity != null) {
                    progressDialog = GnucashProgressDialog(activity).apply {
                        setTitle(R.string.title_create_backup_pref)
                        setCancelable(true)
                        setOnCancelListener(DialogInterface.OnCancelListener { dialogInterface: DialogInterface ->
                            cancel(true)
                        })
                        show()
                    }
                }
            }

            override fun doInBackground(vararg objects: Any?): Boolean {
                return backupBook(activity!!, bookUID)
            }

            override fun onPostExecute(result: Boolean) {
                try {
                    if (progressDialog != null && progressDialog!!.isShowing) {
                        progressDialog!!.dismiss()
                    }
                } catch (_: IllegalArgumentException) {
                    //TODO: This is a hack to catch "View not attached to window" exceptions
                    //FIXME by moving the creation and display of the progress dialog to the Fragment
                } finally {
                    progressDialog = null
                }
                if (!result) {
                    activity?.snackLong(R.string.toast_backup_failed)
                }
                after.invoke(result)
            }

            override fun onCancelled() {
                after.invoke(false)
            }
        }.execute()
    }

    fun backupActiveBookAsync(activity: Activity?, after: BooleanCallback) {
        backupBookAsync(activity, GnuCashApplication.activeBookUID!!, after)
    }
}
