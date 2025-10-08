/*
 * Copyright (c) 2012 - 2015 Ngewi Fet <ngewif@gmail.com>
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
package org.gnucash.android.ui.account

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.database.Cursor
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.appcompat.app.ActionBar
import androidx.appcompat.widget.PopupMenu
import androidx.appcompat.widget.SearchView
import androidx.core.view.isVisible
import androidx.fragment.app.FragmentResultListener
import androidx.lifecycle.Lifecycle
import androidx.loader.app.LoaderManager
import androidx.loader.content.Loader
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.gnucash.android.R
import org.gnucash.android.app.MenuFragment
import org.gnucash.android.app.actionBar
import org.gnucash.android.app.getSerializableCompat
import org.gnucash.android.databinding.CardviewAccountBinding
import org.gnucash.android.databinding.FragmentAccountsListBinding
import org.gnucash.android.db.DatabaseCursorLoader
import org.gnucash.android.db.DatabaseSchema
import org.gnucash.android.db.adapter.AccountsDbAdapter
import org.gnucash.android.db.getString
import org.gnucash.android.model.Account
import org.gnucash.android.ui.adapter.CursorRecyclerAdapter
import org.gnucash.android.ui.common.FormActivity
import org.gnucash.android.ui.common.Refreshable
import org.gnucash.android.ui.common.UxArgument
import org.gnucash.android.ui.util.AccountBalanceTask
import org.gnucash.android.util.BackupManager.backupActiveBookAsync
import org.gnucash.android.util.set
import timber.log.Timber

/**
 * Fragment for displaying the list of accounts in the database
 *
 * @author Ngewi Fet <ngewif@gmail.com>
 */
