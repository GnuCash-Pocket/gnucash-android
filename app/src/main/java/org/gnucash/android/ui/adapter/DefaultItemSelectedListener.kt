package org.gnucash.android.ui.adapter

import android.view.View
import android.widget.AdapterView

typealias ItemSelectedCallback = (
    parent: AdapterView<*>,
    view: View?,
    position: Int,
    id: Long
) -> Unit

open class DefaultItemSelectedListener(
    private val ignoreNullView: Boolean = true,
    private val callback: ItemSelectedCallback
) : AdapterView.OnItemSelectedListener {
    override fun onItemSelected(
        parent: AdapterView<*>,
        view: View?,
        position: Int,
        id: Long
    ) {
        //the item selection is fired twice by the Android framework. Ignore the first one
        if (view == null && ignoreNullView) return
        if (position < 0) return
        callback(parent, view, position, id)
    }

    override fun onNothingSelected(parent: AdapterView<*>) = Unit
}