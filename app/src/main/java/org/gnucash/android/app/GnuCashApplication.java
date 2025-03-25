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
package org.gnucash.android.app;

import android.annotation.SuppressLint;
import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;

import com.google.firebase.FirebaseApp;

import org.gnucash.android.BuildConfig;
import org.gnucash.android.R;
import org.gnucash.android.db.BookDbHelper;
import org.gnucash.android.db.DatabaseHelper;
import org.gnucash.android.db.adapter.AccountsDbAdapter;
import org.gnucash.android.db.adapter.BooksDbAdapter;
import org.gnucash.android.db.adapter.BudgetAmountsDbAdapter;
import org.gnucash.android.db.adapter.BudgetsDbAdapter;
import org.gnucash.android.db.adapter.CommoditiesDbAdapter;
import org.gnucash.android.db.adapter.PricesDbAdapter;
import org.gnucash.android.db.adapter.RecurrenceDbAdapter;
import org.gnucash.android.db.adapter.ScheduledActionDbAdapter;
import org.gnucash.android.db.adapter.SplitsDbAdapter;
import org.gnucash.android.db.adapter.TransactionsDbAdapter;
import org.gnucash.android.model.Commodity;
import org.gnucash.android.model.TransactionType;
import org.gnucash.android.ui.settings.PreferenceActivity;
import org.gnucash.android.ui.settings.ThemeHelper;
import org.gnucash.android.util.CrashlyticsTree;
import org.gnucash.android.util.LogTree;

import java.io.IOException;
import java.util.Currency;
import java.util.Locale;

import timber.log.Timber;

/**
 * An {@link Application} subclass for retrieving static context
 *
 * @author Ngewi Fet <ngewif@gmail.com>
 */
public class GnuCashApplication extends Application {

    /**
     * Authority (domain) for the file provider. Also used in the app manifest
     */
    public static final String FILE_PROVIDER_AUTHORITY = BuildConfig.APPLICATION_ID + ".fileprovider";

    @SuppressLint("StaticFieldLeak")
    private static Context context;
    @Nullable
    private static AccountsDbAdapter mAccountsDbAdapter;
    @Nullable
    private static TransactionsDbAdapter mTransactionsDbAdapter;
    @Nullable
    private static SplitsDbAdapter mSplitsDbAdapter;
    @Nullable
    private static ScheduledActionDbAdapter mScheduledActionDbAdapter;
    @Nullable
    private static CommoditiesDbAdapter mCommoditiesDbAdapter;
    @Nullable
    private static PricesDbAdapter mPricesDbAdapter;
    @Nullable
    private static BudgetsDbAdapter mBudgetsDbAdapter;
    @Nullable
    private static BudgetAmountsDbAdapter mBudgetAmountsDbAdapter;
    @Nullable
    private static RecurrenceDbAdapter mRecurrenceDbAdapter;
    @Nullable
    private static BooksDbAdapter mBooksDbAdapter;
    @Nullable
    private static DatabaseHelper mDbHelper;

    /**
     * Returns darker version of specified <code>color</code>.
     * Use for theming the status bar color when setting the color of the actionBar
     */
    public static int darken(int color) {
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        hsv[2] *= 0.8f; // value component
        return Color.HSVToColor(hsv);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        final Context context = getApplicationContext();
        GnuCashApplication.context = context;
        ThemeHelper.apply(this);

        if (BuildConfig.GOOGLE_GCM) {
            FirebaseApp.initializeApp(this);
        }

        // Logging
        Timber.Tree tree = (Timber.Tree) (BuildConfig.GOOGLE_GCM && isCrashlyticsEnabled() ? new CrashlyticsTree(BuildConfig.DEBUG) : new LogTree(BuildConfig.DEBUG));
        Timber.plant(tree);

        initializeDatabaseAdapters(context);
        setDefaultCurrencyCode(context, getDefaultCurrencyCode());

        StethoUtils.install(this);
    }

    @Override
    public void onTerminate() {
        super.onTerminate();
        destroyDatabaseAdapters();
    }

