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
package org.gnucash.android.ui.homescreen

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import org.gnucash.android.R
import org.gnucash.android.app.GnuCashActivity
import org.gnucash.android.app.GnuCashApplication.Companion.getBookPreferences
import org.gnucash.android.databinding.WidgetConfigurationBinding
import org.gnucash.android.db.BookDbHelper
import org.gnucash.android.db.DatabaseHelper
import org.gnucash.android.db.DatabaseHolder
import org.gnucash.android.db.adapter.AccountsDbAdapter
import org.gnucash.android.db.adapter.BooksDbAdapter
import org.gnucash.android.model.Account
import org.gnucash.android.model.Book
import org.gnucash.android.receivers.TransactionAppWidgetProvider
import org.gnucash.android.ui.account.AccountsActivity
import org.gnucash.android.ui.adapter.QualifiedAccountNameAdapter
import org.gnucash.android.ui.common.FormActivity
import org.gnucash.android.ui.common.UxArgument
import org.gnucash.android.ui.passcode.PasscodeHelper.isPasscodeEnabled
import org.gnucash.android.ui.transaction.TransactionsActivity
import timber.log.Timber

/**
 * Activity for configuration which account to display on a widget.
 * The activity is opened each time a widget is added to the homescreen
 *
 * @author Ngewi Fet <ngewif@gmail.com>
 */
class WidgetConfigurationActivity : GnuCashActivity() {
    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private var selectedBookUID: String? = null
    private var selectedAccountUID: String? = null
    private var isHideBalance = false
    private val books = mutableListOf<Book>()
    private lateinit var accountNameAdapter: QualifiedAccountNameAdapter

    private lateinit var binding: WidgetConfigurationBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = WidgetConfigurationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val context: Context = this
        val booksDbAdapter = BooksDbAdapter.instance
        val allBooks = booksDbAdapter.allRecords
        books.clear()
        books.addAll(allBooks)

        val booksAdapter =
            ArrayAdapter<Book>(context, android.R.layout.simple_spinner_item, allBooks)
        booksAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.inputBooksSpinner.adapter = booksAdapter

        accountNameAdapter = QualifiedAccountNameAdapter(context, this)
        binding.inputAccountsSpinner.adapter = accountNameAdapter

        binding.inputHideAccountBalance.isChecked = isPasscodeEnabled(this)

