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
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.Preference;

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
public class TransactionsPreferenceFragment extends GnuPreferenceFragment {

    @Override
    protected int getTitleId() {
        return R.string.title_transaction_preferences;
    }

    @Override
    public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
        getPreferenceManager().setSharedPreferencesName(GnuCashApplication.getActiveBookUID());
        addPreferencesFromResource(R.xml.fragment_transaction_preferences);

        Preference preferenceDouble = findPreference(getString(R.string.key_use_double_entry));
        preferenceDouble.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(@NonNull Preference preference, Object newValue) {
                boolean useDoubleEntry = (Boolean) newValue;
                setImbalanceAccountsHidden(preference.getContext(), useDoubleEntry);
                return true;
            }
        });

        Preference preferenceDelete = findPreference(getString(R.string.key_delete_all_transactions));
        preferenceDelete.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(@NonNull Preference preference) {
                showDeleteTransactionsDialog(preference.getContext());
                return true;
            }
        });
    }

    /**
     * Deletes all transactions in the system
     */
    public void showDeleteTransactionsDialog(@NonNull Context context) {
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
