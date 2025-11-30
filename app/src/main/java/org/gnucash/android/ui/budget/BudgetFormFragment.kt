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
import android.app.DatePickerDialog
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.DatePicker
import androidx.appcompat.app.ActionBar
import androidx.core.view.isVisible
import com.codetroopers.betterpickers.recurrencepicker.EventRecurrence
import com.codetroopers.betterpickers.recurrencepicker.EventRecurrenceFormatter
import com.codetroopers.betterpickers.recurrencepicker.RecurrencePickerDialogFragment.OnRecurrenceSetListener
import org.gnucash.android.R
import org.gnucash.android.app.MenuFragment
import org.gnucash.android.app.actionBar
import org.gnucash.android.app.finish
import org.gnucash.android.app.getParcelableArrayListCompat
import org.gnucash.android.databinding.FragmentBudgetFormBinding
import org.gnucash.android.db.adapter.BudgetsDbAdapter
import org.gnucash.android.model.Budget
import org.gnucash.android.model.BudgetAmount
import org.gnucash.android.model.Money
import org.gnucash.android.ui.adapter.QualifiedAccountNameAdapter
import org.gnucash.android.ui.common.FormActivity
import org.gnucash.android.ui.common.UxArgument
import org.gnucash.android.ui.snackLong
import org.gnucash.android.ui.transaction.TransactionFormFragment
import org.gnucash.android.ui.util.RecurrenceParser.parse
import org.gnucash.android.ui.util.RecurrenceViewClickListener
import org.gnucash.android.ui.util.dialog.DatePickerDialogFragment
import org.gnucash.android.ui.util.widget.CalculatorKeyboard.Companion.rebind
import timber.log.Timber
import java.util.Calendar

/**
 * Fragment for creating or editing Budgets
 */
