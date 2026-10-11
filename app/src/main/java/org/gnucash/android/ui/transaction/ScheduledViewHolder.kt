package org.gnucash.android.ui.transaction

import android.content.ContentValues
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import androidx.appcompat.widget.PopupMenu
import androidx.appcompat.widget.SwitchCompat
import androidx.recyclerview.widget.RecyclerView
import org.gnucash.android.R
import org.gnucash.android.app.GnuCashApplication.Companion.shouldBackupTransactions
import org.gnucash.android.app.findActivity
import org.gnucash.android.databinding.ListItemScheduledTrxnBinding
import org.gnucash.android.db.DatabaseSchema.ScheduledActionEntry
import org.gnucash.android.db.adapter.ScheduledActionDbAdapter
import org.gnucash.android.model.ScheduledAction
import org.gnucash.android.service.ScheduledActionService
import org.gnucash.android.ui.common.Refreshable
import org.gnucash.android.ui.snackLong
import org.gnucash.android.util.BackupManager.backupActiveBookAsync
import org.gnucash.android.util.formatMediumDateTime
import org.gnucash.android.util.set

abstract class ScheduledViewHolder(
    protected val binding: ListItemScheduledTrxnBinding,
    protected val refreshable: Refreshable
) : RecyclerView.ViewHolder(binding.root), PopupMenu.OnMenuItemClickListener {
    protected val scheduledActionDbAdapter: ScheduledActionDbAdapter =
        ScheduledActionDbAdapter.instance

    protected val primaryTextView: TextView = binding.primaryText
    protected val descriptionTextView: TextView = binding.secondaryText
    protected val amountTextView: TextView = binding.rightText
    protected val enabledView: SwitchCompat = binding.enabledSwitch
    private val menuView: View = binding.optionsMenu

    private var scheduledAction: ScheduledAction? = null

    init {
        menuView.setOnClickListener { v: View ->
            val popupMenu = PopupMenu(v.context, v)
            popupMenu.setOnMenuItemClickListener(this@ScheduledViewHolder)
            val inflater = popupMenu.menuInflater
            inflater.inflate(R.menu.schedxactions_context_menu, popupMenu.menu)
            popupMenu.show()
        }
    }

    open fun bind(scheduledAction: ScheduledAction) {
        this.scheduledAction = scheduledAction

        enabledView.isChecked = scheduledAction.isEnabled
        enabledView.setOnCheckedChangeListener { _, value ->
            persistEnabled(
                scheduledAction,
                value
            )
        }
    }

    protected fun formatSchedule(scheduledAction: ScheduledAction?): String? {
        if (scheduledAction == null) return null

        val context = itemView.context
        val lastTime = scheduledAction.lastRunDate
        if (lastTime > 0) {
            val endTime = scheduledAction.endDate
            val period = if (endTime > 0 && endTime <= System.currentTimeMillis()) {
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
        return when (item.itemId) {
            R.id.menu_delete -> {
                val action = scheduledAction ?: return false
                deleteAction(action)
                true
            }

            R.id.menu_run -> {
                val action = scheduledAction ?: return false
                runAction(action)
                true
            }

            else -> false
        }
    }

    private fun deleteAction(action: ScheduledAction) {
        val activity = itemView.context.findActivity()
        if (shouldBackupTransactions(activity)) {
            backupActiveBookAsync(activity) {
                deleteSchedule(action)
                refreshable.refresh()
            }
        } else {
            deleteSchedule(action)
            refreshable.refresh()
        }
    }

    protected abstract fun deleteSchedule(scheduledAction: ScheduledAction)

    private fun persistEnabled(scheduledAction: ScheduledAction, enabled: Boolean) {
        scheduledAction.isEnabled = enabled
        val contentValues = ContentValues()
        contentValues[ScheduledActionEntry.COLUMN_ENABLED] = enabled
        scheduledActionDbAdapter.updateRecord(scheduledAction.uid, contentValues)
    }

    private fun runAction(scheduledAction: ScheduledAction) {
        ScheduledActionService.processScheduledAction(
            scheduledActionDbAdapter.holder,
            scheduledAction
        )
        itemView.snackLong(R.string.toast_scheduled_recurring_transaction)
    }
}
