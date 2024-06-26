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
import android.os.Bundle;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.squareup.timessquare.CalendarPickerView;

import org.gnucash.android.R;
import org.joda.time.LocalDate;

import java.util.Date;
import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;

/**
 * Dialog for picking date ranges in terms of months.
 * It is currently used for selecting ranges for reports
 *
 * @author Ngewi Fet <ngewif@gmail.com>
 */
public class DateRangePickerDialogFragment extends DialogFragment {

    @BindView(R.id.calendar_view)
    CalendarPickerView mCalendarPickerView;
    @BindView(R.id.btn_save)
    Button mDoneButton;
    @BindView(R.id.btn_cancel)
    Button mCancelButton;

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
        fragment.mStartRange = new LocalDate(min(startDate, endDate));
        fragment.mEndRange = new LocalDate(max(startDate, endDate));
        fragment.mDateRangeSetListener = dateRangeSetListener;
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.dialog_date_range_picker, container, false);
        ButterKnife.bind(this, view);

        mCalendarPickerView.init(mStartRange.toDate(), mEndRange.toDate())
                .inMode(CalendarPickerView.SelectionMode.RANGE);

        mDoneButton.setText(R.string.done_label);
        mDoneButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                List<Date> selectedDates = mCalendarPickerView.getSelectedDates();
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
                dismiss();
            }
        });

        mCancelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dismiss();
            }
        });
        return view;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Dialog dialog = super.onCreateDialog(savedInstanceState);
        dialog.setTitle(R.string.report_time_range_picker_title);
        return dialog;
    }

    public interface OnDateRangeSetListener {
        void onDateRangeSet(LocalDate startDate, LocalDate endDate);
    }
}
