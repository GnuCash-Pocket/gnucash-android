package org.gnucash.android.ui.transaction

import android.annotation.SuppressLint
import org.gnucash.android.R
import org.gnucash.android.databinding.ListItemScheduledTrxnBinding
import org.gnucash.android.db.adapter.ScheduledActionDbAdapter
import org.gnucash.android.model.ScheduledAction
import org.gnucash.android.model.Transaction
import org.gnucash.android.ui.common.FormActivity
import org.gnucash.android.ui.common.Refreshable
import org.gnucash.android.ui.snackLong
import timber.log.Timber

internal class ScheduledTransactionsViewHolder(
    bookUID: String,
    scheduledActionDbAdapter: ScheduledActionDbAdapter,
    binding: ListItemScheduledTrxnBinding,
    refreshable: Refreshable
) : ScheduledViewHolder(bookUID, scheduledActionDbAdapter, binding, refreshable) {

    private val transactionsDbAdapter = scheduledActionDbAdapter.transactionsDbAdapter

    override fun bind(scheduledAction: ScheduledAction) {
        super.bind(scheduledAction)
        val context = itemView.context

        val transactionUID = scheduledAction.actionUID!!
        val transaction = try {
            transactionsDbAdapter.getRecord(transactionUID)
        } catch (e: IllegalArgumentException) {
            //if the record could not be found, abort
            Timber.e(
                e,
                "Scheduled transaction with UID $transactionUID could not be found in the db"
            )
            Transaction("")
        }
        val splits = transaction.splits

        var name = scheduledAction.name
        if (name.isEmpty()) name = transaction.description
        primaryTextView.text = name
        descriptionTextView.text = formatSchedule(scheduledAction)

        var text = ""
        val slitsSize = splits.size
        val first = splits.firstOrNull()
        if (slitsSize == 2) {
            for (split in splits) {
                if ((first != null) && (first !== split) && first.isPairOf(split)) {
                    text = first.value.formattedString()
                    break
                }
            }
        }
        if (text.isEmpty()) {
            text = context.getString(R.string.label_split_count, slitsSize)
        }
        amountTextView.text = text

        val accountUID =
            if (slitsSize > 0) first!!.scheduledActionAccountUID ?: first.accountUID else null
        itemView.setOnClickListener {
            if (accountUID != null) {
                editTransaction(scheduledAction, accountUID, transactionUID)
            }
        }
    }

    private fun editTransaction(
        scheduledAction: ScheduledAction,
        accountUID: String,
        transactionUID: String
    ) {
        if (accountUID.isEmpty()) {
            Timber.w("Account UID required")
            return
        }
        if (transactionUID.isEmpty()) {
            Timber.w("Transaction UID required")
            return
        }
        val context = itemView.context
        FormActivity.showEditTransaction(context, bookUID, accountUID, transactionUID)
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun deleteSchedule(scheduledAction: ScheduledAction) {
        Timber.i("Removing scheduled transaction")
        val transactionUID = scheduledAction.actionUID ?: return
        scheduledActionDbAdapter.deleteRecord(scheduledAction)
        if (transactionsDbAdapter.deleteRecord(transactionUID)) {
            itemView.snackLong(R.string.toast_recurring_transaction_deleted)
        }
    }
}