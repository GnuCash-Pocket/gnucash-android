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

import java.math.BigDecimal
import org.gnucash.android.model.Money.Companion.zeroInstance
import org.gnucash.android.model.Money.CurrencyMismatchException
import org.gnucash.android.util.dayOfWeek
import org.gnucash.android.util.lastDayOfMonth
import org.gnucash.android.util.lastDayOfWeek
import org.joda.time.LocalDateTime
import timber.log.Timber

/**
 * Budgets model
 *
 * @author Ngewi Fet <ngewif@gmail.com>
 */
class Budget : BaseModel {
    /**
     * Default constructor
     */
    constructor() {
        //nothing to see here, move along
    }

    /**
     * Overloaded constructor.
     * Initializes the name and amount of this budget
     *
     * @param name String name of the budget
     */
    constructor(name: String) {
        this.name = name
    }

    constructor(name: String, recurrence: Recurrence) {
        this.name = name
        this.recurrence = recurrence
    }

    /**
     * Returns the name of the budget
     *
     * @return name of the budget
     */
    var name: String? = null
        private set

    /**
     * Sets the name of the budget
     *
     * @param name String name of budget
     */
    fun setName(name: String) {
        this.name = name
    }

    /**
     * A description of the budget
     */
    var description: String? = null

    /**
     * The recurrence for this budget
     */
    var recurrence: Recurrence? = null
        private set

    /**
     * Set the recurrence pattern for this budget
     *
     * @param recurrence Recurrence object
     */
    fun setRecurrence(recurrence: Recurrence) {
        this.recurrence = recurrence
    }

    private var _budgetAmounts: MutableList<BudgetAmount> = ArrayList()

    /**
     * Return list of budget amounts associated with this budget
     *
     * @return List of budget amounts
     */
    val budgetAmounts: List<BudgetAmount>
        get() = _budgetAmounts

    /**
     * Set the list of budget amounts
     *
     * @param budgetAmounts List of budget amounts
     */
    fun setBudgetAmounts(budgetAmounts: MutableList<BudgetAmount>) {
        _budgetAmounts = budgetAmounts
        for (budgetAmount in _budgetAmounts) {
            budgetAmount.budgetUID = uID
        }
    }

    /**
     * Adds a BudgetAmount to this budget
     *
     * @param budgetAmount Budget amount
     */
    fun addBudgetAmount(budgetAmount: BudgetAmount) {
        budgetAmount.budgetUID = uID
        _budgetAmounts.add(budgetAmount)
    }

    /**
     * Returns the budget amount for a specific account
     *
     * @param accountUID GUID of the account
     * @return Money amount of the budget or null if the budget has no amount for the account
     */
    fun getAmount(accountUID: String): Money? {
        for (budgetAmount in _budgetAmounts) {
            if (budgetAmount.accountUID == accountUID) return budgetAmount.amount
        }
        return null
    }

    /**
     * Returns the budget amount for a specific account and period
     *
     * @param accountUID GUID of the account
     * @param periodNum  Budgeting period, zero-based index
     * @return Money amount or zero if no matching [BudgetAmount] is found for the period
     */
    fun getAmount(accountUID: String, periodNum: Int): Money? {
        for (budgetAmount in _budgetAmounts) {
            if (budgetAmount.accountUID == accountUID && (budgetAmount.periodNum == periodNum.toLong() || budgetAmount.periodNum == -1L)) {
                return budgetAmount.amount
            }
        }
        return zeroInstance
    }

    /**
     * Returns the sum of all budget amounts in this budget
     *
     * **NOTE:** This method ignores budgets of accounts which are in different currencies
     *
     * @return Money sum of all amounts
     */
    val amountSum: Money?
        get() {
            var sum: Money? =
                null //we explicitly allow this null instead of a money instance,
            // because this method should never return null for a budget
            for (budgetAmount in _budgetAmounts) {
                if (sum == null) {
                    sum = budgetAmount.amount
                } else {
                    try {
                        sum += budgetAmount.amount.abs()
                    } catch (ex: CurrencyMismatchException) {
                        Timber.w("Skip some budget amounts with different currency")
                    }
                }
            }
            return sum
        }

    /**
     * The number of periods covered by this budget
     */
    var numberOfPeriods: Long = 12 //default to 12 periods per year

