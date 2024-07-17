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

/**
 * Represents a type of period which can be associated with a recurring event
 *
 * @author Ngewi Fet <ngewif@gmail.com>
 * @see org.gnucash.android.model.ScheduledAction
 */
enum class PeriodType(@JvmField val value: String) {
    /* Not a true period at all, but convenient here. */
    ONCE("once"),
    HOUR("hour"),
    DAY("day"),
    /* Also a phase. */
    LAST_WEEKDAY("last weekday"),
    /* Also a phase, e.g. Second Tuesday. */
    NTH_WEEKDAY("nth weekday"),
    WEEK("week"),
    MONTH("month"),
    /* This is actually a period plus a phase. */
    END_OF_MONTH("end of month"),
    YEAR("year");

    companion object {
        @JvmStatic
        fun of(value: String): PeriodType {
            val valueLower = value.lowercase(Locale.ROOT)
            return values().firstOrNull { it.value == valueLower } ?: ONCE
        }
    }
}