    /**
     * Initialize database adapter singletons for use in the application
     * This method should be called every time a new book is opened
     *
     * @param context the context.
     */
    public static void initializeDatabaseAdapters(@NonNull Context context) {
        BookDbHelper bookDbHelper = new BookDbHelper(context);
        SQLiteDatabase db = bookDbHelper.getWritableDatabase();
        mBooksDbAdapter = new BooksDbAdapter(db);

        if (mDbHelper != null) { //close if open
            mDbHelper.getReadableDatabase().close();
        }

        String bookUID;
        try {
            bookUID = mBooksDbAdapter.getActiveBookUID();
        } catch (BooksDbAdapter.NoActiveBookFoundException e) {
            bookUID = mBooksDbAdapter.fixBooksDatabase();
        }
        if (TextUtils.isEmpty(bookUID)) {
            bookUID = bookDbHelper.insertBlankBook(db).getUID();
        }
        mDbHelper = new DatabaseHelper(context, bookUID);
        SQLiteDatabase mainDb;
        try {
            mainDb = mDbHelper.getWritableDatabase();
        } catch (SQLException e) {
            Timber.e(e, "Error getting database: %s", e.getMessage());
            mainDb = mDbHelper.getReadableDatabase();
        }

        mCommoditiesDbAdapter = new CommoditiesDbAdapter(mainDb);
        mSplitsDbAdapter = new SplitsDbAdapter(mainDb, mCommoditiesDbAdapter);
        mTransactionsDbAdapter = new TransactionsDbAdapter(mainDb, mSplitsDbAdapter);
        mAccountsDbAdapter = new AccountsDbAdapter(mainDb, mTransactionsDbAdapter);
        mRecurrenceDbAdapter = new RecurrenceDbAdapter(mainDb);
        mScheduledActionDbAdapter = new ScheduledActionDbAdapter(mainDb, mRecurrenceDbAdapter);
        mPricesDbAdapter = new PricesDbAdapter(mainDb, mCommoditiesDbAdapter);
        mBudgetAmountsDbAdapter = new BudgetAmountsDbAdapter(mainDb);
        mBudgetsDbAdapter = new BudgetsDbAdapter(mainDb, mBudgetAmountsDbAdapter, mRecurrenceDbAdapter);
    }

    private static void destroyDatabaseAdapters() {
        if (mSplitsDbAdapter != null) {
            try {
                mSplitsDbAdapter.close();
                mSplitsDbAdapter = null;
            } catch (IOException ignore) {
            }
        }
        if (mTransactionsDbAdapter != null) {
            try {
                mTransactionsDbAdapter.close();
                mTransactionsDbAdapter = null;
            } catch (IOException ignore) {
            }
        }
        if (mAccountsDbAdapter != null) {
            try {
                mAccountsDbAdapter.close();
                mAccountsDbAdapter = null;
            } catch (IOException ignore) {
            }
        }
        if (mRecurrenceDbAdapter != null) {
            try {
                mRecurrenceDbAdapter.close();
                mRecurrenceDbAdapter = null;
            } catch (IOException ignore) {
            }
        }
        if (mScheduledActionDbAdapter != null) {
            try {
                mScheduledActionDbAdapter.close();
                mScheduledActionDbAdapter = null;
            } catch (IOException ignore) {
            }
        }
        if (mPricesDbAdapter != null) {
            try {
                mPricesDbAdapter.close();
                mPricesDbAdapter = null;
            } catch (IOException ignore) {
            }
        }
        if (mCommoditiesDbAdapter != null) {
            try {
                mCommoditiesDbAdapter.close();
                mCommoditiesDbAdapter = null;
            } catch (IOException ignore) {
            }
        }
        if (mBudgetAmountsDbAdapter != null) {
            try {
                mBudgetAmountsDbAdapter.close();
                mBudgetAmountsDbAdapter = null;
            } catch (IOException ignore) {
            }
        }
        if (mBudgetsDbAdapter != null) {
            try {
                mBudgetsDbAdapter.close();
                mBudgetsDbAdapter = null;
            } catch (IOException ignore) {
            }
        }
        if (mBooksDbAdapter != null) {
            try {
                mBooksDbAdapter.close();
                mBooksDbAdapter = null;
            } catch (IOException ignore) {
            }
        }
        if (mDbHelper != null) {
            mDbHelper.close();
            mDbHelper = null;
        }
    }

