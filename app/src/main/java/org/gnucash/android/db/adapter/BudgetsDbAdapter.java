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

package org.gnucash.android.db.adapter;

import android.database.Cursor;
import android.database.sqlite.SQLiteQueryBuilder;
import android.database.sqlite.SQLiteStatement;

import androidx.annotation.NonNull;

import org.gnucash.android.app.GnuCashApplication;
import org.gnucash.android.db.DatabaseHolder;
import org.gnucash.android.db.DatabaseSchema.BudgetAmountEntry;
import org.gnucash.android.db.DatabaseSchema.BudgetEntry;
import org.gnucash.android.model.Budget;
import org.gnucash.android.model.BudgetAmount;
import org.gnucash.android.model.Money;
import org.gnucash.android.model.Recurrence;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;


/**
 * Database adapter for accessing {@link org.gnucash.android.model.Budget} records
 */
public class BudgetsDbAdapter extends DatabaseAdapter<Budget> {

    @NonNull
    public final RecurrenceDbAdapter recurrenceDbAdapter;
    @NonNull
    public final BudgetAmountsDbAdapter budgetAmountsDbAdapter;

    /**
     * Opens the database adapter with an existing database
     */
    public BudgetsDbAdapter(@NonNull BudgetAmountsDbAdapter budgetAmountsDbAdapter,
                            @NonNull RecurrenceDbAdapter recurrenceDbAdapter) {
        super(budgetAmountsDbAdapter.holder, BudgetEntry.TABLE_NAME, new String[]{
            BudgetEntry.COLUMN_NAME,
            BudgetEntry.COLUMN_DESCRIPTION,
            BudgetEntry.COLUMN_RECURRENCE_UID,
            BudgetEntry.COLUMN_NUM_PERIODS
        });
        this.budgetAmountsDbAdapter = budgetAmountsDbAdapter;
        this.recurrenceDbAdapter = recurrenceDbAdapter;
    }

    /**
     * Opens the database adapter with an existing database
     */
    public BudgetsDbAdapter(@NonNull RecurrenceDbAdapter recurrenceDbAdapter) {
        this(new BudgetAmountsDbAdapter(recurrenceDbAdapter.holder), recurrenceDbAdapter);
    }

    /**
     * Opens the database adapter with an existing database
     */
    public BudgetsDbAdapter(@NonNull DatabaseHolder holder) {
        this(new RecurrenceDbAdapter(holder));
    }

    /**
     * Returns an instance of the budget database adapter
     *
     * @return BudgetsDbAdapter instance
     */
    public static BudgetsDbAdapter getInstance() {
        return GnuCashApplication.getBudgetDbAdapter();
    }

    @Override
    public void close() throws IOException {
        super.close();
        budgetAmountsDbAdapter.close();
        recurrenceDbAdapter.close();
    }

    @Override
    public void addRecord(@NonNull Budget budget, UpdateMethod updateMethod) {
        if (budget.getBudgetAmounts().isEmpty())
            throw new IllegalArgumentException("Budgets must have budget amounts");

        recurrenceDbAdapter.addRecord(budget.getRecurrence(), updateMethod);
        super.addRecord(budget, updateMethod);
        budgetAmountsDbAdapter.deleteBudgetAmountsForBudget(budget.getUID());
        for (BudgetAmount budgetAmount : budget.getBudgetAmounts()) {
            budgetAmountsDbAdapter.addRecord(budgetAmount, updateMethod);
        }
    }

    @Override
    public long bulkAddRecords(@NonNull List<Budget> budgetList, UpdateMethod updateMethod) {
        List<BudgetAmount> budgetAmountList = new ArrayList<>(budgetList.size() * 2);
        for (Budget budget : budgetList) {
            budgetAmountList.addAll(budget.getBudgetAmounts());
        }

        //first add the recurrences, they have no dependencies (foreign key constraints)
        List<Recurrence> recurrenceList = new ArrayList<>(budgetList.size());
        for (Budget budget : budgetList) {
            recurrenceList.add(budget.getRecurrence());
        }
        recurrenceDbAdapter.bulkAddRecords(recurrenceList, updateMethod);

        //now add the budgets themselves
        long nRow = super.bulkAddRecords(budgetList, updateMethod);

        //then add the budget amounts, they require the budgets to exist
        if (nRow > 0 && !budgetAmountList.isEmpty()) {
            budgetAmountsDbAdapter.bulkAddRecords(budgetAmountList, updateMethod);
        }

        return nRow;
    }

