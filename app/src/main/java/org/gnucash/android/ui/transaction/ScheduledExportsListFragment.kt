package org.gnucash.android.ui.transaction

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import org.gnucash.android.R
import org.gnucash.android.ui.common.FormActivity
import org.gnucash.android.ui.common.UxArgument

class ScheduledExportsListFragment : ScheduledActionsListFragment() {

    override fun createAdapter(): ScheduledAdapter<*> {
        return ScheduledExportAdapter(this)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = binding!!
        binding.empty.setText(R.string.label_no_scheduled_exports_to_display)
        binding.fabCreateTransaction.setOnClickListener {
            addExport(it.context)
        }
    }

    private fun addExport(context: Context) {
        val intent = Intent(context, FormActivity::class.java)
            .putExtra(UxArgument.FORM_TYPE, FormActivity.FormType.EXPORT.name)
        startActivity(intent)
    }

}