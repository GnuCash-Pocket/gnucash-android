/*
 * Copyright (c) 2015 Ngewi Fet <ngewif@gmail.com>
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

import static java.lang.Math.max;
import static java.lang.Math.min;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.text.format.DateUtils;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import com.squareup.timessquare.CalendarPickerView;

import org.gnucash.android.R;
import org.gnucash.android.databinding.DialogDateRangePickerBinding;
import org.joda.time.LocalDate;

import java.util.Date;
import java.util.List;

/**
 * Dialog for picking date ranges in terms of months.
 * It is currently used for selecting ranges for reports
 *
 * @author Ngewi Fet <ngewif@gmail.com>
 */
public class DateRangePickerDialogFragment extends DialogFragment {
    private LocalDate mStartRange = LocalDate.now().minusMonths(1);
    private LocalDate mEndRange = LocalDate.now();
    private OnDateRangeSetListener mDateRangeSetListener;
    private static final long ONE_DAY_IN_MILLIS = DateUtils.DAY_IN_MILLIS;

    public static DateRangePickerDialogFragment newInstance(OnDateRangeSetListener dateRangeSetListener) {
        DateRangePickerDialogFragment fragment = new DateRangePickerDialogFragment();
        fragment.mDateRangeSetListener = dateRangeSetListener;
        return fragment;
    }

    public static DateRangePickerDialogFragment newInstance(long startDate, long endDate,
                                                            OnDateRangeSetListener dateRangeSetListener) {
        DateRangePickerDialogFragment fragment = new DateRangePickerDialogFragment();
        // FIXME persist these fields in case dialog is rebuilt.
        fragment.mStartRange = new LocalDate(min(startDate, endDate));
        fragment.mEndRange = new LocalDate(max(startDate, endDate));
        fragment.mDateRangeSetListener = dateRangeSetListener;
        return fragment;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        final DialogDateRangePickerBinding binding = DialogDateRangePickerBinding.inflate(getLayoutInflater());

        binding.calendarView.init(mStartRange.toDate(), mEndRange.toDate())
            .inMode(CalendarPickerView.SelectionMode.RANGE);

        final Context context = requireContext();
        return new AlertDialog.Builder(context, getTheme())
            .setTitle(R.string.report_time_range_picker_title)
            .setView(binding.getRoot())
            .setNegativeButton(R.string.btn_cancel, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    // Dismisses itself.
                }
            })
            .setPositiveButton(R.string.done_label, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    List<Date> selectedDates = binding.calendarView.getSelectedDates();
                    int length = selectedDates.size();
                    if (length > 0) {
                        Date startDate = selectedDates.get(0);
                        // If only one day is selected (no interval) start and end should be the same (the selected one)
                        Date endDate = length > 1 ? selectedDates.get(length - 1) : new Date(startDate.getTime());
                        // CalendarPicker returns the start of the selected day but we want all transactions of that day to be included.
                        // Therefore we have to add 24 hours to the endDate.
                        endDate.setTime(endDate.getTime() + ONE_DAY_IN_MILLIS);
                        mDateRangeSetListener.onDateRangeSet(LocalDate.fromDateFields(startDate), LocalDate.fromDateFields(endDate));
                    }
                }
            })
            .create();
    }

    public interface OnDateRangeSetListener {
        void onDateRangeSet(LocalDate startDate, LocalDate endDate);
    }
}
