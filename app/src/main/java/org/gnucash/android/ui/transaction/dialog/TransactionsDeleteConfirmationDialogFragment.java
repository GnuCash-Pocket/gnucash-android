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
package org.gnucash.android.ui.transaction.dialog;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;

import androidx.annotation.StringRes;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;

import org.gnucash.android.R;
import org.gnucash.android.app.GnuCashApplication;
import org.gnucash.android.db.adapter.AccountsDbAdapter;
import org.gnucash.android.db.adapter.DatabaseAdapter;
import org.gnucash.android.db.adapter.TransactionsDbAdapter;
import org.gnucash.android.model.Transaction;
import org.gnucash.android.ui.common.Refreshable;
import org.gnucash.android.ui.common.UxArgument;
import org.gnucash.android.ui.homescreen.WidgetConfigurationActivity;
import org.gnucash.android.util.BackupManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Displays a delete confirmation dialog for transactions
 * If the transaction ID parameter is 0, then all transactions will be deleted
 *
 * @author Ngewi Fet <ngewif@gmail.com>
 */
@Deprecated
public class TransactionsDeleteConfirmationDialogFragment extends DialogFragment {

    private static final String TAG = "transactions_delete_confirmation";

    private static final String EXTRA_TITLE_ID = "title_id";

    public static TransactionsDeleteConfirmationDialogFragment newInstance(@StringRes int titleId, long id) {
        TransactionsDeleteConfirmationDialogFragment frag = new TransactionsDeleteConfirmationDialogFragment();
        Bundle args = new Bundle();
        args.putInt(EXTRA_TITLE_ID, titleId);
        args.putLong(UxArgument.SELECTED_TRANSACTION_IDS, id);
        frag.setArguments(args);
        return frag;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        int title = getArguments().getInt(EXTRA_TITLE_ID);
        final long rowId = getArguments().getLong(UxArgument.SELECTED_TRANSACTION_IDS);
        int message = rowId == 0 ? R.string.msg_delete_all_transactions_confirmation : R.string.msg_delete_transaction_confirmation;
        return new AlertDialog.Builder(getActivity())
            .setIcon(R.drawable.ic_warning)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(R.string.alert_dialog_ok_delete, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        final FragmentManager fm = getParentFragmentManager();
                        final Activity activity = requireActivity();
                        final TransactionsDbAdapter transactionsDbAdapter = TransactionsDbAdapter.getInstance();
                        if (rowId == 0) {
                            //create backup before deleting everything
                            BackupManager.backupActiveBookAsync(activity, result -> {
                                List<Transaction> openingBalances = new ArrayList<Transaction>();
                                boolean preserveOpeningBalances = GnuCashApplication.shouldSaveOpeningBalances(false);
                                if (preserveOpeningBalances) {
                                    openingBalances = AccountsDbAdapter.getInstance().getAllOpeningBalanceTransactions();
                                }

                                transactionsDbAdapter.deleteAllRecords();

                                if (preserveOpeningBalances) {
                                    transactionsDbAdapter.bulkAddRecords(openingBalances, DatabaseAdapter.UpdateMethod.insert);
                                }
                                refresh(activity, fm);
                                return null;
                            });
                        } else {
                            transactionsDbAdapter.deleteRecord(rowId);
                            refresh(activity, fm);
                        }
                    }
                }
            )
            .setNegativeButton(R.string.alert_dialog_cancel, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        dismiss();
                    }
                }
            )
            .create();
    }

    private void refresh(Context context, FragmentManager fragmentManager) {
        Bundle result = new Bundle();
        result.putBoolean(Refreshable.EXTRA_REFRESH, true);
        fragmentManager.setFragmentResult(TAG, result);

        WidgetConfigurationActivity.updateAllWidgets(context);
    }
}
