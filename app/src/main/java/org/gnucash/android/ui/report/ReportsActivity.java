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

import android.app.DatePickerDialog;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.DatePicker;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import org.gnucash.android.R;
import org.gnucash.android.databinding.ActivityReportsBinding;
import org.gnucash.android.db.adapter.TransactionsDbAdapter;
import org.gnucash.android.model.AccountType;
import org.gnucash.android.model.Commodity;
import org.gnucash.android.ui.adapter.AccountTypesAdapter;
import org.gnucash.android.ui.common.BaseDrawerActivity;
import org.gnucash.android.ui.common.Refreshable;
import org.gnucash.android.ui.util.dialog.DateRangePickerDialogFragment;
import org.gnucash.android.util.DateExtKt;
import org.jetbrains.annotations.NotNull;
import org.joda.time.LocalDate;
import org.joda.time.LocalDateTime;

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

    private TransactionsDbAdapter transactionsDbAdapter;
    private AccountType accountType = AccountType.EXPENSE;
    private ReportType reportType = ReportType.NONE;

    public enum GroupInterval {WEEK, MONTH, QUARTER, YEAR, ALL}

    // default time range is the last 3 months
    @Nullable
    private LocalDateTime reportPeriodStart = LocalDateTime.now().minusMonths(3);
    @Nullable
    private LocalDateTime reportPeriodEnd = LocalDateTime.now();

    private GroupInterval reportGroupInterval = GroupInterval.MONTH;

    private ActivityReportsBinding binding;

    AdapterView.OnItemSelectedListener reportTypeSelectedListener = new AdapterView.OnItemSelectedListener() {

        @Override
        public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
            if (view == null) return;
            ReportType reportType = ReportType.values()[position];
            if (ReportsActivity.this.reportType != reportType) {
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
        binding = ActivityReportsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        drawerLayout = binding.drawerLayout;
        navigationView = binding.navView;
        toolbar = binding.toolbarLayout.toolbar;
        toolbarProgress = binding.toolbarLayout.toolbarProgress.progress;
    }

    @Override
    public int getTitleRes() {
        return R.string.title_reports;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final Context context = this;
        transactionsDbAdapter = TransactionsDbAdapter.getInstance();

        ActionBar actionBar = getSupportActionBar();
        assert actionBar != null;
        ArrayAdapter<String> typesAdapter = new ArrayAdapter<>(actionBar.getThemedContext(),
            android.R.layout.simple_list_item_1,
            ReportType.getReportNames(context));
        binding.toolbarLayout.toolbarSpinner.setAdapter(typesAdapter);
        binding.toolbarLayout.toolbarSpinner.setOnItemSelectedListener(reportTypeSelectedListener);

        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(context, R.array.report_time_range,
            android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.timeRangeSpinner.setAdapter(adapter);
        binding.timeRangeSpinner.setOnItemSelectedListener(this);
        binding.timeRangeSpinner.setSelection(1);

        AccountTypesAdapter accountTypeAdapter = AccountTypesAdapter.expenseAndIncome(context);
        binding.reportAccountTypeSpinner.setAdapter(accountTypeAdapter);
        binding.reportAccountTypeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int position, long id) {
                if (view == null) return;
                if (position < 0) return;
                AccountTypesAdapter.Label label = accountTypeAdapter.getItem(position);
                updateAccountTypeOnFragments(label.value);
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
                reportType = savedInstanceState.getSerializable(STATE_REPORT_TYPE, ReportType.class);
            } else {
                reportType = (ReportType) savedInstanceState.getSerializable(STATE_REPORT_TYPE);
            }
            reportPeriodStart = DateExtKt.toLocalDateTime(savedInstanceState.getLong(STATE_REPORT_START));
            reportPeriodEnd = DateExtKt.toLocalDateTime(savedInstanceState.getLong(STATE_REPORT_END));
        }
    }

    void onFragmentResumed(@NonNull Fragment fragment) {
        ReportType reportType = ReportType.NONE;
        if (fragment instanceof BaseReportFragment reportFragment) {
            reportType = reportFragment.getReportType();

            int visibility = reportFragment.requiresAccountTypeOptions() ? View.VISIBLE : View.GONE;
            binding.reportAccountTypeSpinner.setVisibility(visibility);
        }

        setTitlesColor(ContextCompat.getColor(this, reportType.colorId));
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
        this.reportType = reportType;
        binding.toolbarLayout.toolbarSpinner.setSelection(reportType.ordinal());
    }

    private void toggleToolbarTitleVisibility(ReportType reportType) {
        ActionBar actionBar = getSupportActionBar();
        assert actionBar != null;

        if (reportType == ReportType.NONE) {
            binding.toolbarLayout.toolbarSpinner.setVisibility(View.GONE);
        } else {
            binding.toolbarLayout.toolbarSpinner.setVisibility(View.VISIBLE);
        }
        actionBar.setDisplayShowTitleEnabled(reportType == ReportType.NONE);
    }

    /**
     * Updates the reporting time range for all listening fragments
     */
    private void updateDateRangeOnFragment() {
        List<Fragment> fragments = getSupportFragmentManager().getFragments();
        for (Fragment fragment : fragments) {
            if (fragment instanceof ReportOptionsListener) {
                ((ReportOptionsListener) fragment).onTimeRangeUpdated(reportPeriodStart, reportPeriodEnd);
            }
        }
    }

    /**
     * Updates the account type for all attached fragments which are listening
     */
    private void updateAccountTypeOnFragments(AccountType accountType) {
        this.accountType = accountType;
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
                ((ReportOptionsListener) fragment).onGroupingUpdated(reportGroupInterval);
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(@NonNull Menu menu) {
        super.onCreateOptionsMenu(menu);
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
                reportGroupInterval = GroupInterval.MONTH;
                updateGroupingOnFragments();
                return true;

            case R.id.group_by_quarter:
                item.setChecked(true);
                reportGroupInterval = GroupInterval.QUARTER;
                updateGroupingOnFragments();
                return true;

            case R.id.group_by_year:
                item.setChecked(true);
                reportGroupInterval = GroupInterval.YEAR;
                updateGroupingOnFragments();
                return true;

            case android.R.id.home:
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        if (view == null) return;
        LocalDateTime now = LocalDateTime.now();
        reportPeriodEnd = now;
        switch (position) {
            case 0: //current month
                reportPeriodStart = now.dayOfMonth().withMinimumValue();
                break;
            case 1: // last 3 months.
                reportPeriodStart = now.minusMonths(3);
                break;
            case 2: // last 6 months
                reportPeriodStart = now.minusMonths(6);
                break;
            case 3: // last year
                reportPeriodStart = now.minusYears(1);
                break;
            case 4: //ALL TIME
                reportPeriodStart = null;
                reportPeriodEnd = null;
                break;
            case 5: // custom range
                String commodityUID = Commodity.DEFAULT_COMMODITY.getUID();
                long earliest = transactionsDbAdapter.getTimestampOfEarliestTransaction(accountType, commodityUID);
                DialogFragment rangeFragment = DateRangePickerDialogFragment.newInstance(
                    new LocalDate(earliest),
                    now.toLocalDate(),
                    this
                );
                rangeFragment.show(getSupportFragmentManager(), "range_dialog");
                return;
        }
        //the date picker will trigger the update itself
        updateDateRangeOnFragment();
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {
        //nothing to see here, move along
    }

    @Override
    public void onDateSet(DatePicker view, int year, int monthOfYear, int dayOfMonth) {
        reportPeriodStart = new LocalDateTime(year, monthOfYear, dayOfMonth, 0, 0);
        updateDateRangeOnFragment();
    }

    @Override
    public void onDateRangeSet(LocalDate startDate, LocalDate endDate) {
        reportPeriodStart = startDate.toDateTimeAtStartOfDay().toLocalDateTime();
        reportPeriodEnd = endDate.toDateTimeAtCurrentTime().toLocalDateTime();
        updateDateRangeOnFragment();
    }

    public AccountType getAccountType() {
        return accountType;
    }

    /**
     * Return the end time of the reporting period
     *
     * @return Time in millis
     */
    @Nullable
    public LocalDateTime getReportPeriodEnd() {
        return reportPeriodEnd;
    }

    /**
     * Return the start time of the reporting period
     *
     * @return Time in millis
     */
    @Nullable
    public LocalDateTime getReportPeriodStart() {
        return reportPeriodStart;
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
    protected void onSaveInstanceState(@NotNull Bundle outState) {
        super.onSaveInstanceState(outState);

        outState.putSerializable(STATE_REPORT_TYPE, reportType);
        outState.putLong(STATE_REPORT_START, DateExtKt.toMillis(reportPeriodStart));
        outState.putLong(STATE_REPORT_END, DateExtKt.toMillis(reportPeriodEnd));
    }
}
