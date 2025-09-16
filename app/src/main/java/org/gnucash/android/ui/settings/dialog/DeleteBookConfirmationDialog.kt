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
package org.gnucash.android.ui.settings.dialog

import android.app.Activity
import android.app.Dialog
import android.os.Bundle
import org.gnucash.android.R
import org.gnucash.android.db.adapter.BooksDbAdapter
import org.gnucash.android.ui.common.Refreshable
import org.gnucash.android.util.BackupManager.backupBookAsync

/**
 * Confirmation dialog for deleting a book.
 *
 * @author Àlex Magaz <alexandre.magaz@gmail.com>
 */
class DeleteBookConfirmationDialog : DoubleConfirmationDialog() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val args = requireArguments()
        val bookUID: String = args.getString(EXTRA_BOOK_ID)!!
        val requestKey: String = args.getString(EXTRA_REQUEST_KEY)!!
        val activity: Activity = requireActivity()

        return dialogBuilder
            .setTitle(R.string.title_confirm_delete_book)
            .setIcon(R.drawable.ic_warning)
            .setMessage(R.string.msg_all_book_data_will_be_deleted)
            .setPositiveButton(R.string.btn_delete_book) { _, _ ->
                deleteBook(activity, bookUID, requestKey)
            }
            .create()
    }

    private fun deleteBook(activity: Activity, bookUID: String, requestKey: String) {
        val fm = parentFragmentManager
        backupBookAsync(activity, bookUID) {
            val deleted = BooksDbAdapter.instance.deleteBook(activity, bookUID)
            val result = Bundle()
            result.putBoolean(Refreshable.EXTRA_REFRESH, deleted)
            fm.setFragmentResult(requestKey, result)
        }
    }

    companion object {
        const val TAG: String = "delete_book_confirm"

        private const val EXTRA_BOOK_ID = "book_uid"
        private const val EXTRA_REQUEST_KEY = "request_key"

        fun newInstance(bookUID: String, requestKey: String = TAG): DeleteBookConfirmationDialog {
            val args = Bundle().apply {
                putString(EXTRA_BOOK_ID, bookUID)
                putString(EXTRA_REQUEST_KEY, requestKey)
            }
            val fragment = DeleteBookConfirmationDialog()
            fragment.arguments = args
            return fragment
        }
    }
}
