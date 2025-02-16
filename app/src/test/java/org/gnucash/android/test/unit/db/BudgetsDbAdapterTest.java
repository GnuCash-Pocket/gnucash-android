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
package org.gnucash.android.test.unit.db;

import static org.assertj.core.api.Assertions.assertThat;

import androidx.annotation.NonNull;

import org.gnucash.android.db.adapter.AccountsDbAdapter;
import org.gnucash.android.db.adapter.BudgetAmountsDbAdapter;
import org.gnucash.android.db.adapter.BudgetsDbAdapter;
import org.gnucash.android.db.adapter.RecurrenceDbAdapter;
import org.gnucash.android.model.Account;
import org.gnucash.android.model.Budget;
import org.gnucash.android.model.BudgetAmount;
import org.gnucash.android.model.Commodity;
import org.gnucash.android.model.Money;
import org.gnucash.android.model.PeriodType;
import org.gnucash.android.model.Recurrence;
import org.gnucash.android.test.unit.GnuCashTest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Tests for the budgets database adapter
 */
public class BudgetsDbAdapterTest extends GnuCashTest {

    private BudgetsDbAdapter mBudgetsDbAdapter;
    private RecurrenceDbAdapter mRecurrenceDbAdapter;
    private BudgetAmountsDbAdapter mBudgetAmountsDbAdapter;
    private AccountsDbAdapter mAccountsDbAdapter;

    private Account mAccount;
    private Account mSecondAccount;

    @Before
    public void setUp() {
        mAccountsDbAdapter = AccountsDbAdapter.getInstance();
        mBudgetsDbAdapter = BudgetsDbAdapter.getInstance();
        mBudgetAmountsDbAdapter = mBudgetsDbAdapter.getAmountsDbAdapter();
        mRecurrenceDbAdapter = RecurrenceDbAdapter.getInstance();

        mAccount = new Account("Budgeted account");
        mSecondAccount = new Account("Another account");
        mAccountsDbAdapter.addRecord(mAccount);
        mAccountsDbAdapter.addRecord(mSecondAccount);
    }

    @After
    public void tearDown() {
        mBudgetsDbAdapter.deleteAllRecords();
        mBudgetAmountsDbAdapter.deleteAllRecords();
        mRecurrenceDbAdapter.deleteAllRecords();
    }

    @Test
    public void testAddingBudget() {
        assertThat(mBudgetsDbAdapter.getRecordsCount()).isZero();
        assertThat(mBudgetAmountsDbAdapter.getRecordsCount()).isZero();
        assertThat(mRecurrenceDbAdapter.getRecordsCount()).isZero();
        Commodity defaultCurrency = Commodity.DEFAULT_COMMODITY;

        Budget budget = new Budget("Test");
        budget.addAmount(new BudgetAmount(Money.getZeroInstance(), mAccount.getUID()));
        budget.addAmount(new BudgetAmount(new Money("10", defaultCurrency), mSecondAccount.getUID()));
        Recurrence recurrence = new Recurrence(PeriodType.MONTH);
        budget.setRecurrence(recurrence);

        mBudgetsDbAdapter.addRecord(budget);
        assertThat(mBudgetsDbAdapter.getRecordsCount()).isEqualTo(1);
        assertThat(mBudgetAmountsDbAdapter.getRecordsCount()).isEqualTo(2);
        assertThat(mRecurrenceDbAdapter.getRecordsCount()).isEqualTo(1);

        budget.clearBudgetAmounts();
        BudgetAmount budgetAmount = new BudgetAmount(new Money("5", defaultCurrency), mAccount.getUID());
        budget.addAmount(budgetAmount);
        mBudgetsDbAdapter.addRecord(budget);

        assertThat(mBudgetAmountsDbAdapter.getRecordsCount()).isEqualTo(1);
        assertThat(mBudgetAmountsDbAdapter.getAllRecords().get(0).getUID()).isEqualTo(budgetAmount.getUID());
    }

    /**
     * Test that when bulk adding budgets, all the associated budgetAmounts and recurrences are saved
     */
    @Test
    public void testBulkAddBudgets() {
        assertThat(mBudgetsDbAdapter.getRecordsCount()).isZero();
        assertThat(mBudgetAmountsDbAdapter.getRecordsCount()).isZero();
        assertThat(mRecurrenceDbAdapter.getRecordsCount()).isZero();

        List<Budget> budgets = bulkCreateBudgets();

        mBudgetsDbAdapter.bulkAddRecords(budgets);

        assertThat(mBudgetsDbAdapter.getRecordsCount()).isEqualTo(2);
        assertThat(mBudgetAmountsDbAdapter.getRecordsCount()).isEqualTo(3);
        assertThat(mRecurrenceDbAdapter.getRecordsCount()).isEqualTo(2);

    }

    @Test
    public void testGetAccountBudgets() {
        mBudgetsDbAdapter.bulkAddRecords(bulkCreateBudgets());

        List<Budget> budgets = mBudgetsDbAdapter.getAccountBudgets(mAccount.getUID());
        assertThat(budgets).hasSize(2);

        assertThat(mBudgetsDbAdapter.getAccountBudgets(mSecondAccount.getUID())).hasSize(1);
    }

    @NonNull
    private List<Budget> bulkCreateBudgets() {
        List<Budget> budgets = new ArrayList<>();
        Budget budget = new Budget("", new Recurrence(PeriodType.MONTH));
        budget.addAmount(new BudgetAmount(Money.getZeroInstance(), mAccount.getUID()));
        budgets.add(budget);

        String defaultCurrencyCode = Commodity.DEFAULT_COMMODITY.getCurrencyCode();
        budget = new Budget("Random", new Recurrence(PeriodType.WEEK));
        budget.addAmount(new BudgetAmount(new Money("10.50", defaultCurrencyCode), mAccount.getUID()));
        budget.addAmount(new BudgetAmount(new Money("32.35", defaultCurrencyCode), mSecondAccount.getUID()));

        budgets.add(budget);
        return budgets;
    }

    @Test(expected = NullPointerException.class)
    public void savingBudget_shouldRequireExistingAccount() {
        Budget budget = new Budget("");
        budget.addAmount(new BudgetAmount(Money.getZeroInstance(), "unknown-account"));

        mBudgetsDbAdapter.addRecord(budget);
    }

    @Test(expected = NullPointerException.class)
    public void savingBudget_shouldRequireRecurrence() {
        Budget budget = new Budget("");
        budget.addAmount(new BudgetAmount(Money.getZeroInstance(), mAccount.getUID()));

        mBudgetsDbAdapter.addRecord(budget);
    }

    @Test(expected = IllegalArgumentException.class)
    public void savingBudget_shouldRequireBudgetAmount() {
        Budget budget = new Budget("");
        budget.setRecurrence(new Recurrence(PeriodType.MONTH));

        mBudgetsDbAdapter.addRecord(budget);
    }
}
