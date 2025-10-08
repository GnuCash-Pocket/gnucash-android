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
package org.gnucash.android.test.unit.model

import org.assertj.core.api.Assertions.assertThat
import org.gnucash.android.model.Budget
import org.gnucash.android.model.BudgetAmount
import org.gnucash.android.model.Commodity
import org.gnucash.android.model.Money
import org.gnucash.android.model.Money.Companion.createZeroInstance
import org.gnucash.android.test.unit.GnuCashTest
import org.junit.Test

/**
 * Tests for budgets
 */
class BudgetTest : GnuCashTest() {
    @Test
    fun addingBudgetAmount_shouldSetBudgetUID() {
        val budget = Budget("Test")

        assertThat(budget.budgetAmounts).isNotNull()
        val budgetAmount = BudgetAmount(createZeroInstance(Commodity.DEFAULT_COMMODITY), "test")
        budget.addAmount(budgetAmount)

        assertThat(budget.budgetAmounts).hasSize(1)
        assertThat(budgetAmount.budgetUID).isEqualTo(budget.uid)

        //setting a whole list should also set the budget UIDs
        val budgetAmounts = listOf(
            BudgetAmount(createZeroInstance(Commodity.DEFAULT_COMMODITY), "test"),
            BudgetAmount(createZeroInstance(Commodity.DEFAULT_COMMODITY), "second")
        )

        budget.setBudgetAmounts(budgetAmounts)

        assertThat(budget.budgetAmounts).extracting("budgetUID", String::class.java)
            .contains(budget.uid)
    }

    @Test
    fun shouldComputeAbsoluteAmountSum() {
        val budget = Budget("Test")
        val accountAmount = Money("-20", "USD")
        val budgetAmount1 = BudgetAmount(accountAmount, "account1")
        val budgetAmount2 = BudgetAmount(Money("10", "USD"), "account2")

        budget.addAmount(budgetAmount1)
        budget.addAmount(budgetAmount2)

        assertThat(budget.getAmount("account1")).isEqualTo(accountAmount.abs())
        assertThat(budget.amountSum).isEqualTo(Money("30", "USD"))
    }

    /**
     * Tests that the method [Budget.getCompactedBudgetAmounts] does not aggregate
     * [BudgetAmount]s which have different money amounts
     */
    @Test
    fun shouldNotCompactBudgetAmountsWithDifferentAmounts() {
        val budget = Budget("Test")
        budget.numberOfPeriods = 6
        var budgetAmount = BudgetAmount(Money("10", "USD"), "test")
        budgetAmount.periodNum = 1
        budget.addAmount(budgetAmount)

        budgetAmount = BudgetAmount(Money("15", "USD"), "test")
        budgetAmount.periodNum = 2
        budget.addAmount(budgetAmount)

        budgetAmount = BudgetAmount(Money("5", "USD"), "secondAccount")
        budgetAmount.periodNum = 5
        budget.addAmount(budgetAmount)

        val compactedBudgetAmounts = budget.compactedBudgetAmounts
        assertThat(compactedBudgetAmounts).hasSize(3)
        assertThat(compactedBudgetAmounts).extracting("accountUID", String::class.java)
            .contains("test", "secondAccount")

        val periodNum = assertThat(compactedBudgetAmounts).extracting("periodNum", Long::class.java)
        periodNum.contains(1L, 2L, 5L)
        periodNum.doesNotContain(-1L)
    }

    /**
     * Tests that the method [Budget.getCompactedBudgetAmounts] aggregates [BudgetAmount]s
     * with the same amount but leaves others untouched
     */
    @Test
    fun addingSameAmounts_shouldCompactOnRetrieval() {
        val budget = Budget("Test")
        budget.numberOfPeriods = 6
        var budgetAmount = BudgetAmount(Money("10", "USD"), "first")
        budgetAmount.periodNum = 1
        budget.addAmount(budgetAmount)

        budgetAmount = BudgetAmount(Money("10", "USD"), "first")
        budgetAmount.periodNum = 2
        budget.addAmount(budgetAmount)

        budgetAmount = BudgetAmount(Money("10", "USD"), "first")
        budgetAmount.periodNum = 5
        budget.addAmount(budgetAmount)

        budgetAmount = BudgetAmount(Money("10", "EUR"), "second")
        budgetAmount.periodNum = 4
        budget.addAmount(budgetAmount)

        budgetAmount = BudgetAmount(Money("13", "EUR"), "third")
        budgetAmount.periodNum = -1
        budget.addAmount(budgetAmount)

        val compactedBudgetAmounts = budget.compactedBudgetAmounts

        assertThat(compactedBudgetAmounts).hasSize(3)
        val periodNum = assertThat(compactedBudgetAmounts).extracting("periodNum", Long::class.java)
        periodNum.hasSize(3)
        periodNum.contains(-1L, 4L)
        periodNum.doesNotContain(1L, 2L, 3L)

        assertThat(compactedBudgetAmounts)
            .extracting("accountUID", String::class.java)
            .contains("first", "second", "third")
            .hasSize(3)
    }

    /**
     * Test that when we set a periodNumber of -1 to a budget amount, the method [Budget.getExpandedBudgetAmounts]
     * should create new budget amounts for each of the periods in the budgeting period
     */
    @Test
    fun addingNegativePeriodNum_shouldExpandOnRetrieval() {
        val budget = Budget("Test")
        budget.numberOfPeriods = 6
        val budgetAmount = BudgetAmount(Money("10", "USD"), "first")
        budgetAmount.periodNum = -1
        budget.addAmount(budgetAmount)

        val expandedBudgetAmount = budget.expandedBudgetAmounts

        assertThat(expandedBudgetAmount).hasSize(6)

        val periodNum = assertThat(expandedBudgetAmount).extracting("periodNum", Long::class.java)
        periodNum.hasSize(6)
        periodNum.contains(0L, 1L, 2L, 3L, 4L, 5L)
        periodNum.doesNotContain(-1L)

        assertThat(expandedBudgetAmount).extracting("accountUID", String::class.java).hasSize(6)
    }

    @Test
    fun testGetNumberOfAccounts() {
        val budget = Budget("Test")
        budget.numberOfPeriods = 6
        var budgetAmount = BudgetAmount(Money("10", "USD"), "first")
        budgetAmount.periodNum = 1
        budget.addAmount(budgetAmount)

        budgetAmount = BudgetAmount(Money("10", "USD"), "first")
        budgetAmount.periodNum = 2
        budget.addAmount(budgetAmount)

        budgetAmount = BudgetAmount(Money("10", "USD"), "first")
        budgetAmount.periodNum = 5
        budget.addAmount(budgetAmount)

        budgetAmount = BudgetAmount(Money("10", "EUR"), "second")
        budgetAmount.periodNum = 4
        budget.addAmount(budgetAmount)

        budgetAmount = BudgetAmount(Money("13", "EUR"), "third")
        budgetAmount.periodNum = -1
        budget.addAmount(budgetAmount)

        assertThat(budget.numberOfAccounts).isEqualTo(3)
    }
}
