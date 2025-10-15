package org.gnucash.android.ui.search

import android.database.Cursor
import android.view.LayoutInflater
import android.view.ViewGroup
import org.gnucash.android.databinding.CardviewTransactionBinding
import org.gnucash.android.ui.adapter.CursorRecyclerAdapter

class SearchResultsAdapter(
    cursor: Cursor?,
    private val isDoubleEntry: Boolean = true,
    private val callback: SearchResultCallback
) : CursorRecyclerAdapter<SearchResultViewHolder>(cursor) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SearchResultViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = CardviewTransactionBinding.inflate(inflater, parent, false)
        return SearchResultViewHolder(binding, isDoubleEntry, callback)
    }

    override fun onBindViewHolderCursor(holder: SearchResultViewHolder, cursor: Cursor) {
        holder.bind(cursor)
    }
}