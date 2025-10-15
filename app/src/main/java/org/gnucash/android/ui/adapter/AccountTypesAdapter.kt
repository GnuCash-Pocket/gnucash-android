package org.gnucash.android.ui.adapter

import android.content.Context
import org.gnucash.android.R
import org.gnucash.android.model.AccountType

class AccountTypesAdapter(
    context: Context,
    types: List<AccountType> = AccountType.entries
) : SpinnerArrayAdapter<AccountType>(context) {

    init {
        val records = types.filter { it != AccountType.ROOT }
        val labels = context.resources.getStringArray(R.array.account_type_entry_values)
        val items = records.map { type -> SpinnerItem(type, labels[type.labelIndex]) }
            .sortedBy { it.label }

        clear()
        addAll(items)
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

    companion object {
        fun expenseAndIncome(context: Context): AccountTypesAdapter {
            return AccountTypesAdapter(
                context = context,
                types = listOf(AccountType.EXPENSE, AccountType.INCOME)
            )
        }
    }
}