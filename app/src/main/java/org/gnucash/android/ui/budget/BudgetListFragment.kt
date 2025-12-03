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
package org.gnucash.android.ui.budget

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.database.Cursor
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.ActionBar
import androidx.appcompat.widget.PopupMenu
import androidx.fragment.app.Fragment
import androidx.loader.app.LoaderManager
import androidx.loader.content.Loader
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.gnucash.android.R
import org.gnucash.android.app.actionBar
import org.gnucash.android.databinding.CardviewBudgetBinding
import org.gnucash.android.databinding.FragmentBudgetListBinding
import org.gnucash.android.db.DatabaseCursorLoader
import org.gnucash.android.db.DatabaseSchema
import org.gnucash.android.db.adapter.AccountsDbAdapter
import org.gnucash.android.db.adapter.BudgetsDbAdapter
import org.gnucash.android.model.Budget
import org.gnucash.android.ui.adapter.CursorRecyclerAdapter
import org.gnucash.android.ui.budget.BudgetsActivity.Companion.getBudgetProgressColor
import org.gnucash.android.ui.common.FormActivity
import org.gnucash.android.ui.common.Refreshable
import org.gnucash.android.ui.common.UxArgument
import timber.log.Timber
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Budget list fragment
 */
class BudgetListFragment : Fragment(), Refreshable, LoaderManager.LoaderCallbacks<Cursor> {
    private var budgetsDbAdapter: BudgetsDbAdapter = BudgetsDbAdapter.instance
    private var budgetRecyclerAdapter: BudgetRecyclerAdapter? = null
    private var binding: FragmentBudgetListBinding? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        budgetsDbAdapter = BudgetsDbAdapter.instance
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val binding = FragmentBudgetListBinding.inflate(inflater, container, false)
        this.binding = binding
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = binding!!

        budgetRecyclerAdapter = BudgetRecyclerAdapter(null)

