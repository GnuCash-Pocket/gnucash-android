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
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ImageView
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.ActionBar
import androidx.core.view.isVisible
import org.gnucash.android.R
import org.gnucash.android.app.MenuFragment
import org.gnucash.android.app.actionBar
import org.gnucash.android.app.finish
import org.gnucash.android.app.getParcelableArrayListCompat
import org.gnucash.android.databinding.FragmentBudgetAmountEditorBinding
import org.gnucash.android.databinding.ItemBudgetAmountBinding
import org.gnucash.android.db.adapter.AccountsDbAdapter
import org.gnucash.android.model.BudgetAmount
import org.gnucash.android.model.Money
import org.gnucash.android.ui.adapter.DefaultItemSelectedListener
import org.gnucash.android.ui.adapter.QualifiedAccountNameAdapter
import org.gnucash.android.ui.common.UxArgument
import org.gnucash.android.ui.snackLong
import org.gnucash.android.ui.util.widget.CalculatorEditText
import org.gnucash.android.ui.util.widget.CalculatorKeyboard.Companion.rebind

/**
 * Fragment for editing budgeting amounts
 */
class BudgetAmountEditorFragment : MenuFragment() {
    private var accountsDbAdapter: AccountsDbAdapter = AccountsDbAdapter.instance
    private var accountNameAdapter: QualifiedAccountNameAdapter? = null
    private val budgetAmountViews = mutableListOf<View>()
    private var binding: FragmentBudgetAmountEditorBinding? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        accountsDbAdapter = AccountsDbAdapter.instance
        val context = requireContext()
        accountNameAdapter =
            QualifiedAccountNameAdapter(context, accountsDbAdapter, viewLifecycleOwner)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val binding = FragmentBudgetAmountEditorBinding.inflate(inflater, container, false)
        this.binding = binding
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val actionBar: ActionBar? = this.actionBar
        actionBar?.title = getString(R.string.title_edit_budget_amounts)

