package org.gnucash.android.ui.transaction

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.gnucash.android.databinding.ListItemScheduledTrxnBinding
import org.gnucash.android.model.ScheduledAction
import org.gnucash.android.ui.common.Refreshable

/**
 * Extends a simple cursor adapter to bind transaction attributes to views
 */
abstract class ScheduledAdapter<VH : ScheduledViewHolder>(protected val refreshable: Refreshable) :
    RecyclerView.Adapter<VH>() {

    private val data = mutableListOf<ScheduledAction>()
    private var loadJob: Job? = null

    init {
        setHasStableIds(true)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        if (position >= data.size) {
            return
        }
        val item = data[position]
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

    override fun getItemCount(): Int {
        return data.size
    }

    override fun getItemId(position: Int): Long {
        if (position >= data.size) {
            return 0
        }
        val item = data[position]
        return item.id
    }

    fun load(lifecycleOwner: LifecycleOwner) {
        load(lifecycleOwner.lifecycleScope)
    }

    @SuppressLint("NotifyDataSetChanged")
    fun load(lifecycleScope: LifecycleCoroutineScope) {
        loadJob?.cancel()
        loadJob = lifecycleScope.launch(Dispatchers.IO) {
            val records = loadData()
            data.clear()
            data.addAll(records)
            lifecycleScope.launch(Dispatchers.Main) {
                notifyDataSetChanged()
            }
        }
    }

    protected abstract suspend fun loadData(): List<ScheduledAction>
}
