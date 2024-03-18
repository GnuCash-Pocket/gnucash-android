package org.gnucash.android.ui.common

import android.app.Activity
import android.app.ProgressDialog

class GnucashProgressDialog(context: Activity) : ProgressDialog(context) {
    init {
        isIndeterminate = true
        setProgressStyle(STYLE_HORIZONTAL)
        setProgressNumberFormat(null)
        setProgressPercentFormat(null)
    }
}