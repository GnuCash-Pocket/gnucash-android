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

import timber.log.Timber

/**
 * Logger tree for Timber.
 *
 * @author Moshe Waisberg
 */
open class LogTree(private val debug: Boolean) : Timber.DebugTree() {

    override fun isLoggable(tag: String?, priority: Int): Boolean {
        return (debug || priority >= Log.INFO) && super.isLoggable(tag, priority)
    }
}
