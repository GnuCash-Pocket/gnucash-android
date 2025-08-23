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
import android.app.DatePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.DatePicker;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;

import com.codetroopers.betterpickers.recurrencepicker.EventRecurrence;
import com.codetroopers.betterpickers.recurrencepicker.EventRecurrenceFormatter;
import com.codetroopers.betterpickers.recurrencepicker.RecurrencePickerDialogFragment;

import org.gnucash.android.R;
import org.gnucash.android.app.MenuFragment;
import org.gnucash.android.databinding.FragmentBudgetFormBinding;
import org.gnucash.android.db.adapter.BudgetsDbAdapter;
import org.gnucash.android.db.adapter.DatabaseAdapter;
import org.gnucash.android.inputmethodservice.CalculatorKeyboardView;
import org.gnucash.android.model.Account;
import org.gnucash.android.model.Budget;
import org.gnucash.android.model.BudgetAmount;
import org.gnucash.android.model.Money;
import org.gnucash.android.model.Recurrence;
import org.gnucash.android.ui.adapter.QualifiedAccountNameAdapter;
import org.gnucash.android.ui.common.FormActivity;
import org.gnucash.android.ui.common.UxArgument;
import org.gnucash.android.ui.transaction.TransactionFormFragment;
import org.gnucash.android.ui.util.RecurrenceParser;
import org.gnucash.android.ui.util.RecurrenceViewClickListener;
import org.gnucash.android.ui.util.dialog.DatePickerDialogFragment;
import org.gnucash.android.ui.util.widget.CalculatorKeyboard;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Calendar;

import kotlin.Unit;
import kotlin.jvm.functions.Function0;
import timber.log.Timber;

/**
 * Fragment for creating or editing Budgets
 */
public class BudgetFormFragment extends MenuFragment implements RecurrencePickerDialogFragment.OnRecurrenceSetListener, DatePickerDialog.OnDateSetListener {

    public static final int REQUEST_EDIT_BUDGET_AMOUNTS = 0xBA;

    EventRecurrence mEventRecurrence = new EventRecurrence();
    String mRecurrenceRule;

    private BudgetsDbAdapter mBudgetsDbAdapter;

    private Budget mBudget;
    private final Calendar mStartDate = Calendar.getInstance();
    private ArrayList<BudgetAmount> mBudgetAmounts = new ArrayList<>();
    private QualifiedAccountNameAdapter accountNameAdapter;

    private FragmentBudgetFormBinding mBinding;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mBudgetsDbAdapter = BudgetsDbAdapter.getInstance();
        mBudgetAmounts.clear();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        mBinding = FragmentBudgetFormBinding.inflate(inflater, container, false);
        return mBinding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        final FragmentBudgetFormBinding binding = mBinding;

        binding.budgetAmountLayout.btnRemoveItem.setVisibility(View.GONE);
        binding.budgetAmountLayout.inputBudgetAmount.bindKeyboard(binding.calculatorKeyboard);
        binding.inputStartDate.setText(TransactionFormFragment.DATE_FORMATTER.print(mStartDate.getTimeInMillis()));
        binding.inputStartDate.setOnClickListener(this::onClickBudgetStartDate);
        binding.btnAddBudgetAmount.setOnClickListener(this::onOpenBudgetAmountEditor);

        binding.budgetAmountLayout.btnRemoveItem.setVisibility(View.GONE);
        binding.budgetAmountLayout.inputBudgetAmount.bindKeyboard(binding.calculatorKeyboard);
        binding.inputStartDate.setText(TransactionFormFragment.DATE_FORMATTER.print(mStartDate.getTimeInMillis()));