        val context = binding.list.context
        if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            binding.list.layoutManager = GridLayoutManager(context, 2)
        } else {
            binding.list.layoutManager = LinearLayoutManager(context)
        }
        binding.list.setHasFixedSize(true)
        binding.list.emptyView = binding.empty
        binding.list.adapter = budgetRecyclerAdapter

        binding.fabAdd.setOnClickListener {
            createBudget(it.context)
        }

        loaderManager.initLoader(0, null, this)
    }

    override fun onCreateLoader(id: Int, args: Bundle?): Loader<Cursor?> {
        Timber.d("Creating the accounts loader")
        return BudgetsCursorLoader(requireContext())
    }

    override fun onLoadFinished(loaderCursor: Loader<Cursor?>, cursor: Cursor?) {
        Timber.d("Budget loader finished. Swapping in cursor")
        budgetRecyclerAdapter?.changeCursor(cursor)
    }

    override fun onLoaderReset(arg0: Loader<Cursor?>) {
        Timber.d("Resetting the accounts loader")
        budgetRecyclerAdapter?.changeCursor(null)
    }

    override fun onResume() {
        super.onResume()
        refresh()
        val actionBar: ActionBar? = this.actionBar
        actionBar?.setTitle(R.string.title_budgets)
    }

    override fun refresh() {
        if (isDetached || fragmentManager == null) return
        loaderManager.restartLoader<Cursor>(0, null, this)
    }

    /**
     * This method does nothing with the GUID.
     * Is equivalent to calling [.refresh]
     *
     * @param uid GUID of relevant item to be refreshed
     */
    override fun refresh(uid: String?) {
        refresh()
    }

    /**
     * Opens the budget detail fragment
     *
     * @param budgetUID GUID of budget
     */
    fun onClickBudget(budgetUID: String) {
        val fragmentManager = parentFragmentManager
        fragmentManager.beginTransaction()
            .replace(R.id.fragment_container, BudgetDetailFragment.newInstance(budgetUID))
            .addToBackStack(null)
            .commit()
    }

    /**
     * Launches the FormActivity for editing the budget
     *
     * @param budgetUID Db record UID of the budget
     */
    private fun editBudget(budgetUID: String?) {
        val intent = Intent(requireContext(), FormActivity::class.java)
            .setAction(Intent.ACTION_INSERT_OR_EDIT)
            .putExtra(UxArgument.FORM_TYPE, FormActivity.FormType.BUDGET.name)
            .putExtra(UxArgument.BUDGET_UID, budgetUID)
        startActivityForResult(intent, REQUEST_REFRESH)
    }

    /**
     * Delete the budget from the database
     *
     * @param budgetUID Database record UID
     */
    private fun deleteBudget(budgetUID: String) {
        BudgetsDbAdapter.instance.deleteRecord(budgetUID)
        refresh()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (resultCode == Activity.RESULT_OK) {
            refresh()
        }
    }

    /**
     * Callback when create budget floating action button is clicked
     *
     * @param view View which was clicked
     */
    private fun createBudget(context: Context) {
        val intent = Intent(context, FormActivity::class.java)
            .setAction(Intent.ACTION_INSERT_OR_EDIT)
            .putExtra(UxArgument.FORM_TYPE, FormActivity.FormType.BUDGET.name)
        startActivity(intent)
    }

    internal inner class BudgetRecyclerAdapter(cursor: Cursor?) :
        CursorRecyclerAdapter<BudgetViewHolder>(cursor) {
        override fun onBindViewHolderCursor(holder: BudgetViewHolder, cursor: Cursor) {
            val budget = budgetsDbAdapter.buildModelInstance(cursor)
            holder.bind(budget)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BudgetViewHolder {
            val binding = CardviewBudgetBinding.inflate(layoutInflater, parent, false)
            return BudgetViewHolder(binding)
        }
    }

    internal inner class BudgetViewHolder(binding: CardviewBudgetBinding) :
        RecyclerView.ViewHolder(binding.root), PopupMenu.OnMenuItemClickListener {
        private val budgetName: TextView = binding.listItem2Lines.primaryText
        private val accountName: TextView = binding.listItem2Lines.secondaryText
        private val budgetAmount: TextView = binding.budgetAmount
        private val optionsMenu: ImageView = binding.optionsMenu
        private val budgetIndicator: ProgressBar = binding.budgetIndicator
        private val budgetRecurrence: TextView = binding.budgetRecurrence
        private var budgetUID: String? = null

        init {
            optionsMenu.setOnClickListener { v ->
                val popupMenu = PopupMenu(v.context, v)
                popupMenu.setOnMenuItemClickListener(this@BudgetViewHolder)
                val inflater = popupMenu.menuInflater
                inflater.inflate(R.menu.budget_context_menu, popupMenu.menu)
                popupMenu.show()
            }
        }

        override fun onMenuItemClick(item: MenuItem): Boolean {
            when (item.itemId) {
                R.id.menu_edit -> {
                    editBudget(budgetUID)
                    return true
                }

                R.id.menu_delete -> {
                    deleteBudget(budgetUID!!)
                    return true
                }

                else -> return false
            }
        }

        fun bind(budget: Budget) {
            val context = itemView.context
            this.budgetUID = budget.uid

            budgetName.text = budget.name

            val accountsDbAdapter = AccountsDbAdapter.instance
            val numberOfAccounts = budget.numberOfAccounts
            val accountString = if (numberOfAccounts == 1) {
                accountsDbAdapter.getAccountFullName(budget.budgetAmounts[0].accountUID!!)
            } else {
                context.getString(R.string.budgeted_accounts, numberOfAccounts)
            }
            accountName.text = accountString

            val recurrence = budget.recurrence!!
            budgetRecurrence.text = recurrence.getRepeatString(context) + " — " + context.getString(
                R.string.repeat_remaining,
                recurrence.daysLeftInCurrentPeriod
            )

            var spentAmountValue = BigDecimal.ZERO
            for (budgetAmount in budget.compactedBudgetAmounts) {
                val balance = accountsDbAdapter.getAccountBalance(
                    budgetAmount.accountUID!!,
                    budget.startOfCurrentPeriod,
                    budget.endOfCurrentPeriod
                )
                spentAmountValue += balance.toBigDecimal()
            }

            val budgetTotal = budget.amountSum
            val commodity = budgetTotal.commodity
            val usedAmount =
                (commodity.symbol + spentAmountValue + " / " + budgetTotal.formattedString())
            budgetAmount.text = usedAmount

            val budgetProgress = if (budgetTotal.isAmountZero) 0f else spentAmountValue.divide(
                budgetTotal.toBigDecimal(),
                commodity.smallestFractionDigits,
                RoundingMode.HALF_UP
            ).toFloat()
            budgetIndicator.progress = (budgetProgress * 100).toInt()

            budgetAmount.setTextColor(getBudgetProgressColor(1 - budgetProgress))

            itemView.setOnClickListener {
                onClickBudget(budget.uid)
            }
        }
    }

    /**
     * Loads Budgets asynchronously from the database
     */
    private class BudgetsCursorLoader(context: Context) :
        DatabaseCursorLoader<BudgetsDbAdapter>(context) {
        override fun loadInBackground(): Cursor? {
            databaseAdapter = BudgetsDbAdapter.instance
            return databaseAdapter!!.fetchAllRecords(
                null,
                null,
                DatabaseSchema.BudgetEntry.COLUMN_NAME + " ASC"
            )
        }
    }

    companion object {
        // "ForResult" to force refresh afterwards.
        private const val REQUEST_REFRESH = 0x0000
    }
}
