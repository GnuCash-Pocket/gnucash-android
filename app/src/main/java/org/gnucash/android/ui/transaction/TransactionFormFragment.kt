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
package org.gnucash.android.ui.transaction

import android.app.Activity
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.database.Cursor
import android.os.Bundle
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.AdapterView
import android.widget.AdapterView.OnItemClickListener
import android.widget.DatePicker
import android.widget.TextView
import android.widget.TimePicker
import androidx.appcompat.app.ActionBar
import androidx.core.view.isVisible
import androidx.cursoradapter.widget.SimpleCursorAdapter
import com.codetroopers.betterpickers.recurrencepicker.EventRecurrence
import com.codetroopers.betterpickers.recurrencepicker.EventRecurrenceFormatter
import com.codetroopers.betterpickers.recurrencepicker.RecurrencePickerDialogFragment.OnRecurrenceSetListener
import org.gnucash.android.R
import org.gnucash.android.app.GnuCashApplication.Companion.getDefaultTransactionType
import org.gnucash.android.app.GnuCashApplication.Companion.isDoubleEntryEnabled
import org.gnucash.android.app.MenuFragment
import org.gnucash.android.app.actionBar
import org.gnucash.android.app.getParcelableArrayListCompat
import org.gnucash.android.databinding.FragmentTransactionFormBinding
import org.gnucash.android.db.DatabaseSchema.AccountEntry
import org.gnucash.android.db.DatabaseSchema.TransactionEntry
import org.gnucash.android.db.adapter.AccountsDbAdapter
import org.gnucash.android.db.adapter.PricesDbAdapter
import org.gnucash.android.db.adapter.ScheduledActionDbAdapter
import org.gnucash.android.db.adapter.TransactionsDbAdapter
import org.gnucash.android.db.getString
import org.gnucash.android.model.Account
import org.gnucash.android.model.AccountType
import org.gnucash.android.model.Money
import org.gnucash.android.model.ScheduledAction
import org.gnucash.android.model.Split
import org.gnucash.android.model.Transaction
import org.gnucash.android.model.TransactionType
import org.gnucash.android.ui.adapter.QualifiedAccountNameAdapter
import org.gnucash.android.ui.common.FormActivity
import org.gnucash.android.ui.common.UxArgument
import org.gnucash.android.ui.homescreen.WidgetConfigurationActivity.Companion.updateAllWidgets
import org.gnucash.android.ui.snackLong
import org.gnucash.android.ui.snackShort
import org.gnucash.android.ui.transaction.dialog.TransferFundsDialogFragment
import org.gnucash.android.ui.util.RecurrenceParser.parse
import org.gnucash.android.ui.util.RecurrenceViewClickListener
import org.gnucash.android.ui.util.dialog.DatePickerDialogFragment
import org.gnucash.android.ui.util.dialog.TimePickerDialogFragment
import org.gnucash.android.ui.util.widget.CalculatorKeyboard.Companion.rebind
import org.gnucash.android.ui.util.widget.setTextToEnd
import org.joda.time.format.DateTimeFormat
import org.joda.time.format.DateTimeFormatter
import timber.log.Timber
import java.math.BigDecimal
import java.util.Calendar

/**
 * Fragment for creating or editing transactions
 *
 * @author Ngewi Fet <ngewif@gmail.com>
 */
