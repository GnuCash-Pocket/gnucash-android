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
import android.widget.TextView
import androidx.appcompat.app.ActionBar
import androidx.core.view.isVisible
import androidx.core.widget.addTextChangedListener
import androidx.cursoradapter.widget.SimpleCursorAdapter
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
import org.gnucash.android.model.BaseModel.Companion.isNew
import org.gnucash.android.model.BaseModel.Companion.markNew
import org.gnucash.android.model.Money
import org.gnucash.android.model.ScheduledAction
import org.gnucash.android.model.Split
import org.gnucash.android.model.Transaction
import org.gnucash.android.model.TransactionType
import org.gnucash.android.service.ScheduledActionService
import org.gnucash.android.ui.adapter.QualifiedAccountNameAdapter
import org.gnucash.android.ui.common.FormActivity
import org.gnucash.android.ui.common.UxArgument
import org.gnucash.android.ui.homescreen.WidgetConfigurationActivity.Companion.updateAllWidgets
import org.gnucash.android.ui.snackLong
import org.gnucash.android.ui.snackShort
import org.gnucash.android.ui.transaction.dialog.TransferFundsDialogFragment
import org.gnucash.android.ui.util.RecurrenceParser
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
    private var transaction: Transaction = Transaction("")

    /**
     * Flag to note if double entry accounting is in use or not
     */
    private var useDoubleEntry = false

    /**
     * The Account of the account to which this transaction belongs.
     * Used for determining the accounting rules for credits and debits
     */
    private var account: Account? = null

    private var scheduledAction = ScheduledAction(ScheduledAction.ActionType.TRANSACTION)

    private var rootAccountUID: String? = null

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
        val account = requireAccount()

        val actionBar: ActionBar? = this.actionBar
        if (transaction.id != 0L) {
            actionBar?.setTitle(R.string.title_edit_transaction)
        } else {
            actionBar?.setTitle(R.string.title_add_transaction)
        }

        val binding = this.binding!!
        setListeners(binding, account)

        //updateTransferAccountsList must only be called after initializing accountsDbAdapter
        updateTransferAccountsList(binding, account)
        bind(binding, account)

        val transaction = transaction
        if (transaction.isNew) {
            bindTransactionNameAutocomplete(binding)
        }
        bind(binding, transaction, true)
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
        transactionsDbAdapter = accountsDbAdapter.transactionsDbAdapter
        pricesDbAdapter = accountsDbAdapter.pricesDbAdapter
        scheduledActionDbAdapter = ScheduledActionDbAdapter.instance

        rootAccountUID = accountsDbAdapter.rootAccountUID
        val account = requireAccount()
        this.account = account

        val transactionUID = args.getString(UxArgument.SELECTED_TRANSACTION_UID)
        var transaction = this.transaction
        if (!transactionUID.isNullOrEmpty()) {
            val record = transactionsDbAdapter.getRecordOrNull(transactionUID)
            if (record != null) {
                transaction = record
                this.transaction = transaction

                var scheduledActionUID = args.getString(UxArgument.SCHEDULED_ACTION_UID)
                if (scheduledActionUID.isNullOrEmpty()) {
                    scheduledActionUID = transaction.scheduledActionUID
                }
                if (!scheduledActionUID.isNullOrEmpty()) {
                    val scheduledAction = scheduledActionDbAdapter.getRecord(scheduledActionUID)
                    this.scheduledAction = scheduledAction
                    setRecurrence(scheduledAction.ruleString, false)
                }
            }
        }

        if (transaction.isNew) {
            transaction.commodity = account.commodity

            val transferAccount = accountsDbAdapter.getDefaultTransferAccount(account)
                ?: accountsDbAdapter.getOrCreateImbalanceAccount(context, account.commodity)
            val amount = Money.createZeroInstance(account.commodity)
            val split1 = Split(amount, account)
            split1.type = getDefaultTransactionType(context)
            val split2 = Split(amount, amount, transferAccount)
            split2.type = !split1.type
            transaction.splits = listOf(split1, split2)
            transaction.isTemplate = account.isTemplate

            scheduledAction.markNew()
        }
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
            val balance = transactionsDbAdapter.getBalance(transactionUID, accountUID, true)

            val timestamp =
                cursor.getLong(cursor.getColumnIndexOrThrow(TransactionEntry.COLUMN_DATE_POSTED))
            val dateString = DateUtils.formatDateTime(
                view.context,
                timestamp,
                DateUtils.FORMAT_SHOW_WEEKDAY or DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_YEAR
            )

            val secondaryTextView = view.findViewById<TextView>(R.id.secondary_text)
            //TODO: Extract string
            secondaryTextView.text = "${balance.formattedString()} on $dateString"
        }
    }

    /**
     * Initializes the transaction name field for autocompletion with existing transaction names in the database
     */
    private fun bindTransactionNameAutocomplete(binding: FragmentTransactionFormBinding) {
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
                //we are creating a new transaction after all
                val transaction = transactionDb.copy(datePosted = System.currentTimeMillis())
                //we check here because next method will modify it and we want to catch user-modification
                val amountEntered = binding.inputTransactionAmount.value
                val amountModified = binding.inputTransactionAmount.isInputModified
                bind(binding, transaction, false)
                val splits = transaction.splits
                val isSplitPair = splits.size == 2 && splits[0].isPairOf(splits[1])
                if (isSplitPair) {
                    // FIXME transaction.splits = emptyList()
                    if (amountModified) { //if user already entered an amount
                        binding.inputTransactionAmount.value = amountEntered
                    } else {
                        binding.inputTransactionAmount.value = splits[0].value.toBigDecimal()
                    }
                } else {
                    // if user entered own amount, clear loaded splits and use the user value
                    if (amountModified) {
                        transaction.splits = emptyList()
                        setDoubleEntryViewsVisibility(binding, true)
                    } else {
                        // don't hide the view in single entry mode
                        if (useDoubleEntry) {
                            setDoubleEntryViewsVisibility(binding, false)
                            binding.btnSplitEditor.isVisible = true
                        }
                    }
                }
            }

        binding.inputTransactionName.setAdapter(adapter)
    }

    /**
     * Initialize views in the fragment with information from a transaction.
     * This method is called if the fragment is used for editing a transaction
     */
    private fun bind(
        binding: FragmentTransactionFormBinding,
        transaction: Transaction,
        isFirst: Boolean
    ) {
        val account = requireAccount()
        binding.inputTransactionName.setTextToEnd(transaction.description)

        //when autocompleting, only change the amount if the user has not manually changed it already
        binding.currencySymbol.text = transaction.commodity.symbol
        binding.notes.setText(transaction.notes)
        binding.number.setText(transaction.number)
        binding.inputDate.text = DATE_FORMATTER.print(transaction.datePosted)
        binding.inputTime.text = TIME_FORMATTER.print(transaction.datePosted)

        bindSplits(binding, account, transaction.splits, isFirst)

        val accountCommodity = account.commodity
        binding.currencySymbol.text = accountCommodity.symbol
        binding.inputTransactionAmount.commodity = accountCommodity

        bind(binding, transaction, scheduledAction)
    }

    private fun bindSplits(
        binding: FragmentTransactionFormBinding,
        account: Account,
        splits: List<Split>,
        isFirst: Boolean
    ) {
        val context = binding.root.context
        val accountUID = account.uid
        val accountCommodity = account.commodity
        var transactionType = getDefaultTransactionType(context)

        val splitsSize = splits.size
        toggleAmountInputEntryMode(binding, splitsSize <= 2)
        binding.inputTransactionType.isVisible = (splitsSize <= 2) && !isSplitEditorUsed(binding)

        splitValue = null
        splitQuantity = null
        if (splitsSize == 2) {
            for (split in splits) {
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
        if (splitsSize == 2 && splits[0].isPairOf(splits[1])) {
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

        val balance = Transaction.computeBalance(account, splits, true)
        val amount: BigDecimal? = if (isFirst) {
            if (balance.isZero) null else balance.toBigDecimal()
        } else {
            balance.toBigDecimal()
        }
        binding.inputTransactionAmount.setValue(amount, isFirst)
        binding.inputTransactionType.accountType = account.type
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
    private fun bind(binding: FragmentTransactionFormBinding, account: Account) {
        val context = binding.root.context

        val now = Calendar.getInstance()
        binding.inputDate.text = DATE_FORMATTER.print(now.timeInMillis)
        binding.inputTime.text = TIME_FORMATTER.print(now.timeInMillis)

        val transactionType = getDefaultTransactionType(context)
        binding.inputTransactionType.accountType = account.type
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
            val split = transaction.getTransferSplit(accountUID)
            if (split != null) {
                transferUID = split.accountUID
            }
            setSelectedTransferAccount(binding, transferUID)
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
        if (transaction.isNew) { //if we are creating a new transaction (not editing an existing one)
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
    private fun setListeners(binding: FragmentTransactionFormBinding, account: Account) {
        binding.inputTransactionName.addTextChangedListener {
            transaction.description = it.toString()
        }
        binding.notes.addTextChangedListener {
            transaction.notes = it.toString()
        }
        binding.number.addTextChangedListener {
            transaction.number = it.toString()
        }

        binding.btnSplitEditor.setOnClickListener { openSplitEditor(binding) }

        binding.inputTransactionAmount.addValueChangedListener { _ ->
            transaction.splits = extractSplitsFromView(binding, account)
        }
        binding.inputTransactionType.setAmountFormattingListener(
            binding.inputTransactionAmount,
            binding.currencySymbol
        )

        binding.inputDate.setOnClickListener {
            val timeMillis = transaction.datePosted
            DatePickerDialogFragment.newInstance(timeMillis) { _, year, month, dayOfMonth ->
                val date = Calendar.getInstance().apply {
                    timeInMillis = timeMillis
                    set(Calendar.YEAR, year)
                    set(Calendar.MONTH, month)
                    set(Calendar.DAY_OF_MONTH, dayOfMonth)
                }
                transaction.datePosted = date.timeInMillis
                binding.inputDate.text = DATE_FORMATTER.print(date.timeInMillis)
            }
                .show(parentFragmentManager, "date_picker_dialog")
        }

        binding.inputTime.setOnClickListener {
            val timeMillis = transaction.datePosted
            TimePickerDialogFragment.newInstance(timeMillis) { _, hourOfDay, minute ->
                val date = Calendar.getInstance().apply {
                    timeInMillis = timeMillis
                    set(Calendar.HOUR_OF_DAY, hourOfDay)
                    set(Calendar.MINUTE, minute)
                }
                transaction.datePosted = date.timeInMillis
                binding.inputTime.text = TIME_FORMATTER.print(date.timeInMillis)
            }
                .show(parentFragmentManager, "time_picker_dialog")
        }

        binding.inputRecurrence.setOnClickListener {
            RecurrenceViewClickListener(
                parentFragmentManager,
                scheduledAction.ruleString,
                this@TransactionFormFragment
            ).onClick(it)
        }
        binding.recurrenceAuto.setOnCheckedChangeListener { _, checked ->
            scheduledAction.isAutoCreate = checked
        }

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

                val splits = transaction.splits
                if (splits.size == 2) { //when handling simple transfer to one account
                    val account = requireAccount()
                    val accountUID = account.uid
                    for (split in splits) {
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
    private fun extractSplitsFromView(
        binding: FragmentTransactionFormBinding,
        account: Account
    ): List<Split> {
        val splits = transaction.splits
        if (isSplitEditorUsed(binding)) {
            return splits
        }

        val enteredAmount = binding.inputTransactionAmount.value ?: BigDecimal.ZERO
        val accountUID = account.uid
        val accountCommodity = account.commodity
        val value = Money(enteredAmount, accountCommodity)
        var quantity = Money(value)

        val transferAccount = getTransferAccount(binding) ?: return splits
        val transferAccountUID = transferAccount.uid

        if (isMultiCurrencyTransaction(binding, account)) { //if multi-currency transaction
            val targetCommodity = transferAccount.commodity

            if (splitQuantity != null && (value == splitValue)) {
                quantity = splitQuantity!!
            } else {
                val price = pricesDbAdapter.getPrice(accountCommodity, targetCommodity)
                quantity *= price
            }
        }

        val split1: Split
        val split2: Split
        // Try to preserve the other split attributes.
        if (splits.size >= 2) {
            split1 = splits[0]
            split1.value = value
            split1.quantity = value
            split1.accountUID = accountUID

            split2 = splits[1]
            split2.value = value
            split2.quantity = quantity
            split2.accountUID = transferAccountUID
        } else {
            split1 = Split(value, account)
            split2 = Split(value, quantity, transferAccount)
        }
        split1.type = binding.inputTransactionType.transactionType
        split2.type = !split1.type

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
     * Checks whether the split editor has been used for editing this transaction.
     *
     * @return `true` if split editor was used, `false` otherwise
     */
    private fun isSplitEditorUsed(binding: FragmentTransactionFormBinding): Boolean {
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
    private fun isMultiCurrencyTransaction(
        binding: FragmentTransactionFormBinding,
        account: Account
    ): Boolean {
        if (!useDoubleEntry) return false

        val accountCommodity = account.commodity

        val splits = transaction.splits
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
    private fun saveTransaction(binding: FragmentTransactionFormBinding) {
        binding.inputTransactionAmount.error = null

        //determine whether we need to do currency conversion
        val account = requireAccount()
        if (isMultiCurrencyTransaction(binding, account) &&
            !isSplitEditorUsed(binding) &&
            !onSaveAttempt
        ) {
            onSaveAttempt = true
            startTransferFunds(binding)
            return
        }

        val transaction = this.transaction
        val scheduledAction = this.scheduledAction
        val isRecurring = !scheduledAction.isEmpty()

        try {
            if (transaction.isNew) {
                if (isRecurring) {
                    saveNewRecur(transaction, scheduledAction)
                } else {
                    saveNewRegular(transaction)
                }
            } else if (isRecurring) {
                if (transaction.isTemplate) {
                    saveOldRecur(transaction, scheduledAction)
                } else if (transaction.scheduledActionUID == scheduledAction.uid) {
                    if (transaction.uid == scheduledAction.actionUID) {
                        saveOldRecur(transaction, scheduledAction)
                    } else {
                        saveOldRegular(transaction)
                    }
                } else {
                    saveNewRecur(transaction, scheduledAction)
                }
            } else {
                saveOldRegular(transaction)
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
        if (canSave(binding)) {
            saveTransaction(binding)
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

        return when (item.itemId) {
            android.R.id.home -> {
                finish(Activity.RESULT_CANCELED)
                true
            }

            R.id.menu_save -> {
                maybeSaveTransaction(binding)
                true
            }

            else -> super.onOptionsItemSelected(item)
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
        transaction.splits = splits
        binding.inputTransactionType.isVisible = false
        bindSplits(binding, account, splits, false)
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

    override fun transferComplete(value: Money, amount: Money) {
        splitValue = value
        splitQuantity = amount

        val binding = this.binding ?: return
        // The converted quantity is only known now, so rebuild the splits with it.
        transaction.splits = extractSplitsFromView(binding, requireAccount())

        //The transfer dialog was called while attempting to save. So try saving again
        if (onSaveAttempt) {
            saveTransaction(binding)
        }
        onSaveAttempt = false
    }

    override fun onRecurrenceSet(rrule: String?) {
        setRecurrence(rrule, true)
    }

    private fun setRecurrence(rrule: String?, isUser: Boolean = true) {
        Timber.i("TX reoccurs: %s", rrule)
        if (rrule.isNullOrEmpty()) {
            scheduledAction = ScheduledAction(ScheduledAction.ActionType.TRANSACTION)
        } else {
            try {
                val recurrence = parse(rrule)
                scheduledAction.setRecurrence(recurrence)

                // Instances should not change their schedules - only the owner template.
                // Convert the regular transaction to a template.
                if (isUser && !transaction.isNew && !transaction.isTemplate) {
                    transaction = transaction.copy().apply {
                        isTemplate = true
                        scheduledAction.actionUID = uid
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Bad recurrence for [%s]", rrule)
            }
        }

        if (isUser) {
            val binding = this.binding ?: return
            bind(binding, transaction, scheduledAction)
        }
    }

    private fun bind(
        binding: FragmentTransactionFormBinding,
        transaction: Transaction,
        scheduledAction: ScheduledAction
    ) {
        val context = binding.inputRecurrence.context
        val repeatString = RecurrenceParser.format(context, scheduledAction.recurrence)
            ?: context.getString(R.string.label_tap_to_create_schedule)
        binding.inputRecurrence.text = repeatString
        binding.inputRecurrence.isEnabled = (transaction.isNew || transaction.isTemplate)

        binding.recurrenceAutoRow.isVisible =
            !scheduledAction.isEmpty() && (transaction.isNew || transaction.isTemplate)
        binding.recurrenceAuto.isChecked = scheduledAction.isAutoCreate
    }

    @Deprecated("Deprecated in Java")
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
            throw IllegalArgumentException("Account required")
        }
        return account
    }

    private fun saveNewRegular(transaction: Transaction) {
        val transaction = transaction.copy().apply {
            isTemplate = false
            scheduledActionUID = null
            splits.forEach { split ->
                split.scheduledActionAccountUID = null
            }
        }
        saveToDb(transaction)
    }

    private fun saveOldRegular(transaction: Transaction) {
        // If transaction was recurring then maybe delete its old schedule.
        val oldScheduledActionUID = transaction.scheduledActionUID

        transaction.apply {
            isTemplate = false
            scheduledActionUID = null
            splits.forEach { split ->
                split.scheduledActionAccountUID = null
            }
        }
        saveToDb(transaction)
        maybeDeleteSchedule(transaction.uid, oldScheduledActionUID)
    }

    private fun saveNewRecur(transaction: Transaction, scheduledAction: ScheduledAction) {
        val transaction = transaction.copy().apply {
            isTemplate = true
        }

        scheduledAction.apply {
            markNew()
            name = transaction.description
            startDate = transaction.datePosted
            actionUID = transaction.uid
            instanceCount = 1
        }
        scheduledActionDbAdapter.insert(scheduledAction)

        transaction.scheduledActionUID = scheduledAction.uid
        for (split in transaction.splits) {
            split.scheduledActionAccountUID = split.accountUID
        }
        saveToDb(transaction)
        maybeSchedule(scheduledAction)
    }

    private fun saveOldRecur(transaction: Transaction, scheduledAction: ScheduledAction) {
        scheduledAction.apply {
            name = transaction.description
            startDate = transaction.datePosted
            actionUID = transaction.uid
            instanceCount = scheduledAction.instanceCount.coerceAtLeast(1)
        }
        scheduledActionDbAdapter.replace(scheduledAction)

        transaction.scheduledActionUID = scheduledAction.uid
        for (split in transaction.splits) {
            split.scheduledActionAccountUID = split.accountUID
        }
        saveToDb(transaction)
        maybeSchedule(scheduledAction)
    }

    private fun saveToDb(transaction: Transaction) {
        // 1) Transactions may be existing or non-existing
        // 2) when transaction exists in the db, the splits may exist or not exist in the db
        // So replace is chosen.
        this.transaction = transactionsDbAdapter.replace(transaction)
    }

    private fun maybeSchedule(scheduledAction: ScheduledAction) {
        if (scheduledAction.isAutoCreate) {
            ScheduledActionService.processScheduledAction(
                scheduledActionDbAdapter.holder,
                scheduledAction
            )
            snackLong(R.string.toast_scheduled_recurring_transaction)
        }
    }

    /**
     * Don't leave orphaned scheduled actions after the transaction erased its schedule.
     */
    private fun maybeDeleteSchedule(transactionUID: String, oldScheduledActionUID: String?) {
        if (oldScheduledActionUID.isNullOrEmpty()) return
        val scheduledAction =
            scheduledActionDbAdapter.getRecordOrNull(oldScheduledActionUID) ?: return
        if (scheduledAction.actionType != ScheduledAction.ActionType.TRANSACTION) return
        val actionUID = scheduledAction.actionUID
        if (actionUID.isNullOrEmpty()) return
        // Only if the transaction was the original owner of the scheduled action.
        if (transactionUID != actionUID) return
        try {
            scheduledActionDbAdapter.deleteRecord(scheduledAction)
        } catch (e: Throwable) { // In case the schedule doesn't actually exist.
            Timber.e(e)
        }
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
    }
}
