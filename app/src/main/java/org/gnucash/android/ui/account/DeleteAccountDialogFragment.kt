/*
 * Copyright (c) 2015 Ngewi Fet <ngewif@gmail.com>
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

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import androidx.core.view.isVisible
import androidx.fragment.app.FragmentManager
import org.gnucash.android.R
import org.gnucash.android.app.GnuCashApplication.Companion.isDoubleEntryEnabled
import org.gnucash.android.databinding.DialogAccountDeleteBinding
import org.gnucash.android.db.DatabaseSchema.AccountEntry
import org.gnucash.android.db.adapter.AccountsDbAdapter
import org.gnucash.android.db.joinIn
import org.gnucash.android.model.AccountType
import org.gnucash.android.ui.adapter.QualifiedAccountNameAdapter
import org.gnucash.android.ui.common.Refreshable
import org.gnucash.android.ui.common.UxArgument
import org.gnucash.android.ui.homescreen.WidgetConfigurationActivity
import org.gnucash.android.ui.settings.dialog.DoubleConfirmationDialog
import org.gnucash.android.util.BackupManager.backupActiveBookAsync
import timber.log.Timber

/**
 * Delete confirmation dialog for accounts.
 * It is displayed when deleting an account which has transactions or sub-accounts, and the user
 * has the option to either move the transactions/sub-accounts, or delete them.
 * If an account has no transactions, it is deleted immediately with no confirmation required
 *
 * @author Ngewi Fet <ngewif@gmail.com>
 */
class DeleteAccountDialogFragment : DoubleConfirmationDialog() {
    /**
     * GUID of account from which to move the transactions
     */
    private var originAccountUID: String? = null

    private var binding: DialogAccountDeleteBinding? = null

    private var transactionCount: Long = 0
    private var subAccountCount: Long = 0
    private val accountsDbAdapter = AccountsDbAdapter.instance
    private val transactionsDbAdapter = accountsDbAdapter.transactionsDbAdapter
    private val splitsDbAdapter = transactionsDbAdapter.splitsDbAdapter
    private var accountNameAdapterTransactionsDestination: QualifiedAccountNameAdapter? = null
    private var accountNameAdapterAccountsDestination: QualifiedAccountNameAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val accountUID = requireArguments().getString(UxArgument.SELECTED_ACCOUNT_UID)!!
        originAccountUID = accountUID
        subAccountCount = accountsDbAdapter.getSubAccountCount(accountUID)
        transactionCount = transactionsDbAdapter.getTransactionsCount(accountUID).toLong()
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val accountUID = originAccountUID!!
        val account = accountsDbAdapter.getRecord(accountUID)
        val binding = createView(layoutInflater)