        accountNameAdapter = new QualifiedAccountNameAdapter(requireContext(), getViewLifecycleOwner());
        accountNameAdapter.load(new Function0<Unit>() {
            @Override
            public Unit invoke() {
                // TODO binding.budgetAmountLayout.inputBudgetAccountSpinner.setSelection();
                return null;
            }
        });
        binding.budgetAmountLayout.inputBudgetAccountSpinner.setAdapter(accountNameAdapter);
        String budgetUID = getArguments().getString(UxArgument.BUDGET_UID);
        if (budgetUID != null) { //if we are editing the budget
            initViews(binding, mBudget = mBudgetsDbAdapter.getRecord(budgetUID));
        }
        ActionBar actionbar = ((AppCompatActivity) requireActivity()).getSupportActionBar();
        assert actionbar != null;
        if (mBudget == null)
            actionbar.setTitle("Create Budget");
        else
            actionbar.setTitle(R.string.title_edit_budget);

        binding.inputRecurrence.setOnClickListener(
            new RecurrenceViewClickListener((AppCompatActivity) getActivity(), mRecurrenceRule, this));
    }

    /**
     * Initialize views when editing an existing budget
     *
     * @param budget Budget to use to initialize the views
     */
    private void initViews(final FragmentBudgetFormBinding binding, Budget budget) {
        Context context = binding.inputBudgetName.getContext();
        binding.inputBudgetName.setText(budget.getName());
        binding.inputDescription.setText(budget.getDescription());

        String recurrenceRuleString = budget.getRecurrence().getRuleString();
        mRecurrenceRule = recurrenceRuleString;
        mEventRecurrence.parse(recurrenceRuleString);
        binding.inputRecurrence.setText(budget.getRecurrence().getRepeatString(context));

        mBudgetAmounts = new ArrayList(budget.getCompactedBudgetAmounts());
        toggleAmountInputVisibility(binding);
    }

    /**
     * Extracts the budget amounts from the form
     * <p>If the budget amount was input using the simple form, then read the values.<br>
     * Else return the values gotten from the BudgetAmountEditor</p>
     *
     * @return List of budget amounts
     */
    private ArrayList<BudgetAmount> extractBudgetAmounts(FragmentBudgetFormBinding binding) {
        BigDecimal value = binding.budgetAmountLayout.inputBudgetAmount.getValue();
        if (value == null)
            return mBudgetAmounts;

        if (mBudgetAmounts.isEmpty()) { //has not been set in budget amounts editor
            ArrayList<BudgetAmount> budgetAmounts = new ArrayList<>();
            int accountPosition = binding.budgetAmountLayout.inputBudgetAccountSpinner.getSelectedItemPosition();
            Account account = accountNameAdapter.getAccount(accountPosition);
            if (account == null) return mBudgetAmounts;
            Money amount = new Money(value, account.getCommodity());
            BudgetAmount budgetAmount = new BudgetAmount(amount, account.getUID());
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
    private boolean canSave(@NonNull final FragmentBudgetFormBinding binding) {
        if (mEventRecurrence.until != null && mEventRecurrence.until.length() > 0
            || mEventRecurrence.count <= 0) {
            Toast.makeText(getActivity(),
                "Set a number periods in the recurrence dialog to save the budget",
                Toast.LENGTH_SHORT).show();
            return false;
        }

        mBudgetAmounts = extractBudgetAmounts(binding);
        String budgetName = binding.inputBudgetName.getText().toString();
        boolean canSave = mRecurrenceRule != null
            && !budgetName.isEmpty()
            && !mBudgetAmounts.isEmpty();

        if (!canSave) {
            if (budgetName.isEmpty()) {
                binding.nameTextInputLayout.setError("A name is required");
            } else {
                binding.nameTextInputLayout.setError(null);
            }

            if (mBudgetAmounts.isEmpty()) {
                binding.budgetAmountLayout.inputBudgetAmount.setError("Enter an amount for the budget");
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
        final FragmentBudgetFormBinding binding = mBinding;
        if (!canSave(binding))
            return;
        String name = binding.inputBudgetName.getText().toString().trim();

        if (mBudget == null) {
            mBudget = new Budget(name);
        } else {
            mBudget.setName(name);
        }

        // TODO: 22.10.2015 set the period num of the budget amount
        extractBudgetAmounts(binding);
        mBudget.setBudgetAmounts(mBudgetAmounts);

        mBudget.setDescription(binding.inputDescription.getText().toString().trim());

        Recurrence recurrence = RecurrenceParser.parse(mEventRecurrence);
        recurrence.setPeriodStart(mStartDate.getTimeInMillis());
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
        long dateMillis = mStartDate.getTimeInMillis();
        DatePickerDialogFragment.newInstance(BudgetFormFragment.this, dateMillis)
            .show(getParentFragmentManager(), "date_picker_fragment");
    }

    private void onOpenBudgetAmountEditor(View v) {
        Intent intent = new Intent(getActivity(), FormActivity.class);
        intent.putExtra(UxArgument.FORM_TYPE, FormActivity.FormType.BUDGET_AMOUNT_EDITOR.name());
        mBudgetAmounts = extractBudgetAmounts(mBinding);
        intent.putParcelableArrayListExtra(UxArgument.BUDGET_AMOUNT_LIST, mBudgetAmounts);
        startActivityForResult(intent, REQUEST_EDIT_BUDGET_AMOUNTS);
    }

    @Override
    public void onRecurrenceSet(String rrule) {
        Timber.i("Budget reoccurs: %s", rrule);
        Context context = mBinding.inputRecurrence.getContext();
        String repeatString = null;
        if (!TextUtils.isEmpty(rrule)) {
            try {
                mEventRecurrence.parse(rrule);
                mRecurrenceRule = rrule;
                repeatString = EventRecurrenceFormatter.getRepeatString(context, context.getResources(), mEventRecurrence, true);
            } catch (Exception e) {
                Timber.e(e, "Bad recurrence for [%s]", rrule);
            }
        }
        if (TextUtils.isEmpty(repeatString)) {
            repeatString = context.getString(R.string.label_tap_to_create_schedule);
        }
        mBinding.inputRecurrence.setText(repeatString);
    }

    @Override
    public void onDateSet(DatePicker view, int year, int month, int dayOfMonth) {
        mStartDate.set(Calendar.YEAR, year);
        mStartDate.set(Calendar.MONTH, month);
        mStartDate.set(Calendar.DAY_OF_MONTH, dayOfMonth);
        mBinding.inputStartDate.setText(TransactionFormFragment.DATE_FORMATTER.print(mStartDate.getTimeInMillis()));
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_EDIT_BUDGET_AMOUNTS) {
            if (resultCode == Activity.RESULT_OK) {
                ArrayList<BudgetAmount> budgetAmounts = data.getParcelableArrayListExtra(UxArgument.BUDGET_AMOUNT_LIST);
                if (budgetAmounts != null) {
                    mBudgetAmounts = budgetAmounts;
                    toggleAmountInputVisibility(mBinding);
                }
                return;
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    /**
     * Toggles the visibility of the amount input based on {@link #mBudgetAmounts}
     */
    private void toggleAmountInputVisibility(FragmentBudgetFormBinding binding) {
        if (mBudgetAmounts.size() > 1) {
            binding.budgetAmountLayout.getRoot().setVisibility(View.GONE);
            binding.btnAddBudgetAmount.setText("Edit Budget Amounts");
        } else {
            binding.btnAddBudgetAmount.setText("Add Budget Amounts");
            binding.budgetAmountLayout.getRoot().setVisibility(View.VISIBLE);
            if (!mBudgetAmounts.isEmpty()) {
                BudgetAmount budgetAmount = mBudgetAmounts.get(0);
                binding.budgetAmountLayout.inputBudgetAmount.setValue(budgetAmount.getAmount().toBigDecimal());
                binding.budgetAmountLayout.inputBudgetAccountSpinner.setSelection(accountNameAdapter.getPosition(budgetAmount.getAccountUID()));
            }
        }
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        View view = getView();
        if (view instanceof ViewGroup parent) {
            CalculatorKeyboardView keyboardView = mBinding.calculatorKeyboard.calculatorKeyboard;
            CalculatorKeyboard.rebind(parent, keyboardView, null);
        }
    }
}
