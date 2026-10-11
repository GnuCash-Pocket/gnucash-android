package org.gnucash.android.ui.transaction

import android.content.Context
import android.os.Bundle
import android.view.View
import org.gnucash.android.R
import org.gnucash.android.ui.common.FormActivity

class ScheduledExportsListFragment : ScheduledActionsListFragment() {

    override fun createAdapter(): ScheduledAdapter<*> {
        return ScheduledExportAdapter(bookUID, scheduledActionDbAdapter, this)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = binding!!
        binding.empty.setText(R.string.label_no_scheduled_exports_to_display)
        binding.fabAdd.setOnClickListener {
            addExport(it.context)
        }
    }

    private fun addExport(context: Context) {
        FormActivity.showExport(context, bookUID)
    }
}