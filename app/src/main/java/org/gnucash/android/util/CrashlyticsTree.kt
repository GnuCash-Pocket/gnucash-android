/*
 * Copyright (c) 2024, GnuCash-Pocket
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

import android.util.Log
import com.google.firebase.crashlytics.FirebaseCrashlytics

/**
 * Crashlytics logger tree for Timber.
 *
 * @author Moshe Waisberg
 */
class CrashlyticsTree(debug: Boolean) : LogTree(debug) {

    private val crashlytics: FirebaseCrashlytics = FirebaseCrashlytics.getInstance().apply {
        setCrashlyticsCollectionEnabled(!debug)
    }

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        super.log(priority, tag, message, t)
        val logMessage = priorityChar[priority] + "/" + tag + ": " + message
        crashlytics.log(logMessage)
        if (t != null) {
            crashlytics.recordException(t)
        }
    }

    companion object {
        private val priorityChar = mutableMapOf<Int, String>()

        init {
            priorityChar[Log.ASSERT] = "A"
            priorityChar[Log.ERROR] = "E"
            priorityChar[Log.DEBUG] = "D"
            priorityChar[Log.INFO] = "I"
            priorityChar[Log.VERBOSE] = "V"
            priorityChar[Log.WARN] = "W"
        }
    }
}