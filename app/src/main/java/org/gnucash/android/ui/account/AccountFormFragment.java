/*
 * Copyright (c) 2012 - 2014 Ngewi Fet <ngewif@gmail.com>
 * Copyright (c) 2014 Yongxin Wang <fefe.wyx@gmail.com>
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

package org.gnucash.android.ui.account;

import static org.gnucash.android.ui.colorpicker.ColorPickerDialog.COLOR_PICKER_DIALOG_TAG;
import static org.gnucash.android.ui.util.widget.ViewExtKt.setTextToEnd;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.Spinner;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentResultListener;

import org.gnucash.android.R;
import org.gnucash.android.app.GnuCashApplication;
import org.gnucash.android.app.MenuFragment;
import org.gnucash.android.databinding.FragmentAccountFormBinding;
import org.gnucash.android.db.DatabaseSchema;
import org.gnucash.android.db.adapter.AccountsDbAdapter;
import org.gnucash.android.db.adapter.DatabaseAdapter;
import org.gnucash.android.model.Account;
import org.gnucash.android.model.AccountType;
import org.gnucash.android.model.Commodity;
import org.gnucash.android.ui.adapter.AccountTypesAdapter;
import org.gnucash.android.ui.adapter.CommoditiesAdapter;
import org.gnucash.android.ui.adapter.QualifiedAccountNameAdapter;
import org.gnucash.android.ui.colorpicker.ColorPickerDialog;
import org.gnucash.android.ui.common.UxArgument;

import java.util.ArrayList;
import java.util.List;

import timber.log.Timber;

/**
 * Fragment used for creating and editing accounts
 *
 * @author Ngewi Fet <ngewif@gmail.com>
 * @author Yongxin Wang <fefe.wyx@gmail.com>
 */
public class AccountFormFragment extends MenuFragment implements FragmentResultListener {

    /**
     * Accounts database adapter
     */
    private AccountsDbAdapter mAccountsDbAdapter = AccountsDbAdapter.getInstance();
    private AccountTypesAdapter accountTypesAdapter;
    private CommoditiesAdapter commoditiesAdapter;

    /**
     * GUID of the parent account
     * This value is set to the parent account of the transaction being edited or
     * the account in which a new sub-account is being created
     */
    private String mParentAccountUID = null;
    private String selectedParentAccountUID = null;

    /**
     * Account UID of the root account
     */
    private String mRootAccountUID = null;

    /**
     * Reference to account object which will be created at end of dialog
     */
    private Account mAccount = null;

    /**
     * List of all descendant Account UIDs, if we are modifying an account
     * null if creating a new account
     */
    private final List<String> descendantAccountUIDs = new ArrayList<>();
    private final List<Account> descendantAccounts = new ArrayList<>();

    /**
     * Adapter for the parent account spinner
     */
    private QualifiedAccountNameAdapter parentAccountNameAdapter;
    private QualifiedAccountNameAdapter accountNameAdapter;

    /**
     * Adapter which binds to the spinner for default transfer account
     */
    private QualifiedAccountNameAdapter defaultAccountNameAdapter;

    /**
     * Flag indicating if double entry transactions are enabled
     */
    private boolean mUseDoubleEntry;

    @ColorInt
    private int mSelectedColor = Account.DEFAULT_COLOR;
    private Account selectedDefaultTransferAccount;
    private String selectedName = "";
    private AccountType selectedAccountType = AccountType.ROOT;
    private Commodity selectedCommodity = Commodity.DEFAULT_COMMODITY;

    private FragmentAccountFormBinding mBinding;

