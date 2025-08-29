package org.gnucash.android.lang

import androidx.collection.LongSparseArray

typealias BooleanCallback = ((Boolean) -> Unit)
typealias VoidCallback = (() -> Unit)

operator fun <E> LongSparseArray<E>.set(key: Long, value: E){
    put(key, value)
}

operator fun CharSequence.plus(rhs: CharSequence): String {
    return this.toString() + rhs
}