/*
 * Copyright (c) 2016 Alceu Rodrigues Neto <alceurneto@gmail.com>
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
package org.gnucash.android.util

import org.joda.time.format.DateTimeFormat
import org.joda.time.format.DateTimeFormatter
import java.sql.Timestamp

/**
 * A utility class to deal with [Timestamp] operations in a centralized way.
 */
object TimestampHelper {
    /**
     * @return A [Timestamp] with time in milliseconds equals to zero.
     */
    val timestampFromEpochZero: Timestamp = Timestamp(0)

    /**
     * We are using Joda Time classes because they are thread-safe.
     */
    private val UTC_DATE_FORMAT: DateTimeFormatter =
        DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss").withZoneUTC()

    private val UTC_DATE_WITH_MILLISECONDS_FORMAT: DateTimeFormatter =
        DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss.SSS").withZoneUTC()

    /**
     * Get a [String] representing the [Timestamp]
     * in UTC time zone and 'yyyy-MM-dd HH:mm:ss.SSS' format.
     *
     * @param timestamp The [Timestamp] to format.
     * @return The formatted [String].
     */
    fun getUtcStringFromTimestamp(timestamp: Timestamp?): String {
        return getUtcStringFromTimestamp((timestamp ?: timestampFromEpochZero).time)
    }

    /**
     * Get a [String] representing the [Timestamp]
     * in UTC time zone and 'yyyy-MM-dd HH:mm:ss.SSS' format.
     *
     * @param timestamp The timestamp to format.
     * @return The formatted [String].
     */
    fun getUtcStringFromTimestamp(timestamp: Long): String {
        return UTC_DATE_WITH_MILLISECONDS_FORMAT.print(timestamp)
    }

    /**
     * Get the [Timestamp] with the value of given UTC [String].
     * The [String] should be a representation in UTC time zone with the following format
     * 'yyyy-MM-dd HH:mm:ss.SSS' OR 'yyyy-MM-dd HH:mm:ss' otherwise an IllegalArgumentException
     * will be throw.
     *
     * @param utcString A [String] in UTC.
     * @return A [Timestamp] for given utcString.
     */
    @Throws(IllegalArgumentException::class)
    fun getTimestampFromUtcString(utcString: String): Timestamp {
        // NB: `Timestamp.valueOf(utcString)` uses current time zone, and *not* UTC.
        var millis: Long
        try {
            millis = UTC_DATE_WITH_MILLISECONDS_FORMAT.parseMillis(utcString)
            return Timestamp(millis)
        } catch (firstException: IllegalArgumentException) {
            try {
                // In case of parsing of string without milliseconds.
                millis = UTC_DATE_FORMAT.parseMillis(utcString)
                return Timestamp(millis)
            } catch (secondException: IllegalArgumentException) {
                // If we are here:
                // - The utcString has an invalid format OR
                // - We are missing some relevant pattern.
                throw IllegalArgumentException("Unknown UTC format '$utcString'", secondException)
            }
        }
    }

    /**
     * @return A [Timestamp] initialized with the system current time.
     */
    val timestampFromNow: Timestamp
        get() = Timestamp(System.currentTimeMillis())
}