    /**
     * Construct a new instance of the dialog
     *
     * @return New instance of the dialog fragment
     */
    static public AccountFormFragment newInstance() {
        return new AccountFormFragment();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final Context context = requireContext();
        mUseDoubleEntry = GnuCashApplication.isDoubleEntryEnabled(context);
        String accountUID = getArguments().getString(UxArgument.SELECTED_ACCOUNT_UID);
        mParentAccountUID = getArguments().getString(UxArgument.PARENT_ACCOUNT_UID);
        mRootAccountUID = mAccountsDbAdapter.getOrCreateRootAccountUID();

        mAccountsDbAdapter = AccountsDbAdapter.getInstance();
        accountNameAdapter = new QualifiedAccountNameAdapter(context, null, null, mAccountsDbAdapter);
        accountTypesAdapter = new AccountTypesAdapter(context);
        commoditiesAdapter = new CommoditiesAdapter(context);
        Account account = accountNameAdapter.getAccount(accountUID);
        mAccount = account;
        if (account != null) {
            mParentAccountUID = account.getParentUID();
        }
        if (TextUtils.isEmpty(mParentAccountUID)) {
            // null parent, set Parent as root
            mParentAccountUID = mRootAccountUID;
        }
        selectedParentAccountUID = mParentAccountUID;
    }

