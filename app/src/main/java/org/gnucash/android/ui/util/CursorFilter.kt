package org.gnucash.android.ui.util

import android.database.Cursor
import android.widget.Filter

internal class CursorFilter(val client: CursorFilterClient) : Filter() {
    internal interface CursorFilterClient {
        fun convertToString(cursor: Cursor?): CharSequence

        fun runQueryOnBackgroundThread(constraint: CharSequence?): Cursor?

        val cursor: Cursor?

        fun changeCursor(cursor: Cursor?)
    }

    override fun convertResultToString(resultValue: Any?): CharSequence {
        return client.convertToString(resultValue as Cursor)
    }

    override fun performFiltering(constraint: CharSequence?): FilterResults {
        val cursor = client.runQueryOnBackgroundThread(constraint)

        val results = FilterResults()
        if (cursor != null) {
            results.count = cursor.count
            results.values = cursor
        } else {
            results.count = 0
            results.values = null
        }
        return results
    }

    override fun publishResults(constraint: CharSequence?, results: FilterResults) {
        val oldCursor = client.cursor

        if (results.values !== oldCursor) {
            client.changeCursor(results.values as Cursor?)
        }
    }
}
