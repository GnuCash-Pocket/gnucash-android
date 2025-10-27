package org.gnucash.android.util

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.fragment.app.Fragment
import org.gnucash.android.R
import org.gnucash.android.importer.ImportBookCallback
import org.gnucash.android.ui.account.AccountsActivity.Companion.importXmlFileFromIntent
import org.gnucash.android.ui.snackLong
import timber.log.Timber

private const val contentMimeType = "*/*"
private const val documentMimeType = "text/*|application/*"
private val documentMimeTypes = arrayOf("text/*", "application/*")

private fun prepareChooseContent(): Intent {
    return Intent(Intent.ACTION_GET_CONTENT)
        .addCategory(Intent.CATEGORY_OPENABLE)
        .setType(contentMimeType)
}

fun Activity.chooseContent(requestCode: Int) {
    val context = this
    val intent = prepareChooseContent()
    try {
        startActivityForResult(intent, requestCode)
    } catch (e: ActivityNotFoundException) {
        Timber.e(e, "No file manager for selecting files available")
        context.snackLong(R.string.toast_install_file_manager)
    }
}

fun Fragment.chooseContent(requestCode: Int) {
    val context = context ?: return
    val intent = prepareChooseContent()
    try {
        startActivityForResult(intent, requestCode)
    } catch (e: ActivityNotFoundException) {
        Timber.e(e, "No file manager for selecting files available")
        context.snackLong(R.string.toast_install_file_manager)
    }
}

private fun prepareChooseDocument(): Intent {
    return Intent(Intent.ACTION_OPEN_DOCUMENT)
        .addCategory(Intent.CATEGORY_OPENABLE)
        .setType(documentMimeType)
        .putExtra(Intent.EXTRA_MIME_TYPES, documentMimeTypes)
}

fun Activity.chooseDocument(requestCode: Int) {
    val intent = prepareChooseDocument()
    try {
        startActivityForResult(intent, requestCode)
    } catch (e: ActivityNotFoundException) {
        chooseContent(requestCode)
    }
}

fun Fragment.chooseDocument(requestCode: Int) {
    val intent = prepareChooseDocument()
    try {
        startActivityForResult(intent, requestCode)
    } catch (e: ActivityNotFoundException) {
        chooseContent(requestCode)
    }
}

fun openBook(activity: Activity, data: Intent?, onFinishTask: ImportBookCallback? = null) {
    val uri = data?.data
    if (uri == null) {
        Timber.w("Document location expected!")
        return
    }
    importXmlFileFromIntent(activity, data, onFinishTask)
}