    /**
     * Inflates the dialog view and retrieves references to the dialog elements
     */
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        mBinding = FragmentAccountFormBinding.inflate(inflater, container, false);
        return mBinding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        final FragmentAccountFormBinding binding = mBinding;
        binding.inputAccountName.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                //nothing to see here, move along
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                //nothing to see here, move along
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (!TextUtils.isEmpty(s)) {
                    binding.nameTextInputLayout.setError(null);
                }
                selectedName = s.toString();
            }
        });

        binding.inputAccountTypeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int position, long id) {
                if (view == null) return;
                selectedAccountType = accountTypesAdapter.getType(position);
                loadParentAccountList(binding, selectedAccountType);
                setParentAccountSelection(binding, mParentAccountUID);
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {
                //nothing to see here, move along
            }
        });

        binding.inputParentAccount.setEnabled(false);
        binding.inputParentAccount.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (view == null) return;
                selectedParentAccountUID = parentAccountNameAdapter.getUID(position);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        binding.checkboxParentAccount.setOnCheckedChangeListener(new OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                binding.inputParentAccount.setEnabled(isChecked);
            }
        });

        binding.inputDefaultTransferAccount.setEnabled(false);
        binding.inputDefaultTransferAccount.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (view == null) return;
                selectedDefaultTransferAccount = defaultAccountNameAdapter.getAccount(position);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        binding.checkboxDefaultTransferAccount.setOnCheckedChangeListener(new OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean isChecked) {
                binding.inputDefaultTransferAccount.setEnabled(isChecked);
            }
        });

        binding.inputColorPicker.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showColorPickerDialog();
            }
        });

        binding.inputCurrencySpinner.setAdapter(commoditiesAdapter);
        binding.inputCurrencySpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (view == null) return;
                selectedCommodity = commoditiesAdapter.getCommodity(position);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        ActionBar actionBar = ((AppCompatActivity) requireActivity()).getSupportActionBar();
        assert actionBar != null;

        //need to load the cursor adapters for the spinners before initializing the views
        binding.inputAccountTypeSpinner.setAdapter(accountTypesAdapter);

        Account account = mAccount;
        loadDefaultTransferAccountList(binding, account);
        if (account != null) {
            actionBar.setTitle(R.string.title_edit_account);
            initializeViewsWithAccount(binding, account);
            //do not immediately open the keyboard when editing an account
            requireActivity().getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        } else {
            actionBar.setTitle(R.string.title_create_account);
            initializeViews(binding);
        }
    }

    /**
     * Initialize view with the properties of <code>account</code>.
     * This is applicable when editing an account
     *
     * @param account Account whose fields are used to populate the form
     */
    private void initializeViewsWithAccount(@NonNull FragmentAccountFormBinding binding, @NonNull Account account) {
        if (account == null) {
            throw new IllegalArgumentException("Account required");
        }

        selectedName = account.getName();
        List<Account> descendants = accountNameAdapter.getDescendants(account);
        descendantAccounts.clear();
        descendantAccounts.addAll(descendants);
        descendantAccountUIDs.clear();
        for (Account descendant : descendants) {
            descendantAccountUIDs.add(descendant.getUID());
        }

        setSelectedCurrency(binding, account.getCommodity());
        setAccountTypeSelection(binding, account.getAccountType());
        loadParentAccountList(binding, account.getAccountType());
        setParentAccountSelection(binding, account.getParentUID());

        if (mAccountsDbAdapter.getTransactionMaxSplitNum(account.getUID()) > 1) {
            //TODO: Allow changing the currency and effecting the change for all transactions without any currency exchange (purely cosmetic change)
            binding.inputCurrencySpinner.setEnabled(false);
        }

        setTextToEnd(binding.inputAccountName, account.getName());
        binding.inputAccountDescription.setText(account.getDescription());
        mBinding.notes.setText(account.getNote());

        if (mUseDoubleEntry) {
            String defaultTransferAccountUID = account.getDefaultTransferAccountUID();
            if (!TextUtils.isEmpty(defaultTransferAccountUID)) {
                setDefaultTransferAccountSelection(binding, defaultTransferAccountUID, true);
            } else {
                String parentUID = account.getParentUID();
                while (!TextUtils.isEmpty(parentUID)) {
                    Account parentAccount = defaultAccountNameAdapter.getAccount(parentUID);
                    if (parentAccount == null) break;
                    defaultTransferAccountUID = parentAccount.getDefaultTransferAccountUID();
                    if (!TextUtils.isEmpty(defaultTransferAccountUID)) {
                        setDefaultTransferAccountSelection(binding, parentUID, false);
                        break; //we found a parent with default transfer setting
                    }
                    parentUID = parentAccount.getParentUID();
                }
            }
        }

        mBinding.placeholderStatus.setChecked(account.isPlaceholder());
        mBinding.favoriteStatus.setChecked(account.isFavorite());
        mBinding.hiddenStatus.setChecked(account.isHidden());
        mSelectedColor = account.getColor();
        binding.inputColorPicker.setBackgroundTintList(ColorStateList.valueOf(mSelectedColor));
    }

    /**
     * Initialize views with defaults for new account
     */
    private void initializeViews(@NonNull FragmentAccountFormBinding binding) {
        selectedName = "";
        descendantAccountUIDs.clear();
        setSelectedCurrency(binding, Commodity.DEFAULT_COMMODITY);
        binding.inputColorPicker.setBackgroundTintList(ColorStateList.valueOf(mSelectedColor));

        String parentUID = mParentAccountUID;
        if (!TextUtils.isEmpty(parentUID)) {
            Account parentAccount = accountNameAdapter.getAccount(parentUID);
            if (parentAccount != null) {
                setSelectedCurrency(binding, parentAccount.getCommodity());
                AccountType parentAccountType = parentAccount.getAccountType();
                setAccountTypeSelection(binding, parentAccountType);
                loadParentAccountList(binding, parentAccountType);
                setParentAccountSelection(binding, parentUID);
            }
        }
    }

    /**
     * Selects the corresponding account type in the spinner
     *
     * @param accountType the account type
     */
    private void setAccountTypeSelection(@NonNull FragmentAccountFormBinding binding, AccountType accountType) {
        int position = accountTypesAdapter.getPosition(accountType);
        binding.inputAccountTypeSpinner.setSelection(position);
    }

    /**
     * Toggles the visibility of the default transfer account input fields.
     * This field is irrelevant for users who do not use double accounting
     */
    private void setDefaultTransferAccountInputsVisible(@NonNull FragmentAccountFormBinding binding, boolean visible) {
        final int visibility = visible ? View.VISIBLE : View.GONE;
        binding.checkboxDefaultTransferAccount.setVisibility(visibility);
        binding.inputDefaultTransferAccount.setVisibility(visibility);
    }

    /**
     * Selects the currency in the spinner
     *
     * @param commodity the selected commodity
     */
    private void setSelectedCurrency(@NonNull FragmentAccountFormBinding binding, Commodity commodity) {
        int position = commoditiesAdapter.getPosition(commodity);
        binding.inputCurrencySpinner.setSelection(position);
    }

    /**
     * Selects the account with UID in the parent accounts spinner
     *
     * @param parentAccountUID UID of parent account to be selected
     */
    private void setParentAccountSelection(@NonNull FragmentAccountFormBinding binding, @Nullable String parentAccountUID) {
        if (TextUtils.isEmpty(parentAccountUID) || parentAccountUID.equals(mRootAccountUID)) {
            return;
        }

        int position = parentAccountNameAdapter.getPosition(parentAccountUID);
        if (position >= 0) {
            binding.checkboxParentAccount.setChecked(true);
            binding.inputParentAccount.setEnabled(true);
            binding.inputParentAccount.setSelection(position, true);
        }
    }

    /**
     * Selects the account with UID <code>parentAccountId</code> in the default transfer account spinner
     *
     * @param defaultTransferAccountUID UID of parent account to be selected
     */
    private void setDefaultTransferAccountSelection(@NonNull FragmentAccountFormBinding binding, @Nullable String defaultTransferAccountUID, boolean enableTransferAccount) {
        if (TextUtils.isEmpty(defaultTransferAccountUID)) {
            return;
        }
        binding.checkboxDefaultTransferAccount.setChecked(enableTransferAccount);
        binding.inputDefaultTransferAccount.setEnabled(enableTransferAccount);

        int defaultAccountPosition = defaultAccountNameAdapter.getPosition(defaultTransferAccountUID);
        binding.inputDefaultTransferAccount.setSelection(defaultAccountPosition);
    }

    /**
     * Returns an array of colors used for accounts.
     * The array returned has the actual color values and not the resource ID.
     *
     * @return Integer array of colors used for accounts
     */
    private int[] getAccountColorOptions() {
        Context context = requireContext();
        Resources res = context.getResources();
        return res.getIntArray(R.array.account_colors);
    }

    /**
     * Shows the color picker dialog
     */
    private void showColorPickerDialog() {
        FragmentManager fragmentManager = getParentFragmentManager();
        @ColorInt int currentColor = mSelectedColor;

        ColorPickerDialog colorPickerDialogFragment = ColorPickerDialog.newInstance(
            R.string.color_picker_default_title,
            getAccountColorOptions(),
            currentColor, 4, ColorPickerDialog.SIZE_LARGE);
        fragmentManager.setFragmentResultListener(COLOR_PICKER_DIALOG_TAG, this, this);
        colorPickerDialogFragment.show(fragmentManager, COLOR_PICKER_DIALOG_TAG);
    }

    @Override
    public void onFragmentResult(@NonNull String requestKey, @NonNull Bundle result) {
        if (COLOR_PICKER_DIALOG_TAG.equals(requestKey)) {
            @ColorInt int color = result.getInt(ColorPickerDialog.EXTRA_COLOR);
            FragmentAccountFormBinding binding = mBinding;
            if (binding != null) {
                binding.inputColorPicker.setBackgroundTintList(ColorStateList.valueOf(color));
            }
            mSelectedColor = color;
        }
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
                saveAccount();
                return true;

            case android.R.id.home:
                finishFragment();
                return true;
        }

        return super.onOptionsItemSelected(item);
    }

    /**
     * Initializes the default transfer account spinner with eligible accounts
     */
    private void loadDefaultTransferAccountList(@NonNull FragmentAccountFormBinding binding, @Nullable Account account) {
        String condition = DatabaseSchema.AccountEntry.COLUMN_UID + " != ?"
            + " AND " + DatabaseSchema.AccountEntry.COLUMN_PLACEHOLDER + " = 0"
            + " AND " + DatabaseSchema.AccountEntry.COLUMN_TYPE + " != ?"
            + " AND " + DatabaseSchema.AccountEntry.COLUMN_TEMPLATE + " = 0";

        final Context context = binding.getRoot().getContext();
        String accountUID = (account == null) ? "" : account.getUID();
        defaultAccountNameAdapter = QualifiedAccountNameAdapter.where(
            context,
            condition,
            new String[]{accountUID, AccountType.ROOT.name()}
        );
        binding.inputDefaultTransferAccount.setAdapter(defaultAccountNameAdapter);
        setDefaultTransferAccountInputsVisible(binding, mUseDoubleEntry && (defaultAccountNameAdapter.getCount() > 0));
    }

    /**
     * Loads the list of possible accounts which can be set as a parent account and initializes the spinner.
     * The allowed parent accounts depends on the account type
     *
     * @param accountType AccountType of account whose allowed parent list is to be loaded
     */
    private void loadParentAccountList(@NonNull FragmentAccountFormBinding binding, AccountType accountType) {
        String condition = DatabaseSchema.SplitEntry.COLUMN_TYPE + " IN (" + getAllowedParentAccountTypes(accountType) + ")"
            + " AND " + DatabaseSchema.AccountEntry.COLUMN_TEMPLATE + " = 0";

        Account account = mAccount;
        if (account != null) {  //if editing an account
            // limit cyclic account hierarchies.
            if (descendantAccountUIDs.isEmpty()) {
                condition += " AND (" + DatabaseSchema.AccountEntry.COLUMN_UID + " NOT IN ( '" + account.getUID() + "' ) )";
            } else {
                condition += " AND (" + DatabaseSchema.AccountEntry.COLUMN_UID + " NOT IN ( '"
                    + TextUtils.join("','", descendantAccountUIDs) + "','" + account.getUID() + "' ) )";
            }
        }

        parentAccountNameAdapter = QualifiedAccountNameAdapter.where(binding.getRoot().getContext(), condition);
        binding.inputParentAccount.setAdapter(parentAccountNameAdapter);

        if (parentAccountNameAdapter.getCount() <= 0) {
            binding.checkboxParentAccount.setChecked(false); //disable before hiding, else we can still read it when saving
            binding.checkboxParentAccount.setVisibility(View.GONE);
            binding.inputParentAccount.setVisibility(View.GONE);
        } else {
            binding.checkboxParentAccount.setVisibility(View.VISIBLE);
            binding.inputParentAccount.setVisibility(View.VISIBLE);
        }
    }

    /**
     * Returns a comma separated list of account types which can be parent accounts for the specified <code>type</code>.
     * The strings in the list are the {@link org.gnucash.android.model.AccountType#name()}s of the different types.
     *
     * @param type {@link org.gnucash.android.model.AccountType}
     * @return String comma separated list of account types
     */
    private String getAllowedParentAccountTypes(AccountType type) {
        switch (type) {
            case EQUITY:
                return "'" + AccountType.EQUITY.name() + "'";

            case INCOME:
            case EXPENSE:
                return "'" + AccountType.EXPENSE.name() + "', '" + AccountType.INCOME.name() + "'";

            case CASH:
            case BANK:
            case CREDIT:
            case ASSET:
            case LIABILITY:
            case PAYABLE:
            case RECEIVABLE:
            case CURRENCY:
            case STOCK:
            case MUTUAL: {
                List<String> accountTypeStrings = getAccountTypeStringList();
                accountTypeStrings.remove(AccountType.EQUITY.name());
                accountTypeStrings.remove(AccountType.EXPENSE.name());
                accountTypeStrings.remove(AccountType.INCOME.name());
                accountTypeStrings.remove(AccountType.ROOT.name());
                return "'" + TextUtils.join("','", accountTypeStrings) + "'";
            }

            case TRADING:
                return "'" + AccountType.TRADING.name() + "'";

            case ROOT:
            default: {
                List<String> accountTypeStrings = getAccountTypeStringList();
                return "'" + TextUtils.join("','", accountTypeStrings) + "'";
            }
        }
    }

    /**
     * Returns a list of all the available {@link org.gnucash.android.model.AccountType}s as strings
     *
     * @return String list of all account types
     */
    private List<String> getAccountTypeStringList() {
        List<String> accountTypesList = new ArrayList<>();
        for (AccountType accountType : AccountType.values()) {
            accountTypesList.add(accountType.name());
        }
        return accountTypesList;
    }

    /**
     * Finishes the fragment appropriately.
     * Depends on how the fragment was loaded, it might have a backstack or not
     */
    private void finishFragment() {
        AppCompatActivity activity = (AppCompatActivity) getActivity();
        if (activity == null) {
            Timber.w("Activity required");
            return;
        }
        FragmentAccountFormBinding binding = mBinding;
        if (binding != null) {
            InputMethodManager imm = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(binding.getRoot().getWindowToken(), 0);
        }

        final String action = activity.getIntent().getAction();
        if (action != null && action.equals(Intent.ACTION_INSERT_OR_EDIT)) {
            activity.setResult(Activity.RESULT_OK);
            activity.finish();
        } else {
            activity.getSupportFragmentManager().popBackStack();
        }
    }

    /**
     * Reads the fields from the account form and saves as a new account
     */
    private void saveAccount() {
        Timber.i("Saving account");
        @NonNull FragmentAccountFormBinding binding = mBinding;
        if (binding == null) return;

        // accounts to update, in case we're updating full names of a sub account tree
        String newName = selectedName.trim();
        if (TextUtils.isEmpty(newName)) {
            binding.nameTextInputLayout.setError(getString(R.string.toast_no_account_name_entered));
            return;
        }
        binding.nameTextInputLayout.setError(null);

        Account account = mAccount;
        if (account == null) {
            account = new Account(newName, selectedCommodity);
            //new account, insert it
            mAccountsDbAdapter.addRecord(account, DatabaseAdapter.UpdateMethod.insert);
        } else {
            account.setName(newName);
            account.setCommodity(selectedCommodity);
        }

        account.setAccountType(selectedAccountType);
        account.setDescription(binding.inputAccountDescription.getText().toString().trim());
        account.setNote(binding.notes.getText().toString().trim());
        account.setPlaceholder(binding.placeholderStatus.isChecked());
        account.setFavorite(binding.favoriteStatus.isChecked());
        account.setHidden(binding.hiddenStatus.isChecked());
        account.setColor(mSelectedColor);

        final String newParentAccountUID;
        if (binding.checkboxParentAccount.isChecked()) {
            newParentAccountUID = TextUtils.isEmpty(selectedParentAccountUID) ? mRootAccountUID : selectedParentAccountUID;
        } else {
            //need to do this explicitly in case user removes parent account
            newParentAccountUID = mRootAccountUID;
        }

        List<Account> accountsToUpdate = new ArrayList<>();
        accountsToUpdate.add(account);
        // update full names?
        if (!newParentAccountUID.equals(account.getParentUID())) {
            accountsToUpdate.addAll(descendantAccounts);
        }
        account.setParentUID(newParentAccountUID);

        if (binding.checkboxDefaultTransferAccount.isChecked()
            && binding.inputDefaultTransferAccount.getSelectedItemPosition() != Spinner.INVALID_POSITION) {
            String transferUID = (selectedDefaultTransferAccount != null) ? selectedDefaultTransferAccount.getUID() : null;
            account.setDefaultTransferAccountUID(transferUID);
        } else {
            //explicitly set in case of removal of default account
            account.setDefaultTransferAccountUID(null);
        }

        // bulk update, will not update transactions
        mAccountsDbAdapter.bulkAddRecords(accountsToUpdate, DatabaseAdapter.UpdateMethod.update);

        finishFragment();
    }
}