class AccountsListFragment : MenuFragment(),
    Refreshable,
    LoaderManager.LoaderCallbacks<Cursor?>,
    SearchView.OnQueryTextListener,
    FragmentResultListener {
    private var accountListAdapter: AccountRecyclerAdapter? = null

    /**
     * Describes the kinds of accounts that should be loaded in the accounts list.
     * This enhances reuse of the accounts list fragment
     */
    enum class DisplayMode {
        TOP_LEVEL, RECENT, FAVORITES
    }

    /**
     * Field indicating which kind of accounts to load.
     * Default value is [DisplayMode.TOP_LEVEL]
     */
    private var displayMode: DisplayMode = DisplayMode.TOP_LEVEL

    /**
     * Database adapter for loading Account records from the database
     */
    private var accountsDbAdapter = AccountsDbAdapter.instance

    /**
     * Listener to be notified when an account is clicked
     */
    private var accountClickedListener: OnAccountClickedListener? = null

    /**
     * Filter for which accounts should be displayed. Used by search interface
     */
    private var currentFilter: String? = null
    private var isShowHiddenAccounts = false

    private var binding: FragmentAccountsListBinding? = null
    private val accountBalanceTasks = mutableListOf<AccountBalanceTask>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val binding = FragmentAccountsListBinding.inflate(inflater, container, false)
        this.binding = binding
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val actionbar: ActionBar? = this.actionBar
        actionbar?.setTitle(R.string.title_accounts)
        actionbar?.setDisplayHomeAsUpEnabled(true)

        val binding = binding!!
        binding.list.setHasFixedSize(true)
        binding.list.emptyView = binding.empty
        binding.list.adapter = accountListAdapter
        binding.list.tag = "accounts"

        when (displayMode) {
            DisplayMode.TOP_LEVEL -> binding.empty.setText(R.string.label_no_accounts)
            DisplayMode.RECENT -> binding.empty.setText(R.string.label_no_recent_accounts)
            DisplayMode.FAVORITES -> binding.empty.setText(R.string.label_no_favorite_accounts)
        }

        val context = binding.list.context
        if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            binding.list.layoutManager = GridLayoutManager(context, 2)
        } else {
            binding.list.layoutManager = LinearLayoutManager(context)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val args = arguments
        if (args != null) {
            displayMode = args.getSerializableCompat(STATE_DISPLAY_MODE, DisplayMode::class.java)
                ?: displayMode
            isShowHiddenAccounts = args.getBoolean(UxArgument.SHOW_HIDDEN, isShowHiddenAccounts)
        }

        if (savedInstanceState != null) {
            displayMode = savedInstanceState.getSerializableCompat(
                STATE_DISPLAY_MODE,
                DisplayMode::class.java
            ) ?: displayMode
        }

        // specify an adapter (see also next example)
        accountListAdapter = AccountRecyclerAdapter(null)
    }

    override fun onStart() {
        super.onStart()
        accountsDbAdapter = AccountsDbAdapter.instance
    }

    override fun onStop() {
        super.onStop()
        binding?.list?.adapter = null
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        try {
            accountClickedListener = context as OnAccountClickedListener
        } catch (_: ClassCastException) {
            throw ClassCastException("$context must implement OnAccountSelectedListener")
        }
    }

    private fun onListItemClick(accountUID: String) {
        accountClickedListener?.accountSelected(accountUID)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (resultCode == Activity.RESULT_CANCELED) return
        refresh()
    }

    /**
     * Delete the account with UID.
     * It shows the delete confirmation dialog if the account has transactions,
     * else deletes the account immediately
     *
     * @param activity   The activity context.
     * @param accountUID The UID of the account
     */
    private fun tryDeleteAccount(activity: Activity?, accountUID: String) {
        if (accountsDbAdapter.getTransactionCount(accountUID) > 0
            || accountsDbAdapter.getSubAccountCount(accountUID) > 0
        ) {
            showConfirmationDialog(accountUID)
        } else {
            backupActiveBookAsync(activity) { result ->
                if (result) {
                    try {
                        // Avoid calling AccountsDbAdapter.deleteRecord(long). See #654
                        accountsDbAdapter.deleteRecord(accountUID)
                        refreshActivity()
                    } catch (e: Exception) {
                        Timber.e(e)
                    }
                }
            }
        }
    }

    /**
     * Shows the delete confirmation dialog
     *
     * @param accountUID Unique ID of account to be deleted after confirmation
     */
    private fun showConfirmationDialog(accountUID: String) {
        val fm = parentFragmentManager
        val fragment = DeleteAccountDialogFragment.newInstance(accountUID)
        fm.setFragmentResultListener(DeleteAccountDialogFragment.TAG, this, this)
        fragment.show(fm, DeleteAccountDialogFragment.TAG)
    }

    private fun toggleFavorite(accountUID: String, isFavoriteAccount: Boolean) {
        val contentValues = ContentValues()
        contentValues[DatabaseSchema.AccountEntry.COLUMN_FAVORITE] = isFavoriteAccount
        accountsDbAdapter.updateRecord(accountUID, contentValues)
        refreshActivity()
    }

    /**
     * Refresh the account list as a sublist of another account
     * @param parentAccountUID GUID of the parent account
     */
    override fun refresh(parentAccountUID: String?) {
        requireArguments().putString(UxArgument.PARENT_ACCOUNT_UID, parentAccountUID)
        refresh()
    }

    /**
     * Refreshes the list by restarting the [DatabaseCursorLoader] associated
     * with the ListView
     */
    override fun refresh() {
        if (isDetached) return
        cancelBalances()
        loaderManager.restartLoader<Cursor>(0, null, this)
    }

    private fun refreshActivity() {
        // Tell the parent activity to refresh all the lists.
        val activity = activity
        if (activity is Refreshable) {
            activity.refresh()
        } else {
            refresh()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putSerializable(STATE_DISPLAY_MODE, displayMode)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        accountListAdapter?.changeCursor(null)
        cancelBalances()
    }

    private fun cancelBalances() {
        for (task in accountBalanceTasks) {
            task.cancel(true)
        }
        accountBalanceTasks.clear()
    }

    /**
     * Opens a new activity for creating or editing an account.
     * If the `accountUID` is empty, then create else edit the account.
     *
     * @param accountUID Unique ID of account to be edited. Pass 0 to create a new account.
     */
    fun openCreateOrEditActivity(context: Context, accountUID: String?) {
        val intent = Intent(context, FormActivity::class.java)
            .setAction(Intent.ACTION_INSERT_OR_EDIT)
            .putExtra(UxArgument.SELECTED_ACCOUNT_UID, accountUID)
            .putExtra(UxArgument.FORM_TYPE, FormActivity.FormType.ACCOUNT.name)
        context.startActivity(intent)
    }

    override fun onCreateLoader(id: Int, args: Bundle?): Loader<Cursor?> {
        Timber.d("Creating the accounts loader")
        val arguments = getArguments()
        val parentAccountUID = arguments?.getString(UxArgument.PARENT_ACCOUNT_UID)

        val context = requireContext()
        return AccountsCursorLoader(
            context,
            parentAccountUID,
            displayMode,
            currentFilter,
            isShowHiddenAccounts
        )
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun onLoadFinished(loader: Loader<Cursor?>, cursor: Cursor?) {
        Timber.d("Accounts loader finished. Swapping in cursor")
        accountListAdapter?.changeCursor(cursor)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            val binding = binding
            if (binding != null && binding.list.adapter == null) {
                binding.list.adapter = accountListAdapter
            }
            accountListAdapter!!.notifyDataSetChanged()
        }
    }

    override fun onLoaderReset(loader: Loader<Cursor?>) {
        Timber.d("Resetting the accounts loader")
        accountListAdapter?.changeCursor(null)
    }

    override fun onQueryTextSubmit(query: String?): Boolean {
        //nothing to see here, move along
        return true
    }

    override fun onQueryTextChange(newText: String?): Boolean {
        val newFilter = newText
        val oldFilter = currentFilter
        if (oldFilter == null && newFilter == null) {
            return true
        }
        if (oldFilter != null && oldFilter == newFilter) {
            return true
        }
        currentFilter = newFilter
        refresh()
        return true
    }

    /**
     * Extends [DatabaseCursorLoader] for loading of [Account] from the
     * database asynchronously.
     *
     * By default it loads only top-level accounts (accounts which have no parent or have GnuCash ROOT account as parent.
     * By submitting a parent account ID in the constructor parameter, it will load child accounts of that parent.
     *
     * Class must be static because the Android loader framework requires it to be so
     *
     * @author Ngewi Fet <ngewif@gmail.com>
     */
    private class AccountsCursorLoader(
        context: Context,
        private val parentAccountUID: String?,
        private val displayMode: DisplayMode,
        private val filter: String?,
        private val isShowHiddenAccounts: Boolean
    ) : DatabaseCursorLoader<AccountsDbAdapter>(context) {
        override fun loadInBackground(): Cursor? {
            val dbAdapter = AccountsDbAdapter.instance
            databaseAdapter = dbAdapter

            val cursor = if (!parentAccountUID.isNullOrEmpty()) {
                dbAdapter.fetchSubAccounts(parentAccountUID, isShowHiddenAccounts)
            } else {
                when (displayMode) {
                    DisplayMode.RECENT ->
                        dbAdapter.fetchRecentAccounts(10, filter, isShowHiddenAccounts)

                    DisplayMode.FAVORITES ->
                        dbAdapter.fetchFavoriteAccounts(filter, isShowHiddenAccounts)

                    DisplayMode.TOP_LEVEL ->
                        dbAdapter.fetchTopLevelAccounts(filter, isShowHiddenAccounts)
                }
            }

            return cursor
        }
    }

    override fun onFragmentResult(requestKey: String, result: Bundle) {
        if (DeleteAccountDialogFragment.TAG == requestKey) {
            val refresh = result.getBoolean(Refreshable.EXTRA_REFRESH)
            if (refresh) refreshActivity()
        }
    }

    fun setShowHiddenAccounts(isVisible: Boolean) {
        val wasVisible = isShowHiddenAccounts
        if (wasVisible != isVisible) {
            isShowHiddenAccounts = isVisible
            refresh()
        }
    }

    internal inner class AccountRecyclerAdapter(cursor: Cursor?) :
        CursorRecyclerAdapter<AccountViewHolder>(cursor) {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AccountViewHolder {
            val binding = CardviewAccountBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
            return AccountViewHolder(binding)
        }

        override fun onBindViewHolderCursor(holder: AccountViewHolder, cursor: Cursor) {
            holder.bind(cursor)
        }
    }

    internal inner class AccountViewHolder(binding: CardviewAccountBinding) :
        RecyclerView.ViewHolder(binding.root), PopupMenu.OnMenuItemClickListener {
        private val accountName: TextView = binding.listItem.primaryText
        private val description: TextView = binding.listItem.secondaryText
        private val accountBalance: TextView = binding.accountBalance
        private val createTransaction: ImageView = binding.createTransaction
        private val favoriteStatus: CheckBox = binding.favoriteStatus
        private val optionsMenu: ImageView = binding.optionsMenu
        private val colorStripView: View = binding.accountColorStrip
        private val budgetIndicator: ProgressBar = binding.budgetIndicator

        private var accountUID: String? = null

        init {
            optionsMenu.setOnClickListener { v ->
                val popup = PopupMenu(v.context, v)
                popup.setOnMenuItemClickListener(this@AccountViewHolder)
                val inflater = popup.menuInflater
                inflater.inflate(R.menu.account_context_menu, popup.menu)
                popup.show()
            }
        }

        fun bind(cursor: Cursor) {
            if (!isResumed) return
            val accountsDbAdapter = this@AccountsListFragment.accountsDbAdapter
            val accountUID = cursor.getString(DatabaseSchema.AccountEntry.COLUMN_UID)!!
            this.accountUID = accountUID
            val account = accountsDbAdapter.getSimpleRecord(accountUID)

            accountName.text = account!!.name
            val subAccountCount = accountsDbAdapter.getSubAccountCount(accountUID)
            if (subAccountCount > 0) {
                description.isVisible = true
                val count = subAccountCount.toInt()
                val text =
                    resources.getQuantityString(R.plurals.label_sub_accounts, count, count)
                description.text = text
            } else {
                description.isVisible = false
            }

            // add a summary of transactions to the account view

            // Make sure the balance task is truly multi-thread
            val task = AccountBalanceTask(
                accountsDbAdapter,
                accountBalance,
                description.currentTextColor
            )
            accountBalanceTasks.add(task)
            task.execute(accountUID)

            @ColorInt val accountColor = getColor(account, accountsDbAdapter)
            colorStripView.setBackgroundColor(accountColor)

            if (account.isPlaceholder) {
                createTransaction.isVisible = false
            } else {
                createTransaction.setOnClickListener { v ->
                    val context = v.context
                    val intent = Intent(context, FormActivity::class.java)
                        .setAction(Intent.ACTION_INSERT_OR_EDIT)
                        .putExtra(UxArgument.SELECTED_ACCOUNT_UID, accountUID)
                        .putExtra(UxArgument.FORM_TYPE, FormActivity.FormType.TRANSACTION.name)
                    context.startActivity(intent)
                }
            }

            // TODO budgets is not an official feature yet.
//                List<Budget> budgets = BudgetsDbAdapter.getInstance().getAccountBudgets(accountUID);
//                //TODO: include fetch only active budgets
//                if (!budgets.isEmpty()) {
//                    Budget budget = budgets.get(0);
//                    Money balance = accountsDbAdapter.getAccountBalance(accountUID, budget.getStartOfCurrentPeriod(), budget.getEndOfCurrentPeriod());
//                    Money budgetAmount = budget.getAmount(accountUID);
//
//                    if (budgetAmount != null) {
//                        double budgetProgress = budgetAmount.isAmountZero() ? 0 : balance.div(budgetAmount).toDouble() * 100;
//                        budgetIndicator.isVisible = true;
//                        budgetIndicator.setProgress((int) budgetProgress);
//                    } else {
//                        budgetIndicator.isVisible = false;
//                    }
//                } else {
//                    budgetIndicator.isVisible = false;
//                }
            val isFavoriteAccount = accountsDbAdapter.isFavoriteAccount(accountUID)
            favoriteStatus.setOnCheckedChangeListener(null)
            favoriteStatus.isChecked = isFavoriteAccount
            favoriteStatus.setOnCheckedChangeListener { _, isChecked ->
                toggleFavorite(accountUID, isChecked)
            }

            itemView.setOnClickListener {
                onListItemClick(accountUID)
            }
        }

        override fun onMenuItemClick(item: MenuItem): Boolean {
            val activity = activity ?: return false
            val accountUID = accountUID ?: return false

            when (item.itemId) {
                R.id.menu_edit -> {
                    openCreateOrEditActivity(activity, accountUID)
                    return true
                }

                R.id.menu_delete -> {
                    tryDeleteAccount(activity, accountUID)
                    return true
                }

                else -> return false
            }
        }

        @ColorInt
        private fun getColor(account: Account, accountsDbAdapter: AccountsDbAdapter): Int {
            @ColorInt var color = account.color
            if (color == Account.DEFAULT_COLOR) {
                color = getParentColor(account, accountsDbAdapter)
            }
            return color
        }

        @ColorInt
        private fun getParentColor(
            account: Account,
            accountsDbAdapter: AccountsDbAdapter
        ): Int {
            val parentUID = account.parentUID ?: return Account.DEFAULT_COLOR
            val parentAccount = accountsDbAdapter.getSimpleRecord(parentUID)!!
            return getColor(parentAccount, accountsDbAdapter)
        }
    }

    companion object {
        /**
         * Tag to save [AccountsListFragment.displayMode] to fragment state
         */
        private const val STATE_DISPLAY_MODE = "display_mode"

        fun newInstance(displayMode: DisplayMode): AccountsListFragment {
            val args = Bundle()
            args.putSerializable(STATE_DISPLAY_MODE, displayMode)
            val fragment = AccountsListFragment()
            fragment.arguments = args
            return fragment
        }
    }
}