    @Nullable
    public static AccountsDbAdapter getAccountsDbAdapter() {
        return mAccountsDbAdapter;
    }

    @Nullable
    public static TransactionsDbAdapter getTransactionDbAdapter() {
        return mTransactionsDbAdapter;
    }

    @Nullable
    public static SplitsDbAdapter getSplitsDbAdapter() {
        return mSplitsDbAdapter;
    }

    @Nullable
    public static ScheduledActionDbAdapter getScheduledEventDbAdapter() {
        return mScheduledActionDbAdapter;
    }

    @Nullable
    public static CommoditiesDbAdapter getCommoditiesDbAdapter() {
        return mCommoditiesDbAdapter;
    }

    @Nullable
    public static PricesDbAdapter getPricesDbAdapter() {
        return mPricesDbAdapter;
    }

    @Nullable
    public static BudgetsDbAdapter getBudgetDbAdapter() {
        return mBudgetsDbAdapter;
    }

    @Nullable
    public static RecurrenceDbAdapter getRecurrenceDbAdapter() {
        return mRecurrenceDbAdapter;
    }

    @Nullable
    public static BudgetAmountsDbAdapter getBudgetAmountsDbAdapter() {
        return mBudgetAmountsDbAdapter;
    }

    @Nullable
    public static BooksDbAdapter getBooksDbAdapter() {
        return mBooksDbAdapter;
    }

    @Nullable
    public static String getActiveBookUID() {
        BooksDbAdapter adapter = getBooksDbAdapter();
        return (adapter != null) ? adapter.getActiveBookUID() : null;
    }

    /**
     * Returns the currently active database in the application
     *
     * @return Currently active {@link SQLiteDatabase}
     */
    @Nullable
    public static SQLiteDatabase getActiveDb() {
        return (mDbHelper != null) ? mDbHelper.getWritableDatabase() : null;
    }

    /**
     * Returns the application context
     *
     * @return Application {@link Context} object
     */
    @NonNull
    public static Context getAppContext() {
        return GnuCashApplication.context;
    }

    /**
     * Checks if crashlytics is enabled
     *
     * @return {@code true} if crashlytics is enabled, {@code false} otherwise
     */
    public static boolean isCrashlyticsEnabled() {
        return PreferenceManager.getDefaultSharedPreferences(context)
            .getBoolean(context.getString(R.string.key_enable_crashlytics), false);
    }

    /**
     * Returns <code>true</code> if double entry is enabled in the app settings, <code>false</code> otherwise.
     * If the value is not set, the default value can be specified in the parameters.
     *
     * @return <code>true</code> if double entry is enabled, <code>false</code> otherwise
     */
    public static boolean isDoubleEntryEnabled() {
        SharedPreferences sharedPrefs = PreferenceActivity.getActiveBookSharedPreferences();
        return sharedPrefs.getBoolean(context.getString(R.string.key_use_double_entry), true);
    }

    /**
     * Returns <code>true</code> if setting is enabled to save opening balances after deleting transactions,
     * <code>false</code> otherwise.
     *
     * @param defaultValue Default value to return if double entry is not explicitly set
     * @return <code>true</code> if opening balances should be saved, <code>false</code> otherwise
     */
    public static boolean shouldSaveOpeningBalances(boolean defaultValue) {
        SharedPreferences sharedPrefs = PreferenceActivity.getActiveBookSharedPreferences();
        return sharedPrefs.getBoolean(context.getString(R.string.key_save_opening_balances), defaultValue);
    }

    /**
     * Returns the default currency code for the application. <br/>
     * What value is actually returned is determined in this order of priority:<ul>
     * <li>User currency preference (manually set be user in the app)</li>
     * <li>Default currency for the device locale</li>
     * <li>United States Dollars</li>
     * </ul>
     *
     * @return Default currency code string for the application
     */
    @NonNull
    public static String getDefaultCurrencyCode() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String currencyCode = prefs.getString(context.getString(R.string.key_default_currency), null);
        if (!TextUtils.isEmpty(currencyCode)) return currencyCode;

