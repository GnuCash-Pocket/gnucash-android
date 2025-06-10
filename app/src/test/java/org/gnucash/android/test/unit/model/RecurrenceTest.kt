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

import com.codetroopers.betterpickers.recurrencepicker.EventRecurrence
import org.assertj.core.api.Assertions.assertThat
import org.gnucash.android.model.PeriodType
import org.gnucash.android.model.Recurrence
import org.gnucash.android.test.unit.GnuCashTest
import org.gnucash.android.util.dayOfWeek
import org.gnucash.android.util.weekOfMonth
import org.joda.time.DateTime
import org.joda.time.DateTimeConstants
import org.joda.time.LocalDateTime
import org.joda.time.Weeks
import org.joda.time.format.DateTimeFormat
import org.junit.Test
import java.sql.Timestamp
import java.util.Calendar
import java.util.Locale

/**
 * Test [Recurrence]s
 */
class RecurrenceTest : GnuCashTest() {
    @Test
    fun settingCount_shouldComputeCorrectEndTime() {
        val recurrence = Recurrence(PeriodType.MONTH)

        val startTime = DateTime(2015, 10, 5, 0, 0)
        recurrence.periodStart = startTime.millis
        recurrence.setPeriodEnd(3)

        val expectedEndtime = DateTime(2016, 1, 5, 0, 0)
        assertThat(recurrence.periodEnd).isEqualTo(expectedEndtime.millis)
    }

    /**
     * When the end date of a recurrence is set, we should be able to correctly get the number of occurrences
     */
    @Test
    fun testRecurrenceCountComputation() {
        var recurrence = Recurrence(PeriodType.MONTH)

        val start = DateTime(2015, 10, 5, 0, 0)
        recurrence.periodStart = start.millis

        val end = DateTime(2016, 8, 5, 0, 0)
        recurrence.setPeriodEnd(Timestamp(end.millis))

        assertThat(recurrence.occurrences).isEqualTo(10)

        //test case where last appointment is just a little before end time, but not a complete period since last
        val startTime = DateTime(2016, 6, 6, 9, 0)
        val endTime = DateTime(2016, 8, 29, 10, 0)
        val biWeekly = PeriodType.WEEK
        recurrence = Recurrence(biWeekly)
        recurrence.multiplier = 2
        recurrence.periodStart = startTime.millis
        recurrence.setPeriodEnd(Timestamp(endTime.millis))

        assertThat(recurrence.occurrences).isEqualTo(7)
    }

    /**
     * When no end period is set, getCount() should return the special value -1.
     *
     *
     * Tests for bug [codinguser/gnucash-android#526](https://github.com/codinguser/gnucash-android/issues/526)
     */
    @Test
    fun notSettingEndDate_shouldReturnSpecialCountValue() {
        val recurrence = Recurrence(PeriodType.MONTH)

        val start = DateTime(2015, 10, 5, 0, 0)
        recurrence.periodStart = start.millis

        assertThat(recurrence.occurrences).isEqualTo(-1)
    }

    // Italian "MO" is "LU".
    @Test
    fun no_language() {
        val locale = Locale.getDefault()
        Locale.setDefault(Locale.ITALY)
        val recurrence = Recurrence(PeriodType.WEEK)
        val start = DateTime(2024, 1, 1, 0, 0)
        val days = mutableListOf<Int>()
        days.add(Calendar.MONDAY)
        recurrence.periodStart = start.millis
        recurrence.byDays = days

        assertThat(recurrence.periodType).isEqualTo(PeriodType.WEEK)
        assertThat(recurrence.multiplier).isOne()
        assertThat(recurrence.byDays).isEqualTo(days)
        val ruleString = recurrence.ruleString
        assertThat(ruleString).isEqualTo("FREQ=WEEKLY;INTERVAL=1;BYDAY=MO")
        Locale.setDefault(locale)
    }

