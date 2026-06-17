package org.gnucash.android.ui.price

import android.content.Context
import android.database.Cursor
import android.view.LayoutInflater
import android.view.ViewGroup
import org.gnucash.android.app.GnuCashApplication.Companion.isAbsoluteDate
import org.gnucash.android.databinding.CardviewPriceBinding
import org.gnucash.android.ui.adapter.CursorRecyclerAdapter

class PriceCursorAdapter(
    private val onEditPriceClick: PriceCallback,
    private val onDeletePriceClick: PriceCallback,
    private val onDuplicatePriceClick: PriceCallback,
) : CursorRecyclerAdapter<PriceViewHolder>(null) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PriceViewHolder {
        val context: Context = parent.context
        val inflater = LayoutInflater.from(context)
        val binding = CardviewPriceBinding.inflate(inflater, parent, false)
        val useAbsoluteDate = isAbsoluteDate(context)
        return PriceViewHolder(
            binding,
            useAbsoluteDate,
            onEditPriceClick,
            onDeletePriceClick,
            onDuplicatePriceClick,
        )
    }

    override fun onBindViewHolderCursor(holder: PriceViewHolder, cursor: Cursor) {
        holder.bind(cursor)
    }
}

