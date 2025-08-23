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
import org.gnucash.android.math.toBigDecimal
import java.math.BigDecimal

/**
 * Budget amounts for the different accounts.
 * The [Money] amounts are absolute values
 *
 * @see Budget
 */
class BudgetAmount(
    var budgetUID: String? = null,
    var accountUID: String? = null,
    // FIXME should be the account commodity
    var amount: Money = Money.createZeroInstance(Commodity.DEFAULT_COMMODITY),
    /**
     * Period number for this budget amount. The period is zero-based index, and a value of -1
     * indicates that this budget amount is applicable to all budgeting periods.
     */
    var periodNum: Long = 0,
    var notes: String? = null
) : BaseModel(), Parcelable {

    /**
     * Creates a new budget amount with the absolute value of `amount`
     *
     * @param amount     Money amount of the budget
     * @param accountUID GUID of the account
     */
    constructor(amount: Money, accountUID: String?) : this(accountUID = accountUID) {
        this.amount = amount.abs()
    }

    override fun describeContents(): Int {
        return 0
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeString(uid)
        dest.writeString(budgetUID)
        dest.writeString(accountUID)
        dest.writeLong(periodNum)
        dest.writeMoney(amount, flags)
        dest.writeString(notes)
    }

    /**
     * Private constructor for creating new BudgetAmounts from a Parcel
     *
     * @param source Parcel
     */
    private constructor(source: Parcel) : this() {
        setUID(source.readString())
        budgetUID = source.readString()
        accountUID = source.readString()
        periodNum = source.readLong()
        amount = source.readMoney()!!
        notes = source.readString()
    }

    val amountNumerator: Long = amount.numerator

    val amountDenominator: Long = amount.denominator

    fun setAmount(numerator: Long, denominator: Long) {
        setAmount(toBigDecimal(numerator, denominator))
    }

    fun setAmount(value: BigDecimal) {
        amount = Money(value, amount.commodity)
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
