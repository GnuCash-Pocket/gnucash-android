/*
 * Copyright (c) 2012 - 2014 Ngewi Fet <ngewif@gmail.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gnucash.android.ui.transaction

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.database.Cursor
import android.database.SQLException
import android.os.Bundle
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.appcompat.app.ActionBar
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.isVisible
import androidx.fragment.app.FragmentResultListener
import androidx.loader.app.LoaderManager
import androidx.loader.content.Loader
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.gnucash.android.R
import org.gnucash.android.app.GnuCashApplication.Companion.getBookPreferences
import org.gnucash.android.app.GnuCashApplication.Companion.isDoubleEntryEnabled
import org.gnucash.android.app.GnuCashApplication.Companion.shouldBackupTransactions
import org.gnucash.android.app.MenuFragment
import org.gnucash.android.app.actionBar
import org.gnucash.android.databinding.CardviewTransactionBinding
import org.gnucash.android.databinding.FragmentTransactionsListBinding
import org.gnucash.android.db.DatabaseCursorLoader
import org.gnucash.android.db.DatabaseSchema.TransactionEntry
import org.gnucash.android.db.adapter.AccountsDbAdapter
import org.gnucash.android.db.adapter.TransactionsDbAdapter
import org.gnucash.android.db.getString
import org.gnucash.android.model.Transaction
import org.gnucash.android.ui.adapter.CursorRecyclerAdapter
import org.gnucash.android.ui.common.FormActivity
import org.gnucash.android.ui.common.Refreshable
import org.gnucash.android.ui.common.UxArgument
import org.gnucash.android.ui.homescreen.WidgetConfigurationActivity.Companion.updateAllWidgets
import org.gnucash.android.ui.transaction.dialog.BulkMoveDialogFragment
import org.gnucash.android.ui.util.displayBalance
import org.gnucash.android.util.BackupManager.backupActiveBookAsync
import timber.log.Timber

/**
 * List Fragment for displaying list of transactions for an account
 *
 * @author Ngewi Fet <ngewif@gmail.com>
 */
