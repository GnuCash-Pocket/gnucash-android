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

package org.gnucash.android.ui.settings;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SwitchPreference;

import org.gnucash.android.R;
import org.gnucash.android.app.GnuCashApplication;
import org.gnucash.android.db.DatabaseSchema;
import org.gnucash.android.db.adapter.AccountsDbAdapter;
import org.gnucash.android.model.Commodity;
import org.gnucash.android.ui.settings.dialog.DeleteAllTransactionsConfirmationDialog;

import java.util.List;

/**
 * Fragment for displaying transaction preferences
 *
 * @author Ngewi Fet <ngewif@gmail.com>
 */
public class TransactionsPreferenceFragment extends PreferenceFragmentCompat implements Preference.OnPreferenceChangeListener {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getPreferenceManager().setSharedPreferencesName(GnuCashApplication.getActiveBookUID());
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        ActionBar actionBar = ((AppCompatActivity) requireActivity()).getSupportActionBar();
        assert actionBar != null;
        actionBar.setTitle(R.string.title_transaction_preferences);
    }

    @Override
    public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
        addPreferencesFromResource(R.xml.fragment_transaction_preferences);

        SharedPreferences sharedPreferences = getPreferenceManager().getSharedPreferences();

        Preference pref = findPreference(getString(R.string.key_use_double_entry));
        pref.setOnPreferenceChangeListener(this);

        String keyCompactView = getString(R.string.key_use_compact_list);
        SwitchPreference switchPref = findPreference(keyCompactView);
        switchPref.setChecked(sharedPreferences.getBoolean(keyCompactView, false));

        String keySaveBalance = getString(R.string.key_save_opening_balances);
        switchPref = findPreference(keySaveBalance);
        switchPref.setChecked(sharedPreferences.getBoolean(keySaveBalance, false));

        String keyDoubleEntry = getString(R.string.key_use_double_entry);
        switchPref = findPreference(keyDoubleEntry);
        switchPref.setChecked(sharedPreferences.getBoolean(keyDoubleEntry, true));

        Preference preference = findPreference(getString(R.string.key_delete_all_transactions));
        preference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                showDeleteTransactionsDialog();
                return true;
            }
        });
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (preference.getKey().equals(getString(R.string.key_use_double_entry))) {
            boolean useDoubleEntry = (Boolean) newValue;
            setImbalanceAccountsHidden(preference.getContext(), useDoubleEntry);
        }
        return true;
    }

    /**
     * Deletes all transactions in the system
     */
    public void showDeleteTransactionsDialog() {
        Context context = requireContext();
        DeleteAllTransactionsConfirmationDialog deleteTransactionsConfirmationDialog =
            DeleteAllTransactionsConfirmationDialog.newInstance();
        if (GnuCashApplication.shouldBackupTransactions(context)) {
            deleteTransactionsConfirmationDialog.show(getParentFragmentManager(), "transaction_settings");
        } else {
            deleteTransactionsConfirmationDialog.deleteAll(context);
        }
    }


    /**
     * Hide all imbalance accounts when double-entry mode is disabled
     *
     * @param useDoubleEntry flag if double entry is enabled or not
     */
    private void setImbalanceAccountsHidden(@NonNull Context context, boolean useDoubleEntry) {
        String isHidden = useDoubleEntry ? "0" : "1";
        AccountsDbAdapter accountsDbAdapter = AccountsDbAdapter.getInstance();
        List<Commodity> commodities = accountsDbAdapter.getCommoditiesInUse();
        for (Commodity commodity : commodities) {
            String uid = accountsDbAdapter.getImbalanceAccountUID(context, commodity);
            if (uid != null) {
                accountsDbAdapter.updateRecord(uid, DatabaseSchema.AccountEntry.COLUMN_HIDDEN, isHidden);
            }
        }
    }
}
