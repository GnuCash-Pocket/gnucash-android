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
import android.view.LayoutInflater
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import android.widget.TableLayout
import androidx.annotation.ColorInt
import org.gnucash.android.R
import org.gnucash.android.databinding.FragmentTextReportBinding
import org.gnucash.android.databinding.RowBalanceSheetBinding
import org.gnucash.android.databinding.TotalBalanceSheetBinding
import org.gnucash.android.db.DatabaseSchema.AccountEntry
import org.gnucash.android.db.joinIn
import org.gnucash.android.model.AccountType
import org.gnucash.android.model.Money.Companion.createZeroInstance
import org.gnucash.android.model.isNullOrZero
import org.gnucash.android.ui.report.BaseReportFragment
import org.gnucash.android.ui.report.ReportType
import org.gnucash.android.ui.util.displayBalance

/**
 * Balance sheet report fragment
 *
 * @author Ngewi Fet <ngewif@gmail.com>
 */
class BalanceSheetFragment : BaseReportFragment() {
    private var assetsBalance = createZeroInstance(commodity)
    private var liabilitiesBalance = createZeroInstance(commodity)

    private var binding: FragmentTextReportBinding? = null

    @ColorInt
    private var colorBalanceZero = 0

    override fun inflateView(inflater: LayoutInflater, container: ViewGroup?): View {
        val binding = FragmentTextReportBinding.inflate(inflater, container, false)
        this.binding = binding
        colorBalanceZero = binding.totalLiabilityAndEquity.currentTextColor
        return binding.root
    }

    override val reportType: ReportType = ReportType.SHEET

    override fun requiresAccountTypeOptions(): Boolean {
        return false
    }

    override fun requiresTimeRangeOptions(): Boolean {
        return false
    }

    override fun generateReport(context: Context) {
        assetsBalance = accountsDbAdapter.getCurrentAccountsBalance(assetAccountTypes, commodity)
        liabilitiesBalance =
            -accountsDbAdapter.getCurrentAccountsBalance(liabilityAccountTypes, commodity)
    }

    override fun displayReport() {
        val binding = binding ?: return
        loadAccountViews(assetAccountTypes, binding.tableAssets)
        loadAccountViews(liabilityAccountTypes, binding.tableLiabilities)
        loadAccountViews(equityAccountTypes, binding.tableEquity)

        binding.totalLiabilityAndEquity.displayBalance(
            assetsBalance + liabilitiesBalance,
            colorBalanceZero
        )
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        super.onPrepareOptionsMenu(menu)
        menu.findItem(R.id.menu_group_reports_by).isVisible = false
    }

    /**
     * Loads rows for the individual accounts and adds them to the report
     *
     * @param accountTypes Account types for which to load balances
     * @param tableLayout  Table layout into which to load the rows
     */
    private fun loadAccountViews(
        accountTypes: List<AccountType>,
        tableLayout: TableLayout
    ) {
        val context = tableLayout.context
        val inflater = LayoutInflater.from(context)
        tableLayout.removeAllViews()

        // FIXME move this to generateReport
        val accountTypesList = accountTypes.map { it.name }.joinIn()
        val where = (AccountEntry.COLUMN_TYPE + " IN " + accountTypesList
                + " AND " + AccountEntry.COLUMN_PLACEHOLDER + " = 0"
                + " AND " + AccountEntry.COLUMN_TEMPLATE + " = 0")
        val orderBy = AccountEntry.COLUMN_FULL_NAME + " ASC"
        val accounts = accountsDbAdapter.getSimpleAccounts(where, null, orderBy)
        var total = createZeroInstance(commodity)
        var isRowEven = true

        for (account in accounts) {
            var balance = accountsDbAdapter.getAccountBalance(account.uid)
            if (balance.isNullOrZero()) continue
            val accountType = account.accountType
            balance = if (accountType.hasDebitNormalBalance) balance else -balance
            val binding = RowBalanceSheetBinding.inflate(inflater, tableLayout, true)
            // alternate light and dark rows
            if (isRowEven) {
                binding.root.setBackgroundResource(R.color.row_even)
                isRowEven = false
            } else {
                binding.root.setBackgroundResource(R.color.row_odd)
                isRowEven = true
            }
            binding.accountName.text = account.name
            val balanceTextView = binding.accountBalance
            @ColorInt val colorBalanceZero = balanceTextView.currentTextColor
            balanceTextView.displayBalance(balance, colorBalanceZero)

            // Price conversion.
            val price = pricesDbAdapter.getPrice(balance.commodity, total.commodity)
            if (price == null) continue
            balance *= price
            total += balance
        }

        val binding = TotalBalanceSheetBinding.inflate(inflater, tableLayout, true)
        val accountBalance = binding.accountBalance
        @ColorInt val colorBalanceZero = accountBalance.currentTextColor
        accountBalance.displayBalance(total, colorBalanceZero)
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
