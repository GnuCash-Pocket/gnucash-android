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

import android.net.Uri
import java.sql.Timestamp

/**
 * Represents a GnuCash book which is made up of accounts and transactions
 *
 * @author Ngewi Fet <ngewif@gmail.com>
 */
class Book(rootAccountUID: String? = null) : BaseModel() {
    /**
     * The Uri of the GnuCash XML source for the book
     *
     * In API level 16 and above, this is the Uri from the storage access framework which will
     * be used for synchronization of the book
     *
     * This Uri will be used for sync where applicable
     */
    var sourceUri: Uri? = null

    /**
     * Name of the book.
     *
     * This is the user readable string which is used in UI unlike the root account GUID which
     * is used for uniquely identifying each book
     *
     * @return Name of the book
     */
    var displayName: String? = null

    /**
     * The root account GUID of this book
     *
     * Each book has only one root account
     *
     * @param rootAccountUID GUID of the book root account
     */
    var rootAccountUID: String = rootAccountUID ?: generateUID()

    /**
     * The GUID of the root template account
     */
    var rootTemplateUID: String? = generateUID()

    /**
     * `true` if this book is the currently active book in the app, `false` otherwise.
     *
     * An active book is one whose data is currently displayed in the UI
     */
    var isActive = false

    /**
     * The time of last synchronization of the book
     *
     * @param lastSync Timestamp of last synchronization
     */
    var lastSync: Timestamp = Timestamp(System.currentTimeMillis())

    override fun toString(): String {
        return displayName ?: super.toString()
    }

    override fun equals(other: Any?): Boolean {
        if (other is Book) {
            if (this.displayName != other.displayName) return false
            if (this.rootAccountUID != other.rootAccountUID) return false
            if (this.isActive != other.isActive) return false
            if (this.lastSync != other.lastSync) return false
        }
        return super.equals(other)
    }
}
