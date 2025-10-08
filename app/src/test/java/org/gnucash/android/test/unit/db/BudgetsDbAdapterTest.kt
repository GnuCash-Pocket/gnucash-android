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
package org.gnucash.android.test.unit.db

import org.assertj.core.api.Assertions.assertThat
import org.gnucash.android.db.adapter.AccountsDbAdapter
import org.gnucash.android.db.adapter.BudgetAmountsDbAdapter
import org.gnucash.android.db.adapter.BudgetsDbAdapter
import org.gnucash.android.db.adapter.RecurrenceDbAdapter
import org.gnucash.android.model.Account
import org.gnucash.android.model.Budget
import org.gnucash.android.model.BudgetAmount
import org.gnucash.android.model.Commodity
import org.gnucash.android.model.Money
import org.gnucash.android.model.Money.Companion.createZeroInstance
import org.gnucash.android.model.PeriodType
import org.gnucash.android.model.Recurrence
import org.gnucash.android.test.unit.GnuCashTest
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * Tests for the budgets database adapter
 */
class BudgetsDbAdapterTest : GnuCashTest() {
    private lateinit var budgetsDbAdapter: BudgetsDbAdapter
    private lateinit var recurrenceDbAdapter: RecurrenceDbAdapter
    private lateinit var budgetAmountsDbAdapter: BudgetAmountsDbAdapter
    private lateinit var accountsDbAdapter: AccountsDbAdapter

    private lateinit var account: Account
    private lateinit var secondAccount: Account

    @Before
    fun setUp() {
        accountsDbAdapter = AccountsDbAdapter.instance
        budgetsDbAdapter = BudgetsDbAdapter.instance
        budgetAmountsDbAdapter = budgetsDbAdapter.budgetAmountsDbAdapter
        recurrenceDbAdapter = RecurrenceDbAdapter.instance

        account = Account("Budgeted account")
        secondAccount = Account("Another account")
        accountsDbAdapter.addRecord(account)
        accountsDbAdapter.addRecord(secondAccount)
    }

    @After
    fun tearDown() {
        budgetsDbAdapter.deleteAllRecords()
        budgetAmountsDbAdapter.deleteAllRecords()
        recurrenceDbAdapter.deleteAllRecords()
    }

    @Test
    fun testAddingBudget() {
        assertThat(budgetsDbAdapter.recordsCount).isZero()
        assertThat(budgetAmountsDbAdapter.recordsCount).isZero()
        assertThat(recurrenceDbAdapter.recordsCount).isZero()
        val defaultCurrency = Commodity.DEFAULT_COMMODITY

        val budget = Budget("Test")
        budget.addAmount(BudgetAmount(createZeroInstance(account.commodity), account.uid))
        budget.addAmount(BudgetAmount(Money("10", defaultCurrency), secondAccount.uid))
        val recurrence = Recurrence(PeriodType.MONTH)
        budget.recurrence = recurrence

        budgetsDbAdapter.addRecord(budget)
        assertThat(budgetsDbAdapter.recordsCount).isOne()
        assertThat(budgetAmountsDbAdapter.recordsCount).isEqualTo(2)
        assertThat(recurrenceDbAdapter.recordsCount).isOne()

        budget.clearBudgetAmounts()
        val budgetAmount = BudgetAmount(Money("5", defaultCurrency), account.uid)
        budget.addAmount(budgetAmount)
        budgetsDbAdapter.addRecord(budget)

        assertThat(budgetAmountsDbAdapter.recordsCount).isOne()
        assertThat(budgetAmountsDbAdapter.allRecords[0].uid).isEqualTo(budgetAmount.uid)
    }

    /**
     * Test that when bulk adding budgets, all the associated budgetAmounts and recurrences are saved
     */
    @Test
    fun testBulkAddBudgets() {
        assertThat(budgetsDbAdapter.recordsCount).isZero()
        assertThat(budgetAmountsDbAdapter.recordsCount).isZero()
        assertThat(recurrenceDbAdapter.recordsCount).isZero()

        val budgets = bulkCreateBudgets()

        budgetsDbAdapter.bulkAddRecords(budgets)

        assertThat(budgetsDbAdapter.recordsCount).isEqualTo(2)
        assertThat(budgetAmountsDbAdapter.recordsCount).isEqualTo(3)
        assertThat(recurrenceDbAdapter.recordsCount).isEqualTo(2)
    }

    @Test
    fun testGetAccountBudgets() {
        budgetsDbAdapter.bulkAddRecords(bulkCreateBudgets())

        val budgets = budgetsDbAdapter.getAccountBudgets(account.uid)
        assertThat(budgets).hasSize(2)

        assertThat(budgetsDbAdapter.getAccountBudgets(secondAccount.uid)).hasSize(1)
    }

    private fun bulkCreateBudgets(): List<Budget> {
        val budgets = mutableListOf<Budget>()
        var budget = Budget("", Recurrence(PeriodType.MONTH))
        budget.addAmount(
            BudgetAmount(createZeroInstance(Commodity.DEFAULT_COMMODITY), account.uid)
        )
        budgets.add(budget)

        val defaultCurrencyCode = Commodity.DEFAULT_COMMODITY.currencyCode
        budget = Budget("Random", Recurrence(PeriodType.WEEK))
        budget.addAmount(BudgetAmount(Money("10.50", defaultCurrencyCode), account.uid))
        budget.addAmount(
            BudgetAmount(Money("32.35", defaultCurrencyCode), secondAccount.uid)
        )

        budgets.add(budget)
        return budgets
    }

    @Test(expected = NullPointerException::class)
    fun savingBudget_shouldRequireExistingAccount() {
        val budget = Budget("")
        budget.addAmount(
            BudgetAmount(createZeroInstance(Commodity.DEFAULT_COMMODITY), "unknown-account")
        )

        budgetsDbAdapter.addRecord(budget)
    }

    @Test(expected = NullPointerException::class)
    fun savingBudget_shouldRequireRecurrence() {
        val budget = Budget("")
        budget.addAmount(
            BudgetAmount(createZeroInstance(Commodity.DEFAULT_COMMODITY), account.uid)
        )

        budgetsDbAdapter.addRecord(budget)
    }

    @Test(expected = IllegalArgumentException::class)
    fun savingBudget_shouldRequireBudgetAmount() {
        val budget = Budget("")
        budget.recurrence = Recurrence(PeriodType.MONTH)

        budgetsDbAdapter.addRecord(budget)
    }
}
