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
package org.gnucash.android.db.adapter

import android.database.Cursor
import android.database.sqlite.SQLiteStatement
import org.gnucash.android.app.GnuCashApplication
import org.gnucash.android.db.DatabaseHolder
import org.gnucash.android.db.DatabaseSchema.RecurrenceEntry
import org.gnucash.android.db.bindInt
import org.gnucash.android.db.getInt
import org.gnucash.android.db.getString
import org.gnucash.android.model.PeriodType
import org.gnucash.android.model.Recurrence
import org.gnucash.android.util.TimestampHelper.getTimestampFromUtcString
import org.gnucash.android.util.TimestampHelper.getUtcStringFromTimestamp
import java.util.Calendar

/**
 * Database adapter for [Recurrence] entries
 */
class RecurrenceDbAdapter(holder: DatabaseHolder) : DatabaseAdapter<Recurrence>(
    holder,
    RecurrenceEntry.TABLE_NAME,
    arrayOf(
        RecurrenceEntry.COLUMN_MULTIPLIER,
        RecurrenceEntry.COLUMN_PERIOD_TYPE,
        RecurrenceEntry.COLUMN_BYDAY,
        RecurrenceEntry.COLUMN_PERIOD_START,
        RecurrenceEntry.COLUMN_PERIOD_END
    )
) {
    override fun buildModelInstance(cursor: Cursor): Recurrence {
        val type = cursor.getString(RecurrenceEntry.COLUMN_PERIOD_TYPE)!!
        val multiplier = cursor.getInt(RecurrenceEntry.COLUMN_MULTIPLIER)
        val periodStart = cursor.getString(RecurrenceEntry.COLUMN_PERIOD_START)!!
        val periodEnd = cursor.getString(RecurrenceEntry.COLUMN_PERIOD_END)
        val byDays = cursor.getString(RecurrenceEntry.COLUMN_BYDAY)

        val periodType = PeriodType.valueOf(type)
        val recurrence = Recurrence(periodType)
        populateBaseModelAttributes(cursor, recurrence)
        recurrence.multiplier = multiplier
        recurrence.periodStart = getTimestampFromUtcString(periodStart).time
        if (periodEnd != null) {
            recurrence.periodEnd = getTimestampFromUtcString(periodEnd).time
        }
        recurrence.byDays = stringToByDays(byDays)

        return recurrence
    }

    override fun bind(stmt: SQLiteStatement, recurrence: Recurrence): SQLiteStatement {
        bindBaseModel(stmt, recurrence)
        stmt.bindInt(1, recurrence.multiplier)
        stmt.bindString(2, recurrence.periodType.name)
        if (!recurrence.byDays.isEmpty()) {
            stmt.bindString(3, byDaysToString(recurrence.byDays))
        }
        //recurrence should always have a start date
        stmt.bindString(4, getUtcStringFromTimestamp(recurrence.periodStart))
        if (recurrence.periodEnd != null) {
            stmt.bindString(5, getUtcStringFromTimestamp(recurrence.periodEnd!!))
        }

        return stmt
    }

    companion object {
        val instance: RecurrenceDbAdapter get() = GnuCashApplication.recurrenceDbAdapter!!

        /**
         * Converts a list of days of week as Calendar constants to an String for
         * storing in the database.
         *
         * @param byDays list of days of week constants from Calendar
         * @return String of days of the week or null if `byDays` was empty
         */
        private fun byDaysToString(byDays: List<Int>): String {
            val builder = StringBuilder()
            for (day in byDays) {
                when (day) {
                    Calendar.MONDAY -> builder.append("MO")
                    Calendar.TUESDAY -> builder.append("TU")
                    Calendar.WEDNESDAY -> builder.append("WE")
                    Calendar.THURSDAY -> builder.append("TH")
                    Calendar.FRIDAY -> builder.append("FR")
                    Calendar.SATURDAY -> builder.append("SA")
                    Calendar.SUNDAY -> builder.append("SU")
                    else -> throw RuntimeException("bad day of week: $day")
                }
                builder.append(",")
            }
            builder.deleteCharAt(builder.length - 1)
            return builder.toString()
        }

        /**
         * Converts a String with the comma-separated days of the week into a
         * list of Calendar constants.
         *
         * @param byDaysString String with comma-separated days fo the week
         * @return list of days of the week as Calendar constants.
         */
        private fun stringToByDays(byDaysString: String?): List<Int> {
            if (byDaysString.isNullOrEmpty()) return emptyList<Int>()

            val byDaysList = mutableListOf<Int>()
            for (day in byDaysString.split(",".toRegex()).dropLastWhile { it.isEmpty() }
                .toTypedArray()) {
                when (day) {
                    "MO" -> byDaysList.add(Calendar.MONDAY)
                    "TU" -> byDaysList.add(Calendar.TUESDAY)
                    "WE" -> byDaysList.add(Calendar.WEDNESDAY)
                    "TH" -> byDaysList.add(Calendar.THURSDAY)
                    "FR" -> byDaysList.add(Calendar.FRIDAY)
                    "SA" -> byDaysList.add(Calendar.SATURDAY)
                    "SU" -> byDaysList.add(Calendar.SUNDAY)
                }
            }
            return byDaysList
        }
    }
}
