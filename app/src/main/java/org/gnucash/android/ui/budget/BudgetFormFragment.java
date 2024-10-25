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

package org.gnucash.android.ui.budget;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.codetroopers.betterpickers.calendardatepicker.CalendarDatePickerDialogFragment;
import com.codetroopers.betterpickers.recurrencepicker.EventRecurrence;
import com.codetroopers.betterpickers.recurrencepicker.EventRecurrenceFormatter;
import com.codetroopers.betterpickers.recurrencepicker.RecurrencePickerDialogFragment;

import org.gnucash.android.R;
import org.gnucash.android.databinding.FragmentBudgetFormBinding;
import org.gnucash.android.db.DatabaseSchema;
import org.gnucash.android.db.adapter.AccountsDbAdapter;
import org.gnucash.android.db.adapter.BudgetsDbAdapter;
import org.gnucash.android.db.adapter.DatabaseAdapter;
import org.gnucash.android.model.Budget;
import org.gnucash.android.model.BudgetAmount;
import org.gnucash.android.model.Commodity;
import org.gnucash.android.model.Money;
import org.gnucash.android.model.Recurrence;
import org.gnucash.android.ui.common.FormActivity;
import org.gnucash.android.ui.common.UxArgument;
import org.gnucash.android.ui.transaction.TransactionFormFragment;
import org.gnucash.android.ui.util.RecurrenceParser;
import org.gnucash.android.ui.util.RecurrenceViewClickListener;
import org.gnucash.android.util.QualifiedAccountNameCursorAdapter;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;

import timber.log.Timber;

/**
 * Fragment for creating or editing Budgets
 */
public class BudgetFormFragment extends Fragment implements RecurrencePickerDialogFragment.OnRecurrenceSetListener, CalendarDatePickerDialogFragment.OnDateSetListener {

    public static final int REQUEST_EDIT_BUDGET_AMOUNTS = 0xBA;

    EventRecurrence mEventRecurrence = new EventRecurrence();
    String mRecurrenceRule;

    private BudgetsDbAdapter mBudgetsDbAdapter;

    private Budget mBudget;
    private Calendar mStartDate;
    private ArrayList<BudgetAmount> mBudgetAmounts;
    private AccountsDbAdapter mAccountsDbAdapter;
    private QualifiedAccountNameCursorAdapter mAccountsCursorAdapter;

    private FragmentBudgetFormBinding mBinding;

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        mBinding = FragmentBudgetFormBinding.inflate(inflater, container, false);
        View view = mBinding.getRoot();

        view.findViewById(R.id.btn_remove_item).setVisibility(View.GONE);
        mBinding.budgetAmountLayout.inputBudgetAmount.bindListeners(mBinding.calculatorKeyboard);
        mBinding.inputStartDate.setText(TransactionFormFragment.DATE_FORMATTER.print(mStartDate.getTimeInMillis()));
        mBinding.inputStartDate.setOnClickListener(this::onClickBudgetStartDate);
        mBinding.btnAddBudgetAmount.setOnClickListener(this::onOpenBudgetAmountEditor);
        return view;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mBudgetsDbAdapter = BudgetsDbAdapter.getInstance();
        mStartDate = Calendar.getInstance();
        mBudgetAmounts = new ArrayList<>();
        String conditions = "(" + DatabaseSchema.AccountEntry.COLUMN_HIDDEN + " = 0 )";
        mAccountsDbAdapter = AccountsDbAdapter.getInstance();
        Cursor accountCursor = mAccountsDbAdapter.fetchAccountsOrderedByFavoriteAndFullName(conditions, null);
        mAccountsCursorAdapter = new QualifiedAccountNameCursorAdapter(getActivity(), accountCursor);
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        setHasOptionsMenu(true);

        mBinding.budgetAmountLayout.inputBudgetAccountSpinner.setAdapter(mAccountsCursorAdapter);
        String budgetUID = getArguments().getString(UxArgument.BUDGET_UID);
        if (budgetUID != null) { //if we are editing the budget
            initViews(mBudget = mBudgetsDbAdapter.getRecord(budgetUID));
        }
        ActionBar actionbar = ((AppCompatActivity) getActivity()).getSupportActionBar();
        assert actionbar != null;
        if (mBudget == null)
            actionbar.setTitle("Create Budget");
        else
            actionbar.setTitle(R.string.title_edit_budget);

