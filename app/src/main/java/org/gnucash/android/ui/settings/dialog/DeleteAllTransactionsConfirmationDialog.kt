/*
 * Copyright (c) 2013 - 2014 Ngewi Fet <ngewif@gmail.com>
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
package org.gnucash.android.ui.settings.dialog

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.os.Bundle
import org.gnucash.android.R
import org.gnucash.android.app.GnuCashApplication.Companion.shouldSaveOpeningBalances
import org.gnucash.android.db.adapter.AccountsDbAdapter
import org.gnucash.android.db.adapter.DatabaseAdapter
import org.gnucash.android.db.adapter.TransactionsDbAdapter
import org.gnucash.android.model.Transaction
import org.gnucash.android.ui.homescreen.WidgetConfigurationActivity
import org.gnucash.android.ui.snackLong
import org.gnucash.android.util.BackupManager.backupActiveBookAsync
import timber.log.Timber

/**
 * Confirmation dialog for deleting all transactions
 *
 * @author ngewif <ngewif@gmail.com>
 * @author Yongxin Wang <fefe.wyx@gmail.com>
 */
class DeleteAllTransactionsConfirmationDialog : DoubleConfirmationDialog() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val activity: Activity = requireActivity()

        return dialogBuilder
            .setIcon(R.drawable.ic_warning)
            .setTitle(R.string.title_confirm_delete)
            .setMessage(R.string.msg_delete_all_transactions_confirmation)
            .setPositiveButton(R.string.alert_dialog_ok_delete) { _, _ ->
                deleteTransactions(activity)
            }
            .create()
    }

    private fun deleteTransactions(activity: Activity) {
        backupActiveBookAsync(activity) {
            deleteAll(activity)
        }
    }

    fun deleteAll(context: Context) {
        // TODO show a "Deleting Transactions" progress dialog.

        val accountsDbAdapter = AccountsDbAdapter.instance
        val preserveOpeningBalances = shouldSaveOpeningBalances(false)
        var openingBalances: List<Transaction> = emptyList()
        if (preserveOpeningBalances) {
            openingBalances = accountsDbAdapter.allOpeningBalanceTransactions
        }
        val transactionsDbAdapter = TransactionsDbAdapter.instance
        val count = transactionsDbAdapter.deleteAllNonTemplateTransactions()
        Timber.i("Deleted %d transactions successfully", count)

        if (preserveOpeningBalances) {
            transactionsDbAdapter.bulkAddRecords(
                openingBalances,
                DatabaseAdapter.UpdateMethod.Insert
            )
        }
        snackLong(R.string.toast_all_transactions_deleted)
        WidgetConfigurationActivity.updateAllWidgets(context)
    }
}
