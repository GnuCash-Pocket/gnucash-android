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
package org.gnucash.android.ui.account;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CompoundButton;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentManager;

import org.gnucash.android.R;
import org.gnucash.android.app.GnuCashApplication;
import org.gnucash.android.databinding.DialogAccountDeleteBinding;
import org.gnucash.android.databinding.RadioGroupDeleteOrMoveBinding;
import org.gnucash.android.db.DatabaseSchema;
import org.gnucash.android.db.adapter.AccountsDbAdapter;
import org.gnucash.android.db.adapter.SplitsDbAdapter;
import org.gnucash.android.db.adapter.TransactionsDbAdapter;
import org.gnucash.android.model.AccountType;
import org.gnucash.android.model.Commodity;
import org.gnucash.android.ui.adapter.QualifiedAccountNameAdapter;
import org.gnucash.android.ui.common.Refreshable;
import org.gnucash.android.ui.common.UxArgument;
import org.gnucash.android.ui.homescreen.WidgetConfigurationActivity;
import org.gnucash.android.ui.settings.dialog.DoubleConfirmationDialog;
import org.gnucash.android.util.BackupManager;

import java.util.List;

/**
 * Delete confirmation dialog for accounts.
 * It is displayed when deleting an account which has transactions or sub-accounts, and the user
 * has the option to either move the transactions/sub-accounts, or delete them.
 * If an account has no transactions, it is deleted immediately with no confirmation required
 *
 * @author Ngewi Fet <ngewif@gmail.com>
 */
public class DeleteAccountDialogFragment extends DoubleConfirmationDialog {

    public static final String TAG = "delete_account_dialog";

    /**
     * GUID of account from which to move the transactions
     */
    private String mOriginAccountUID = null;

    private DialogAccountDeleteBinding binding;

    private int mTransactionCount;
    private int mSubAccountCount;
    private final AccountsDbAdapter accountsDbAdapter = AccountsDbAdapter.getInstance();
    private final TransactionsDbAdapter transactionsDbAdapter = accountsDbAdapter.transactionsDbAdapter;
    private final SplitsDbAdapter splitsDbAdapter = transactionsDbAdapter.splitsDbAdapter;
    private QualifiedAccountNameAdapter accountNameAdapterTransactionsDestination;
    private QualifiedAccountNameAdapter accountNameAdapterAccountsDestination;

    /**
     * Creates new instance of the delete confirmation dialog and provides parameters for it
     *
     * @param accountUID GUID of the account to be deleted
     * @return New instance of the delete confirmation dialog
     */
    public static DeleteAccountDialogFragment newInstance(String accountUID) {
        Bundle args = new Bundle();
        args.putString(UxArgument.SELECTED_ACCOUNT_UID, accountUID);
        DeleteAccountDialogFragment fragment = new DeleteAccountDialogFragment();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String accountUID = getArguments().getString(UxArgument.SELECTED_ACCOUNT_UID);
        assert accountUID != null;
        mOriginAccountUID = accountUID;
        mSubAccountCount = accountsDbAdapter.getSubAccountCount(accountUID);
        mTransactionCount = transactionsDbAdapter.getTransactionsCount(accountUID);
    }

