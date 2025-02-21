package org.gnucash.android.ui.transaction

import android.os.Bundle
import android.view.View
import org.gnucash.android.R

class ScheduledTransactionsListFragment : ScheduledActionsListFragment() {

    override fun createAdapter(): ScheduledAdapter<*> {
        return ScheduledTransactionsAdapter(this)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = binding!!
        binding.empty.setText(R.string.label_no_recurring_transactions)
        binding.fabCreateTransaction.visibility = View.GONE
    }

}