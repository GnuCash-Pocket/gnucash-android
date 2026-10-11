package org.gnucash.android.ui.settings

import android.os.Bundle
import org.gnucash.android.db.BookProvider
import org.gnucash.android.db.DatabaseHolder

abstract class BookPreferencesFragment : GnuPreferenceFragment(), BookProvider {
    private val bookProvider: BookProvider get() = requireActivity() as BookProvider

    override val bookUID: String
        get() = bookProvider.bookUID
    override val databaseHolder: DatabaseHolder
        get() = bookProvider.databaseHolder
    override val readableDatabaseHolder: DatabaseHolder
        get() = bookProvider.readableDatabaseHolder

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        preferenceManager.setSharedPreferencesName(bookUID)
        initDatabase(databaseHolder)
    }

    abstract fun initDatabase(dbHolder: DatabaseHolder)
}