package org.gnucash.android.db

import android.content.ContentValues
import android.database.Cursor
import android.database.DatabaseUtils
import android.database.SQLException
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteDatabase.CONFLICT_NONE
import android.database.sqlite.SQLiteStatement
import androidx.annotation.IntRange
import org.gnucash.android.util.TimestampHelper.getTimestampFromUtcString
import org.gnucash.android.util.TimestampHelper.getUtcStringFromTimestamp
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

fun SQLiteStatement.bindTimestamp(@IntRange(from = 1) index: Int, value: Timestamp) {
    bindString(index, getUtcStringFromTimestamp(value))
}

fun SQLiteStatement.bindTimestamp(@IntRange(from = 1) index: Int, value: Long) {
    bindString(index, getUtcStringFromTimestamp(value))
}

fun Cursor.getBigDecimal(@IntRange(from = 0) columnIndex: Int): BigDecimal? {
    val s = getString(columnIndex) ?: return null
    return BigDecimal(s)
}

fun Cursor.getBigDecimal(columnName: String, @IntRange(from = 0) offset: Int = 0): BigDecimal? {
    return getBigDecimal(getColumnIndexOrThrow(columnName, offset))
}

fun Cursor.getBigInteger(@IntRange(from = 0) columnIndex: Int): BigInteger? {
    val s = getString(columnIndex) ?: return null
    return BigInteger(s)
}

fun Cursor.getBigInteger(columnName: String, @IntRange(from = 0) offset: Int = 0): BigInteger? {
    return getBigInteger(getColumnIndexOrThrow(columnName, offset))
}

fun Cursor.getBoolean(@IntRange(from = 0) columnIndex: Int): Boolean {
    return getInt(columnIndex) != 0
}

fun Cursor.getBoolean(columnName: String, @IntRange(from = 0) offset: Int = 0): Boolean {
    return getBoolean(getColumnIndexOrThrow(columnName, offset))
}

fun Cursor.getDouble(columnName: String, @IntRange(from = 0) offset: Int = 0): Double {
    return getDouble(getColumnIndexOrThrow(columnName, offset))
}

fun Cursor.getFloat(columnName: String, @IntRange(from = 0) offset: Int = 0): Float {
    return getFloat(getColumnIndexOrThrow(columnName, offset))
}

fun Cursor.getInt(columnName: String, @IntRange(from = 0) offset: Int = 0): Int {
    return getInt(getColumnIndexOrThrow(columnName, offset))
}

fun Cursor.getLong(columnName: String, @IntRange(from = 0) offset: Int = 0): Long {
    return getLong(getColumnIndexOrThrow(columnName, offset))
}

fun Cursor.getString(columnName: String, @IntRange(from = 0) offset: Int = 0): String? {
    return getString(getColumnIndexOrThrow(columnName, offset))
}

fun Cursor.getTimestamp(@IntRange(from = 0) columnIndex: Int): Timestamp? {
    val value = getString(columnIndex) ?: return null
    if (value.isEmpty()) return null
    return getTimestampFromUtcString(value)
}

fun Cursor.getTimestamp(columnName: String, @IntRange(from = 0) offset: Int = 0): Timestamp? {
    val value = getString(columnName) ?: return null
    if (value.isEmpty()) return null
    return getTimestampFromUtcString(value)
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

fun Cursor.forEachIndexed(callback: (Long, Cursor) -> Unit) {
    use { cursor ->
        if (cursor.moveToFirst()) {
            var i = 0L
            do {
                callback(i++, cursor)
            } while (cursor.moveToNext())
        }
    }
}

fun Long.toTimestamp(): Timestamp {
    return Timestamp(this)
}

fun Cursor.toValues(): ContentValues {
    val values = ContentValues()
    DatabaseUtils.cursorRowToContentValues(this, values)
    return values
}

@Throws(SQLException::class)
fun SQLiteDatabase.insert(table: String, values: ContentValues): Long {
    return insertWithOnConflict(table, null, values, CONFLICT_NONE)
}

fun Cursor.getColumnIndexOrThrow(columnName: String, startingIndex: Int = 0): Int {
    val columnNames = columnNames!!
    val length = columnNames.size
    for (i in startingIndex until length) {
        if (columnNames[i].equals(columnName, ignoreCase = true)) {
            return i
        }
    }
    return -1
}