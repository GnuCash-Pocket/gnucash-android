package org.gnucash.android.db

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor

/**
 * Denotes that a field is a [ContentProvider] column. It can be used as a
 * key for [ContentValues] when inserting or updating data, or as a
 * projection when querying.
 *
 * @hide
 */
@MustBeDocumented
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FIELD)
annotation class Column(
    /**
     * The [Cursor.getType] of the data stored in this column.
     */
    val value: Int,
    /**
     * This column is read-only and cannot be defined during insert or updates.
     */
    val readOnly: Boolean = false
)
