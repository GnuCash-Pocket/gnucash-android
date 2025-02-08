package org.gnucash.android.ui.adapter

import android.content.Context
import android.widget.ArrayAdapter
import androidx.annotation.LayoutRes
import org.gnucash.android.R
import org.gnucash.android.model.AccountType

class AccountTypesAdapter @JvmOverloads constructor(
    context: Context,
    @LayoutRes resource: Int = android.R.layout.simple_spinner_item
) : ArrayAdapter<AccountTypesAdapter.Label>(context, resource) {

    init {
        setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        val records = AccountType.entries.filter { it != AccountType.ROOT }
        val labels = context.resources.getStringArray(R.array.account_type_entry_values)

        clear()
        addAll(records.mapIndexed { index, type -> Label(type, labels[index]) })
    }

    override fun hasStableIds(): Boolean {
        return true
    }

    fun getType(position: Int): AccountType? {
        return getItem(position)?.value
    }

    fun getPosition(accountType: AccountType): Int {
        for (i in 0 until count) {
            val type = getType(i)!!
            if (type == accountType) {
                return i
            }
        }
        return -1
    }

    data class Label(val value: AccountType, val label: String) {
        override fun toString(): String = label
    }
}