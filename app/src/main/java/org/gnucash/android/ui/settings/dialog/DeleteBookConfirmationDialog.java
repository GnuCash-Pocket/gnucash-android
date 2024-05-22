/*
 * Copyright (c) 2017 Àlex Magaz Graça <alexandre.magaz@gmail.com>
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

import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentManager;

import org.gnucash.android.R;
import org.gnucash.android.db.adapter.BooksDbAdapter;
import org.gnucash.android.ui.common.Refreshable;
import org.gnucash.android.util.BackupManager;

/**
 * Confirmation dialog for deleting a book.
 *
 * @author Àlex Magaz <alexandre.magaz@gmail.com>
 */
public class DeleteBookConfirmationDialog extends DoubleConfirmationDialog {

    public static final String TAG = "delete_book_confirm";

    private static final String EXTRA_BOOK_ID = "book_uid";
    private static final String EXTRA_REQUEST_KEY = "request_key";

    @NonNull
    public static DeleteBookConfirmationDialog newInstance(String bookUID) {
        return newInstance(bookUID, TAG);
    }

    @NonNull
    public static DeleteBookConfirmationDialog newInstance(String bookUID, @NonNull String requestKey) {
        DeleteBookConfirmationDialog frag = new DeleteBookConfirmationDialog();
        Bundle args = new Bundle();
        args.putString(EXTRA_BOOK_ID, bookUID);
        args.putString(EXTRA_REQUEST_KEY, requestKey);
        frag.setArguments(args);
        return frag;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        return getDialogBuilder()
                .setTitle(R.string.title_confirm_delete_book)
                .setIcon(R.drawable.ic_warning_black)
                .setMessage(R.string.msg_all_book_data_will_be_deleted)
                .setPositiveButton(R.string.btn_delete_book, new DialogInterface.OnClickListener() {
                    @SuppressWarnings("ConstantConditions")
                    @Override
                    public void onClick(DialogInterface dialogInterface, int which) {
                        Bundle args = getArguments();
                        final String bookUID = args.getString(EXTRA_BOOK_ID);
                        final String requestKey = args.getString(EXTRA_REQUEST_KEY);
                        final FragmentManager fm = getParentFragmentManager();
                        BackupManager.backupBookAsync(requireActivity(), bookUID, backed -> {
                            boolean deleted = BooksDbAdapter.getInstance().deleteBook(bookUID);
                            Bundle result = new Bundle();
                            result.putBoolean(Refreshable.EXTRA_REFRESH, deleted);
                            fm.setFragmentResult(requestKey, result);
                            return null;
                        });
                    }
                })
                .create();
    }
}
