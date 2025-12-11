package org.gnucash.android.db

import android.content.ContentValues
import android.database.Cursor
import android.database.SQLException
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteDatabase.CONFLICT_NONE
import android.database.sqlite.SQLiteStatement
import androidx.annotation.IntRange
import org.gnucash.android.util.set
import java.math.BigDecimal
import java.math.BigInteger
import java.sql.Timestamp

fun SQLiteStatement.bindBigDecimal(@IntRange(from = 1) index: Int, value: BigDecimal?) {
    requireNotNull(value) { "the bind value at index $index is null" }
    bindString(index, value.toString())
}

fun SQLiteStatement.bindBigInteger(@IntRange(from = 1) index: Int, value: BigInteger?) {
    requireNotNull(value) { "the bind value at index $index is null" }
    bindString(index, value.toString())
}

fun SQLiteStatement.bindBigInteger(@IntRange(from = 1) index: Int, value: Long?) {
    requireNotNull(value) { "the bind value at index $index is null" }
    bindString(index, value.toString())
}

fun SQLiteStatement.bindBoolean(@IntRange(from = 1) index: Int, value: Boolean) {
    bindLong(index, if (value) 1L else 0L)
}

fun SQLiteStatement.bindInt(@IntRange(from = 1) index: Int, value: Int) {
    bindLong(index, value.toLong())
}

fun Cursor.getBigDecimal(@IntRange(from = 0) columnIndex: Int): BigDecimal? {
    val s = getString(columnIndex) ?: return null
    return BigDecimal(s)
}

fun Cursor.getBigDecimal(columnName: String): BigDecimal? {
    return getBigDecimal(getColumnIndexOrThrow(columnName))
}

fun Cursor.getBigInteger(@IntRange(from = 0) columnIndex: Int): BigInteger? {
    val s = getString(columnIndex) ?: return null
    return BigInteger(s)
}

fun Cursor.getBigInteger(columnName: String): BigInteger? {
    return getBigInteger(getColumnIndexOrThrow(columnName))
}

fun Cursor.getBoolean(@IntRange(from = 0) columnIndex: Int): Boolean {
    return getInt(columnIndex) != 0
}

fun Cursor.getBoolean(columnName: String): Boolean {
    return getBoolean(getColumnIndexOrThrow(columnName))
}

fun Cursor.getDouble(columnName: String): Double {
    return getDouble(getColumnIndexOrThrow(columnName))
}

fun Cursor.getFloat(columnName: String): Float {
    return getFloat(getColumnIndexOrThrow(columnName))
}

fun Cursor.getInt(columnName: String): Int {
    return getInt(getColumnIndexOrThrow(columnName))
}

fun Cursor.getLong(columnName: String): Long {
    return getLong(getColumnIndexOrThrow(columnName))
}

fun Cursor.getString(columnName: String): String? {
    return getString(getColumnIndexOrThrow(columnName))
}

fun Array<String>.joinIn(): String = joinToString("','", prefix = "('", postfix = "')")

fun Iterable<String>.joinIn(): String = joinToString("','", prefix = "('", postfix = "')")

fun Cursor.forEach(callback: (Cursor) -> Unit) {
    use { cursor ->
        if (cursor.moveToFirst()) {
            do {
                callback(cursor)
            } while (cursor.moveToNext())
        }
    }
}

fun Long.toTimestamp(): Timestamp {
    return Timestamp(this)
}

fun Cursor.toValues(): ContentValues {
    val values = ContentValues()
    val columnCount = columnCount
    for (i in 0 until columnCount) {
        val name = columnNames[i]
        val value = getString(i)
        values[name] = value
    }
    return values
}

@Throws(SQLException::class)
fun SQLiteDatabase.insert(table: String, values: ContentValues): Long {
    return insertWithOnConflict(table, null, values, CONFLICT_NONE)
}
