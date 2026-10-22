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
import org.gnucash.android.util.NEVER
import org.gnucash.android.util.dayOfWeek
import org.gnucash.android.util.lastDayOfMonth
import org.gnucash.android.util.lastDayOfWeek
import org.gnucash.android.util.toLocalDayOfWeek
import org.gnucash.android.util.toMillis
import org.joda.time.LocalDateTime
import timber.log.Timber
import java.util.Locale

/**
 * Represents a scheduled event which is stored in the database and run at regular period
 *
 * @author Ngewi Fet <ngewif@gmail.com>
 */
class ScheduledAction(
    /**
     * Type of event being scheduled
     */
    var actionType: ActionType = ActionType.TRANSACTION
) : BaseModel() {

    /** "Scheduled Transaction Name" */
    var name: String = ""

    /**
     * The tag saves additional information about the scheduled action,
     * e.g. such as export parameters for scheduled backups
     */
    var tag: String? = null

    /**
     * Recurrence of this scheduled action
     */
    var recurrence: Recurrence = Recurrence(PeriodType.ONCE)
        private set

    /**
     * Types of events which can be scheduled
     */
    enum class ActionType(val value: String, @param:StringRes val labelId: Int) {
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
    var lastRunDate: Long = 0L

    @Deprecated("renamed", ReplaceWith("lastRunDate"))
    var lastRunTime: Long
        get() = lastRunDate
        set(value) {
            lastRunDate = value
        }

    /**
     * Unique ID of the template from which the recurring event will be executed.
     * For example, transaction UID
     */
    var actionUID: String? = null

    /**
     * "TRUE if the scheduled transaction is enabled."
     * All actions are enabled by default.
     */
    var isEnabled = true

    /**
     * "Total number of occurrences for this scheduled transaction."
     */
    var totalPlannedExecutionCount
        get() = recurrence.count
        set(value) {
            recurrence.count = value
        }

    /**
     * "Number of instances of this scheduled transaction."
     */
    var instanceCount = 0

    /**
     * "TRUE if the transaction will be automatically created when its time comes."
     */
    var isAutoCreate = false

    /**
     * "TRUE if the user will be notified when the transaction is automatically created."
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

    constructor(type: ActionType, builder: ScheduledAction.() -> Unit) : this(type) {
        apply(builder)
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
        val startAt = startDate
        val count = instanceCount
        if (count <= 0) {
            Timber.w("Invalid instance count")
            return startAt
        }
        val factor = (count - 1) * recurrence.multiplier
        return computeNextScheduledExecutionTimeStartingAt(startAt, factor)
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
        val startAt = lastRunDate
        if (startAt < startDate) {
            return computeNextScheduledExecutionTimeStartingAt(startDate, 0)
        }
        val factor = recurrence.multiplier
        return computeNextScheduledExecutionTimeStartingAt(startAt, factor)
    }

    /**
     * Computes the next time that this scheduled action is supposed to be
     * executed starting at startTime.
     *
     * This method does not consider the end time, or number of times it should be run.
     * It only considers when the next execution would theoretically be due.
     *
     * @param startAt time in milliseconds to use as start to compute the next schedule.
     * @return Next run time in milliseconds
     */
    private fun computeNextScheduledExecutionTimeStartingAt(startAt: Long, factor: Int): Long {
        val recurrence = recurrence
        val startDate = LocalDateTime(startAt)
        val nextScheduledExecution: LocalDateTime = when (recurrence.periodType) {
            PeriodType.ONCE -> {
                val endTime = endDate
                return if (endTime > 0) endTime else System.currentTimeMillis()
            }

            PeriodType.HOUR -> startDate.plusHours(factor)
            PeriodType.DAY -> startDate.plusDays(factor)
            PeriodType.WEEK -> computeNextWeeklyExecutionStartingAt(recurrence, startDate, factor)
            PeriodType.MONTH -> startDate.plusMonths(factor)
            PeriodType.YEAR -> startDate.plusYears(factor)
            PeriodType.LAST_WEEKDAY -> startDate.plusMonths(factor).lastDayOfWeek(startDate)
            PeriodType.NTH_WEEKDAY -> startDate.plusMonths(factor).dayOfWeek(startDate)
            PeriodType.END_OF_MONTH -> startDate.plusMonths(factor).lastDayOfMonth()
        }
        return nextScheduledExecution.toMillis()
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
    private fun computeNextWeeklyExecutionStartingAt(
        recurrence: Recurrence,
        startTime: LocalDateTime,
        factor: Int
    ): LocalDateTime {
        if (recurrence.byDays.isEmpty()) {
            return LocalDateTime.now().plusWeeks(1) // Just a date in the future
        }

        // Look into the week of `startTime` for another scheduled day of the week
        for (dayOfWeek in recurrence.byDays) {
            val localDayOfWeek = toLocalDayOfWeek[dayOfWeek] ?: continue
            val candidateNextDueTime = startTime.withDayOfWeek(localDayOfWeek)
            if (candidateNextDueTime.isAfter(startTime)) {
                return candidateNextDueTime
            }
        }

        // Return the first scheduled day of the week from the next due week
        val localDayOfWeek = toLocalDayOfWeek[recurrence.byDays[0]] ?: return startTime
        return startTime.withDayOfWeek(localDayOfWeek).plusWeeks(factor)
    }

    /** "Date for the first occurrence for the scheduled transaction." */
    var startDate: Long
        get() = recurrence.periodStart
        set(value) {
            recurrence.periodStart = value
        }

    /**
     * "Date for the scheduled transaction to end."
     */
    var endDate: Long
        get() = recurrence.periodEnd ?: NEVER
        set(value) {
            recurrence.periodEnd = if (value <= 0L) null else value
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
        return recurrence.formatRepeatString(context)
    }

    /**
     * Creates an RFC 2445 string which describes this recurring event
     *
     * @return String describing event
     */
    val ruleString: String
        get() = recurrence.ruleString

    /**
     * Overloaded method for setting the recurrence of the scheduled action.
     *
     * This method allows you to specify the periodicity and the ordinal of it. For example,
     * a recurrence every fortnight would give parameters: [PeriodType.WEEK], ordinal:2
     *
     * @param periodType Periodicity of the scheduled action
     * @param multiplier    Ordinal of the periodicity. If unsure, specify 1
     * @see recurrence
     */
    fun setRecurrence(periodType: PeriodType, multiplier: Int) {
        setRecurrence(Recurrence(periodType, multiplier))
    }

    /**
     * Sets the recurrence pattern of this scheduled action
     *
     * This also sets the start period of the recurrence object, if there is one
     *
     * @param recurrence [Recurrence] object
     */
    fun setRecurrence(recurrence: Recurrence?) {
        val startDate = this.startDate
        val endDate = this.endDate
        val recurrence = recurrence ?: Recurrence(PeriodType.ONCE)
        this.recurrence = recurrence
        //if we were parsing XML and parsed the start and end date from the scheduled action first,
        //then use those over the values which might be gotten from the recurrence
        if (startDate > 0) {
            recurrence.periodStart = startDate
        }
        if (endDate > 0) {
            recurrence.periodEnd = endDate
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

    fun isEmpty(): Boolean {
        return recurrence.isEmpty()
    }

    var periodType: PeriodType
        get() = recurrence.periodType
        set(value) {
            recurrence.periodType = value
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