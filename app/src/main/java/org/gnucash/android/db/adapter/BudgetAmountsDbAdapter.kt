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
import android.database.sqlite.SQLiteStatement
import org.gnucash.android.app.GnuCashApplication
import org.gnucash.android.db.DatabaseHolder
import org.gnucash.android.db.DatabaseSchema.AccountEntry
import org.gnucash.android.db.DatabaseSchema.BudgetAmountEntry
import org.gnucash.android.db.getLong
import org.gnucash.android.db.getString
import org.gnucash.android.model.BudgetAmount
import org.gnucash.android.model.Commodity
import org.gnucash.android.model.Money
import org.gnucash.android.model.Money.Companion.createZeroInstance
import java.io.IOException

/**
 * Database adapter for [BudgetAmount]s
 */
class BudgetAmountsDbAdapter(val commoditiesDbAdapter: CommoditiesDbAdapter) :
    DatabaseAdapter<BudgetAmount>(
        commoditiesDbAdapter.holder,
        BudgetAmountEntry.TABLE_NAME,
        arrayOf(
            BudgetAmountEntry.COLUMN_BUDGET_UID,
            BudgetAmountEntry.COLUMN_ACCOUNT_UID,
            BudgetAmountEntry.COLUMN_AMOUNT_NUM,
            BudgetAmountEntry.COLUMN_AMOUNT_DENOM,
            BudgetAmountEntry.COLUMN_PERIOD_NUM,
            BudgetAmountEntry.COLUMN_NOTES
        )
    ) {
    constructor(holder: DatabaseHolder) : this(CommoditiesDbAdapter(holder))

    @Throws(IOException::class)
    override fun close() {
        commoditiesDbAdapter.close()
        super.close()
    }

    override fun buildModelInstance(cursor: Cursor): BudgetAmount {
        val budgetUID = cursor.getString(BudgetAmountEntry.COLUMN_BUDGET_UID)!!
        val accountUID = cursor.getString(BudgetAmountEntry.COLUMN_ACCOUNT_UID)!!
        val amountNum = cursor.getLong(BudgetAmountEntry.COLUMN_AMOUNT_NUM)
        val amountDenom = cursor.getLong(BudgetAmountEntry.COLUMN_AMOUNT_DENOM)
        val periodNum = cursor.getLong(BudgetAmountEntry.COLUMN_PERIOD_NUM)
        val notes = cursor.getString(BudgetAmountEntry.COLUMN_NOTES)

        val budgetAmount = BudgetAmount(budgetUID, accountUID)
        populateBaseModelAttributes(cursor, budgetAmount)
        budgetAmount.amount = Money(amountNum, amountDenom, getCommodity(accountUID))
        budgetAmount.periodNum = periodNum
        budgetAmount.notes = notes

        return budgetAmount
    }

    override fun bind(stmt: SQLiteStatement, budgetAmount: BudgetAmount): SQLiteStatement {
        bindBaseModel(stmt, budgetAmount)
        stmt.bindString(1, budgetAmount.budgetUID)
        stmt.bindString(2, budgetAmount.accountUID)
        stmt.bindLong(3, budgetAmount.amount.numerator)
        stmt.bindLong(4, budgetAmount.amount.denominator)
        stmt.bindLong(5, budgetAmount.periodNum)
        if (budgetAmount.notes != null) {
            stmt.bindString(6, budgetAmount.notes)
        }

        return stmt
    }

    /**
     * Return budget amounts for the specific budget
     *
     * @param budgetUID GUID of the budget
     * @return List of budget amounts
     */
    fun getBudgetAmountsForBudget(budgetUID: String): List<BudgetAmount> {
        val cursor = fetchAllRecords(
            BudgetAmountEntry.COLUMN_BUDGET_UID + "=?",
            arrayOf<String?>(budgetUID),
            null
        )
        return getRecords(cursor)
    }

    /**
     * Delete all the budget amounts for a budget
     *
     * @param budgetUID GUID of the budget
     * @return Number of records deleted
     */
    fun deleteBudgetAmountsForBudget(budgetUID: String): Int {
        return db.delete(
            tableName,
            BudgetAmountEntry.COLUMN_BUDGET_UID + "=?",
            arrayOf<String?>(budgetUID)
        )
    }

    /**
     * Returns the budgets associated with a specific account
     *
     * @param accountUID GUID of the account
     * @return List of [BudgetAmount]s for the account
     */
    fun getBudgetAmounts(accountUID: String): List<BudgetAmount> {
        val cursor = fetchAllRecords(
            BudgetAmountEntry.COLUMN_ACCOUNT_UID + " = ?",
            arrayOf<String?>(accountUID),
            null
        )
        return getRecords(cursor)
    }

    /**
     * Returns the sum of the budget amounts for a particular account
     *
     * @param accountUID GUID of the account
     * @return Sum of the budget amounts
     */
    fun getBudgetAmountSum(accountUID: String): Money {
        val budgetAmounts = getBudgetAmounts(accountUID)
        var sum = createZeroInstance(getCommodity(accountUID))
        for (budgetAmount in budgetAmounts) {
            sum += budgetAmount.amount
        }
        return sum
    }

    /**
     * Returns the commodity of the account
     * with unique Identifier `accountUID`
     *
     * @param accountUID Unique Identifier of the account
     * @return Commodity of the account.
     */
    @Throws(IllegalArgumentException::class)
    fun getCommodity(accountUID: String): Commodity {
        val cursor = db.query(
            AccountEntry.TABLE_NAME,
            arrayOf<String?>(AccountEntry.COLUMN_COMMODITY_UID),
            AccountEntry.COLUMN_UID + "= ?",
            arrayOf<String?>(accountUID),
            null, null, null
        )
        try {
            if (cursor.moveToFirst()) {
                val commodityUID = cursor.getString(0)
                return commoditiesDbAdapter.getRecord(commodityUID)
            }
            throw IllegalArgumentException("Account not found")
        } finally {
            cursor.close()
        }
    }

    companion object {
        val instance: BudgetAmountsDbAdapter get() = GnuCashApplication.budgetAmountsDbAdapter!!
    }
}
