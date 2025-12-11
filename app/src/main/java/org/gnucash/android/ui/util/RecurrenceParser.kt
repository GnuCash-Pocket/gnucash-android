/*
 * Copyright (c) 2014 Ngewi Fet <ngewif@gmail.com>
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

import android.text.format.DateUtils
import android.text.format.Time
import com.codetroopers.betterpickers.recurrencepicker.EventRecurrence
import org.gnucash.android.model.PeriodType
import org.gnucash.android.model.Recurrence

/**
 * Parses [EventRecurrence]s to generate
 * [org.gnucash.android.model.ScheduledAction]s
 *
 * @author Ngewi Fet <ngewif@gmail.com>
 */
object RecurrenceParser {
    //these are time millisecond constants which are used for scheduled actions.
    //they may not be calendar accurate, but they serve the purpose for scheduling approximate time for background service execution
    const val HOUR_MILLIS: Long = DateUtils.HOUR_IN_MILLIS
    const val DAY_MILLIS: Long = DateUtils.DAY_IN_MILLIS
    const val WEEK_MILLIS: Long = DateUtils.WEEK_IN_MILLIS
    const val MONTH_MILLIS: Long = 30 * DAY_MILLIS
    const val YEAR_MILLIS: Long = DateUtils.YEAR_IN_MILLIS

    /**
     * Parse an [EventRecurrence] into a [Recurrence] object
     *
     * @param eventRecurrence EventRecurrence object
     * @return Recurrence object
     */
    fun parse(eventRecurrence: EventRecurrence?): Recurrence {
        if (eventRecurrence == null) return Recurrence(PeriodType.ONCE)
        val periodType: PeriodType = when (eventRecurrence.freq) {
            EventRecurrence.HOURLY -> PeriodType.HOUR
            EventRecurrence.DAILY -> PeriodType.DAY
            EventRecurrence.WEEKLY -> PeriodType.WEEK
            EventRecurrence.YEARLY -> PeriodType.YEAR
            EventRecurrence.MONTHLY -> PeriodType.MONTH
            else -> PeriodType.MONTH
        }

        //bug from betterpickers library sometimes returns 0 as the interval
        val interval = if (eventRecurrence.interval == 0) 1 else eventRecurrence.interval
        val recurrence = Recurrence(periodType)
        recurrence.multiplier = interval
        parseEndTime(eventRecurrence, recurrence)
        recurrence.byDays = parseByDay(eventRecurrence.byday)
        if (eventRecurrence.startDate != null) {
            recurrence.periodStart = eventRecurrence.startDate.toMillis(false)
        }

        return recurrence
    }

    /**
     * Parses the end time from an EventRecurrence object and sets it to the `scheduledEvent`.
     * The end time is specified in the dialog either by number of occurrences or a date.
     *
     * @param eventRecurrence Event recurrence pattern obtained from dialog
     * @param recurrence      Recurrence event to set the end period to
     */
    private fun parseEndTime(eventRecurrence: EventRecurrence, recurrence: Recurrence) {
        if (eventRecurrence.until != null && !eventRecurrence.until.isEmpty()) {
            val endTime = Time()
            endTime.parse(eventRecurrence.until)
            recurrence.periodEnd = endTime.toMillis(false)
        } else if (eventRecurrence.count > 0) {
            recurrence.setPeriodEndOccurrences(eventRecurrence.count)
        }
    }

    /**
     * Parses an array of byDay values to return a list of days of week
     * constants from [Calendar].
     *
     *
     * Currently only supports byDay values for weeks.
     *
     * @param byDay Array of byDay values
     * @return list of days of week constants from Calendar.
     */
    private fun parseByDay(byDay: IntArray?): List<Int> {
        if (byDay == null) {
            return emptyList<Int>()
        }

        val byDaysList = mutableListOf<Int>()
        for (day in byDay) {
            byDaysList.add(EventRecurrence.day2CalendarDay(day))
        }

        return byDaysList
    }
}
