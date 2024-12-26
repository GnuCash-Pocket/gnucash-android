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
import android.database.Cursor;
import android.os.Bundle;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.cursoradapter.widget.SimpleCursorAdapter;
import androidx.fragment.app.DialogFragment;

import org.gnucash.android.R;
import org.gnucash.android.databinding.DialogBulkMoveBinding;
import org.gnucash.android.db.DatabaseSchema;
import org.gnucash.android.db.adapter.AccountsDbAdapter;
import org.gnucash.android.db.adapter.TransactionsDbAdapter;
import org.gnucash.android.ui.common.Refreshable;
import org.gnucash.android.ui.common.UxArgument;
import org.gnucash.android.ui.homescreen.WidgetConfigurationActivity;
import org.gnucash.android.util.QualifiedAccountNameCursorAdapter;

/**
 * Dialog fragment for moving transactions from one account to another
 *
 * @author Ngewi Fet <ngewif@gmail.com>
 */
public class BulkMoveDialogFragment extends DialogFragment {

    public static final String TAG = "bulk_move_transactions";

    /**
     * Spinner for selecting the account to move the transactions to
     */
    private Spinner mDestinationAccountSpinner;

    /**
     * Record IDs of the transactions to be moved
     */
    private long[] mTransactionIds = null;

    /**
     * GUID of account from which to move the transactions
     */
    private String mOriginAccountUID = null;

    /**
     * Create new instance of the bulk move dialog
     *
     * @param transactionIds   Array of transaction database record IDs
     * @param originAccountUID Account from which to move the transactions
     * @return BulkMoveDialogFragment instance with arguments set
     */
    public static BulkMoveDialogFragment newInstance(long[] transactionIds, String originAccountUID) {
        Bundle args = new Bundle();
        args.putLongArray(UxArgument.SELECTED_TRANSACTION_IDS, transactionIds);
        args.putString(UxArgument.ORIGIN_ACCOUNT_UID, originAccountUID);
        BulkMoveDialogFragment fragment = new BulkMoveDialogFragment();
        fragment.setArguments(args);
        return fragment;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        DialogBulkMoveBinding binding = DialogBulkMoveBinding.inflate(getLayoutInflater());
        Context context = binding.getRoot().getContext();
        mDestinationAccountSpinner = binding.accountsListSpinner;

        String title = context.getString(R.string.title_move_transactions, mTransactionIds.length);

        return new AlertDialog.Builder(context, getTheme())
            .setTitle(title)
            .setView(binding.getRoot())
            .setNegativeButton(R.string.btn_cancel, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dismiss();
                }
            })
            .setPositiveButton(R.string.btn_move, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    moveTransaction();
                }
            })
            .create();
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        Bundle args = getArguments();
        mTransactionIds = args.getLongArray(UxArgument.SELECTED_TRANSACTION_IDS);
        mOriginAccountUID = args.getString(UxArgument.ORIGIN_ACCOUNT_UID);

        AccountsDbAdapter accountsDbAdapter = AccountsDbAdapter.getInstance();
        String conditions = "(" + DatabaseSchema.AccountEntry.COLUMN_UID + " != ? AND "
                + DatabaseSchema.AccountEntry.COLUMN_CURRENCY + " = ? AND "
                + DatabaseSchema.AccountEntry.COLUMN_HIDDEN + " = 0 AND "
                + DatabaseSchema.AccountEntry.COLUMN_PLACEHOLDER + " = 0"
                + ")";
        Cursor cursor = accountsDbAdapter.fetchAccountsOrderedByFullName(conditions,
                new String[]{mOriginAccountUID, accountsDbAdapter.getCurrencyCode(mOriginAccountUID)});

        SimpleCursorAdapter adapter = new QualifiedAccountNameCursorAdapter(getActivity(), cursor);
        mDestinationAccountSpinner.setAdapter(adapter);
    }

    private void moveTransaction() {
        if (mTransactionIds == null) {
            dismiss();
        }
        Context context = requireContext();

        String srcAccountUID = mOriginAccountUID;
        long dstAccountId = mDestinationAccountSpinner.getSelectedItemId();
        String dstAccountUID = AccountsDbAdapter.getInstance().getUID(dstAccountId);
        TransactionsDbAdapter trxnAdapter = TransactionsDbAdapter.getInstance();
        if (!trxnAdapter.getAccountCurrencyCode(dstAccountUID).equals(trxnAdapter.getAccountCurrencyCode(srcAccountUID))) {
            Toast.makeText(context, R.string.toast_incompatible_currency, Toast.LENGTH_LONG).show();
            return;
        }

        for (long trxnId : mTransactionIds) {
            trxnAdapter.moveTransaction(trxnAdapter.getUID(trxnId), srcAccountUID, dstAccountUID);
        }

        WidgetConfigurationActivity.updateAllWidgets(context);
        Bundle result = new Bundle();
        result.putBoolean(Refreshable.EXTRA_REFRESH, true);
        result.putString(UxArgument.SELECTED_ACCOUNT_UID, dstAccountUID);
        getParentFragmentManager().setFragmentResult(TAG, result);

        dismiss();
    }
}
