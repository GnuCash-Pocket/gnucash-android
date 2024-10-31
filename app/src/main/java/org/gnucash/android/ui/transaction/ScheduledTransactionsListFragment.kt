package org.gnucash.android.ui.transaction

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.loader.content.Loader
import org.gnucash.android.R
import org.gnucash.android.databinding.ListItemScheduledTrxnBinding
import org.gnucash.android.db.DatabaseCursorLoader
import org.gnucash.android.db.DatabaseSchema
import org.gnucash.android.db.adapter.ScheduledActionDbAdapter
import org.gnucash.android.db.adapter.TransactionsDbAdapter
import org.gnucash.android.model.ScheduledAction
import org.gnucash.android.ui.common.FormActivity
import org.gnucash.android.ui.common.Refreshable
import org.gnucash.android.ui.common.UxArgument
import timber.log.Timber

class ScheduledTransactionsListFragment : ScheduledActionsListFragment() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        listAdapter = ScheduledTransactionsCursorAdapter(this)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.empty.setText(R.string.label_no_recurring_transactions)
    }

    override fun onCreateLoader(id: Int, args: Bundle?): Loader<Cursor> {
        return ScheduledTransactionsCursorLoader(requireContext())
    }

    /**
     * [DatabaseCursorLoader] for loading recurring transactions asynchronously from the database
     *
     * @author Ngewi Fet <ngewif></ngewif>@gmail.com>
     */
    private class ScheduledTransactionsCursorLoader(context: Context) :
        DatabaseCursorLoader(context) {
        init {
            mDatabaseAdapter = ScheduledActionDbAdapter.getInstance()
        }

        override fun loadInBackground(): Cursor {
            val cursor = mDatabaseAdapter.fetchAllRecords(
                DatabaseSchema.ScheduledActionEntry.COLUMN_TYPE + "=?",
                arrayOf(ScheduledAction.ActionType.TRANSACTION.name), null
            )

            registerContentObserver(cursor)
            return cursor
        }
    }

    /**
     * Extends a simple cursor adapter to bind transaction attributes to views
     *
     * @author Ngewi Fet <ngewif></ngewif>@gmail.com>
     */
    private class ScheduledTransactionsCursorAdapter(refreshable: Refreshable) :
        ScheduledCursorAdapter<ScheduledTransactionsViewHolder>(refreshable) {

        override fun createViewHolder(
            binding: ListItemScheduledTrxnBinding,
            refreshable: Refreshable
        ) = ScheduledTransactionsViewHolder(binding, refreshable)
    }

    private class ScheduledTransactionsViewHolder(
        binding: ListItemScheduledTrxnBinding,
        refreshable: Refreshable
    ) : ScheduledViewHolder(binding, refreshable) {

        private val transactionsDbAdapter = TransactionsDbAdapter.getInstance()

        override fun bind(scheduledAction: ScheduledAction) {
            super.bind(scheduledAction)
            val context = itemView.context
            primaryTextView.text = scheduledAction.toString()

            val transactionUID = scheduledAction.actionUID!!
            val transaction = transactionsDbAdapter.getRecord(transactionUID)
            val splits = transaction.splits
            val accountUID = splits[0].accountUID!!

            primaryTextView.text = transaction.description
            descriptionTextView.text = formatSchedule(scheduledAction)

            var text = ""
            if (splits.size == 2) {
                val first = splits[0]
                for (split in splits) {
                    if ((first !== split) && first.isPairOf(split)) {
                        text = first.value!!.formattedString()
                        break
                    }
                }
            } else {
                text = context.getString(R.string.label_split_count, splits.size)
            }
            amountTextView.text = text

            itemView.setOnClickListener { editTransaction(scheduledAction, accountUID) }
        }

        private fun editTransaction(scheduledAction: ScheduledAction, accountUID: String) {
            val context = itemView.context
            val intent = Intent(context, FormActivity::class.java)
                .setAction(Intent.ACTION_INSERT_OR_EDIT)
                .putExtra(UxArgument.FORM_TYPE, FormActivity.FormType.TRANSACTION.name)
                .putExtra(UxArgument.SCHEDULED_ACTION_UID, scheduledAction.uID)
                .putExtra(UxArgument.SELECTED_ACCOUNT_UID, accountUID)
                .putExtra(UxArgument.SELECTED_TRANSACTION_UID, scheduledAction.actionUID)
            context.startActivity(intent)
        }

        @SuppressLint("NotifyDataSetChanged")
        override fun deleteSchedule(scheduledAction: ScheduledAction) {
            Timber.i("Removing scheduled transaction")
            val transactionUID = scheduledAction.actionUID!!
            scheduledActionDbAdapter.deleteRecord(scheduledAction.uID!!);
            if (transactionsDbAdapter.deleteRecord(transactionUID)) {
                val context = itemView.context
                Toast.makeText(
                    context,
                    R.string.toast_recurring_transaction_deleted,
                    Toast.LENGTH_LONG
                ).show();
            }
        }
    }
}