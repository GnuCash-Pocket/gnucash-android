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

import org.gnucash.android.R
import org.gnucash.android.app.GnuCashApplication
import org.gnucash.android.ui.util.RecurrenceParser
import org.joda.time.Days
import org.joda.time.Hours
import org.joda.time.LocalDate
import org.joda.time.LocalDateTime
import org.joda.time.Months
import org.joda.time.ReadablePeriod
import org.joda.time.Weeks
import org.joda.time.Years
import java.sql.Timestamp
import java.util.Collections
import java.util.Date
import org.joda.time.format.DateTimeFormat

/**
 * Model for recurrences in the database
 *
 * Basically a wrapper around [PeriodType]
 */
class Recurrence(periodType: PeriodType) : BaseModel() {
    /**
     * Return the [PeriodType] for this recurrence
     */
    var periodType: PeriodType

    /**
     * Timestamp of start of recurrence
     */
    var periodStart: Timestamp

    /**
     * End date of the recurrence period
     */
    var periodEnd: Timestamp? = null
        private set

    /**
     * Days of week on which to run the recurrence
     */
    private var _byDays = emptyList<Int>()

    /**
     * The multiplier for the period type. The default multiplier is 1.
     * e.g. bi-weekly actions have period type [PeriodType.WEEK] and multiplier 2.
     */
    var multiplier = 1 //multiplier for the period type

    init {
        this.periodType = periodType
        periodStart = Timestamp(System.currentTimeMillis())
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
            var baseMillis: Long = 0
            when (periodType) {
                PeriodType.HOUR -> baseMillis = RecurrenceParser.HOUR_MILLIS
                PeriodType.DAY -> baseMillis = RecurrenceParser.DAY_MILLIS
                PeriodType.WEEK -> baseMillis = RecurrenceParser.WEEK_MILLIS
                PeriodType.MONTH -> baseMillis = RecurrenceParser.MONTH_MILLIS
                PeriodType.YEAR -> baseMillis = RecurrenceParser.YEAR_MILLIS
            }
            return multiplier * baseMillis
        }

