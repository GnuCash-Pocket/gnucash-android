package org.gnucash.android.ui

import android.widget.Adapter
import android.widget.ArrayAdapter

operator fun <T> Adapter.get(position: Int): T {
    return getItem(position) as T
}

operator fun <T> ArrayAdapter<T>.get(position: Int): T {
    return getItem(position) as T
}