    @Test
    fun rfc2445() {
        // Every other week on Tuesday and Thursday, for 8 occurrences:
        var rrule = "FREQ=WEEKLY;INTERVAL=2;COUNT=8;WKST=SU;BYDAY=TU,TH"
        var recurrence = EventRecurrence()
        recurrence.parse(rrule)
        assertThat(recurrence.freq).isEqualTo(EventRecurrence.WEEKLY)
        assertThat(recurrence.interval).isEqualTo(2)
        assertThat(recurrence.count).isEqualTo(8)
        assertThat(recurrence.bydayCount).isEqualTo(2)
        assertThat(recurrence.byday[0]).isEqualTo(EventRecurrence.TU)
        assertThat(recurrence.byday[1]).isEqualTo(EventRecurrence.TH)
        assertThat(recurrence.bydayNum[0]).isZero()
        assertThat(recurrence.bydayNum[1]).isZero()
        assertThat(recurrence.bymonthCount).isZero()
        assertThat(recurrence.bymonthdayCount).isZero()
        assertThat(recurrence.byyeardayCount).isZero()

        // On the last Sunday in October.
        rrule = "FREQ=YEARLY;BYDAY=-1SU;BYMONTH=10"
        recurrence = EventRecurrence()
        recurrence.parse(rrule)
        assertThat(recurrence.freq).isEqualTo(EventRecurrence.YEARLY)
        assertThat(recurrence.interval).isZero()
        assertThat(recurrence.count).isZero()
        assertThat(recurrence.bydayCount).isOne()
        assertThat(recurrence.byday[0]).isEqualTo(EventRecurrence.SU)
        assertThat(recurrence.bydayNum[0]).isEqualTo(-1)
        assertThat(recurrence.bymonthCount).isOne()
        assertThat(recurrence.bymonth[0]).isEqualTo(10)
        assertThat(recurrence.bymonthdayCount).isZero()
        assertThat(recurrence.byyeardayCount).isZero()

        // On the first Sunday in April.
        rrule = "FREQ=YEARLY;BYDAY=1SU;BYMONTH=4"
        recurrence = EventRecurrence()
        recurrence.parse(rrule)
        assertThat(recurrence.freq).isEqualTo(EventRecurrence.YEARLY)
        assertThat(recurrence.interval).isZero()
        assertThat(recurrence.count).isZero()
        assertThat(recurrence.bydayCount).isOne()
        assertThat(recurrence.byday[0]).isEqualTo(EventRecurrence.SU)
        assertThat(recurrence.bydayNum[0]).isOne()
        assertThat(recurrence.bymonthCount).isOne()
        assertThat(recurrence.bymonth[0]).isEqualTo(4)
        assertThat(recurrence.bymonthdayCount).isZero()
        assertThat(recurrence.byyeardayCount).isZero()
    }

    @Test
    fun frequency_formatted() {
        Locale.setDefault(Locale.US)
        assertThat(context).isNotNull()
        val res = context.resources
        assertThat(res).isNotNull()

        val start = DateTime(2024, 1, 1, 0, 0)

        val recurrence = Recurrence(PeriodType.HOUR)
        recurrence.periodStart = start.millis
        recurrence.multiplier = 1
        var formatted1 = recurrence.frequencyRepeatString(context)
        assertThat(formatted1).isEqualTo("Hourly")
        recurrence.multiplier = 2
        formatted1 = recurrence.frequencyRepeatString(context)
        assertThat(formatted1).isEqualTo("Every 2 hours")

        recurrence.periodType = PeriodType.DAY
        recurrence.multiplier = 1
        formatted1 = recurrence.frequencyRepeatString(context)
        assertThat(formatted1).isEqualTo("Daily")
        recurrence.multiplier = 2
        formatted1 = recurrence.frequencyRepeatString(context)
        assertThat(formatted1).isEqualTo("Every 2 days")

        recurrence.periodType = PeriodType.WEEK
        recurrence.multiplier = 1
        formatted1 = recurrence.frequencyRepeatString(context)
        assertThat(formatted1).isEqualTo("Weekly on Monday")
        recurrence.multiplier = 2
        formatted1 = recurrence.frequencyRepeatString(context)
        assertThat(formatted1).isEqualTo("Every 2 weeks on Monday")

        recurrence.periodType = PeriodType.MONTH
        recurrence.multiplier = 1
        formatted1 = recurrence.frequencyRepeatString(context)
        assertThat(formatted1).isEqualTo("Monthly ")
        recurrence.multiplier = 2
        formatted1 = recurrence.frequencyRepeatString(context)
        assertThat(formatted1).isEqualTo("Every 2 months ")

        recurrence.periodType = PeriodType.YEAR
        recurrence.multiplier = 1
        formatted1 = recurrence.frequencyRepeatString(context)
        assertThat(formatted1).isEqualTo("Yearly ")
        recurrence.multiplier = 2
        formatted1 = recurrence.frequencyRepeatString(context)
        assertThat(formatted1).isEqualTo("Every 2 years ")
    }

