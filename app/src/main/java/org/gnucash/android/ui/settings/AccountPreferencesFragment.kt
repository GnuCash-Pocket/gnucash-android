/*
 * Copyright (c) 2013 Ngewi Fet <ngewif@gmail.com>
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

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import androidx.preference.ListPreference
import androidx.preference.Preference
import org.gnucash.android.R
import org.gnucash.android.app.GnuCashApplication.Companion.activeBookUID
import org.gnucash.android.app.GnuCashApplication.Companion.defaultCurrencyCode
import org.gnucash.android.app.getActivity
import org.gnucash.android.db.adapter.CommoditiesDbAdapter
import org.gnucash.android.ui.account.AccountsActivity
import org.gnucash.android.ui.settings.BookManagerFragment.Companion.openBook
import org.gnucash.android.ui.settings.dialog.DeleteAllAccountsConfirmationDialog

/**
 * Account settings fragment inside the Settings activity
 *
 * @author Ngewi Fet <ngewi.fet@gmail.com>
 * @author Oleksandr Tyshkovets <olexandr.tyshkovets@gmail.com>
 */
class AccountPreferencesFragment : GnuPreferenceFragment() {
    private var commoditiesDbAdapter = CommoditiesDbAdapter.instance
    private val currencyEntries = mutableListOf<CharSequence>()
    private val currencyEntryValues = mutableListOf<String>()

    override val titleId: Int = R.string.title_account_preferences

    override fun onStart() {
        super.onStart()
        commoditiesDbAdapter = CommoditiesDbAdapter.instance
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        preferenceManager.setSharedPreferencesName(activeBookUID)
        addPreferencesFromResource(R.xml.fragment_account_preferences)

        currencyEntries.clear()
        currencyEntryValues.clear()
        val commodities = commoditiesDbAdapter.allRecords
        for (commodity in commodities) {
            currencyEntries.add(commodity.formatListItem())
            currencyEntryValues.add(commodity.currencyCode)
        }
        val listPreference =
            findPreference<ListPreference>(getString(R.string.key_default_currency))!!
        var currencyCode = listPreference.value
        if (currencyCode.isNullOrEmpty()) {
            currencyCode = defaultCurrencyCode
        }
        val currency = commoditiesDbAdapter.getCurrency(currencyCode)
            ?: commoditiesDbAdapter.defaultCommodity
        listPreference.summary = currency.formatListItem()
        listPreference.setOnPreferenceChangeListener { preference, newValue ->
            val currencyCode = newValue?.toString() ?: return@setOnPreferenceChangeListener false
            val currency = commoditiesDbAdapter.setDefaultCurrencyCode(currencyCode)
            preference.summary = currency?.formatListItem()
            true
        }
        listPreference.entries = currencyEntries.toTypedArray()
        listPreference.entryValues = currencyEntryValues.toTypedArray()

        var preference = findPreference<Preference>(getString(R.string.key_import_accounts))!!
        preference.setOnPreferenceClickListener { _ ->
            AccountsActivity.startXmlFileChooser(this@AccountPreferencesFragment)
            true
        }

        preference = findPreference(getString(R.string.key_delete_all_accounts))!!
        preference.setOnPreferenceClickListener { _ ->
            showDeleteAccountsDialog()
            true
        }

        preference = findPreference(getString(R.string.key_create_default_accounts))!!
        preference.setOnPreferenceClickListener { _ ->
            val activity: Activity = activity
                ?: preference.context.getActivity()
                ?: return@setOnPreferenceClickListener false
            showConfirmDefaultAccounts(activity)
            true
        }
    }

    /**
     * Show the dialog for deleting accounts
     */
    fun showDeleteAccountsDialog() {
        DeleteAllAccountsConfirmationDialog()
            .show(parentFragmentManager, "delete_accounts")
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        val activity = requireActivity()

        when (requestCode) {
            AccountsActivity.REQUEST_PICK_ACCOUNTS_FILE ->
                if (resultCode == Activity.RESULT_OK && data != null) {
                    // FIXME - only import the accounts and not the entire book
                    openBook(activity, data)
                }

            else -> super.onActivityResult(requestCode, resultCode, data)
        }
    }

    private fun showConfirmDefaultAccounts(activity: Activity) {
        AlertDialog.Builder(activity)
            .setTitle(R.string.title_create_default_accounts)
            .setMessage(R.string.msg_confirm_create_default_accounts_setting)
            .setIcon(R.drawable.ic_warning)
            .setNegativeButton(R.string.btn_cancel) { _, _ ->
                // Dismisses itself
            }
            .setPositiveButton(R.string.btn_create_accounts) { _, _ ->
                val currencyCode = defaultCurrencyCode
                AccountsActivity.createDefaultAccounts(activity, currencyCode)
            }
            .show()
    }
}
