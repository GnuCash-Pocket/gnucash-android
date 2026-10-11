package org.gnucash.android.db

import android.content.Context
import android.os.Bundle
import org.gnucash.android.app.GnuCashActivity
import org.gnucash.android.app.requireArguments
import org.gnucash.android.ui.common.UxArgument
import timber.log.Timber

open class DatabaseActivity : GnuCashActivity(), BookProvider {
    private lateinit var dbHelper: DatabaseHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        val context: Context = this

        //if a parameter was passed to open an account within a specific book, then switch
        val bookUID = requireArguments().getString(UxArgument.BOOK_UID)!!
        if (bookUID.isEmpty()) {
            Timber.e("Book required")
            finish()
            return
        }

        val dbHelper = DatabaseHelper(context, bookUID)
        this.dbHelper = dbHelper

        super.onCreate(savedInstanceState)
    }

    override fun onDestroy() {
        if (::dbHelper.isInitialized) dbHelper.close()
        super.onDestroy()
    }

    override fun onStop() {
        super.onStop()
        if (::dbHelper.isInitialized) dbHelper.clearCache()
    }

    override val bookUID: String
        get() = dbHelper.bookUID
    override val databaseHolder: DatabaseHolder
        get() = dbHelper.holder
    override val readableDatabaseHolder: DatabaseHolder
        get() = dbHelper.readableHolder
}