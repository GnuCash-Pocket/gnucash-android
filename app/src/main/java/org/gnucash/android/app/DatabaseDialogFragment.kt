package org.gnucash.android.app

import org.gnucash.android.db.BookProvider
import org.gnucash.android.db.DatabaseHolder
import org.gnucash.android.ui.util.dialog.VolatileDialogFragment

open class DatabaseDialogFragment : VolatileDialogFragment(), BookProvider {
    private val bookProvider: BookProvider get() = requireActivity() as BookProvider

    override val bookUID: String
        get() = bookProvider.bookUID
    override val databaseHolder: DatabaseHolder
        get() = bookProvider.databaseHolder
    override val readableDatabaseHolder: DatabaseHolder
        get() = bookProvider.readableDatabaseHolder
}