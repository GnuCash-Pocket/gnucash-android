package org.gnucash.android.util

import android.content.Context
import org.gnucash.android.app.GnuCashApplication
import org.gnucash.android.app.GnuCashApplication.Companion.activeBookUID
import org.gnucash.android.app.GnuCashApplication.Companion.initializeDatabaseAdapters
import org.gnucash.android.db.adapter.BooksDbAdapter
import org.gnucash.android.model.Book
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
        activateBook(GnuCashApplication.appContext, bookUID)
    }

    /**
     * Activates the book with unique identifier `bookUID`, and refreshes the database adapters
     *
     * @param bookUID GUID of the book to be activated
     */
    fun activateBook(context: Context, bookUID: String) {
        activeBookUID = bookUID
        initializeDatabaseAdapters(context, bookUID)
    }

    /**
     * Loads the book with GUID `bookUID` and opens the AccountsActivity
     *
     * @param context the context.
     * @param bookUID GUID of the book to be loaded
     */
    fun showBook(context: Context, bookUID: String) {
        activateBook(context, bookUID)
        AccountsActivity.start(context, bookUID)
    }

    fun populateName(context: Context, booksDbAdapter: BooksDbAdapter, book: Book) {
        var displayName = book.displayName
        if (displayName.isNullOrEmpty()) {
            var name = book.sourceUri?.getDocumentName(context)
            if (!name.isNullOrEmpty()) {
                // Remove short file type extension, e.g. ".xml" or ".gnucash" or ".gnca.gz"
                val indexFileType = name.indexOf('.')
                if (indexFileType > 0) {
                    name = name.take(indexFileType)
                }
                displayName = name
            }
            if (displayName.isNullOrEmpty()) {
                displayName = booksDbAdapter.generateDefaultBookName()
            }
            book.displayName = displayName
        }
    }

    // Does not delete the actual database files.
    fun deleteRecords(booksDbAdapter: BooksDbAdapter) {
        booksDbAdapter.deleteAllRecords()
        activeBookUID = ""
    }
}
