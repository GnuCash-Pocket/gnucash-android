package org.gnucash.android.ui.util.widget

import android.text.InputFilter
import android.widget.EditText
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

fun EditText.setTextToEnd(text: CharSequence?) {
    setText(text)
    if (text.isNullOrEmpty()) return
    try {
        setSelection(text.length)
    } catch (e: IndexOutOfBoundsException) {
        setSelection(0)
    }
}
