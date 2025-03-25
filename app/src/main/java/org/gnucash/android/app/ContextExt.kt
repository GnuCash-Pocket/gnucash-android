package org.gnucash.android.app

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.view.View

fun Context.getActivity(): Activity? {
    if (this is Activity) {
        return this
    }
    if (this is ContextWrapper) {
        return baseContext.getActivity()
    }
    return null
}

fun View.getActivity(): Activity = context.getActivity()!!

val Context.isNightMode: Boolean
    get() {
        val nightMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return (nightMode == Configuration.UI_MODE_NIGHT_YES)
    }
