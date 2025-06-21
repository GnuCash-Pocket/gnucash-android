package org.gnucash.android.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import org.gnucash.android.db.BookDbHelper.getBookUID
import java.io.Closeable

data class DatabaseHolder @JvmOverloads constructor(
    @JvmField
    val context: Context,
    @JvmField
    val db: SQLiteDatabase,
    @JvmField
    val name: String = getBookUID(db)
) : Closeable {
    override fun close() {
        db.close()
    }
}