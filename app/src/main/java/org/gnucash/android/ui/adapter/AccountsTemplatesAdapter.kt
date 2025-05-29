package org.gnucash.android.ui.adapter

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.annotation.LayoutRes
import org.gnucash.android.importer.AccountsTemplate

class AccountsTemplatesAdapter @JvmOverloads constructor(
    context: Context,
    @LayoutRes resource: Int = android.R.layout.simple_list_item_2
) : ArrayAdapter<AccountsTemplate.Header>(context, resource, android.R.id.text1) {

    init {
        clear()
        val examples = AccountsTemplate()
        addAll(examples.headers(context))
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val item = getItem(position)!!
        val view = super.getView(position, convertView, parent)

        val text2 = view.findViewById<TextView>(android.R.id.text2)
        text2.text = item.shortDescription ?: item.longDescription

        return view
    }

    override fun hasStableIds(): Boolean {
        return true
    }

    fun getPosition(header: AccountsTemplate.Header): Int {
        for (i in 0 until count) {
            val item = getItem(i)!!
            if (item == header) {
                return i
            }
        }
        return -1
    }
}