package org.gnucash.android.ui.price

import android.content.Context
import org.gnucash.android.model.Commodity
import org.gnucash.android.ui.adapter.CommoditiesAdapter
import org.gnucash.android.ui.adapter.SpinnerArrayAdapter
import org.gnucash.android.ui.adapter.SpinnerItem

class NamespaceAdapter(context: Context) :
    SpinnerArrayAdapter<CommoditiesAdapter.Namespace>(context) {

    init {
        val types = CommoditiesAdapter.Namespace.entries
        val items = types.map { type -> SpinnerItem(type, context.getString(type.labelId)) }

        clear()
        addAll(items)
    }

    fun getType(position: Int): CommoditiesAdapter.Namespace? {
        return getItem(position)?.value
    }

    fun getCommodityPosition(commodity: Commodity): Int {
        val count = this.count
        for (i in 0 until count) {
            val item = getItem(i) ?: continue
            if (item.value === CommoditiesAdapter.Namespace.Currencies && commodity.isCurrency) {
                return i
            }
            if (item.value === CommoditiesAdapter.Namespace.NonCurrency && !commodity.isCurrency) {
                return i
            }
        }
        return NO_SELECTION
    }
}