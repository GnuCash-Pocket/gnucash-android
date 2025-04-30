package org.gnucash.android.db

import android.database.Cursor
import android.database.sqlite.SQLiteStatement
import androidx.annotation.IntRange
import java.math.BigDecimal
import java.math.BigInteger

fun Cursor.getBigDecimal(@IntRange(from = 0) columnIndex: Int): BigDecimal? {
    val s = getString(columnIndex) ?: return null
    return BigDecimal(s)
}

fun SQLiteStatement.bindBigDecimal(@IntRange(from = 1) index: Int, value: BigDecimal?) {
    requireNotNull(value) { "the bind value at index $index is null" }
    bindString(index, value.toString())
}

fun Cursor.getBigInteger(@IntRange(from = 0) columnIndex: Int): BigInteger? {
    val s = getString(columnIndex) ?: return null
    return BigInteger(s)
}

fun SQLiteStatement.bindBigInteger(@IntRange(from = 1) index: Int, value: BigInteger?) {
    requireNotNull(value) { "the bind value at index $index is null" }
    bindString(index, value.toString())
}

fun SQLiteStatement.bindBigInteger(@IntRange(from = 1) index: Int, value: Long?) {
    requireNotNull(value) { "the bind value at index $index is null" }
    bindString(index, value.toString())
}