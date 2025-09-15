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
package org.gnucash.android.export

import android.app.Activity
import android.app.ProgressDialog
import android.content.Context
import android.content.DialogInterface
import android.net.Uri
import android.os.AsyncTask
import android.os.OperationCanceledException
import android.widget.Toast
import org.gnucash.android.R
import org.gnucash.android.export.ExportParams.ExportTarget
import org.gnucash.android.export.csv.CsvAccountExporter
import org.gnucash.android.export.csv.CsvTransactionsExporter
import org.gnucash.android.export.ofx.OfxExporter
import org.gnucash.android.export.qif.QifExporter
import org.gnucash.android.export.xml.GncXmlExporter
import org.gnucash.android.gnc.AsyncTaskProgressListener
import org.gnucash.android.gnc.GncProgressListener
import org.gnucash.android.importer.ExportBookCallback
import org.gnucash.android.ui.common.GnucashProgressDialog
import org.gnucash.android.ui.common.Refreshable
import org.gnucash.android.ui.settings.OwnCloudPreferences
import timber.log.Timber

/**
 * Asynchronous task for exporting transactions.
 *
 * @author Ngewi Fet <ngewif@gmail.com>
 */
class ExportAsyncTask(
    context: Context,
    private val bookUID: String,
    private val bookCallback: ExportBookCallback? = null
) : AsyncTask<ExportParams, Any, Uri>() {
    private val progressDialog: ProgressDialog?
    private var exportParams: ExportParams? = null
    private var exporter: Exporter? = null
    private val listener: AsyncTaskProgressListener?

    init {
        if (context is Activity) {
            progressDialog = GnucashProgressDialog(context).apply {
                setTitle(R.string.nav_menu_export)
                setCancelable(true)
                setOnCancelListener(DialogInterface.OnCancelListener { dialog: DialogInterface ->
                    cancel(true)
                    exporter?.cancel()
                })
            }
            listener = ProgressListener(context)
        } else {
            progressDialog = null
            listener = null
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onPreExecute() {
        super.onPreExecute()
        progressDialog?.show()
    }

    @Deprecated("Deprecated in Java")
    override fun doInBackground(vararg params: ExportParams): Uri? {
        val exportParams: ExportParams = params[0]
        this.exportParams = exportParams
        val context = progressDialog?.context ?: return null
        val exporter: Exporter = createExporter(context, exportParams, bookUID, listener)
        this.exporter = exporter
        val exportedFile: Uri?

        try {
            exportedFile = exporter.export()
        } catch (ce: OperationCanceledException) {
            Timber.i(ce)
            return null
        } catch (e: Throwable) {
            if (e.cause is OperationCanceledException) {
                Timber.i(e.cause)
                return null
            }
            Timber.e(e, "Error exporting: %s", e.message)
            return null
        }
        if (exportedFile == null) {
            Timber.e("Nothing exported")
            return null
        }
        return exportedFile
    }

    @Deprecated("Deprecated in Java")
    override fun onProgressUpdate(vararg values: Any) {
        if (progressDialog?.isShowing == true) {
            listener?.showProgress(progressDialog, *values)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onPostExecute(exportLocation: Uri?) {
        dismissProgressDialog()

        val context = progressDialog?.context ?: return
        val exportParams = this.exportParams ?: return
        if (exportLocation != null) {
            if (context is Activity) {
                reportSuccess(context, exportParams)
            }
            if (exportParams.deleteTransactionsAfterExport) {
                refreshViews(context)
            }

        } else {
            if (context is Activity) {
                Toast.makeText(
                    context,
                    context.getString(R.string.toast_export_error, exportParams.exportFormat.name),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
        bookCallback?.invoke(exportLocation)
    }

    private fun dismissProgressDialog() {
        val progressDialog = this.progressDialog
        try {
            if (progressDialog?.isShowing == true) {
                progressDialog.dismiss()
            }
        } catch (_: IllegalArgumentException) {
            //TODO: This is a hack to catch "View not attached to window" exceptions
            //FIXME by moving the creation and display of the progress dialog to the Fragment
        }
        val context = progressDialog?.context
        if (context is Activity) {
            context.finish()
        }
    }

    private fun reportSuccess(context: Context, exportParams: ExportParams) {
        val targetLocation: String
        when (exportParams.exportTarget) {
            ExportTarget.SD_CARD -> targetLocation = "SD card"
            ExportTarget.DROPBOX -> targetLocation = "DropBox -> Apps -> GnuCash"
            ExportTarget.OWNCLOUD -> {
                val preferences = OwnCloudPreferences(context)
                targetLocation =
                    if (preferences.isSync) "ownCloud -> " + preferences.dir else "ownCloud sync not enabled"
            }

            else -> targetLocation =
                context.getString(R.string.label_export_target_external_service)
        }
        Toast.makeText(
            context,
            String.format(context.getString(R.string.toast_exported_to), targetLocation),
            Toast.LENGTH_LONG
        ).show()
    }

    private fun refreshViews(context: Context) {
        (context as? Refreshable)?.refresh()
        (listener as? Refreshable)?.refresh()
    }

    private inner class ProgressListener(context: Context) : AsyncTaskProgressListener(context) {
        override fun publishProgress(label: String, progress: Long, total: Long) {
            this@ExportAsyncTask.publishProgress(label, progress, total)
        }
    }

    companion object {
        /**
         * Returns an exporter corresponding to the user settings.
         *
         * @return Object of one of [QifExporter], [OfxExporter] or [GncXmlExporter], {@Link CsvAccountExporter} or {@Link CsvTransactionsExporter}
         */
        fun createExporter(
            context: Context,
            exportParams: ExportParams,
            bookUID: String,
            listener: GncProgressListener?
        ): Exporter {
            when (exportParams.exportFormat) {
                ExportFormat.QIF -> return QifExporter(context, exportParams, bookUID, listener)
                ExportFormat.OFX -> return OfxExporter(context, exportParams, bookUID, listener)
                ExportFormat.CSVA -> return CsvAccountExporter(
                    context,
                    exportParams,
                    bookUID,
                    listener
                )

                ExportFormat.CSVT -> return CsvTransactionsExporter(
                    context,
                    exportParams,
                    bookUID,
                    listener
                )

                else -> return GncXmlExporter(context, exportParams, bookUID, listener)
            }
        }
    }
}
