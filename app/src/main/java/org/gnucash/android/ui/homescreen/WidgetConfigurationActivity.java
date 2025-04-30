/*
 * Copyright (c) 2012 - 2015 Ngewi Fet <ngewif@gmail.com>
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

package org.gnucash.android.ui.homescreen;

import android.app.Activity;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CompoundButton;
import android.widget.RemoteViews;

import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceManager;

import org.gnucash.android.R;
import org.gnucash.android.databinding.WidgetConfigurationBinding;
import org.gnucash.android.db.BookDbHelper;
import org.gnucash.android.db.adapter.AccountsDbAdapter;
import org.gnucash.android.db.adapter.BooksDbAdapter;
import org.gnucash.android.model.Account;
import org.gnucash.android.model.Book;
import org.gnucash.android.model.Money;
import org.gnucash.android.receivers.TransactionAppWidgetProvider;
import org.gnucash.android.ui.account.AccountsActivity;
import org.gnucash.android.ui.common.FormActivity;
import org.gnucash.android.ui.common.UxArgument;
import org.gnucash.android.ui.passcode.PasscodeHelper;
import org.gnucash.android.ui.settings.PreferenceActivity;
import org.gnucash.android.ui.transaction.TransactionsActivity;
import org.gnucash.android.util.QualifiedAccountNameCursorAdapter;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import timber.log.Timber;

/**
 * Activity for configuration which account to display on a widget.
 * The activity is opened each time a widget is added to the homescreen
 *
 * @author Ngewi Fet <ngewif@gmail.com>
 */
public class WidgetConfigurationActivity extends Activity {

    private static final String PREFS_PREFIX = "widget:";
    private static final int FLAGS_UPDATE = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;

    private AccountsDbAdapter mAccountsDbAdapter;
    private int mAppWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID;
    private String selectedBookUID = null;
    private String selectedAccountUID = null;
    private boolean isHideBalance = false;
    private final List<Book> books = new ArrayList<>();
    private QualifiedAccountNameCursorAdapter accountsAdapter;

    private WidgetConfigurationBinding mBinding;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mBinding = WidgetConfigurationBinding.inflate(getLayoutInflater());
        setContentView(mBinding.getRoot());

        Context context = this;
        BooksDbAdapter booksDbAdapter = BooksDbAdapter.getInstance();
        List<Book> allBooks = booksDbAdapter.getAllRecords();
        books.clear();
        books.addAll(allBooks);

        ArrayAdapter<Book> booksAdapter = new ArrayAdapter<>(context, android.R.layout.simple_spinner_item, allBooks);
        booksAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mBinding.inputBooksSpinner.setAdapter(booksAdapter);

        mAccountsDbAdapter = AccountsDbAdapter.getInstance();

        accountsAdapter = new QualifiedAccountNameCursorAdapter(context, null);
        mBinding.inputAccountsSpinner.setAdapter(accountsAdapter);

        boolean passcodeEnabled = PasscodeHelper.isPasscodeEnabled(this);
        mBinding.inputHideAccountBalance.setChecked(passcodeEnabled);