        try {
            Locale locale = getDefaultLocale();
            currencyCode = Currency.getInstance(locale).getCurrencyCode();
            if (!TextUtils.isEmpty(currencyCode)) return currencyCode;
        } catch (Throwable e) {
            Timber.e(e);
        }

        // Maybe use the cached commodity.
        Commodity commodity = Commodity.DEFAULT_COMMODITY;
        currencyCode = (commodity != null) ? commodity.getCurrencyCode() : null;
        if (!TextUtils.isEmpty(currencyCode)) return currencyCode;

        // Last chance!
        commodity = Commodity.USD;
        currencyCode = commodity.getCurrencyCode();
        return currencyCode;
    }

    /**
     * Sets the default currency for the application in all relevant places:
     * <ul>
     *     <li>Shared preferences</li>
     *     <li>{@link Commodity#DEFAULT_COMMODITY}</li>
     * </ul>
     *
     * @param currencyCode ISO 4217 currency code
     * @see #getDefaultCurrencyCode()
     */
    public static void setDefaultCurrencyCode(@NonNull String currencyCode) {
        setDefaultCurrencyCode(context, currencyCode);
    }

    /**
     * Sets the default currency for the application in all relevant places:
     * <ul>
     *     <li>Shared preferences</li>
     *     <li>{@link Commodity#DEFAULT_COMMODITY}</li>
     * </ul>
     *
     * @param context      the context.
     * @param currencyCode ISO 4217 currency code
     * @see #getDefaultCurrencyCode()
     */
    public static void setDefaultCurrencyCode(@NonNull Context context, @NonNull String currencyCode) {
        PreferenceManager.getDefaultSharedPreferences(context)
            .edit()
            .putString(context.getString(R.string.key_default_currency), currencyCode)
            .apply();
        Commodity.DEFAULT_COMMODITY = Commodity.getInstance(currencyCode);
    }

    /**
     * Returns the default locale which is used for currencies, while handling special cases for
     * locales which are not supported for currency such as en_GB
     *
     * @return The default locale for this device
     */
    @NonNull
    public static Locale getDefaultLocale() {
        Locale locale = Locale.getDefault();
        //sometimes the locale en_UK is returned which causes a crash with Currency
        if (locale.getCountry().equals("UK")) {
            locale = new Locale(locale.getLanguage(), "GB");
        }

        //for unsupported locale es_LG
        if (locale.getCountry().equals("LG")) {
            locale = new Locale(locale.getLanguage(), "ES");
        }

        //there are some strange locales out there
        if (locale.getCountry().equals("en")) {
            locale = Locale.US;
        }
        return locale;
    }

    /**
     * Returns <code>true</code> if setting is enabled to backup the book before deleting transactions,
     * <code>false</code> otherwise.
     *
     * @param context The context.
     * @return <code>true</code> if the book should be backed-up.
     */
    public static boolean shouldBackupTransactions(Context context) {
        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context);
        return sharedPrefs.getBoolean(context.getString(R.string.key_delete_transaction_backup), true);
    }

    /**
     * Returns <code>true</code> if setting is enabled to backup the book before importing a book,
     * <code>false</code> otherwise.
     *
     * @param context The context.
     * @return <code>true</code> if the book should be backed-up.
     */
    public static boolean shouldBackupForImport(Context context) {
        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context);
        return sharedPrefs.getBoolean(context.getString(R.string.key_import_book_backup), true);
    }

    /**
     * Get the default transaction type.
     *
     * @param context The context.
     * @return <code>DEBIT</code> or <code>CREDIT</code>
     */
    public static TransactionType getDefaultTransactionType(@NonNull Context context) {
        SharedPreferences sharedPrefs = PreferenceActivity.getActiveBookSharedPreferences();
        String value = sharedPrefs.getString(context.getString(R.string.key_default_transaction_type), null);
        return TransactionType.of(value);
    }
}
