package org.gnucash.android.app

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent

private const val AccessUriModeFlags =
    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION

@SuppressLint("WrongConstant")
fun Context.takePersistableUriPermission(intent: Intent) {
    val uri = intent.data ?: return
    val modeFlags: Int = intent.flags and AccessUriModeFlags
    contentResolver.takePersistableUriPermission(uri, modeFlags)
}