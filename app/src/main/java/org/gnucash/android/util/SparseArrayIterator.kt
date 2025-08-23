package org.gnucash.android.util

import android.util.SparseArray

class SparseArrayIterator<E>(private val array: SparseArray<E>) : Iterator<E> {
    private val size = array.size()
    private var index = 0;

    override fun hasNext(): Boolean {
        return index < size
    }

    override fun next(): E {
        return array.valueAt(index++)
    }
}