        mBinding.inputRecurrence.setOnClickListener(
                new RecurrenceViewClickListener((AppCompatActivity) getActivity(), mRecurrenceRule, this));
    }

    /**
     * Initialize views when editing an existing budget
     *
     * @param budget Budget to use to initialize the views
     */
    private void initViews(Budget budget) {
        mBinding.inputBudgetName.setText(budget.getName());
        mBinding.inputDescription.setText(budget.getDescription());

        String recurrenceRuleString = budget.getRecurrence().getRuleString();
        mRecurrenceRule = recurrenceRuleString;
        mEventRecurrence.parse(recurrenceRuleString);
        mBinding.inputRecurrence.setText(budget.getRecurrence().getRepeatString());

        mBudgetAmounts = (ArrayList<BudgetAmount>) budget.getCompactedBudgetAmounts();
        toggleAmountInputVisibility();
    }

    /**
     * Extracts the budget amounts from the form
     * <p>If the budget amount was input using the simple form, then read the values.<br>
     * Else return the values gotten from the BudgetAmountEditor</p>
     *
     * @return List of budget amounts
     */
    private ArrayList<BudgetAmount> extractBudgetAmounts() {
        BigDecimal value = mBinding.budgetAmountLayout.inputBudgetAmount.getValue();
        if (value == null)
            return mBudgetAmounts;

        if (mBudgetAmounts.isEmpty()) { //has not been set in budget amounts editor
            ArrayList<BudgetAmount> budgetAmounts = new ArrayList<>();
            Money amount = new Money(value, Commodity.DEFAULT_COMMODITY);
            String accountUID = mAccountsDbAdapter.getUID(mBinding.budgetAmountLayout.inputBudgetAccountSpinner.getSelectedItemId());
            BudgetAmount budgetAmount = new BudgetAmount(amount, accountUID);
            budgetAmounts.add(budgetAmount);
            return budgetAmounts;
        } else {
            return mBudgetAmounts;
        }
    }

    /**
     * Checks that this budget can be saved
     * Also sets the appropriate error messages on the relevant views
     * <p>For a budget to be saved, it needs to have a name, an amount and a schedule</p>
     *
     * @return {@code true} if the budget can be saved, {@code false} otherwise
     */
    private boolean canSave() {
        if (mEventRecurrence.until != null && mEventRecurrence.until.length() > 0
                || mEventRecurrence.count <= 0) {
            Toast.makeText(getActivity(),
                    "Set a number periods in the recurrence dialog to save the budget",
                    Toast.LENGTH_SHORT).show();
            return false;
        }

        mBudgetAmounts = extractBudgetAmounts();
        String budgetName = mBinding.inputBudgetName.getText().toString();
        boolean canSave = mRecurrenceRule != null
                && !budgetName.isEmpty()
                && !mBudgetAmounts.isEmpty();

        if (!canSave) {
            if (budgetName.isEmpty()) {
                mBinding.nameTextInputLayout.setError("A name is required");
                mBinding.nameTextInputLayout.setErrorEnabled(true);
            } else {
                mBinding.nameTextInputLayout.setErrorEnabled(false);
            }

            if (mBudgetAmounts.isEmpty()) {
                mBinding.budgetAmountLayout.inputBudgetAmount.setError("Enter an amount for the budget");
                Toast.makeText(getActivity(), "Add budget amounts in order to save the budget",
                        Toast.LENGTH_SHORT).show();
            }

            if (mRecurrenceRule == null) {
                Toast.makeText(getActivity(), "Set a repeat pattern to create a budget!",
                        Toast.LENGTH_SHORT).show();
            }
        }

        return canSave;
    }

    /**
     * Extracts the information from the form and saves the budget
     */
    private void saveBudget() {
        if (!canSave())
            return;
        String name = mBinding.inputBudgetName.getText().toString().trim();


        if (mBudget == null) {
            mBudget = new Budget(name);
        } else {
            mBudget.setName(name);
        }

        // TODO: 22.10.2015 set the period num of the budget amount
        extractBudgetAmounts();
        mBudget.setBudgetAmounts(mBudgetAmounts);

        mBudget.setDescription(mBinding.inputDescription.getText().toString().trim());

        Recurrence recurrence = RecurrenceParser.parse(mEventRecurrence);
        recurrence.setPeriodStart(new Timestamp(mStartDate.getTimeInMillis()));
        mBudget.setRecurrence(recurrence);

        mBudgetsDbAdapter.addRecord(mBudget, DatabaseAdapter.UpdateMethod.insert);
        getActivity().finish();
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.default_save_actions, menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_save:
                saveBudget();
                return true;
        }
        return false;
    }

    private void onClickBudgetStartDate(View v) {
        long dateMillis = 0;
        try {
            dateMillis = TransactionFormFragment.DATE_FORMATTER.parseMillis(((TextView) v).getText().toString());
        } catch (IllegalArgumentException e) {
            Timber.e("Error converting input time to Date object");
        }
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(dateMillis);

        int year = calendar.get(Calendar.YEAR);
        int monthOfYear = calendar.get(Calendar.MONTH);
        int dayOfMonth = calendar.get(Calendar.DAY_OF_MONTH);
        CalendarDatePickerDialogFragment datePickerDialog = new CalendarDatePickerDialogFragment();
        datePickerDialog.setOnDateSetListener(BudgetFormFragment.this);
        datePickerDialog.setPreselectedDate(year, monthOfYear, dayOfMonth);
        datePickerDialog.show(getFragmentManager(), "date_picker_fragment");
    }

    private void onOpenBudgetAmountEditor(View v) {
        Intent intent = new Intent(getActivity(), FormActivity.class);
        intent.putExtra(UxArgument.FORM_TYPE, FormActivity.FormType.BUDGET_AMOUNT_EDITOR.name());
        mBudgetAmounts = extractBudgetAmounts();
        intent.putParcelableArrayListExtra(UxArgument.BUDGET_AMOUNT_LIST, mBudgetAmounts);
        startActivityForResult(intent, REQUEST_EDIT_BUDGET_AMOUNTS);
    }

    @Override
    public void onRecurrenceSet(String rrule) {
        mRecurrenceRule = rrule;
        String repeatString = getString(R.string.label_tap_to_create_schedule);
        if (mRecurrenceRule != null) {
            mEventRecurrence.parse(mRecurrenceRule);
            repeatString = EventRecurrenceFormatter.getRepeatString(getActivity(), getResources(), mEventRecurrence, true);
        }

        mBinding.inputRecurrence.setText(repeatString);
    }

    @Override
    public void onDateSet(CalendarDatePickerDialogFragment dialog, int year, int monthOfYear, int dayOfMonth) {
        Calendar cal = new GregorianCalendar(year, monthOfYear, dayOfMonth);
        mBinding.inputStartDate.setText(TransactionFormFragment.DATE_FORMATTER.print(cal.getTimeInMillis()));
        mStartDate.set(Calendar.YEAR, year);
        mStartDate.set(Calendar.MONTH, monthOfYear);
        mStartDate.set(Calendar.DAY_OF_MONTH, dayOfMonth);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_EDIT_BUDGET_AMOUNTS) {
            if (resultCode == Activity.RESULT_OK) {
                ArrayList<BudgetAmount> budgetAmounts = data.getParcelableArrayListExtra(UxArgument.BUDGET_AMOUNT_LIST);
                if (budgetAmounts != null) {
                    mBudgetAmounts = budgetAmounts;
                    toggleAmountInputVisibility();
                }
                return;
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    /**
     * Toggles the visibility of the amount input based on {@link #mBudgetAmounts}
     */
    private void toggleAmountInputVisibility() {
        if (mBudgetAmounts.size() > 1) {
            mBinding.budgetAmountLayout.getRoot().setVisibility(View.GONE);
            mBinding.btnAddBudgetAmount.setText("Edit Budget Amounts");
        } else {
            mBinding.btnAddBudgetAmount.setText("Add Budget Amounts");
            mBinding.budgetAmountLayout.getRoot().setVisibility(View.VISIBLE);
            if (!mBudgetAmounts.isEmpty()) {
                BudgetAmount budgetAmount = mBudgetAmounts.get(0);
                mBinding.budgetAmountLayout.inputBudgetAmount.setValue(budgetAmount.getAmount().asBigDecimal());
                mBinding.budgetAmountLayout.inputBudgetAccountSpinner.setSelection(mAccountsCursorAdapter.getItemPosition(budgetAmount.getAccountUID()));
            }
        }
    }
}
