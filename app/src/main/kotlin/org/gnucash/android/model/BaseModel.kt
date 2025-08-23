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

import org.gnucash.android.util.TimestampHelper
import java.sql.Timestamp
import java.util.UUID

/**
 * Abstract class representing the base data model which is persisted to the database.
 * All other models should extend this base model.
 */
abstract class BaseModel {
    /** Database record id. */
    var id: Long = 0

    /**
     * Unique identifier of this model instance.
     *
     * It is declared private because it is generated only on-demand.
     * Sub-classes should use the accessor methods to read and write this value
     *
     * @see getUID()
     * @see setUID(String)
     */
    private var _uid: String? = null

    /**
     * The timestamp when this model entry was created in the database.
     */
    var createdTimestamp: Timestamp = TimestampHelper.timestampFromNow

    /**
     * The timestamp when the model was last modified in the database
     *
     * Although the database automatically has triggers for entering the timestamp,
     * when SQL INSERT OR REPLACE syntax is used, it is possible to override the modified timestamp.
     * <br />In that case, it has to be explicitly set in the SQL statement.
     *
     */
    var modifiedTimestamp: Timestamp = TimestampHelper.timestampFromNow

    /**
     * A unique string identifier for this model instance.
     */
    val uid: String
        get() {
            var value = _uid
            if (value == null) {
                value = generateUID()
                _uid = value
            }
            return value
        }

    open fun setUID(uid: String?) {
        _uid = uid
    }

    /**
     * Two instances are considered equal if their GUID's are the same
     *
     * @param other BaseModel instance to compare
     * @return `true` if both instances are equal, `false` otherwise
     */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BaseModel) return false
        return uid == other.uid
    }

    override fun hashCode(): Int {
        return uid.hashCode()
    }

    companion object {
        private val regexDash = "-".toRegex()

        /**
         * Method for generating the Global Unique ID for the model object
         *
         * @return Random GUID for the model object
         */
        fun generateUID(): String {
            return UUID.randomUUID().toString().replace(regexDash, "")
        }
    }
}
