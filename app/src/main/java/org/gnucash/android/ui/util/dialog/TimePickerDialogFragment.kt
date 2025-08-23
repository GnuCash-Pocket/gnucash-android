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

import android.app.Dialog;
import android.app.TimePickerDialog;
import android.app.TimePickerDialog.OnTimeSetListener;
import android.content.Context;
import android.os.Bundle;
import android.text.format.DateFormat;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import java.util.Calendar;

/**
 * Fragment for displaying a time picker dialog
 *
 * @author Ngewi Fet <ngewif@gmail.com>
 */
public class TimePickerDialogFragment extends DialogFragment {
    /**
     * Listener to notify when the time is set.
     */
    @Nullable
    private OnTimeSetListener listener;

    /**
     * Current time to initialize the dialog to, or to notify the listener of.
     */
    @NonNull
    private final Calendar time = Calendar.getInstance();

    /**
     * Create a new instance.
     *
     * @param listener   {@link OnTimeSetListener} to notify when the time has been set
     * @param timeMillis Time in milliseconds to initialize the dialog to
     */
    public static TimePickerDialogFragment newInstance(@Nullable OnTimeSetListener listener, long timeMillis) {
        TimePickerDialogFragment fragment = new TimePickerDialogFragment();
        fragment.listener = listener;
        if (timeMillis > 0) {
            fragment.time.setTimeInMillis(timeMillis);
        }
        return fragment;
    }

    /**
     * Creates and returns an Android {@link TimePickerDialog}
     */
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        final Context context = requireContext();
        return new TimePickerDialog(
            context,
            listener,
            time.get(Calendar.HOUR_OF_DAY),
            time.get(Calendar.MINUTE),
            DateFormat.is24HourFormat(context)
        );
    }

}
