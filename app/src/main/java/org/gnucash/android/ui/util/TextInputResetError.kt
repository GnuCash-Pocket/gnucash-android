package org.gnucash.android.ui.util

import android.text.Editable
import android.text.TextWatcher
import com.google.android.material.textfield.TextInputLayout

/**
 * Hides the error message when the user edits the content.
 */
class TextInputResetError(private vararg val inputs: TextInputLayout) : TextWatcher {
    override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {
        inputs.forEach { it.isErrorEnabled = false }
    }

    override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) = Unit

    override fun afterTextChanged(s: Editable) = Unit
}