        bindListeners();
        handleIntent(getIntent());
    }

    /**
     * Sets click listeners for the buttons in the dialog
     */
    private void bindListeners() {
        mBinding.inputBooksSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                Book book = books.get(position);
                String bookUID = book.getUID();
                selectedBookUID = bookUID;

                mAccountsDbAdapter = new AccountsDbAdapter(BookDbHelper.getDatabase(bookUID));

                Cursor cursor = mAccountsDbAdapter.fetchAllRecordsOrderedByFullName();
                accountsAdapter.swapCursor(cursor);

                int accountIndex = accountsAdapter.getItemPosition(selectedAccountUID);
                mBinding.inputAccountsSpinner.setSelection(accountIndex);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                //nothing to see here, move along
            }
        });

        mBinding.inputAccountsSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedAccountUID = mAccountsDbAdapter.getUID(id);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        mBinding.inputHideAccountBalance.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                isHideBalance = isChecked;
            }
        });

        mBinding.defaultButtons.btnSave.setOnClickListener(unusedView -> {
            int appWidgetId = mAppWidgetId;
            if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
                finish();
                return;
            }

            String bookUID = selectedBookUID;
            String accountUID = selectedAccountUID;
            boolean hideAccountBalance = isHideBalance;

            Context context = WidgetConfigurationActivity.this;
            configureWidget(context, appWidgetId, bookUID, accountUID, hideAccountBalance);
            updateWidget(context, appWidgetId);

            Intent resultValue = new Intent()
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
            setResult(RESULT_OK, resultValue);
            finish();
        });

        mBinding.defaultButtons.btnCancel.setOnClickListener(unusedView -> {
            setResult(RESULT_CANCELED);
            finish();
        });
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(Intent intent) {
        int appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID;

        Bundle extras = intent.getExtras();
        if (extras != null) {
            appWidgetId = extras.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, mAppWidgetId);
        }

        Context context = this;
        SharedPreferences preferences = context.getSharedPreferences(PREFS_PREFIX + appWidgetId, MODE_PRIVATE);
        String bookUID = preferences.getString(UxArgument.BOOK_UID, null);
        String accountUID = preferences.getString(UxArgument.SELECTED_ACCOUNT_UID, null);
        boolean hideAccountBalance = preferences.getBoolean(UxArgument.HIDE_ACCOUNT_BALANCE_IN_WIDGET, false);

        mAppWidgetId = appWidgetId;
        selectedBookUID = bookUID;
        selectedAccountUID = accountUID;
        isHideBalance = hideAccountBalance;

        //determine the position of the book
        int bookIndex = -1;
        final int booksCount = books.size();
        for (int i = 0; i < booksCount; i++) {
            Book book = books.get(i);
            if (book.getUID().equals(bookUID) || book.isActive()) {
                bookIndex = i;
                break;
            }
        }
        mBinding.inputBooksSpinner.setSelection(bookIndex);

        mBinding.inputHideAccountBalance.setChecked(hideAccountBalance);
    }

    /**
     * Configure a given widget with the given parameters.
     *
     * @param context            The current context
     * @param appWidgetId        ID of the widget to configure
     * @param bookUID            UID of the book for this widget
     * @param accountUID         UID of the account for this widget
     * @param hideAccountBalance <code>true</code> if the account balance should be hidden,
     *                           <code>false</code> otherwise
     */
    public static void configureWidget(final Context context, int appWidgetId, String bookUID, String accountUID, boolean hideAccountBalance) {
        context.getSharedPreferences(PREFS_PREFIX + appWidgetId, MODE_PRIVATE).edit()
            .putString(UxArgument.BOOK_UID, bookUID)
            .putString(UxArgument.SELECTED_ACCOUNT_UID, accountUID)
            .putBoolean(UxArgument.HIDE_ACCOUNT_BALANCE_IN_WIDGET, hideAccountBalance)
            .apply();
    }

    /**
     * Remove the configuration for a widget. Primarily this should be called when a widget is
     * destroyed.
     *
     * @param context     The current context
     * @param appWidgetId ID of the widget whose configuration should be removed
     */
    public static void removeWidgetConfiguration(final Context context, int appWidgetId) {
        context.getSharedPreferences(PREFS_PREFIX + appWidgetId, MODE_PRIVATE).edit()
            .clear()
            .apply();
    }

    /**
     * Updates the widget with id <code>appWidgetId</code> with information from the
     * account with record ID <code>accountId</code>
     * If the account has been deleted, then a notice is posted in the widget
     *
     * @param appWidgetId ID of the widget to be updated
     */
    public static void updateWidget(final Context context, int appWidgetId) {
        Timber.i("Updating widget: %s", appWidgetId);
        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
        final RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_4x1);

        SharedPreferences preferences = context.getSharedPreferences(PREFS_PREFIX + appWidgetId, MODE_PRIVATE);
        String bookUID = preferences.getString(UxArgument.BOOK_UID, null);
        String accountUID = preferences.getString(UxArgument.SELECTED_ACCOUNT_UID, null);
        boolean hideAccountBalance = preferences.getBoolean(UxArgument.HIDE_ACCOUNT_BALANCE_IN_WIDGET, false);

        if (TextUtils.isEmpty(bookUID) || TextUtils.isEmpty(accountUID)) {
            appWidgetManager.updateAppWidget(appWidgetId, views);
            return;
        }

        AccountsDbAdapter accountsDbAdapter = new AccountsDbAdapter(BookDbHelper.getDatabase(bookUID));

        Account account = null;
        try {
            account = accountsDbAdapter.getSimpleRecord(accountUID);
        } catch (IllegalArgumentException e) {
            Timber.e(e, "Account not found, resetting widget %s", appWidgetId);
        }
        if (account == null) {
            accountsDbAdapter.closeQuietly();

            //if account has been deleted, let the user know
            views.setTextViewText(R.id.account_name, context.getString(R.string.toast_account_deleted));
            views.setTextViewText(R.id.transactions_summary, "");
            //set it to simply open the app
            PendingIntent pendingIntent = PendingIntent.getActivity(context, 0,
                new Intent(context, AccountsActivity.class), FLAGS_UPDATE);
            views.setOnClickPendingIntent(R.id.widget_layout, pendingIntent);
            views.setOnClickPendingIntent(R.id.btn_new_transaction, pendingIntent);
            appWidgetManager.updateAppWidget(appWidgetId, views);

            PreferenceActivity.getActiveBookSharedPreferences().edit()
                .remove(UxArgument.SELECTED_ACCOUNT_UID + appWidgetId)
                .apply();
            return;
        }

        views.setTextViewText(R.id.account_name, account.getName());

        if (hideAccountBalance) {
            views.setViewVisibility(R.id.transactions_summary, View.GONE);
        } else {
            Money accountBalance = accountsDbAdapter.getCurrentAccountBalance(accountUID);
            views.setTextViewText(R.id.transactions_summary,
                accountBalance.formattedString());
            int color = accountBalance.isNegative() ? R.color.debit_red : R.color.credit_green;
            views.setTextColor(R.id.transactions_summary, ContextCompat.getColor(context, color));
        }

        Intent accountViewIntent = new Intent(context, TransactionsActivity.class)
            .setAction(Intent.ACTION_VIEW)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK)
            .putExtra(UxArgument.SELECTED_ACCOUNT_UID, accountUID)
            .putExtra(UxArgument.BOOK_UID, bookUID);
        PendingIntent accountPendingIntent = PendingIntent
            .getActivity(context, appWidgetId, accountViewIntent, FLAGS_UPDATE);
        views.setOnClickPendingIntent(R.id.widget_layout, accountPendingIntent);

        if (accountsDbAdapter.isPlaceholderAccount(accountUID)) {
            views.setViewVisibility(R.id.btn_new_transaction, View.GONE);
        } else {
            Intent newTransactionIntent = new Intent(context, FormActivity.class)
                .setAction(Intent.ACTION_INSERT_OR_EDIT)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(UxArgument.FORM_TYPE, FormActivity.FormType.TRANSACTION.name())
                .putExtra(UxArgument.BOOK_UID, bookUID)
                .putExtra(UxArgument.SELECTED_ACCOUNT_UID, accountUID);
            PendingIntent pendingIntent = PendingIntent
                .getActivity(context, appWidgetId, newTransactionIntent, FLAGS_UPDATE);
            views.setOnClickPendingIntent(R.id.btn_new_transaction, pendingIntent);
        }

        accountsDbAdapter.closeQuietly();
        appWidgetManager.updateAppWidget(appWidgetId, views);
    }

    /**
     * Updates all widgets belonging to the application
     *
     * @param context Application context
     */
    public static void updateAllWidgets(final Context context) {
        Timber.i("Updating all widgets");
        AppWidgetManager widgetManager = AppWidgetManager.getInstance(context);
        ComponentName componentName = new ComponentName(context, TransactionAppWidgetProvider.class);
        final int[] appWidgetIds = widgetManager.getAppWidgetIds(componentName);

        //update widgets asynchronously so as not to block method which called the update
        //inside the computation of the account balance
        new Thread(new Runnable() {
            @Override
            public void run() {
                for (final int widgetId : appWidgetIds) {
                    updateWidget(context, widgetId);
                }
            }
        }).start();
    }
}
