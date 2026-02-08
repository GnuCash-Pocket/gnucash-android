/*
 * Copyright (c) 2017 Àlex Magaz Graça <alexandre.magaz@gmail.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gnucash.android.ui.settings.dialog

import android.widget.CheckBox
import androidx.appcompat.app.AlertDialog
import org.gnucash.android.R
import org.gnucash.android.ui.util.dialog.VolatileDialogFragment

/**
 * Confirmation dialog with additional checkbox to confirm the action.
 *
 *
 * It's meant to avoid the user confirming irreversible actions by
 * mistake. The positive button to confirm the action is only enabled
 * when the checkbox is checked.
 *
 *
 * Extend this class and override onCreateDialog to finish setting
 * up the dialog. See getDialogBuilder().
 *
 * @author Àlex Magaz <alexandre.magaz@gmail.com>
 */
abstract class DoubleConfirmationDialog : VolatileDialogFragment() {
    /**
     * Returns the dialog builder with the defaults for a double confirmation
     * dialog already set up.
     *
     *
     * Call it from onCreateDialog to finish setting up the dialog.
     * At least the following should be set:
     *
     *
     *  * The title.
     *  * The positive button.
     *
     *
     * @return AlertDialog.Builder with the defaults for a double confirmation
     * dialog already set up.
     */
    protected val dialogBuilder: AlertDialog.Builder
        get() = AlertDialog.Builder(requireActivity(), theme)
            .setView(R.layout.dialog_double_confirm)
            .setNegativeButton(R.string.btn_cancel) { _, _ ->
                onNegativeButton()
            }

    override fun onStart() {
        super.onStart()
        val dialog = dialog as AlertDialog?
        if (dialog != null) {
            val confirmCheckBox = dialog.findViewById<CheckBox>(R.id.checkbox_confirm) ?: return
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
            confirmCheckBox.setOnCheckedChangeListener { _, isChecked ->
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = isChecked
            }
        }
    }

    /**
     * Called when the negative button is pressed.
     *
     *
     * By default it just dismisses the dialog.
     */
    protected fun onNegativeButton() {
        dismiss()
    }
}
