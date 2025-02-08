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

package org.gnucash.android.ui.transaction.dialog;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import org.gnucash.android.R;
import org.gnucash.android.databinding.DialogBulkMoveBinding;
import org.gnucash.android.db.DatabaseSchema;
import org.gnucash.android.db.adapter.AccountsDbAdapter;
import org.gnucash.android.db.adapter.TransactionsDbAdapter;
import org.gnucash.android.model.Account;
import org.gnucash.android.model.Commodity;
import org.gnucash.android.ui.adapter.QualifiedAccountNameAdapter;
import org.gnucash.android.ui.common.Refreshable;
import org.gnucash.android.ui.common.UxArgument;
import org.gnucash.android.ui.homescreen.WidgetConfigurationActivity;

/**
 * Dialog fragment for moving transactions from one account to another
 *
 * @author Ngewi Fet <ngewif@gmail.com>
 */
public class BulkMoveDialogFragment extends DialogFragment {

    public static final String TAG = "bulk_move_transactions";

    /**
     * Create new instance of the bulk move dialog
     *
     * @param transactionUIDs   Array of transaction database record IDs
     * @param originAccountUID Account from which to move the transactions
     * @return BulkMoveDialogFragment instance with arguments set
     */
    public static BulkMoveDialogFragment newInstance(String[] transactionUIDs, String originAccountUID) {
        Bundle args = new Bundle();
        args.putStringArray(UxArgument.SELECTED_TRANSACTION_UIDS, transactionUIDs);
        args.putString(UxArgument.ORIGIN_ACCOUNT_UID, originAccountUID);
        BulkMoveDialogFragment fragment = new BulkMoveDialogFragment();
        fragment.setArguments(args);
        return fragment;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        DialogBulkMoveBinding binding = DialogBulkMoveBinding.inflate(getLayoutInflater());
        final Context context = binding.getRoot().getContext();
        final Spinner accountSpinner = binding.accountsListSpinner;
        AccountsDbAdapter accountsDbAdapter = AccountsDbAdapter.getInstance();

        Bundle args = getArguments();
        final String[] selectedTransactionUIDs = args.getStringArray(UxArgument.SELECTED_TRANSACTION_UIDS);
        final String[] transactionUIDs = (selectedTransactionUIDs != null) ? selectedTransactionUIDs : new String[0];
        final String originAccountUID = args.getString(UxArgument.ORIGIN_ACCOUNT_UID);
        final Commodity originCommodity = accountsDbAdapter.getCommodity(originAccountUID);

        String where = DatabaseSchema.AccountEntry.COLUMN_UID + " != ? AND "
            + DatabaseSchema.AccountEntry.COLUMN_COMMODITY_UID + " = ? AND "
            + DatabaseSchema.AccountEntry.COLUMN_HIDDEN + " = 0 AND "
            + DatabaseSchema.AccountEntry.COLUMN_PLACEHOLDER + " = 0";
        String[] whereArgs = new String[]{originAccountUID, originCommodity.getUID()};

        final QualifiedAccountNameAdapter accountNameAdapter = QualifiedAccountNameAdapter.where(context, where, whereArgs);
        accountSpinner.setAdapter(accountNameAdapter);

        String title = context.getString(R.string.title_move_transactions, transactionUIDs.length);

        return new AlertDialog.Builder(context, getTheme())
            .setTitle(title)
            .setView(binding.getRoot())
            .setNegativeButton(R.string.btn_cancel, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    // Dismisses itself.
                }
            })
            .setPositiveButton(R.string.btn_move, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    int position = accountSpinner.getSelectedItemPosition();
                    if (position < 0) {
                        return;
                    }
                    Account account = accountNameAdapter.getAccount(position);
                    if (account == null) {
                        return;
                    }
                    String targetAccountUID = account.getUID();
                    moveTransaction(context, transactionUIDs, originAccountUID, targetAccountUID);
                }
            })
            .create();
    }

    private void moveTransaction(@NonNull Context context, @Nullable String[] transactionUIDs, String srcAccountUID, String dstAccountUID) {
        if ((transactionUIDs == null) || (transactionUIDs.length == 0)) {
            return;
        }

        TransactionsDbAdapter trxnAdapter = TransactionsDbAdapter.getInstance();
        AccountsDbAdapter accountsDbAdapter = AccountsDbAdapter.getInstance();
        Commodity currencySrc = accountsDbAdapter.getCommodity(srcAccountUID);
        Commodity currencyDst = accountsDbAdapter.getCommodity(dstAccountUID);
        if (!currencySrc.equals(currencyDst)) {
            Toast.makeText(context, R.string.toast_incompatible_currency, Toast.LENGTH_LONG).show();
            return;
        }

        for (String transactionUID : transactionUIDs) {
            trxnAdapter.moveTransaction(transactionUID, srcAccountUID, dstAccountUID);
        }

        WidgetConfigurationActivity.updateAllWidgets(context);
        Bundle result = new Bundle();
        result.putBoolean(Refreshable.EXTRA_REFRESH, true);
        result.putString(UxArgument.SELECTED_ACCOUNT_UID, dstAccountUID);
        getParentFragmentManager().setFragmentResult(TAG, result);
    }
}