    @Test
    fun min_max() {
        val date = LocalDateTime(2024, 2, 2, 12, 34, 56, 789)
        var hMin: String
        var hMax: String
        val df = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss").withZoneUTC()

        val h = df.print(date)
        assertThat(h).isEqualTo("2024-02-02 12:34:56")

        var dateMin = date.millisOfDay().withMinimumValue()
        hMin = df.print(dateMin)
        assertThat(hMin).isEqualTo("2024-02-02 00:00:00")
        var dateMax = date.millisOfDay().withMaximumValue()
        hMax = df.print(dateMax)
        assertThat(hMax).isEqualTo("2024-02-02 23:59:59")

        dateMin = date.secondOfMinute().withMinimumValue()
        hMin = df.print(dateMin)
        assertThat(hMin).isEqualTo("2024-02-02 12:34:00")
        dateMax = date.secondOfMinute().withMaximumValue()
        hMax = df.print(dateMax)
        assertThat(hMax).isEqualTo("2024-02-02 12:34:59")

        dateMin = date.minuteOfHour().withMinimumValue()
        hMin = df.print(dateMin)
        assertThat(hMin).isEqualTo("2024-02-02 12:00:56")
        dateMax = date.minuteOfHour().withMaximumValue()
        hMax = df.print(dateMax)
        assertThat(hMax).isEqualTo("2024-02-02 12:59:56")

        dateMin = date.hourOfDay().withMinimumValue()
        hMin = df.print(dateMin)
        assertThat(hMin).isEqualTo("2024-02-02 00:34:56")
        dateMax = date.hourOfDay().withMaximumValue()
        hMax = df.print(dateMax)
        assertThat(hMax).isEqualTo("2024-02-02 23:34:56")

        dateMin = date.hourOfDay().withMinimumValue()
        hMin = df.print(dateMin)
        assertThat(hMin).isEqualTo("2024-02-02 00:34:56")
        dateMax = date.hourOfDay().withMaximumValue()
        hMax = df.print(dateMax)
        assertThat(hMax).isEqualTo("2024-02-02 23:34:56")

        dateMin = date.dayOfMonth().withMinimumValue()
        hMin = df.print(dateMin)
        assertThat(hMin).isEqualTo("2024-02-01 12:34:56")
        dateMax = date.dayOfMonth().withMaximumValue()
        hMax = df.print(dateMax)
        assertThat(hMax).isEqualTo("2024-02-29 12:34:56")

        // Monday is the first day of week.
        dateMin = date.dayOfWeek().withMinimumValue()
        hMin = df.print(dateMin)
        assertThat(hMin).isEqualTo("2024-01-29 12:34:56")
        // Sunday is the last day of the week.
        dateMax = date.dayOfWeek().withMaximumValue()
        hMax = df.print(dateMax)
        assertThat(hMax).isEqualTo("2024-02-04 12:34:56")
        // Sunday is the last day of the week.
        dateMax = date.dayOfMonth().withMaximumValue().dayOfWeek().withMaximumValue()
        hMax = df.print(dateMax)
        assertThat(hMax).isEqualTo("2024-03-03 12:34:56")

        dateMin = date.monthOfYear().withMinimumValue()
        hMin = df.print(dateMin)
        assertThat(hMin).isEqualTo("2024-01-02 12:34:56")
        dateMax = date.monthOfYear().withMaximumValue()
        hMax = df.print(dateMax)
        assertThat(hMax).isEqualTo("2024-12-02 12:34:56")
    }

