/*
 * Copyright (c) 2012 - 2014 Ngewi Fet <ngewif@gmail.com>
 * Copyright (c) 2014 Yongxin Wang <fefe.wyx@gmail.com>
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
package org.gnucash.android.ui.account

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.AdapterView
import android.widget.Spinner
import androidx.annotation.ColorInt
import androidx.appcompat.app.ActionBar
import androidx.core.view.isVisible
import androidx.fragment.app.FragmentResultListener
import org.gnucash.android.R
import org.gnucash.android.app.GnuCashApplication.Companion.isDoubleEntryEnabled
import org.gnucash.android.app.MenuFragment
import org.gnucash.android.app.actionBar
import org.gnucash.android.databinding.FragmentAccountFormBinding
import org.gnucash.android.db.DatabaseSchema.AccountEntry
import org.gnucash.android.db.DatabaseSchema.SplitEntry
import org.gnucash.android.db.adapter.AccountsDbAdapter
import org.gnucash.android.db.adapter.DatabaseAdapter
import org.gnucash.android.db.joinIn
import org.gnucash.android.model.Account
import org.gnucash.android.model.AccountType
import org.gnucash.android.model.Commodity
import org.gnucash.android.ui.adapter.AccountTypesAdapter
import org.gnucash.android.ui.adapter.CommoditiesAdapter
import org.gnucash.android.ui.adapter.DefaultItemSelectedListener
import org.gnucash.android.ui.adapter.QualifiedAccountNameAdapter
import org.gnucash.android.ui.colorpicker.ColorPickerDialog
import org.gnucash.android.ui.common.UxArgument
import org.gnucash.android.ui.text.DefaultTextWatcher
import org.gnucash.android.ui.util.widget.setTextToEnd
import timber.log.Timber

/**
 * Fragment used for creating and editing accounts
 *
 * @author Ngewi Fet <ngewif@gmail.com>
 * @author Yongxin Wang <fefe.wyx@gmail.com>
 */
class AccountFormFragment : MenuFragment(), FragmentResultListener {
    /**
     * Accounts database adapter
     */
    private var accountsDbAdapter = AccountsDbAdapter.instance
    private var accountTypesAdapter: AccountTypesAdapter? = null
    private var commoditiesAdapter: CommoditiesAdapter? = null

    /**
     * GUID of the parent account
     * This value is set to the parent account of the transaction being edited or
     * the account in which a new sub-account is being created
     */
    private var parentAccountUID: String? = null
    private var selectedParentAccountUID: String? = null

    /**
     * Account UID of the root account
     */
    private var rootAccountUID: String? = null

    /**
     * Reference to account object which will be created at end of dialog
     */
    private var account: Account? = null

    /**
     * List of all descendant accounts, if we are modifying an account.
     */
    private val descendantAccounts = mutableListOf<Account>()

    /**
     * Adapter for the parent account spinner
     */
    private var parentAccountNameAdapter: QualifiedAccountNameAdapter? = null

    /**
     * Adapter which binds to the spinner for default transfer account
     */
    private var defaultAccountNameAdapter: QualifiedAccountNameAdapter? = null

    /**
     * Flag indicating if double entry transactions are enabled
     */
    private var useDoubleEntry = false

    @ColorInt
    private var selectedColor = Account.DEFAULT_COLOR
    private var selectedDefaultTransferAccount: Account? = null
    private var selectedName = ""
    private var selectedAccountType: AccountType = AccountType.ROOT
    private var selectedCommodity: Commodity = Commodity.DEFAULT_COMMODITY

    private var binding: FragmentAccountFormBinding? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val context = requireContext()
        useDoubleEntry = isDoubleEntryEnabled(context)
        val args = requireArguments()
        val accountUID = args.getString(UxArgument.SELECTED_ACCOUNT_UID)
        parentAccountUID = args.getString(UxArgument.PARENT_ACCOUNT_UID)
        rootAccountUID = accountsDbAdapter.rootAccountUID