        return dialogBuilder
            .setTitle(getString(R.string.alert_dialog_ok_delete) + ": " + account.name)
            .setIcon(R.drawable.ic_warning)
            .setView(binding.root)
            .setPositiveButton(R.string.alert_dialog_ok_delete) { _, _ ->
                maybeDelete(binding)
            }
            .create()
    }

    private fun createView(inflater: LayoutInflater): DialogAccountDeleteBinding {
        val binding = DialogAccountDeleteBinding.inflate(inflater)
        this.binding = binding
        return binding
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return binding!!.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val accountUID = originAccountUID!!
        val binding = this.binding!!
        val context = view.context

        val accountsOptions = binding.accountsOptions.apply {
            titleContent.setText(R.string.section_header_subaccounts)
            description.setText(R.string.label_delete_account_subaccounts_description)
            radioDelete.setText(R.string.label_delete_sub_accounts)
            radioMove.setOnCheckedChangeListener { _, isChecked ->
                targetAccountsSpinner.isEnabled = isChecked
            }
            radioDelete.isChecked = true
            radioMove.isChecked = false
            radioMove.isEnabled = false
            targetAccountsSpinner.isVisible = false
            root.isVisible = subAccountCount > 0
        }

        val transactionsOptions = binding.transactionsOptions.apply {
            titleContent.setText(R.string.section_header_transactions)
            description.setText(R.string.label_delete_account_transactions_description)
            radioDelete.setText(R.string.label_delete_transactions)
            radioMove.setOnCheckedChangeListener { _, isChecked ->
                targetAccountsSpinner.isEnabled = isChecked
            }
            radioDelete.isChecked = true
            radioMove.isChecked = false
            radioMove.isEnabled = false
            targetAccountsSpinner.isVisible = false
            root.isVisible = transactionCount > 0
        }

        val account = accountsDbAdapter.getRecord(accountUID)
        val commodity = account.commodity
        val accountType = account.type
        val descendantAccountUIDs =
            accountsDbAdapter.getDescendantAccountUIDs(accountUID, null, null)
        val joinedUIDs = (descendantAccountUIDs + accountUID).joinIn()

        //target accounts for transactions and accounts have different conditions
        val accountMoveConditions = (AccountEntry.COLUMN_UID + " NOT IN " + joinedUIDs
                + " AND " + AccountEntry.COLUMN_TYPE + " != ?"
                + " AND " + AccountEntry.COLUMN_TEMPLATE + " = 0")
        val accountMoveArgs = arrayOf<String?>(AccountType.ROOT.name)
        accountNameAdapterAccountsDestination = QualifiedAccountNameAdapter(
            context,
            accountMoveConditions,
            accountMoveArgs,
            accountsDbAdapter,
            this
        ).load { adapter ->
            val hasData = !adapter.isEmpty
            accountsOptions.radioMove.isChecked = hasData
            accountsOptions.radioMove.isEnabled = hasData
            accountsOptions.targetAccountsSpinner.isVisible = hasData
        }
        accountsOptions.targetAccountsSpinner.adapter = accountNameAdapterAccountsDestination

        val transactionDeleteConditions = (AccountEntry.COLUMN_UID + " NOT IN " + joinedUIDs
                + " AND " + AccountEntry.COLUMN_COMMODITY_UID + " = ?"
                + " AND " + AccountEntry.COLUMN_TYPE + " = ?"
                + " AND " + AccountEntry.COLUMN_TEMPLATE + " = 0")
        val transactionDeleteArgs = arrayOf<String?>(commodity.uid, accountType.name)

        accountNameAdapterTransactionsDestination = QualifiedAccountNameAdapter(
            context,
            transactionDeleteConditions,
            transactionDeleteArgs,
            accountsDbAdapter,
            this
        ).load { adapter ->
            val hasData = !adapter.isEmpty
            transactionsOptions.radioMove.isChecked = hasData
            transactionsOptions.radioMove.isEnabled = hasData
            transactionsOptions.targetAccountsSpinner.isVisible = hasData
        }
        transactionsOptions.targetAccountsSpinner.adapter =
            accountNameAdapterTransactionsDestination
    }

    override fun onDestroyView() {
        super.onDestroyView()
        this.binding = null
    }

    private fun maybeDelete(binding: DialogAccountDeleteBinding) {
        val accountUID = originAccountUID!!

        val accountsOptions = binding.accountsOptions
        val transactionsOptions = binding.transactionsOptions

        val canDeleteAccount =
            accountsOptions.radioDelete.isEnabled || accountsOptions.radioMove.isEnabled
        val canDeleteTransactions =
            transactionsOptions.radioDelete.isEnabled || transactionsOptions.radioMove.isEnabled
        if (!canDeleteAccount && !canDeleteTransactions) {
            Timber.w("Cannot delete account")
            return
        }

        val moveAccountsIndex = if (accountsOptions.radioMove.isChecked) {
            accountsOptions.targetAccountsSpinner.selectedItemPosition
        } else {
            AdapterView.INVALID_POSITION
        }
        val moveTransactionsIndex = if (transactionsOptions.radioMove.isChecked) {
            transactionsOptions.targetAccountsSpinner.selectedItemPosition
        } else {
            AdapterView.INVALID_POSITION
        }

        val activity = activity ?: return
        val fm = parentFragmentManager
        backupActiveBookAsync(activity) { _ ->
            deleteAccount(activity, fm, accountUID, moveAccountsIndex, moveTransactionsIndex)
        }
    }

    private fun deleteAccount(
        context: Context,
        fm: FragmentManager,
        accountUID: String,
        moveAccountsAccountIndex: Int,
        moveTransactionsAccountIndex: Int
    ) {
        if (accountUID.isEmpty()) {
            return
        }
        if ((subAccountCount > 0) && (moveAccountsAccountIndex >= 0)) {
            val targetAccountUID =
                accountNameAdapterAccountsDestination!!.getUID(moveAccountsAccountIndex)
            if (targetAccountUID.isNullOrEmpty()) {
                return
            }
            accountsDbAdapter.reassignDescendantAccounts(accountUID, targetAccountUID)
        }

        if ((transactionCount > 0) && (moveTransactionsAccountIndex >= 0)) {
            val targetAccountUID =
                accountNameAdapterTransactionsDestination!!.getUID(moveTransactionsAccountIndex)
            if (targetAccountUID.isNullOrEmpty()) {
                return
            }
            //move all the splits
            splitsDbAdapter.reassignAccount(accountUID, targetAccountUID)
        }

        if (isDoubleEntryEnabled(context)) { //reassign splits to imbalance
            transactionsDbAdapter.deleteTransactionsForAccount(accountUID)
        }

        //now kill them all!!
        accountsDbAdapter.recursiveDeleteAccount(accountUID)

        WidgetConfigurationActivity.updateAllWidgets(context)

        val result = Bundle()
        result.putBoolean(Refreshable.EXTRA_REFRESH, true)
        fm.setFragmentResult(TAG, result)
    }

    companion object {
        const val TAG: String = "delete_account_dialog"

        /**
         * Creates new instance of the delete confirmation dialog and provides parameters for it
         *
         * @param accountUID GUID of the account to be deleted
         * @return New instance of the delete confirmation dialog
         */
        fun newInstance(accountUID: String): DeleteAccountDialogFragment {
            val args = Bundle()
            args.putString(UxArgument.SELECTED_ACCOUNT_UID, accountUID)
            val fragment = DeleteAccountDialogFragment()
            fragment.arguments = args
            return fragment
        }
    }
}