class TransactionFormFragment : MenuFragment(),
    DatePickerDialog.OnDateSetListener,
    TimePickerDialog.OnTimeSetListener,
    OnRecurrenceSetListener,
    OnTransferFundsListener {
    /**
     * Transactions database adapter
     */
    private var transactionsDbAdapter = TransactionsDbAdapter.instance

    /**
     * Accounts database adapter
     */
    private var accountsDbAdapter = AccountsDbAdapter.instance
    private var pricesDbAdapter = PricesDbAdapter.instance
    private var scheduledActionDbAdapter = ScheduledActionDbAdapter.instance

    /**
     * Adapter for transfer account spinner
     */
    private var accountTransferNameAdapter: QualifiedAccountNameAdapter? = null

    /**
     * Transaction to be created/updated
     */
    private var transaction: Transaction? = null

    /**
     * Flag to note if double entry accounting is in use or not
     */
    private var useDoubleEntry = false

    /**
     * [Calendar] for holding the set date
     */
    private var date: Calendar = Calendar.getInstance()

    /**
     * The Account of the account to which this transaction belongs.
     * Used for determining the accounting rules for credits and debits
     */
    private var account: Account? = null

    private var recurrenceViewClickListener: RecurrenceViewClickListener? = null
    private var recurrenceRule: String? = null
    private val eventRecurrence = EventRecurrence()

    private var rootAccountUID: String? = null

    private val splitsList = mutableListOf<Split>()

    private var editMode = false

    /**
     * Flag which is set if another action is triggered during a transaction save (which interrrupts the save process).
     * Allows the fragment to check and resume the save operation.
     * Primarily used for multi-currency transactions when the currency transfer dialog is opened during save
     */
    private var onSaveAttempt = false

    /**
     * Split value for the current account.
     */
    private var splitValue: Money? = null

    /**
     * Split quantity for the transfer account.
     */
    private var splitQuantity: Money? = null

    private var binding: FragmentTransactionFormBinding? = null

    /**
     * Create the view and retrieve references to the UI elements
     */
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val binding = FragmentTransactionFormBinding.inflate(inflater, container, false)
        this.binding = binding
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val actionBar: ActionBar? = this.actionBar
        if (editMode) {
            actionBar?.setTitle(R.string.title_edit_transaction)
        } else {
            actionBar?.setTitle(R.string.title_add_transaction)
        }

        val binding = this.binding!!
        setListeners(binding)

        val account = requireAccount()
        //updateTransferAccountsList must only be called after initializing accountsDbAdapter
        updateTransferAccountsList(binding, account)
        initializeViews(binding, account)

        val transaction = transaction
        if (transaction == null) {
            initTransactionNameAutocomplete(binding)
        } else {
            initializeViewsWithTransaction(binding, transaction!!)
        }
    }

    /**
     * Starts the transfer of funds from one currency to another
     */
    private fun startTransferFunds(binding: FragmentTransactionFormBinding) {
        val accountFrom = requireAccount()
        val fromCommodity = accountFrom.commodity
        val position = binding.inputTransferAccountSpinner.selectedItemPosition
        val accountTarget = accountTransferNameAdapter?.getAccount(position) ?: return
        val targetCommodity = accountTarget.commodity

        val enteredAmount = binding.inputTransactionAmount.value
        if ((enteredAmount == null) || enteredAmount == BigDecimal.ZERO) {
            return
        }
        val amount = Money(enteredAmount, fromCommodity).abs()

        //if both accounts have same currency
        if (fromCommodity == targetCommodity) {
            transferComplete(amount, amount)
            return
        }

        if (amount == splitValue
            && (splitQuantity != null)
            && amount != splitQuantity
        ) {
            transferComplete(amount, splitQuantity!!)
            return
        }
        splitValue = null
        splitQuantity = null

        val fragment = TransferFundsDialogFragment.getInstance(amount, targetCommodity, this)
        fragment.show(
            parentFragmentManager,
            "transfer_funds_editor;" + fromCommodity + ";" + targetCommodity + ";" + amount.toPlainString()
        )
        //FIXME if dialog "Cancel"ed, then revert account
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val binding = this.binding ?: return
        val parent: ViewGroup = binding.root
        val keyboardView = binding.calculatorKeyboard.calculatorKeyboard
        rebind(parent, keyboardView, binding.inputTransactionAmount)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val args = requireArguments()
        val context = requireContext()

        useDoubleEntry = isDoubleEntryEnabled(context)

        accountsDbAdapter = AccountsDbAdapter.instance
        pricesDbAdapter = PricesDbAdapter.instance
        scheduledActionDbAdapter = ScheduledActionDbAdapter.instance

        rootAccountUID = accountsDbAdapter.rootAccountUID
        this.account = requireAccount()

        editMode = false

        val transactionUID = args.getString(UxArgument.SELECTED_TRANSACTION_UID)
        transactionsDbAdapter = TransactionsDbAdapter.instance
        var transaction: Transaction? = null
        if (!transactionUID.isNullOrEmpty()) {
            transaction = transactionsDbAdapter.getRecordOrNull(transactionUID)
            if (transaction != null) {
                val scheduledActionUID = args.getString(UxArgument.SCHEDULED_ACTION_UID)
                if (!scheduledActionUID.isNullOrEmpty()) {
                    transaction.scheduledActionUID = scheduledActionUID
                }
            }
        }
        editMode = transaction != null
        this.transaction = transaction
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        requireActivity().window
            .setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
    }

    /**
     * Extension of SimpleCursorAdapter which is used to populate the fields for the list items
     * in the transactions suggestions (auto-complete transaction description).
     */
    private inner class DropDownCursorAdapter(
        context: Context,
        layout: Int,
        c: Cursor?,
        from: Array<String?>?,
        to: IntArray?
    ) : SimpleCursorAdapter(context, layout, c, from, to, 0) {
        override fun bindView(view: View, context: Context, cursor: Cursor) {
            super.bindView(view, context, cursor)
            val account = requireAccount()
            val accountUID = account.uid
            val transactionUID =
                cursor.getString(cursor.getColumnIndexOrThrow(TransactionEntry.COLUMN_UID))
            val balance = transactionsDbAdapter.getBalance(transactionUID, accountUID)

            val timestamp =
                cursor.getLong(cursor.getColumnIndexOrThrow(TransactionEntry.COLUMN_TIMESTAMP))
            val dateString = DateUtils.formatDateTime(
                view.context,
                timestamp,
                DateUtils.FORMAT_SHOW_WEEKDAY or DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_YEAR
            )

            val secondaryTextView = view.findViewById<TextView>(R.id.secondary_text)
            //TODO: Extract string
            secondaryTextView.text =
                "${balance.formattedString()} on $dateString"
        }
    }

    /**
     * Initializes the transaction name field for autocompletion with existing transaction names in the database
     */
    private fun initTransactionNameAutocomplete(binding: FragmentTransactionFormBinding) {
        val from = arrayOf<String?>(TransactionEntry.COLUMN_DESCRIPTION)
        val to = intArrayOf(R.id.primary_text)

        val context = binding.inputTransactionName.context
        val adapter = DropDownCursorAdapter(context, R.layout.dropdown_item_2lines, null, from, to)

        adapter.setCursorToStringConverter { cursor ->
            cursor.getString(TransactionEntry.COLUMN_DESCRIPTION)
        }

        adapter.setFilterQueryProvider { name ->
            val account = requireAccount()
            val accountUID = account.uid
            transactionsDbAdapter.fetchTransactionSuggestions(
                name?.toString().orEmpty(),
                accountUID
            )
        }

        binding.inputTransactionName.onItemClickListener =
            OnItemClickListener { adapterView, view, position, id ->
                val transactionDb = transactionsDbAdapter.getRecord(id)
                val transaction = Transaction(transactionDb, true)
                transaction.time = System.currentTimeMillis()
                //we check here because next method will modify it and we want to catch user-modification
                val amountEntered = binding.inputTransactionAmount.value
                val amountModified = binding.inputTransactionAmount.isInputModified
                initializeViewsWithTransaction(binding, transaction)
                val splits: List<Split> = transaction.splits
                val isSplitPair = splits.size == 2 && splits[0].isPairOf(splits[1])
                if (isSplitPair) {
                    splitsList.clear()
                    if (amountModified) { //if user already entered an amount
                        binding.inputTransactionAmount.value = amountEntered
                    } else {
                        binding.inputTransactionAmount.value = splits[0].value.toBigDecimal()
                    }
                } else {
                    // if user entered own amount, clear loaded splits and use the user value
                    if (amountModified) {
                        splitsList.clear()
                        setDoubleEntryViewsVisibility(binding, true)
                    } else {
                        // don't hide the view in single entry mode
                        if (useDoubleEntry) {
                            setDoubleEntryViewsVisibility(binding, false)
                            binding.btnSplitEditor.isVisible = true
                        }
                    }
                }
                //we are creating a new transaction after all
                this@TransactionFormFragment.transaction = null
            }

        binding.inputTransactionName.setAdapter(adapter)
        recurrenceRule = null
    }

    /**
     * Initialize views in the fragment with information from a transaction.
     * This method is called if the fragment is used for editing a transaction
     */
    private fun initializeViewsWithTransaction(
        binding: FragmentTransactionFormBinding,
        transaction: Transaction
    ) {
        val context = binding.root.context
        val account = requireAccount()
        val accountUID = account.uid
        binding.inputTransactionName.setTextToEnd(transaction.description)

        //when autocompleting, only change the amount if the user has not manually changed it already
        binding.inputTransactionAmount.setValue(
            transaction.getBalance(account).toBigDecimal(),
            !binding.inputTransactionAmount.isInputModified
        )
        binding.currencySymbol.text = transaction.commodity.symbol
        binding.notes.setText(transaction.note)
        binding.inputDate.text = DATE_FORMATTER.print(transaction.time)
        binding.inputTime.text = TIME_FORMATTER.print(transaction.time)
        date = Calendar.getInstance().apply { timeInMillis = transaction.time }

        bindSplits(binding, account, transaction.splits)

        val accountCommodity = account.commodity
        binding.currencySymbol.text = accountCommodity.symbol
        binding.inputTransactionAmount.commodity = accountCommodity

        binding.checkboxSaveTemplate.isChecked = transaction.isTemplate
        val scheduledActionUID = transaction.scheduledActionUID
        if (!scheduledActionUID.isNullOrEmpty()) {
            val scheduledAction = scheduledActionDbAdapter.getRecord(scheduledActionUID)
            onRecurrenceSet(scheduledAction.ruleString)
        }
    }

    private fun bindSplits(
        binding: FragmentTransactionFormBinding,
        account: Account,
        splits: List<Split>
    ) {
        val context = binding.root.context
        val accountUID = account.uid
        val accountCommodity = account.commodity
        var transactionType = getDefaultTransactionType(context)

        splitsList.clear()
        splitsList.addAll(splits)
        val splitsSize = splits.size
        toggleAmountInputEntryMode(binding, splitsSize <= 2)
        binding.inputTransactionType.isVisible = splitsSize <= 2

        splitValue = null
        splitQuantity = null
        if (splitsSize == 2) {
            for (split in splitsList) {
                if (split.accountUID == accountUID) {
                    splitValue = split.value
                    transactionType = split.type
                } else if (split.quantity.commodity != accountCommodity) {
                    splitQuantity = split.quantity
                }
            }
        }
        //if there are more than two splits (which is the default for one entry), then
        //disable editing of the transfer account. User should open editor
        if (splitsSize == 2 && splitsList[0].isPairOf(splitsList[1])) {
            for (split in splits) {
                //two splits, one belongs to this account and the other to another account
                if (useDoubleEntry && split.accountUID != accountUID) {
                    setSelectedTransferAccount(binding, split.accountUID)
                }
            }
        } else {
            setDoubleEntryViewsVisibility(binding, false)
            if (useDoubleEntry && splitsSize >= 2) {
                binding.btnSplitEditor.isVisible = true
            }
            if (splitValue != null) {
                transactionType =
                    if (splitValue!!.isNegative) TransactionType.CREDIT else TransactionType.DEBIT
            }
        }

        val balance = Transaction.computeBalance(account, splits)
        binding.inputTransactionAmount.value = balance.toBigDecimal()
        binding.inputTransactionType.accountType = account.accountType
        binding.inputTransactionType.setChecked(transactionType)
    }

    private fun setDoubleEntryViewsVisibility(
        binding: FragmentTransactionFormBinding,
        visible: Boolean
    ) {
        binding.layoutDoubleEntry.isVisible = visible
        binding.btnSplitEditor.isVisible = visible
    }

    private fun toggleAmountInputEntryMode(
        binding: FragmentTransactionFormBinding,
        enabled: Boolean
    ) {
        if (enabled) {
            binding.inputTransactionAmount.isFocusable = true
            binding.inputTransactionAmount.setOnClickListener(null)
        } else {
            binding.inputTransactionAmount.isFocusable = false
            binding.inputTransactionAmount.setOnClickListener { openSplitEditor(binding) }
        }
    }

    /**
     * Initialize views with default data for new transactions
     */
    private fun initializeViews(binding: FragmentTransactionFormBinding, account: Account) {
        val context = binding.root.context

        val now = Calendar.getInstance()
        binding.inputDate.text = DATE_FORMATTER.print(now.timeInMillis)
        binding.inputTime.text = TIME_FORMATTER.print(now.timeInMillis)
        date = now

        val transactionType = getDefaultTransactionType(context)
        binding.inputTransactionType.accountType = account.accountType
        binding.inputTransactionType.setChecked(transactionType)

        val commodity = account.commodity
        binding.currencySymbol.text = commodity.symbol
        binding.inputTransactionAmount.commodity = commodity
        binding.inputTransactionAmount.bindKeyboard(binding.calculatorKeyboard)

        if (useDoubleEntry) {
            setSelectedTransferAccount(binding, account.defaultTransferAccountUID)
        } else {
            setDoubleEntryViewsVisibility(binding, false)
        }
    }

    /**
     * Updates the list of possible transfer accounts.
     * Only accounts with the same currency can be transferred to
     */
    private fun updateTransferAccountsList(
        binding: FragmentTransactionFormBinding,
        account: Account
    ) {
        val accountUID = account.uid
        val conditions = (AccountEntry.COLUMN_UID + " != ?"
                + " AND " + AccountEntry.COLUMN_TYPE + " != ?"
                + " AND " + AccountEntry.COLUMN_TEMPLATE + " = 0"
                + " AND " + AccountEntry.COLUMN_PLACEHOLDER + " = 0")

        accountTransferNameAdapter = QualifiedAccountNameAdapter(
            binding.root.context,
            conditions,
            arrayOf(accountUID, AccountType.ROOT.name),
            accountsDbAdapter,
            viewLifecycleOwner
        ).load { _ ->
            var transferUID = account.defaultTransferAccountUID
            if (transaction != null) {
                val split = transaction!!.getTransferSplit(accountUID)
                if (split != null) {
                    transferUID = split.accountUID
                }
            }
            setSelectedTransferAccount(binding, transferUID)
            null
        }
        binding.inputTransferAccountSpinner.adapter = accountTransferNameAdapter
    }

    /**
     * Opens the split editor dialog
     */
    private fun openSplitEditor(binding: FragmentTransactionFormBinding) {
        val enteredAmount = binding.inputTransactionAmount.value
        if (enteredAmount == null) {
            snackShort(R.string.toast_enter_amount_to_split)
            binding.inputTransactionAmount.requestFocus()
            binding.inputTransactionAmount.error = getString(R.string.toast_enter_amount_to_split)
            return
        }
        binding.inputTransactionAmount.error = null

        val baseAmountString: String?

        val transaction = this.transaction
        if (transaction == null) { //if we are creating a new transaction (not editing an existing one)
            baseAmountString = enteredAmount.toPlainString()
        } else {
            var biggestAmount = BigDecimal.ZERO
            for (split in transaction.splits) {
                val splitValue = split.value.toBigDecimal()
                if (splitValue > biggestAmount) {
                    biggestAmount = splitValue
                }
            }
            baseAmountString = biggestAmount.toPlainString()
        }

        val context = binding.root.context
        val account = requireAccount()
        val accountUID = account.uid
        val splits = extractSplitsFromView(binding, account)
        val intent = Intent(context, FormActivity::class.java)
            .putExtra(UxArgument.FORM_TYPE, FormActivity.FormType.SPLIT_EDITOR.name)
            .putExtra(UxArgument.SELECTED_ACCOUNT_UID, accountUID)
            .putExtra(UxArgument.AMOUNT_STRING, baseAmountString)
            .putParcelableArrayListExtra(UxArgument.SPLIT_LIST, ArrayList(splits))

        startActivityForResult(intent, REQUEST_SPLIT_EDITOR)
    }

    /**
     * Sets click listeners for the dialog buttons
     */
    private fun setListeners(binding: FragmentTransactionFormBinding) {
        binding.btnSplitEditor.setOnClickListener { openSplitEditor(binding) }

        binding.inputTransactionType.setAmountFormattingListener(
            binding.inputTransactionAmount,
            binding.currencySymbol
        )

        binding.inputDate.setOnClickListener {
            val dateMillis = date.timeInMillis
            DatePickerDialogFragment.newInstance(this@TransactionFormFragment, dateMillis)
                .show(parentFragmentManager, "date_picker_fragment")
        }

        binding.inputTime.setOnClickListener {
            val timeMillis = date.timeInMillis
            TimePickerDialogFragment.newInstance(this@TransactionFormFragment, timeMillis)
                .show(parentFragmentManager, "time_picker_dialog_fragment")
        }

        recurrenceViewClickListener =
            RecurrenceViewClickListener(parentFragmentManager, recurrenceRule, this)
        binding.inputRecurrence.setOnClickListener(recurrenceViewClickListener)

        binding.inputTransferAccountSpinner.onItemSelectedListener = object :
            AdapterView.OnItemSelectedListener {
            /**
             * Flag for ignoring first call to this listener.
             * The first call is during layout, but we want it called only in response to user interaction
             */
            var userInteraction: Boolean = false

            override fun onItemSelected(
                adapterView: AdapterView<*>,
                view: View?,
                position: Int,
                id: Long
            ) {
                if (view == null) return
                removeFavoriteIconFromSelectedView(view as TextView)
                val transferAccountUID = accountTransferNameAdapter!!.getUID(position)

                if (splitsList.size == 2) { //when handling simple transfer to one account
                    val account = requireAccount()
                    val accountUID = account.uid
                    for (split in splitsList) {
                        if (split.accountUID != accountUID) {
                            split.accountUID = transferAccountUID
                        }
                        // else case is handled when saving the transactions
                    }
                }
                if (!userInteraction) {
                    userInteraction = true
                    return
                }
                startTransferFunds(binding)
            }

            // Removes the icon from view to avoid visual clutter
            fun removeFavoriteIconFromSelectedView(view: TextView) {
                view.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0)
            }

            override fun onNothingSelected(adapterView: AdapterView<*>) = Unit
        }
    }

    /**
     * Updates the spinner to the selected transfer account
     *
     * @param accountUID UID of the transfer account
     */
    private fun setSelectedTransferAccount(
        binding: FragmentTransactionFormBinding,
        accountUID: String?
    ) {
        val position = accountTransferNameAdapter!!.getPosition(accountUID)
        binding.inputTransferAccountSpinner.setSelection(position)
    }

    /**
     * Returns a list of splits based on the input in the transaction form.
     * This only gets the splits from the simple view, and not those from the Split Editor.
     * If the Split Editor has been used and there is more than one split, then it returns [.splitsList]
     *
     * @return List of splits in the view or [.splitsList] is there are more than 2 splits in the transaction
     */
    private fun extractSplitsFromView(binding: FragmentTransactionFormBinding, account: Account): List<Split> {
        if (splitEditorUsed(binding)) {
            return splitsList
        }

        var enteredAmount = binding.inputTransactionAmount.value
        if (enteredAmount == null) enteredAmount = BigDecimal.ZERO
        val accountUID = account.uid
        val accountCommodity = account.commodity
        val value = Money(enteredAmount, accountCommodity)
        var quantity = Money(value)

        val transferAccount = getTransferAccount(binding) ?: return splitsList
        val transferAccountUID = transferAccount.uid

        if (isMultiCurrencyTransaction(binding)) { //if multi-currency transaction
            val targetCommodity = transferAccount.commodity

            if (splitQuantity != null && (value == splitValue)) {
                quantity = splitQuantity!!
            } else {
                val price = pricesDbAdapter.getPrice(accountCommodity, targetCommodity)
                if (price != null) {
                    quantity *= price
                }
            }
        }

        val split1: Split
        val split2: Split
        // Try to preserve the other split attributes.
        if (splitsList.size >= 2) {
            split1 = splitsList[0]
            split1.value = value
            split1.quantity = value
            split1.accountUID = accountUID

            split2 = splitsList[1]
            split2.value = value
            split2.quantity = quantity
            split2.accountUID = transferAccountUID
        } else {
            split1 = Split(value, accountUID)
            split2 = Split(value, quantity, transferAccountUID)
        }
        split1.type = binding.inputTransactionType.transactionType
        split2.type = binding.inputTransactionType.transactionType.invert()

        return listOf(split1, split2)
    }

    /**
     * Returns the GUID of the currently selected transfer account.
     * If double-entry is disabled, this method returns the GUID of the imbalance account for the currently active account
     *
     * @return GUID of transfer account
     */
    private fun getTransferAccount(binding: FragmentTransactionFormBinding): Account? {
        if (useDoubleEntry) {
            val position = binding.inputTransferAccountSpinner.selectedItemPosition
            return accountTransferNameAdapter!!.getAccount(position)
        }
        val context = binding.root.context
        val account = requireAccount()
        val accountCommodity = account.commodity
        return accountsDbAdapter.getOrCreateImbalanceAccount(context, accountCommodity)
    }

    /**
     * Extracts a transaction from the input in the form fragment
     *
     * @return New transaction object containing all info in the form
     */
    private fun extractTransactionFromView(binding: FragmentTransactionFormBinding): Transaction {
        val description = binding.inputTransactionName.getText().toString()
        val notes = binding.notes.getText().toString()
        val account = requireAccount()
        val accountCommodity = account.commodity

        val splits = extractSplitsFromView(binding, account)

        val transaction = Transaction(description).apply {
            time = date.timeInMillis
            commodity = accountCommodity
            note = notes
            this.splits = splits
            isExported = false //not necessary as exports use timestamps now. Because, legacy
            isTemplate = account.isTemplate || !recurrenceRule.isNullOrEmpty()
        }
        return transaction
    }

    /**
     * Checks whether the split editor has been used for editing this transaction.
     *
     * @return `true` if split editor was used, `false` otherwise
     */
    private fun splitEditorUsed(binding: FragmentTransactionFormBinding): Boolean {
        return !binding.inputTransactionType.isVisible
    }

    /**
     * Checks if this is a multi-currency transaction being created/edited
     *
     * A multi-currency transaction is one in which the main account and transfer account have different currencies. <br></br>
     * Single-entry transactions cannot be multi-currency
     *
     * @return `true` if multi-currency transaction, `false` otherwise
     */
    private fun isMultiCurrencyTransaction(binding: FragmentTransactionFormBinding): Boolean {
        if (!useDoubleEntry) return false

        val account = requireAccount()
        val accountCommodity = account.commodity

        val splits = splitsList
        for (split in splits) {
            val splitCommodity = split.quantity.commodity
            if (accountCommodity != splitCommodity) {
                return true
            }
        }

        val position = binding.inputTransferAccountSpinner.selectedItemPosition
        val transferAccount = accountTransferNameAdapter?.getAccount(position) ?: return false
        val transferCommodity = transferAccount.commodity

        return accountCommodity != transferCommodity
    }

    /**
     * Collects information from the fragment views and uses it to create
     * and save a transaction
     */
    private fun saveNewTransaction(binding: FragmentTransactionFormBinding) {
        binding.inputTransactionAmount.error = null

        //determine whether we need to do currency conversion
        if (isMultiCurrencyTransaction(binding) && !splitEditorUsed(binding) && !onSaveAttempt) {
            onSaveAttempt = true
            startTransferFunds(binding)
            return
        }

        val isTemplate = binding.checkboxSaveTemplate.isChecked
        val transactionOld = transaction
        val transaction = extractTransactionFromView(binding)
        var scheduledActionUID: String? = null

        if (transactionOld != null) { //if editing an existing transaction
            transaction.setUID(transactionOld.uid)
            transaction.isTemplate = transactionOld.isTemplate
            scheduledActionUID = transactionOld.scheduledActionUID
        }
        val wasScheduled = !scheduledActionUID.isNullOrEmpty()

        this.transaction = transaction

        try {
            if (isTemplate) { //template is automatically checked when a transaction is scheduled
                if (editMode && wasScheduled) {
                    transaction.scheduledActionUID = scheduledActionUID
                    scheduleRecurringTransaction(transaction)
                } else { //means it was new transaction, so a new template
                    val templateTransaction = Transaction(transaction)
                    templateTransaction.isTemplate = true
                    transactionsDbAdapter.insert(templateTransaction)
                    scheduleRecurringTransaction(templateTransaction)
                }
            }

            // 1) Transactions may be existing or non-existing
            // 2) when transaction exists in the db, the splits may exist or not exist in the db
            // So replace is chosen.
            transactionsDbAdapter.replace(transaction)

            if (!isTemplate && wasScheduled) { //we were editing a schedule and it was turned off
                scheduledActionDbAdapter.deleteRecord(scheduledActionUID)
            }

            finish(Activity.RESULT_OK)
        } catch (ae: ArithmeticException) {
            Timber.e(ae)
            binding.inputTransactionAmount.error = getString(R.string.error_invalid_amount)
        } catch (e: Throwable) {
            Timber.e(e)
        }
    }

    private fun maybeSaveTransaction(binding: FragmentTransactionFormBinding) {
        val view = binding.root
        if (canSave(binding)) {
            saveNewTransaction(binding)
        } else {
            if (binding.inputTransactionAmount.value == null) {
                snackLong(R.string.toast_transaction_amount_required)
                binding.inputTransactionAmount.requestFocus()
                binding.inputTransactionAmount.error =
                    getString(R.string.toast_transaction_amount_required)
            } else {
                binding.inputTransactionAmount.error = null
            }
            if (useDoubleEntry && binding.inputTransferAccountSpinner.count == 0) {
                snackLong(R.string.toast_disable_double_entry_to_save_transaction)
            }
        }
    }

    /**
     * Schedules a recurring transaction (if necessary) after the transaction has been saved
     *
     * @see .saveNewTransaction
     */
    private fun scheduleRecurringTransaction(transaction: Transaction) {
        val transactionUID = transaction.uid

        val recurrence = parse(eventRecurrence)

        val scheduledAction = ScheduledAction(ScheduledAction.ActionType.TRANSACTION)
        scheduledAction.setRecurrence(recurrence)

        var scheduledActionUID = transaction.scheduledActionUID

        if (!scheduledActionUID.isNullOrEmpty()) { //if we are editing an existing schedule
            if (recurrence == null) {
                scheduledActionDbAdapter.deleteRecord(scheduledActionUID)
                transaction.scheduledActionUID = null
            } else {
                scheduledAction.setUID(scheduledActionUID)
                scheduledActionDbAdapter.updateRecurrenceAttributes(scheduledAction)
                snackLong(R.string.toast_updated_transaction_recurring_schedule)
            }
        } else {
            if (recurrence != null) {
                scheduledAction.actionUID = transactionUID
                scheduledActionDbAdapter.replace(scheduledAction)
                scheduledActionUID = scheduledAction.uid
                transaction.scheduledActionUID = scheduledActionUID
                snackLong(R.string.toast_scheduled_recurring_transaction)
            }
        }
    }


    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }

    @Deprecated("Deprecated in Java")
    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.default_save_actions, menu)
    }

    @Deprecated("Deprecated in Java")
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        //hide the keyboard if it is visible
        val binding = this.binding ?: return false
        val view: View = binding.root
        val context = view.context
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(view.windowToken, 0)

        when (item.itemId) {
            android.R.id.home -> {
                finish(Activity.RESULT_CANCELED)
                return true
            }

            R.id.menu_save -> {
                maybeSaveTransaction(binding)
                return true
            }

            else -> return super.onOptionsItemSelected(item)
        }
    }

    /**
     * Checks if the pre-requisites for saving the transaction are fulfilled
     *
     * The conditions checked are that a valid amount is entered and that a transfer account is set (where applicable)
     *
     * @return `true` if the transaction can be saved, `false` otherwise
     */
    private fun canSave(binding: FragmentTransactionFormBinding): Boolean {
        return (useDoubleEntry && binding.inputTransactionAmount.isInputValid
                && binding.inputTransferAccountSpinner.count > 0)
                || (!useDoubleEntry && binding.inputTransactionAmount.isInputValid)
    }

    /**
     * Called by the split editor fragment to notify of finished editing
     *
     * @param splits List of splits produced in the fragment
     */
    private fun setSplits(binding: FragmentTransactionFormBinding, splits: List<Split>) {
        val account = requireAccount()
        bindSplits(binding, account, splits)
        binding.inputTransactionType.isVisible = false
    }

    /**
     * Finishes the fragment appropriately.
     * Depends on how the fragment was loaded, it might have a backstack or not
     */
    private fun finish(resultCode: Int) {
        val activity = requireActivity()

        if (resultCode == Activity.RESULT_OK) {
            //update widgets, if any
            updateAllWidgets(activity)
        }

        val fm = activity.supportFragmentManager
        if (fm.backStackEntryCount == 0) {
            activity.setResult(resultCode)
            //means we got here directly from the accounts list activity, need to finish
            activity.finish()
        } else {
            //go back to transactions list
            fm.popBackStack()
        }
    }

    override fun onDateSet(view: DatePicker, year: Int, month: Int, dayOfMonth: Int) {
        date.set(Calendar.YEAR, year)
        date.set(Calendar.MONTH, month)
        date.set(Calendar.DAY_OF_MONTH, dayOfMonth)
        val binding = this.binding ?: return
        binding.inputDate.text = DATE_FORMATTER.print(date.timeInMillis)
    }

    override fun onTimeSet(view: TimePicker, hourOfDay: Int, minute: Int) {
        date.set(Calendar.HOUR_OF_DAY, hourOfDay)
        date.set(Calendar.MINUTE, minute)
        val binding = this.binding ?: return
        binding.inputTime.text = TIME_FORMATTER.print(date.timeInMillis)
    }

    override fun transferComplete(value: Money, amount: Money) {
        splitValue = value
        splitQuantity = amount

        //The transfer dialog was called while attempting to save. So try saving again
        if (onSaveAttempt) {
            val binding = this.binding ?: return
            saveNewTransaction(binding)
        }
        onSaveAttempt = false
    }

    override fun onRecurrenceSet(rrule: String?) {
        Timber.i("TX reoccurs: %s", rrule)
        val binding = this.binding ?: return
        val context = binding.inputRecurrence.context
        var repeatString: String? = null
        if (!rrule.isNullOrEmpty()) {
            try {
                eventRecurrence.parse(rrule)
                repeatString = EventRecurrenceFormatter.getRepeatString(
                    context,
                    context.resources,
                    eventRecurrence,
                    true
                )
            } catch (e: Exception) {
                Timber.e(e, "Bad recurrence for [%s]", rrule)
                return
            }

            //when recurrence is set, we will definitely be saving a template
            binding.checkboxSaveTemplate.isChecked = true
            binding.checkboxSaveTemplate.setEnabled(false)
        } else {
            binding.checkboxSaveTemplate.setEnabled(true)
            binding.checkboxSaveTemplate.isChecked = false
        }
        if (repeatString.isNullOrEmpty()) {
            repeatString = context.getString(R.string.label_tap_to_create_schedule)
        }

        binding.inputRecurrence.text = repeatString
        recurrenceRule = rrule
        recurrenceViewClickListener?.setRecurrence(rrule)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (resultCode == Activity.RESULT_OK) {
            val binding = this.binding ?: return
            val splits =
                data?.getParcelableArrayListCompat(UxArgument.SPLIT_LIST, Split::class.java)
                    ?: return
            setSplits(binding, splits)

            //once split editor has been used and saved, only allow editing through it
            toggleAmountInputEntryMode(binding, false)
            setDoubleEntryViewsVisibility(binding, false)
            binding.inputTransactionType.isVisible = false
            binding.btnSplitEditor.isVisible = true
        }
    }

    private fun requireAccount(): Account {
        var account = this.account
        if (account != null) {
            return account
        }
        val args: Bundle = requireArguments()
        val accountUID = args.getString(UxArgument.SELECTED_ACCOUNT_UID, rootAccountUID)!!
        try {
            account = accountsDbAdapter.getRecord(accountUID)
            this.account = account
        } catch (e: IllegalArgumentException) {
            Timber.e(e)
        }
        if (account == null) {
            Timber.e("Account not found")
            finish(Activity.RESULT_CANCELED)
            throw NullPointerException("Account required")
        }
        return account
    }

    companion object {
        private const val REQUEST_SPLIT_EDITOR = 0x11

        /**
         * Formats milliseconds into a date string of the format "dd MMM yyyy" e.g. 18 July 2012
         */
        val DATE_FORMATTER: DateTimeFormatter = DateTimeFormat.mediumDate()

        /**
         * Formats milliseconds to time string of format "HH:mm" e.g. 15:25
         */
        val TIME_FORMATTER: DateTimeFormatter = DateTimeFormat.mediumTime()

        /**
         * Strips formatting from a currency string.
         * All non-digit information is removed, but the sign is preserved.
         *
         * @param s String to be stripped
         * @return Stripped string with all non-digits removed
         */
        fun stripCurrencyFormatting(s: String): String {
            if (s.isEmpty()) return s
            //remove all currency formatting and anything else which is not a number
            val sign = s.trim()[0]
            var stripped = s.trim().replace("\\D*".toRegex(), "")
            if (stripped.isEmpty()) return ""
            if (sign == '+' || sign == '-') {
                stripped = sign + stripped
            }
            return stripped
        }
    }
}
