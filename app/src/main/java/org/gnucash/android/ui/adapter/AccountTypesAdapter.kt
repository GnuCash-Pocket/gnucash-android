package org.gnucash.android.ui.adapter

import android.content.Context
import android.widget.ArrayAdapter
import androidx.annotation.LayoutRes
import org.gnucash.android.R
import org.gnucash.android.model.AccountType

class AccountTypesAdapter(
    context: Context,
    @LayoutRes resource: Int = android.R.layout.simple_spinner_item,
    types: List<AccountType> = AccountType.entries
) : ArrayAdapter<AccountTypesAdapter.Label>(context, resource) {

    init {
        setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        val records = types.filter { it != AccountType.ROOT }
        val labels = context.resources.getStringArray(R.array.account_type_entry_values)
        val items = records.map { type -> Label(type, labels[type.labelIndex]) }
            .sortedBy { it.label }

        clear()
        addAll(items)
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

        override fun equals(other: Any?): Boolean {
            return (other is Label) && (this.value == other.value)
        }
    }

    companion object {
        fun expenseAndIncome(context: Context): AccountTypesAdapter {
            return AccountTypesAdapter(
                context = context,
                types = listOf(AccountType.EXPENSE, AccountType.INCOME)
            )
        }
    }
}