    @Test
    fun budget_CurrentPeriod() {
        val start = DateTime(2024, 7, 18, 12, 0)
        assertThat(start.weekOfMonth()).isEqualTo(3)
        assertThat(start.dayOfWeek).isEqualTo(DateTimeConstants.THURSDAY)

        val recurrence = Recurrence(PeriodType.ONCE)
        recurrence.periodStart = start.millis
        var s = recurrence.getTextOfCurrentPeriod(1)
        assertThat(s).isEqualTo("Now")
        s = recurrence.getTextOfCurrentPeriod(2)
        assertThat(s).isEqualTo("Now")

        recurrence.periodType = PeriodType.HOUR
        s = recurrence.getTextOfCurrentPeriod(1)
        assertThat(s).isEqualTo("12")
        s = recurrence.getTextOfCurrentPeriod(2)
        assertThat(s).isEqualTo("13")

        recurrence.periodType = PeriodType.DAY
        s = recurrence.getTextOfCurrentPeriod(1)
        assertThat(s).isEqualTo("Thursday")
        s = recurrence.getTextOfCurrentPeriod(2)
        assertThat(s).isEqualTo("Friday")

        recurrence.periodType = PeriodType.WEEK
        s = recurrence.getTextOfCurrentPeriod(1)
        assertThat(s).isEqualTo("29")
        s = recurrence.getTextOfCurrentPeriod(2)
        assertThat(s).isEqualTo("30")

        recurrence.periodType = PeriodType.MONTH
        s = recurrence.getTextOfCurrentPeriod(1)
        assertThat(s).isEqualTo("July")
        s = recurrence.getTextOfCurrentPeriod(2)
        assertThat(s).isEqualTo("August")

        recurrence.periodType = PeriodType.YEAR
        s = recurrence.getTextOfCurrentPeriod(1)
        assertThat(s).isEqualTo("2024")
        s = recurrence.getTextOfCurrentPeriod(2)
        assertThat(s).isEqualTo("2025")

        recurrence.periodType = PeriodType.END_OF_MONTH
        s = recurrence.getTextOfCurrentPeriod(1)
        assertThat(s).isEqualTo("31") // 31st of July
        s = recurrence.getTextOfCurrentPeriod(2)
        assertThat(s).isEqualTo("31") // 31st of August

        recurrence.periodType = PeriodType.LAST_WEEKDAY
        s = recurrence.getTextOfCurrentPeriod(1)
        assertThat(s).isEqualTo("25") // Last Thursday of July
        s = recurrence.getTextOfCurrentPeriod(2)
        assertThat(s).isEqualTo("29") // Last Thursday of August

        recurrence.periodType = PeriodType.NTH_WEEKDAY
        s = recurrence.getTextOfCurrentPeriod(1)
        assertThat(s).isEqualTo("3rd Thursday")
        s = recurrence.getTextOfCurrentPeriod(2)
        assertThat(s).isEqualTo("3rd Thursday")
    }

    @Test
    fun date() {
        val now = System.currentTimeMillis()
        val date = DateTime(now)
        assertThat(date.toDate().time).isEqualTo(now)
        assertThat(date.toDateTime().millis).isEqualTo(now)
    }

