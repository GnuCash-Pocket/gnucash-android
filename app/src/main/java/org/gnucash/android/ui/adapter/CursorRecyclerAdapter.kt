package org.gnucash.android.ui.adapter

import android.annotation.SuppressLint
import android.database.ContentObserver
import android.database.Cursor
import android.database.DataSetObserver
import android.os.Handler
import android.provider.BaseColumns
import android.widget.Filter
import android.widget.Filterable
import androidx.recyclerview.widget.RecyclerView
import org.gnucash.android.ui.util.CursorFilter

/**
 * Provide a [RecyclerView.Adapter] implementation with cursor
 * support.
 *
 *
 * Child classes only need to implement [.onCreateViewHolder] and
 * [.onBindViewHolderCursor].
 *
 *
 * This class does not implement deprecated fields and methods from CursorAdapter! Incidentally,
 * only [CursorAdapter.FLAG_REGISTER_CONTENT_OBSERVER] is available, so the
 * flag is implied, and only the Adapter behavior using this flag has been ported.
 *
 * @param <VH> {@inheritDoc}
 * @see RecyclerView.Adapter
 *
 * @see android.widget.CursorAdapter
 *
 * @see Filterable
 *
 * @see CursorFilter.CursorFilterClient
</VH> */
abstract class CursorRecyclerAdapter<VH : RecyclerView.ViewHolder>(cursor: Cursor?) :
    RecyclerView.Adapter<VH>(),
    Filterable,
    CursorFilter.CursorFilterClient {
    private var _cursor: Cursor? = null
    private var isDataValid = false
    private var rowIDColumn = 0
    private var changeObserver: ChangeObserver? = null
    private var dataSetObserver: DataSetObserver? = null
    private var cursorFilter: CursorFilter? = null

    init {
        _cursor = cursor
        isDataValid = false
        rowIDColumn = cursor?.getColumnIndexOrThrow(BaseColumns._ID) ?: -1

        changeObserver = ChangeObserver()
        dataSetObserver = CursorDataSetObserver()

        if (cursor != null) {
            isDataValid = true
            if (changeObserver != null) cursor.registerContentObserver(changeObserver)
            if (dataSetObserver != null) cursor.registerDataSetObserver(dataSetObserver)
        }
    }

    override val cursor: Cursor? get() = _cursor

    override fun onBindViewHolder(holder: VH, position: Int) {
        val cursor = cursor!!
        check(isDataValid) { "this should only be called when the cursor is valid" }
        check(cursor.moveToPosition(position)) { "Couldn't move cursor to position $position" }
        onBindViewHolderCursor(holder, cursor)
    }

    /**
     * See [android.widget.CursorAdapter.bindView],
     * [.onBindViewHolder]
     *
     * @param holder View holder.
     * @param cursor The cursor from which to get the data. The cursor is already
     * moved to the correct position.
     */
    abstract fun onBindViewHolderCursor(holder: VH, cursor: Cursor)

    override fun getItemCount(): Int {
        val cursor = cursor ?: return 0
        return if (isDataValid) {
            cursor.count
        } else {
            0
        }
    }

    /**
     * @see android.widget.ListAdapter.getItemId
     */
    override fun getItemId(position: Int): Long {
        val cursor = cursor ?: return 0
        return if (isDataValid && cursor.moveToPosition(position)) {
            cursor.getLong(rowIDColumn)
        } else {
            0
        }
    }

    /**
     * Change the underlying cursor to a new cursor. If there is an existing cursor it will be
     * closed.
     *
     * @param cursor The new cursor to be used
     */
    override fun changeCursor(cursor: Cursor?) {
        val old = swapCursor(cursor)
        old?.close()
    }

    /**
     * Swap in a new Cursor, returning the old Cursor.  Unlike
     * [.changeCursor], the returned old Cursor is *not*
     * closed.
     *
     * @param newCursor The new cursor to be used.
     * @return Returns the previously set Cursor, or null if there was not one.
     * If the given new Cursor is the same instance is the previously set
     * Cursor, null is also returned.
     */
    @SuppressLint("NotifyDataSetChanged")
    fun swapCursor(newCursor: Cursor?): Cursor? {
        val oldCursor = _cursor
        if (newCursor === oldCursor) {
            return null
        }
        if (oldCursor != null) {
            if (changeObserver != null) oldCursor.unregisterContentObserver(changeObserver)
            if (dataSetObserver != null) oldCursor.unregisterDataSetObserver(dataSetObserver)
        }
        _cursor = newCursor
        if (newCursor != null) {
            if (changeObserver != null) newCursor.registerContentObserver(changeObserver)
            if (dataSetObserver != null) newCursor.registerDataSetObserver(dataSetObserver)
            rowIDColumn = newCursor.getColumnIndexOrThrow(BaseColumns._ID)
            isDataValid = true
            // notify the observers about the new cursor
            notifyDataSetChanged()
        } else {
            rowIDColumn = -1
            isDataValid = false
            // notify the observers about the lack of a data set
            // notifyDataSetInvalidated();
            notifyItemRangeRemoved(0, itemCount)
        }
        return oldCursor
    }

    /**
     *
     * Converts the cursor into a CharSequence. Subclasses should override this
     * method to convert their results. The default implementation returns an
     * empty String for null values or the default String representation of
     * the value.
     *
     * @param cursor the cursor to convert to a CharSequence
     * @return a CharSequence representing the value
     */
    override fun convertToString(cursor: Cursor?): CharSequence {
        return cursor?.toString().orEmpty()
    }

    /**
     * Runs a query with the specified constraint. This query is requested
     * by the filter attached to this adapter.
     *
     *
     * The query is provided by a
     * [android.widget.FilterQueryProvider].
     * If no provider is specified, the current cursor is not filtered and returned.
     *
     *
     * After this method returns the resulting cursor is passed to [.changeCursor]
     * and the previous cursor is closed.
     *
     *
     * This method is always executed on a background thread, not on the
     * application's main thread (or UI thread.)
     *
     *
     * Contract: when constraint is null or empty, the original results,
     * prior to any filtering, must be returned.
     *
     * @param constraint the constraint with which the query must be filtered
     * @return a Cursor representing the results of the new query
     * @see .getFilter
     */
    override fun runQueryOnBackgroundThread(constraint: CharSequence?): Cursor? {
        return cursor
    }

    override fun getFilter(): Filter? {
        if (cursorFilter == null) {
            cursorFilter = CursorFilter(this)
        }
        return cursorFilter
    }

    /**
     * Called when the [ContentObserver] on the cursor receives a change notification.
     * Can be implemented by sub-class.
     *
     * @see ContentObserver.onChange
     */
    @SuppressLint("NotifyDataSetChanged")
    protected fun onContentChanged() {
        notifyDataSetChanged()
    }

    private inner class ChangeObserver : ContentObserver(Handler()) {
        override fun deliverSelfNotifications(): Boolean {
            return true
        }

        override fun onChange(selfChange: Boolean) {
            onContentChanged()
        }
    }

    private inner class CursorDataSetObserver : DataSetObserver() {
        @SuppressLint("NotifyDataSetChanged")
        override fun onChanged() {
            isDataValid = true
            notifyDataSetChanged()
        }

        override fun onInvalidated() {
            isDataValid = false
            // notifyDataSetInvalidated();
            notifyItemRangeRemoved(0, itemCount)
        }
    }
}