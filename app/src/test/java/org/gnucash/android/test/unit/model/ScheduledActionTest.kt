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
import org.joda.time.DateTime
import org.joda.time.LocalDateTime
import org.junit.Test
import java.util.Arrays
import java.util.Calendar

/**
 * Test scheduled actions
 */
class ScheduledActionTest : GnuCashTest() {
    @Test
    fun settingStartTime_shouldSetRecurrenceStart() {
        val scheduledAction = ScheduledAction(ScheduledAction.ActionType.TRANSACTION)
        val startTime = getTimeInMillis(2014, 8, 26)
        scheduledAction.startTime = startTime
        assertThat(scheduledAction.recurrence).isNull()

        val recurrence = Recurrence(PeriodType.MONTH)
        assertThat(recurrence.periodStart).isNotEqualTo(startTime)
        scheduledAction.setRecurrence(recurrence)
        assertThat(recurrence.periodStart).isEqualTo(startTime)

        val newStartTime = getTimeInMillis(2015, 6, 6)
        scheduledAction.startTime = newStartTime
        assertThat(recurrence.periodStart).isEqualTo(newStartTime)
    }

    @Test
    fun settingEndTime_shouldSetRecurrenceEnd() {
        val scheduledAction = ScheduledAction(ScheduledAction.ActionType.TRANSACTION)
        val endTime = getTimeInMillis(2014, 8, 26)
        scheduledAction.endTime = endTime
        assertThat(scheduledAction.recurrence).isNull()

        val recurrence = Recurrence(PeriodType.MONTH)
        assertThat(recurrence.periodEnd).isNull()
        scheduledAction.setRecurrence(recurrence)
        assertThat(recurrence.periodEnd).isEqualTo(endTime)

        val newEndTime = getTimeInMillis(2015, 6, 6)
        scheduledAction.endTime = newEndTime
        assertThat(recurrence.periodEnd).isEqualTo(newEndTime)
    }

    @Test
    fun settingRecurrence_shouldSetScheduledActionStartTime() {
        val scheduledAction = ScheduledAction(ScheduledAction.ActionType.BACKUP)
        assertThat(scheduledAction.startTime).isZero()

        val startTime = getTimeInMillis(2014, 8, 26)
        val recurrence = Recurrence(PeriodType.WEEK)
        recurrence.periodStart = startTime
        scheduledAction.setRecurrence(recurrence)
        assertThat(scheduledAction.startTime).isEqualTo(startTime)
    }

    @Test
    fun settingRecurrence_shouldSetEndTime() {
        val scheduledAction = ScheduledAction(ScheduledAction.ActionType.BACKUP)
        assertThat(scheduledAction.startTime).isZero()

        val endTime = getTimeInMillis(2017, 8, 26)
        val recurrence = Recurrence(PeriodType.WEEK)
        recurrence.periodEnd = endTime
        scheduledAction.setRecurrence(recurrence)

        assertThat(scheduledAction.endTime).isEqualTo(endTime)
    }

    /**
     * Checks that scheduled actions accurately compute the next run time based on the start date
     * and the last time the action was run
     */
    @Test
    fun testComputingNextScheduledExecution() {
        val scheduledAction = ScheduledAction(ScheduledAction.ActionType.TRANSACTION)
        val periodType = PeriodType.MONTH

        val recurrence = Recurrence(periodType)
        recurrence.multiplier = 2
        val startDate = DateTime(2015, 8, 15, 12, 0)
        recurrence.periodStart = startDate.millis
        scheduledAction.setRecurrence(recurrence)

        assertThat(scheduledAction.computeNextCountBasedScheduledExecutionTime())
            .isEqualTo(startDate.millis)

        scheduledAction.executionCount = 3
        val expectedTime = DateTime(2016, 2, 15, 12, 0)
        assertThat(scheduledAction.computeNextCountBasedScheduledExecutionTime())
            .isEqualTo(expectedTime.millis)
    }

    @Test
    fun testComputingTimeOfLastSchedule() {
        val scheduledAction = ScheduledAction(ScheduledAction.ActionType.TRANSACTION)
        val periodType = PeriodType.WEEK
        val recurrence = Recurrence(periodType)
        recurrence.multiplier = 2
        scheduledAction.setRecurrence(recurrence)
        val startDate = DateTime(2016, 6, 6, 9, 0)
        scheduledAction.startTime = startDate.millis

        assertThat(scheduledAction.timeOfLastSchedule).isEqualTo(-1L)

        scheduledAction.executionCount = 3
        val expectedDate = DateTime(2016, 7, 4, 9, 0)
        assertThat(scheduledAction.timeOfLastSchedule).isEqualTo(expectedDate.millis)
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
        val scheduledAction = ScheduledAction(ScheduledAction.ActionType.BACKUP)
        val recurrence = Recurrence(PeriodType.WEEK)
        recurrence.byDays = Arrays.asList(Calendar.MONDAY, Calendar.THURSDAY)
        scheduledAction.setRecurrence(recurrence)
        scheduledAction.startTime = DateTime(2016, 6, 6, 9, 0).millis
        scheduledAction.lastRunTime = DateTime(2017, 4, 17, 9, 0).millis // Monday

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
        val scheduledAction = ScheduledAction(ScheduledAction.ActionType.BACKUP)
        val recurrence = Recurrence(PeriodType.WEEK)
        recurrence.multiplier = 2
        recurrence.byDays = listOf(Calendar.WEDNESDAY)
        scheduledAction.setRecurrence(recurrence)
        scheduledAction.startTime = DateTime(2016, 6, 6, 9, 0).millis
        scheduledAction.lastRunTime = DateTime(2017, 4, 12, 9, 0).millis // Wednesday

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
        val scheduledAction = ScheduledAction(ScheduledAction.ActionType.BACKUP)
        val recurrence = Recurrence(PeriodType.WEEK)
        recurrence.byDays = emptyList()
        scheduledAction.setRecurrence(recurrence)
        scheduledAction.startTime = DateTime(2016, 6, 6, 9, 0).millis
        scheduledAction.lastRunTime = DateTime(2017, 4, 12, 9, 0).millis

        val now = LocalDateTime.now().toDateTime().millis
        assertThat(scheduledAction.computeNextTimeBasedScheduledExecutionTime())
            .isGreaterThan(now)
    }

    private fun getTimeInMillis(year: Int, month: Int, day: Int): Long {
        val calendar = Calendar.getInstance()
        calendar[year, month] = day
        calendar[Calendar.MILLISECOND] = 0
        return calendar.timeInMillis
    } //todo add test for computing the scheduledaction endtime from the recurrence count
}
