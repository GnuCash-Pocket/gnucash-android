package org.gnucash.android.ui.price

import android.content.Context
import org.gnucash.android.R
import org.gnucash.android.model.Price
import org.gnucash.android.ui.adapter.SpinnerArrayAdapter
import org.gnucash.android.ui.adapter.SpinnerItem

class PriceTypeAdapter(
    context: Context,
    types: List<Price.Type> = Price.Type.entries
) : SpinnerArrayAdapter<Price.Type>(context) {

    init {
        val labels = context.resources.getStringArray(R.array.price_types)
        val items = types.map { type -> SpinnerItem(type, labels[type.ordinal]) }
            .sortedBy { it.label }

        clear()
        addAll(items)
    }

    fun getType(position: Int): Price.Type? {
        return getItem(position)?.value
    }
}