/*
 * Copyright (c) 2015 Alceu Rodrigues Neto <alceurneto@gmail.com>
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

import android.content.Context;

import org.gnucash.android.app.GnuCashApplication;

import java.sql.Timestamp;

import timber.log.Timber;

/**
 * A utility class to deal with Android Preferences in a centralized way.
 */
public final class PreferencesHelper {

    /**
     * Should be not instantiated.
     */
    private PreferencesHelper() {
    }

    /**
     * Preference key for saving the last export time
     */
    public static final String PREFERENCE_LAST_EXPORT_TIME_KEY = "last_export_time";

    /**
     * Set the last export time in UTC time zone of the currently active Book in the application.
     * This method calls through to {@link #setLastExportTime(Timestamp, String)}
     *
     * @param lastExportTime the last export time to set.
     * @see #setLastExportTime(Timestamp, String)
     */
    public static void setLastExportTime(Timestamp lastExportTime) {
        Timber.v("Saving last export time for the currently active book");
        setLastExportTime(lastExportTime, GnuCashApplication.getActiveBookUID());
    }

    /**
     * Set the last export time in UTC time zone for a specific book.
     * This value will be used during export to determine new transactions since the last export
     *
     * @param lastExportTime the last export time to set.
     */
    public static void setLastExportTime(Timestamp lastExportTime, String bookUID) {
        final String utcString = TimestampHelper.getUtcStringFromTimestamp(lastExportTime);
        Timber.d("Storing '" + utcString + "' as lastExportTime in Android Preferences.");
        GnuCashApplication.getAppContext().getSharedPreferences(bookUID, Context.MODE_PRIVATE)
            .edit()
            .putString(PREFERENCE_LAST_EXPORT_TIME_KEY, utcString)
            .apply();
    }

    /**
     * Get the time for the last export operation.
     *
     * @return A {@link Timestamp} with the time.
     */
    public static Timestamp getLastExportTime(Context context) {
        final String utcString = GnuCashApplication.getBookPreferences(context)
            .getString(PREFERENCE_LAST_EXPORT_TIME_KEY,
                TimestampHelper.getUtcStringFromTimestamp(TimestampHelper.getTimestampFromEpochZero()));
        Timber.d("Retrieving '" + utcString + "' as lastExportTime from Android Preferences.");
        return TimestampHelper.getTimestampFromUtcString(utcString);
    }

    /**
     * Get the time for the last export operation of a specific book.
     *
     * @return A {@link Timestamp} with the time.
     */
    public static Timestamp getLastExportTime(Context context, String bookUID) {
        final String utcString = GnuCashApplication.getBookPreferences(context, bookUID)
            .getString(PREFERENCE_LAST_EXPORT_TIME_KEY,
                TimestampHelper.getUtcStringFromTimestamp(
                    TimestampHelper.getTimestampFromEpochZero()));
        Timber.d("Retrieving '" + utcString + "' as lastExportTime from Android Preferences.");
        return TimestampHelper.getTimestampFromUtcString(utcString);
    }
}