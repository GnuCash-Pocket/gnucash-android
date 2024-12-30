package org.gnucash.android.ui.transaction

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.View
import androidx.loader.content.Loader
import org.gnucash.android.R
import org.gnucash.android.databinding.ListItemScheduledTrxnBinding
import org.gnucash.android.db.DatabaseCursorLoader
import org.gnucash.android.db.DatabaseSchema
import org.gnucash.android.db.adapter.ScheduledActionDbAdapter
import org.gnucash.android.export.ExportParams
import org.gnucash.android.model.ScheduledAction
import org.gnucash.android.ui.common.FormActivity
import org.gnucash.android.ui.common.Refreshable
import org.gnucash.android.ui.common.UxArgument
import org.gnucash.android.util.getDocumentName
import timber.log.Timber

class ScheduledExportsListFragment : ScheduledActionsListFragment() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        listAdapter = ScheduledExportCursorAdapter(this)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.empty.setText(R.string.label_no_scheduled_exports_to_display)
    }

    override fun onCreateLoader(id: Int, args: Bundle?): Loader<Cursor> {
        return ScheduledExportCursorLoader(requireContext())
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.scheduled_export_actions, menu)
    }

    /**
     * [DatabaseCursorLoader] for loading recurring transactions asynchronously from the database
     *
     * @author Ngewi Fet <ngewif></ngewif>@gmail.com>
     */
    private class ScheduledExportCursorLoader(context: Context) : DatabaseCursorLoader<ScheduledActionDbAdapter>(context) {
        init {
            databaseAdapter = ScheduledActionDbAdapter.getInstance()
        }

        override fun loadInBackground(): Cursor? {
            if (databaseAdapter == null) return null
            val cursor = databaseAdapter.fetchAllRecords(
                DatabaseSchema.ScheduledActionEntry.COLUMN_TYPE + "=?",
                arrayOf(ScheduledAction.ActionType.BACKUP.name), null
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
    private class ScheduledExportCursorAdapter(refreshable: Refreshable) :
        ScheduledCursorAdapter<ScheduledExportViewHolder>(refreshable) {

        override fun createViewHolder(
            binding: ListItemScheduledTrxnBinding,
            refreshable: Refreshable
        ) = ScheduledExportViewHolder(binding, refreshable)
    }

    private class ScheduledExportViewHolder(
        binding: ListItemScheduledTrxnBinding,
        refreshable: Refreshable
    ) : ScheduledViewHolder(binding, refreshable) {

        override fun bind(scheduledAction: ScheduledAction) {
            super.bind(scheduledAction)
            val context = itemView.context

            val params = ExportParams.parseCsv(scheduledAction.tag)
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
                .putExtra(UxArgument.SCHEDULED_ACTION_UID, scheduledAction.uID)
            context.startActivity(intent)
        }

        @SuppressLint("NotifyDataSetChanged")
        override fun deleteSchedule(scheduledAction: ScheduledAction) {
            Timber.i("Removing scheduled export")
            scheduledActionDbAdapter.deleteRecord(scheduledAction.uID!!)
        }
    }
}