    /**
     * Returns the event schedule (start, end and recurrence)
     *
     * @return String description of repeat schedule
     */
    val repeatString: String
        get() {
            val repeatBuilder = StringBuilder(frequencyRepeatString)
            val context = GnuCashApplication.getAppContext()
            val dayOfWeek = dayOfWeekFormatter.print(periodStart.time)
            if (periodType === PeriodType.WEEK) {
                repeatBuilder.append(" ")
                    .append(context.getString(R.string.repeat_on_weekday, dayOfWeek))
            }
            if (periodEnd != null) {
                val endDateString = DateTimeFormat.mediumDate().print(periodEnd!!.time)
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
    val ruleString: String
        get() {
            val separator = ";"
            val ruleBuilder = StringBuilder()

//        =======================================================================
            //This section complies with the formal rules, but the betterpickers library doesn't like/need it
//        SimpleDateFormat startDateFormat = new SimpleDateFormat("'TZID'=zzzz':'yyyyMMdd'T'HHmmss", Locale.US);
//        ruleBuilder.append("DTSTART;");
//        ruleBuilder.append(startDateFormat.format(new Date(mStartDate)));
//            ruleBuilder.append("\n");
//        ruleBuilder.append("RRULE:");
//        ========================================================================
            ruleBuilder.append("FREQ=").append(periodType.frequencyDescription).append(separator)
            ruleBuilder.append("INTERVAL=").append(multiplier).append(separator)
            if (count > 0) ruleBuilder.append("COUNT=").append(count).append(separator)
            ruleBuilder.append(periodType.getByParts(periodStart.time)).append(separator)
            return ruleBuilder.toString()
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
            var endDate: LocalDateTime? = null
            when (periodType) {
                PeriodType.HOUR -> endDate =
                    LocalDateTime(System.currentTimeMillis()).plusHours(interval)

                PeriodType.DAY -> endDate =
                    LocalDateTime(System.currentTimeMillis()).plusDays(interval)

                PeriodType.WEEK -> endDate =
                    startDate.dayOfWeek().withMaximumValue().plusWeeks(interval)

                PeriodType.MONTH -> endDate =
                    startDate.dayOfMonth().withMaximumValue().plusMonths(interval)

                PeriodType.YEAR -> endDate =
                    startDate.dayOfYear().withMaximumValue().plusYears(interval)
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
        val startDate = LocalDateTime(periodStart.time)
        val endDate: LocalDateTime
        val interval = multiplier
        when (periodType) {
            PeriodType.HOUR -> {
                endDate = startDate.plusHours(numberOfPeriods)
                return Hours.hoursBetween(startDate, endDate).hours
            }

            PeriodType.DAY -> {
                endDate = startDate.plusDays(numberOfPeriods)
                return Days.daysBetween(startDate, endDate).days
            }

            PeriodType.WEEK -> {
                endDate = startDate.dayOfWeek().withMaximumValue().plusWeeks(numberOfPeriods)
                return Weeks.weeksBetween(startDate, endDate).weeks / interval
            }

            PeriodType.MONTH -> {
                endDate = startDate.dayOfMonth().withMaximumValue().plusMonths(numberOfPeriods)
                return Months.monthsBetween(startDate, endDate).months / interval
            }

            PeriodType.YEAR -> {
                endDate = startDate.dayOfYear().withMaximumValue().plusYears(numberOfPeriods)
                return Years.yearsBetween(startDate, endDate).years / interval
            }
        }
    }

    /**
     * Return the name of the current period
     *
     * @return String of current period
     */
    fun getTextOfCurrentPeriod(periodNum: Int): String {
        val startDate = LocalDate(periodStart.time)
        when (periodType) {
            PeriodType.HOUR -> {}
            PeriodType.DAY -> return startDate.dayOfWeek().asText
            PeriodType.WEEK -> return startDate.weekOfWeekyear().asText
            PeriodType.MONTH -> return startDate.monthOfYear().asText
            PeriodType.YEAR -> return startDate.year().asText
        }
        return "Period $periodNum"
    }

    /**
     * The days of week on which to run the recurrence.
     *
     * Days are expressed as defined in [java.util.Calendar].
     * For example, Calendar.MONDAY
     *
     */
    var byDays: List<Int>
        get() = Collections.unmodifiableList(_byDays)
        set(byDays) {
            _byDays = ArrayList(byDays)
        }

    /**
     * Computes the number of occurrences of this recurrences between start and end date
     *
     * If there is no end date or the PeriodType is unknown, it returns -1
     *
     * @return Number of occurrences, or -1 if there is no end date
     */
    val count: Int
        get() {
            if (periodEnd == null) return -1
            val multiple = multiplier
            val jodaPeriod: ReadablePeriod
            jodaPeriod = when (periodType) {
                PeriodType.HOUR -> Hours.hours(multiple)
                PeriodType.DAY -> Days.days(multiple)
                PeriodType.WEEK -> Weeks.weeks(multiple)
                PeriodType.MONTH -> Months.months(multiple)
                PeriodType.YEAR -> Years.years(multiple)
            }
            var count = 0
            var startTime = LocalDateTime(periodStart.time)
            while (startTime.toDateTime().millis < periodEnd!!.time) {
                ++count
                startTime += jodaPeriod
            }
            return count

            /*
        //this solution does not use looping, but is not very accurate

        int multiplier = mMultiplier;
        LocalDateTime startDate = new LocalDateTime(mPeriodStart.getTime());
        LocalDateTime endDate = new LocalDateTime(mPeriodEnd.getTime());
        switch (mPeriodType){
            case DAY:
                return Days.daysBetween(startDate, endDate).dividedBy(multiplier).getDays();
            case WEEK:
                return Weeks.weeksBetween(startDate, endDate).dividedBy(multiplier).getWeeks();
            case MONTH:
                return Months.monthsBetween(startDate, endDate).dividedBy(multiplier).getMonths();
            case YEAR:
                return Years.yearsBetween(startDate, endDate).dividedBy(multiplier).getYears();
            default:
                return -1;
        }
*/
        }

    /**
     * Sets the end time of this recurrence by specifying the number of occurences
     *
     * @param numberOfOccurences Number of occurences from the start time
     */
    fun setPeriodEnd(numberOfOccurences: Int) {
        val localDate = LocalDateTime(periodStart.time)
        val endDate: LocalDateTime
        val occurrenceDuration = numberOfOccurences * multiplier
        endDate = when (periodType) {
            PeriodType.HOUR -> localDate.plusHours(occurrenceDuration)
            PeriodType.DAY -> localDate.plusDays(occurrenceDuration)
            PeriodType.WEEK -> localDate.plusWeeks(occurrenceDuration)
            PeriodType.MONTH -> localDate.plusMonths(occurrenceDuration)
            PeriodType.YEAR -> localDate.plusYears(occurrenceDuration)
        }
        periodEnd = Timestamp(endDate.toDateTime().millis)
    }

    /**
     * Set period end date
     *
     * @param endTimestamp End time in milliseconds
     */
    fun setPeriodEnd(endTimestamp: Timestamp?) {
        periodEnd = endTimestamp
    }

    /**
     * Returns a localized string describing the period type's frequency.
     *
     * @return String describing the period type
     */
    private val frequencyRepeatString: String
        get() {
            val res = GnuCashApplication.getAppContext().resources
            return when (periodType) {
                PeriodType.HOUR -> res.getQuantityString(
                    R.plurals.label_every_x_hours,
                    multiplier,
                    multiplier
                )

                PeriodType.DAY -> res.getQuantityString(
                    R.plurals.label_every_x_days,
                    multiplier,
                    multiplier
                )

                PeriodType.WEEK -> res.getQuantityString(
                    R.plurals.label_every_x_weeks,
                    multiplier,
                    multiplier
                )

                PeriodType.MONTH -> res.getQuantityString(
                    R.plurals.label_every_x_months,
                    multiplier,
                    multiplier
                )

                PeriodType.YEAR -> res.getQuantityString(
                    R.plurals.label_every_x_years,
                    multiplier,
                    multiplier
                )

            }
        }

    companion object {
        private val dayOfWeekFormatter = DateTimeFormat.forPattern("EEEE")

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
