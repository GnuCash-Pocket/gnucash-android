package org.gnucash.android.util

import androidx.annotation.IntRange
import org.joda.time.DateTime
import org.joda.time.DateTimeConstants
import org.joda.time.DateTimeUtils
import org.joda.time.DateTimeZone
import org.joda.time.LocalDate
import org.joda.time.LocalDateTime
import org.joda.time.Weeks
import org.joda.time.format.DateTimeFormat
import java.text.DateFormat
import java.util.Calendar
import java.util.Date

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

fun formatFullDate(millis: Long): String {
    return try {
        DateTimeFormat.fullDate().print(millis)
    } catch (e: IllegalArgumentException) {
        DateFormat.getDateInstance(DateFormat.FULL).format(Date(millis))
    }
}

fun formatFullDateTime(millis: Long): String {
    return try {
        DateTimeFormat.fullDateTime().print(millis)
    } catch (e: IllegalArgumentException) {
        DateFormat.getDateInstance(DateFormat.FULL).format(Date(millis))
    }
}

fun formatLongDate(millis: Long): String {
    return try {
        DateTimeFormat.longDate().print(millis)
    } catch (e: IllegalArgumentException) {
        DateFormat.getDateInstance(DateFormat.LONG).format(Date(millis))
    }
}

fun formatLongDateTime(millis: Long): String {
    return try {
        DateTimeFormat.longDateTime().print(millis)
    } catch (e: IllegalArgumentException) {
        DateFormat.getDateInstance(DateFormat.LONG).format(Date(millis))
    }
}

fun formatMediumDate(millis: Long): String {
    return try {
        DateTimeFormat.mediumDate().print(millis)
    } catch (e: IllegalArgumentException) {
        DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(millis))
    }
}

fun formatMediumDateTime(millis: Long): String {
    return try {
        DateTimeFormat.mediumDateTime().print(millis)
    } catch (e: IllegalArgumentException) {
        DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(millis))
    }
}

fun formatShortDate(millis: Long): String {
    return try {
        DateTimeFormat.shortDate().print(millis)
    } catch (e: IllegalArgumentException) {
        DateFormat.getDateInstance(DateFormat.SHORT).format(Date(millis))
    }
}

fun formatShortDateTime(millis: Long): String {
    return try {
        DateTimeFormat.shortDateTime().print(millis)
    } catch (e: IllegalArgumentException) {
        DateFormat.getDateInstance(DateFormat.SHORT).format(Date(millis))
    }
}

private val quarters = intArrayOf(1, 1, 1, 2, 2, 2, 3, 3, 3, 4, 4, 4)

/**
 * Returns a quarter of the specified date.
 * January, February, March = 1
 * April, May, June = 2
 * July, August, September = 3
 * October, November, December = 4
 *
 * @return a quarter
 */
@IntRange(from = 1, to = 4)
fun LocalDateTime.getQuarter(): Int {
    return quarters[monthOfYear - 1]
}

@IntRange(from = 1, to = 12)
fun LocalDateTime.getFirstQuarterMonth(): Int {
    val m = monthOfYear

    return when (m) {
        DateTimeConstants.FEBRUARY -> DateTimeConstants.JANUARY
        DateTimeConstants.MARCH -> DateTimeConstants.JANUARY
        DateTimeConstants.MAY -> DateTimeConstants.APRIL
        DateTimeConstants.JUNE -> DateTimeConstants.APRIL
        DateTimeConstants.AUGUST -> DateTimeConstants.JULY
        DateTimeConstants.SEPTEMBER -> DateTimeConstants.JULY
        DateTimeConstants.NOVEMBER -> DateTimeConstants.OCTOBER
        DateTimeConstants.DECEMBER -> DateTimeConstants.OCTOBER
        else -> m
    }
}

private const val NEVER = Long.MIN_VALUE

fun LocalDateTime?.toMillis(): Long {
    return this?.toDateTime()?.millis ?: NEVER
}

fun LocalDate?.toMillis(): Long {
    return this?.toDate()?.time ?: NEVER
}

fun DateTime?.toMillis(): Long {
    return this?.toDate()?.time ?: NEVER
}

fun Long?.toLocalDateTime(): LocalDateTime? {
    return if (this == NEVER) null else LocalDateTime(this)
}

fun Long?.toLocalDate(): LocalDate? {
    return if (this == NEVER) null else LocalDate(this)
}

fun LocalDate.toDateTimeAtEndOfDay(): DateTime {
    return toDateTimeAtEndOfDay(null)
}

fun LocalDate.toDateTimeAtEndOfDay(zone: DateTimeZone?): DateTime {
    val zone = DateTimeUtils.getZone(zone)!!
    val calendar = Calendar.getInstance(zone.toTimeZone()).apply {
        timeInMillis = toMillis()
        set(Calendar.HOUR_OF_DAY, getMaximum(Calendar.HOUR_OF_DAY))
        set(Calendar.MINUTE, getMaximum(Calendar.MINUTE))
        set(Calendar.SECOND, getMaximum(Calendar.SECOND))
        set(Calendar.MILLISECOND, getMaximum(Calendar.MILLISECOND))
    }
    return DateTime(calendar.timeInMillis, zone)
}
