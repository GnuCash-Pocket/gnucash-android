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
package org.gnucash.android.util;

import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import java.sql.Timestamp;

/**
 * A utility class to deal with {@link Timestamp} operations in a centralized way.
 */
public final class TimestampHelper {

    public static final Timestamp EPOCH_ZERO_TIMESTAMP = new Timestamp(0);

    /**
     * Should be not instantiated.
     */
    private TimestampHelper() {
    }

    /**
     * We are using Joda Time classes because they are thread-safe.
     */
    private static final DateTimeFormatter UTC_DATE_FORMAT =
        DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss").withZoneUTC();
    private static final DateTimeFormatter UTC_DATE_WITH_MILLISECONDS_FORMAT =
        DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss.SSS").withZoneUTC();

    /**
     * Get a {@link String} representing the {@link Timestamp}
     * in UTC time zone and 'yyyy-MM-dd HH:mm:ss.SSS' format.
     *
     * @param timestamp The {@link Timestamp} to format.
     * @return The formatted {@link String}.
     */
    public static String getUtcStringFromTimestamp(Timestamp timestamp) {
        return getUtcStringFromTimestamp(timestamp.getTime());
    }

    /**
     * Get a {@link String} representing the {@link Timestamp}
     * in UTC time zone and 'yyyy-MM-dd HH:mm:ss.SSS' format.
     *
     * @param timestamp The timestamp to format.
     * @return The formatted {@link String}.
     */
    public static String getUtcStringFromTimestamp(Long timestamp) {
        return UTC_DATE_WITH_MILLISECONDS_FORMAT.print(timestamp);
    }

    /**
     * @return A {@link Timestamp} with time in milliseconds equals to zero.
     */
    public static Timestamp getTimestampFromEpochZero() {
        return EPOCH_ZERO_TIMESTAMP;
    }

    /**
     * Get the {@link Timestamp} with the value of given UTC {@link String}.
     * The {@link String} should be a representation in UTC time zone with the following format
     * 'yyyy-MM-dd HH:mm:ss.SSS' OR 'yyyy-MM-dd HH:mm:ss' otherwise an IllegalArgumentException
     * will be throw.
     *
     * @param utcString A {@link String} in UTC.
     * @return A {@link Timestamp} for given utcString.
     */
    public static Timestamp getTimestampFromUtcString(String utcString) {
        // NB: `Timestamp.valueOf(utcString)` uses current time zone, and *not* UTC.
        long millis;
        try {

            millis = UTC_DATE_WITH_MILLISECONDS_FORMAT.parseMillis(utcString);
            return new Timestamp(millis);

        } catch (IllegalArgumentException firstException) {
            try {
                // In case of parsing of string without milliseconds.
                millis = UTC_DATE_FORMAT.parseMillis(utcString);
                return new Timestamp(millis);
            } catch (IllegalArgumentException secondException) {
                // If we are here:
                // - The utcString has an invalid format OR
                // - We are missing some relevant pattern.
                throw new IllegalArgumentException("Unknown UTC format '" + utcString + "'", secondException);
            }
        }
    }

    /**
     * @return A {@link Timestamp} initialized with the system current time.
     */
    public static Timestamp getTimestampFromNow() {
        return new Timestamp(System.currentTimeMillis());
    }
}