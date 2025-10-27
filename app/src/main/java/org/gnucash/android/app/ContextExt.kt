package org.gnucash.android.app

import android.content.Context
import android.content.res.Configuration

val Context.isNightMode: Boolean
    get() {
        val nightMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return (nightMode == Configuration.UI_MODE_NIGHT_YES)
    }
