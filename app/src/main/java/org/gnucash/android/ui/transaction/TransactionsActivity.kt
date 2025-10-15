/*
 * Copyright (c) 2012 - 2015 Ngewi Fet <ngewif@gmail.com>
 * Copyright (c) 2014 Yongxin Wang <fefe.wyx@gmail.com>
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

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.appcompat.app.ActionBar
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentResultListener
import com.google.android.material.tabs.TabLayoutMediator
import org.gnucash.android.R
import org.gnucash.android.databinding.ActivityTransactionsBinding
import org.gnucash.android.db.DatabaseSchema.AccountEntry
import org.gnucash.android.db.adapter.AccountsDbAdapter
import org.gnucash.android.db.adapter.TransactionsDbAdapter
import org.gnucash.android.model.Account
import org.gnucash.android.ui.account.AccountsListFragment
import org.gnucash.android.ui.account.DeleteAccountDialogFragment
import org.gnucash.android.ui.account.OnAccountClickedListener
import org.gnucash.android.ui.adapter.QualifiedAccountNameAdapter
import org.gnucash.android.ui.common.BaseDrawerActivity
import org.gnucash.android.ui.common.FormActivity
import org.gnucash.android.ui.common.Refreshable
import org.gnucash.android.ui.common.UxArgument
import org.gnucash.android.ui.util.widget.FragmentStateAdapter
import org.gnucash.android.util.BackupManager.backupActiveBookAsync
import timber.log.Timber

/**
 * Activity for displaying, creating and editing transactions
 *
 * @author Ngewi Fet <ngewif@gmail.com>
 */
