package org.gnucash.android.ui.transaction

import android.annotation.SuppressLint
import android.content.Context
import org.gnucash.android.R
import org.gnucash.android.databinding.ListItemScheduledTrxnBinding
import org.gnucash.android.db.adapter.ScheduledActionDbAdapter
import org.gnucash.android.model.ScheduledAction
import org.gnucash.android.ui.common.FormActivity
import org.gnucash.android.ui.common.Refreshable
import org.gnucash.android.util.getDocumentName
import timber.log.Timber

internal class ScheduledExportViewHolder(
    bookUID: String,
    scheduledActionDbAdapter: ScheduledActionDbAdapter,
    binding: ListItemScheduledTrxnBinding,
    refreshable: Refreshable
) : ScheduledViewHolder(bookUID, scheduledActionDbAdapter, binding, refreshable) {

    override fun bind(scheduledAction: ScheduledAction) {
        super.bind(scheduledAction)
        val context = itemView.context

        val params = scheduledAction.getExportParams()
        if (params == null) {
            primaryTextView.text = null
            descriptionTextView.text = null
            amountTextView.text = null
            itemView.setOnClickListener(null)
            return
        }

        var exportDestination = params.exportLocation?.getDocumentName(context)
        if (exportDestination.isNullOrEmpty()) {
            exportDestination = params.exportLocation.toString()
        }
        val description = context.getString(
            R.string.schedule_export_description,
            context.getString(scheduledAction.actionType.labelId),
            exportDestination
        )
        primaryTextView.text = description.trim()
        descriptionTextView.text = formatSchedule(scheduledAction)
        amountTextView.text = params.exportFormat.name

        itemView.setOnClickListener { editExport(context, scheduledAction) }
    }

    private fun editExport(context: Context, scheduledAction: ScheduledAction) {
        FormActivity.showEditExport(context, bookUID, scheduledAction.uid)
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun deleteSchedule(scheduledAction: ScheduledAction) {
        Timber.i("Removing scheduled export")
        scheduledActionDbAdapter.deleteRecord(scheduledAction)
    }
}