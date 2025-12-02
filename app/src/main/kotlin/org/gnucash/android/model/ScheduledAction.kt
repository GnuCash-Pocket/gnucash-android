/*
 * Copyright (c) 2014 - 2015 Ngewi Fet <ngewif@gmail.com>
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
import androidx.annotation.StringRes
import org.gnucash.android.R
import org.gnucash.android.app.GnuCashApplication
import org.gnucash.android.export.ExportParams
import org.gnucash.android.util.dayOfWeek
import org.gnucash.android.util.lastDayOfMonth
import org.gnucash.android.util.lastDayOfWeek
import org.joda.time.LocalDateTime
import org.joda.time.format.DateTimeFormat
import java.util.Calendar
import java.util.Locale

/**
 * Represents a scheduled event which is stored in the database and run at regular period
 *
 * @author Ngewi Fet <ngewif@gmail.com>
 */
class ScheduledAction    //all actions are enabled by default
    (
    /**
     * Type of event being scheduled
     */
    var actionType: ActionType = ActionType.TRANSACTION
) : BaseModel() {

    /** "Scheduled Transaction Name" */
    var name: String? = null

    /**
     * The tag saves additional information about the scheduled action,
     * e.g. such as export parameters for scheduled backups
     */
    var tag: String? = null

    /**
     * Recurrence of this scheduled action
     */
    var recurrence: Recurrence? = null
        private set

    /**
     * Types of events which can be scheduled
     */
    enum class ActionType(val value: String, @StringRes val labelId: Int) {
        TRANSACTION("TRANSACTION", R.string.action_transaction),

        EXPORT("BACKUP", R.string.action_backup);

        companion object {
            private val _values = values()

            fun of(value: String): ActionType {
                val valueLower = value.uppercase(Locale.ROOT)
                return _values.firstOrNull { it.value == valueLower } ?: TRANSACTION
            }
        }
    }

    /**
     * Next scheduled run of Event
     */
    var lastRunTime: Long = 0

    /**
     * Unique ID of the template from which the recurring event will be executed.
     * For example, transaction UID
     */
    var actionUID: String? = null

    /**
     * "TRUE if the scheduled transaction is enabled."
     */
    var isEnabled = true

    /**
     * "Total number of occurrences for this scheduled transaction."
     */
    var totalPlannedExecutionCount = 0

    /**
     * "Number of instances of this scheduled transaction."
     */
    var instanceCount = 0

    /**
     * "TRUE if the transaction will be automatically created when its time comes."
     */
    var isAutoCreate = false

    /**
     * "TRUE if the the user will be notified when the transaction is automatically created."
     *
     * This flag is currently unused in the app. It is only included here for compatibility with GnuCash desktop XML
     */
    var isAutoCreateNotify = false

    /**
     * "Number of days in advance to create this scheduled transaction."
     *
     * This flag is currently unused in the app. It is only included here for compatibility with GnuCash desktop XML
     */
    var advanceCreateDays = 0

    /**
     * "Number of days in advance to remind about this scheduled transaction."
     *
     * This flag is currently unused in the app. It is only included here for compatibility with GnuCash desktop XML
     */
    var advanceRemindDays = 0

    /**
     * Returns the time when the last schedule in the sequence of planned executions was executed.
     * This relies on the number of executions of the scheduled action
     *
     * This is different from [.getLastRunTime] which returns the date when the system last
     * ran the scheduled action.
     *
     * @return Time of last schedule, or `-1` if the scheduled action has never been run
     */
    val timeOfLastSchedule: Long
        get() {
            val count = instanceCount
            if (count <= 0) return -1
            recurrence?.let { recurrence ->
                var startDate = LocalDateTime(startDate)
                val multiplier = recurrence.multiplier
                val factor = (count - 1) * multiplier
                startDate = when (recurrence.periodType) {
                    PeriodType.ONCE -> startDate
                    PeriodType.HOUR -> startDate.plusHours(factor)
                    PeriodType.DAY -> startDate.plusDays(factor)
                    PeriodType.WEEK -> startDate.plusWeeks(factor)
                    PeriodType.MONTH -> startDate.plusMonths(factor)
                    PeriodType.YEAR -> startDate.plusYears(factor)
                    PeriodType.LAST_WEEKDAY -> startDate.plusMonths(factor).lastDayOfWeek(startDate)
                    PeriodType.NTH_WEEKDAY -> startDate.plusMonths(factor).dayOfWeek(startDate)
                    PeriodType.END_OF_MONTH -> startDate.plusMonths(factor).lastDayOfMonth()
                }
                return startDate.toDateTime().millis
            }
            return startDate
        }

    /**
     * Computes the next time that this scheduled action is supposed to be
     * executed based on the execution count.
     *
     * This method does not consider the end time, or number of times it should be run.
     * It only considers when the next execution would theoretically be due.
     *
     * @return Next run time in milliseconds
     */
    fun computeNextCountBasedScheduledExecutionTime(): Long {
        return computeNextScheduledExecutionTimeStartingAt(timeOfLastSchedule)
    }

    /**
     * Computes the next time that this scheduled action is supposed to be
     * executed based on the time of the last run.
     *
     * This method does not consider the end time, or number of times it should be run.
     * It only considers when the next execution would theoretically be due.
     *
     * @return Next run time in milliseconds
     */
    fun computeNextTimeBasedScheduledExecutionTime(): Long {
        return computeNextScheduledExecutionTimeStartingAt(lastRunTime)
    }

    /**
     * Computes the next time that this scheduled action is supposed to be
     * executed starting at startTime.
     *
     * This method does not consider the end time, or number of times it should be run.
     * It only considers when the next execution would theoretically be due.
     *
     * @param startTime time in milliseconds to use as start to compute the next schedule.
     * @return Next run time in milliseconds
     */
    private fun computeNextScheduledExecutionTimeStartingAt(startTime: Long): Long {
        if (startTime <= 0) { // has never been run
            return startDate
        }
        val recurrence = recurrence ?: return startDate
        val multiplier = recurrence.multiplier
        val startDate = LocalDateTime(startTime)
        val nextScheduledExecution: LocalDateTime = when (recurrence.periodType) {
            PeriodType.ONCE -> if (instanceCount < multiplier) {
                startDate
            } else {
                val endTime = endDate
                return if (endTime > 0) endTime else System.currentTimeMillis()
            }
            PeriodType.HOUR -> startDate.plusHours(multiplier)
            PeriodType.DAY -> startDate.plusDays(multiplier)
            PeriodType.WEEK -> computeNextWeeklyExecutionStartingAt(startDate)
            PeriodType.MONTH -> startDate.plusMonths(multiplier)
            PeriodType.YEAR -> startDate.plusYears(multiplier)
            PeriodType.LAST_WEEKDAY -> startDate.plusMonths(multiplier).lastDayOfWeek(startDate)
            PeriodType.NTH_WEEKDAY -> startDate.plusMonths(multiplier).dayOfWeek(startDate)
            PeriodType.END_OF_MONTH -> startDate.plusMonths(multiplier).lastDayOfMonth()
        }
        return nextScheduledExecution.toDateTime().millis
    }

    /**
     * Computes the next time that this weekly scheduled action is supposed to be
     * executed starting at startTime.
     *
     * If no days of the week have been set (GnuCash desktop allows it), it will return a
     * date in the future to ensure ScheduledActionService doesn't execute it.
     *
     * @param startTime LocalDateTime to use as start to compute the next schedule.
     * @return Next run time as a LocalDateTime. A date in the future, if no days of the week
     * were set in the Recurrence.
     */
    private fun computeNextWeeklyExecutionStartingAt(startTime: LocalDateTime): LocalDateTime {
        val recurrence = recurrence ?: return startTime
        if (recurrence.byDays.isEmpty()) return LocalDateTime.now()
            .plusDays(1) // Just a date in the future

        // Look into the week of startTime for another scheduled day of the week
        for (dayOfWeek in recurrence.byDays) {
            val jodaDayOfWeek = convertCalendarDayOfWeekToJoda(dayOfWeek)
            val candidateNextDueTime = startTime.withDayOfWeek(jodaDayOfWeek)
            if (candidateNextDueTime.isAfter(startTime)) return candidateNextDueTime
        }

        // Return the first scheduled day of the week from the next due week
        val firstScheduledDayOfWeek = convertCalendarDayOfWeekToJoda(recurrence.byDays[0])
        return startTime.plusWeeks(recurrence.multiplier)
            .withDayOfWeek(firstScheduledDayOfWeek)
    }

    /**
     * Converts a java.util.Calendar day of the week constant to the
     * org.joda.time.DateTimeConstants equivalent.
     *
     * @param calendarDayOfWeek day of the week constant from java.util.Calendar
     * @return day of the week constant equivalent from org.joda.time.DateTimeConstants
     */
    private fun convertCalendarDayOfWeekToJoda(calendarDayOfWeek: Int): Int {
        val cal = Calendar.getInstance()
        cal[Calendar.DAY_OF_WEEK] = calendarDayOfWeek
        return LocalDateTime.fromCalendarFields(cal).dayOfWeek
    }

    /**
     * Returns the period of this scheduled action in milliseconds.
     *
     * @return Period in milliseconds since Epoch
     */
    @get:Deprecated("Uses fixed values for time of months and years (which actually vary depending on number of days in month or leap year)")
    val period: Long
        get() = recurrence!!.period

    /** "Date for the first occurrence for the scheduled transaction." */
    var startDate: Long = 0L
        set(startDate) {
            field = startDate
            recurrence?.periodStart = startDate
        }

    @Deprecated("renamed", ReplaceWith("startDate"))
    var startTime: Long
        get() = startDate
        set(value) {
            startDate = value
        }

    /**
     * "Date for the scheduled transaction to end."
     */
    var endDate: Long = 0L
        set(endDate) {
            field = endDate
            recurrence?.periodEnd = endDate
        }

    @Deprecated("renamed", ReplaceWith("endDate"))
    var endTime: Long
        get() = endDate
        set(value) {
            endDate = value
        }

    private var _templateAccountUID: String? = null

    /**
     * "Account which holds the template transactions."
     *
     * If no GUID was set, a new one is going to be generated and returned.
     */
    val templateAccountUID: String
        get() {
            var value = _templateAccountUID
            if (value == null) {
                value = generateUID()
                _templateAccountUID = value
            }
            return value
        }

    fun setTemplateAccountUID(uid: String?) {
        _templateAccountUID = uid
    }

    /**
     * Returns the event schedule (start, end and recurrence)
     *
     * @return String description of repeat schedule
     */
    fun getRepeatString(context: Context): String {
        val ruleBuilder = StringBuilder(recurrence!!.getRepeatString(context))
        if (endDate <= 0 && totalPlannedExecutionCount > 0) {
            ruleBuilder.append(", ")
                .append(context.getString(R.string.repeat_x_times, totalPlannedExecutionCount))
        }
        return ruleBuilder.toString()
    }

    /**
     * Creates an RFC 2445 string which describes this recurring event
     *
     * See [recurrance](http://recurrance.sourceforge.net/)
     *
     * @return String describing event
     */
    val ruleString: String
        get() {
            val recurrence = recurrence ?: return ""
            val ruleBuilder = StringBuilder(recurrence.ruleString)
            if (endDate > 0) {
                val df = DateTimeFormat.forPattern("yyyyMMdd'T'HHmmss'Z'").withZoneUTC()
                ruleBuilder.append(";UNTIL=").append(df.print(endDate))
            } else if (totalPlannedExecutionCount > 0) {
                ruleBuilder.append(";COUNT=").append(totalPlannedExecutionCount)
            }
            return ruleBuilder.toString()
        }

    /**
     * Overloaded method for setting the recurrence of the scheduled action.
     *
     * This method allows you to specify the periodicity and the ordinal of it. For example,
     * a recurrence every fortnight would give parameters: [PeriodType.WEEK], ordinal:2
     *
     * @param periodType Periodicity of the scheduled action
     * @param ordinal    Ordinal of the periodicity. If unsure, specify 1
     * @see recurrence
     */
    fun setRecurrence(periodType: PeriodType, ordinal: Int) {
        val recurrence = Recurrence(periodType)
        recurrence.multiplier = ordinal
        setRecurrence(recurrence)
    }

    /**
     * Sets the recurrence pattern of this scheduled action
     *
     * This also sets the start period of the recurrence object, if there is one
     *
     * @param recurrence [Recurrence] object
     */
    fun setRecurrence(recurrence: Recurrence?) {
        this.recurrence = recurrence ?: return
        //if we were parsing XML and parsed the start and end date from the scheduled action first,
        //then use those over the values which might be gotten from the recurrence
        if (startDate > 0) {
            recurrence.periodStart = startDate
        } else {
            startDate = recurrence.periodStart
        }
        if (endDate > 0) {
            recurrence.periodEnd = endDate
        } else {
            val periodEnd = recurrence.periodEnd
            if (periodEnd != null) {
                endDate = periodEnd
            }
        }
    }

    override fun toString(): String {
        return actionType.name + " - " + getRepeatString(GnuCashApplication.appContext)
    }

    fun setExportParams(exportParams: ExportParams) {
        tag = exportParams.toTag()
    }

    fun getExportParams(): ExportParams? {
        val tag = tag ?: return null
        if (tag.isEmpty()) return null
        return ExportParams.parseTag(tag)
    }

    companion object {
        /**
         * Creates a ScheduledAction from a Transaction and a period
         *
         * @param transaction Transaction to be scheduled
         * @param period      Period in milliseconds since Epoch
         * @return Scheduled Action
         */
        @Deprecated("Used for parsing legacy backup files. Use [Recurrence] instead")
        fun parseScheduledAction(transaction: Transaction, period: Long): ScheduledAction {
            val scheduledAction = ScheduledAction(ActionType.TRANSACTION)
            scheduledAction.actionUID = transaction.uid
            val recurrence = Recurrence.fromLegacyPeriod(period)
            scheduledAction.setRecurrence(recurrence)
            return scheduledAction
        }
    }
}