class TransactionsActivity : BaseDrawerActivity(),
    Refreshable,
    OnAccountClickedListener,
    FragmentResultListener {
    /**
     * GUID of [Account] whose transactions are displayed
     */
    private var account: Account? = null

    /**
     * Account database adapter for manipulating the accounts list in navigation
     */
    private var accountsDbAdapter = AccountsDbAdapter.instance
    private var transactionsDbAdapter = TransactionsDbAdapter.instance
    private var accountNameAdapter: QualifiedAccountNameAdapter? = null

    private var isShowHiddenAccounts = false

    private lateinit var binding: ActivityTransactionsBinding

    private val accountSpinnerListener: AdapterView.OnItemSelectedListener =
        object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>,
                view: View?,
                position: Int,
                id: Long
            ) {
                val account = accountNameAdapter?.getAccount(position)
                swapAccount(account)
            }

            override fun onNothingSelected(parent: AdapterView<*>) = Unit
        }

    private var pagerAdapter: AccountViewPagerAdapter? = null

    /**
     * Adapter for managing the sub-account and transaction fragment pages in the accounts view
     */
    private inner class AccountViewPagerAdapter(activity: FragmentActivity, val account: Account) :
        FragmentStateAdapter(activity) {
        private val accountUID = account.uid

        override fun createFragment(position: Int): Fragment {
            return when (position) {
                INDEX_SUB_ACCOUNTS_FRAGMENT -> prepareSubAccountsListFragment(accountUID)
                INDEX_TRANSACTIONS_FRAGMENT -> prepareTransactionsListFragment(accountUID)
                else -> throw IndexOutOfBoundsException()
            }
        }

        override fun getItemCount(): Int {
            if (account.isPlaceholder) {
                return 1
            }
            return NUM_PAGES
        }

        /**
         * Creates and initializes the fragment for displaying sub-account list
         *
         * @return [AccountsListFragment] initialized with the sub-accounts
         */
        fun prepareSubAccountsListFragment(accountUID: String): AccountsListFragment {
            val args = Bundle()
            args.putString(UxArgument.PARENT_ACCOUNT_UID, accountUID)
            args.putBoolean(UxArgument.SHOW_HIDDEN, isShowHiddenAccounts)
            val fragment = AccountsListFragment()
            fragment.arguments = args
            return fragment
        }

        /**
         * Creates and initializes fragment for displaying transactions
         *
         * @return [TransactionsListFragment] initialized with the current account transactions
         */
        fun prepareTransactionsListFragment(accountUID: String): TransactionsListFragment {
            Timber.i("Opening transactions for account: %s", accountUID)
            val args = Bundle()
            args.putString(UxArgument.SELECTED_ACCOUNT_UID, accountUID)
            val fragment = TransactionsListFragment()
            fragment.arguments = args
            return fragment
        }
    }

    override val titleRes: Int = R.string.title_transactions

    override fun onFragmentResult(requestKey: String, result: Bundle) {
        if (DeleteAccountDialogFragment.TAG == requestKey) {
            val refresh = result.getBoolean(Refreshable.EXTRA_REFRESH)
            if (refresh) {
                finish()
            }
        }
    }

    /**
     * Refreshes the fragments currently in the transactions activity
     */
    override fun refresh(uid: String?) {
        val binding = this.binding
        setTitleIndicatorColor(binding)

        val fragments = supportFragmentManager.fragments
        for (fragment in fragments) {
            if (fragment is Refreshable) {
                fragment.refresh(uid)
            }
        }

        pagerAdapter?.notifyDataSetChanged()

        binding.toolbarLayout.toolbarSpinner.setEnabled(!accountNameAdapter!!.isEmpty)
    }

    override fun refresh() {
        val accountUID = account?.uid ?: intent.getStringExtra(UxArgument.SELECTED_ACCOUNT_UID)
        refresh(accountUID)
    }

    override fun inflateView() {
        this.binding = ActivityTransactionsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        drawerLayout = binding.drawerLayout
        navigationView = binding.navView
        toolbar = binding.toolbarLayout.toolbar
        toolbarProgress = binding.toolbarLayout.toolbarProgress.progress
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val actionBar: ActionBar? = supportActionBar
        actionBar?.setDisplayShowTitleEnabled(false)
        actionBar?.setDisplayHomeAsUpEnabled(true)

        accountsDbAdapter = AccountsDbAdapter.instance
        transactionsDbAdapter = TransactionsDbAdapter.instance

        isShowHiddenAccounts =
            intent.getBooleanExtra(UxArgument.SHOW_HIDDEN, isShowHiddenAccounts)

        val account = requireAccount()
        val accountUID = account.uid

        val tabLayout = binding.tabLayout
        tabLayout.addTab(tabLayout.newTab())

        setupActionBarNavigation(binding, accountUID)

        pagerAdapter = AccountViewPagerAdapter(this, account)
        binding.pager.adapter = pagerAdapter
        TabLayoutMediator(tabLayout, binding.pager) { tab, position ->
            when (position) {
                INDEX_SUB_ACCOUNTS_FRAGMENT -> tab.setText(R.string.section_header_subaccounts)
                INDEX_TRANSACTIONS_FRAGMENT -> tab.setText(R.string.section_header_transactions)
            }
        }.attach()

        binding.fabCreateTransaction.setOnClickListener {
            when (binding.pager.currentItem) {
                INDEX_SUB_ACCOUNTS_FRAGMENT -> createNewAccount(accountUID)
                INDEX_TRANSACTIONS_FRAGMENT -> createNewTransaction(accountUID)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    /**
     * Sets the color for the ViewPager title indicator to match the account color
     */
    private fun setTitleIndicatorColor(binding: ActivityTransactionsBinding) {
        val account = this.account ?: return
        @ColorInt var accountColor = account.color
        if (accountColor == Account.DEFAULT_COLOR) {
            accountColor = accountsDbAdapter.getActiveAccountColor(this, account.uid)
        }
        setTitlesColor(accountColor)
        binding.tabLayout.setBackgroundColor(accountColor)
    }

    /**
     * Set up action bar navigation list and listener callbacks
     */
    private fun setupActionBarNavigation(
        binding: ActivityTransactionsBinding,
        accountUID: String
    ) {
        var accountNameAdapter = accountNameAdapter
        if (accountNameAdapter == null) {
            val contextWithTheme = binding.toolbarLayout.toolbarSpinner.context
            accountNameAdapter =
                QualifiedAccountNameAdapter(contextWithTheme, accountsDbAdapter, this)
                    .load { adapter ->
                        val position = adapter.getPosition(accountUID)
                        binding.toolbarLayout.toolbarSpinner.setSelection(position)
                    }
            this.accountNameAdapter = accountNameAdapter
        }
        binding.toolbarLayout.toolbarSpinner.adapter = accountNameAdapter
        binding.toolbarLayout.toolbarSpinner.onItemSelectedListener = accountSpinnerListener
        updateNavigationSelection(binding, accountUID)
        setTitleIndicatorColor(binding)
    }

    /**
     * Updates the action bar navigation list selection to that of the current account
     * whose transactions are being displayed/manipulated
     */
    private fun updateNavigationSelection(
        binding: ActivityTransactionsBinding,
        accountUID: String
    ) {
        val accountNameAdapter = accountNameAdapter ?: return
        if (accountNameAdapter.isEmpty) return
        var account = accountsDbAdapter.getRecordOrNull(accountUID)
        // In case the account was deleted.
        var position = accountNameAdapter.getPosition(accountUID)
        if (position == AdapterView.INVALID_POSITION && account != null) {
            val parentUID = account.parentUID
            if (!parentUID.isNullOrEmpty()) {
                position = accountNameAdapter.getPosition(parentUID)
                account = accountNameAdapter.getAccount(position)
            }
        }
        if (account == null) {
            Timber.e("Account not found")
            finish()
            return
        }
        this.account = account
        // Spinner will call `swapAccount` in its listener.
        binding.toolbarLayout.toolbarSpinner.setSelection(position)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        super.onCreateOptionsMenu(menu)
        menuInflater.inflate(R.menu.sub_account_actions, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        val account = account ?: return false
        val favoriteAccountMenuItem = menu.findItem(R.id.menu_favorite)
        if (favoriteAccountMenuItem == null)  //when the activity is used to edit a transaction
            return false

        val isFavoriteAccount = account.isFavorite
        @DrawableRes val favoriteIcon =
            if (isFavoriteAccount) R.drawable.ic_favorite else R.drawable.ic_favorite_border
        favoriteAccountMenuItem.setIcon(favoriteIcon)
        favoriteAccountMenuItem.isChecked = isFavoriteAccount

        val itemHidden = menu.findItem(R.id.menu_hidden)
        if (itemHidden != null) {
            showHiddenAccounts(itemHidden, isShowHiddenAccounts)
        }

        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_favorite -> {
                val account = account ?: return false
                toggleFavorite(account)
                return true
            }

            R.id.menu_edit -> {
                val account = account ?: return false
                editAccount(account.uid)
                return true
            }

            R.id.menu_delete -> {
                val account = account ?: return false
                deleteAccount(account.uid)
                return true
            }

            R.id.menu_hidden -> {
                toggleHidden(item)
                return true
            }

            else -> return super.onOptionsItemSelected(item)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (resultCode == RESULT_OK) {
            refresh()
            return
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    private fun createNewAccount(parentAccountUID: String) {
        val intent = Intent(this, FormActivity::class.java)
            .setAction(Intent.ACTION_INSERT_OR_EDIT)
            .putExtra(UxArgument.PARENT_ACCOUNT_UID, parentAccountUID)
            .putExtra(UxArgument.FORM_TYPE, FormActivity.FormType.ACCOUNT.name)
        startActivityForResult(intent, REQUEST_REFRESH)
    }

    private fun createNewTransaction(accountUID: String) {
        val intent = Intent(this, FormActivity::class.java)
            .setAction(Intent.ACTION_INSERT_OR_EDIT)
            .putExtra(UxArgument.SELECTED_ACCOUNT_UID, accountUID)
            .putExtra(UxArgument.FORM_TYPE, FormActivity.FormType.TRANSACTION.name)
        startActivityForResult(intent, REQUEST_REFRESH)
    }

    override fun accountSelected(accountUID: String) {
        val intent = Intent(this, TransactionsActivity::class.java)
            .setAction(Intent.ACTION_VIEW)
            .putExtra(UxArgument.SELECTED_ACCOUNT_UID, accountUID)
            .putExtra(UxArgument.SHOW_HIDDEN, isShowHiddenAccounts)
        startActivity(intent)
    }

    private fun toggleFavorite(account: Account) {
        val accountId = account.id
        val isFavorite = !account.isFavorite
        //toggle favorite preference
        account.isFavorite = isFavorite
        accountsDbAdapter.updateAccount(accountId, AccountEntry.COLUMN_FAVORITE, isFavorite)
        supportInvalidateOptionsMenu()
    }

    private fun editAccount(accountUID: String?) {
        val editAccountIntent = Intent(this, FormActivity::class.java)
            .setAction(Intent.ACTION_INSERT_OR_EDIT)
            .putExtra(UxArgument.SELECTED_ACCOUNT_UID, accountUID)
            .putExtra(UxArgument.FORM_TYPE, FormActivity.FormType.ACCOUNT.name)
        startActivityForResult(editAccountIntent, REQUEST_REFRESH)
    }

    /**
     * Delete the account with UID.
     * It shows the delete confirmation dialog if the account has transactions,
     * else deletes the account immediately
     *
     * @param accountUID The UID of the account
     */
    private fun deleteAccount(accountUID: String) {
        if (accountsDbAdapter.getTransactionCount(accountUID) > 0
            || accountsDbAdapter.getSubAccountCount(accountUID) > 0
        ) {
            showConfirmationDialog(accountUID)
        } else {
            backupActiveBookAsync(this) { result ->
                // Avoid calling AccountsDbAdapter.deleteRecord(long). See #654
                if (accountsDbAdapter.deleteRecord(accountUID)) {
                    finish()
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
        val fm = supportFragmentManager
        val fragment = DeleteAccountDialogFragment.newInstance(accountUID)
        fm.setFragmentResultListener(DeleteAccountDialogFragment.TAG, this, this)
        fragment.show(fm, DeleteAccountDialogFragment.TAG)
    }

    private fun toggleHidden(item: MenuItem) {
        showHiddenAccounts(item, !item.isChecked)
    }

    private fun showHiddenAccounts(item: MenuItem, isVisible: Boolean) {
        item.isChecked = isVisible
        @DrawableRes val visibilityIcon =
            if (isVisible) R.drawable.ic_visibility_off else R.drawable.ic_visibility
        item.setIcon(visibilityIcon)
        isShowHiddenAccounts = isVisible
        // apply to each page
        val fragments = supportFragmentManager.fragments
        for (fragment in fragments) {
            if (fragment is AccountsListFragment) {
                fragment.setShowHiddenAccounts(isVisible)
            }
        }
    }

    private fun swapAccount(account: Account?) {
        val binding = this.binding
        this.account = account
        if (account != null) {
            val accountUID = account.uid
            //update the intent in case the account gets rotated
            intent.putExtra(UxArgument.SELECTED_ACCOUNT_UID, accountUID)
            //TODO pagerAdapter?.notifyDataSetChanged()
            val tabLayout = binding.tabLayout
            if (account.isPlaceholder) {
                if (tabLayout.tabCount > 1) {
                    tabLayout.removeTabAt(INDEX_TRANSACTIONS_FRAGMENT)
                }
            } else {
                if (tabLayout.tabCount < 2) {
                    tabLayout.addTab(tabLayout.newTab())
                }
            }

            //if there are no transactions, and there are sub-accounts, show the sub-accounts
            val txCount = transactionsDbAdapter.getTransactionsCount(accountUID)
            if (txCount == 0) {
                val subCount = accountsDbAdapter.getSubAccountCount(accountUID)
                if ((subCount > 0) || (binding.tabLayout.tabCount < 2)) {
                    binding.pager.currentItem = INDEX_SUB_ACCOUNTS_FRAGMENT
                } else {
                    binding.pager.currentItem = INDEX_TRANSACTIONS_FRAGMENT
                }
            } else {
                binding.pager.currentItem = INDEX_TRANSACTIONS_FRAGMENT
            }

            //refresh any fragments in the tab with the new account UID
            refresh(accountUID)
        } else {
            //refresh any fragments in the tab with the new account UID
            refresh()
        }
        supportInvalidateOptionsMenu()
    }

    private fun requireAccount(): Account {
        var account = this.account
        if (account != null) {
            return account
        }
        var accountUID = intent.getStringExtra(UxArgument.SELECTED_ACCOUNT_UID)
        if (accountUID.isNullOrEmpty()) {
            Timber.w("Account UID expected for intent %s", intent)
            accountUID = accountsDbAdapter.rootAccountUID
        }
        try {
            account = accountsDbAdapter.getRecord(accountUID)
            this.account = account
        } catch (e: IllegalArgumentException) {
            Timber.e(e)
        }
        if (account == null) {
            throw IllegalArgumentException("Account required")
        }
        return account
    }

    companion object {
        /**
         * ViewPager index for sub-accounts fragment
         */
        private const val INDEX_SUB_ACCOUNTS_FRAGMENT = 0

        /**
         * ViewPager index for transactions fragment
         */
        private const val INDEX_TRANSACTIONS_FRAGMENT = 1

        /**
         * Number of pages to show
         */
        private const val NUM_PAGES = 2

        private const val REQUEST_REFRESH = 0x0000

        /**
         * Displays the form for searching transactions
         */
        fun openSearchFragment(context: Context) {
            val intent = Intent(context, FormActivity::class.java)
                .putExtra(UxArgument.FORM_TYPE, FormActivity.FormType.SEARCH_FORM.name)
            context.startActivity(intent)
        }
    }
}
