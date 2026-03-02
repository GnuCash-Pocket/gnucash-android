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
package org.gnucash.android.ui.report.sheet

import android.content.Context
import android.graphics.Color
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TableLayout
import androidx.annotation.ColorInt
import com.github.mikephil.charting.data.ChartData
import com.github.mikephil.charting.data.DataSet
import com.github.mikephil.charting.data.Entry
import org.gnucash.android.R
import org.gnucash.android.databinding.FragmentTextReportBinding
import org.gnucash.android.databinding.RowBalanceSheetBinding
import org.gnucash.android.databinding.TotalBalanceSheetBinding
import org.gnucash.android.db.DatabaseSchema.AccountEntry
import org.gnucash.android.db.adapter.AccountsDbAdapter.Companion.ALWAYS
import org.gnucash.android.db.joinIn
import org.gnucash.android.model.AccountType
import org.gnucash.android.model.Money
import org.gnucash.android.model.Money.Companion.createZeroInstance
import org.gnucash.android.model.isNullOrZero
import org.gnucash.android.ui.report.BaseReportFragment
import org.gnucash.android.ui.report.ReportType
import org.gnucash.android.ui.util.displayBalance

class AccountBalance(
    val name: String,
    val amount: Money
) : Entry()

class Balance(
    val balances: List<AccountBalance>,
    val total: Money,
    label: String
) : DataSet<AccountBalance>(balances, label) {
    override fun copy(): DataSet<AccountBalance> {
        val copied = Balance(balances, total, label)
        copy(copied)
        return copied
    }
}

class BalanceSheet() : ChartData<Balance>() {
    constructor(
        assets: Balance,
        liabilities: Balance,
        equity: Balance
    ) : this() {
        addDataSet(assets)
        addDataSet(liabilities)
        addDataSet(equity)
    }

    val assets: Balance get() = dataSets[0]
    val liabilities: Balance get() = dataSets[1]
    val equity: Balance get() = dataSets[2]
}

/**
 * Balance sheet report fragment
 *
 * @author Ngewi Fet <ngewif@gmail.com>
 */
class BalanceSheetFragment : BaseReportFragment<BalanceSheet>() {
    private var sheet: BalanceSheet? = null
    private var binding: FragmentTextReportBinding? = null

    @ColorInt
    private var colorBalanceZero = Color.TRANSPARENT

    override fun inflateView(inflater: LayoutInflater, container: ViewGroup?): View {
        val binding = FragmentTextReportBinding.inflate(inflater, container, false)
        this.binding = binding
        colorBalanceZero = binding.totalLiabilityAndEquity.currentTextColor
        return binding.root
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        // No menu
    }

    override val reportType: ReportType = ReportType.SHEET

    override fun requiresAccountTypeOptions(): Boolean {
        return false
    }

    override fun requiresTimeRangeOptions(): Boolean {
        return false
    }

    override fun generateReport(context: Context): BalanceSheet {
        return getData(context)
    }

    private fun getData(context: Context): BalanceSheet {
        val assets = calculateBalance(assetAccountTypes, context.getString(R.string.label_assets))
        val liabilities = calculateBalance(liabilityAccountTypes, context.getString(R.string.label_liabilities))
        val equity = calculateBalance(equityAccountTypes, context.getString(R.string.label_equity))
        return BalanceSheet(assets, liabilities, equity)
    }

    override fun displayReport(data: BalanceSheet) {
        val binding = binding ?: return
        this.sheet = data
        loadAccountViews(data.assets, binding.tableAssets)
        loadAccountViews(data.liabilities, binding.tableLiabilities)
        loadAccountViews(data.equity, binding.tableEquity)

        val net = data.assets.total + data.liabilities.total
        binding.totalLiabilityAndEquity.displayBalance(net, colorBalanceZero)
    }

    /**
     * Loads rows for the individual accounts and adds them to the report
     *
     * @param balance Account balances
     * @param tableLayout  Table layout into which to load the rows
     */
    private fun loadAccountViews(
        balance: Balance,
        tableLayout: TableLayout
    ) {
        val context = tableLayout.context
        val inflater = LayoutInflater.from(context)
        tableLayout.removeAllViews()

        var isRowEven = true

        for (accountBalance in balance.balances) {
            val binding = RowBalanceSheetBinding.inflate(inflater, tableLayout, true)
            // alternate light and dark rows
            if (isRowEven) {
                binding.root.setBackgroundResource(R.color.row_even)
                isRowEven = false
            } else {
                binding.root.setBackgroundResource(R.color.row_odd)
                isRowEven = true
            }
            binding.accountName.text = accountBalance.name
            val balanceTextView = binding.accountBalance
            @ColorInt val colorBalanceZero = balanceTextView.currentTextColor
            balanceTextView.displayBalance(accountBalance.amount, colorBalanceZero)
        }

        val binding = TotalBalanceSheetBinding.inflate(inflater, tableLayout, true)
        val accountBalance = binding.accountBalance
        @ColorInt val colorBalanceZero = accountBalance.currentTextColor
        accountBalance.displayBalance(balance.total, colorBalanceZero)
    }

    private fun calculateBalance(accountTypes: List<AccountType>, label: String): Balance {
        val accountBalances = mutableListOf<AccountBalance>()
        var total = createZeroInstance(commodity)

        val accountTypesList = accountTypes.map { it.name }.joinIn()
        val where = (AccountEntry.COLUMN_TYPE + " IN " + accountTypesList
                + " AND " + AccountEntry.COLUMN_TEMPLATE + " = 0")
        val orderBy = AccountEntry.COLUMN_FULL_NAME + " ASC"
        val accounts = accountsDbAdapter.getAllRecords(where, null, orderBy)
        val now = System.currentTimeMillis()

        for (account in accounts) {
            var amount = accountsDbAdapter.getAccountBalance(account, ALWAYS, now, false)
            if (amount.isNullOrZero()) continue
            val accountType = account.type
            amount = if (accountType.hasDebitNormalBalance) amount else -amount
            accountBalances.add(AccountBalance(account.name, amount))

            // Price conversion.
            val price = pricesDbAdapter.getPrice(amount.commodity, total.commodity) ?: continue
            amount *= price
            total += amount
        }

        return Balance(accountBalances, total, label)
    }

    companion object {
        private val assetAccountTypes = listOf<AccountType>(
            AccountType.ASSET,
            AccountType.CASH,
            AccountType.BANK
        )
        private val liabilityAccountTypes = listOf<AccountType>(
            AccountType.LIABILITY,
            AccountType.CREDIT
        )
        private val equityAccountTypes = listOf<AccountType>(
            AccountType.EQUITY
        )
    }
}
