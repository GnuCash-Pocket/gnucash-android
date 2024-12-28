/*
 * Copyright (c) 2012 Ngewi Fet <ngewif@gmail.com>
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
package org.gnucash.android.ui.util.dialog;

import android.app.DatePickerDialog;
import android.app.DatePickerDialog.OnDateSetListener;
import android.app.Dialog;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Calendar;

/**
 * Fragment for displaying a date picker dialog
 *
 * @author Ngewi Fet <ngewif@gmail.com>
 */
public class DatePickerDialogFragment extends VolatileDialogFragment {

    /**
     * Listener to notify when the date is set.
     */
    @Nullable
    private OnDateSetListener listener;

    /**
     * Date selected in the dialog or to which the dialog is initialized
     */
    @NonNull
    private final Calendar date = Calendar.getInstance();

    /**
     * Create a new instance.
     *
     * @param listener   Listener to notify when the date is set and the dialog is closed
     * @param dateMillis Date in milliseconds to which to initialize the dialog
     */
    public static DatePickerDialogFragment newInstance(@Nullable OnDateSetListener listener, long dateMillis) {
        DatePickerDialogFragment fragment = new DatePickerDialogFragment();
        fragment.listener = listener;
        if (dateMillis > 0) {
            fragment.date.setTimeInMillis(dateMillis);
        }
        return fragment;
    }

    /**
     * Creates and returns an Android {@link DatePickerDialog}
     */
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        return new DatePickerDialog(requireContext(),
            listener,
            date.get(Calendar.YEAR),
            date.get(Calendar.MONTH),
            date.get(Calendar.DAY_OF_MONTH)
        );
    }

}
