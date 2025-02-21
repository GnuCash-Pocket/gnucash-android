package org.gnucash.android.ui.adapter

import android.content.Context
import android.widget.ArrayAdapter
import androidx.annotation.LayoutRes
import org.gnucash.android.db.adapter.CommoditiesDbAdapter
import org.gnucash.android.model.Commodity

class CommoditiesAdapter(
    context: Context,
    adapter: CommoditiesDbAdapter,
    @LayoutRes resource: Int = android.R.layout.simple_spinner_item
) : ArrayAdapter<CommoditiesAdapter.Label>(context, resource) {

    @JvmOverloads
    constructor(
        context: Context,
        @LayoutRes resource: Int = android.R.layout.simple_spinner_item
    ) : this(
        context,
        CommoditiesDbAdapter.getInstance()!!,
        resource
    )

    init {
        setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        val records = adapter.allRecords
        clear()
        addAll(records.map { Label(it) })
    }

    override fun hasStableIds(): Boolean {
        return true
    }

    fun getCommodity(position: Int): Commodity? {
        return getItem(position)?.commodity
    }

    fun getPosition(mnemonic: String): Int {
        for (i in 0 until count) {
            val commodity = getCommodity(i)!!
            if (commodity.currencyCode == mnemonic) {
                return i
            }
        }
        return -1
    }

    fun getPosition(commodity: Commodity): Int {
        for (i in 0 until count) {
            if (commodity == getCommodity(i)) {
                return i
            }
        }
        return -1
    }

    data class Label(val commodity: Commodity) {
        override fun toString(): String = commodity.formatListItem()
    }
}