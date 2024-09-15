package org.gnucash.android.ui.util.widget

import android.text.InputFilter
import android.widget.TextView

fun TextView.addFilter(filter: InputFilter) {
    var filters = filters
    if (filters != null) {
        filters += filter
    } else {
        filters = arrayOf(filter)
    }
    setFilters(filters)
}