class TransactionsListFragment : MenuFragment(),
    Refreshable,
    LoaderManager.LoaderCallbacks<Cursor>,
    FragmentResultListener {
    private var accountsDbAdapter: AccountsDbAdapter = AccountsDbAdapter.instance
    private var transactionsDbAdapter: TransactionsDbAdapter = TransactionsDbAdapter.instance
    private var accountUID: String? = null
    private var scrollTransactionUID: String? = null

    private var useCompactView = false
    private var useDoubleEntry = true

    private var transactionsAdapter: TransactionCursorAdapter? = null

    private var binding: FragmentTransactionsListBinding? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val context = requireContext()
        val args = requireArguments()
        accountUID = args.getString(UxArgument.SELECTED_ACCOUNT_UID)
        scrollTransactionUID = args.getString(UxArgument.SELECTED_TRANSACTION_UID)

        useDoubleEntry = isDoubleEntryEnabled(context)
        useCompactView = getBookPreferences(context).getBoolean(
            getString(R.string.key_use_compact_list),
            useCompactView
        )
        //if there was a local override of the global setting, respect it
        if (savedInstanceState != null) {
            useCompactView = savedInstanceState.getBoolean(
                getString(R.string.key_use_compact_list),
                useCompactView
            )
        }

        accountsDbAdapter = AccountsDbAdapter.instance
        transactionsDbAdapter = TransactionsDbAdapter.instance
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(getString(R.string.key_use_compact_list), useCompactView)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val binding = FragmentTransactionsListBinding.inflate(inflater, container, false)
        this.binding = binding
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val actionBar: ActionBar? = this.actionBar
        actionBar?.setDisplayShowTitleEnabled(false)
        actionBar?.setDisplayHomeAsUpEnabled(true)

        val binding = this.binding!!
        val context = binding.list.context
        transactionsAdapter = TransactionCursorAdapter(null)

        binding.list.setHasFixedSize(true)
        if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            binding.list.setLayoutManager(GridLayoutManager(context, 2))
        } else {
            binding.list.setLayoutManager(LinearLayoutManager(context))
        }
        binding.list.emptyView = binding.empty
        binding.list.tag = TAG
        binding.list.adapter = transactionsAdapter

        val accountUID = accountUID
        if (accountUID.isNullOrEmpty()) {
            binding.fabAdd.isEnabled = false
        } else {
            binding.fabAdd.setOnClickListener {
                createNewTransaction(context, accountUID)
            }
        }
    }

    /**
     * Refresh the list with transactions from account with ID `accountId`
     *
     * @param uid GUID of account to load transactions from
     */
    override fun refresh(uid: String?) {
        this.accountUID = uid
        refresh()
    }

    /**
     * Reload the list of transactions and recompute account balances
     */
    override fun refresh() {
        if (isDetached || fragmentManager == null) return
        try {
            loaderManager.restartLoader(0, null, this)
        } catch (e: IllegalStateException) {
            // No fragment manager.
            Timber.e(e)
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun showTransactionDetails(
        context: Context,
        transactionUID: String,
        accountUID: String
    ) {
        if (transactionUID.isEmpty() || accountUID.isEmpty()) {
            Timber.w("You must specify both the transaction and account UID")
            return
        }
        val intent = Intent(context, TransactionDetailActivity::class.java)
            .putExtra(UxArgument.SELECTED_TRANSACTION_UID, transactionUID)
            .putExtra(UxArgument.SELECTED_ACCOUNT_UID, accountUID)
        startActivity(intent)
    }

    @Deprecated("Deprecated in Java")
    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.transactions_list_actions, menu)
    }

    @Deprecated("Deprecated in Java")
    override fun onPrepareOptionsMenu(menu: Menu) {
        super.onPrepareOptionsMenu(menu)
        val item = menu.findItem(R.id.menu_toggle_compact)
        item.isChecked = useCompactView
        item.isEnabled = useDoubleEntry //always compact for single-entry
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_toggle_compact -> {
                item.isChecked = !item.isChecked
                useCompactView = item.isChecked
                refresh()
                return true
            }

            else -> return super.onOptionsItemSelected(item)
        }
    }

    override fun onCreateLoader(id: Int, args: Bundle?): Loader<Cursor?> {
        Timber.d("Creating transactions loader")
        return TransactionsCursorLoader(requireContext(), accountUID!!)
    }

    override fun onLoadFinished(loader: Loader<Cursor>, cursor: Cursor?) {
        Timber.d("Transactions loader finished. Swapping in cursor")
        transactionsAdapter?.changeCursor(cursor)
        scrollToTransaction(cursor, scrollTransactionUID)
    }

    override fun onLoaderReset(loader: Loader<Cursor>) {
        Timber.d("Resetting transactions loader")
        transactionsAdapter?.changeCursor(null)
    }

    override fun onFragmentResult(requestKey: String, result: Bundle) {
        if (BulkMoveDialogFragment.TAG == requestKey) {
            val refresh = result.getBoolean(Refreshable.EXTRA_REFRESH)
            if (refresh) refresh()
        }
    }

    /**
     * [DatabaseCursorLoader] for loading transactions asynchronously from the database
     *
     * @author Ngewi Fet <ngewif@gmail.com>
     */
    private class TransactionsCursorLoader(context: Context, private val accountUID: String) :
        DatabaseCursorLoader<TransactionsDbAdapter>(context) {
        override fun loadInBackground(): Cursor? {
            val databaseAdapter = TransactionsDbAdapter.instance
            this.databaseAdapter = databaseAdapter
            val c = databaseAdapter.fetchAllTransactionsForAccount(accountUID)
            if (c != null) registerContentObserver(c)
            return c
        }
    }

    private inner class TransactionCursorAdapter(cursor: Cursor?) :
        CursorRecyclerAdapter<TransactionViewHolder>(cursor) {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TransactionViewHolder {
            val inflater = LayoutInflater.from(parent.context)
            val binding = CardviewTransactionBinding.inflate(inflater, parent, false)
            return TransactionViewHolder(binding)
        }

        override fun onBindViewHolderCursor(holder: TransactionViewHolder, cursor: Cursor) {
            holder.bind(cursor)
        }
    }

    private inner class TransactionViewHolder(binding: CardviewTransactionBinding) :
        RecyclerView.ViewHolder(binding.root), PopupMenu.OnMenuItemClickListener {
        private val primaryText: TextView = binding.listItem2Lines.primaryText
        private val secondaryText: TextView = binding.listItem2Lines.secondaryText
        private val transactionAmount: TextView = binding.transactionAmount
        private val optionsMenu: ImageView = binding.optionsMenu

        //these views are not used in the compact view, hence the nullability
        private val transactionDate: TextView = binding.transactionDate
        private val editTransaction: ImageView = binding.editTransaction

        private var transaction: Transaction? = null

        @ColorInt
        private val colorBalanceZero: Int = transactionAmount.currentTextColor

        init {
            optionsMenu.setOnClickListener { v ->
                val popupMenu = PopupMenu(v.context, v)
                popupMenu.setOnMenuItemClickListener(this@TransactionViewHolder)
                val inflater = popupMenu.menuInflater
                val menu = popupMenu.menu
                inflater.inflate(R.menu.transactions_context_menu, menu)
                menu.findItem(R.id.menu_edit).isVisible = useCompactView || !useDoubleEntry
                popupMenu.show()
            }

            itemView.setOnClickListener {
                val transactionUID = transaction?.uid ?: return@setOnClickListener
                val accountUID = accountUID ?: return@setOnClickListener
                showTransactionDetails(itemView.context, transactionUID, accountUID)
            }
        }

        override fun onMenuItemClick(item: MenuItem): Boolean {
            val transactionUID = transaction?.uid
            if (transactionUID.isNullOrEmpty()) return false
            val accountUID = accountUID
            if (accountUID.isNullOrEmpty()) return false

            return when (item.itemId) {
                R.id.menu_delete -> {
                    deleteTransaction(transactionUID)
                    true
                }

                R.id.menu_duplicate -> {
                    duplicateTransaction(transactionUID)
                    true
                }

                R.id.menu_move -> {
                    moveTransaction(transactionUID, accountUID)
                    true
                }

                R.id.menu_edit -> {
                    editTransaction(transactionUID, accountUID)
                    true
                }

                else -> false
            }
        }

        fun bind(cursor: Cursor) {
            val context = itemView.context
            val accountUID = accountUID!!
            val transaction = transactionsDbAdapter.buildModelInstance(cursor)
            this.transaction = transaction
            val transactionUID = transaction.uid

            primaryText.text = transaction.description

            val amount = transaction.getBalance(accountUID)
            transactionAmount.displayBalance(amount, colorBalanceZero)

            val dateText = getPrettyDateFormat(context, transaction.time)
            transactionDate.text = dateText

            if (useCompactView || !useDoubleEntry) {
                secondaryText.isVisible = false
                editTransaction.isVisible = false
            } else {
                secondaryText.isVisible = true
                editTransaction.isVisible = true

                val splits = transaction.splits
                var text: String? = ""
                var error: String? = null

                if (splits.size == 2) {
                    if (splits[0].isPairOf(splits[1])) {
                        for (split in splits) {
                            if (split.accountUID != accountUID) {
                                text = accountsDbAdapter.getFullyQualifiedAccountName(
                                    split.accountUID!!
                                )
                                break
                            }
                        }
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
                    editTransaction(transactionUID, accountUID)
                }
            }
        }

        /**
         * Formats the date to show the the day of the week if the `dateMillis` is within 7 days
         * of today. Else it shows the actual date formatted as short string. <br></br>
         * It also shows "today", "yesterday" or "tomorrow" if the date is on any of those days
         *
         * @param context
         * @param time
         * @return
         */
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

    private fun deleteTransaction(transactionUID: String) {
        val activity: Activity = requireActivity()
        if (shouldBackupTransactions(activity)) {
            backupActiveBookAsync(activity) { result ->
                transactionsDbAdapter.deleteRecord(transactionUID)
                updateAllWidgets(activity)
                refresh()
            }
        } else {
            transactionsDbAdapter.deleteRecord(transactionUID)
            updateAllWidgets(activity)
            refresh()
        }
    }

    private fun duplicateTransaction(transactionUID: String) {
        try {
            val transaction = transactionsDbAdapter.getRecord(transactionUID)
            val duplicate = transaction.copy()
            duplicate.time = System.currentTimeMillis()
            transactionsDbAdapter.insert(duplicate)
            refresh()
        } catch (e: SQLException) {
            Timber.e(e)
        }
    }

    private fun moveTransaction(transactionUID: String, accountUID: String) {
        val uids = arrayOf(transactionUID)
        val fm = parentFragmentManager
        fm.setFragmentResultListener(BulkMoveDialogFragment.TAG, viewLifecycleOwner, this)
        val fragment = BulkMoveDialogFragment.newInstance(uids, accountUID)
        fragment.show(fm, BulkMoveDialogFragment.TAG)
    }

    private fun editTransaction(transactionUID: String, accountUID: String) {
        val context: Context = requireContext()
        val intent = Intent(context, FormActivity::class.java)
            .putExtra(UxArgument.FORM_TYPE, FormActivity.FormType.TRANSACTION.name)
            .putExtra(UxArgument.SELECTED_TRANSACTION_UID, transactionUID)
            .putExtra(UxArgument.SELECTED_ACCOUNT_UID, accountUID)
        startActivity(intent)
    }

    private fun scrollToTransaction(cursor: Cursor?, scrollTransactionUID: String?) {
        val cursor = cursor ?: return
        val scrollTransactionUID = scrollTransactionUID ?: return
        if (scrollTransactionUID.isEmpty()) return
        val binding = binding ?: return

        var position = 0
        if (cursor.moveToFirst()) {
            do {
                val transactionUID = cursor.getString(TransactionEntry.COLUMN_UID)
                if (transactionUID == scrollTransactionUID) {
                    break
                }
                position++
            } while (cursor.moveToNext())
        }
        cursor.moveToFirst()
        binding.list.scrollToPosition(position)
    }

    private fun createNewTransaction(context: Context, accountUID: String) {
        val intent = Intent(context, FormActivity::class.java)
            .setAction(Intent.ACTION_INSERT_OR_EDIT)
            .putExtra(UxArgument.SELECTED_ACCOUNT_UID, accountUID)
            .putExtra(UxArgument.FORM_TYPE, FormActivity.FormType.TRANSACTION.name)
        startActivity(intent)
    }

    companion object {
        const val TAG = "transactions"
    }
}
