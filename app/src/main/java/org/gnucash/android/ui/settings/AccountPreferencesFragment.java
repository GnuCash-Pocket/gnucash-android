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

package org.gnucash.android.ui.settings;

import static org.gnucash.android.util.DocumentExtKt.openBook;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import com.google.android.material.snackbar.Snackbar;

import org.gnucash.android.R;
import org.gnucash.android.app.GnuCashApplication;
import org.gnucash.android.db.adapter.BooksDbAdapter;
import org.gnucash.android.db.adapter.CommoditiesDbAdapter;
import org.gnucash.android.export.ExportAsyncTask;
import org.gnucash.android.export.ExportFormat;
import org.gnucash.android.export.ExportParams;
import org.gnucash.android.export.Exporter;
import org.gnucash.android.model.Commodity;
import org.gnucash.android.ui.account.AccountsActivity;
import org.gnucash.android.ui.settings.dialog.DeleteAllAccountsConfirmationDialog;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

import timber.log.Timber;

/**
 * Account settings fragment inside the Settings activity
 *
 * @author Ngewi Fet <ngewi.fet@gmail.com>
 * @author Oleksandr Tyshkovets <olexandr.tyshkovets@gmail.com>
 */
public class AccountPreferencesFragment extends PreferenceFragmentCompat implements
        Preference.OnPreferenceChangeListener, Preference.OnPreferenceClickListener {

    private static final int REQUEST_EXPORT_FILE = 0xC5;

    private final CommoditiesDbAdapter commoditiesDbAdapter = CommoditiesDbAdapter.getInstance();
    private final List<CharSequence> currencyEntries = new ArrayList<>();
    private final List<CharSequence> currencyEntryValues = new ArrayList<>();

    @Override
    public void onCreatePreferences(Bundle bundle, String s) {
        addPreferencesFromResource(R.xml.fragment_account_preferences);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        currencyEntries.clear();
        currencyEntryValues.clear();
        List<Commodity> commodities = commoditiesDbAdapter.getAllRecords();
        for (Commodity commodity : commodities) {
            currencyEntries.add(commodity.formatListItem());
            currencyEntryValues.add(commodity.getCurrencyCode());
        }
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        ActionBar actionBar = ((AppCompatActivity) requireActivity()).getSupportActionBar();
        assert actionBar != null;
        actionBar.setTitle(R.string.title_account_preferences);
    }

    @Override
    public void onResume() {
        super.onResume();

        ListPreference listPreference = findPreference(getString(R.string.key_default_currency));
        String currencyCode = listPreference.getValue();
        if (TextUtils.isEmpty(currencyCode)) {
            currencyCode = GnuCashApplication.getDefaultCurrencyCode();
        }
        Commodity commodity = commoditiesDbAdapter.getCommodity(currencyCode);
        if (commodity == null) {
            commodity = Commodity.DEFAULT_COMMODITY;
        }
        String currencyName = commodity.formatListItem();
        listPreference.setSummary(currencyName);
        listPreference.setOnPreferenceChangeListener(this);
        listPreference.setEntries(currencyEntries.toArray(new CharSequence[0]));
        listPreference.setEntryValues(currencyEntryValues.toArray(new CharSequence[0]));

        Preference preference = findPreference(getString(R.string.key_import_accounts));
        preference.setOnPreferenceClickListener(this);

        preference = findPreference(getString(R.string.key_export_accounts_csv));
        preference.setOnPreferenceClickListener(this);

        preference = findPreference(getString(R.string.key_delete_all_accounts));
        preference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                showDeleteAccountsDialog();
                return true;
            }
        });

        preference = findPreference(getString(R.string.key_create_default_accounts));
        preference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                final Activity activity = requireActivity();
                new AlertDialog.Builder(activity)
                        .setTitle(R.string.title_create_default_accounts)
                        .setMessage(R.string.msg_confirm_create_default_accounts_setting)
                        .setIcon(R.drawable.ic_warning)
                        .setPositiveButton(R.string.btn_create_accounts, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int which) {
                                AccountsActivity.createDefaultAccounts(activity, Commodity.DEFAULT_COMMODITY.getCurrencyCode());
                            }
                        })
                        .setNegativeButton(R.string.btn_cancel, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.dismiss();
                            }
                        })
                        .create()
                        .show();

                return true;
            }
        });
    }

    @Override
    public boolean onPreferenceClick(Preference preference) {
        String key = preference.getKey();

        if (key.equals(getString(R.string.key_import_accounts))) {
            AccountsActivity.startXmlFileChooser(this);
            return true;
        }

        if (key.equals(getString(R.string.key_export_accounts_csv))) {
            selectExportFile();
            return true;
        }

        return false;
    }

    /**
     * Open a chooser for user to pick a file to export to
     */
    private void selectExportFile() {
        String bookName = BooksDbAdapter.getInstance().getActiveBookDisplayName();
        String filename = Exporter.buildExportFilename(ExportFormat.CSVA, bookName);

        Intent createIntent = new Intent(Intent.ACTION_CREATE_DOCUMENT)
            .setType("*/*")
            .addCategory(Intent.CATEGORY_OPENABLE)
            .putExtra(Intent.EXTRA_TITLE, filename);
        try {
            startActivityForResult(createIntent, REQUEST_EXPORT_FILE);
        } catch (ActivityNotFoundException e) {
            Timber.e(e, "Cannot create document for export");
            if (isVisible()) {
                View view = getView();
                assert view != null;
                Snackbar.make(view, R.string.toast_install_file_manager, Snackbar.LENGTH_LONG).show();
            } else {
                Toast.makeText(requireContext(), R.string.toast_install_file_manager, Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (preference.getKey().equals(getString(R.string.key_default_currency))) {
            String currencyCode = newValue.toString();
            GnuCashApplication.setDefaultCurrencyCode(preference.getContext(), currencyCode);
            String summary = commoditiesDbAdapter.getCommodity(currencyCode).formatListItem();
            preference.setSummary(summary);
            return true;
        }
        return false;
    }

    /**
     * Show the dialog for deleting accounts
     */
    public void showDeleteAccountsDialog() {
        DeleteAllAccountsConfirmationDialog deleteConfirmationDialog = DeleteAllAccountsConfirmationDialog.newInstance();
        deleteConfirmationDialog.show(getActivity().getSupportFragmentManager(), "account_settings");
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        Activity activity = requireActivity();

        switch (requestCode) {
            case AccountsActivity.REQUEST_PICK_ACCOUNTS_FILE:
                if (resultCode == Activity.RESULT_OK && data != null) {
                    openBook(activity, data);
                }
                break;

            case REQUEST_EXPORT_FILE:
                if (resultCode == Activity.RESULT_OK && data != null) {
                    ExportParams exportParams = new ExportParams(ExportFormat.CSVA);
                    exportParams.setExportTarget(ExportParams.ExportTarget.URI);
                    exportParams.setExportLocation(data.getData());
                    ExportAsyncTask exportTask = new ExportAsyncTask(activity, GnuCashApplication.getActiveBookUID());

                    try {
                        exportTask.execute(exportParams).get();
                    } catch (InterruptedException | ExecutionException e) {
                        Timber.e(e);
                        Toast.makeText(activity, "An error occurred during the Accounts CSV export",
                                Toast.LENGTH_LONG).show();
                    }
                }

            default:
                super.onActivityResult(requestCode, resultCode, data);
                break;
        }
    }
}
