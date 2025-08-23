/*
 * Copyright (c) 2012 - 2014 Ngewi Fet <ngewif@gmail.com>
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
package org.gnucash.android.db

import android.content.Context
import android.database.ContentObserver
import android.database.Cursor
import androidx.loader.content.AsyncTaskLoader
import org.gnucash.android.db.adapter.DatabaseAdapter

/**
 * Abstract base class for asynchronously loads records from a database and manages the cursor.
 * In order to use this class, you must subclass it and implement the
 * [.loadInBackground] method to load the particular records from the database.
 * Ideally, the database has [DatabaseAdapter] which is used for managing access to the
 * records from the database
 *
 * @author Ngewi Fet <ngewif@gmail.com>
 * @see DatabaseAdapter
 */
abstract class DatabaseCursorLoader<DA : DatabaseAdapter<*>>(context: Context) :
    AsyncTaskLoader<Cursor?>(context) {
    /**
     * Cursor which will hold the loaded data set.
     * The cursor will be returned from the [.loadInBackground] method
     */
    private var cursor: Cursor? = null

    /**
     * [DatabaseAdapter] which will be used to load the records from the database
     */
    protected var databaseAdapter: DA? = null

    /**
     * A content observer which monitors the cursor and provides notifications when
     * the dataset backing the cursor changes. You need to register the oberserver on
     * your cursor using [.registerContentObserver]
     */
    protected val observer: ContentObserver = ForceLoadContentObserver()

    /**
     * Asynchronously loads the results from the database.
     */
    abstract override fun loadInBackground(): Cursor?

    /**
     * Registers the content observer for the cursor.
     *
     * @param cursor [Cursor] whose content is to be observed for changes
     */
    protected fun registerContentObserver(cursor: Cursor) {
        cursor.registerContentObserver(observer)
    }

    override fun deliverResult(data: Cursor?) {
        if (isReset) {
            data?.let { onReleaseResources(it) }
            return
        }

        val oldCursor = cursor
        cursor = data

        if (isStarted) {
            super.deliverResult(data)
        }

        if (oldCursor != null && oldCursor !== data && !oldCursor.isClosed) {
            onReleaseResources(oldCursor)
        }
    }

    override fun onStartLoading() {
        if (cursor != null) {
            deliverResult(cursor)
        }

        if (takeContentChanged() || cursor == null) {
            // If the data has changed since the last time it was loaded
            // or is not currently available, start a load.
            forceLoad()
        }
    }

    override fun onStopLoading() {
        cancelLoad()
    }

    override fun onCanceled(data: Cursor?) {
        super.onCanceled(data)
        onReleaseResources(data)
    }

    /**
     * Handles a request to completely reset the Loader.
     */
    override fun onReset() {
        super.onReset()

        onStopLoading()

        // At this point we can release the resources associated with 'mCursor'
        // if needed.
        if (cursor?.isClosed == false) {
            onReleaseResources(cursor)
        }
        cursor = null
    }

    /**
     * Helper function to take care of releasing resources associated
     * with an actively loaded data set.
     *
     * @param c [Cursor] to be released
     */
    private fun onReleaseResources(c: Cursor?) {
        c?.close()
    }
}
