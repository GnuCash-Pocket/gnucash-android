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
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.appcompat.app.ActionBar
import androidx.core.view.isVisible
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.components.LimitLine
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import org.gnucash.android.R
import org.gnucash.android.app.MenuFragment
import org.gnucash.android.app.actionBar
import org.gnucash.android.databinding.CardviewBudgetAmountBinding
import org.gnucash.android.databinding.FragmentBudgetDetailBinding
import org.gnucash.android.db.DatabaseSchema.BudgetEntry
import org.gnucash.android.db.adapter.AccountsDbAdapter
import org.gnucash.android.db.adapter.BudgetsDbAdapter
import org.gnucash.android.math.isZero
import org.gnucash.android.model.Budget
import org.gnucash.android.model.BudgetAmount
import org.gnucash.android.ui.adapter.ModelDiff
import org.gnucash.android.ui.budget.BudgetsActivity.Companion.getBudgetProgressColor
import org.gnucash.android.ui.common.FormActivity
import org.gnucash.android.ui.common.Refreshable
import org.gnucash.android.ui.common.UxArgument
import org.gnucash.android.ui.transaction.TransactionsActivity
import java.math.RoundingMode

/**
 * Fragment for displaying budget details
 */
class BudgetDetailFragment : MenuFragment(), Refreshable {
    private var budgetUID: String? = null
    private var budgetsDbAdapter = BudgetsDbAdapter.instance

    private var binding: FragmentBudgetDetailBinding? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        budgetsDbAdapter = BudgetsDbAdapter.instance
        budgetUID = requireArguments().getString(UxArgument.BUDGET_UID)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val binding = FragmentBudgetDetailBinding.inflate(inflater, container, false)
        this.binding = binding
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = binding!!

        binding.listItem2Lines.secondaryText.setMaxLines(3)

