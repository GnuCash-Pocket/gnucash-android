package org.gnucash.android.ui.transaction

import org.gnucash.android.databinding.ListItemScheduledTrxnBinding
import org.gnucash.android.db.adapter.ScheduledActionDbAdapter
import org.gnucash.android.model.ScheduledAction
import org.gnucash.android.ui.common.Refreshable

/**
 * Extends a simple cursor adapter to bind transaction attributes to views
 */
internal class ScheduledTransactionsAdapter(refreshable: Refreshable) :
    ScheduledAdapter<ScheduledTransactionsViewHolder>(refreshable) {

    override suspend fun loadData(): List<ScheduledAction> {
        val databaseAdapter = ScheduledActionDbAdapter.instance
        return databaseAdapter.getRecords(ScheduledAction.ActionType.TRANSACTION)
    }

    override fun createViewHolder(
        binding: ListItemScheduledTrxnBinding,
        refreshable: Refreshable
    ) = ScheduledTransactionsViewHolder(binding, refreshable)
}