    @Test
    fun nth_weekday() {
        // 2024, July, 18th, Thursday.
        val date = DateTime(2024, 7, 18, 0, 0)
        assertThat(date.dayOfMonth).isEqualTo(18)
        assertThat(date.dayOfWeek).isEqualTo(DateTimeConstants.THURSDAY)
        assertThat(date.weekOfMonth()).isEqualTo(3)
        val firstDayOfMonth = date.dayOfMonth().withMinimumValue()

        val weeksDiff = Weeks.weeksBetween(firstDayOfMonth, date)
        // 18th of July is the 3rd week.
        assertThat(weeksDiff.weeks).isEqualTo(2)

        // Monday is the first day of the week.
        val firstWeekday = firstDayOfMonth.dayOfWeek().withMinimumValue()
        assertThat(firstWeekday.dayOfWeek).isEqualTo(DateTimeConstants.MONDAY)
        val week1 = firstWeekday.plusWeeks(0)
        assertThat(week1.dayOfWeek).isEqualTo(DateTimeConstants.MONDAY)
        assertThat(week1.dayOfMonth).isOne()
        val week2 = firstWeekday.plusWeeks(1)
        assertThat(week2.dayOfWeek).isEqualTo(DateTimeConstants.MONDAY)
        assertThat(week2.dayOfMonth).isEqualTo(8)
        val week3 = firstWeekday.plusWeeks(2)
        assertThat(week3.dayOfWeek).isEqualTo(DateTimeConstants.MONDAY)
        assertThat(week3.dayOfMonth).isEqualTo(15)
        val week4 = firstWeekday.plusWeeks(3)
        assertThat(week4.dayOfWeek).isEqualTo(DateTimeConstants.MONDAY)
        assertThat(week4.dayOfMonth).isEqualTo(22)
        val week5 = firstWeekday.plusWeeks(4)
        assertThat(week5.dayOfWeek).isEqualTo(DateTimeConstants.MONDAY)
        assertThat(week5.dayOfMonth).isEqualTo(29)

        assertThat(date.dayOfWeek(1).dayOfMonth).isEqualTo(4)
        assertThat(date.dayOfWeek(1).dayOfWeek).isEqualTo(DateTimeConstants.THURSDAY)
        assertThat(date.dayOfWeek(2).dayOfMonth).isEqualTo(11)
        assertThat(date.dayOfWeek(2).dayOfWeek).isEqualTo(DateTimeConstants.THURSDAY)
        assertThat(date.dayOfWeek(3).dayOfMonth).isEqualTo(18)
        assertThat(date.dayOfWeek(3).dayOfWeek).isEqualTo(DateTimeConstants.THURSDAY)
        assertThat(date.dayOfWeek(4).dayOfMonth).isEqualTo(25)
        assertThat(date.dayOfWeek(4).dayOfWeek).isEqualTo(DateTimeConstants.THURSDAY)
        assertThat(date.dayOfWeek(5).dayOfMonth).isOne()
        assertThat(date.dayOfWeek(5).dayOfWeek).isEqualTo(DateTimeConstants.THURSDAY)

        val nextMonth = date.plusMonths(1)
        assertThat(nextMonth.dayOfMonth).isEqualTo(18)
        assertThat(nextMonth.dayOfWeek).isEqualTo(DateTimeConstants.SUNDAY)
        val nextMonthSameWeekday = nextMonth.dayOfWeek(date)
        assertThat(nextMonthSameWeekday.dayOfMonth).isEqualTo(15)
        assertThat(nextMonthSameWeekday.dayOfWeek).isEqualTo(DateTimeConstants.THURSDAY)
        assertThat(nextMonthSameWeekday.dayOfWeek(1).dayOfMonth).isOne()
        assertThat(nextMonthSameWeekday.dayOfWeek(1).dayOfWeek).isEqualTo(DateTimeConstants.THURSDAY)
        assertThat(nextMonthSameWeekday.dayOfWeek(2).dayOfMonth).isEqualTo(8)
        assertThat(nextMonthSameWeekday.dayOfWeek(2).dayOfWeek).isEqualTo(DateTimeConstants.THURSDAY)
        assertThat(nextMonthSameWeekday.dayOfWeek(3).dayOfMonth).isEqualTo(15)
        assertThat(nextMonthSameWeekday.dayOfWeek(3).dayOfWeek).isEqualTo(DateTimeConstants.THURSDAY)
        assertThat(nextMonthSameWeekday.dayOfWeek(4).dayOfMonth).isEqualTo(22)
        assertThat(nextMonthSameWeekday.dayOfWeek(4).dayOfWeek).isEqualTo(DateTimeConstants.THURSDAY)
        assertThat(nextMonthSameWeekday.dayOfWeek(5).dayOfMonth).isEqualTo(29)
        assertThat(nextMonthSameWeekday.dayOfWeek(5).dayOfWeek).isEqualTo(DateTimeConstants.THURSDAY)
    }

    @Test
    fun reoccurs_every_4th_wednesday() {
        val recurrence = Recurrence(PeriodType.ONCE)
        assertThat(recurrence.ruleString).isEqualTo("FREQ=;INTERVAL=1")
        assertThat(recurrence.periodType).isEqualTo(PeriodType.ONCE)
        assertThat(recurrence.multiplier).isOne()
        assertThat(recurrence.byDays).isEmpty()

        // Monthly; week starts on Sunday; on 4th Wednesday of the month.
        val rule = "FREQ=MONTHLY;WKST=SU;BYDAY=4WE"
        recurrence.ruleString = rule
        assertThat(recurrence.ruleString).isEqualTo(rule)
        assertThat(recurrence.periodType).isEqualTo(PeriodType.MONTH)
        assertThat(recurrence.multiplier).isOne()
        assertThat(recurrence.byDays).isEqualTo(listOf(Calendar.WEDNESDAY))
    }
}
