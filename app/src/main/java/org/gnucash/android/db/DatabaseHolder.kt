package org.gnucash.android.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import org.gnucash.android.db.BookDbHelper.Companion.getBookUID
import java.io.Closeable

data class DatabaseHolder(
    val context: Context,
    val db: SQLiteDatabase,
    val name: String = getBookUID(db)
) : Closeable {
    override fun close() {
        db.close()
    }
}