        val context = binding.list.context
        if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            binding.list.layoutManager = GridLayoutManager(context, 2)
        } else {
            binding.list.layoutManager = LinearLayoutManager(context)
        }
        binding.list.setHasFixedSize(true)

        bindViews(binding)
    }

    private fun bindViews(binding: FragmentBudgetDetailBinding) {
        val context = binding.budgetRecurrence.context
        val budget = budgetsDbAdapter.getRecord(budgetUID!!)
        binding.listItem2Lines.primaryText.text = budget.name

        val description = budget.description
        if (description != null && !description.isEmpty()) {
            binding.listItem2Lines.secondaryText.text = description
        } else {
            binding.listItem2Lines.secondaryText.isVisible = false
        }
        binding.budgetRecurrence.text = budget.recurrence!!.getRepeatString(context)

        binding.list.adapter = BudgetAmountAdapter(budgetUID!!)
    }

    override fun onResume() {
        super.onResume()
        refresh()
        requireActivity().findViewById<View>(R.id.fab_create_budget)?.isVisible = false
    }

    override fun refresh() {
        refresh(budgetUID)
    }

    override fun refresh(uid: String?) {
        this.budgetUID = uid
        if (!uid.isNullOrEmpty()) {
            val budgetName =
                budgetsDbAdapter.getAttribute(uid, BudgetEntry.COLUMN_NAME)
            val actionBar: ActionBar? = this.actionBar
            actionBar?.title = "Budget: $budgetName"
        }
        binding?.let { bindViews(it) }
    }

    @Deprecated("Deprecated in Java")
    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.budget_actions, menu)
    }

    @Deprecated("Deprecated in Java")
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_edit -> {
                val context = context ?: return false
                val intent = Intent(context, FormActivity::class.java)
                    .setAction(Intent.ACTION_INSERT_OR_EDIT)
                    .putExtra(UxArgument.FORM_TYPE, FormActivity.FormType.BUDGET.name)
                    .putExtra(UxArgument.BUDGET_UID, budgetUID)
                startActivityForResult(intent, REQUEST_REFRESH)
                return true
            }

            else -> return super.onOptionsItemSelected(item)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (resultCode == Activity.RESULT_OK) {
            refresh()
        }
    }

    internal inner class BudgetAmountAdapter(budgetUID: String) :
        ListAdapter<BudgetAmount, BudgetAmountViewHolder>(ModelDiff<BudgetAmount>()) {
        private val budget: Budget = budgetsDbAdapter.getRecord(budgetUID)

        init {
            submitList(budget.compactedBudgetAmounts)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BudgetAmountViewHolder {
            val binding = CardviewBudgetAmountBinding.inflate(layoutInflater, parent, false)
            return BudgetAmountViewHolder(binding)
        }

        override fun onBindViewHolder(holder: BudgetAmountViewHolder, position: Int) {
            val budgetAmount = getItem(position)
            holder.bind(budget, budgetAmount)
        }
    }

    internal inner class BudgetAmountViewHolder(binding: CardviewBudgetAmountBinding) :
        RecyclerView.ViewHolder(binding.root) {
        private val budgetAccount: TextView = binding.budgetAccount
        private val budgetAmount: TextView = binding.budgetAmount
        private val budgetSpent: TextView = binding.budgetSpent
        private val budgetLeft: TextView = binding.budgetLeft
        private val budgetIndicator: ProgressBar = binding.budgetIndicator
        private val budgetChart: BarChart = binding.budgetChart

        fun bind(budget: Budget, budgetAmount: BudgetAmount) {
            val budgetAccountUID = budgetAmount.accountUID!!
            val projectedAmount = budgetAmount.amount
            val accountsDbAdapter = AccountsDbAdapter.instance
            val spentAmount = accountsDbAdapter.getAccountBalance(
                budgetAccountUID,
                budget.startOfCurrentPeriod,
                budget.endOfCurrentPeriod
            )
            val spentAmountAbs = spentAmount.abs()

            budgetAccount.text = accountsDbAdapter.getAccountFullName(budgetAccountUID)
            this.budgetAmount.text = projectedAmount.formattedString()

            budgetSpent.text = spentAmountAbs.formattedString()
            budgetLeft.text = projectedAmount.minus(spentAmountAbs).formattedString()

            var budgetProgress = 0f
            if (!projectedAmount.isAmountZero) {
                budgetProgress = spentAmount.toBigDecimal().divide(
                    projectedAmount.toBigDecimal(),
                    spentAmount.commodity.smallestFractionDigits,
                    RoundingMode.HALF_EVEN
                ).toFloat()
            }

            budgetIndicator.progress = (budgetProgress * 100).toInt()
            @ColorInt val color = getBudgetProgressColor(1 - budgetProgress)
            budgetSpent.setTextColor(color)
            budgetLeft.setTextColor(color)

            generateChartData(budget, budgetAmount, budgetChart)

            itemView.setOnClickListener { v ->
                val accountUID = budgetAmount.accountUID
                val intent = Intent(v.context, TransactionsActivity::class.java)
                    .putExtra(UxArgument.SELECTED_ACCOUNT_UID, accountUID)
                startActivityForResult(intent, REQUEST_REFRESH)
            }
        }

        /**
         * Generate the chart data for the chart
         *
         * @param barChart     View where to display the chart
         * @param budgetAmount BudgetAmount to visualize
         */
        private fun generateChartData(
            budget: Budget,
            budgetAmount: BudgetAmount,
            barChart: BarChart
        ) {
            // FIXME: 25.10.15 chart is broken

            val accountsDbAdapter = AccountsDbAdapter.instance
            val budgetAccountUID = budgetAmount.accountUID!!

            //todo: refactor getNumberOfPeriods into budget
            var budgetPeriods = budget.numberOfPeriods.toInt()
            budgetPeriods = if (budgetPeriods <= 0) 12 else budgetPeriods
            val periods = budget.recurrence!!.getNumberOfPeriods(budgetPeriods)

            val barEntries = mutableListOf<BarEntry>()
            // FIXME: 15.08.2016 why do we need number of periods */
            for (periodNum in 1..periods) {
                val amount = accountsDbAdapter.getAccountBalance(
                    budgetAccountUID,
                    budget.getStartOfPeriod(periodNum),
                    budget.getEndOfPeriod(periodNum)
                ).toBigDecimal()

                if (amount.isZero) continue

                barEntries.add(BarEntry(periodNum.toFloat(), amount.toFloat()))
            }

            val label = accountsDbAdapter.getAccountName(budgetAccountUID)
            val barDataSet = BarDataSet(barEntries, label)

            val barData = BarData(barDataSet)
            val limitLine = LimitLine(budgetAmount.amount.toFloat())
            limitLine.setLineWidth(2f)
            limitLine.lineColor = Color.RED

            barChart.data = barData
            barChart.axisLeft.addLimitLine(limitLine)
            val maxValue = budgetAmount.amount * 1.2
            barChart.axisLeft.setAxisMaxValue(maxValue.toFloat())
            barChart.animateX(1000)
            barChart.isAutoScaleMinMaxEnabled = true
            barChart.setDrawValueAboveBar(true)
            barChart.invalidate()
        }
    }

    companion object {
        // "ForResult" to force refresh afterwards.
        private const val REQUEST_REFRESH = 0x0000

        fun newInstance(budgetUID: String): BudgetDetailFragment {
            val args = Bundle()
            args.putString(UxArgument.BUDGET_UID, budgetUID)
            val fragment = BudgetDetailFragment()
            fragment.arguments = args
            return fragment
        }
    }
}
