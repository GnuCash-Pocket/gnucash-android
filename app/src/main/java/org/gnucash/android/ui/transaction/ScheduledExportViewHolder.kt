package org.gnucash.android.ui.transaction

import android.annotation.SuppressLint
import android.content.Intent
import android.view.View
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

        val params = ExportParams.parseTag(scheduledAction.tag)
        var exportDestination = params.exportTarget.description
        if (params.exportTarget == ExportParams.ExportTarget.URI) {
            exportDestination =
                exportDestination + " (" + params.exportLocation.getDocumentName(context) + ")"
        }
        val description = context.getString(
            R.string.schedule_export_description,
            params.exportFormat.name,
            context.getString(scheduledAction.actionType.labelId),
            exportDestination
        )
        primaryTextView.text = description
        descriptionTextView.text = formatSchedule(scheduledAction)
        amountTextView.visibility = View.GONE

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
        scheduledActionDbAdapter.deleteRecord(scheduledAction.uid)
    }
}