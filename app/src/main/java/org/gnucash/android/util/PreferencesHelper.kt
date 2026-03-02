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
package org.gnucash.android.util

import android.content.Context
import androidx.core.content.edit
import org.gnucash.android.app.GnuCashApplication
import org.gnucash.android.app.GnuCashApplication.Companion.activeBookUID
import org.gnucash.android.util.TimestampHelper.getUtcStringFromTimestamp
import timber.log.Timber
import java.sql.Timestamp

/**
 * A utility class to deal with Android Preferences in a centralized way.
 */
object PreferencesHelper {
    /**
     * Preference key for saving the last export time
     */
    private const val KEY_LAST_EXPORT_TIME: String = "last_export_time"

    /**
     * Set the last export time in UTC time zone of the currently active Book in the application.
     * This method calls through to [.setLastExportTime]
     *
     * @param lastExportTime the last export time to set.
     * @see .setLastExportTime
     */
    fun setLastExportTime(context: Context, lastExportTime: Timestamp) {
        Timber.v("Saving last export time for the currently active book")
        setLastExportTime(context, lastExportTime, activeBookUID!!)
    }

    /**
     * Set the last export time in UTC time zone for a specific book.
     * This value will be used during export to determine new transactions since the last export
     *
     * @param lastExportTime the last export time to set.
     */
    fun setLastExportTime(context: Context, lastExportTime: Timestamp, bookUID: String) {
        val utcString = getUtcStringFromTimestamp(lastExportTime)
        Timber.d("Storing '%s' as lastExportTime in Android Preferences.", utcString)
        val prefs = GnuCashApplication.getBookPreferences(context, bookUID)
        prefs.edit {
            putString(KEY_LAST_EXPORT_TIME, utcString)
        }
    }

    /**
     * Get the time for the last export operation.
     *
     * @return A [Timestamp] with the time.
     */
    fun getLastExportTime(context: Context): Timestamp {
        return getLastExportTime(context, activeBookUID!!)
    }

    /**
     * Get the time for the last export operation of a specific book.
     *
     * @return A [Timestamp] with the time.
     */
    fun getLastExportTime(context: Context, bookUID: String): Timestamp {
        val prefs = GnuCashApplication.getBookPreferences(context, bookUID)
        val utcString = prefs.getString(KEY_LAST_EXPORT_TIME, null)
            ?: return TimestampHelper.timestampFromEpochZero
        Timber.d("Retrieving '%s' as lastExportTime from Android Preferences.", utcString)
        return TimestampHelper.getTimestampFromUtcString(utcString)
    }
}