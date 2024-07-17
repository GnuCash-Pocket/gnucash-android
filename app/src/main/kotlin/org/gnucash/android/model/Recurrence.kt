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
package org.gnucash.android.model

import android.content.Context
import android.text.format.Time
import com.codetroopers.betterpickers.recurrencepicker.EventRecurrence
import com.codetroopers.betterpickers.recurrencepicker.EventRecurrenceFormatter
import java.sql.Timestamp
import java.util.Calendar
import kotlin.math.max
import org.gnucash.android.R
import org.gnucash.android.ui.util.RecurrenceParser
import org.gnucash.android.util.dayOfWeek
import org.gnucash.android.util.lastDayOfMonth
import org.gnucash.android.util.lastDayOfWeek
import org.gnucash.android.util.weekOfMonth
import org.joda.time.DateTime
import org.joda.time.Days
import org.joda.time.Hours
import org.joda.time.LocalDateTime
import org.joda.time.Months
import org.joda.time.ReadablePeriod
import org.joda.time.Seconds
import org.joda.time.Weeks
import org.joda.time.Years
import org.joda.time.format.DateTimeFormat

/**
 * Model for recurrences in the database
 *
 * Basically a wrapper around [PeriodType]
 */
class Recurrence(periodType: PeriodType) : BaseModel() {

    private val event = EventRecurrence()

    /**
     * Return the [PeriodType] for this recurrence
     */
    var periodType: PeriodType = PeriodType.ONCE
        set(value) {
            field = value
            event.freq = periodTypeToFrequency[value] ?: 0
        }

    /**
     * Timestamp of start of recurrence
     */
    var periodStart: Long
        get() = event.startDate.toMillis(true)
        set(value) {
            event.startDate = Time().apply { set(value) }
        }

    /**
     * End date of the recurrence period
     */
    var periodEnd: Long? = null
        private set

    /**
     * The multiplier for the period type. The default multiplier is 1.
     * e.g. bi-weekly actions have period type [PeriodType.WEEK] and multiplier 2.
     */
    var multiplier: Int
        get() = max(event.interval, 1)
        set(value) {
            event.interval = value
        }

    init {
        this.periodType = periodType
        this.periodStart = System.currentTimeMillis()
        this.multiplier = 1
    }

    /**
     * Returns an approximate period for this recurrence
     *
     * The period is approximate because months do not all have the same number of days,
     * but that is assumed
     *
     * @return Milliseconds since Epoch representing the period
     */
    @get:Deprecated("Do not use in new code. Uses fixed period values for months and years (which have variable units of time)")
    val period: Long
        get() {
            val baseMillis: Long = when (periodType) {
                PeriodType.ONCE -> 0
                PeriodType.HOUR -> RecurrenceParser.HOUR_MILLIS
                PeriodType.DAY -> RecurrenceParser.DAY_MILLIS
                PeriodType.WEEK -> RecurrenceParser.WEEK_MILLIS
                PeriodType.MONTH -> RecurrenceParser.MONTH_MILLIS
                PeriodType.YEAR -> RecurrenceParser.YEAR_MILLIS
                PeriodType.LAST_WEEKDAY -> RecurrenceParser.MONTH_MILLIS
                PeriodType.NTH_WEEKDAY -> RecurrenceParser.MONTH_MILLIS
                PeriodType.END_OF_MONTH -> RecurrenceParser.MONTH_MILLIS
            }
            return multiplier * baseMillis
        }

    /**
     * Returns the event schedule (start, end and recurrence)
     *
     * @return String description of repeat schedule
     */
    fun getRepeatString(context: Context): String {
        val repeatBuilder = StringBuilder(frequencyRepeatString(context))
        periodEnd?.let { periodEnd ->
            val endDateString = DateTimeFormat.mediumDate().print(periodEnd)
            repeatBuilder.append(", ")
                .append(context.getString(R.string.repeat_until_date, endDateString))
        }
        return repeatBuilder.toString()
    }

