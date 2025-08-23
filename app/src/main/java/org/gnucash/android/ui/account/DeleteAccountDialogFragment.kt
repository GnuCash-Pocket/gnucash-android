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

import static android.widget.AdapterView.INVALID_POSITION;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentManager;

import org.gnucash.android.R;
import org.gnucash.android.app.GnuCashApplication;
import org.gnucash.android.databinding.DialogAccountDeleteBinding;
import org.gnucash.android.databinding.RadioGroupDeleteOrMoveBinding;
import org.gnucash.android.db.DatabaseSchema.AccountEntry;
import org.gnucash.android.db.adapter.AccountsDbAdapter;
import org.gnucash.android.db.adapter.SplitsDbAdapter;
import org.gnucash.android.db.adapter.TransactionsDbAdapter;
import org.gnucash.android.model.Account;
import org.gnucash.android.model.AccountType;
import org.gnucash.android.model.Commodity;
import org.gnucash.android.ui.adapter.QualifiedAccountNameAdapter;
import org.gnucash.android.ui.common.Refreshable;
import org.gnucash.android.ui.common.UxArgument;
import org.gnucash.android.ui.homescreen.WidgetConfigurationActivity;
import org.gnucash.android.ui.settings.dialog.DoubleConfirmationDialog;
import org.gnucash.android.util.BackupManager;

import java.util.List;

