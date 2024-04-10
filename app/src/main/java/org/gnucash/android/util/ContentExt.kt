package org.gnucash.android.util

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import org.gnucash.android.ui.transaction.ScheduledActionsListFragment

private val PROJECTION_DOCUMENT_NAME = arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
private const val INDEX_DOCUMENT_NAME = 0

fun Uri.getDocumentName(context: Context): String {
    var name: String = this.authority ?: this.host ?: ""
    val resolver: ContentResolver = context.getContentResolver()
    val cursor = resolver.query(this, PROJECTION_DOCUMENT_NAME, null, null, null)
    if (cursor != null) {
        if (cursor.moveToFirst()) {
            name = cursor.getString(INDEX_DOCUMENT_NAME)
        }
        cursor.close()
    }
    return name
}