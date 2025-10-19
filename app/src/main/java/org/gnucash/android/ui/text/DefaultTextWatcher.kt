package org.gnucash.android.ui.text

import android.text.Editable
import android.text.TextWatcher

typealias TextChangedCallback = (s: Editable) -> Unit

open class DefaultTextWatcher(private val callback: TextChangedCallback) : TextWatcher {
    override fun afterTextChanged(s: Editable) {
        callback(s)
    }

    override fun beforeTextChanged(
        s: CharSequence,
        start: Int,
        count: Int,
        after: Int
    ) = Unit

    override fun onTextChanged(
        s: CharSequence,
        start: Int,
        before: Int,
        count: Int
    ) = Unit
}