package org.gnucash.android.ui.adapter

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import org.gnucash.android.importer.xml.AccountsTemplate

class AccountsTemplatesAdapter(context: Context) : SpinnerArrayAdapter<AccountsTemplate.Header>(
    context,
    android.R.layout.simple_list_item_2,
    android.R.id.text1
) {

    init {
        val examples = AccountsTemplate()
        val items = examples.headers(context).map { header ->
            SpinnerItem(header)
        }
        clear()
        addAll(items)
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val item = getItem(position)!!
        val header = item.value
        val view = super.getView(position, convertView, parent)

        val text2 = view.findViewById<TextView>(android.R.id.text2)
        text2.text = header.shortDescription ?: header.longDescription

        return view
    }

    fun getPosition(header: AccountsTemplate.Header): Int {
        for (i in 0 until count) {
            val item = getItem(i) ?: continue
            if (item.value == header) {
                return i
            }
        }
        return -1
    }
}