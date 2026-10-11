/*
 * Copyright (c) 2012 Ngewi Fet <ngewif@gmail.com>
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
package org.gnucash.android.ui.transaction.dialog

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import org.gnucash.android.R
import org.gnucash.android.app.DatabaseDialogFragment
import org.gnucash.android.app.resultBundle
import org.gnucash.android.app.withArguments
import org.gnucash.android.databinding.DialogBulkMoveBinding
import org.gnucash.android.db.DatabaseSchema.AccountEntry
import org.gnucash.android.db.adapter.AccountsDbAdapter
import org.gnucash.android.db.adapter.TransactionsDbAdapter
import org.gnucash.android.model.AccountType
import org.gnucash.android.ui.adapter.QualifiedAccountNameAdapter
import org.gnucash.android.ui.common.Refreshable
import org.gnucash.android.ui.common.UxArgument
import org.gnucash.android.ui.homescreen.WidgetConfigurationActivity
import org.gnucash.android.ui.snackLong

/**
 * Dialog fragment for moving transactions from one account to another
 *
 * @author Ngewi Fet <ngewif@gmail.com>
 */
class BulkMoveDialogFragment : DatabaseDialogFragment() {
    private lateinit var accountsDbAdapter: AccountsDbAdapter
    private lateinit var transactionsDbAdapter: TransactionsDbAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val dbHolder = databaseHolder
        accountsDbAdapter = dbHolder.accountsDbAdapter
        transactionsDbAdapter = dbHolder.transactionsDbAdapter
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val binding = DialogBulkMoveBinding.inflate(layoutInflater)
        val context = binding.root.context

        val args = requireArguments()
        val selectedTransactionUIDs = args.getStringArray(UxArgument.SELECTED_TRANSACTION_UIDS)
        val transactionUIDs = selectedTransactionUIDs ?: arrayOfNulls<String>(0)
        val originAccountUID: String = args.getString(UxArgument.ORIGIN_ACCOUNT_UID)!!
        val originCommodity = accountsDbAdapter.getCommodity(originAccountUID)

        val where = (AccountEntry.COLUMN_UID + " != ?"
                + " AND " + AccountEntry.COLUMN_COMMODITY_UID + " = ?"
                + " AND " + AccountEntry.COLUMN_TYPE + " != ?"
                + " AND " + AccountEntry.COLUMN_TEMPLATE + " = 0"
                + " AND " + AccountEntry.COLUMN_PLACEHOLDER + " = 0")
        val whereArgs = arrayOf<String?>(
            originAccountUID,
            originCommodity.uid,
            AccountType.ROOT.name
        )

        val accountNameAdapter =
            QualifiedAccountNameAdapter(context, where, whereArgs, accountsDbAdapter, this).load()
        binding.accountsListSpinner.adapter = accountNameAdapter

        val title = context.getString(R.string.title_move_transactions, transactionUIDs.size)

        return AlertDialog.Builder(context, theme)
            .setTitle(title)
            .setView(binding.root)
            .setNegativeButton(R.string.btn_cancel) { _, _ ->
                // Dismisses itself.
            }
            .setPositiveButton(R.string.btn_move) { _, _ ->
                val position = binding.accountsListSpinner.selectedItemPosition
                val account = accountNameAdapter.getAccount(position) ?: return@setPositiveButton
                val targetAccountUID = account.uid
                moveTransaction(context, transactionUIDs, originAccountUID, targetAccountUID)
            }
            .create()
    }

    private fun moveTransaction(
        context: Context,
        transactionUIDs: Array<String>?,
        srcAccountUID: String,
        dstAccountUID: String
    ) {
        if (transactionUIDs.isNullOrEmpty()) {
            return
        }

        val currencySrc = accountsDbAdapter.getCommodity(srcAccountUID)
        val currencyDst = accountsDbAdapter.getCommodity(dstAccountUID)
        if (currencySrc != currencyDst) {
            snackLong(R.string.toast_incompatible_currency)
            return
        }

        for (transactionUID in transactionUIDs) {
            transactionsDbAdapter.moveTransaction(transactionUID, srcAccountUID, dstAccountUID)
        }

        WidgetConfigurationActivity.updateAllWidgets(context)
        val result = resultBundle {
            putBoolean(Refreshable.EXTRA_REFRESH, true)
            putString(UxArgument.SELECTED_ACCOUNT_UID, dstAccountUID)
        }
        parentFragmentManager.setFragmentResult(TAG, result)
    }

    companion object {
        const val TAG: String = "bulk_move_transactions"

        /**
         * Create new instance of the bulk move dialog
         *
         * @param transactionUIDs  Array of transaction database record IDs
         * @param originAccountUID Account from which to move the transactions
         * @return BulkMoveDialogFragment instance with arguments set
         */
        fun newInstance(
            transactionUIDs: Array<String>,
            originAccountUID: String
        ): BulkMoveDialogFragment {
            return BulkMoveDialogFragment().withArguments {
                putStringArray(UxArgument.SELECTED_TRANSACTION_UIDS, transactionUIDs)
                putString(UxArgument.ORIGIN_ACCOUNT_UID, originAccountUID)
            }
        }
    }
}
