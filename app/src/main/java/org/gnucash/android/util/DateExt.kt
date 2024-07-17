package org.gnucash.android.util

import org.joda.time.DateTime
import org.joda.time.LocalDateTime
import org.joda.time.Weeks

/**
 * Get the last weekday of the month.
 */
fun DateTime.lastDayOfWeek() = dayOfMonth().withMaximumValue().dayOfWeek().withMaximumValue()

/**
 * Get the last day of the month.
 */
fun DateTime.lastDayOfMonth() = dayOfMonth().withMaximumValue()

/**
 * Get the `n`th week of the month.
 */
fun DateTime.weekOfMonth(): Int {
    val firstDayOfMonth = dayOfMonth().withMinimumValue()
    val weeksDiff = Weeks.weeksBetween(firstDayOfMonth, this)
    return 1 + weeksDiff.weeks
}

/**
 * Get the `n`th weekday of the month.
 */
fun DateTime.dayOfWeek(/*@Range(1..5)*/ n: Int): DateTime {
    val dayOfWeekOld = dayOfWeek
    val firstDayOfMonth = dayOfMonth().withMinimumValue()
    val firstDayOfWeek = firstDayOfMonth.dayOfWeek().setCopy(dayOfWeekOld)
    return firstDayOfWeek.plusWeeks(n - 1)
}

/**
 * Get the `n`th weekday of the month.
 * @param date the date with the original day-of-week.
 */
fun DateTime.dayOfWeek(date: DateTime): DateTime {
    val dayOfWeekOriginal = date.dayOfWeek
    val weekOfMonth = date.weekOfMonth()
    val firstDayOfMonth = dayOfMonth().withMinimumValue()
    val firstDayOfWeek = firstDayOfMonth.dayOfWeek().setCopy(dayOfWeekOriginal)
    return firstDayOfWeek.plusWeeks(weekOfMonth - 1)
}

/**
 * Get the last weekday of the month.
 */
fun LocalDateTime.lastDayOfWeek() = dayOfMonth().withMaximumValue().dayOfWeek().withMaximumValue()

/**
 * Get the last weekday of the month.
 * @param date the date with the original day-of-week.
 */
fun LocalDateTime.lastDayOfWeek(date: LocalDateTime): LocalDateTime {
    val dayOfWeekOriginal = date.dayOfWeek
    val lastDayOfMonth = dayOfMonth().withMaximumValue()
    var lastWeekday = lastDayOfMonth.dayOfWeek().setCopy(dayOfWeekOriginal)
    if (lastWeekday > lastDayOfMonth) {
        lastWeekday = lastWeekday.minusWeeks(1)
    }
    return lastWeekday
}

/**
 * Get the last day of the month.
 */
fun LocalDateTime.lastDayOfMonth() = dayOfMonth().withMaximumValue()

/**
 * Get the `n`th week of the month.
 */
fun LocalDateTime.weekOfMonth(): Int {
    val firstDayOfMonth = dayOfMonth().withMinimumValue()
    val weeksDiff = Weeks.weeksBetween(firstDayOfMonth, this)
    return 1 + weeksDiff.weeks
}

/**
 * Get the `n`th weekday of the month.
 * @param date the date with the original day-of-week.
 */
fun LocalDateTime.dayOfWeek(date: LocalDateTime): LocalDateTime {
    val dayOfWeekOriginal = date.dayOfWeek
    val weekOfMonth = date.weekOfMonth()
    val firstDayOfMonth = dayOfMonth().withMinimumValue()
    val firstDayOfWeek = firstDayOfMonth.dayOfWeek().setCopy(dayOfWeekOriginal)
    return firstDayOfWeek.plusWeeks(weekOfMonth - 1)
}
