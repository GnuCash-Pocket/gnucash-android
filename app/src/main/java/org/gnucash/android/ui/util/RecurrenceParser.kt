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

import android.content.Context
import android.text.format.DateUtils
import android.text.format.Time
import android.util.TimeFormatException
import com.codetroopers.betterpickers.recurrencepicker.EventRecurrence
import com.codetroopers.betterpickers.recurrencepicker.EventRecurrenceFormatter
import org.gnucash.android.model.PeriodType
import org.gnucash.android.model.Recurrence
import timber.log.Timber

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

    private val toPeriodType = mapOf(
        EventRecurrence.HOURLY to PeriodType.HOUR,
        EventRecurrence.DAILY to PeriodType.DAY,
        EventRecurrence.WEEKLY to PeriodType.WEEK,
        EventRecurrence.YEARLY to PeriodType.YEAR,
        EventRecurrence.MONTHLY to PeriodType.MONTH,
    )

    /**
     * Parse an [EventRecurrence] into a [Recurrence] object
     *
     * @param eventRecurrence EventRecurrence object
     * @return Recurrence object
     */
    fun parse(eventRecurrence: EventRecurrence?): Recurrence {
        val rrule = eventRecurrence?.toString()
        return parse(rrule)
    }

    fun parse(rule: String?): Recurrence {
        if (rule.isNullOrEmpty()) return Recurrence(PeriodType.ONCE)
        val recurrence = Recurrence(PeriodType.ONCE)
        val eventRecurrence = recurrence.eventRaw
        eventRecurrence.parse(rule)
        recurrence.periodType = toPeriodType[eventRecurrence.freq] ?: PeriodType.ONCE
        val until = eventRecurrence.until
        if (!until.isNullOrEmpty()) {
            try {
                val untilTime = Time()
                untilTime.parse(until)
                recurrence.periodEnd = untilTime.toMillis(true)
            } catch (e: TimeFormatException) {
                Timber.e(e, "Bad until date: %s", until)
            }
        }
        return recurrence
    }

    fun format(context: Context, recurrence: Recurrence): String? {
        if (!recurrence.isEmpty()) {
            return try {
                EventRecurrenceFormatter.getRepeatString(
                    context,
                    context.resources,
                    recurrence.eventRaw,
                    true
                )
            } catch (e: Exception) {
                Timber.e(e, "Bad recurrence for [%s]", recurrence.ruleString)
                null
            }
        }
        return null
    }
}
