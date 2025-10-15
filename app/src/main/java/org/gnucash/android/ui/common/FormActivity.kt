/*
 * Copyright (c) 2015 Ngewi Fet <ngewif@gmail.com>
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
package org.gnucash.android.ui.common

import android.os.Bundle
import android.view.MenuItem
import androidx.annotation.ColorInt
import androidx.appcompat.app.ActionBar
import androidx.fragment.app.Fragment
import org.gnucash.android.R
import org.gnucash.android.app.GnuCashApplication.Companion.activeBookUID
import org.gnucash.android.app.isNullOrEmpty
import org.gnucash.android.databinding.ActivityFormBinding
import org.gnucash.android.db.adapter.AccountsDbAdapter
import org.gnucash.android.ui.account.AccountFormFragment
import org.gnucash.android.ui.budget.BudgetAmountEditorFragment
import org.gnucash.android.ui.budget.BudgetFormFragment
import org.gnucash.android.ui.export.ExportFormFragment
import org.gnucash.android.ui.passcode.PasscodeLockActivity
import org.gnucash.android.ui.search.SearchFormFragment
import org.gnucash.android.ui.transaction.SplitEditorFragment
import org.gnucash.android.ui.transaction.TransactionFormFragment
import org.gnucash.android.util.BookUtils.activateBook
import timber.log.Timber

/**
 * Activity for displaying forms in the application.
 * The activity provides the standard close button, but it is up to the form fragments to display
 * menu options (e.g. for saving etc)
 *
 * @author Ngewi Fet <ngewif@gmail.com>
 */
class FormActivity : PasscodeLockActivity() {

    enum class FormType {
        ACCOUNT,
        TRANSACTION,
        EXPORT,
        SPLIT_EDITOR,
        BUDGET,
        BUDGET_AMOUNT_EDITOR,
        SEARCH_FORM
    }

    private lateinit var binding: ActivityFormBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFormBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val args = intent.extras
        if (args.isNullOrEmpty()) {
            Timber.e("Arguments required")
            finish()
            return
        }

        //if a parameter was passed to open an account within a specific book, then switch
        val bookUID = args.getString(UxArgument.BOOK_UID)
        if (bookUID != null && bookUID != activeBookUID) {
            activateBook(this, bookUID)
        }

        setSupportActionBar(binding.toolbarLayout.toolbar)

        val actionBar: ActionBar? = supportActionBar
        actionBar?.setHomeButtonEnabled(true)
        actionBar?.setDisplayHomeAsUpEnabled(true)

        var accountUID = args.getString(UxArgument.SELECTED_ACCOUNT_UID)
        if (accountUID.isNullOrEmpty()) {
            accountUID = args.getString(UxArgument.PARENT_ACCOUNT_UID)
        }
        if (!accountUID.isNullOrEmpty()) {
            @ColorInt val accountColor =
                AccountsDbAdapter.instance.getActiveAccountColor(this, accountUID)
            setTitlesColor(accountColor)
        }

        val formTypeString = args.getString(UxArgument.FORM_TYPE)
        if (formTypeString.isNullOrEmpty()) {
            Timber.e("No form display type specified")
            finish()
            return
        }
        val formType = FormType.valueOf(formTypeString)
        when (formType) {
            FormType.ACCOUNT -> showAccountFormFragment(args)
            FormType.TRANSACTION -> showTransactionFormFragment(args)
            FormType.EXPORT -> showExportFormFragment(args)
            FormType.SPLIT_EDITOR -> showSplitEditorFragment(args)
            FormType.BUDGET -> showBudgetFormFragment(args)
            FormType.BUDGET_AMOUNT_EDITOR -> showBudgetAmountEditorFragment(args)
            FormType.SEARCH_FORM -> showSearchForm(args)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                goBack()
                return true
            }
        }

        return super.onOptionsItemSelected(item)
    }

    /**
     * Shows the form for creating/editing accounts
     *
     * @param args Arguments to use for initializing the form.
     * This could be an account to edit or a preset for the parent account
     */
    private fun showAccountFormFragment(args: Bundle) {
        val fragment = AccountFormFragment()
        showFormFragment(fragment, args)
    }

    /**
     * Loads the transaction insert/edit fragment and passes the arguments
     *
     * @param args Bundle arguments to be passed to the fragment
     */
    private fun showTransactionFormFragment(args: Bundle) {
        val fragment = TransactionFormFragment()
        showFormFragment(fragment, args)
    }

    /**
     * Loads the export form fragment and passes the arguments
     *
     * @param args Bundle arguments
     */
    private fun showExportFormFragment(args: Bundle) {
        val fragment = ExportFormFragment()
        showFormFragment(fragment, args)
    }

    /**
     * Load the split editor fragment
     *
     * @param args View arguments
     */
    private fun showSplitEditorFragment(args: Bundle) {
        val fragment = SplitEditorFragment()
        showFormFragment(fragment, args)
    }

    /**
     * Load the budget form
     *
     * @param args View arguments
     */
    private fun showBudgetFormFragment(args: Bundle) {
        val fragment = BudgetFormFragment()
        showFormFragment(fragment, args)
    }

    /**
     * Load the budget amount editor fragment
     *
     * @param args Arguments
     */
    private fun showBudgetAmountEditorFragment(args: Bundle) {
        val fragment = BudgetAmountEditorFragment()
        showFormFragment(fragment, args)
    }

    /**
     * Load the search form fragment
     *
     * @param args Arguments
     */
    private fun showSearchForm(args: Bundle) {
        val fragment = SearchFormFragment()
        showFormFragment(fragment, args)
    }

    /**
     * Loads the fragment into the fragment container, replacing whatever was there before
     *
     * @param fragment Fragment to be displayed
     */
    private fun showFormFragment(fragment: Fragment, args: Bundle) {
        fragment.arguments = args
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }

    private fun goBack() {
        val fm = supportFragmentManager
        if (fm.backStackEntryCount > 0) {
            fm.popBackStack()
        } else {
            setResult(RESULT_CANCELED)
            finish()
        }
    }
}
