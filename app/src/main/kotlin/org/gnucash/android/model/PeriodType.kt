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

import java.util.Locale
import org.joda.time.format.DateTimeFormat

/**
 * Represents a type of period which can be associated with a recurring event
 *
 * @author Ngewi Fet <ngewif@gmail.com>
 * @see org.gnucash.android.model.ScheduledAction
 */
enum class PeriodType {
    HOUR, DAY, WEEK, MONTH, YEAR;

    /**
     * Returns the frequency description of this period type.
     * This is used mostly for generating the recurrence rule.
     *
     * @return Frequency description
     */
    val frequencyDescription: String
        get() = when (this) {
            HOUR -> "HOURLY"
            DAY -> "DAILY"
            WEEK -> "WEEKLY"
            MONTH -> "MONTHLY"
            YEAR -> "YEARLY"
            else -> ""
        }

    /**
     * Returns the parts of the recurrence rule which describe the day or month on which to run the
     * scheduled transaction. These parts are the BYxxx
     *
     * @param startTime Start time of transaction used to determine the start day of execution
     * @return String describing the BYxxx parts of the recurrence rule
     */
    fun getByParts(startTime: Long): String {
        var partString = ""
        if (this == WEEK) {
            val dayOfWeek = dayOfWeekFormatter.print(startTime)
            //our parser only supports two-letter day names
            partString = "BYDAY=" + dayOfWeek.substring(0, dayOfWeek.length - 1)
                .uppercase(Locale.getDefault())
        }
        return partString
    }

    companion object {
        private val dayOfWeekFormatter = DateTimeFormat.forPattern("E")
    }
}