    /**
     * Creates an RFC 2445 string which describes this recurring event.
     *
     * See [reassurance](http://recurrance.sourceforge.net/)
     *
     * The output of this method is not meant for human consumption
     *
     * @return String describing event
     */
    var ruleString: String
        get() = event.toString()
        set(value) {
            event.parse(value)
            val freq = event.freq
            periodType = frequencyToPeriodType[freq] ?: PeriodType.ONCE
            event.freq = freq
        }

    /**
     * Return the number of days left in this period
     *
     * @return Number of days left in period
     */
    val daysLeftInCurrentPeriod: Int
        get() {
            val startDate = LocalDateTime(System.currentTimeMillis())
            val interval = multiplier - 1
            val endDate: LocalDateTime? = when (periodType) {
                PeriodType.ONCE -> startDate
                PeriodType.HOUR -> startDate.plusHours(interval)
                PeriodType.DAY -> startDate.plusDays(interval)
                PeriodType.WEEK -> startDate.plusWeeks(interval)
                PeriodType.MONTH -> startDate.plusMonths(interval)
                PeriodType.YEAR -> startDate.plusYears(interval)
                PeriodType.LAST_WEEKDAY -> startDate.plusMonths(interval).lastDayOfWeek(startDate)
                PeriodType.NTH_WEEKDAY -> startDate.plusMonths(interval).dayOfWeek(startDate)
                PeriodType.END_OF_MONTH -> startDate.plusMonths(interval).lastDayOfMonth()
            }
            return Days.daysBetween(startDate, endDate).days
        }

    /**
     * Returns the number of periods from the start date of this recurrence until the end of the
     * interval multiplier specified in the [PeriodType]
     * //fixme: Improve the documentation
     *
     * @return Number of periods in this recurrence
     */
    fun getNumberOfPeriods(numberOfPeriods: Int): Int {
        val startDate = LocalDateTime(periodStart)
        val endDate: LocalDateTime
        val interval = multiplier
        return when (periodType) {
            PeriodType.ONCE -> 1

            PeriodType.HOUR -> {
                endDate = startDate.plusHours(numberOfPeriods)
                Hours.hoursBetween(startDate, endDate).hours
            }

            PeriodType.DAY -> {
                endDate = startDate.plusDays(numberOfPeriods)
                Days.daysBetween(startDate, endDate).days
            }

            PeriodType.WEEK -> {
                endDate = startDate.plusWeeks(numberOfPeriods)
                Weeks.weeksBetween(startDate, endDate).weeks / interval
            }

            PeriodType.MONTH -> {
                endDate = startDate.plusMonths(numberOfPeriods)
                Months.monthsBetween(startDate, endDate).months / interval
            }

            PeriodType.YEAR -> {
                endDate = startDate.plusYears(numberOfPeriods)
                Years.yearsBetween(startDate, endDate).years / interval
            }

            PeriodType.LAST_WEEKDAY -> {
                endDate = startDate.plusMonths(numberOfPeriods).lastDayOfWeek(startDate)
                Months.monthsBetween(startDate, endDate).months / interval
            }

            PeriodType.NTH_WEEKDAY -> {
                endDate = startDate.plusMonths(interval).dayOfWeek(startDate)
                Months.monthsBetween(startDate, endDate).months / interval
            }

            PeriodType.END_OF_MONTH -> {
                endDate = startDate.plusMonths(numberOfPeriods).lastDayOfMonth()
                Months.monthsBetween(startDate, endDate).months / interval
            }
        }
    }

    /**
     * Return the name of the current period
     *
     * @return String of current period
     */
    fun getTextOfCurrentPeriod(periodNum: Int): String {
        val interval = max(1, periodNum) - 1
        val startDate = LocalDateTime(periodStart)
        return when (periodType) {
            PeriodType.ONCE -> "Now"
            PeriodType.HOUR -> startDate.plusHours(interval).hourOfDay().asText
            PeriodType.DAY -> startDate.plusDays(interval).dayOfWeek().asText
            PeriodType.WEEK -> startDate.plusWeeks(interval).weekOfWeekyear().asText
            PeriodType.MONTH -> startDate.plusMonths(interval).monthOfYear().asText
            PeriodType.YEAR -> startDate.plusYears(interval).year().asText
            PeriodType.LAST_WEEKDAY -> startDate.plusMonths(interval).lastDayOfWeek(startDate)
                .dayOfMonth().asText

            PeriodType.NTH_WEEKDAY -> {
                val week = startDate.weekOfMonth() - 1
                val dayName = startDate.dayOfWeek().asText
                String.format("%s %s", numerals[week], dayName)
            }

            PeriodType.END_OF_MONTH -> startDate.plusMonths(interval).lastDayOfMonth()
                .dayOfMonth().asText
        }
    }

