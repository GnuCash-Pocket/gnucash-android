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
import android.database.sqlite.SQLiteDatabase
import androidx.preference.PreferenceManager
import com.google.firebase.FirebaseApp
import org.gnucash.android.BuildConfig
import org.gnucash.android.R
import org.gnucash.android.db.BookDbHelper
import org.gnucash.android.db.DatabaseHelper
import org.gnucash.android.db.DatabaseHolder
import org.gnucash.android.db.adapter.AccountsDbAdapter
import org.gnucash.android.db.adapter.BooksDbAdapter
import org.gnucash.android.db.adapter.BooksDbAdapter.NoActiveBookFoundException
import org.gnucash.android.db.adapter.BudgetAmountsDbAdapter
import org.gnucash.android.db.adapter.BudgetsDbAdapter
import org.gnucash.android.db.adapter.CommoditiesDbAdapter
import org.gnucash.android.db.adapter.PricesDbAdapter
import org.gnucash.android.db.adapter.RecurrenceDbAdapter
import org.gnucash.android.db.adapter.ScheduledActionDbAdapter
import org.gnucash.android.db.adapter.SplitsDbAdapter
import org.gnucash.android.db.adapter.TransactionsDbAdapter
import org.gnucash.android.model.Commodity
import org.gnucash.android.model.Commodity.Companion.getLocaleCurrencyCode
import org.gnucash.android.model.TransactionType
import org.gnucash.android.ui.settings.ThemeHelper
import org.gnucash.android.util.CrashlyticsTree
import org.gnucash.android.util.LogTree
import timber.log.Timber
import java.io.IOException
import java.util.Locale

/**
 * An [Application] subclass for retrieving static context
 *
 * @author Ngewi Fet <ngewif@gmail.com>
 */
class GnuCashApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        val context = applicationContext
        Companion.context = context
        ThemeHelper.apply(this)

        if (BuildConfig.GOOGLE_GCM) {
            FirebaseApp.initializeApp(this)
        }

        // Logging
        val tree = if (BuildConfig.GOOGLE_GCM && isCrashlyticsEnabled) {
            CrashlyticsTree(BuildConfig.DEBUG)
        } else {
            LogTree(BuildConfig.DEBUG)
        } as Timber.Tree
        Timber.plant(tree)

        initializeDatabaseAdapters(context)
        defaultCurrencyCode = defaultCurrencyCode
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

        @SuppressLint("StaticFieldLeak")
        private var context: Context? = null

        var accountsDbAdapter: AccountsDbAdapter? = null
            private set

        var transactionDbAdapter: TransactionsDbAdapter? = null
            private set

        var splitsDbAdapter: SplitsDbAdapter? = null
            private set

        var scheduledEventDbAdapter: ScheduledActionDbAdapter? = null
            private set

        var commoditiesDbAdapter: CommoditiesDbAdapter? = null
            private set

        var pricesDbAdapter: PricesDbAdapter? = null
            private set

        var budgetDbAdapter: BudgetsDbAdapter? = null
            private set

        var budgetAmountsDbAdapter: BudgetAmountsDbAdapter? = null
            private set

        var recurrenceDbAdapter: RecurrenceDbAdapter? = null
            private set

        var booksDbAdapter: BooksDbAdapter? = null
            private set

        @SuppressLint("StaticFieldLeak")
        private var dbHelper: DatabaseHelper? = null

        /**
         * Initialize database adapter singletons for use in the application
         * This method should be called every time a new book is opened
         *
         * @param context the context.
         */
        fun initializeDatabaseAdapters(context: Context) {
            val bookDbHelper = BookDbHelper(context)
            val bookHolder = bookDbHelper.getHolder()
            val booksDbAdapter = BooksDbAdapter(bookHolder)
            Companion.booksDbAdapter = booksDbAdapter

            dbHelper?.close()

            var bookUID = try {
                booksDbAdapter.activeBookUID
            } catch (_: NoActiveBookFoundException) {
                booksDbAdapter.fixBooksDatabase()
            }
            if (bookUID.isNullOrEmpty()) {
                bookUID = bookDbHelper.insertBlankBook().uid
            }
            val dbHelper = DatabaseHelper(context, bookUID)
            Companion.dbHelper = dbHelper
            val dbHolder: DatabaseHolder = dbHelper.holder

            val commoditiesDbAdapter = CommoditiesDbAdapter(dbHolder, true)
            this.commoditiesDbAdapter = commoditiesDbAdapter
            pricesDbAdapter = PricesDbAdapter(commoditiesDbAdapter)
            splitsDbAdapter = SplitsDbAdapter(commoditiesDbAdapter)
            transactionDbAdapter = TransactionsDbAdapter(splitsDbAdapter!!)
            accountsDbAdapter = AccountsDbAdapter(transactionDbAdapter!!, pricesDbAdapter!!)
            recurrenceDbAdapter = RecurrenceDbAdapter(dbHolder)
            scheduledEventDbAdapter = ScheduledActionDbAdapter(recurrenceDbAdapter!!, transactionDbAdapter!!)
            budgetAmountsDbAdapter = BudgetAmountsDbAdapter(commoditiesDbAdapter)
            budgetDbAdapter = BudgetsDbAdapter(budgetAmountsDbAdapter!!, recurrenceDbAdapter!!)
            Commodity.DEFAULT_COMMODITY = commoditiesDbAdapter.defaultCommodity
        }

        private fun destroyDatabaseAdapters() {
            if (splitsDbAdapter != null) {
                try {
                    splitsDbAdapter!!.close()
                    splitsDbAdapter = null
                } catch (_: IOException) {
                }
            }
            if (transactionDbAdapter != null) {
                try {
                    transactionDbAdapter!!.close()
                    transactionDbAdapter = null
                } catch (_: IOException) {
                }
            }
            if (accountsDbAdapter != null) {
                try {
                    accountsDbAdapter!!.close()
                    accountsDbAdapter = null
                } catch (_: IOException) {
                }
            }
            if (recurrenceDbAdapter != null) {
                try {
                    recurrenceDbAdapter!!.close()
                    recurrenceDbAdapter = null
                } catch (_: IOException) {
                }
            }
            if (scheduledEventDbAdapter != null) {
                try {
                    scheduledEventDbAdapter!!.close()
                    scheduledEventDbAdapter = null
                } catch (_: IOException) {
                }
            }
            if (pricesDbAdapter != null) {
                try {
                    pricesDbAdapter!!.close()
                    pricesDbAdapter = null
                } catch (_: IOException) {
                }
            }
            if (commoditiesDbAdapter != null) {
                try {
                    commoditiesDbAdapter!!.close()
                    commoditiesDbAdapter = null
                } catch (_: IOException) {
                }
            }
            if (budgetAmountsDbAdapter != null) {
                try {
                    budgetAmountsDbAdapter!!.close()
                    budgetAmountsDbAdapter = null
                } catch (_: IOException) {
                }
            }
            if (budgetDbAdapter != null) {
                try {
                    budgetDbAdapter!!.close()
                    budgetDbAdapter = null
                } catch (_: IOException) {
                }
            }
            if (booksDbAdapter != null) {
                try {
                    booksDbAdapter!!.close()
                    booksDbAdapter = null
                } catch (_: IOException) {
                }
            }
            dbHelper?.close()
            dbHelper = null
        }

        @get:Throws(NoActiveBookFoundException::class)
        val activeBookUID: String?
            get() {
                val adapter: BooksDbAdapter? = booksDbAdapter
                return adapter?.activeBookUID
            }

        /**
         * Returns the currently active database in the application
         *
         * @return Currently active [SQLiteDatabase]
         */
        val activeDb: SQLiteDatabase?
            get() = if (dbHelper != null) dbHelper!!.getWritableDatabase() else null

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
            get() = PreferenceManager.getDefaultSharedPreferences(context!!)
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
            get() = getDefaultCurrencyCode(context!!)
            set(currencyCode) {
                commoditiesDbAdapter!!.setDefaultCurrencyCode(currencyCode)
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
        fun getDefaultCurrencyCode(context: Context): String {
            val prefKey = context.getString(R.string.key_default_currency)
            var preferences: SharedPreferences = getBookPreferences(context)
            var currencyCode = preferences.getString(prefKey, null)
            if (!currencyCode.isNullOrEmpty()) return currencyCode

            preferences = PreferenceManager.getDefaultSharedPreferences(context)
            currencyCode = preferences.getString(prefKey, null)
            if (!currencyCode.isNullOrEmpty()) return currencyCode

            currencyCode = getLocaleCurrencyCode()
            if (!currencyCode.isNullOrEmpty()) return currencyCode

            // Maybe use the cached commodity.
            var commodity = Commodity.DEFAULT_COMMODITY
            currencyCode = if (commodity != null) commodity.currencyCode else null
            if (!currencyCode.isNullOrEmpty()) return currencyCode

            // Last chance!
            commodity = Commodity.USD
            currencyCode = commodity.currencyCode
            return currencyCode
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
        @Throws(NoActiveBookFoundException::class)
        fun getBookPreferences(context: Context): SharedPreferences {
            return getBookPreferences(context, activeBookUID!!)
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
    }
}
