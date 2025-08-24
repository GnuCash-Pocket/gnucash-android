package org.gnucash.android.util

import android.content.Context
import org.gnucash.android.app.GnuCashApplication
import org.gnucash.android.ui.account.AccountsActivity

/**
 * Utility class for common operations involving books
 */
object BookUtils {
    /**
     * Activates the book with unique identifier `bookUID`, and refreshes the database adapters
     *
     * @param bookUID GUID of the book to be activated
     */
    fun activateBook(bookUID: String) {
        activateBook(GnuCashApplication.getAppContext(), bookUID)
    }

    /**
     * Activates the book with unique identifier `bookUID`, and refreshes the database adapters
     *
     * @param bookUID GUID of the book to be activated
     */
    @JvmStatic
    fun activateBook(context: Context, bookUID: String) {
        GnuCashApplication.getBooksDbAdapter()!!.setActive(bookUID)
        GnuCashApplication.initializeDatabaseAdapters(context)
    }

    /**
     * Loads the book with GUID `bookUID` and opens the AccountsActivity
     *
     * @param context the context.
     * @param bookUID GUID of the book to be loaded
     */
    @JvmStatic
    fun loadBook(context: Context, bookUID: String) {
        activateBook(context, bookUID)
        AccountsActivity.start(context)
    }
}
