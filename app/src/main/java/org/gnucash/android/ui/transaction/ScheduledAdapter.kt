package org.gnucash.android.ui.transaction

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ListAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.gnucash.android.databinding.ListItemScheduledTrxnBinding
import org.gnucash.android.model.ScheduledAction
import org.gnucash.android.ui.adapter.ModelDiff
import org.gnucash.android.ui.common.Refreshable

/**
 * Extends a simple cursor adapter to bind transaction attributes to views
 */
abstract class ScheduledAdapter<VH : ScheduledViewHolder>(protected val refreshable: Refreshable) :
    ListAdapter<ScheduledAction, VH>(ModelDiff<ScheduledAction>()) {

    private var loadJob: Job? = null

    init {
        setHasStableIds(true)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)
        holder.bind(item)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ListItemScheduledTrxnBinding.inflate(inflater, parent, false)
        return createViewHolder(binding, refreshable)
    }

    protected abstract fun createViewHolder(
        binding: ListItemScheduledTrxnBinding,
        refreshable: Refreshable
    ): VH

    fun load(lifecycleOwner: LifecycleOwner) {
        load(lifecycleOwner.lifecycleScope)
    }

    @SuppressLint("NotifyDataSetChanged")
    fun load(lifecycleScope: LifecycleCoroutineScope) {
        loadJob?.cancel()
        loadJob = lifecycleScope.launch(Dispatchers.IO) {
            val records = loadData()
            withContext(Dispatchers.Main) {
                submitList(records)
            }
        }
    }

    protected abstract suspend fun loadData(): List<ScheduledAction>
}
