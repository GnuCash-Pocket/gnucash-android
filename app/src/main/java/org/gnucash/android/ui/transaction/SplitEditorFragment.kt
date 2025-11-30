/*
 * Copyright (c) 2014 - 2016 Ngewi Fet <ngewif@gmail.com>
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
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.EditText
import android.widget.ImageView
import android.widget.Spinner
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.appcompat.app.ActionBar
import androidx.core.view.isVisible
import org.gnucash.android.R
import org.gnucash.android.app.MenuFragment
import org.gnucash.android.app.actionBar
import org.gnucash.android.app.finish
import org.gnucash.android.app.getParcelableArrayListCompat
import org.gnucash.android.databinding.FragmentSplitEditorBinding
import org.gnucash.android.databinding.ItemSplitEntryBinding
import org.gnucash.android.model.Account
import org.gnucash.android.model.BaseModel.Companion.generateUID
import org.gnucash.android.model.Commodity
import org.gnucash.android.model.Money
import org.gnucash.android.model.Split
import org.gnucash.android.model.Transaction.Companion.getTypeForBalance
import org.gnucash.android.ui.adapter.QualifiedAccountNameAdapter
import org.gnucash.android.ui.common.UxArgument
import org.gnucash.android.ui.snackLong
import org.gnucash.android.ui.transaction.dialog.TransferFundsDialogFragment.Companion.getInstance
import org.gnucash.android.ui.util.displayBalance
import org.gnucash.android.ui.util.widget.CalculatorEditText
import org.gnucash.android.ui.util.widget.CalculatorKeyboard.Companion.rebind
import org.gnucash.android.ui.util.widget.TransactionTypeSwitch
import org.gnucash.android.util.AmountParser.evaluate
import timber.log.Timber
import java.math.BigDecimal

/**
 * Dialog for editing the splits in a transaction
 *
 * @author Ngewi Fet <ngewif@gmail.com>
 */
class SplitEditorFragment : MenuFragment() {
    private var accountNameAdapter: QualifiedAccountNameAdapter? = null
    private val splitViewHolders = mutableListOf<SplitViewHolder>()
    private var account: Account? = null

    private var baseAmount: BigDecimal = BigDecimal.ZERO

    private var imbalanceWatcher: BalanceTextWatcher? = null

    /**
     * Flag for checking where the TransferFunds dialog has already been displayed to the user
     */
    private var currencyConversionDone = false

    /**
     * Flag which is set if another action is triggered during a transaction save (which interrupts the save process).
     * Allows the fragment to check and resume the save operation.
     * Primarily used for multi-currency transactions when the currency transfer dialog is opened during save
     */
    private var onSaveAttempt = false
    private val transferAttempt = mutableListOf<SplitViewHolder>()

    private var binding: FragmentSplitEditorBinding? = null

    @ColorInt
    private var colorBalanceZero = Color.TRANSPARENT

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val binding = FragmentSplitEditorBinding.inflate(inflater, container, false)
        this.binding = binding
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val context = view.context

        val actionBar: ActionBar? = this.actionBar
        actionBar?.setTitle(R.string.title_split_editor)

        //we are editing splits for a new transaction.
        // But the user may have already created some splits before. Let's check
        val args = requireArguments()
        baseAmount = BigDecimal(args.getString(UxArgument.AMOUNT_STRING))
        var accountUID = args.getString(UxArgument.SELECTED_ACCOUNT_UID)
        if (accountUID.isNullOrEmpty()) {
            accountUID = args.getString(UxArgument.PARENT_ACCOUNT_UID)
            if (accountUID.isNullOrEmpty()) {
                Timber.e("Account expected!")
                finish()
                return
            }
        }

        val binding = binding!!
        imbalanceWatcher = BalanceTextWatcher(binding)
        colorBalanceZero = binding.imbalanceTextview.currentTextColor

