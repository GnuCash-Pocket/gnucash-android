package org.gnucash.android.util

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import timber.log.Timber

private val PROJECTION_DOCUMENT_NAME = arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
private const val INDEX_DOCUMENT_NAME = 0

fun Uri.getDocumentName(context: Context?): String {
    var name: String = this.authority ?: this.host ?: ""
    val lastPath = this.lastPathSegment
    if ((lastPath != null) && lastPath.indexOf('.') > 0) {
        name = lastPath
        val indexSlash = lastPath.lastIndexOf('/')
        if ((indexSlash >= 0) && (indexSlash < name.lastIndex)) {
            name = name.substring(indexSlash + 1)
        }
    }
    if (context != null) {
        try {
            val resolver: ContentResolver = context.contentResolver
            val cursor = resolver.query(this, PROJECTION_DOCUMENT_NAME, null, null, null)
            if (cursor != null) {
                if (cursor.moveToFirst()) {
                    name = cursor.getString(INDEX_DOCUMENT_NAME)
                }
                cursor.close()
            }
        } catch (e: Exception) {
            Timber.w(e, "Cannot get document name for %s", this)
            return ""
        }
    }
    return name
}