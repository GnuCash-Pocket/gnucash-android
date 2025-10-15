package org.gnucash.android.ui.search

import android.content.Context
import android.database.Cursor
import android.text.format.DateUtils
import android.view.MenuItem
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import org.gnucash.android.R
import org.gnucash.android.databinding.CardviewTransactionBinding
import org.gnucash.android.db.adapter.AccountsDbAdapter
import org.gnucash.android.db.adapter.TransactionsDbAdapter
import org.gnucash.android.model.Transaction
import org.gnucash.android.ui.util.displayBalance

enum class SearchResultAction {
    View,
    Edit,
    Delete,
    Duplicate,
    Move
}

typealias SearchResultCallback = (Transaction, SearchResultAction) -> Unit

class SearchResultViewHolder(
    binding: CardviewTransactionBinding,
    private val isDoubleEntry: Boolean = true,
    private val callback: SearchResultCallback
) : RecyclerView.ViewHolder(binding.root),
    PopupMenu.OnMenuItemClickListener {
    private val primaryText: TextView = binding.listItem2Lines.primaryText
    private val secondaryText: TextView = binding.listItem2Lines.secondaryText
    private val transactionAmount: TextView = binding.transactionAmount
    private val optionsMenu: ImageView = binding.optionsMenu

    //these views are not used in the compact view, hence the nullability
    private val transactionDate: TextView = binding.transactionDate
    private val editTransaction: ImageView = binding.editTransaction

    private var transaction: Transaction? = null
    private val accountsDbAdapter: AccountsDbAdapter = AccountsDbAdapter.instance
    private val transactionsDbAdapter: TransactionsDbAdapter =
        accountsDbAdapter.transactionsDbAdapter

    @ColorInt
    private val colorBalanceZero: Int = transactionAmount.currentTextColor

    init {
        optionsMenu.setOnClickListener { v ->
            val popupMenu = PopupMenu(v.context, v)
            popupMenu.setOnMenuItemClickListener(this@SearchResultViewHolder)
            val inflater = popupMenu.menuInflater
            val menu = popupMenu.menu
            inflater.inflate(R.menu.transactions_context_menu, menu)
            menu.findItem(R.id.menu_edit).isVisible = false
            menu.findItem(R.id.menu_move).isVisible = false
            popupMenu.show()
        }

        itemView.setOnClickListener {
            val transaction = transaction ?: return@setOnClickListener
            callback(transaction, SearchResultAction.View)
        }
    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        val transaction = transaction ?: return false

        return when (item.itemId) {
            R.id.menu_delete -> {
                callback(transaction, SearchResultAction.Delete)
                true
            }

            R.id.menu_duplicate -> {
                callback(transaction, SearchResultAction.Duplicate)
                true
            }

            R.id.menu_edit -> {
                callback(transaction, SearchResultAction.Edit)
                true
            }

            R.id.menu_move -> {
                callback(transaction, SearchResultAction.Move)
                true
            }

            else -> false
        }
    }

    fun bind(cursor: Cursor) {
        val context = itemView.context
        val transaction = transactionsDbAdapter.buildModelInstance(cursor)
        this.transaction = transaction

        primaryText.text = transaction.description
        transactionDate.text = getPrettyDateFormat(context, transaction.time)
        transactionAmount.isVisible = false

        if (isDoubleEntry) {
            secondaryText.isVisible = true
            editTransaction.isVisible = true

            val splits = transaction.splits
            var text: String? = ""
            var error: String? = null

            if (splits.size == 2) {
                val accountUID = transaction.defaultAccountUID
                if (accountUID != null) {
                    text = accountsDbAdapter.getFullyQualifiedAccountName(accountUID)

                    val amount = transaction.getBalance(accountUID)
                    transactionAmount.displayBalance(amount, colorBalanceZero)
                    transactionAmount.isVisible = true
                }
                if (text.isNullOrEmpty()) {
                    text = context.getString(R.string.label_split_count, splits.size)
                    error = context.getString(R.string.imbalance_account_name)
                }
            } else if (splits.size > 2) {
                text = context.getString(R.string.label_split_count, splits.size)
            }
            secondaryText.text = text
            secondaryText.error = error

            editTransaction.setOnClickListener {
                callback(transaction, SearchResultAction.Edit)
            }
        } else {
            secondaryText.isVisible = false
            editTransaction.isVisible = false
        }
    }

    private fun getPrettyDateFormat(context: Context, time: Long): String {
        return DateUtils.getRelativeDateTimeString(
            context,
            time,
            DateUtils.MINUTE_IN_MILLIS,
            DateUtils.WEEK_IN_MILLIS,
            0
        ).toString()
    }
}