    /**
     * The days of week on which to run the recurrence.
     *
     * Days are expressed as defined in [java.util.Calendar].
     * For example, Calendar.MONDAY
     *
     */
    var byDays: List<Int>
        get() = event.byday?.map { dayToUtilDay[it] ?: -1 } ?: emptyList()
        set(value) {
            if (value.isEmpty()) {
                event.byday = null
                event.bydayNum = null
                event.bydayCount = 0
            } else {
                event.byday = value.map { weekdays[it] ?: 0 }.toIntArray()
                if (event.freq == EventRecurrence.MONTHLY) {
                    val weekOfMonth = DateTime(periodStart).weekOfMonth()
                    event.bydayNum = IntArray(value.size) { weekOfMonth }
                } else {
                    event.bydayNum = IntArray(value.size)
                }
                event.bydayCount = value.size
            }
        }

    /**
     * Computes the number of occurrences of this recurrences between start and end date
     *
     * If there is no end date or the PeriodType is unknown, it returns -1
     *
     * @return Number of occurrences, or` -1` if there is no end date
     */
    val occurrences: Int
        get() {
            val periodEnd = this.periodEnd ?: return -1
            val multiple = multiplier
            val jodaPeriod: ReadablePeriod = when (periodType) {
                PeriodType.ONCE -> Seconds.ZERO
                PeriodType.HOUR -> Hours.hours(multiple)
                PeriodType.DAY -> Days.days(multiple)
                PeriodType.WEEK -> Weeks.weeks(multiple)
                PeriodType.MONTH -> Months.months(multiple)
                PeriodType.YEAR -> Years.years(multiple)
                PeriodType.LAST_WEEKDAY -> Months.months(multiple)
                PeriodType.NTH_WEEKDAY -> Months.months(multiple)
                PeriodType.END_OF_MONTH -> Months.months(multiple)
            }
            var count = 0
            var startTime = LocalDateTime(periodStart)
            while (startTime.toDateTime().millis < periodEnd) {
                ++count
                startTime += jodaPeriod
            }
            return count
        }

    var count: Int
        get() = event.count
        set(value) {
            event.count = value
        }

    /**
     * How to adjust to the nearest weekday when the date falls on a weekend.
     */
    var weekendAdjust: WeekendAdjust = WeekendAdjust.NONE

    /**
     * Sets the end time of this recurrence by specifying the number of occurrences
     *
     * @param numberOfOccurrences Number of occurrences from the start time
     */
    fun setPeriodEnd(numberOfOccurrences: Int) {
        val localDate = LocalDateTime(periodStart)
        val occurrenceDuration = numberOfOccurrences * multiplier
        val endDate: LocalDateTime = when (periodType) {
            PeriodType.ONCE -> localDate
            PeriodType.HOUR -> localDate.plusHours(occurrenceDuration)
            PeriodType.DAY -> localDate.plusDays(occurrenceDuration)
            PeriodType.WEEK -> localDate.plusWeeks(occurrenceDuration)
            PeriodType.MONTH -> localDate.plusMonths(occurrenceDuration)
            PeriodType.YEAR -> localDate.plusYears(occurrenceDuration)
            PeriodType.LAST_WEEKDAY -> localDate.plusMonths(occurrenceDuration)
                .lastDayOfWeek(localDate)

            PeriodType.NTH_WEEKDAY -> localDate.plusMonths(occurrenceDuration).dayOfWeek(localDate)
            PeriodType.END_OF_MONTH -> localDate.plusMonths(occurrenceDuration).lastDayOfMonth()
        }
        periodEnd = endDate.toDateTime().millis
    }

    /**
     * Set period end date
     *
     * @param endTimestamp End time in milliseconds
     */
    fun setPeriodEnd(endTimestamp: Timestamp?) {
        periodEnd = endTimestamp?.time
    }

