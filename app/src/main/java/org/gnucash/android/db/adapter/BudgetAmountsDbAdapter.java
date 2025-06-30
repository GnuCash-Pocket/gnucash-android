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

import static org.gnucash.android.db.DatabaseSchema.BudgetAmountEntry;

import android.database.Cursor;
import android.database.sqlite.SQLiteStatement;

import androidx.annotation.NonNull;

import org.gnucash.android.app.GnuCashApplication;
import org.gnucash.android.db.DatabaseHolder;
import org.gnucash.android.db.DatabaseSchema;
import org.gnucash.android.model.BudgetAmount;
import org.gnucash.android.model.Commodity;
import org.gnucash.android.model.Money;

import java.io.IOException;
import java.util.List;

/**
 * Database adapter for {@link BudgetAmount}s
 */
public class BudgetAmountsDbAdapter extends DatabaseAdapter<BudgetAmount> {

    @NonNull
    final CommoditiesDbAdapter commoditiesDbAdapter;

    public BudgetAmountsDbAdapter(@NonNull DatabaseHolder holder) {
        this(new CommoditiesDbAdapter(holder));
    }

    public BudgetAmountsDbAdapter(@NonNull CommoditiesDbAdapter commoditiesDbAdapter) {
        super(commoditiesDbAdapter.holder, BudgetAmountEntry.TABLE_NAME, new String[]{
            BudgetAmountEntry.COLUMN_BUDGET_UID,
            BudgetAmountEntry.COLUMN_ACCOUNT_UID,
            BudgetAmountEntry.COLUMN_AMOUNT_NUM,
            BudgetAmountEntry.COLUMN_AMOUNT_DENOM,
            BudgetAmountEntry.COLUMN_PERIOD_NUM,
            BudgetAmountEntry.COLUMN_NOTES
        });
        this.commoditiesDbAdapter = commoditiesDbAdapter;
    }

    public static BudgetAmountsDbAdapter getInstance() {
        return GnuCashApplication.getBudgetAmountsDbAdapter();
    }

    @Override
    public void close() throws IOException {
        commoditiesDbAdapter.close();
        super.close();
    }

    @Override
    public BudgetAmount buildModelInstance(@NonNull Cursor cursor) {
        String budgetUID = cursor.getString(cursor.getColumnIndexOrThrow(BudgetAmountEntry.COLUMN_BUDGET_UID));
        String accountUID = cursor.getString(cursor.getColumnIndexOrThrow(BudgetAmountEntry.COLUMN_ACCOUNT_UID));
        long amountNum = cursor.getLong(cursor.getColumnIndexOrThrow(BudgetAmountEntry.COLUMN_AMOUNT_NUM));
        long amountDenom = cursor.getLong(cursor.getColumnIndexOrThrow(BudgetAmountEntry.COLUMN_AMOUNT_DENOM));
        long periodNum = cursor.getLong(cursor.getColumnIndexOrThrow(BudgetAmountEntry.COLUMN_PERIOD_NUM));
        String notes = cursor.getString(cursor.getColumnIndexOrThrow(BudgetAmountEntry.COLUMN_NOTES));

        BudgetAmount budgetAmount = new BudgetAmount(budgetUID, accountUID);
        populateBaseModelAttributes(cursor, budgetAmount);
        budgetAmount.setAmount(new Money(amountNum, amountDenom, getCommodity(accountUID)));
        budgetAmount.setPeriodNum(periodNum);
        budgetAmount.setNotes(notes);

        return budgetAmount;
    }

    @Override
    protected @NonNull SQLiteStatement bind(@NonNull SQLiteStatement stmt, @NonNull final BudgetAmount budgetAmount) {
        bindBaseModel(stmt, budgetAmount);
        stmt.bindString(1, budgetAmount.getBudgetUID());
        stmt.bindString(2, budgetAmount.getAccountUID());
        stmt.bindLong(3, budgetAmount.getAmount().getNumerator());
        stmt.bindLong(4, budgetAmount.getAmount().getDenominator());
        stmt.bindLong(5, budgetAmount.getPeriodNum());
        if (budgetAmount.getNotes() != null) {
            stmt.bindString(6, budgetAmount.getNotes());
        }

        return stmt;
    }

    /**
     * Return budget amounts for the specific budget
     *
     * @param budgetUID GUID of the budget
     * @return List of budget amounts
     */
    public List<BudgetAmount> getBudgetAmountsForBudget(String budgetUID) {
        Cursor cursor = fetchAllRecords(BudgetAmountEntry.COLUMN_BUDGET_UID + "=?",
            new String[]{budgetUID}, null);
        return getRecords(cursor);
    }

    /**
     * Delete all the budget amounts for a budget
     *
     * @param budgetUID GUID of the budget
     * @return Number of records deleted
     */
    public int deleteBudgetAmountsForBudget(String budgetUID) {
        return mDb.delete(mTableName, BudgetAmountEntry.COLUMN_BUDGET_UID + "=?",
            new String[]{budgetUID});
    }

    /**
     * Returns the budgets associated with a specific account
     *
     * @param accountUID GUID of the account
     * @return List of {@link BudgetAmount}s for the account
     */
    public List<BudgetAmount> getBudgetAmounts(String accountUID) {
        Cursor cursor = fetchAllRecords(BudgetAmountEntry.COLUMN_ACCOUNT_UID + " = ?", new String[]{accountUID}, null);
        return getRecords(cursor);
    }

    /**
     * Returns the sum of the budget amounts for a particular account
     *
     * @param accountUID GUID of the account
     * @return Sum of the budget amounts
     */
    public Money getBudgetAmountSum(String accountUID) {
        List<BudgetAmount> budgetAmounts = getBudgetAmounts(accountUID);
        Money sum = Money.createZeroInstance(getCommodity(accountUID));
        for (BudgetAmount budgetAmount : budgetAmounts) {
            sum = sum.plus(budgetAmount.getAmount());
        }
        return sum;
    }

    /**
     * Returns the commodity of the account
     * with unique Identifier <code>accountUID</code>
     *
     * @param accountUID Unique Identifier of the account
     * @return Commodity of the account.
     */
    public Commodity getCommodity(@NonNull String accountUID) {
        Cursor cursor = mDb.query(
            DatabaseSchema.AccountEntry.TABLE_NAME,
            new String[]{DatabaseSchema.AccountEntry.COLUMN_COMMODITY_UID},
            DatabaseSchema.AccountEntry.COLUMN_UID + "= ?",
            new String[]{accountUID}, null, null, null);
        try {
            if (cursor.moveToFirst()) {
                String commodityUID = cursor.getString(0);
                return commoditiesDbAdapter.getRecord(commodityUID);
            } else {
                throw new IllegalArgumentException("Account not found");
            }
        } finally {
            cursor.close();
        }
    }
}