        accountsDbAdapter = AccountsDbAdapter.instance
        accountTypesAdapter = AccountTypesAdapter(context)
        val account = accountUID?.let { accountsDbAdapter.getRecordOrNull(it) }
        this.account = account
        if (account != null) {
            parentAccountUID = account.parentUID
        }
        if (parentAccountUID.isNullOrEmpty()) {
            // null parent, set parent as root
            parentAccountUID = rootAccountUID
        }
        selectedParentAccountUID = parentAccountUID
    }

    /**
     * Inflates the dialog view and retrieves references to the dialog elements
     */
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val binding = FragmentAccountFormBinding.inflate(inflater, container, false)
        this.binding = binding
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = this.binding!!
        val context = view.context
        val account = this.account

        binding.inputAccountName.addTextChangedListener(DefaultTextWatcher { s ->
            if (s.isNotEmpty()) {
                binding.nameTextInputLayout.error = null
            }
            selectedName = s.toString()
        })

        binding.inputAccountTypeSpinner.onItemSelectedListener =
            DefaultItemSelectedListener { parent: AdapterView<*>,
                                          view: View?,
                                          position: Int,
                                          id: Long ->
                val accountType =
                    accountTypesAdapter!!.getType(position) ?: return@DefaultItemSelectedListener
                selectedAccountType = accountType
                loadParentAccountList(binding, accountType)
                setParentAccountSelection(binding, parentAccountUID)
            }

        binding.inputParentAccount.isEnabled = false
        binding.inputParentAccount.onItemSelectedListener =
            DefaultItemSelectedListener { parent: AdapterView<*>,
                                          view: View?,
                                          position: Int,
                                          id: Long ->
                selectedParentAccountUID = parentAccountNameAdapter!!.getUID(position)
            }

        binding.checkboxParentAccount.setOnCheckedChangeListener { _, isChecked ->
            binding.inputParentAccount.isEnabled = isChecked
        }

        binding.inputDefaultTransferAccount.isEnabled = false
        binding.inputDefaultTransferAccount.onItemSelectedListener =
            DefaultItemSelectedListener { parent: AdapterView<*>,
                                          view: View?,
                                          position: Int,
                                          id: Long ->
                selectedDefaultTransferAccount = defaultAccountNameAdapter!!.getAccount(position)
            }
        binding.checkboxDefaultTransferAccount.setOnCheckedChangeListener { _, isChecked ->
            binding.inputDefaultTransferAccount.isEnabled = isChecked
        }

        binding.inputColorPicker.setOnClickListener {
            showColorPickerDialog()
        }

        commoditiesAdapter = CommoditiesAdapter(context, viewLifecycleOwner).load { adapter ->
            val commodity = account?.commodity ?: Commodity.DEFAULT_COMMODITY
            val position = adapter.getPosition(commodity)
            binding.inputCurrencySpinner.setSelection(position)
        }
        binding.inputCurrencySpinner.adapter = commoditiesAdapter
        binding.inputCurrencySpinner.onItemSelectedListener =
            DefaultItemSelectedListener { parent: AdapterView<*>,
                                          view: View?,
                                          position: Int,
                                          id: Long ->
                selectedCommodity = commoditiesAdapter!!.getCommodity(position) ?: selectedCommodity
            }

        val actionBar: ActionBar? = this.actionBar
        //need to load the cursor adapters for the spinners before initializing the views
        binding.inputAccountTypeSpinner.adapter = accountTypesAdapter

        loadDefaultTransferAccountList(binding, account)
        if (account != null) {
            actionBar?.setTitle(R.string.title_edit_account)
            initializeViewsWithAccount(binding, account)
            //do not immediately open the keyboard when editing an account
            requireActivity().window
                .setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
        } else {
            actionBar?.setTitle(R.string.title_create_account)
            initializeViews(binding)
        }
    }

    /**
     * Initialize view with the properties of `account`.
     * This is applicable when editing an account
     *
     * @param account Account whose fields are used to populate the form
     */
    private fun initializeViewsWithAccount(binding: FragmentAccountFormBinding, account: Account) {
        selectedName = account.name

        val descendants = accountsDbAdapter.getDescendants(account)
        descendantAccounts.clear()
        descendantAccounts.addAll(descendants)

        setSelectedCurrency(binding, account.commodity)
        setAccountTypeSelection(binding, account.accountType)
        loadParentAccountList(binding, account.accountType)
        setParentAccountSelection(binding, account.parentUID)

        if (accountsDbAdapter.getTransactionMaxSplitNum(account.uid) > 1) {
            //TODO: Allow changing the currency and effecting the change for all transactions without any currency exchange (purely cosmetic change)
            binding.inputCurrencySpinner.isEnabled = false
        }

        binding.inputAccountName.setTextToEnd(account.name)
        binding.inputAccountDescription.setText(account.description)
        binding.notes.setText(account.note)

        if (useDoubleEntry) {
            var defaultTransferAccountUID = account.defaultTransferAccountUID
            if (!defaultTransferAccountUID.isNullOrEmpty()) {
                setDefaultTransferAccountSelection(binding, defaultTransferAccountUID, true)
            } else {
                var parentUID = account.parentUID
                while (!parentUID.isNullOrEmpty()) {
                    val parentAccount = defaultAccountNameAdapter!!.getAccount(parentUID)
                    if (parentAccount == null) break
                    defaultTransferAccountUID = parentAccount.defaultTransferAccountUID
                    if (!defaultTransferAccountUID.isNullOrEmpty()) {
                        setDefaultTransferAccountSelection(binding, parentUID, false)
                        break //we found a parent with default transfer setting
                    }
                    parentUID = parentAccount.parentUID
                }
            }
        }

        binding.placeholderStatus.isChecked = account.isPlaceholder
        binding.favoriteStatus.isChecked = account.isFavorite
        binding.hiddenStatus.isChecked = account.isHidden
        selectedColor = account.color
        binding.inputColorPicker.setBackgroundTintList(ColorStateList.valueOf(selectedColor))
    }

    /**
     * Initialize views with defaults for new account
     */
    private fun initializeViews(binding: FragmentAccountFormBinding) {
        selectedName = ""
        setSelectedCurrency(binding, Commodity.DEFAULT_COMMODITY)
        binding.inputColorPicker.setBackgroundTintList(ColorStateList.valueOf(selectedColor))

        val parentUID = parentAccountUID
        if (!parentUID.isNullOrEmpty()) {
            val parentAccount = accountsDbAdapter.getRecordOrNull(parentUID)
            if (parentAccount != null) {
                setSelectedCurrency(binding, parentAccount.commodity)
                val parentAccountType = parentAccount.accountType
                setAccountTypeSelection(binding, parentAccountType)
                loadParentAccountList(binding, parentAccountType)
                setParentAccountSelection(binding, parentUID)
            }
        }
    }

    /**
     * Selects the corresponding account type in the spinner
     *
     * @param accountType the account type
     */
    private fun setAccountTypeSelection(
        binding: FragmentAccountFormBinding,
        accountType: AccountType
    ) {
        val position = accountTypesAdapter!!.getPosition(accountType)
        binding.inputAccountTypeSpinner.setSelection(position)
    }

    /**
     * Toggles the visibility of the default transfer account input fields.
     * This field is irrelevant for users who do not use double accounting
     */
    private fun setDefaultTransferAccountInputsVisible(
        binding: FragmentAccountFormBinding,
        visible: Boolean
    ) {
        binding.checkboxDefaultTransferAccount.isVisible = visible
        binding.inputDefaultTransferAccount.isVisible = visible
    }

    /**
     * Selects the currency in the spinner
     *
     * @param commodity the selected commodity
     */
    private fun setSelectedCurrency(binding: FragmentAccountFormBinding, commodity: Commodity) {
        val position = commoditiesAdapter!!.getPosition(commodity)
        binding.inputCurrencySpinner.setSelection(position)
    }

    /**
     * Selects the account with UID in the parent accounts spinner
     *
     * @param parentAccountUID UID of parent account to be selected
     */
    private fun setParentAccountSelection(
        binding: FragmentAccountFormBinding,
        parentAccountUID: String?
    ) {
        val parentAdapter = parentAccountNameAdapter!!
        binding.checkboxParentAccount.isChecked = false
        if (parentAdapter.isEmpty) {
            binding.checkboxParentAccount.isVisible = false
            binding.inputParentAccount.isVisible = false
        } else {
            binding.checkboxParentAccount.isVisible = true
            binding.inputParentAccount.isVisible = true
        }

        val position = parentAdapter.getPosition(parentAccountUID)
        if (position >= 0) {
            binding.checkboxParentAccount.isVisible = true
            binding.inputParentAccount.isVisible = true
            binding.checkboxParentAccount.isChecked = true
            binding.inputParentAccount.isEnabled = true
            binding.inputParentAccount.setSelection(position, true)
        }
    }

    /**
     * Selects the account with UID `parentAccountId` in the default transfer account spinner
     *
     * @param defaultTransferAccountUID UID of parent account to be selected
     */
    private fun setDefaultTransferAccountSelection(
        binding: FragmentAccountFormBinding,
        defaultTransferAccountUID: String?,
        enableTransferAccount: Boolean
    ) {
        setDefaultTransferAccountInputsVisible(binding, enableTransferAccount)
        binding.checkboxDefaultTransferAccount.isChecked = enableTransferAccount
        binding.inputDefaultTransferAccount.isEnabled = enableTransferAccount

        if (defaultTransferAccountUID.isNullOrEmpty()) {
            binding.checkboxDefaultTransferAccount.isChecked = false
            binding.inputDefaultTransferAccount.isEnabled = false
            return
        }
        val defaultAccountPosition =
            defaultAccountNameAdapter!!.getPosition(defaultTransferAccountUID)
        binding.inputDefaultTransferAccount.setSelection(defaultAccountPosition)
    }

    /**
     * Shows the color picker dialog
     */
    private fun showColorPickerDialog() {
        val fragmentManager = parentFragmentManager
        @ColorInt val currentColor = selectedColor

        val context = requireContext()
        val accountColorOptions = context.resources.getIntArray(R.array.account_colors)

        val colorPickerDialogFragment = ColorPickerDialog.newInstance(
            R.string.color_picker_default_title,
            accountColorOptions,
            currentColor, -1, ColorPickerDialog.SIZE_SMALL
        )
        fragmentManager.setFragmentResultListener(
            ColorPickerDialog.COLOR_PICKER_DIALOG_TAG,
            this,
            this
        )
        colorPickerDialogFragment.show(
            fragmentManager,
            ColorPickerDialog.COLOR_PICKER_DIALOG_TAG
        )
    }

    override fun onFragmentResult(requestKey: String, result: Bundle) {
        if (ColorPickerDialog.COLOR_PICKER_DIALOG_TAG == requestKey) {
            @ColorInt val color = result.getInt(ColorPickerDialog.EXTRA_COLOR)
            val binding = this.binding
            binding?.inputColorPicker?.setBackgroundTintList(ColorStateList.valueOf(color))
            selectedColor = color
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.default_save_actions, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_save -> {
                saveAccount()
                return true
            }

            android.R.id.home -> {
                finishFragment()
                return true
            }
        }

        return super.onOptionsItemSelected(item)
    }

    /**
     * Initializes the default transfer account spinner with eligible accounts
     */
    private fun loadDefaultTransferAccountList(
        binding: FragmentAccountFormBinding,
        account: Account?
    ) {
        val condition = (AccountEntry.COLUMN_UID + " != ?"
                + " AND " + AccountEntry.COLUMN_PLACEHOLDER + " = 0"
                + " AND " + AccountEntry.COLUMN_TYPE + " != ?"
                + " AND " + AccountEntry.COLUMN_TEMPLATE + " = 0")

        val context = binding.root.context
        val accountUID = account?.uid.orEmpty()
        defaultAccountNameAdapter = QualifiedAccountNameAdapter(
            context,
            condition,
            arrayOf(accountUID, AccountType.ROOT.name),
            accountsDbAdapter,
            viewLifecycleOwner
        ).load { adapter ->
            setDefaultTransferAccountSelection(
                binding,
                account?.defaultTransferAccountUID,
                useDoubleEntry && (adapter.count > 0)
            )
        }
        binding.inputDefaultTransferAccount.adapter = defaultAccountNameAdapter
        setDefaultTransferAccountInputsVisible(binding, useDoubleEntry)
    }

    /**
     * Loads the list of possible accounts which can be set as a parent account and initializes the spinner.
     * The allowed parent accounts depends on the account type
     *
     * @param accountType AccountType of account whose allowed parent list is to be loaded
     */
    private fun loadParentAccountList(
        binding: FragmentAccountFormBinding,
        accountType: AccountType
    ) {
        var condition =
            (AccountEntry.COLUMN_TYPE + " IN " + getAllowedParentAccountTypes(accountType)
                    + " AND " + AccountEntry.COLUMN_TEMPLATE + " = 0")

        val account = this.account
        if (account != null) {  //if editing an account
            // limit cyclic account hierarchies.
            if (descendantAccounts.isEmpty()) {
                condition += " AND (" + AccountEntry.COLUMN_UID + " NOT IN ( '" + account.uid + "' ) )"
            } else {
                val descendantAccountUIDs = mutableListOf<String>()
                descendantAccountUIDs.add(account.uid)
                for (descendant in descendantAccounts) {
                    descendantAccountUIDs.add(descendant.uid)
                }
                val descendantAccounts = descendantAccountUIDs.joinIn()
                condition += (" AND (" + AccountEntry.COLUMN_UID + " NOT IN " + descendantAccounts + ")")
            }
        }
        //disable before hiding, else we can still read it when saving
        binding.checkboxParentAccount.isChecked = false
        binding.checkboxParentAccount.isVisible = false
        binding.inputParentAccount.isVisible = false

        parentAccountNameAdapter = QualifiedAccountNameAdapter(
            binding.root.context,
            condition,
            null,
            accountsDbAdapter,
            viewLifecycleOwner
        )
        parentAccountNameAdapter!!.load {
            val parentUID = account?.parentUID ?: parentAccountUID
            setParentAccountSelection(binding, parentUID)
        }
        binding.inputParentAccount.adapter = parentAccountNameAdapter
    }

    /**
     * Returns a comma separated list of account types which can be parent accounts for the specified `type`.
     * The strings in the list are the [AccountType.name]s of the different types.
     *
     * @param type [AccountType]
     * @return String comma separated list of account types
     */
    private fun getAllowedParentAccountTypes(type: AccountType): String {
        val names = mutableListOf<String>()

        when (type) {
            AccountType.EQUITY -> names.add(AccountType.EQUITY.name)

            AccountType.INCOME,
            AccountType.EXPENSE -> {
                names.add(AccountType.EXPENSE.name)
                names.add(AccountType.INCOME.name)
            }

            AccountType.CASH,
            AccountType.BANK,
            AccountType.CREDIT,
            AccountType.ASSET,
            AccountType.LIABILITY,
            AccountType.PAYABLE,
            AccountType.RECEIVABLE,
            AccountType.CURRENCY,
            AccountType.STOCK,
            AccountType.MUTUAL -> {
                names.addAll(accountTypeStringList)
                names.remove(AccountType.EQUITY.name)
                names.remove(AccountType.EXPENSE.name)
                names.remove(AccountType.INCOME.name)
                names.remove(AccountType.ROOT.name)
            }

            AccountType.TRADING -> names.add(AccountType.TRADING.name)

            AccountType.ROOT -> names.addAll(this.accountTypeStringList)

            else -> names.addAll(accountTypeStringList)
        }
        return names.joinIn()
    }

    /**
     * Returns a list of all the available [AccountType]s as strings
     *
     * @return String list of all account types
     */
    private val accountTypeStringList: List<String>
        get() = AccountType.values().map { it.name }

    /**
     * Finishes the fragment appropriately.
     * Depends on how the fragment was loaded, it might have a backstack or not
     */
    private fun finishFragment() {
        val activity = activity
        if (activity == null) {
            Timber.w("Activity required")
            return
        }
        val binding = this.binding
        if (binding != null) {
            val imm = activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(binding.root.windowToken, 0)
        }

        val action = activity.intent.action
        if (action == Intent.ACTION_INSERT_OR_EDIT) {
            activity.setResult(Activity.RESULT_OK)
            activity.finish()
        } else {
            activity.supportFragmentManager.popBackStack()
        }
    }

    /**
     * Reads the fields from the account form and saves as a new account
     */
    private fun saveAccount() {
        Timber.i("Saving account")
        val binding = this.binding ?: return

        // accounts to update, in case we're updating full names of a sub account tree
        val newName = selectedName.trim()
        if (newName.isEmpty()) {
            binding.nameTextInputLayout.error = getString(R.string.toast_no_account_name_entered)
            return
        }
        binding.nameTextInputLayout.error = null

        var account = this.account
        if (account == null) {
            account = Account(newName, selectedCommodity)
            //new account, insert it
            accountsDbAdapter.insert(account)
        } else {
            account.name = newName
            account.commodity = selectedCommodity
        }

        account.accountType = selectedAccountType
        account.description = binding.inputAccountDescription.getText().toString().trim()
        account.note = binding.notes.getText().toString().trim()
        account.isPlaceholder = binding.placeholderStatus.isChecked
        account.isFavorite = binding.favoriteStatus.isChecked
        account.isHidden = binding.hiddenStatus.isChecked
        account.color = selectedColor
        val newParentAccountUID = if (binding.checkboxParentAccount.isChecked) {
            if (selectedParentAccountUID.isNullOrEmpty()) {
                rootAccountUID
            } else {
                selectedParentAccountUID
            }
        } else {
            //need to do this explicitly in case user removes parent account
            rootAccountUID
        }

        val accountsToUpdate = mutableListOf<Account>()
        accountsToUpdate.add(account)
        // update full names?
        if (newParentAccountUID != account.parentUID) {
            accountsToUpdate.addAll(descendantAccounts)
        }
        account.parentUID = newParentAccountUID

        if (binding.checkboxDefaultTransferAccount.isChecked
            && binding.inputDefaultTransferAccount.selectedItemPosition != Spinner.INVALID_POSITION
        ) {
            account.defaultTransferAccountUID = selectedDefaultTransferAccount?.uid
        } else {
            //explicitly set in case of removal of default account
            account.defaultTransferAccountUID = null
        }

        // bulk update, will not update transactions
        accountsDbAdapter.bulkAddRecords(accountsToUpdate, DatabaseAdapter.UpdateMethod.Update)

        finishFragment()
    }
}