class BudgetFormFragment : MenuFragment(), OnRecurrenceSetListener,
    DatePickerDialog.OnDateSetListener {
    var eventRecurrence: EventRecurrence = EventRecurrence()
    var recurrenceRule: String? = null

    private var budgetsDbAdapter: BudgetsDbAdapter = BudgetsDbAdapter.instance

    private var budget: Budget? = null
    private val startDate: Calendar = Calendar.getInstance()
    private var budgetAmounts: List<BudgetAmount> = emptyList()
    private var accountNameAdapter: QualifiedAccountNameAdapter? = null

    private var binding: FragmentBudgetFormBinding? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        budgetsDbAdapter = BudgetsDbAdapter.instance
        budgetAmounts = emptyList()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val binding = FragmentBudgetFormBinding.inflate(inflater, container, false)
        this.binding = binding
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val actionBar: ActionBar? = this.actionBar
        actionBar?.setTitle(R.string.title_edit_budget)

        val binding = this.binding!!
        binding.budgetAmountLayout.btnRemoveItem.isVisible = false
        binding.budgetAmountLayout.inputBudgetAmount.bindKeyboard(binding.calculatorKeyboard)
        binding.inputStartDate.text =
            TransactionFormFragment.DATE_FORMATTER.print(startDate.timeInMillis)
        binding.inputStartDate.setOnClickListener { v ->
            onClickBudgetStartDate()
        }
        binding.btnAddBudgetAmount.setOnClickListener { v ->
            onOpenBudgetAmountEditor(v)
        }

        binding.budgetAmountLayout.btnRemoveItem.isVisible = false
        binding.budgetAmountLayout.inputBudgetAmount.bindKeyboard(binding.calculatorKeyboard)
        binding.inputStartDate.text =
            TransactionFormFragment.DATE_FORMATTER.print(startDate.timeInMillis)

        val context = binding.root.context
        accountNameAdapter = QualifiedAccountNameAdapter(context, viewLifecycleOwner)
            .load()
        binding.budgetAmountLayout.inputBudgetAccountSpinner.adapter = accountNameAdapter

        val budgetUID = requireArguments().getString(UxArgument.BUDGET_UID)
        if (budgetUID != null) { //if we are editing the budget
            initViews(binding, budgetsDbAdapter.getRecord(budgetUID).also { budget = it })
        }

        binding.inputRecurrence.setOnClickListener(
            RecurrenceViewClickListener(parentFragmentManager, recurrenceRule, this)
        )
    }

    /**
     * Initialize views when editing an existing budget
     *
     * @param budget Budget to use to initialize the views
     */
    private fun initViews(binding: FragmentBudgetFormBinding, budget: Budget) {
        val context = binding.inputBudgetName.context
        binding.inputBudgetName.setText(budget.name)
        binding.inputDescription.setText(budget.description)

        val recurrenceRuleString = budget.recurrence!!.ruleString
        recurrenceRule = recurrenceRuleString
        eventRecurrence.parse(recurrenceRuleString)
        binding.inputRecurrence.text = budget.recurrence!!.getRepeatString(context)

        budgetAmounts = budget.compactedBudgetAmounts
        toggleAmountInputVisibility(binding)
    }

    /**
     * Extracts the budget amounts from the form
     *
     * If the budget amount was input using the simple form, then read the values.<br></br>
     * Else return the values gotten from the BudgetAmountEditor
     *
     * @return List of budget amounts
     */
    private fun extractBudgetAmounts(binding: FragmentBudgetFormBinding): List<BudgetAmount> {
        val value = binding.budgetAmountLayout.inputBudgetAmount.value ?: return budgetAmounts

        if (budgetAmounts.isEmpty()) { //has not been set in budget amounts editor
            val budgetAmounts = mutableListOf<BudgetAmount>()
            val accountPosition =
                binding.budgetAmountLayout.inputBudgetAccountSpinner.selectedItemPosition
            val account =
                accountNameAdapter!!.getAccount(accountPosition) ?: return this.budgetAmounts
            val amount = Money(value, account.commodity)
            val budgetAmount = BudgetAmount(amount, account.uid)
            budgetAmounts.add(budgetAmount)
            return budgetAmounts
        }
        return budgetAmounts
    }

    /**
     * Checks that this budget can be saved
     * Also sets the appropriate error messages on the relevant views
     *
     * For a budget to be saved, it needs to have a name, an amount and a schedule
     *
     * @return `true` if the budget can be saved, `false` otherwise
     */
    private fun canSave(binding: FragmentBudgetFormBinding): Boolean {
        val context: Context = binding.root.context
        if (eventRecurrence.until != null && eventRecurrence.until.isNotEmpty()
            || eventRecurrence.count <= 0
        ) {
            snackLong(R.string.budget_periods_required)
            return false
        }

        budgetAmounts = extractBudgetAmounts(binding)
        val budgetName = binding.inputBudgetName.getText().toString()
        val canSave = recurrenceRule != null && !budgetName.isEmpty() && !budgetAmounts.isEmpty()

        if (!canSave) {
            if (budgetName.isEmpty()) {
                binding.nameTextInputLayout.error = context.getString(R.string.name_required)
            } else {
                binding.nameTextInputLayout.error = null
            }

            if (budgetAmounts.isEmpty()) {
                val message = context.getString(R.string.budget_amount_required)
                binding.budgetAmountLayout.inputBudgetAmount.error = message
                snackLong(message)
            }

            if (recurrenceRule == null) {
                snackLong(R.string.budget_repeat_required)
            }
        }

        return canSave
    }

    /**
     * Extracts the information from the form and saves the budget
     */
    private fun saveBudget() {
        val binding = this.binding!!
        if (!canSave(binding)) return
        val name = binding.inputBudgetName.getText().toString().trim()

        var budget = budget
        if (budget == null) {
            budget = Budget(name)
        } else {
            budget.name = name
        }
        this.budget = budget

        // TODO: 22.10.2015 set the period num of the budget amount
        extractBudgetAmounts(binding)
        budget.setBudgetAmounts(budgetAmounts)

        budget.description = binding.inputDescription.getText().toString().trim()

        val recurrence = parse(eventRecurrence)
        recurrence!!.periodStart = startDate.timeInMillis
        budget.recurrence = recurrence

        budgetsDbAdapter.insert(budget)
        finish()
    }

    @Deprecated("Deprecated in Java")
    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.default_save_actions, menu)
    }

    @Deprecated("Deprecated in Java")
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_save -> {
                saveBudget()
                return true
            }
        }
        return false
    }

    private fun onClickBudgetStartDate() {
        DatePickerDialogFragment.newInstance(this, startDate.timeInMillis)
            .show(parentFragmentManager, "date_picker_fragment")
    }

    private fun onOpenBudgetAmountEditor(v: View) {
        budgetAmounts = extractBudgetAmounts(binding!!)
        val intent = Intent(v.context, FormActivity::class.java)
            .putExtra(UxArgument.FORM_TYPE, FormActivity.FormType.BUDGET_AMOUNT_EDITOR.name)
            .putParcelableArrayListExtra(
                UxArgument.BUDGET_AMOUNT_LIST,
                ArrayList<BudgetAmount>(budgetAmounts)
            )
        startActivityForResult(intent, REQUEST_EDIT_BUDGET_AMOUNTS)
    }

    override fun onRecurrenceSet(rrule: String?) {
        Timber.i("Budget reoccurs: %s", rrule)
        val binding = binding ?: return
        val context = binding.inputRecurrence.context
        var repeatString: String? = null
        if (!rrule.isNullOrEmpty()) {
            try {
                eventRecurrence.parse(rrule)
                recurrenceRule = rrule
                repeatString = EventRecurrenceFormatter.getRepeatString(
                    context,
                    context.resources,
                    eventRecurrence,
                    true
                )
            } catch (e: Exception) {
                Timber.e(e, "Bad recurrence for [%s]", rrule)
            }
        }
        if (repeatString.isNullOrEmpty()) {
            repeatString = context.getString(R.string.label_tap_to_create_schedule)
        }
        binding.inputRecurrence.text = repeatString
    }

    override fun onDateSet(view: DatePicker?, year: Int, month: Int, dayOfMonth: Int) {
        startDate.set(Calendar.YEAR, year)
        startDate.set(Calendar.MONTH, month)
        startDate.set(Calendar.DAY_OF_MONTH, dayOfMonth)
        val binding = binding ?: return
        binding.inputStartDate.text =
            TransactionFormFragment.DATE_FORMATTER.print(startDate.timeInMillis)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQUEST_EDIT_BUDGET_AMOUNTS) {
            if (resultCode == Activity.RESULT_OK) {
                val budgetAmounts = data?.getParcelableArrayListCompat(
                    UxArgument.BUDGET_AMOUNT_LIST,
                    BudgetAmount::class.java
                )
                if (budgetAmounts != null) {
                    this.budgetAmounts = budgetAmounts
                    toggleAmountInputVisibility(binding!!)
                }
            }
            return
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    /**
     * Toggles the visibility of the amount input based on [.budgetAmounts]
     */
    private fun toggleAmountInputVisibility(binding: FragmentBudgetFormBinding) {
        binding.btnAddBudgetAmount.text = getString(R.string.title_edit_budget_amounts)
        if (budgetAmounts.size > 1) {
            binding.budgetAmountLayout.root.isVisible = false
        } else {
            binding.budgetAmountLayout.root.isVisible = true
            if (!budgetAmounts.isEmpty()) {
                val budgetAmount = budgetAmounts[0]
                binding.budgetAmountLayout.inputBudgetAmount.value =
                    budgetAmount.amount.toBigDecimal()
                binding.budgetAmountLayout.inputBudgetAccountSpinner.setSelection(
                    accountNameAdapter!!.getPosition(budgetAmount.accountUID)
                )
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val view = getView()
        if (view is ViewGroup) {
            val keyboardView = binding!!.calculatorKeyboard.calculatorKeyboard
            rebind(view, keyboardView, null)
        }
    }

    companion object {
        private const val REQUEST_EDIT_BUDGET_AMOUNTS = 0xBA
    }
}