        accountNameAdapter = QualifiedAccountNameAdapter(context, viewLifecycleOwner)
            .load { adapter ->
                account = adapter.getAccountDb(accountUID)
                if (account == null) {
                    Timber.e("Account not found!")
                    finish()
                    return@load
                }
                loadSplits(binding)
            }
    }

    private fun loadSplits(binding: FragmentSplitEditorBinding) {
        splitViewHolders.clear()
        binding.splitListLayout.removeAllViews()

        val splitList: List<Split>? = requireArguments().getParcelableArrayListCompat(
            UxArgument.SPLIT_LIST,
            Split::class.java
        )
        if (splitList.isNullOrEmpty()) {
            val account = this.account!!
            val commodity = account.commodity
            val split = Split(Money(baseAmount, commodity), account.uid)
            val accountType = account.accountType
            val transactionType = getTypeForBalance(accountType, baseAmount.signum() < 0)
            split.type = transactionType
            val splitViewHolder = addSplitView(split)
            val splitViewBinding = splitViewHolder.binding
            splitViewBinding.inputAccountsSpinner.isEnabled = false
            splitViewBinding.btnRemoveSplit.isVisible = false
            binding.imbalanceTextview.displayBalance(
                Money(-baseAmount, commodity),
                colorBalanceZero
            )
        } else {
            //aha! there are some splits. Let's load those instead
            loadSplitViews(splitList)
            imbalanceWatcher?.notifyChanged()
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val binding = binding ?: return
        var keyboardView = binding.calculatorKeyboard.calculatorKeyboard
        keyboardView = rebind(binding.root, keyboardView, null)
        for (viewHolder in splitViewHolders) {
            viewHolder.splitAmountEditText.bindKeyboard(keyboardView)
        }
    }

    private fun loadSplitViews(splits: List<Split>) {
        for (split in splits) {
            addSplitView(split)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.split_editor_actions, menu)
    }

    @Deprecated("Deprecated in Java")
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                val activity = activity
                if (activity == null) {
                    Timber.w("Activity required")
                    return false
                }
                activity.setResult(Activity.RESULT_CANCELED)
                activity.finish()
                return true
            }

            R.id.menu_save -> {
                saveSplits()
                return true
            }

            R.id.menu_add -> {
                addSplitView(null)
                return true
            }

            else -> return super.onOptionsItemSelected(item)
        }
    }

    /**
     * Add a split view and initialize it with `split`
     *
     * @param split Split to initialize the contents to
     * @return Returns the split view which was added
     */
    private fun addSplitView(split: Split?): SplitViewHolder {
        val binding =
            ItemSplitEntryBinding.inflate(layoutInflater, binding!!.splitListLayout, true)
        val splitView = binding.root
        val viewHolder = SplitViewHolder(binding)
        viewHolder.bind(split)
        splitView.tag = viewHolder
        splitViewHolders.add(viewHolder)
        return viewHolder
    }

    /**
     * Holds a split item view and binds the items in it
     */
    internal inner class SplitViewHolder(val binding: ItemSplitEntryBinding) :
        OnTransferFundsListener {
        val itemView = binding.root
        val splitMemoEditText: EditText = binding.inputSplitMemo
        val splitAmountEditText: CalculatorEditText = binding.inputSplitAmount
        val removeSplitButton: ImageView = binding.btnRemoveSplit
        val accountsSpinner: Spinner = binding.inputAccountsSpinner
        val splitCurrencyTextView: TextView = binding.splitCurrencySymbol
        val splitUidTextView: TextView = binding.splitUid
        val splitTypeSwitch: TransactionTypeSwitch = binding.btnSplitType

        var quantity: Money? = null
            private set

        init {
            splitAmountEditText.bindKeyboard(this@SplitEditorFragment.binding!!.calculatorKeyboard)

            removeSplitButton.setOnClickListener {
                val viewHolder = itemView.tag as SplitViewHolder
                this@SplitEditorFragment.binding!!.splitListLayout.removeView(itemView)
                splitViewHolders.remove(viewHolder)
                imbalanceWatcher?.notifyChanged()
            }

            accountsSpinner.onItemSelectedListener = SplitAccountListener(splitTypeSwitch, this)
            splitTypeSwitch.setAmountFormattingListener(splitAmountEditText, splitCurrencyTextView)
            splitTypeSwitch.addOnCheckedChangeListener { _, _ ->
                imbalanceWatcher?.notifyChanged()
            }
            splitAmountEditText.addTextChangedListener(imbalanceWatcher)
        }

        override fun transferComplete(value: Money, amount: Money) {
            currencyConversionDone = true
            quantity = amount

            //The transfer dialog was called while attempting to save. So try saving again
            val viewHolder = this
            transferAttempt.remove(viewHolder)
            if (onSaveAttempt && transferAttempt.isEmpty()) {
                onSaveAttempt = false
                saveSplits()
            }
        }

        /**
         * Returns the value of the amount in the binding.inputSplitAmount field without setting the value to the view
         *
         * If the expression in the view is currently incomplete or invalid, null is returned.
         * This method is used primarily for computing the imbalance
         *
         * @return Value in the split item amount field, or [BigDecimal.ZERO] if the expression is empty or invalid
         */
        val amountValue: BigDecimal
            get() {
                val amountString = splitAmountEditText.cleanString
                val amount = evaluate(amountString)
                return amount ?: BigDecimal.ZERO
            }

        fun bind(split: Split?) {
            if (split != null && split.quantity != split.value) {
                this.quantity = split.quantity
            }

            val accountNameAdapter = accountNameAdapter!!
            accountsSpinner.adapter = accountNameAdapter

            if (split != null) {
                val valueCommodity = split.value.commodity
                splitAmountEditText.commodity = valueCommodity
                val splitAccountUID = split.accountUID
                val account = accountNameAdapter.getAccount(splitAccountUID)
                if (account == null) {
                    Timber.e("Account for split not found")
                    bind(null)
                    return
                }
                splitAmountEditText.setValue(
                    split.getFormattedValue(account).toBigDecimal(),
                    true /* isOriginal */
                )
                splitCurrencyTextView.text = valueCommodity.symbol
                splitMemoEditText.setText(split.memo)
                splitUidTextView.text = split.uid
                setSelectedTransferAccount(splitAccountUID, accountsSpinner)
                splitTypeSwitch.accountType = account.accountType
                splitTypeSwitch.setChecked(split.type)
            } else {
                val account = this@SplitEditorFragment.account
                val commodity = account!!.commodity
                splitCurrencyTextView.text = commodity.symbol
                splitUidTextView.text = generateUID()

                val transferUID = account.defaultTransferAccountUID
                val accountTransfer = transferUID?.let { accountNameAdapter.getAccountDb(it) }
                if (accountTransfer != null) {
                    setSelectedTransferAccount(transferUID, accountsSpinner)
                    splitTypeSwitch.accountType = accountTransfer.accountType
                }
                splitTypeSwitch.isChecked = baseAmount.signum() > 0
            }

            if (splitAmountEditText.isFocused) {
                splitAmountEditText.showKeyboard()
            }
        }
    }

    /**
     * Updates the spinner to the selected transfer account
     *
     * @param accountUID Database ID of the transfer account
     */
    private fun setSelectedTransferAccount(accountUID: String?, inputAccountsSpinner: Spinner) {
        val position = accountNameAdapter?.getPosition(accountUID) ?: Spinner.INVALID_POSITION
        inputAccountsSpinner.setSelection(position)
    }

    /**
     * Check if all the split amounts have valid values that can be saved
     *
     * @return `true` if splits can be saved, `false` otherwise
     */
    private fun canSave(): Boolean {
        for (viewHolder in splitViewHolders) {
            if (!viewHolder.splitAmountEditText.isInputValid) {
                return false
            }
            //TODO: also check that multi-currency splits have a conversion amount present
        }
        return true
    }

    /**
     * Save all the splits from the split editor
     */
    private fun saveSplits() {
        if (!canSave()) {
            snackLong(R.string.toast_error_check_split_amounts)
            return
        }

        if (isMultiCurrencyTransaction && !currencyConversionDone) {
            onSaveAttempt = true
            if (startTransferFunds()) {
                return
            }
        }

        val activity: Activity? = activity
        if (activity == null) {
            Timber.w("Activity required")
            return
        }
        val splits = ArrayList(extractSplitsFromView())
        val data = Intent()
            .putParcelableArrayListExtra(UxArgument.SPLIT_LIST, splits)
        activity.setResult(Activity.RESULT_OK, data)
        activity.finish()
    }

    /**
     * Extracts the input from the views and builds [Split]s to correspond to the input.
     *
     * @return List of [Split]s represented in the view
     */
    private fun extractSplitsFromView(): List<Split> {
        val accountNameAdapter = accountNameAdapter!!
        val splits = mutableListOf<Split>()
        for (viewHolder in splitViewHolders) {
            val enteredAmount = viewHolder.splitAmountEditText.value ?: continue

            var account = this.account
            val valueAmount = Money(enteredAmount.abs(), account!!.commodity)

            val position = viewHolder.accountsSpinner.selectedItemPosition
            account = accountNameAdapter.getAccount(position) ?: continue
            val split = Split(valueAmount, account.uid)
            split.memo = viewHolder.splitMemoEditText.getText().toString()
            split.type = viewHolder.splitTypeSwitch.transactionType
            split.setUID(viewHolder.splitUidTextView.getText().toString())
            splits.add(split)
        }
        return splits
    }

    /**
     * Updates the displayed balance of the accounts when the amount of a split is changed
     */
    private inner class BalanceTextWatcher(private val binding: FragmentSplitEditorBinding) :
        TextWatcher {
        override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) = Unit

        override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) = Unit

        override fun afterTextChanged(editable: Editable) {
            notifyChanged()
        }

        fun notifyChanged() {
            val accountNameAdapter = accountNameAdapter!!
            var imbalance = BigDecimal.ZERO

            for (viewHolder in splitViewHolders) {
                val amount = viewHolder.amountValue.abs()
                val position = viewHolder.accountsSpinner.selectedItemPosition
                if (position < 0) return
                val account = accountNameAdapter.getAccount(position) ?: return
                val hasDebitNormalBalance = account.accountType.hasDebitNormalBalance

                imbalance += if (viewHolder.splitTypeSwitch.isChecked) {
                    if (hasDebitNormalBalance) amount else -amount
                } else {
                    if (hasDebitNormalBalance) -amount else amount
                }
            }

            val account = this@SplitEditorFragment.account!!
            val commodity = account.commodity
            binding.imbalanceTextview.displayBalance(
                Money(imbalance, commodity),
                colorBalanceZero
            )
        }
    }

    /**
     * Listens to changes in the transfer account and updates the currency symbol, the label of the
     * transaction type and if necessary
     */
    private inner class SplitAccountListener(
        private val typeToggleButton: TransactionTypeSwitch,
        private val splitViewHolder: SplitViewHolder
    ) : AdapterView.OnItemSelectedListener {
        /**
         * Flag to know when account spinner callback is due to user interaction or layout of components
         */
        var userInteraction: Boolean = false

        override fun onItemSelected(
            parentView: AdapterView<*>,
            view: View?,
            position: Int,
            id: Long
        ) {
            if (view == null) return
            val accountFrom = this@SplitEditorFragment.account!!

            val accountTo = accountNameAdapter!!.getAccount(position) ?: return
            val accountType = accountTo.accountType
            typeToggleButton.accountType = accountType

            //refresh the imbalance amount if we change the account
            imbalanceWatcher?.notifyChanged()

            val fromCommodity = accountFrom.commodity
            val targetCommodity = accountTo.commodity

            if (!userInteraction || fromCommodity == targetCommodity) {
                //first call is on layout, subsequent calls will be true and transfer will work as usual
                userInteraction = true
                return
            }

            transferAttempt.clear()
            startTransferFunds(fromCommodity, targetCommodity, splitViewHolder)
        }

        override fun onNothingSelected(adapterView: AdapterView<*>?) {
            //nothing to see here, move along
        }
    }

    /**
     * Starts the transfer of funds from one currency to another
     */
    private fun startTransferFunds(
        fromCommodity: Commodity,
        targetCommodity: Commodity,
        splitViewHolder: SplitViewHolder
    ) {
        val enteredAmount = splitViewHolder.splitAmountEditText.value
        if ((enteredAmount == null) || enteredAmount == BigDecimal.ZERO) return

        transferAttempt.add(splitViewHolder)

        val amount = Money(enteredAmount, fromCommodity).abs()
        val fragment = getInstance(amount, targetCommodity, splitViewHolder)
        fragment.show(
            parentFragmentManager,
            "transfer_funds_editor;" + fromCommodity + ";" + targetCommodity + ";" + amount.toPlainString()
        )
    }

    /**
     * Starts the transfer of funds from one currency to another
     */
    private fun startTransferFunds(): Boolean {
        var result = false
        val accountFrom = this.account
        val fromCommodity = accountFrom!!.commodity
        transferAttempt.clear()

        for (viewHolder in splitViewHolders) {
            if (!viewHolder.splitAmountEditText.isInputModified) continue
            val splitQuantity = viewHolder.quantity ?: continue
            val splitCommodity = splitQuantity.commodity
            if (fromCommodity == splitCommodity) continue
            startTransferFunds(fromCommodity, splitCommodity, viewHolder)
            result = true
        }

        return result
    }

    /**
     * Checks if this is a multi-currency transaction being created/edited
     *
     * A multi-currency transaction is one in which the main account and transfer account have different currencies. <br></br>
     * Single-entry transactions cannot be multi-currency
     *
     * @return `true` if multi-currency transaction, `false` otherwise
     */
    private val isMultiCurrencyTransaction: Boolean
        get() {
            val accountFrom = this.account
            val accountCommodity = accountFrom!!.commodity

            val splits: List<Split> = extractSplitsFromView()
            for (split in splits) {
                val splitCommodity = split.quantity.commodity
                if (accountCommodity != splitCommodity) {
                    return true
                }
            }

            return false
        }
}
