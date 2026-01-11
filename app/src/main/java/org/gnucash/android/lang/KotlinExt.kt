package org.gnucash.android.lang

import android.text.Editable
import android.text.TextUtils
import android.util.SparseArray
import android.widget.EditText
import androidx.collection.LongSparseArray
import org.gnucash.android.util.SparseArrayIterator

typealias BooleanCallback = ((Boolean) -> Unit)
typealias VoidCallback = (() -> Unit)

operator fun <E> LongSparseArray<E>.set(key: Long, value: E) {
    put(key, value)
}

operator fun CharSequence.plus(rhs: CharSequence): String {
    return this.toString() + rhs
}

infix fun CharSequence.equals(rhs: CharSequence?): Boolean {
    return TextUtils.equals(this, rhs)
}

fun Editable.trim(): String {
    return this.toString().trim()
}

fun <E> SparseArray<E>.iterator(): Iterator<E> {
    return SparseArrayIterator(this)
}

fun EditText.trim(): String = text.toString().trim()
