package org.gnucash.android.ui.transaction

import android.annotation.SuppressLint
import android.content.Intent
import org.gnucash.android.R
import org.gnucash.android.databinding.ListItemScheduledTrxnBinding
import org.gnucash.android.export.ExportParams
import org.gnucash.android.model.ScheduledAction
import org.gnucash.android.ui.common.FormActivity
import org.gnucash.android.ui.common.Refreshable
import org.gnucash.android.ui.common.UxArgument
import org.gnucash.android.util.getDocumentName
import timber.log.Timber

internal class ScheduledExportViewHolder(
    binding: ListItemScheduledTrxnBinding,
    refreshable: Refreshable
) : ScheduledViewHolder(binding, refreshable) {

    override fun bind(scheduledAction: ScheduledAction) {
        super.bind(scheduledAction)
        val context = itemView.context

        val params = ExportParams.parseTag(scheduledAction.tag!!)
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

        itemView.setOnClickListener { editExport(scheduledAction) }
    }

    private fun editExport(scheduledAction: ScheduledAction) {
        val context = itemView.context
        val intent = Intent(context, FormActivity::class.java)
            .setAction(Intent.ACTION_EDIT)
            .putExtra(UxArgument.FORM_TYPE, FormActivity.FormType.EXPORT.name)
            .putExtra(UxArgument.SCHEDULED_ACTION_UID, scheduledAction.uid)
        context.startActivity(intent)
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun deleteSchedule(scheduledAction: ScheduledAction) {
        Timber.i("Removing scheduled export")
        scheduledActionDbAdapter.deleteRecord(scheduledAction)
    }
}