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
package org.gnucash.android.ui.util.dialog

import android.app.Dialog
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import com.squareup.timessquare.CalendarPickerView
import org.gnucash.android.R
import org.gnucash.android.databinding.DialogDateRangePickerBinding
import org.joda.time.LocalDate
import kotlin.math.max
import kotlin.math.min

/**
 * Dialog for picking date ranges in terms of months.
 * It is currently used for selecting ranges for reports
 *
 * @author Ngewi Fet <ngewif@gmail.com>
 */
class DateRangePickerDialogFragment : VolatileDialogFragment() {
    private var startRange: LocalDate = LocalDate.now().minusMonths(1)
    private var endRange: LocalDate = LocalDate.now()
    private var dateRangeSetListener: OnDateRangeSetListener? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val binding = DialogDateRangePickerBinding.inflate(layoutInflater)

        val startDate = startRange.toDate()
        val endDate = endRange.plusDays(1).toDate()
        val date = endRange.toDateTimeAtStartOfDay().toDate()
        binding.calendarView.init(startDate, endDate)
            .inMode(CalendarPickerView.SelectionMode.RANGE)
        binding.calendarView.selectDate(date)

        val context = requireContext()
        return AlertDialog.Builder(context, theme)
            .setTitle(R.string.report_time_range_picker_title)
            .setView(binding.root)
            .setNegativeButton(R.string.btn_cancel) { _, _ ->
                // Dismisses itself
            }
            .setPositiveButton(R.string.done_label) { _, _ ->
                onRangeSelected(binding)
            }
            .create()
    }

    private fun onRangeSelected(binding: DialogDateRangePickerBinding) {
        val selectedDates = binding.calendarView.getSelectedDates()
        val length = selectedDates.size
        if (length > 0) {
            val startDateSelected = selectedDates[0]
            // If only one day is selected (no interval) start and end should be the same (the selected one)
            val endDateSelected = if (length > 1) selectedDates[length - 1] else startDateSelected
            val startDate = LocalDate.fromDateFields(startDateSelected)
                .toDateTimeAtStartOfDay()
                .toLocalDate()
            // CalendarPicker returns the start of the selected day but we want all transactions of that day to be included.
            // Therefore we have to add 24 hours to the endDate.
            val endDate = LocalDate.fromDateFields(endDateSelected)
                .toDateTimeAtCurrentTime()
                .toLocalDate()
            dateRangeSetListener?.onDateRangeSet(startDate, endDate)
        }
    }

    interface OnDateRangeSetListener {
        fun onDateRangeSet(startDate: LocalDate, endDate: LocalDate)
    }

    companion object {
        fun newInstance(dateRangeSetListener: OnDateRangeSetListener): DateRangePickerDialogFragment {
            val fragment = DateRangePickerDialogFragment()
            fragment.dateRangeSetListener = dateRangeSetListener
            return fragment
        }

        fun newInstance(
            startRange: Long,
            endRange: Long,
            dateRangeSetListener: OnDateRangeSetListener
        ): DateRangePickerDialogFragment {
            return newInstance(
                LocalDate(min(startRange, endRange)),
                LocalDate(max(startRange, endRange)),
                dateRangeSetListener
            )
        }

        fun newInstance(
            startRange: LocalDate?,
            endRange: LocalDate?,
            dateRangeSetListener: OnDateRangeSetListener
        ): DateRangePickerDialogFragment {
            val fragment = DateRangePickerDialogFragment()
            fragment.startRange = startRange ?: LocalDate.now().minusMonths(1)
            fragment.endRange = endRange ?: LocalDate.now()
            fragment.dateRangeSetListener = dateRangeSetListener
            return fragment
        }
    }
}
