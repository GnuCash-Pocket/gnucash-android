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

package org.gnucash.android.ui.settings.dialog;

import android.content.DialogInterface;
import android.widget.CheckBox;
import android.widget.CompoundButton;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;

import org.gnucash.android.R;
import org.gnucash.android.ui.util.dialog.VolatileDialogFragment;

/**
 * Confirmation dialog with additional checkbox to confirm the action.
 *
 * <p>It's meant to avoid the user confirming irreversible actions by
 * mistake. The positive button to confirm the action is only enabled
 * when the checkbox is checked.</p>
 *
 * <p>Extend this class and override onCreateDialog to finish setting
 * up the dialog. See getDialogBuilder().</p>
 *
 * @author Àlex Magaz <alexandre.magaz@gmail.com>
 */
public abstract class DoubleConfirmationDialog extends VolatileDialogFragment {
    /**
     * Returns the dialog builder with the defaults for a double confirmation
     * dialog already set up.
     *
     * <p>Call it from onCreateDialog to finish setting up the dialog.
     * At least the following should be set:</p>
     *
     * <ul>
     *     <li>The title.</li>
     *     <li>The positive button.</li>
     * </ul>
     *
     * @return AlertDialog.Builder with the defaults for a double confirmation
     * dialog already set up.
     */
    @NonNull
    protected AlertDialog.Builder getDialogBuilder() {
        return new AlertDialog.Builder(requireActivity())
            .setView(R.layout.dialog_double_confirm)
            .setNegativeButton(R.string.btn_cancel, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    onNegativeButton();
                }
            });
    }

    @Override
    public void onStart() {
        super.onStart();
        final AlertDialog dialog = (AlertDialog) getDialog();
        if (dialog != null) {
            setUpConfirmCheckBox(dialog);
        }
    }

    @SuppressWarnings("ConstantConditions")
    private void setUpConfirmCheckBox(@NonNull final AlertDialog dialog) {
        CheckBox confirmCheckBox = dialog.findViewById(R.id.checkbox_confirm);
        if (confirmCheckBox == null) return;
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
        confirmCheckBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean isChecked) {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(isChecked);
            }
        });
    }

    /**
     * Called when the negative button is pressed.
     *
     * <p>By default it just dismisses the dialog.</p>
     */
    protected void onNegativeButton() {
        dismiss();
    }
}
