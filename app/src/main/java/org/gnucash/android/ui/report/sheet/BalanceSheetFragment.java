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
import android.database.Cursor;
import android.graphics.Typeface;
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
import org.gnucash.android.db.DatabaseSchema;
import org.gnucash.android.model.AccountType;
import org.gnucash.android.model.Money;
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

    private Money mAssetsBalance;
    private Money mLiabilitiesBalance;
    private List<AccountType> mAssetAccountTypes;
    private List<AccountType> mLiabilityAccountTypes;
    private List<AccountType> mEquityAccountTypes;

    private FragmentTextReportBinding mBinding;
    @ColorInt
    private int colorBalanceZero;

    @Override
    public View inflateView(LayoutInflater inflater, ViewGroup container) {
        mBinding = FragmentTextReportBinding.inflate(inflater, container, false);
        colorBalanceZero = mBinding.totalLiabilityAndEquity.getCurrentTextColor();
        return mBinding.getRoot();
    }

    @Override
    public ReportType getReportType() {
        return ReportType.TEXT;
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        mAssetAccountTypes = new ArrayList<>();
        mAssetAccountTypes.add(AccountType.ASSET);
        mAssetAccountTypes.add(AccountType.CASH);
        mAssetAccountTypes.add(AccountType.BANK);

        mLiabilityAccountTypes = new ArrayList<>();
        mLiabilityAccountTypes.add(AccountType.LIABILITY);
        mLiabilityAccountTypes.add(AccountType.CREDIT);

        mEquityAccountTypes = new ArrayList<>();
        mEquityAccountTypes.add(AccountType.EQUITY);
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
        mAssetsBalance = mAccountsDbAdapter.getAccountBalance(mAssetAccountTypes, -1, System.currentTimeMillis());
        mLiabilitiesBalance = mAccountsDbAdapter.getAccountBalance(mLiabilityAccountTypes, -1, System.currentTimeMillis());
    }

    @Override
    protected void displayReport() {
        loadAccountViews(mAssetAccountTypes, mBinding.tableAssets);
        loadAccountViews(mLiabilityAccountTypes, mBinding.tableLiabilities);
        loadAccountViews(mEquityAccountTypes, mBinding.tableEquity);

        displayBalance(mBinding.totalLiabilityAndEquity, mAssetsBalance.minus(mLiabilitiesBalance), colorBalanceZero);
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
        LayoutInflater inflater = LayoutInflater.from(getActivity());

        Cursor cursor = mAccountsDbAdapter.fetchAccounts(DatabaseSchema.AccountEntry.COLUMN_TYPE
                        + " IN ( '" + TextUtils.join("' , '", accountTypes) + "' ) AND "
                        + DatabaseSchema.AccountEntry.COLUMN_PLACEHOLDER + " = 0",
                null, DatabaseSchema.AccountEntry.COLUMN_FULL_NAME + " ASC");
        final int columnIndexUID = cursor.getColumnIndexOrThrow(DatabaseSchema.AccountEntry.COLUMN_UID);
        final int columnIndexName = cursor.getColumnIndexOrThrow(DatabaseSchema.AccountEntry.COLUMN_NAME);

        while (cursor.moveToNext()) {
            String accountUID = cursor.getString(columnIndexUID);
            String name = cursor.getString(columnIndexName);
            Money balance = mAccountsDbAdapter.getAccountBalance(accountUID);
            if (balance.isAmountZero()) continue;
            // TODO alternate light and dark rows
            View view = inflater.inflate(R.layout.row_balance_sheet, tableLayout, false);
            ((TextView) view.findViewById(R.id.account_name)).setText(name);
            TextView balanceTextView = (TextView) view.findViewById(R.id.account_balance);
            @ColorInt int colorBalanceZero = balanceTextView.getCurrentTextColor();
            displayBalance(balanceTextView, balance, colorBalanceZero);
            tableLayout.addView(view);
        }
        cursor.close();

        View totalView = inflater.inflate(R.layout.row_balance_sheet, tableLayout, false);
        TableLayout.LayoutParams layoutParams = (TableLayout.LayoutParams) totalView.getLayoutParams();
        layoutParams.setMargins(layoutParams.leftMargin, 20, layoutParams.rightMargin, layoutParams.bottomMargin);
        totalView.setLayoutParams(layoutParams);

        TextView accountName = (TextView) totalView.findViewById(R.id.account_name);
        accountName.setTextSize(16);
        accountName.setText(R.string.label_balance_sheet_total);
        TextView accountBalance = (TextView) totalView.findViewById(R.id.account_balance);
        accountBalance.setTextSize(16);
        accountBalance.setTypeface(null, Typeface.BOLD);
        @ColorInt int colorBalanceZero = accountBalance.getCurrentTextColor();
        displayBalance(accountBalance, mAccountsDbAdapter.getAccountBalance(accountTypes, -1, System.currentTimeMillis()), colorBalanceZero);

        tableLayout.addView(totalView);
    }

}
