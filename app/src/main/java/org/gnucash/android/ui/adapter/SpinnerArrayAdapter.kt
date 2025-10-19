package org.gnucash.android.ui.adapter

import android.content.Context
import android.widget.AdapterView.INVALID_POSITION
import android.widget.ArrayAdapter
import androidx.annotation.IdRes
import androidx.annotation.LayoutRes

open class SpinnerArrayAdapter<T> : ArrayAdapter<SpinnerItem<T>> {
    constructor(context: Context) : this(context, android.R.layout.simple_spinner_item)

    constructor(context: Context, @LayoutRes resource: Int) : super(context, resource)

    constructor(context: Context, @LayoutRes resource: Int, @IdRes textViewResourceId: Int) :
            super(context, resource, textViewResourceId)

    constructor(
        context: Context,
        @LayoutRes resource: Int,
        @IdRes textViewResourceId: Int,
        objects: List<SpinnerItem<T>>
    ) : super(context, resource, textViewResourceId, objects)

    constructor(
        context: Context,
        @LayoutRes resource: Int,
        @IdRes textViewResourceId: Int,
        objects: Array<SpinnerItem<T>>
    ) : super(context, resource, textViewResourceId, objects)

    constructor(context: Context, @LayoutRes resource: Int, objects: List<SpinnerItem<T>>) :
            super(context, resource, objects)

    constructor(context: Context, @LayoutRes resource: Int, objects: Array<SpinnerItem<T>>) :
            super(context, resource, objects)

    constructor(context: Context, objects: List<SpinnerItem<T>>) :
            this(context, android.R.layout.simple_spinner_item, objects)

    constructor(context: Context, objects: Array<SpinnerItem<T>>) :
            this(context, android.R.layout.simple_spinner_item, objects)

    init {
        setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
    }

    override fun hasStableIds(): Boolean {
        return true
    }

    fun getValuePosition(value: T): Int {
        for (i in 0 until count) {
            val item = getItem(i) ?: continue
            if (item.value == value) {
                return i
            }
        }
        return INVALID_POSITION
    }
}