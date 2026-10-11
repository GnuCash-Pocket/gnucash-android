package org.gnucash.android.db

interface BookProvider {
    val bookUID: String
    val databaseHolder: DatabaseHolder
    val readableDatabaseHolder: DatabaseHolder
}