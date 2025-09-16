package org.gnucash.android.ui.transaction

import android.view.MenuItem
import android.view.View
import android.widget.TextView
import androidx.appcompat.widget.PopupMenu
import androidx.recyclerview.widget.RecyclerView
import org.gnucash.android.R
import org.gnucash.android.app.findActivity
import org.gnucash.android.databinding.ListItemScheduledTrxnBinding
import org.gnucash.android.db.adapter.ScheduledActionDbAdapter
import org.gnucash.android.model.ScheduledAction
import org.gnucash.android.ui.common.Refreshable
import org.gnucash.android.util.BackupManager
import org.gnucash.android.util.formatMediumDateTime

abstract class ScheduledViewHolder(
    protected val binding: ListItemScheduledTrxnBinding,
    protected val refreshable: Refreshable
) : RecyclerView.ViewHolder(binding.root), PopupMenu.OnMenuItemClickListener {
    protected val scheduledActionDbAdapter: ScheduledActionDbAdapter =
        ScheduledActionDbAdapter.instance

    protected val primaryTextView: TextView = binding.primaryText
    protected val descriptionTextView: TextView = binding.secondaryText
    protected val amountTextView: TextView = binding.rightText
    private val menuView: View = binding.optionsMenu

    private var scheduledAction: ScheduledAction? = null

    init {
        menuView.setOnClickListener { v: View ->
            val popup = PopupMenu(v.context, v)
            popup.setOnMenuItemClickListener(this@ScheduledViewHolder)
            val inflater = popup.menuInflater
            inflater.inflate(R.menu.schedxactions_context_menu, popup.menu)
            popup.show()
        }
    }

    open fun bind(scheduledAction: ScheduledAction) {
        this.scheduledAction = scheduledAction
    }

    protected fun formatSchedule(scheduledAction: ScheduledAction?): String? {
        if (scheduledAction == null) return null

        val context = itemView.context
        val lastTime = scheduledAction.lastRunTime
        if (lastTime > 0) {
            val endTime = scheduledAction.endTime
            val period = if (endTime > 0 && endTime < System.currentTimeMillis()) {
                context.getString(R.string.label_scheduled_action_ended)
            } else {
                scheduledAction.getRepeatString(context)
            }
            return context.getString(
                R.string.label_scheduled_action,
                period,
                formatMediumDateTime(lastTime)
            )
        }
        return scheduledAction.getRepeatString(context)
    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_delete -> {
                val action = scheduledAction
                if (action != null) {
                    val activity = itemView.context.findActivity()
                    BackupManager.backupActiveBookAsync(activity) {
                        deleteSchedule(action)
                        refreshable.refresh()
                    }
                    return true
                }
                return false
            }

            else -> return false
        }
    }

    protected abstract fun deleteSchedule(scheduledAction: ScheduledAction)
}
