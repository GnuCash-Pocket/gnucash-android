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
package org.gnucash.android.test.unit.model

import org.assertj.core.api.Assertions.assertThat
import org.gnucash.android.model.PeriodType
import org.gnucash.android.model.Recurrence
import org.gnucash.android.model.ScheduledAction
import org.gnucash.android.test.unit.GnuCashTest
import org.gnucash.android.ui.util.RecurrenceParser
import org.gnucash.android.util.NEVER
import org.gnucash.android.util.toMillis
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.joda.time.LocalDateTime
import org.junit.Test
import java.util.Calendar

/**
 * Test scheduled actions
 */
class ScheduledActionTest : GnuCashTest() {
    @Test
    fun settingStartTime_shouldSetRecurrenceStart() {
        val startTime = getTimeInMillis(2014, 8, 26)
        val scheduledAction = ScheduledAction(ScheduledAction.ActionType.TRANSACTION) {
            this.startDate = startTime
        }
        assertThat(scheduledAction.recurrence.periodType).isEqualTo(PeriodType.ONCE)

        val recurrence = Recurrence(PeriodType.MONTH)
        assertThat(recurrence.periodStart).isNotEqualTo(startTime)
        scheduledAction.setRecurrence(recurrence)
        assertThat(recurrence.periodStart).isEqualTo(startTime)

        val newStartTime = getTimeInMillis(2015, 6, 6)
        scheduledAction.startDate = newStartTime
        assertThat(recurrence.periodStart).isEqualTo(newStartTime)
    }

    @Test
    fun settingEndTime_shouldSetRecurrenceEnd() {
        val endTime = getTimeInMillis(2014, 8, 26)
        val scheduledAction = ScheduledAction(ScheduledAction.ActionType.TRANSACTION) {
            endDate = endTime
        }
        assertThat(scheduledAction.recurrence.periodType).isEqualTo(PeriodType.ONCE)

        val recurrence = Recurrence(PeriodType.MONTH)
        assertThat(recurrence.periodEnd).isNull()
        scheduledAction.setRecurrence(recurrence)
        assertThat(recurrence.periodEnd).isEqualTo(endTime)

        val newEndTime = getTimeInMillis(2015, 6, 6)
        scheduledAction.endDate = newEndTime
        assertThat(recurrence.periodEnd).isEqualTo(newEndTime)
    }

    @Test
    fun settingRecurrence_shouldSetScheduledActionStartTime() {
        val scheduledAction = ScheduledAction(ScheduledAction.ActionType.EXPORT)
        assertThat(scheduledAction.startDate).isEqualTo(NEVER)

        val startTime = getTimeInMillis(2014, 8, 26)
        val recurrence = Recurrence(PeriodType.WEEK).apply {
            periodStart = startTime
        }
        scheduledAction.setRecurrence(recurrence)
        assertThat(scheduledAction.startDate).isEqualTo(startTime)
    }

    @Test
    fun settingRecurrence_shouldSetEndTime() {
        val endTime = getTimeInMillis(2017, 8, 26)
        val recurrence = Recurrence(PeriodType.WEEK)
        val scheduledAction = ScheduledAction(ScheduledAction.ActionType.EXPORT) {
            endDate = endTime
            setRecurrence(recurrence)
        }
        assertThat(scheduledAction.startDate).isEqualTo(NEVER)
        assertThat(scheduledAction.endDate).isEqualTo(endTime)
    }

    /**
     * Checks that scheduled actions accurately compute the next run time based on the start date
     * and the last time the action was run
     */
    @Test
    fun testComputingNextScheduledExecution() {
        val scheduledAction = ScheduledAction(ScheduledAction.ActionType.TRANSACTION)

        val startDate = DateTime(2015, 8, 15, 12, 0)
        val recurrence = Recurrence(PeriodType.MONTH, 2).apply {
            periodStart = startDate.millis
        }
        scheduledAction.setRecurrence(recurrence)

        assertThat(scheduledAction.computeNextCountBasedScheduledExecutionTime())
            .isEqualTo(startDate.millis)

        scheduledAction.instanceCount = 4
        val expectedTime = DateTime(2016, 2, 15, 12, 0)
        assertThat(scheduledAction.computeNextCountBasedScheduledExecutionTime())
            .isEqualTo(expectedTime.millis)
    }

    /**
     * Weekly actions scheduled to run on multiple days of the week should be due
     * in each of them in the same week.
     *
     *
     * For an action scheduled on Mondays and Thursdays, we test that, if
     * the last run was on Monday, the next should be due on the Thursday
     * of the same week instead of the following week.
     */
    @Test
    fun multiDayOfWeekWeeklyActions_shouldBeDueOnEachDayOfWeekSet() {
        val recurrence = Recurrence(PeriodType.WEEK).apply {
            byDays = listOf(Calendar.MONDAY, Calendar.THURSDAY)
        }
        val scheduledAction = ScheduledAction(ScheduledAction.ActionType.EXPORT) {
            setRecurrence(recurrence)
            instanceCount = 1
            startDate = DateTime(2016, 6, 6, 9, 0).millis
            lastRunDate = DateTime(2017, 4, 17, 9, 0).millis // Monday
        }

        val expectedNextDueDate = DateTime(2017, 4, 20, 9, 0).millis // Thursday
        assertThat(scheduledAction.computeNextTimeBasedScheduledExecutionTime())
            .isEqualTo(expectedNextDueDate)
    }

