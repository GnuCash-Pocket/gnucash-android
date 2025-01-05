/*
 * Copyright (c) 2015 Oleksandr Tyshkovets <olexandr.tyshkovets@gmail.com>
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

package org.gnucash.android.ui.report;

import static java.lang.Math.max;
import static java.lang.Math.min;

import android.app.DatePickerDialog;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.DatePicker;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import org.gnucash.android.R;
import org.gnucash.android.app.GnuCashApplication;
import org.gnucash.android.databinding.ActivityReportsBinding;
import org.gnucash.android.db.adapter.TransactionsDbAdapter;
import org.gnucash.android.model.AccountType;
import org.gnucash.android.ui.common.BaseDrawerActivity;
import org.gnucash.android.ui.common.Refreshable;
import org.gnucash.android.ui.util.dialog.DateRangePickerDialogFragment;
import org.joda.time.LocalDate;

import java.util.Date;
import java.util.List;

import timber.log.Timber;

/**
 * Activity for displaying report fragments (which must implement {@link BaseReportFragment})
 * <p>In order to add new reports, extend the {@link BaseReportFragment} class to provide the view
 * for the report. Then add the report mapping in {@link ReportType} constructor depending on what
 * kind of report it is. The report will be dynamically included at runtime.</p>
 *
 * @author Oleksandr Tyshkovets <olexandr.tyshkovets@gmail.com>
 * @author Ngewi Fet <ngewif@gmail.com>
 */
