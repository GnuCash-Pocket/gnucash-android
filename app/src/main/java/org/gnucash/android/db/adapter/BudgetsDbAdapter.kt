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
package org.gnucash.android.db.adapter

import android.database.Cursor
import android.database.sqlite.SQLiteQueryBuilder
import android.database.sqlite.SQLiteStatement
import org.gnucash.android.app.GnuCashApplication
import org.gnucash.android.db.DatabaseHolder
import org.gnucash.android.db.DatabaseSchema.BudgetAmountEntry
import org.gnucash.android.db.DatabaseSchema.BudgetEntry
import org.gnucash.android.db.bindInt
import org.gnucash.android.db.getInt
import org.gnucash.android.db.getString
import org.gnucash.android.model.Budget
import org.gnucash.android.model.BudgetAmount
import org.gnucash.android.model.Money
import java.io.IOException

/**
 * Database adapter for accessing [Budget] records
 */
class BudgetsDbAdapter
/**
 * Opens the database adapter with an existing database
 */(
    val budgetAmountsDbAdapter: BudgetAmountsDbAdapter,
    val recurrenceDbAdapter: RecurrenceDbAdapter
) : DatabaseAdapter<Budget>(
    budgetAmountsDbAdapter.holder,
    BudgetEntry.TABLE_NAME,
    arrayOf(
        BudgetEntry.COLUMN_NAME,
        BudgetEntry.COLUMN_DESCRIPTION,
        BudgetEntry.COLUMN_RECURRENCE_UID,
        BudgetEntry.COLUMN_NUM_PERIODS
    )
) {
    /**
     * Opens the database adapter with an existing database
     */
    constructor(recurrenceDbAdapter: RecurrenceDbAdapter) :
            this(BudgetAmountsDbAdapter(recurrenceDbAdapter.holder), recurrenceDbAdapter)

    /**
     * Opens the database adapter with an existing database
     */
    constructor(holder: DatabaseHolder) : this(RecurrenceDbAdapter(holder))

    @Throws(IOException::class)
    override fun close() {
        super.close()
        budgetAmountsDbAdapter.close()
        recurrenceDbAdapter.close()
    }

    override fun addRecord(budget: Budget, updateMethod: UpdateMethod): Budget {
        require(!budget.budgetAmounts.isEmpty()) { "Budgets must have budget amounts" }

        recurrenceDbAdapter.addRecord(budget.recurrence, updateMethod)
        super.addRecord(budget, updateMethod)
        budgetAmountsDbAdapter.deleteBudgetAmountsForBudget(budget.uid)
        for (budgetAmount in budget.budgetAmounts) {
            budgetAmountsDbAdapter.addRecord(budgetAmount, updateMethod)
        }

        return budget
    }

    override fun bulkAddRecords(budgets: List<Budget>, updateMethod: UpdateMethod): Long {
        val budgetAmounts: List<BudgetAmount> = budgets.flatMap { it.budgetAmounts }

        //first add the recurrences, they have no dependencies (foreign key constraints)
        val recurrences = budgets.map { it.recurrence }
        recurrenceDbAdapter.bulkAddRecords(recurrences, updateMethod)

        //now add the budgets themselves
        val nRow = super.bulkAddRecords(budgets, updateMethod)

        //then add the budget amounts, they require the budgets to exist
        if (nRow > 0 && !budgetAmounts.isEmpty()) {
            budgetAmountsDbAdapter.bulkAddRecords(budgetAmounts, updateMethod)
        }

        return nRow
    }

    override fun buildModelInstance(cursor: Cursor): Budget {
        val name = cursor.getString(BudgetEntry.COLUMN_NAME)!!
        val description = cursor.getString(BudgetEntry.COLUMN_DESCRIPTION)
        val recurrenceUID = cursor.getString(BudgetEntry.COLUMN_RECURRENCE_UID)!!
        val numPeriods = cursor.getInt(BudgetEntry.COLUMN_NUM_PERIODS)

        val budget = Budget()
        populateBaseModelAttributes(cursor, budget)
        budget.name = name
        budget.description = description
        budget.recurrence = recurrenceDbAdapter.getRecord(recurrenceUID)
        budget.numberOfPeriods = numPeriods
        val amounts = budgetAmountsDbAdapter.getBudgetAmountsForBudget(budget.uid)
        budget.setBudgetAmounts(amounts)

        return budget
    }

    override fun bind(stmt: SQLiteStatement, budget: Budget): SQLiteStatement {
        bindBaseModel(stmt, budget)
        stmt.bindString(1, budget.name)
        if (budget.description != null) {
            stmt.bindString(2, budget.description)
        }
        stmt.bindString(3, budget.recurrence.uid)
        stmt.bindInt(4, budget.numberOfPeriods)

        return stmt
    }

    /**
     * Fetch all budgets which have an amount specified for the account
     *
     * @param accountUID GUID of account
     * @return Cursor with budgets data
     */
    fun fetchBudgetsForAccount(accountUID: String): Cursor? {
        val queryBuilder = SQLiteQueryBuilder()
        queryBuilder.setTables(
            (BudgetEntry.TABLE_NAME + "," + BudgetAmountEntry.TABLE_NAME
                    + " ON " + BudgetEntry.TABLE_NAME + "." + BudgetEntry.COLUMN_UID + " = "
                    + BudgetAmountEntry.TABLE_NAME + "." + BudgetAmountEntry.COLUMN_BUDGET_UID)
        )

        queryBuilder.isDistinct = true
        val projectionIn = arrayOf<String?>(BudgetEntry.TABLE_NAME + ".*")
        val selection =
            BudgetAmountEntry.TABLE_NAME + "." + BudgetAmountEntry.COLUMN_ACCOUNT_UID + " = ?"
        val selectionArgs = arrayOf<String?>(accountUID)
        val sortOrder = BudgetEntry.TABLE_NAME + "." + BudgetEntry.COLUMN_NAME + " ASC"

        return queryBuilder.query(
            db,
            projectionIn,
            selection,
            selectionArgs,
            null,
            null,
            sortOrder
        )
    }

    /**
     * Returns the budgets associated with a specific account
     *
     * @param accountUID GUID of the account
     * @return List of budgets for the account
     */
    fun getAccountBudgets(accountUID: String): List<Budget> {
        val cursor = fetchBudgetsForAccount(accountUID)
        return getRecords(cursor)
    }

    /**
     * Returns the sum of the account balances for all accounts in a budget for a specified time period
     *
     * This represents the total amount spent within the account of this budget in a given period
     *
     * @param budgetUID   GUID of budget
     * @param periodStart Start of the budgeting period in millis
     * @param periodEnd   End of the budgeting period in millis
     * @return Balance of all the accounts
     */
    fun getAccountSum(budgetUID: String, periodStart: Long, periodEnd: Long): Money? {
        val budgetAmounts =
            budgetAmountsDbAdapter.getBudgetAmountsForBudget(budgetUID)
        val accountUIDs = mutableListOf<String>()
        for (budgetAmount in budgetAmounts) {
            budgetAmount.accountUID?.let { accountUIDs.add(it) }
        }

        return AccountsDbAdapter(holder)
            .getAccountsBalanceByUID(accountUIDs, periodStart, periodEnd)
    }

    companion object {
        /**
         * Returns an instance of the budget database adapter
         *
         * @return BudgetsDbAdapter instance
         */
        val instance: BudgetsDbAdapter get() = GnuCashApplication.budgetDbAdapter!!
    }
}