        val budgetAmounts = arguments?.getParcelableArrayListCompat(
            UxArgument.BUDGET_AMOUNT_LIST,
            BudgetAmount::class.java
        )
        if (budgetAmounts != null) {
            if (budgetAmounts.isEmpty()) {
                val viewHolder = addBudgetAmountView(null).tag as BudgetAmountViewHolder
                viewHolder.removeItemBtn.isVisible = false //there should always be at least one
            } else {
                loadBudgetAmountViews(budgetAmounts)
            }
        } else {
            val viewHolder = addBudgetAmountView(null).tag as BudgetAmountViewHolder
            viewHolder.removeItemBtn.isVisible = false //there should always be at least one
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.budget_amount_editor_actions, menu)
    }

    @Deprecated("Deprecated in Java")
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_add -> {
                addBudgetAmountView(null)
                return true
            }

            R.id.menu_save -> {
                saveBudgetAmounts()
                return true
            }

            else -> return super.onOptionsItemSelected(item)
        }
    }

    /**
     * Checks if the budget amounts can be saved
     *
     * @return `true` if all amounts a properly entered, `false` otherwise
     */
    private fun canSave(): Boolean {
        for (budgetAmountView in budgetAmountViews) {
            val viewHolder = budgetAmountView.tag as BudgetAmountViewHolder
            if (!viewHolder.amountEditText.isInputValid) {
                return false
            }
            //at least one account should be loaded (don't create budget with empty account tree
            if (viewHolder.budgetAccountSpinner.count == 0) {
                viewHolder.itemView.snackLong(R.string.budget_account_required)
                return false
            }
        }
        return true
    }

    private fun saveBudgetAmounts() {
        if (canSave()) {
            val budgetAmounts = ArrayList<BudgetAmount>(extractBudgetAmounts())
            val data = Intent()
                .putParcelableArrayListExtra(UxArgument.BUDGET_AMOUNT_LIST, budgetAmounts)
            requireActivity().setResult(Activity.RESULT_OK, data)
            finish()
        }
    }

    /**
     * Load views for the budget amounts
     *
     * @param budgetAmounts List of [BudgetAmount]s
     */
    private fun loadBudgetAmountViews(budgetAmounts: List<BudgetAmount>) {
        for (budgetAmount in budgetAmounts) {
            addBudgetAmountView(budgetAmount)
        }
    }

    /**
     * Inflates a new BudgetAmount item view and adds it to the UI.
     *
     * If the `budgetAmount` is not null, then it is used to initialize the view
     *
     * @param budgetAmount Budget amount
     */
    private fun addBudgetAmountView(budgetAmount: BudgetAmount?): View {
        val binding = ItemBudgetAmountBinding.inflate(
            layoutInflater,
            this.binding!!.budgetAmountLayout,
            false
        )
        if (budgetAmount != null) {
            val viewHolder = BudgetAmountViewHolder(binding)
            viewHolder.bindViews(budgetAmount)
        }
        val view: View = binding.root
        this.binding!!.budgetAmountLayout.addView(view, 0)
        budgetAmountViews.add(view)
        return view
    }

    /**
     * Extract [BudgetAmount]s from the views
     *
     * @return List of budget amounts
     */
    private fun extractBudgetAmounts(): List<BudgetAmount> {
        val budgetAmounts = mutableListOf<BudgetAmount>()
        for (view in budgetAmountViews) {
            val viewHolder = view.tag as BudgetAmountViewHolder
            val amountValue = viewHolder.amountEditText.value ?: continue
            val accountPosition = viewHolder.budgetAccountSpinner.selectedItemPosition
            val account = accountNameAdapter!!.getAccount(accountPosition) ?: continue
            val amount = Money(amountValue, account.commodity)
            val budgetAmount = BudgetAmount(amount, account.uid)
            budgetAmounts.add(budgetAmount)
        }
        return budgetAmounts
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val binding = binding ?: return
        if (view is ViewGroup) {
            var keyboardView = binding.calculatorKeyboard.calculatorKeyboard
            keyboardView = rebind(binding.root, keyboardView, null)
            for (budgetAmountView in budgetAmountViews) {
                val viewHolder = budgetAmountView.tag as BudgetAmountViewHolder
                viewHolder.amountEditText.bindKeyboard(keyboardView)
            }
        }
    }

    /**
     * View holder for budget amounts
     */
    internal inner class BudgetAmountViewHolder(binding: ItemBudgetAmountBinding) {
        val itemView = binding.root
        val currencySymbolTextView: TextView = binding.currencySymbol
        val amountEditText: CalculatorEditText = binding.inputBudgetAmount
        val removeItemBtn: ImageView = binding.btnRemoveItem
        val budgetAccountSpinner: Spinner = binding.inputBudgetAccountSpinner

        init {
            itemView.tag = this

            amountEditText.bindKeyboard(this@BudgetAmountEditorFragment.binding!!.calculatorKeyboard)
            budgetAccountSpinner.adapter = accountNameAdapter

            budgetAccountSpinner.onItemSelectedListener =
                DefaultItemSelectedListener { parent: AdapterView<*>,
                                              view: View?,
                                              position: Int,
                                              id: Long ->
                    val account = accountNameAdapter!!.getAccount(position)!!
                    val commodity = account.commodity
                    currencySymbolTextView.text = commodity.symbol
                }

            removeItemBtn.setOnClickListener {
                this@BudgetAmountEditorFragment.binding!!.budgetAmountLayout.removeView(itemView)
                budgetAmountViews.remove(itemView)
            }
        }

        fun bindViews(budgetAmount: BudgetAmount) {
            amountEditText.value = budgetAmount.amount.toBigDecimal()
            budgetAccountSpinner.setSelection(accountNameAdapter!!.getPosition(budgetAmount.accountUID))
        }
    }
}