    @Override
    public Budget buildModelInstance(@NonNull Cursor cursor) {
        String name = cursor.getString(cursor.getColumnIndexOrThrow(BudgetEntry.COLUMN_NAME));
        String description = cursor.getString(cursor.getColumnIndexOrThrow(BudgetEntry.COLUMN_DESCRIPTION));
        String recurrenceUID = cursor.getString(cursor.getColumnIndexOrThrow(BudgetEntry.COLUMN_RECURRENCE_UID));
        long numPeriods = cursor.getLong(cursor.getColumnIndexOrThrow(BudgetEntry.COLUMN_NUM_PERIODS));

        Budget budget = new Budget(name);
        populateBaseModelAttributes(cursor, budget);
        budget.setDescription(description);
        budget.setRecurrence(recurrenceDbAdapter.getRecord(recurrenceUID));
        budget.setNumberOfPeriods(numPeriods);
        budget.setBudgetAmounts(budgetAmountsDbAdapter.getBudgetAmountsForBudget(budget.getUID()));

        return budget;
    }

    @Override
    protected @NonNull SQLiteStatement bind(@NonNull SQLiteStatement stmt, @NonNull final Budget budget) {
        bindBaseModel(stmt, budget);
        stmt.bindString(1, budget.getName());
        if (budget.getDescription() != null) {
            stmt.bindString(2, budget.getDescription());
        }
        stmt.bindString(3, budget.getRecurrence().getUID());
        stmt.bindLong(4, budget.getNumberOfPeriods());

        return stmt;
    }

    /**
     * Fetch all budgets which have an amount specified for the account
     *
     * @param accountUID GUID of account
     * @return Cursor with budgets data
     */
    public Cursor fetchBudgetsForAccount(String accountUID) {
        SQLiteQueryBuilder queryBuilder = new SQLiteQueryBuilder();
        queryBuilder.setTables(BudgetEntry.TABLE_NAME + "," + BudgetAmountEntry.TABLE_NAME
            + " ON " + BudgetEntry.TABLE_NAME + "." + BudgetEntry.COLUMN_UID + " = "
            + BudgetAmountEntry.TABLE_NAME + "." + BudgetAmountEntry.COLUMN_BUDGET_UID);

        queryBuilder.setDistinct(true);
        String[] projectionIn = new String[]{BudgetEntry.TABLE_NAME + ".*"};
        String selection = BudgetAmountEntry.TABLE_NAME + "." + BudgetAmountEntry.COLUMN_ACCOUNT_UID + " = ?";
        String[] selectionArgs = new String[]{accountUID};
        String sortOrder = BudgetEntry.TABLE_NAME + "." + BudgetEntry.COLUMN_NAME + " ASC";

        return queryBuilder.query(mDb, projectionIn, selection, selectionArgs, null, null, sortOrder);
    }

    /**
     * Returns the budgets associated with a specific account
     *
     * @param accountUID GUID of the account
     * @return List of budgets for the account
     */
    public List<Budget> getAccountBudgets(String accountUID) {
        Cursor cursor = fetchBudgetsForAccount(accountUID);
        return getRecords(cursor);
    }

    /**
     * Returns the sum of the account balances for all accounts in a budget for a specified time period
     * <p>This represents the total amount spent within the account of this budget in a given period</p>
     *
     * @param budgetUID   GUID of budget
     * @param periodStart Start of the budgeting period in millis
     * @param periodEnd   End of the budgeting period in millis
     * @return Balance of all the accounts
     */
    public Money getAccountSum(String budgetUID, long periodStart, long periodEnd) {
        List<BudgetAmount> budgetAmounts = budgetAmountsDbAdapter.getBudgetAmountsForBudget(budgetUID);
        List<String> accountUIDs = new ArrayList<>();
        for (BudgetAmount budgetAmount : budgetAmounts) {
            accountUIDs.add(budgetAmount.getAccountUID());
        }

        return new AccountsDbAdapter(holder).getAccountsBalanceByUID(accountUIDs, periodStart, periodEnd);
    }
}