    /**
     * Returns the timestamp of the start of current period of the budget
     *
     * @return Start timestamp in milliseconds
     */
    val startOfCurrentPeriod: Long
        get() {
            var localDate = LocalDateTime()
            recurrence?.let { recurrence ->
                val interval = recurrence.multiplier
                localDate = when (recurrence.periodType) {
                    PeriodType.ONCE -> localDate
                    PeriodType.HOUR -> localDate.millisOfDay().withMinimumValue()
                        .minusHours(interval)

                    PeriodType.DAY -> localDate.minusDays(interval).millisOfDay().withMinimumValue()
                    PeriodType.WEEK -> localDate.minusWeeks(interval).dayOfWeek().withMinimumValue()
                    PeriodType.MONTH -> localDate.minusMonths(interval)
                        .dayOfMonth().withMinimumValue()

                    PeriodType.YEAR -> localDate.minusYears(interval).dayOfYear().withMinimumValue()
                    PeriodType.LAST_WEEKDAY -> localDate.minusMonths(interval).lastDayOfWeek(localDate)
                    PeriodType.NTH_WEEKDAY -> localDate.minusMonths(interval).dayOfWeek(localDate)
                    PeriodType.END_OF_MONTH -> localDate.minusMonths(interval)
                        .dayOfMonth().withMaximumValue()
                }
            }
            return localDate.toDateTime().millis
        }

    /**
     * Returns the end timestamp of the current period
     *
     * @return End timestamp in milliseconds
     */
    val endOfCurrentPeriod: Long
        get() {
            var localDate = LocalDateTime()
            recurrence?.let { recurrence ->
                val interval = recurrence.multiplier
                localDate = when (recurrence.periodType) {
                    PeriodType.ONCE -> localDate
                    PeriodType.HOUR -> localDate.plusHours(interval).millisOfDay()
                        .withMaximumValue()

                    PeriodType.DAY -> localDate.plusDays(interval).millisOfDay().withMaximumValue()
                    PeriodType.WEEK -> localDate.plusWeeks(interval).dayOfWeek().withMaximumValue()
                    PeriodType.MONTH -> localDate.plusMonths(interval)
                        .dayOfMonth().withMaximumValue()

                    PeriodType.YEAR -> localDate.plusYears(interval).dayOfYear().withMaximumValue()
                    PeriodType.LAST_WEEKDAY -> localDate.plusMonths(interval).lastDayOfWeek(localDate)
                    PeriodType.NTH_WEEKDAY -> localDate.plusMonths(interval).dayOfWeek(localDate)
                    PeriodType.END_OF_MONTH -> localDate.plusMonths(interval).lastDayOfMonth()
                }
            }
            return localDate.toDateTime().millis
        }

    fun getStartOfPeriod(periodNum: Int): Long {
        var localDate = LocalDateTime()
        recurrence?.let { recurrence ->
            localDate = LocalDateTime(recurrence.periodStart)
            val interval = recurrence.multiplier * periodNum
            localDate = when (recurrence.periodType) {
                PeriodType.ONCE -> localDate
                PeriodType.HOUR -> localDate.plusHours(interval).millisOfDay().withMinimumValue()
                PeriodType.DAY -> localDate.plusDays(interval).millisOfDay().withMinimumValue()
                PeriodType.WEEK -> localDate.minusDays(interval).dayOfWeek().withMinimumValue()
                PeriodType.MONTH -> localDate.minusMonths(interval).dayOfMonth().withMinimumValue()
                PeriodType.YEAR -> localDate.minusYears(interval).dayOfYear().withMinimumValue()
                PeriodType.LAST_WEEKDAY -> localDate.minusMonths(interval).lastDayOfMonth()
                PeriodType.NTH_WEEKDAY -> localDate.minusMonths(interval).dayOfWeek(localDate)
                PeriodType.END_OF_MONTH -> localDate.minusMonths(interval).lastDayOfMonth()
            }
        }
        return localDate.toDateTime().millis
    }

    /**
     * Returns the end timestamp of the period
     *
     * @param periodNum Number of the period
     * @return End timestamp in milliseconds of the period
     */
    fun getEndOfPeriod(periodNum: Int): Long {
        var localDate = LocalDateTime()
        recurrence?.let { recurrence ->
            val interval = recurrence.multiplier * periodNum
            localDate = when (recurrence.periodType) {
                PeriodType.ONCE -> localDate
                PeriodType.HOUR -> localDate.plusHours(interval)
                PeriodType.DAY -> localDate.plusDays(interval).millisOfDay().withMaximumValue()
                PeriodType.WEEK -> localDate.plusWeeks(interval).dayOfWeek().withMaximumValue()
                PeriodType.MONTH -> localDate.plusMonths(interval).dayOfMonth().withMaximumValue()
                PeriodType.YEAR -> localDate.plusYears(interval).dayOfYear().withMaximumValue()
                PeriodType.LAST_WEEKDAY -> localDate.plusMonths(interval).lastDayOfWeek(localDate)
                PeriodType.NTH_WEEKDAY -> localDate.plusMonths(interval).dayOfWeek(localDate)
                PeriodType.END_OF_MONTH -> localDate.plusMonths(interval).lastDayOfMonth()
            }
        }
        return localDate.toDateTime().millis
    }