public class ReportsActivity extends BaseDrawerActivity implements AdapterView.OnItemSelectedListener,
    DatePickerDialog.OnDateSetListener, DateRangePickerDialogFragment.OnDateRangeSetListener,
    Refreshable {

    private static final String STATE_REPORT_TYPE = "report_type";
    private static final String STATE_REPORT_START = "report_start";
    private static final String STATE_REPORT_END = "report_end";

    private TransactionsDbAdapter mTransactionsDbAdapter;
    private AccountType mAccountType = AccountType.EXPENSE;
    private ReportType mReportType = ReportType.NONE;

    public enum GroupInterval {WEEK, MONTH, QUARTER, YEAR, ALL}

    // default time range is the last 3 months
    private LocalDate mReportPeriodStart = LocalDate.now().minusMonths(2).dayOfMonth().withMinimumValue();
    private LocalDate mReportPeriodEnd = LocalDate.now().plusDays(1);

    private GroupInterval mReportGroupInterval = GroupInterval.MONTH;

    private ActivityReportsBinding mBinding;

    AdapterView.OnItemSelectedListener mReportTypeSelectedListener = new AdapterView.OnItemSelectedListener() {

        @Override
        public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
            ReportType reportType = ReportType.values()[position];
            if (mReportType != reportType) {
                showReport(reportType);
            }
        }

        @Override
        public void onNothingSelected(AdapterView<?> parent) {
            //nothing to see here, move along
        }
    };

    @Override
    public void inflateView() {
        mBinding = ActivityReportsBinding.inflate(getLayoutInflater());
        setContentView(mBinding.getRoot());
        mDrawerLayout = mBinding.drawerLayout;
        mNavigationView = mBinding.navView;
        mToolbar = mBinding.toolbarLayout.toolbar;
        mToolbarProgress = mBinding.toolbarLayout.toolbarProgress.progress;
    }

    @Override
    public int getTitleRes() {
        return R.string.title_reports;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mTransactionsDbAdapter = TransactionsDbAdapter.getInstance();

        ActionBar actionBar = getSupportActionBar();
        assert actionBar != null;
        ArrayAdapter<String> typesAdapter = new ArrayAdapter<>(actionBar.getThemedContext(),
            android.R.layout.simple_list_item_1,
            ReportType.getReportNames(this));
        mBinding.toolbarLayout.toolbarSpinner.setAdapter(typesAdapter);
        mBinding.toolbarLayout.toolbarSpinner.setOnItemSelectedListener(mReportTypeSelectedListener);

        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this, R.array.report_time_range,
                android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mBinding.timeRangeSpinner.setAdapter(adapter);
        mBinding.timeRangeSpinner.setOnItemSelectedListener(this);
        mBinding.timeRangeSpinner.setSelection(1);

        ArrayAdapter<CharSequence> dataAdapter = ArrayAdapter.createFromResource(this,
            R.array.report_account_types, android.R.layout.simple_spinner_item);
        dataAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mBinding.reportAccountTypeSpinner.setAdapter(dataAdapter);
        mBinding.reportAccountTypeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int position, long id) {
                final AccountType accountType;
                switch (position) {
                    case 1:
                        accountType = AccountType.INCOME;
                        break;
                    case 0:
                    default:
                        accountType = AccountType.EXPENSE;
                        break;
                }
                updateAccountTypeOnFragments(accountType);
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {
                //nothing to see here, move along
            }
        });

        if (savedInstanceState == null) {
            showOverview();
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                mReportType = savedInstanceState.getSerializable(STATE_REPORT_TYPE, ReportType.class);
            } else {
                mReportType = (ReportType) savedInstanceState.getSerializable(STATE_REPORT_TYPE);
            }
            mReportPeriodStart = LocalDate.fromDateFields(new Date(savedInstanceState.getLong(STATE_REPORT_START)));
            mReportPeriodEnd = LocalDate.fromDateFields(new Date(savedInstanceState.getLong(STATE_REPORT_END)));
        }
    }

    void onFragmentResumed(@NonNull Fragment fragment) {
        ReportType reportType = ReportType.NONE;
        if (fragment instanceof BaseReportFragment) {
            BaseReportFragment reportFragment = (BaseReportFragment) fragment;
            reportType = reportFragment.getReportType();

            int visibility = reportFragment.requiresAccountTypeOptions() ? View.VISIBLE : View.GONE;
            mBinding.reportAccountTypeSpinner.setVisibility(visibility);
        }

        setAppBarColor(reportType.colorId);
        updateReportTypeSpinner(reportType);
        toggleToolbarTitleVisibility(reportType);
    }

    /**
     * Show the overview.
     */
    private void showOverview() {
        showReport(ReportType.NONE);
    }

    /**
     * Show the report.
     *
     * @param reportType the report type.
     */
    void showReport(@NonNull ReportType reportType) {
        BaseReportFragment fragment = reportType.getFragment();
        if (fragment == null) {
            Timber.w("Report fragment required");
            return;
        }
        FragmentManager fragmentManager = getSupportFragmentManager();
        // First, remove the current report to replace it.
        if (fragmentManager.getBackStackEntryCount() > 0) {
            fragmentManager.popBackStack();
        }
        FragmentTransaction tx = fragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment);
        if (reportType != ReportType.NONE) {
            String stackName = getString(reportType.titleId);
            tx.addToBackStack(stackName);
        }
        tx.commit();
    }

    /**
     * Update the report type spinner
     */
    public void updateReportTypeSpinner(@NonNull ReportType reportType) {
        mReportType = reportType;
        mBinding.toolbarLayout.toolbarSpinner.setSelection(reportType.ordinal());
    }

    private void toggleToolbarTitleVisibility(ReportType reportType) {
        ActionBar actionBar = getSupportActionBar();
        assert actionBar != null;

        if (reportType == ReportType.NONE) {
            mBinding.toolbarLayout.toolbarSpinner.setVisibility(View.GONE);
        } else {
            mBinding.toolbarLayout.toolbarSpinner.setVisibility(View.VISIBLE);
        }
        actionBar.setDisplayShowTitleEnabled(reportType == ReportType.NONE);
    }

    /**
     * Sets the color Action Bar and Status bar (where applicable)
     */
    public void setAppBarColor(int color) {
        int resolvedColor = ContextCompat.getColor(this, color);
        if (getSupportActionBar() != null)
            getSupportActionBar().setBackgroundDrawable(new ColorDrawable(resolvedColor));

        getWindow().setStatusBarColor(GnuCashApplication.darken(resolvedColor));
    }

    /**
     * Updates the reporting time range for all listening fragments
     */
    private void updateDateRangeOnFragment() {
        List<Fragment> fragments = getSupportFragmentManager().getFragments();
        for (Fragment fragment : fragments) {
            if (fragment instanceof ReportOptionsListener) {
                long startTime = mReportPeriodStart.toDate().getTime();
                long endTime = mReportPeriodEnd.toDate().getTime();
                ((ReportOptionsListener) fragment).onTimeRangeUpdated(startTime, endTime);
            }
        }
    }

    /**
     * Updates the account type for all attached fragments which are listening
     */
    private void updateAccountTypeOnFragments(AccountType accountType) {
        mAccountType = accountType;
        List<Fragment> fragments = getSupportFragmentManager().getFragments();
        for (Fragment fragment : fragments) {
            if (fragment instanceof ReportOptionsListener) {
                ((ReportOptionsListener) fragment).onAccountTypeUpdated(accountType);
            }
        }
    }

    /**
     * Updates the report grouping interval on all attached fragments which are listening
     */
    private void updateGroupingOnFragments() {
        List<Fragment> fragments = getSupportFragmentManager().getFragments();
        for (Fragment fragment : fragments) {
            if (fragment instanceof ReportOptionsListener) {
                ((ReportOptionsListener) fragment).onGroupingUpdated(mReportGroupInterval);
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.report_actions, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_group_reports_by:
                return true;

            case R.id.group_by_month:
                item.setChecked(true);
                mReportGroupInterval = GroupInterval.MONTH;
                updateGroupingOnFragments();
                return true;

            case R.id.group_by_quarter:
                item.setChecked(true);
                mReportGroupInterval = GroupInterval.QUARTER;
                updateGroupingOnFragments();
                return true;

            case R.id.group_by_year:
                item.setChecked(true);
                mReportGroupInterval = GroupInterval.YEAR;
                updateGroupingOnFragments();
                return true;

            case android.R.id.home:
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        LocalDate now = LocalDate.now();
        mReportPeriodEnd = now.plusDays(1);
        switch (position) {
            case 0: //current month
                mReportPeriodStart = now.dayOfMonth().withMinimumValue();
                break;
            case 1: // last 3 months. x-2, x-1, x
                mReportPeriodStart = now.minusMonths(2).dayOfMonth().withMinimumValue();
                break;
            case 2: // last 6 months
                mReportPeriodStart = now.minusMonths(5).dayOfMonth().withMinimumValue();
                break;
            case 3: // last year
                mReportPeriodStart = now.minusMonths(11).dayOfMonth().withMinimumValue();
                break;
            case 4: //ALL TIME
                mReportPeriodStart = new LocalDate(-1L);
                mReportPeriodEnd = new LocalDate(-1L);
                break;
            case 5: // custom range
                String currencyCode = GnuCashApplication.getDefaultCurrencyCode();
                long earliest = mTransactionsDbAdapter.getTimestampOfEarliestTransaction(mAccountType, currencyCode);
                long latest = mTransactionsDbAdapter.getTimestampOfLatestTransaction(mAccountType, currencyCode);
                long today = now.toDate().getTime();
                long tomorrow = now.plusDays(1).toDate().getTime();
                DialogFragment rangeFragment = DateRangePickerDialogFragment.newInstance(
                    min(earliest, today),
                    max(latest, tomorrow),
                    this);
                rangeFragment.show(getSupportFragmentManager(), "range_dialog");
                break;
        }
        if (position != 5) { //the date picker will trigger the update itself
            updateDateRangeOnFragment();
        }
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {
        //nothing to see here, move along
    }

    @Override
    public void onDateSet(DatePicker view, int year, int monthOfYear, int dayOfMonth) {
        mReportPeriodStart = new LocalDate(year, monthOfYear, dayOfMonth);
        updateDateRangeOnFragment();
    }

    @Override
    public void onDateRangeSet(LocalDate startDate, LocalDate endDate) {
        mReportPeriodStart = startDate;
        mReportPeriodEnd = endDate;
        updateDateRangeOnFragment();
    }

    public AccountType getAccountType() {
        return mAccountType;
    }

    /**
     * Return the end time of the reporting period
     *
     * @return Time in millis
     */
    public long getReportPeriodEnd() {
        return mReportPeriodEnd.toDate().getTime();
    }

    /**
     * Return the start time of the reporting period
     *
     * @return Time in millis
     */
    public long getReportPeriodStart() {
        return mReportPeriodStart.toDate().getTime();
    }

    @Override
    public void refresh() {
        List<Fragment> fragments = getSupportFragmentManager().getFragments();
        for (Fragment fragment : fragments) {
            if (fragment instanceof Refreshable) {
                ((Refreshable) fragment).refresh();
            }
        }
    }

    @Override
    /**
     * Just another call to refresh
     */
    public void refresh(String uid) {
        refresh();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        outState.putSerializable(STATE_REPORT_TYPE, mReportType);
        outState.putLong(STATE_REPORT_START, mReportPeriodStart.toDate().getTime());
        outState.putLong(STATE_REPORT_END, mReportPeriodEnd.toDate().getTime());
    }
}
