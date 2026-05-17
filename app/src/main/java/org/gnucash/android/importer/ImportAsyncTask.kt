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
package org.gnucash.android.importer

import android.annotation.SuppressLint
import android.app.Activity
import android.app.ProgressDialog
import android.content.ContentValues
import android.content.Context
import android.content.DialogInterface
import android.net.Uri
import android.os.AsyncTask
import android.os.OperationCanceledException
import androidx.appcompat.app.AlertDialog
import org.gnucash.android.R
import org.gnucash.android.db.DatabaseSchema.BookEntry
import org.gnucash.android.db.adapter.BooksDbAdapter
import org.gnucash.android.gnc.AsyncTaskProgressListener
import org.gnucash.android.model.Book
import org.gnucash.android.service.ScheduledActionService.Companion.schedulePeriodic
import org.gnucash.android.ui.common.GnucashProgressDialog
import org.gnucash.android.ui.snackLong
import org.gnucash.android.util.BackupManager.backupActiveBook
import org.gnucash.android.util.BookUtils
import org.gnucash.android.util.getDocumentName
import org.gnucash.android.util.openStream
import org.gnucash.android.util.set
import timber.log.Timber
import java.io.EOFException

typealias ImportProduct = Result<String>

/**
 * Imports a GnuCash (desktop) account file and displays a progress dialog.
 * The AccountsActivity is opened when importing is done.
 */
class ImportAsyncTask(
    @SuppressLint("StaticFieldLeak")
    private val activity: Activity,
    private val backup: Boolean = false,
    private val bookCallback: ImportBookCallback? = null
) : AsyncTask<Uri, Any, ImportProduct>() {
    private val progressDialog: ProgressDialog
    private val listener: AsyncTaskProgressListener = ProgressListener(activity)
    private var importer: Importer? = null

    init {
        progressDialog = GnucashProgressDialog(activity).apply {
            setTitle(R.string.title_import_accounts)
            setCancelable(true)
            setOnCancelListener(DialogInterface.OnCancelListener { dialog: DialogInterface ->
                cancel(true)
                importer?.cancel()
            })
        }
    }

    private inner class ProgressListener(context: Context) : AsyncTaskProgressListener(context) {
        override fun publishProgress(label: String, progress: Long, total: Long) {
            this@ImportAsyncTask.publishProgress(label, progress, total)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onPreExecute() {
        super.onPreExecute()
        progressDialog.show()
    }

    @Deprecated("Deprecated in Java")
    override fun doInBackground(vararg uris: Uri): ImportProduct {
        if (backup) {
            backupActiveBook()
        }
        if (isCancelled) {
            return ImportProduct.failure(OperationCanceledException())
        }

        val uri: Uri = uris[0]
        val context = progressDialog.context
        val books: List<Book>

        try {
            val accountInputStream = uri.openStream(context)!!
            val importer = ImporterFactory.create(context, accountInputStream, listener)
            this.importer = importer
            books = importer.parse()
        } catch (e: Throwable) {
            Timber.e(e, "Error importing: %s", uri)
            return ImportProduct.failure(e)
        }

        val booksDbAdapter = BooksDbAdapter.instance
        val contentValues = ContentValues()
        contentValues[BookEntry.COLUMN_SOURCE_URI] = uri.toString()

        var bookUID = ""

        for (book in books) {
            bookUID = book.uid
            book.sourceUri = uri
            var displayName = book.displayName
            if (displayName.isNullOrEmpty()) {
                var name = uri.getDocumentName(context)
                if (name.isNotEmpty()) {
                    // Remove short file type extension, e.g. ".xml" or ".gnucash" or ".gnca.gz"
                    val indexFileType = name.indexOf('.')
                    if (indexFileType > 0) {
                        name = name.take(indexFileType)
                    }
                    displayName = name
                }
                if (displayName.isNullOrEmpty()) {
                    displayName = booksDbAdapter.generateDefaultBookName()
                }
                book.displayName = displayName
            }
            contentValues[BookEntry.COLUMN_DISPLAY_NAME] = displayName
            booksDbAdapter.updateRecord(book.uid, contentValues)
        }

        return ImportProduct.success(bookUID)
    }

    @Deprecated("Deprecated in Java")
    override fun onProgressUpdate(vararg values: Any) {
        if (progressDialog.isShowing) {
            listener.showProgress(progressDialog, *values)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onPostExecute(result: ImportProduct) {
        dismissProgressDialog()
        val context: Context = activity

        if (result.isSuccess) {
            val bookUID = result.getOrNull()
            if (!bookUID.isNullOrEmpty()) {
                context.snackLong(R.string.toast_success_importing_accounts)
                BookUtils.loadBook(context, bookUID)
            } else {
                context.snackLong(R.string.toast_error_importing_accounts)
            }

            bookCallback?.invoke(bookUID)

            schedulePeriodic(context)
        } else if (result.isFailure) {
            var message = context.getString(R.string.toast_error_importing_accounts)

            when (val error = result.exceptionOrNull()) {
                is EOFException,
                is SecurityException -> {
                    val errorMessage = error.message
                    if (!errorMessage.isNullOrEmpty()) {
                        message = errorMessage
                    }
                }
            }

            if (activity.isFinishing || activity.isDestroyed) {
                context.snackLong(message)
            } else {
                AlertDialog.Builder(activity)
                    .setTitle(R.string.title_import_accounts)
                    .setMessage(message)
                    .setPositiveButton(R.string.btn_ok) { _, _ ->
                        // dismisses itself
                    }
                    .show()
            }
        }
    }

    private fun dismissProgressDialog() {
        val progressDialog = this.progressDialog
        try {
            if (progressDialog.isShowing) {
                progressDialog.dismiss()
            }
        } catch (_: IllegalArgumentException) {
            //TODO: This is a hack to catch "View not attached to window" exceptions
            //FIXME by moving the creation and display of the progress dialog to the Fragment
        }
    }
}