        bindListeners()
        handleIntent(intent)
    }

    /**
     * Sets click listeners for the buttons in the dialog
     */
    private fun bindListeners() {
        binding.inputBooksSpinner.onItemSelectedListener = object :
            AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>,
                view: View?,
                position: Int,
                id: Long
            ) {
                if (view == null) return
                val context = view.context
                val book = books[position]
                val holder = DatabaseHelper(context, book.uid).holder
                accountNameAdapter.swapAdapter(AccountsDbAdapter(holder))
                selectedBookUID = book.uid
            }

            override fun onNothingSelected(parent: AdapterView<*>) = Unit
        }

        binding.inputAccountsSpinner.onItemSelectedListener = object :
            AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>,
                view: View?,
                position: Int,
                id: Long
            ) {
                if (view == null) return
                selectedAccountUID = accountNameAdapter.getUID(position)
            }

            override fun onNothingSelected(parent: AdapterView<*>) = Unit
        }

        binding.inputHideAccountBalance.setOnCheckedChangeListener { _, isChecked ->
            isHideBalance = isChecked
        }

        binding.defaultButtons.btnSave.setOnClickListener { view ->
            val appWidgetId = appWidgetId
            if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
                finish()
                return@setOnClickListener
            }

            val bookUID = selectedBookUID
            val accountUID = selectedAccountUID
            val hideAccountBalance = isHideBalance

            val context: Context = view.context
            configureWidget(context, appWidgetId, bookUID, accountUID, hideAccountBalance)
            updateWidget(context, appWidgetId)

            val resultValue = Intent()
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            setResult(RESULT_OK, resultValue)
            finish()
        }

        binding.defaultButtons.btnCancel.setOnClickListener {
            setResult(RESULT_CANCELED)
            finish()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

        val extras = intent.extras
        if (extras != null) {
            appWidgetId = extras.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, this.appWidgetId)
        }

        val context: Context = this
        val preferences = context.getSharedPreferences(PREFS_PREFIX + appWidgetId, MODE_PRIVATE)
        val bookUID = preferences.getString(UxArgument.BOOK_UID, null)
        val accountUID = preferences.getString(UxArgument.SELECTED_ACCOUNT_UID, null)
        val hideAccountBalance =
            preferences.getBoolean(UxArgument.HIDE_ACCOUNT_BALANCE_IN_WIDGET, false)

        this.appWidgetId = appWidgetId
        selectedBookUID = bookUID
        selectedAccountUID = accountUID
        isHideBalance = hideAccountBalance

        //determine the position of the book
        var bookIndex = -1
        val booksCount = books.size
        for (i in 0 until booksCount) {
            val book = books[i]
            if (book.uid == bookUID || book.isActive) {
                bookIndex = i
                break
            }
        }

        val accountIndex = accountNameAdapter.getPosition(accountUID)

        binding.inputBooksSpinner.setSelection(bookIndex)
        binding.inputAccountsSpinner.setSelection(accountIndex)

        binding.inputHideAccountBalance.isChecked = hideAccountBalance
    }

    companion object {
        private const val PREFS_PREFIX = "widget:"
        private const val FLAGS_UPDATE =
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

        /**
         * Configure a given widget with the given parameters.
         *
         * @param context            The current context
         * @param appWidgetId        ID of the widget to configure
         * @param bookUID            UID of the book for this widget
         * @param accountUID         UID of the account for this widget
         * @param hideAccountBalance `true` if the account balance should be hidden,
         * `false` otherwise
         */
        fun configureWidget(
            context: Context,
            appWidgetId: Int,
            bookUID: String?,
            accountUID: String?,
            hideAccountBalance: Boolean
        ) {
            context.getSharedPreferences(PREFS_PREFIX + appWidgetId, MODE_PRIVATE).edit {
                putString(UxArgument.BOOK_UID, bookUID)
                putString(UxArgument.SELECTED_ACCOUNT_UID, accountUID)
                putBoolean(UxArgument.HIDE_ACCOUNT_BALANCE_IN_WIDGET, hideAccountBalance)
            }
        }

        /**
         * Remove the configuration for a widget. Primarily this should be called when a widget is
         * destroyed.
         *
         * @param context     The current context
         * @param appWidgetId ID of the widget whose configuration should be removed
         */
        fun removeWidgetConfiguration(context: Context, appWidgetId: Int) {
            context.getSharedPreferences(PREFS_PREFIX + appWidgetId, MODE_PRIVATE).edit {
                clear()
            }
        }

        /**
         * Updates the widget with id `appWidgetId` with information from the
         * account with record ID `accountId`
         * If the account has been deleted, then a notice is posted in the widget
         *
         * @param appWidgetId ID of the widget to be updated
         */
        fun updateWidget(context: Context, appWidgetId: Int) {
            Timber.i("Updating widget: %s", appWidgetId)
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val views =
                RemoteViews(context.packageName, R.layout.widget_4x1)

            val preferences = context.getSharedPreferences(PREFS_PREFIX + appWidgetId, MODE_PRIVATE)
            val bookUID = preferences.getString(UxArgument.BOOK_UID, null)
            val accountUID = preferences.getString(UxArgument.SELECTED_ACCOUNT_UID, null)
            val hideAccountBalance =
                preferences.getBoolean(UxArgument.HIDE_ACCOUNT_BALANCE_IN_WIDGET, false)

            if (bookUID.isNullOrEmpty() || accountUID.isNullOrEmpty()) {
                appWidgetManager.updateAppWidget(appWidgetId, views)
                return
            }

            val holder = DatabaseHolder(context, BookDbHelper.getDatabase(bookUID), bookUID)
            val accountsDbAdapter = AccountsDbAdapter(holder)

            var account: Account? = null
            try {
                account = accountsDbAdapter.getSimpleRecord(accountUID)
            } catch (e: IllegalArgumentException) {
                Timber.e(e, "Account not found, resetting widget")
            }
            if (account == null) {
                accountsDbAdapter.closeQuietly()

                //if account has been deleted, let the user know
                views.setTextViewText(
                    R.id.account_name,
                    context.getString(R.string.toast_account_deleted)
                )
                views.setTextViewText(R.id.transactions_summary, "")
                //set it to simply open the app
                val pendingIntent = PendingIntent.getActivity(
                    context, 0,
                    Intent(context, AccountsActivity::class.java), FLAGS_UPDATE
                )
                views.setOnClickPendingIntent(R.id.widget_layout, pendingIntent)
                views.setOnClickPendingIntent(R.id.btn_new_transaction, pendingIntent)
                appWidgetManager.updateAppWidget(appWidgetId, views)

                getBookPreferences(context).edit {
                    remove(UxArgument.SELECTED_ACCOUNT_UID + appWidgetId)
                }
                return
            }

            views.setTextViewText(R.id.account_name, account.name)

            if (hideAccountBalance) {
                views.setViewVisibility(R.id.transactions_summary, View.GONE)
            } else {
                val accountBalance = accountsDbAdapter.getCurrentAccountBalance(account)
                views.setTextViewText(
                    R.id.transactions_summary,
                    accountBalance.formattedString()
                )
                val color =
                    if (accountBalance.isNegative) R.color.debit_red else R.color.credit_green
                views.setTextColor(
                    R.id.transactions_summary,
                    ContextCompat.getColor(context, color)
                )
            }

            val accountViewIntent = Intent(context, TransactionsActivity::class.java)
                .setAction(Intent.ACTION_VIEW)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                .putExtra(UxArgument.SELECTED_ACCOUNT_UID, accountUID)
                .putExtra(UxArgument.BOOK_UID, bookUID)
            val accountPendingIntent = PendingIntent
                .getActivity(context, appWidgetId, accountViewIntent, FLAGS_UPDATE)
            views.setOnClickPendingIntent(R.id.widget_layout, accountPendingIntent)

            if (accountsDbAdapter.isPlaceholderAccount(accountUID)) {
                views.setViewVisibility(R.id.btn_new_transaction, View.GONE)
            } else {
                val newTransactionIntent = Intent(context, FormActivity::class.java)
                    .setAction(Intent.ACTION_INSERT_OR_EDIT)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .putExtra(UxArgument.FORM_TYPE, FormActivity.FormType.TRANSACTION.name)
                    .putExtra(UxArgument.BOOK_UID, bookUID)
                    .putExtra(UxArgument.SELECTED_ACCOUNT_UID, accountUID)
                val pendingIntent = PendingIntent
                    .getActivity(context, appWidgetId, newTransactionIntent, FLAGS_UPDATE)
                views.setOnClickPendingIntent(R.id.btn_new_transaction, pendingIntent)
            }

            accountsDbAdapter.closeQuietly()
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        /**
         * Updates all widgets belonging to the application
         *
         * @param context Application context
         */
        fun updateAllWidgets(context: Context) {
            Timber.i("Updating all widgets")
            val widgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, TransactionAppWidgetProvider::class.java)
            val appWidgetIds = widgetManager.getAppWidgetIds(componentName)

            //update widgets asynchronously so as not to block method which called the update
            //inside the computation of the account balance
            Thread {
                for (widgetId in appWidgetIds) {
                    updateWidget(context, widgetId)
                }
            }.start()
        }
    }
}
