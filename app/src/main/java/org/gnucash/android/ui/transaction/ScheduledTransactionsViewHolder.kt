package org.gnucash.android.ui.transaction

import android.annotation.SuppressLint
import android.content.Intent
import android.widget.Toast
import org.gnucash.android.R
import org.gnucash.android.databinding.ListItemScheduledTrxnBinding
import org.gnucash.android.db.adapter.TransactionsDbAdapter
import org.gnucash.android.model.ScheduledAction
import org.gnucash.android.model.Transaction
import org.gnucash.android.ui.common.FormActivity
import org.gnucash.android.ui.common.Refreshable
import org.gnucash.android.ui.common.UxArgument
import timber.log.Timber

internal class ScheduledTransactionsViewHolder(
    binding: ListItemScheduledTrxnBinding,
    refreshable: Refreshable
) : ScheduledViewHolder(binding, refreshable) {

    private val transactionsDbAdapter = TransactionsDbAdapter.getInstance()

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
            Transaction(null)
        }
        val splits = transaction.splits

        primaryTextView.text = transaction.description
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
        val context = itemView.context
        val intent = Intent(context, FormActivity::class.java)
            .setAction(Intent.ACTION_INSERT_OR_EDIT)
            .putExtra(UxArgument.FORM_TYPE, FormActivity.FormType.TRANSACTION.name)
            .putExtra(UxArgument.SCHEDULED_ACTION_UID, scheduledAction.uid)
            .putExtra(UxArgument.SELECTED_ACCOUNT_UID, accountUID)
            .putExtra(UxArgument.SELECTED_TRANSACTION_UID, transactionUID)
        context.startActivity(intent)
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun deleteSchedule(scheduledAction: ScheduledAction) {
        Timber.i("Removing scheduled transaction")
        val transactionUID = scheduledAction.actionUID ?: return
        scheduledActionDbAdapter.deleteRecord(scheduledAction)
        if (transactionsDbAdapter.deleteRecord(transactionUID)) {
            val context = itemView.context
            Toast.makeText(
                context,
                R.string.toast_recurring_transaction_deleted,
                Toast.LENGTH_LONG
            ).show()
        }
    }
}