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
class Budget @JvmOverloads constructor(
    name: String = "",
    recurrence: Recurrence? = null
) : BaseModel() {

    /**
     * Returns the name of the budget
     *
     * @return name of the budget
     */
    var name: String = ""

    /**
     * A description of the budget
     */
    var description: String? = null

    /**
     * The recurrence for this budget
     */
    var recurrence: Recurrence? = null

    private val amountsByAccount = mutableMapOf<String, MutableList<BudgetAmount>>()

    /**
     * Return list of budget amounts associated with this budget
     *
     * @return List of budget amounts
     */
    val budgetAmounts: List<BudgetAmount>
        get() = amountsByAccount.values.flatten()

    init {
        this.name = name
        this.recurrence = recurrence
    }

    /**
     * Set the list of budget amounts
     *
     * @param budgetAmounts List of budget amounts
     */
    fun setBudgetAmounts(budgetAmounts: List<BudgetAmount>) {
        clearBudgetAmounts()
        for (budgetAmount in budgetAmounts) {
            addAmount(budgetAmount)
        }
    }

    fun clearBudgetAmounts() {
        amountsByAccount.clear()
    }

    /**
     * Adds a BudgetAmount to this budget
     *
     * @param budgetAmount Budget amount
     */
    fun addAmount(budgetAmount: BudgetAmount): BudgetAmount {
        budgetAmount.budgetUID = uid
        val list = amountsByAccount.getOrPut(budgetAmount.accountUID!!) { mutableListOf() }
        list.add(budgetAmount)
        return budgetAmount
    }

    /**
     * Adds a BudgetAmount to this budget
     *
     * @param account The budget account.
     * @param period The budget period.
     * @param amount The budget amount.
     */
    fun addAmount(account: Account, period: Long, amount: BigDecimal): BudgetAmount {
        val budgetAmount = BudgetAmount(budgetUID = uid, accountUID = account.uid).apply {
            this.periodNum = period
            this.amount = Money(amount, account.commodity)
        }
        return addAmount(budgetAmount)
    }

    /**
     * Returns the budget amount for a specific account
     *
     * @param accountUID GUID of the account
     * @return Money amount of the budget or null if the budget has no amount for the account
     */
    fun getAmount(accountUID: String): Money? {
        val budgetAmounts = amountsByAccount[accountUID] ?: return null
        var result = zeroInstance
        for (budgetAmount in budgetAmounts) {
            if (budgetAmount.accountUID == accountUID) {
                result += budgetAmount.amount
            }
        }
        return result
    }

    /**
     * Returns the budget amount for a specific account and period
     *
     * @param accountUID GUID of the account
     * @param periodNum  Budgeting period, zero-based index
     * @return Money amount or zero if no matching [BudgetAmount] is found for the period
     */
    fun getAmount(accountUID: String, periodNum: Long): Money {
        return getBudgetAmount(accountUID, periodNum)?.amount ?: zeroInstance
    }

    /**
     * Returns the sum of all budget amounts in this budget
     *
     * **NOTE:** This method ignores budgets of accounts which are in different currencies
     *
     * @return Money sum of all amounts
     */
    val amountSum: Money
        get() {
            var sum: Money = zeroInstance
            for (budgetAmount in amountsByAccount.values.flatten()) {
                try {
                    sum += budgetAmount.amount.abs()
                } catch (ex: CurrencyMismatchException) {
                    Timber.w("Skip some budget amounts with different currency")
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
                    PeriodType.LAST_WEEKDAY -> localDate.minusMonths(interval)
                        .lastDayOfWeek(localDate)

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
                    PeriodType.LAST_WEEKDAY -> localDate.plusMonths(interval)
                        .lastDayOfWeek(localDate)

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

    fun getBudgetAmount(account: Account, period: Long): BudgetAmount? {
        return getBudgetAmount(account.uid!!, period)
    }

    fun getBudgetAmount(accountUID: String, period: Long): BudgetAmount? {
        val budgetAmounts = amountsByAccount[accountUID] ?: return null
        val budgetUID = uid

        return budgetAmounts.firstOrNull {
            it.budgetUID == budgetUID
                    && it.accountUID == accountUID
                    && (it.periodNum == period || it.periodNum == PERIOD_ALL)
        }
    }

    /**
     * Returns the number of accounts in this budget
     *
     * @return Number of budgeted accounts
     */
    val numberOfAccounts: Int get() = accounts.size

    /**
     * Returns the list of accounts in this budget
     *
     * @return The accounts UIDs
     */
    val accounts: Set<String> get() = amountsByAccount.keys

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
            val amountsPerAccount = mutableMapOf<String, List<BigDecimal>>()

            for (accountUID in amountsByAccount.keys) {
                amountsPerAccount[accountUID] =
                    amountsByAccount[accountUID]!!.map { it.amount.toBigDecimal() }
            }

            val compactBudgetAmounts = mutableListOf<BudgetAmount>()

            for ((accountUID, amounts) in amountsPerAccount) {
                val first = amounts[0]
                var allSame = true
                for (amount in amounts) {
                    allSame = allSame && (amount == first)
                }

                if (allSame) {
                    if (amounts.size == 1) {
                        compactBudgetAmounts.addAll(amountsByAccount[accountUID]!!)
                    } else {
                        val budgetAmount = BudgetAmount(uid, accountUID).apply {
                            amount = Money(first, Commodity.DEFAULT_COMMODITY)
                            periodNum = PERIOD_ALL
                        }
                        compactBudgetAmounts.add(budgetAmount)
                    }
                } else {
                    //if not all amounts are the same, then just add them as we read them
                    compactBudgetAmounts.addAll(amountsByAccount[accountUID]!!)
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
            val amounts = mutableListOf<BudgetAmount>()

            for (budgetAmount in amountsByAccount.values.flatten()) {
                if (budgetAmount.periodNum == PERIOD_ALL) {
                    val accountUID = budgetAmount.accountUID

                    for (period in 0 until numberOfPeriods) {
                        val bgtAmount = BudgetAmount(uid, accountUID).apply {
                            amount = budgetAmount.amount
                            periodNum = period
                        }
                        amounts.add(bgtAmount)
                    }
                } else {
                    amounts.add(budgetAmount)
                }
            }

            return amounts
        }

    companion object {
        const val PERIOD_ALL = -1L
    }
}
