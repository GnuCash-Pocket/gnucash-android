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

package org.gnucash.android.ui.settings.dialog;

import android.app.Activity;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.widget.Toast;

import org.gnucash.android.R;
import org.gnucash.android.db.adapter.AccountsDbAdapter;
import org.gnucash.android.ui.homescreen.WidgetConfigurationActivity;
import org.gnucash.android.util.BackupManager;

/**
 * Confirmation dialog for deleting all accounts from the system.
 * This class currently only works with HONEYCOMB and above.
 *
 * @author Ngewi Fet <ngewif@gmail.com>
 */
public class DeleteAllAccountsConfirmationDialog extends DoubleConfirmationDialog {

    public static DeleteAllAccountsConfirmationDialog newInstance() {
        return new DeleteAllAccountsConfirmationDialog();
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        final Activity activity = requireActivity();

        return getDialogBuilder()
            .setIcon(R.drawable.ic_warning)
            .setTitle(R.string.title_confirm_delete)
            .setMessage(R.string.confirm_delete_all_accounts)
            .setPositiveButton(R.string.alert_dialog_ok_delete, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        BackupManager.backupActiveBookAsync(activity, result -> {
                            AccountsDbAdapter.getInstance().deleteAllRecords();
                            Toast.makeText(activity, R.string.toast_all_accounts_deleted, Toast.LENGTH_SHORT).show();
                            WidgetConfigurationActivity.updateAllWidgets(activity);
                            return null;
                        });
                    }
                }
            )
            .create();
    }
}
