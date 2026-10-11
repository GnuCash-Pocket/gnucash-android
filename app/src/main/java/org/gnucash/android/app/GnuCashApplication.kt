/*
 * Copyright (c) 2013 - 2014 Ngewi Fet <ngewif@gmail.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gnucash.android.app

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import com.google.firebase.FirebaseApp
import org.gnucash.android.BuildConfig
import org.gnucash.android.R
import org.gnucash.android.db.DatabaseHelper
import org.gnucash.android.db.NoActiveBookException
import org.gnucash.android.db.adapter.BooksDbAdapter
import org.gnucash.android.model.Commodity
import org.gnucash.android.model.Commodity.Companion.getLocaleCurrencyCode
import org.gnucash.android.model.TransactionType
import org.gnucash.android.ui.settings.ThemeHelper
import org.gnucash.android.util.CrashlyticsTree
import org.gnucash.android.util.LogTree
import timber.log.Timber
import java.util.Locale

/**
 * An [Application] subclass for retrieving static context
 *
 * @author Ngewi Fet <ngewif@gmail.com>
 */
class GnuCashApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        val context: Context = this
        Companion.context = context
        ThemeHelper.apply(this)

        if (BuildConfig.GOOGLE_GCM) {
            FirebaseApp.initializeApp(context)
        }

        // Logging
        val tree = if (BuildConfig.GOOGLE_GCM && isCrashlyticsEnabled) {
            CrashlyticsTree(BuildConfig.DEBUG)
        } else {
            LogTree(BuildConfig.DEBUG)
        } as Timber.Tree
        Timber.plant(tree)

        val bookUID = initializeDatabaseAdapters(context)
        setDefaultCurrencyCode(context, getDefaultCurrencyCode(context, bookUID))
    }

    override fun onTerminate() {
        super.onTerminate()
        destroyDatabaseAdapters()
    }

    companion object {
        /**
         * Authority (domain) for the file provider. Also used in the app manifest
         */
        const val FILE_PROVIDER_AUTHORITY: String = BuildConfig.APPLICATION_ID + ".fileprovider"
        private const val KEY_ACTIVE_BOOK = "active_book"

        @SuppressLint("StaticFieldLeak")
        private var context: Context? = null

        @SuppressLint("StaticFieldLeak")
        private var dbHelper: DatabaseHelper? = null

        /**
         * Initialize database adapter singletons for use in the application
         * This method should be called every time a new book is opened
         *
         * @param context the context.
         */
        fun initializeDatabaseAdapters(context: Context, bookUID: String? = null): String {
            val booksDbAdapter = BooksDbAdapter.init(context)

            dbHelper?.close()

            var bookUID = bookUID ?: try {
                activeBookUID
            } catch (_: NoActiveBookException) {
                null
            }
            if (bookUID.isNullOrEmpty()) {
                bookUID = booksDbAdapter.fixBooksDatabase()
                activeBookUID = bookUID
            }
            dbHelper = DatabaseHelper(context, bookUID)
            return bookUID
        }

        private fun destroyDatabaseAdapters() {
            try {
                dbHelper?.close()
                dbHelper = null
            } catch (_: Throwable) {
            }
            BooksDbAdapter.close()
        }

        @get:Throws(NoActiveBookException::class)
        var activeBookUID: String = ""
            get() {
                if (field.isEmpty()) {
                    val preferences = PreferenceManager.getDefaultSharedPreferences(appContext)
                    var bookUID = preferences.getString(KEY_ACTIVE_BOOK, null)
                    if (bookUID.isNullOrEmpty()) {
                        val booksDbAdapter = BooksDbAdapter.instance
                        @Suppress("DEPRECATION")
                        bookUID = booksDbAdapter.activeBookUID
                    }
                    field = bookUID
                }
                return field
            }
            set(value) {
                field = value
                val preferences = PreferenceManager.getDefaultSharedPreferences(appContext)
                preferences.edit {
                    putString(KEY_ACTIVE_BOOK, value)
                }
            }

        /**
         * Returns the application context
         *
         * @return Application [Context] object
         */
        val appContext: Context
            get() = context!!

        /**
         * Checks if crashlytics is enabled
         *
         * @return `true` if crashlytics is enabled, `false` otherwise
         */
        val isCrashlyticsEnabled: Boolean
            get() = PreferenceManager.getDefaultSharedPreferences(appContext)
                .getBoolean(
                    context!!.getString(R.string.key_enable_crashlytics),
                    false
                )

        /**
         * Returns `true` if double entry is enabled in the app settings, `false` otherwise.
         * If the value is not set, the default value can be specified in the parameters.
         *
         * @return `true` if double entry is enabled, `false` otherwise
         */
        fun isDoubleEntryEnabled(context: Context): Boolean {
            val preferences: SharedPreferences = getBookPreferences(context)
            return preferences.getBoolean(context.getString(R.string.key_use_double_entry), true)
        }

        /**
         * Returns `true` if setting is enabled to save opening balances after deleting transactions,
         * `false` otherwise.
         *
         * @param defaultValue Default value to return if double entry is not explicitly set
         * @return `true` if opening balances should be saved, `false` otherwise
         */
        fun shouldSaveOpeningBalances(defaultValue: Boolean): Boolean {
            val preferences: SharedPreferences = getBookPreferences(context!!)
            return preferences.getBoolean(
                context!!.getString(R.string.key_save_opening_balances),
                defaultValue
            )
        }

        /**
         * Returns the default currency code for the application. <br></br>
         * What value is actually returned is determined in this order of priority:
         *  * User currency preference (manually set be user in the app)
         *  * Default currency for the device locale
         *  * United States Dollars
         *
         *
         * @return Default currency code string for the application
         */
        var defaultCurrencyCode: String
            get() = getDefaultCurrencyCode(appContext, activeBookUID)
            set(currencyCode) {
                setDefaultCurrencyCode(appContext, currencyCode)
            }

        /**
         * Returns the default currency code for the application. <br></br>
         * What value is actually returned is determined in this order of priority:
         *  * User currency preference (manually set be user in the app)
         *  * Default currency for the device locale
         *  * United States Dollars
         *
         *
         * @return Default currency code string for the application
         */
        private fun getDefaultCurrencyCode(context: Context, bookUID: String): String {
            var currencyCode = getCurrencyCode(context, bookUID)
            if (!currencyCode.isNullOrEmpty()) return currencyCode

            // Maybe use the cached commodity.
            var commodity = Commodity.DEFAULT_COMMODITY
            currencyCode = commodity.currencyCode
            if (currencyCode.isNotEmpty()) return currencyCode

            // Last chance!
            commodity = Commodity.USD
            currencyCode = commodity.currencyCode
            return currencyCode
        }

        internal fun getCurrencyCode(context: Context, bookUID: String): String? {
            val prefKey = context.getString(R.string.key_default_currency)
            var preferences: SharedPreferences = getBookPreferences(context, bookUID)
            var currencyCode = preferences.getString(prefKey, null)
            if (!currencyCode.isNullOrEmpty()) return currencyCode

            preferences = PreferenceManager.getDefaultSharedPreferences(context)
            currencyCode = preferences.getString(prefKey, null)
            if (!currencyCode.isNullOrEmpty()) return currencyCode

            currencyCode = getLocaleCurrencyCode()
            if (!currencyCode.isNullOrEmpty()) return currencyCode

            return null
        }

        fun setDefaultCurrencyCode(context: Context, currencyCode: String?) {
            val prefKey = context.getString(R.string.key_default_currency)
            val preferences: SharedPreferences = getBookPreferences(context)
            preferences.edit { putString(prefKey, currencyCode) }
        }

        /**
         * Returns the default locale which is used for currencies, while handling special cases for
         * locales which are not supported for currency such as en_GB
         *
         * @return The default locale for this device
         */
        val defaultLocale: Locale
            get() {
                var locale = Locale.getDefault()
                //sometimes the locale en_UK is returned which causes a crash with Currency
                if (locale.country == "UK") {
                    locale = Locale(locale.language, "GB")
                }

                //for unsupported locale es_LG
                if (locale.country == "LG") {
                    locale = Locale(locale.language, "ES")
                }

                //there are some strange locales out there
                if (locale.country == "en") {
                    locale = Locale.US
                }
                return locale
            }

        /**
         * Returns `true` if setting is enabled to backup the book before deleting transactions,
         * `false` otherwise.
         *
         * @param context The context.
         * @return `true` if the book should be backed-up.
         */
        fun shouldBackupTransactions(context: Context): Boolean {
            val preferences = PreferenceManager.getDefaultSharedPreferences(context)
            return preferences.getBoolean(
                context.getString(R.string.key_delete_transaction_backup),
                true
            )
        }

        /**
         * Returns `true` if setting is enabled to backup the book before importing a book,
         * `false` otherwise.
         *
         * @param context The context.
         * @return `true` if the book should be backed-up.
         */
        fun shouldBackupForImport(context: Context): Boolean {
            val preferences = PreferenceManager.getDefaultSharedPreferences(context)
            return preferences.getBoolean(context.getString(R.string.key_import_book_backup), true)
        }

        /**
         * Get the default transaction type.
         *
         * @param context The context.
         * @return `DEBIT` or `CREDIT`
         */
        fun getDefaultTransactionType(context: Context): TransactionType {
            val preferences: SharedPreferences = getBookPreferences(context)
            val value = preferences.getString(
                context.getString(R.string.key_default_transaction_type),
                null
            )
            return TransactionType.of(value)
        }

        /**
         * Returns the shared preferences file for the currently active book.
         * Should be used instead of [PreferenceManager.getDefaultSharedPreferences]
         *
         * @param context the context.
         * @return Shared preferences file
         */
        @Throws(NoActiveBookException::class)
        fun getBookPreferences(context: Context): SharedPreferences {
            return getBookPreferences(context, activeBookUID)
        }

        /**
         * Return the [SharedPreferences] for a specific book
         *
         * @param bookUID GUID of the book
         * @return Shared preferences
         */
        fun getBookPreferences(context: Context, bookUID: String): SharedPreferences {
            return context.getSharedPreferences(bookUID, MODE_PRIVATE)
        }

        /**
         * Show transactions formatted with their actual date - relative date otherwise.
         *
         * @return `true` to format with absolute date.
         */
        fun isAbsoluteDate(context: Context): Boolean {
            val preferences: SharedPreferences = getBookPreferences(context)
            return preferences.getBoolean(context.getString(R.string.key_absolute_date), false)
        }
    }
}