    @NonNull
    private View createView(@NonNull LayoutInflater inflater) {
        DialogAccountDeleteBinding binding = DialogAccountDeleteBinding.inflate(inflater);
        this.binding = binding;

        final RadioGroupDeleteOrMoveBinding transactionOptions = binding.transactionsOptions;
        transactionOptions.titleContent.setText(R.string.section_header_transactions);
        transactionOptions.description.setText(R.string.label_delete_account_transactions_description);
        transactionOptions.radioDelete.setText(R.string.label_delete_transactions);

        transactionOptions.radioMove.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                transactionOptions.targetAccountsSpinner.setEnabled(isChecked);
            }
        });
        transactionOptions.getRoot().setVisibility(mTransactionCount > 0 ? View.VISIBLE : View.GONE);

        final RadioGroupDeleteOrMoveBinding accountOptions = binding.accountsOptions;
        accountOptions.titleContent.setText(R.string.section_header_subaccounts);
        accountOptions.description.setText(R.string.label_delete_account_subaccounts_description);
        accountOptions.radioDelete.setText(R.string.label_delete_sub_accounts);

        accountOptions.radioMove.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                accountOptions.targetAccountsSpinner.setEnabled(isChecked);
            }
        });
        accountOptions.getRoot().setVisibility(mSubAccountCount > 0 ? View.VISIBLE : View.GONE);

        return binding.getRoot();
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        final FragmentManager fm = getParentFragmentManager();
        final Activity activity = requireActivity();
        final String accountUID = mOriginAccountUID;

        return getDialogBuilder()
            .setTitle(R.string.alert_dialog_ok_delete)
            .setIcon(R.drawable.ic_warning)
            .setView(createView(getLayoutInflater()))
            .setPositiveButton(R.string.alert_dialog_ok_delete, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    final DialogAccountDeleteBinding binding = DeleteAccountDialogFragment.this.binding;
                    final RadioGroupDeleteOrMoveBinding transactionOptions = binding.transactionsOptions;
                    final RadioGroupDeleteOrMoveBinding accountOptions = binding.accountsOptions;
                    final Integer moveTransactionsIndex = transactionOptions.radioMove.isChecked() ? transactionOptions.targetAccountsSpinner.getSelectedItemPosition() : null;
                    final Integer moveAccountsIndex = accountOptions.radioMove.isChecked() ? accountOptions.targetAccountsSpinner.getSelectedItemPosition() : null;

                    BackupManager.backupActiveBookAsync(activity, result -> {
                        deleteAccount(activity, fm, accountUID, moveTransactionsIndex, moveAccountsIndex);
                        return null;
                    });
                }
            })
            .create();
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        DialogAccountDeleteBinding binding = DeleteAccountDialogFragment.this.binding;
        if (binding == null) return;
        final Context context = binding.getRoot().getContext();
        final RadioGroupDeleteOrMoveBinding transactionOptions = binding.transactionsOptions;
        final RadioGroupDeleteOrMoveBinding accountOptions = binding.accountsOptions;

        String accountName = accountsDbAdapter.getAccountName(mOriginAccountUID);
        getDialog().setTitle(getString(R.string.alert_dialog_ok_delete) + ": " + accountName);
        List<String> descendantAccountUIDs = accountsDbAdapter.getDescendantAccountUIDs(mOriginAccountUID, null, null);

        Commodity commodity = accountsDbAdapter.getCommodity(mOriginAccountUID);
        AccountType accountType = accountsDbAdapter.getAccountType(mOriginAccountUID);

        String joinedUIDs = "('" + TextUtils.join("','", descendantAccountUIDs) + "')";
        String transactionDeleteConditions = DatabaseSchema.AccountEntry.COLUMN_UID + " != ? AND "
            + DatabaseSchema.AccountEntry.COLUMN_COMMODITY_UID + " = ? AND "
            + DatabaseSchema.AccountEntry.COLUMN_TYPE + " = ? AND "
            + DatabaseSchema.AccountEntry.COLUMN_PLACEHOLDER + " = 0 AND "
            + DatabaseSchema.AccountEntry.COLUMN_UID + " NOT IN " + joinedUIDs;
        String[] transactionDeleteConditionsArgs = new String[]{mOriginAccountUID, commodity.getUID(), accountType.name()};

        accountNameAdapterTransactionsDestination = QualifiedAccountNameAdapter.where(context, transactionDeleteConditions, transactionDeleteConditionsArgs);
        transactionOptions.targetAccountsSpinner.setAdapter(accountNameAdapterTransactionsDestination);

        //target accounts for transactions and accounts have different conditions
        String accountMoveConditions = DatabaseSchema.AccountEntry.COLUMN_UID + " != ? AND "
            + DatabaseSchema.AccountEntry.COLUMN_COMMODITY_UID + " = ? AND "
            + DatabaseSchema.AccountEntry.COLUMN_TYPE + " = ? AND "
            + DatabaseSchema.AccountEntry.COLUMN_UID + " NOT IN " + joinedUIDs;
        String[] accountMoveConditionsArgs = new String[]{mOriginAccountUID, commodity.getUID(), accountType.name()};
        accountNameAdapterAccountsDestination = QualifiedAccountNameAdapter.where(context, accountMoveConditions, accountMoveConditionsArgs);
        accountOptions.targetAccountsSpinner.setAdapter(accountNameAdapterAccountsDestination);

        //this comes after the listeners because of some useful bindings done there
        if (accountNameAdapterAccountsDestination.getCount() == 0) {
            accountOptions.radioMove.setEnabled(false);
            accountOptions.radioMove.setChecked(false);
            accountOptions.radioDelete.setChecked(true);
            accountOptions.targetAccountsSpinner.setVisibility(View.GONE);
            transactionOptions.radioMove.setEnabled(false);
            transactionOptions.radioMove.setChecked(false);
            transactionOptions.radioDelete.setChecked(true);
            transactionOptions.targetAccountsSpinner.setVisibility(View.GONE);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        this.binding = null;
    }

    private void deleteAccount(
        @NonNull Context context,
        @NonNull FragmentManager fm,
        @NonNull String accountUID,
        @Nullable Integer moveTransactionsAccountIndex,
        @Nullable Integer moveAccountsAccountIndex
    ) {
        if (TextUtils.isEmpty(accountUID)) {
            return;
        }
        if ((mTransactionCount > 0) && (moveTransactionsAccountIndex != null)) {
            String targetAccountUID = accountNameAdapterTransactionsDestination.getUID(moveTransactionsAccountIndex);
            if (TextUtils.isEmpty(targetAccountUID)) {
                return;
            }
            //move all the splits
            splitsDbAdapter.reassignAccount(accountUID, targetAccountUID);
        }

        if ((mSubAccountCount > 0) && (moveAccountsAccountIndex != null)) {
            String targetAccountUID = accountNameAdapterAccountsDestination.getUID(moveAccountsAccountIndex);
            if (TextUtils.isEmpty(targetAccountUID)) {
                return;
            }
            accountsDbAdapter.reassignDescendantAccounts(accountUID, targetAccountUID);
        }

        if (GnuCashApplication.isDoubleEntryEnabled(context)) { //reassign splits to imbalance
            transactionsDbAdapter.deleteTransactionsForAccount(accountUID);
        }

        //now kill them all!!
        accountsDbAdapter.recursiveDeleteAccount(accountUID);

        WidgetConfigurationActivity.updateAllWidgets(context);

        Bundle result = new Bundle();
        result.putBoolean(Refreshable.EXTRA_REFRESH, true);
        fm.setFragmentResult(TAG, result);
    }
}