    /**
     * Returns the number of accounts in this budget
     *
     * @return Number of budgeted accounts
     */
    val numberOfAccounts: Int
        get() {
            val accountSet: MutableSet<String?> = HashSet()
            for (budgetAmount in _budgetAmounts) {
                accountSet.add(budgetAmount.accountUID)
            }
            return accountSet.size
        }//if not all amounts are the same, then just add them as we read them

    /**
     * Returns the list of budget amounts where only one BudgetAmount is present if the amount of
     * the budget amount is the same for all periods in the budget.
     * BudgetAmounts with different amounts per period are still return separately
     *
     *
     * This method is used during import because GnuCash desktop saves one BudgetAmount per period
     * for the whole budgeting period.
     * While this can be easily displayed in a table form on the desktop, it is not feasible in the
     * Android app.
     * So we display only one BudgetAmount if it covers all periods in the budgeting period
     *
     *
     * @return List of [BudgetAmount]s
     */
    val compactedBudgetAmounts: List<BudgetAmount>
        get() {
            val accountAmountMap: MutableMap<String?, MutableList<BigDecimal>> = HashMap()

            for (budgetAmount in _budgetAmounts) {
                val accountUID = budgetAmount.accountUID
                val amount = budgetAmount.amount.asBigDecimal()

                if (accountAmountMap.containsKey(accountUID)) {
                    accountAmountMap[accountUID]!!.add(amount)

                } else {
                    val amounts: MutableList<BigDecimal> = ArrayList()
                    amounts.add(amount)
                    accountAmountMap[accountUID] = amounts
                }
            }

            val compactBudgetAmounts: MutableList<BudgetAmount> = ArrayList()

            for ((key, amounts) in accountAmountMap) {
                val first = amounts[0]
                var allSame = true
                for (bigDecimal in amounts) {
                    allSame = allSame and (bigDecimal == first)
                }

                if (allSame) {
                    if (amounts.size == 1) {
                        for (bgtAmount in _budgetAmounts) {
                            if (bgtAmount.accountUID == key) {
                                compactBudgetAmounts.add(bgtAmount)
                                break
                            }
                        }

                    } else {
                        val bgtAmount = BudgetAmount(uID, key)
                        bgtAmount.setAmount(Money(first, Commodity.DEFAULT_COMMODITY))
                        bgtAmount.periodNum = -1
                        compactBudgetAmounts.add(bgtAmount)
                    }

                } else {
                    //if not all amounts are the same, then just add them as we read them
                    for (bgtAmount in _budgetAmounts) {
                        if (bgtAmount.accountUID == key) {
                            compactBudgetAmounts.add(bgtAmount)
                        }
                    }
                }
            }
            return compactBudgetAmounts
        }

    /**
     * A list of budget amounts where each period has it's own budget amount
     *
     * Any budget amounts in the database with a period number of `-1` are expanded to individual
     * budget amounts for all periods
     *
     * This derived property is useful with exporting budget amounts to XML
     *
     * @return List of expanded [BudgetAmount].
     */
    val expandedBudgetAmounts: List<BudgetAmount>
        get() {
            val amountsToAdd: MutableList<BudgetAmount> = ArrayList()
            val amountsToRemove: MutableList<BudgetAmount> = ArrayList()

            for (budgetAmount in _budgetAmounts) {
                if (budgetAmount.periodNum == -1L) {
                    amountsToRemove.add(budgetAmount)
                    val accountUID = budgetAmount.accountUID

                    for (period in 0 until numberOfPeriods) {
                        val bgtAmount = BudgetAmount(uID, accountUID)
                        bgtAmount.setAmount(budgetAmount.amount)
                        bgtAmount.periodNum = period
                        amountsToAdd.add(bgtAmount)
                    }
                }
            }

            val expandedBudgetAmounts: MutableList<BudgetAmount> = ArrayList(_budgetAmounts)
            for (bgtAmount in amountsToRemove) {
                expandedBudgetAmounts.remove(bgtAmount)
            }

            for (bgtAmount in amountsToAdd) {
                expandedBudgetAmounts.add(bgtAmount)
            }

            return expandedBudgetAmounts
        }
}