    /**
     * Weekly actions scheduled with multiplier should skip intermediate
     * weeks and be due in the specified day of the week.
     */
    @Test
    fun weeklyActionsWithMultiplier_shouldBeDueOnTheDayOfWeekSet() {
        val recurrence = Recurrence(PeriodType.WEEK, 2).apply {
            byDays = listOf(Calendar.WEDNESDAY)
        }
        val scheduledAction = ScheduledAction(ScheduledAction.ActionType.EXPORT) {
            setRecurrence(recurrence)
            startDate = DateTime(2016, 6, 6, 9, 0).millis
            lastRunDate = DateTime(2017, 4, 12, 9, 0).millis // Wednesday
            instanceCount = 2 // 2016-06-05 + 2017-04-12
        }

        // Wednesday, 2 weeks after the last run
        val expectedNextDueDate = DateTime(2017, 4, 26, 9, 0).millis
        assertThat(scheduledAction.computeNextTimeBasedScheduledExecutionTime())
            .isEqualTo(expectedNextDueDate)
    }

    /**
     * Weekly actions should return a date in the future when no
     * days of the week have been set in the recurrence.
     *
     *
     * See ScheduledAction.computeNextTimeBasedScheduledExecutionTime()
     */
    @Test
    fun weeklyActionsWithoutDayOfWeekSet_shouldReturnDateInTheFuture() {
        val recurrence = Recurrence(PeriodType.WEEK).apply {
            byDays = emptyList()
        }
        val scheduledAction = ScheduledAction(ScheduledAction.ActionType.EXPORT).apply {
            setRecurrence(recurrence)
            instanceCount = 1
            startDate = DateTime(2016, 6, 6, 9, 0).millis
            lastRunDate = DateTime(2017, 4, 12, 9, 0).millis
        }

        val now = LocalDateTime.now().toMillis()
        assertThat(scheduledAction.computeNextTimeBasedScheduledExecutionTime())
            .isGreaterThan(now)
    }

    @Test
    fun `recurrence with fixed count`() {
        val rrule = "FREQ=WEEKLY;COUNT=5;WKST=SU;BYDAY=SA"
        val recurrence = RecurrenceParser.parse(rrule)
        val scheduledActionParsed = ScheduledAction(ScheduledAction.ActionType.TRANSACTION)
        scheduledActionParsed.setRecurrence(recurrence)

        assertThat(scheduledActionParsed.recurrence).isNotNull
        assertThat(scheduledActionParsed.periodType).isEqualTo(PeriodType.WEEK)
        assertThat(scheduledActionParsed.totalPlannedExecutionCount).isEqualTo(5)
        assertThat(scheduledActionParsed.recurrence.multiplier).isOne
        assertThat(scheduledActionParsed.recurrence.count).isEqualTo(5)
        assertThat(scheduledActionParsed.recurrence.weekStart).isEqualTo(Calendar.SUNDAY)
        assertThat(scheduledActionParsed.recurrence.byDays).hasSize(1)
        assertThat(scheduledActionParsed.recurrence.byDays[0]).isEqualTo(Calendar.SATURDAY)
        assertThat(scheduledActionParsed.recurrence.eventRaw.until).isNullOrEmpty()
        assertThat(scheduledActionParsed.endDate).isEqualTo(NEVER)

        val scheduledAction = ScheduledAction(ScheduledAction.ActionType.TRANSACTION)
        scheduledAction.periodType = PeriodType.WEEK
        scheduledAction.recurrence.weekStart = Calendar.SUNDAY
        scheduledAction.recurrence.byDays = listOf(Calendar.SATURDAY)
        scheduledAction.totalPlannedExecutionCount = 5

        val ruleString = scheduledAction.ruleString
        assertThat(ruleString).isEqualTo(rrule)
    }

    @Test
    fun `recurrence with fixed end date`() {
        val rrule = "FREQ=WEEKLY;UNTIL=20261031T184014Z;WKST=SU;BYDAY=SA"
        val recurrence = RecurrenceParser.parse(rrule)
        val scheduledActionParsed = ScheduledAction(ScheduledAction.ActionType.TRANSACTION)
        scheduledActionParsed.setRecurrence(recurrence)

        val endDate = LocalDateTime(2026, 10, 31, 18, 40, 14).toMillis(DateTimeZone.UTC)
        assertThat(scheduledActionParsed.periodType).isEqualTo(PeriodType.WEEK)
        assertThat(scheduledActionParsed.totalPlannedExecutionCount).isZero
        assertThat(scheduledActionParsed.recurrence).isNotNull
        assertThat(scheduledActionParsed.recurrence.multiplier).isOne
        assertThat(scheduledActionParsed.recurrence.count).isZero
        assertThat(scheduledActionParsed.recurrence.weekStart).isEqualTo(Calendar.SUNDAY)
        assertThat(scheduledActionParsed.recurrence.byDays).hasSize(1)
        assertThat(scheduledActionParsed.recurrence.byDays[0]).isEqualTo(Calendar.SATURDAY)
        assertThat(scheduledActionParsed.recurrence.eventRaw.until).isEqualTo("20261031T184014Z")
        assertThat(scheduledActionParsed.endDate).isEqualTo(endDate)

        val scheduledAction = ScheduledAction(ScheduledAction.ActionType.TRANSACTION)
        scheduledAction.periodType = PeriodType.WEEK
        scheduledAction.recurrence.weekStart = Calendar.SUNDAY
        scheduledAction.recurrence.byDays = listOf(Calendar.SATURDAY)
        scheduledAction.totalPlannedExecutionCount = 0
        scheduledAction.endDate = endDate

        val ruleString = scheduledAction.ruleString
        assertThat(ruleString).isEqualTo(rrule)
    }

    private fun getTimeInMillis(year: Int, month: Int, day: Int): Long {
        val calendar = Calendar.getInstance()
        calendar[year, month] = day
        calendar[Calendar.MILLISECOND] = 0
        return calendar.timeInMillis
    } //todo add test for computing the scheduledaction endtime from the recurrence count
}
