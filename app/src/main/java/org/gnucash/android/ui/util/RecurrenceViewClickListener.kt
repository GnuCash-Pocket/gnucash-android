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
package org.gnucash.android.ui.util

import android.os.Bundle
import android.view.View
import androidx.fragment.app.FragmentManager
import com.codetroopers.betterpickers.recurrencepicker.RecurrencePickerDialogFragment
import com.codetroopers.betterpickers.recurrencepicker.RecurrencePickerDialogFragment.OnRecurrenceSetListener
import java.util.Calendar

/**
 * Shows the recurrence dialog when the recurrence view is clicked
 */
class RecurrenceViewClickListener(
    private val fragmentManager: FragmentManager,
    private var recurrenceRule: String?,
    private val onRecurrenceSetListener: OnRecurrenceSetListener?
) : View.OnClickListener {

    override fun onClick(v: View) {
        val args = Bundle()

        val now = Calendar.getInstance()
        args.putLong(RecurrencePickerDialogFragment.BUNDLE_START_TIME_MILLIS, now.timeInMillis)
        args.putString(RecurrencePickerDialogFragment.BUNDLE_TIME_ZONE, now.getTimeZone().id)

        // may be more efficient to serialize and pass in EventRecurrence
        args.putString(RecurrencePickerDialogFragment.BUNDLE_RRULE, recurrenceRule)

        var fragmentOld = fragmentManager.findFragmentByTag(TAG_RECURRENCE_PICKER) as? RecurrencePickerDialogFragment
        fragmentOld?.dismiss()
        val fragment = RecurrencePickerDialogFragment()
        fragment.arguments = args
        fragment.setOnRecurrenceSetListener(onRecurrenceSetListener)
        fragment.show(fragmentManager, TAG_RECURRENCE_PICKER)
    }

    fun setRecurrence(rule: String?) {
        recurrenceRule = rule
    }

    companion object {
        private const val TAG_RECURRENCE_PICKER = "recurrence_picker"
    }
}
