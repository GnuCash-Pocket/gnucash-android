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

package org.gnucash.android.test.unit.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.gnucash.android.model.Budget;
import org.gnucash.android.model.BudgetAmount;
import org.gnucash.android.model.Commodity;
import org.gnucash.android.model.Money;
import org.gnucash.android.test.unit.GnuCashTest;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Tests for budgets
 */
public class BudgetTest extends GnuCashTest {

    @Test
    public void addingBudgetAmount_shouldSetBudgetUID() {
        Budget budget = new Budget("Test");

        assertThat(budget.getBudgetAmounts()).isNotNull();
        BudgetAmount budgetAmount = new BudgetAmount(Money.createZeroInstance(Commodity.DEFAULT_COMMODITY), "test");
        budget.addAmount(budgetAmount);

        assertThat(budget.getBudgetAmounts()).hasSize(1);
        assertThat(budgetAmount.getBudgetUID()).isEqualTo(budget.getUID());

        //setting a whole list should also set the budget UIDs
        List<BudgetAmount> budgetAmounts = new ArrayList<>();
        budgetAmounts.add(new BudgetAmount(Money.createZeroInstance(Commodity.DEFAULT_COMMODITY), "test"));
        budgetAmounts.add(new BudgetAmount(Money.createZeroInstance(Commodity.DEFAULT_COMMODITY), "second"));

        budget.setBudgetAmounts(budgetAmounts);

        assertThat(budget.getBudgetAmounts()).extracting("budgetUID")
                .contains(budget.getUID());
    }

    @Test
    public void shouldComputeAbsoluteAmountSum() {
        Budget budget = new Budget("Test");
        Money accountAmount = new Money("-20", "USD");
        BudgetAmount budgetAmount1 = new BudgetAmount(accountAmount, "account1");
        BudgetAmount budgetAmount2 = new BudgetAmount(new Money("10", "USD"), "account2");

        budget.addAmount(budgetAmount1);
        budget.addAmount(budgetAmount2);

        assertThat(budget.getAmount("account1")).isEqualTo(accountAmount.abs());
        assertThat(budget.getAmountSum()).isEqualTo(new Money("30", "USD"));
    }

    /**
     * Tests that the method {@link Budget#getCompactedBudgetAmounts()} does not aggregate
     * {@link BudgetAmount}s which have different money amounts
     */
    @Test
    public void shouldNotCompactBudgetAmountsWithDifferentAmounts() {
        Budget budget = new Budget("Test");
        budget.setNumberOfPeriods(6);
        BudgetAmount budgetAmount = new BudgetAmount(new Money("10", "USD"), "test");
        budgetAmount.setPeriodNum(1);
        budget.addAmount(budgetAmount);

        budgetAmount = new BudgetAmount(new Money("15", "USD"), "test");
        budgetAmount.setPeriodNum(2);
        budget.addAmount(budgetAmount);

        budgetAmount = new BudgetAmount(new Money("5", "USD"), "secondAccount");
        budgetAmount.setPeriodNum(5);
        budget.addAmount(budgetAmount);

        List<BudgetAmount> compactedBudgetAmounts = budget.getCompactedBudgetAmounts();
        assertThat(compactedBudgetAmounts).hasSize(3);
        assertThat(compactedBudgetAmounts).extracting("accountUID")
                .contains("test", "secondAccount");

        assertThat(compactedBudgetAmounts).extracting("periodNum")
                .contains(1L, 2L, 5L).doesNotContain(-1L);
    }

    /**
     * Tests that the method {@link Budget#getCompactedBudgetAmounts()} aggregates {@link BudgetAmount}s
     * with the same amount but leaves others untouched
     */
    @Test
    public void addingSameAmounts_shouldCompactOnRetrieval() {
        Budget budget = new Budget("Test");
        budget.setNumberOfPeriods(6);
        BudgetAmount budgetAmount = new BudgetAmount(new Money("10", "USD"), "first");
        budgetAmount.setPeriodNum(1);
        budget.addAmount(budgetAmount);

        budgetAmount = new BudgetAmount(new Money("10", "USD"), "first");
        budgetAmount.setPeriodNum(2);
        budget.addAmount(budgetAmount);

        budgetAmount = new BudgetAmount(new Money("10", "USD"), "first");
        budgetAmount.setPeriodNum(5);
        budget.addAmount(budgetAmount);

        budgetAmount = new BudgetAmount(new Money("10", "EUR"), "second");
        budgetAmount.setPeriodNum(4);
        budget.addAmount(budgetAmount);

        budgetAmount = new BudgetAmount(new Money("13", "EUR"), "third");
        budgetAmount.setPeriodNum(-1);
        budget.addAmount(budgetAmount);

        List<BudgetAmount> compactedBudgetAmounts = budget.getCompactedBudgetAmounts();

        assertThat(compactedBudgetAmounts).hasSize(3);
        assertThat(compactedBudgetAmounts).extracting("periodNum").hasSize(3)
                .contains(-1L, 4L).doesNotContain(1L, 2L, 3L);

        assertThat(compactedBudgetAmounts).extracting("accountUID").hasSize(3)
                .contains("first", "second", "third");

    }

    /**
     * Test that when we set a periodNumber of -1 to a budget amount, the method {@link Budget#getExpandedBudgetAmounts()}
     * should create new budget amounts for each of the periods in the budgeting period
     */
    @Test
    public void addingNegativePeriodNum_shouldExpandOnRetrieval() {
        Budget budget = new Budget("Test");
        budget.setNumberOfPeriods(6);
        BudgetAmount budgetAmount = new BudgetAmount(new Money("10", "USD"), "first");
        budgetAmount.setPeriodNum(-1);
        budget.addAmount(budgetAmount);

        List<BudgetAmount> expandedBudgetAmount = budget.getExpandedBudgetAmounts();

        assertThat(expandedBudgetAmount).hasSize(6);

        assertThat(expandedBudgetAmount).extracting("periodNum").hasSize(6)
                .contains(0L, 1L, 2L, 3L, 4L, 5L).doesNotContain(-1L);

        assertThat(expandedBudgetAmount).extracting("accountUID").hasSize(6);
    }

    @Test
    public void testGetNumberOfAccounts() {
        Budget budget = new Budget("Test");
        budget.setNumberOfPeriods(6);
        BudgetAmount budgetAmount = new BudgetAmount(new Money("10", "USD"), "first");
        budgetAmount.setPeriodNum(1);
        budget.addAmount(budgetAmount);

        budgetAmount = new BudgetAmount(new Money("10", "USD"), "first");
        budgetAmount.setPeriodNum(2);
        budget.addAmount(budgetAmount);

        budgetAmount = new BudgetAmount(new Money("10", "USD"), "first");
        budgetAmount.setPeriodNum(5);
        budget.addAmount(budgetAmount);

        budgetAmount = new BudgetAmount(new Money("10", "EUR"), "second");
        budgetAmount.setPeriodNum(4);
        budget.addAmount(budgetAmount);

        budgetAmount = new BudgetAmount(new Money("13", "EUR"), "third");
        budgetAmount.setPeriodNum(-1);
        budget.addAmount(budgetAmount);

        assertThat(budget.getNumberOfAccounts()).isEqualTo(3);
    }
}