import kotlin.Unit;
import kotlin.jvm.functions.Function0;
import timber.log.Timber;

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
        return binding.getRoot();
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        final String accountUID = mOriginAccountUID;
        Account account = accountsDbAdapter.getSimpleRecord(accountUID);
        assert account != null;

        return getDialogBuilder()
            .setTitle(getString(R.string.alert_dialog_ok_delete) + ": " + account.getName())
            .setIcon(R.drawable.ic_warning)
            .setView(createView(getLayoutInflater()))
            .setPositiveButton(R.string.alert_dialog_ok_delete, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    maybeDelete();
                }
            })
            .create();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        final String accountUID = mOriginAccountUID;
        final DialogAccountDeleteBinding binding = this.binding;
        final Context context = view.getContext();

        final RadioGroupDeleteOrMoveBinding accountOptions = binding.accountsOptions;
        accountOptions.titleContent.setText(R.string.section_header_subaccounts);
        accountOptions.description.setText(R.string.label_delete_account_subaccounts_description);
        accountOptions.radioDelete.setText(R.string.label_delete_sub_accounts);
        accountOptions.radioMove.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(@NonNull CompoundButton buttonView, boolean isChecked) {
                accountOptions.targetAccountsSpinner.setEnabled(isChecked);
            }
        });
        accountOptions.radioDelete.setChecked(true);
        accountOptions.radioMove.setChecked(false);
        accountOptions.radioMove.setEnabled(false);
        accountOptions.targetAccountsSpinner.setVisibility(View.GONE);
        accountOptions.getRoot().setVisibility(mSubAccountCount > 0 ? View.VISIBLE : View.GONE);

        final RadioGroupDeleteOrMoveBinding transactionOptions = binding.transactionsOptions;
        transactionOptions.titleContent.setText(R.string.section_header_transactions);
        transactionOptions.description.setText(R.string.label_delete_account_transactions_description);
        transactionOptions.radioDelete.setText(R.string.label_delete_transactions);
        transactionOptions.radioMove.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(@NonNull CompoundButton buttonView, boolean isChecked) {
                transactionOptions.targetAccountsSpinner.setEnabled(isChecked);
            }
        });
        transactionOptions.radioDelete.setChecked(true);
        transactionOptions.radioMove.setChecked(false);
        transactionOptions.radioMove.setEnabled(false);
        transactionOptions.targetAccountsSpinner.setVisibility(View.GONE);
        transactionOptions.getRoot().setVisibility(mTransactionCount > 0 ? View.VISIBLE : View.GONE);

        Account account = accountsDbAdapter.getSimpleRecord(accountUID);
        Commodity commodity = account.getCommodity();
        AccountType accountType = account.getAccountType();
        List<String> descendantAccountUIDs = accountsDbAdapter.getDescendantAccountUIDs(accountUID, null, null);
        String joinedUIDs = "('" + TextUtils.join("','", descendantAccountUIDs) + "')";

        //target accounts for transactions and accounts have different conditions
        String accountMoveConditions = AccountEntry.COLUMN_UID + " != ?"
            + " AND " + AccountEntry.COLUMN_COMMODITY_UID + " = ?"
            + " AND " + AccountEntry.COLUMN_TYPE + " = ?"
            + " AND " + AccountEntry.COLUMN_TEMPLATE + " = 0"
            + " AND " + AccountEntry.COLUMN_UID + " NOT IN " + joinedUIDs;
        String[] accountMoveConditionsArgs = new String[]{account.getUID(), commodity.getUID(), accountType.name()};
        accountNameAdapterAccountsDestination = new QualifiedAccountNameAdapter(context, accountMoveConditions, accountMoveConditionsArgs, accountsDbAdapter, this);
        accountNameAdapterAccountsDestination.load(new Function0<Unit>() {
            @Override
            public Unit invoke() {
                boolean isEmpty = accountNameAdapterAccountsDestination.isEmpty();
                accountOptions.radioMove.setChecked(!isEmpty);
                accountOptions.radioMove.setEnabled(!isEmpty);
                accountOptions.targetAccountsSpinner.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
                return null;
            }
        });
        accountOptions.targetAccountsSpinner.setAdapter(accountNameAdapterAccountsDestination);

        String transactionDeleteConditions = AccountEntry.COLUMN_UID + " != ?"
            + " AND " + AccountEntry.COLUMN_COMMODITY_UID + " = ?"
            + " AND " + AccountEntry.COLUMN_TYPE + " = ?"
            + " AND " + AccountEntry.COLUMN_TEMPLATE + " = 0"
            + " AND " + AccountEntry.COLUMN_PLACEHOLDER + " = 0"
            + " AND " + AccountEntry.COLUMN_UID + " NOT IN " + joinedUIDs;
        String[] transactionDeleteConditionsArgs = new String[]{account.getUID(), commodity.getUID(), accountType.name()};

        accountNameAdapterTransactionsDestination = new QualifiedAccountNameAdapter(context, transactionDeleteConditions, transactionDeleteConditionsArgs, accountsDbAdapter, this);
        accountNameAdapterTransactionsDestination.load(new Function0<Unit>() {
            @Override
            public Unit invoke() {
                boolean isEmpty = accountNameAdapterTransactionsDestination.isEmpty();
                transactionOptions.radioMove.setChecked(!isEmpty);
                transactionOptions.radioMove.setEnabled(!isEmpty);
                transactionOptions.targetAccountsSpinner.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
                return null;
            }
        });
        transactionOptions.targetAccountsSpinner.setAdapter(accountNameAdapterTransactionsDestination);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        this.binding = null;
    }

    private void maybeDelete() {
        final String accountUID = mOriginAccountUID;
        final DialogAccountDeleteBinding binding = this.binding;

        final RadioGroupDeleteOrMoveBinding accountOptions = binding.accountsOptions;
        final RadioGroupDeleteOrMoveBinding transactionOptions = binding.transactionsOptions;

        final boolean canDeleteAccount = accountOptions.radioDelete.isEnabled() || accountOptions.radioMove.isEnabled();
        final boolean canDeleteTransactions = transactionOptions.radioDelete.isEnabled() || transactionOptions.radioMove.isEnabled();
        if (!canDeleteAccount && !canDeleteTransactions) {
            Timber.w("Cannot delete account");
            return;
        }

        final int moveAccountsIndex = accountOptions.radioMove.isChecked() ? accountOptions.targetAccountsSpinner.getSelectedItemPosition() : INVALID_POSITION;
        final int moveTransactionsIndex = transactionOptions.radioMove.isChecked() ? transactionOptions.targetAccountsSpinner.getSelectedItemPosition() : INVALID_POSITION;

        final Activity activity = requireActivity();
        final FragmentManager fm = getParentFragmentManager();
        BackupManager.backupActiveBookAsync(activity, result -> {
            deleteAccount(activity, fm, accountUID, moveAccountsIndex, moveTransactionsIndex);
            return null;
        });
    }

    private void deleteAccount(
        @NonNull Context context,
        @NonNull FragmentManager fm,
        @NonNull String accountUID,
        int moveAccountsAccountIndex,
        int moveTransactionsAccountIndex
    ) {
        if (TextUtils.isEmpty(accountUID)) {
            return;
        }
        if ((mSubAccountCount > 0) && (moveAccountsAccountIndex >= 0)) {
            String targetAccountUID = accountNameAdapterAccountsDestination.getUID(moveAccountsAccountIndex);
            if (TextUtils.isEmpty(targetAccountUID)) {
                return;
            }
            accountsDbAdapter.reassignDescendantAccounts(accountUID, targetAccountUID);
        }

        if ((mTransactionCount > 0) && (moveTransactionsAccountIndex >= 0)) {
            String targetAccountUID = accountNameAdapterTransactionsDestination.getUID(moveTransactionsAccountIndex);
            if (TextUtils.isEmpty(targetAccountUID)) {
                return;
            }
            //move all the splits
            splitsDbAdapter.reassignAccount(accountUID, targetAccountUID);
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