    /**
     * Returns a localized string describing the period type's frequency.
     *
     * @return String describing the period type
     */
    fun frequencyRepeatString(context: Context): String {
        val res = context.resources
        return try {
            EventRecurrenceFormatter.getRepeatString(context, res, event, true)
        } catch (e: Exception) {
            "?"
        }
    }

    companion object {
        private val weekdays = mapOf(
            Calendar.SUNDAY to EventRecurrence.SU,
            Calendar.MONDAY to EventRecurrence.MO,
            Calendar.TUESDAY to EventRecurrence.TU,
            Calendar.WEDNESDAY to EventRecurrence.WE,
            Calendar.THURSDAY to EventRecurrence.TH,
            Calendar.FRIDAY to EventRecurrence.FR,
            Calendar.SATURDAY to EventRecurrence.SA
        )

        private val dayToUtilDay = mapOf(
            EventRecurrence.SU to Calendar.SUNDAY,
            EventRecurrence.MO to Calendar.MONDAY,
            EventRecurrence.TU to Calendar.TUESDAY,
            EventRecurrence.WE to Calendar.WEDNESDAY,
            EventRecurrence.TH to Calendar.THURSDAY,
            EventRecurrence.FR to Calendar.FRIDAY,
            EventRecurrence.SA to Calendar.SATURDAY
        )

        private val periodTypeToFrequency = mapOf(
            PeriodType.ONCE to 0,
            PeriodType.HOUR to EventRecurrence.HOURLY,
            PeriodType.DAY to EventRecurrence.DAILY,
            PeriodType.WEEK to EventRecurrence.WEEKLY,
            PeriodType.MONTH to EventRecurrence.MONTHLY,
            PeriodType.END_OF_MONTH to EventRecurrence.MONTHLY,
            PeriodType.LAST_WEEKDAY to EventRecurrence.MONTHLY,
            PeriodType.NTH_WEEKDAY to EventRecurrence.MONTHLY,
            PeriodType.YEAR to EventRecurrence.YEARLY
        )

        private val frequencyToPeriodType = mapOf(
            0 to PeriodType.ONCE,
            EventRecurrence.HOURLY to PeriodType.HOUR,
            EventRecurrence.DAILY to PeriodType.DAY,
            EventRecurrence.WEEKLY to PeriodType.WEEK,
            EventRecurrence.MONTHLY to PeriodType.MONTH,
            EventRecurrence.YEARLY to PeriodType.YEAR
        )

        // TODO move this to strings.xml
        private val numerals = arrayOf("1st", "2nd", "3rd", "4th", "5th")

        /**
         * Returns a new [Recurrence] with the [PeriodType] specified in the old format.
         *
         * @param period Period in milliseconds since Epoch (old format to define a period)
         * @return Recurrence with the specified period.
         */
        @JvmStatic
        fun fromLegacyPeriod(period: Long): Recurrence {
            var result = (period / RecurrenceParser.YEAR_MILLIS).toInt()
            if (result > 0) {
                val recurrence = Recurrence(PeriodType.YEAR)
                recurrence.multiplier = result
                return recurrence
            }
            result = (period / RecurrenceParser.MONTH_MILLIS).toInt()
            if (result > 0) {
                val recurrence = Recurrence(PeriodType.MONTH)
                recurrence.multiplier = result
                return recurrence
            }
            result = (period / RecurrenceParser.WEEK_MILLIS).toInt()
            if (result > 0) {
                val recurrence = Recurrence(PeriodType.WEEK)
                recurrence.multiplier = result
                return recurrence
            }
            result = (period / RecurrenceParser.DAY_MILLIS).toInt()
            if (result > 0) {
                val recurrence = Recurrence(PeriodType.DAY)
                recurrence.multiplier = result
                return recurrence
            }
            result = (period / RecurrenceParser.HOUR_MILLIS).toInt()
            if (result > 0) {
                val recurrence = Recurrence(PeriodType.HOUR)
                recurrence.multiplier = result
                return recurrence
            }
            return Recurrence(PeriodType.DAY)
        }
    }
}
