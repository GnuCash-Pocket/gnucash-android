package org.gnucash.android.app

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper

fun Context.findActivity(): Activity {
    if (this is Activity) {
        return this
    }
    if (this is ContextWrapper) {
        return baseContext.findActivity()
    }
    throw IllegalArgumentException("context has not activity")
}