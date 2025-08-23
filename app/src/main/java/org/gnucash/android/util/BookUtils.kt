package org.gnucash.android.util;

import android.content.Context;

import androidx.annotation.NonNull;

import org.gnucash.android.app.GnuCashApplication;
import org.gnucash.android.ui.account.AccountsActivity;

/**
 * Utility class for common operations involving books
 */

public class BookUtils {

    /**
     * Activates the book with unique identifier {@code bookUID}, and refreshes the database adapters
     *
     * @param bookUID GUID of the book to be activated
     */
    public static void activateBook(@NonNull String bookUID) {
        activateBook(GnuCashApplication.getAppContext(), bookUID);
    }

    /**
     * Activates the book with unique identifier {@code bookUID}, and refreshes the database adapters
     *
     * @param bookUID GUID of the book to be activated
     */
    public static void activateBook(Context context, @NonNull String bookUID) {
        GnuCashApplication.getBooksDbAdapter().setActive(bookUID);
        GnuCashApplication.initializeDatabaseAdapters(context);
    }

    /**
     * Loads the book with GUID {@code bookUID} and opens the AccountsActivity
     *
     * @param context the context.
     * @param bookUID GUID of the book to be loaded
     */
    public static void loadBook(@NonNull Context context, @NonNull String bookUID) {
        activateBook(context, bookUID);
        AccountsActivity.start(context);
    }
}
