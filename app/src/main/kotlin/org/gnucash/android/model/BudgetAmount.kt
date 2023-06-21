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
package org.gnucash.android.model

import android.os.Parcel
import android.os.Parcelable
import java.math.BigDecimal

/**
 * Budget amounts for the different accounts.
 * The [Money] amounts are absolute values
 *
 * @see Budget
 */
class BudgetAmount : BaseModel, Parcelable {
    var budgetUID: String? = null
    var accountUID: String?
    /**
     * Period number for this budget amount. The period is zero-based index, and a value of -1
     * indicates that this budget amount is applicable to all budgeting periods.
     */
    var periodNum: Long = 0

    /**
     * Returns the Money amount of this budget amount
     *
     * @return Money amount
     */
    var amount: Money? = null
        private set

    /**
     * Create a new budget amount
     *
     * @param budgetUID  GUID of the budget
     * @param accountUID GUID of the account
     */
    constructor(budgetUID: String?, accountUID: String?) {
        this.budgetUID = budgetUID
        this.accountUID = accountUID
    }

    /**
     * Creates a new budget amount with the absolute value of `amount`
     *
     * @param amount     Money amount of the budget
     * @param accountUID GUID of the account
     */
    constructor(amount: Money, accountUID: String?) {
        this.amount = amount.abs()
        this.accountUID = accountUID
    }

    /**
     * Sets the amount for the budget
     *
     * The absolute value of the amount is used
     *
     * @param amount Money amount
     */
    fun setAmount(amount: Money) {
        this.amount = amount.abs()
    }

    override fun describeContents(): Int {
        return 0
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeString(uID)
        dest.writeString(budgetUID)
        dest.writeString(accountUID)
        dest.writeString(amount!!.toPlainString())
        dest.writeLong(periodNum)
    }

    /**
     * Private constructor for creating new BudgetAmounts from a Parcel
     *
     * @param source Parcel
     */
    private constructor(source: Parcel) {
        uID = source.readString()
        budgetUID = source.readString()
        accountUID = source.readString()
        amount = Money(BigDecimal(source.readString()), Commodity.DEFAULT_COMMODITY)
        periodNum = source.readLong()
    }

    companion object {
        @JvmField
        val CREATOR: Parcelable.Creator<BudgetAmount> = object : Parcelable.Creator<BudgetAmount> {
            override fun createFromParcel(source: Parcel): BudgetAmount {
                return BudgetAmount(source)
            }

            override fun newArray(size: Int): Array<BudgetAmount?> {
                return arrayOfNulls(size)
            }
        }
    }
}
