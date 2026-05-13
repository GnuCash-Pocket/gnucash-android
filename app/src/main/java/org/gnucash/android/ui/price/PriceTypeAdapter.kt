package org.gnucash.android.ui.price

import android.content.Context
import org.gnucash.android.R
import org.gnucash.android.model.Price
import org.gnucash.android.ui.adapter.SpinnerArrayAdapter
import org.gnucash.android.ui.adapter.SpinnerItem

// `Transaction` type is not available for users to set via the GUI.
class PriceTypeAdapter(context: Context) : SpinnerArrayAdapter<Price.Type>(context) {

    init {
        val types = Price.Type.entries.filterNot { it === Price.Type.Transaction }
        val labels = context.resources.getStringArray(R.array.price_types)
        val items = types.mapIndexed { index, type -> SpinnerItem(type, labels[index]) }
            .sortedBy { it.label }

        clear()
        addAll(items)
    }

    fun getType(position: Int): Price.Type? {
        return getItem(position)?.value
    }

    override fun getValuePosition(value: Price.Type): Int {
        val value = if (value === Price.Type.Transaction) Price.Type.Unknown else value
        return super.getValuePosition(value)
    }
}