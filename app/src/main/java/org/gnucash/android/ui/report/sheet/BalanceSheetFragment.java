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
package org.gnucash.android.ui.report.sheet;

import static org.gnucash.android.ui.util.TextViewExtKt.displayBalance;

import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TableLayout;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.gnucash.android.R;
import org.gnucash.android.databinding.FragmentTextReportBinding;
import org.gnucash.android.databinding.RowBalanceSheetBinding;
import org.gnucash.android.databinding.TotalBalanceSheetBinding;
import org.gnucash.android.db.DatabaseSchema.AccountEntry;
import org.gnucash.android.model.Account;
import org.gnucash.android.model.AccountType;
import org.gnucash.android.model.Commodity;
import org.gnucash.android.model.Money;
import org.gnucash.android.model.Price;
import org.gnucash.android.ui.report.BaseReportFragment;
import org.gnucash.android.ui.report.ReportType;

import java.util.ArrayList;
import java.util.List;

/**
 * Balance sheet report fragment
 *
 * @author Ngewi Fet <ngewif@gmail.com>
 */
public class BalanceSheetFragment extends BaseReportFragment {

    private Money assetsBalance;
    private Money liabilitiesBalance;
    private final List<AccountType> assetAccountTypes = new ArrayList<>();
    private final List<AccountType> liabilityAccountTypes = new ArrayList<>();
    private final List<AccountType> equityAccountTypes = new ArrayList<>();

    private FragmentTextReportBinding binding;
    @ColorInt
    private int colorBalanceZero;

    @Override
    public View inflateView(LayoutInflater inflater, ViewGroup container) {
        binding = FragmentTextReportBinding.inflate(inflater, container, false);
        colorBalanceZero = binding.totalLiabilityAndEquity.getCurrentTextColor();
        return binding.getRoot();
    }

    @Override
    public ReportType getReportType() {
        return ReportType.SHEET;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        assetAccountTypes.clear();
        assetAccountTypes.add(AccountType.ASSET);
        assetAccountTypes.add(AccountType.CASH);
        assetAccountTypes.add(AccountType.BANK);

        liabilityAccountTypes.clear();
        liabilityAccountTypes.add(AccountType.LIABILITY);
        liabilityAccountTypes.add(AccountType.CREDIT);

        equityAccountTypes.clear();
        equityAccountTypes.add(AccountType.EQUITY);
    }

    @Override
    public boolean requiresAccountTypeOptions() {
        return false;
    }

    @Override
    public boolean requiresTimeRangeOptions() {
        return false;
    }

    @Override
    protected void generateReport(@NonNull Context context) {
        assetsBalance = accountsDbAdapter.getCurrentAccountsBalance(assetAccountTypes, commodity);
        liabilitiesBalance = accountsDbAdapter.getCurrentAccountsBalance(liabilityAccountTypes, commodity).unaryMinus();
    }

    @Override
    protected void displayReport() {
        loadAccountViews(assetAccountTypes, binding.tableAssets);
        loadAccountViews(liabilityAccountTypes, binding.tableLiabilities);
        loadAccountViews(equityAccountTypes, binding.tableEquity);

        displayBalance(binding.totalLiabilityAndEquity, assetsBalance.plus(liabilitiesBalance), colorBalanceZero);
    }

    @Override
    public void onPrepareOptionsMenu(@NonNull Menu menu) {
        super.onPrepareOptionsMenu(menu);
        menu.findItem(R.id.menu_group_reports_by).setVisible(false);
    }

    /**
     * Loads rows for the individual accounts and adds them to the report
     *
     * @param accountTypes Account types for which to load balances
     * @param tableLayout  Table layout into which to load the rows
     */
    private void loadAccountViews(List<AccountType> accountTypes, TableLayout tableLayout) {
        Context context = tableLayout.getContext();
        LayoutInflater inflater = LayoutInflater.from(context);
        tableLayout.removeAllViews();

        // FIXME move this to generateReport
        String where = AccountEntry.COLUMN_TYPE + " IN ('" + TextUtils.join("','", accountTypes) + "')"
            + " AND " + AccountEntry.COLUMN_PLACEHOLDER + " = 0"
            + " AND " + AccountEntry.COLUMN_TEMPLATE + " = 0";
        String orderBy = AccountEntry.COLUMN_FULL_NAME + " ASC";
        List<Account> accounts = accountsDbAdapter.getSimpleAccounts(where, null, orderBy);
        Money total = Money.createZeroInstance(Commodity.DEFAULT_COMMODITY);
        boolean isRowEven = true;

        for (Account account : accounts) {
            Money balance = accountsDbAdapter.getAccountBalance(account.getUID());
            if (balance.isAmountZero()) continue;
            AccountType accountType = account.getAccountType();
            balance = (accountType.hasDebitNormalBalance) ? balance : balance.unaryMinus();
            RowBalanceSheetBinding binding = RowBalanceSheetBinding.inflate(inflater, tableLayout, true);
            // alternate light and dark rows
            if (isRowEven) {
                binding.getRoot().setBackgroundResource(R.color.row_even);
                isRowEven = false;
            } else {
                binding.getRoot().setBackgroundResource(R.color.row_odd);
                isRowEven = true;
            }
            binding.accountName.setText(account.getName());
            TextView balanceTextView = binding.accountBalance;
            @ColorInt int colorBalanceZero = balanceTextView.getCurrentTextColor();
            displayBalance(balanceTextView, balance, colorBalanceZero);

            // Price conversion.
            Price price = pricesDbAdapter.getPrice(balance.getCommodity(), total.getCommodity());
            if (price == null) continue;
            balance = balance.times(price);
            total = total.plus(balance);
        }

        TotalBalanceSheetBinding binding = TotalBalanceSheetBinding.inflate(inflater, tableLayout, true);

        TextView accountBalance = binding.accountBalance;
        @ColorInt int colorBalanceZero = accountBalance.getCurrentTextColor();
        displayBalance(accountBalance, total, colorBalanceZero);
    }

}
