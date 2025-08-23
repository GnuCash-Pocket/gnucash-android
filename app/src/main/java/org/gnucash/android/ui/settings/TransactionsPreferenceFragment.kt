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
package org.gnucash.android.ui.settings

import android.content.Context
import android.os.Bundle
import androidx.preference.Preference
import org.gnucash.android.R
import org.gnucash.android.app.GnuCashApplication.Companion.activeBookUID
import org.gnucash.android.app.GnuCashApplication.Companion.shouldBackupTransactions
import org.gnucash.android.db.DatabaseSchema
import org.gnucash.android.db.adapter.AccountsDbAdapter
import org.gnucash.android.ui.settings.dialog.DeleteAllTransactionsConfirmationDialog

/**
 * Fragment for displaying transaction preferences
 *
 * @author Ngewi Fet <ngewif@gmail.com>
 */
class TransactionsPreferenceFragment : GnuPreferenceFragment() {
    override val titleId: Int = R.string.title_transaction_preferences

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        preferenceManager.setSharedPreferencesName(activeBookUID)
        addPreferencesFromResource(R.xml.fragment_transaction_preferences)

        val preferenceDouble =
            findPreference<Preference>(getString(R.string.key_use_double_entry))!!
        preferenceDouble.setOnPreferenceChangeListener { preference, newValue ->
            val useDoubleEntry = newValue as Boolean
            setImbalanceAccountsHidden(preference.context, useDoubleEntry)
            true
        }

        val preferenceDelete = findPreference<Preference?>(getString(R.string.key_delete_all_transactions))!!
        preferenceDelete.setOnPreferenceClickListener { preference ->
            showDeleteTransactionsDialog(preference.context)
            true
        }
    }

    /**
     * Deletes all transactions in the system
     */
    fun showDeleteTransactionsDialog(context: Context) {
        val dialog = DeleteAllTransactionsConfirmationDialog()
        if (shouldBackupTransactions(context)) {
            dialog.show(parentFragmentManager, "transaction_settings")
        } else {
            dialog.deleteAll(context)
        }
    }

    /**
     * Hide all imbalance accounts when double-entry mode is disabled
     *
     * @param isDoubleEntry flag if double entry is enabled or not
     */
    private fun setImbalanceAccountsHidden(context: Context, isDoubleEntry: Boolean) {
        val isHidden = if (isDoubleEntry) "0" else "1"
        val accountsDbAdapter = AccountsDbAdapter.instance
        val commodities = accountsDbAdapter.commoditiesInUse
        for (commodity in commodities) {
            val uid = accountsDbAdapter.getImbalanceAccountUID(context, commodity)
            if (uid != null) {
                accountsDbAdapter.updateRecord(
                    uid,
                    DatabaseSchema.AccountEntry.COLUMN_HIDDEN,
                    isHidden
                )
            }
        }
    }
}
