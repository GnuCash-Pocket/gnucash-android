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
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.database.Cursor;
import android.graphics.Color;
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
import android.widget.ArrayAdapter;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.Spinner;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.cursoradapter.widget.SimpleCursorAdapter;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentResultListener;

import org.gnucash.android.R;
import org.gnucash.android.app.GnuCashApplication;
import org.gnucash.android.app.MenuFragment;
import org.gnucash.android.databinding.FragmentAccountFormBinding;
import org.gnucash.android.db.DatabaseSchema;
import org.gnucash.android.db.adapter.AccountsDbAdapter;
import org.gnucash.android.db.adapter.CommoditiesDbAdapter;
import org.gnucash.android.db.adapter.DatabaseAdapter;
import org.gnucash.android.model.Account;
import org.gnucash.android.model.AccountType;
import org.gnucash.android.model.Commodity;
import org.gnucash.android.ui.colorpicker.ColorPickerDialog;
import org.gnucash.android.ui.common.UxArgument;
import org.gnucash.android.util.CommoditiesCursorAdapter;
import org.gnucash.android.util.QualifiedAccountNameCursorAdapter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    private final AccountsDbAdapter mAccountsDbAdapter = AccountsDbAdapter.getInstance();
    private final CommoditiesDbAdapter commodityDbAdapter = CommoditiesDbAdapter.getInstance();

    /**
     * GUID of the parent account
     * This value is set to the parent account of the transaction being edited or
     * the account in which a new sub-account is being created
     */
    private String mParentAccountUID = null;

    /**
     * Account ID of the root account
     */
    private long mRootAccountId = -1;

    /**
     * Account UID of the root account
     */
    private String mRootAccountUID = null;

    /**
     * Reference to account object which will be created at end of dialog
     */
    private Account mAccount = null;

    /**
     * Unique ID string of account being edited
     */
    private String mAccountUID = null;

    /**
     * Cursor which will hold set of eligible parent accounts
     */
    private Cursor mParentAccountCursor;

    /**
     * List of all descendant Account UIDs, if we are modifying an account
     * null if creating a new account
     */
    private List<String> mDescendantAccountUIDs;

    /**
     * SimpleCursorAdapter for the parent account spinner
     *
     * @see QualifiedAccountNameCursorAdapter
     */
    private SimpleCursorAdapter mParentAccountCursorAdapter;

    /**
     * Cursor adapter which binds to the spinner for default transfer account
     */
    private SimpleCursorAdapter mDefaultTransferAccountCursorAdapter;

    /**
     * Flag indicating if double entry transactions are enabled
     */
    private boolean mUseDoubleEntry;

    private int mSelectedColor = Account.DEFAULT_COLOR;

    private FragmentAccountFormBinding mBinding;

    /**
     * Default constructor
     * Required, else the app crashes on screen rotation
     */
    public AccountFormFragment() {
        //nothing to see here, move along
    }

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
        mUseDoubleEntry = GnuCashApplication.isDoubleEntryEnabled();
        mAccountUID = getArguments().getString(UxArgument.SELECTED_ACCOUNT_UID);
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

        mBinding.inputAccountName.addTextChangedListener(new TextWatcher() {
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
                    mBinding.nameTextInputLayout.setErrorEnabled(false);
                }
            }
        });

        mBinding.inputAccountTypeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int position, long id) {
                loadParentAccountList(getSelectedAccountType());
                if (mParentAccountUID != null)
                    setParentAccountSelection(mAccountsDbAdapter.getID(mParentAccountUID));
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {
                //nothing to see here, move along
            }
        });

        mBinding.inputParentAccount.setEnabled(false);

        mBinding.checkboxParentAccount.setOnCheckedChangeListener(new OnCheckedChangeListener() {

            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                mBinding.inputParentAccount.setEnabled(isChecked);
            }
        });

        mBinding.inputDefaultTransferAccount.setEnabled(false);
        mBinding.checkboxDefaultTransferAccount.setOnCheckedChangeListener(new OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean isChecked) {
                mBinding.inputDefaultTransferAccount.setEnabled(isChecked);
            }
        });

        mBinding.inputColorPicker.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showColorPickerDialog();
            }
        });

        CommoditiesCursorAdapter commoditiesAdapter = new CommoditiesCursorAdapter(
            getActivity(), android.R.layout.simple_spinner_item);
        commoditiesAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        mBinding.inputCurrencySpinner.setAdapter(commoditiesAdapter);

        ActionBar actionBar = ((AppCompatActivity) requireActivity()).getSupportActionBar();
        assert actionBar != null;
        if (mAccountUID != null) {
            mAccount = mAccountsDbAdapter.getSimpleRecord(mAccountUID);
            actionBar.setTitle(R.string.title_edit_account);
        } else {
            actionBar.setTitle(R.string.title_create_account);
        }

        mRootAccountUID = mAccountsDbAdapter.getOrCreateGnuCashRootAccountUID();
        if (mRootAccountUID != null)
            mRootAccountId = mAccountsDbAdapter.getID(mRootAccountUID);

        //need to load the cursor adapters for the spinners before initializing the views
        loadAccountTypesList();
        loadDefaultTransferAccountList();
        setDefaultTransferAccountInputsVisible(mUseDoubleEntry);

        if (mAccount != null) {
            initializeViewsWithAccount(mAccount);
            //do not immediately open the keyboard when editing an account
            getActivity().getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        } else {
            initializeViews();
        }
    }

    /**
     * Initialize view with the properties of <code>account</code>.
     * This is applicable when editing an account
     *
     * @param account Account whose fields are used to populate the form
     */
    private void initializeViewsWithAccount(Account account) {
        if (account == null)
            throw new IllegalArgumentException("Account cannot be null");

        loadParentAccountList(account.getAccountType());
        mParentAccountUID = account.getParentUID();
        if (mParentAccountUID == null) {
            // null parent, set Parent as root
            mParentAccountUID = mRootAccountUID;
        }

        if (mParentAccountUID != null) {
            setParentAccountSelection(mAccountsDbAdapter.getID(mParentAccountUID));
        }

        String currencyCode = account.getCommodity().getCurrencyCode();
        setSelectedCurrency(currencyCode);

        if (mAccountsDbAdapter.getTransactionMaxSplitNum(account.getUID()) > 1) {
            //TODO: Allow changing the currency and effecting the change for all transactions without any currency exchange (purely cosmetic change)
            mBinding.inputCurrencySpinner.setEnabled(false);
        }

        setTextToEnd(mBinding.inputAccountName, account.getName());
        mBinding.inputAccountDescription.setText(account.getDescription());

        if (mUseDoubleEntry) {
            if (account.getDefaultTransferAccountUID() != null) {
                long doubleDefaultAccountId = mAccountsDbAdapter.getID(account.getDefaultTransferAccountUID());
                setDefaultTransferAccountSelection(doubleDefaultAccountId, true);
            } else {
                String currentAccountUID = account.getParentUID();
                String rootAccountUID = mAccountsDbAdapter.getOrCreateGnuCashRootAccountUID();
                while (!currentAccountUID.equals(rootAccountUID)) {
                    long defaultTransferAccountID = mAccountsDbAdapter.getDefaultTransferAccountID(mAccountsDbAdapter.getID(currentAccountUID));
                    if (defaultTransferAccountID > 0) {
                        setDefaultTransferAccountSelection(defaultTransferAccountID, false);
                        break; //we found a parent with default transfer setting
                    }
                    currentAccountUID = mAccountsDbAdapter.getParentAccountUID(currentAccountUID);
                }
            }
        }

        mBinding.checkboxPlaceholderAccount.setChecked(account.isPlaceholderAccount());
        mBinding.favoriteStatus.setChecked(account.isFavorite());
        mSelectedColor = account.getColor();
        mBinding.inputColorPicker.setBackgroundColor(account.getColor());

        setAccountTypeSelection(account.getAccountType());
    }

    /**
     * Initialize views with defaults for new account
     */
    private void initializeViews() {
        setSelectedCurrency(Commodity.DEFAULT_COMMODITY.getCurrencyCode());
        mBinding.inputColorPicker.setBackgroundColor(Color.LTGRAY);
        mParentAccountUID = getArguments().getString(UxArgument.PARENT_ACCOUNT_UID);

        if (mParentAccountUID != null) {
            AccountType parentAccountType = mAccountsDbAdapter.getAccountType(mParentAccountUID);
            setAccountTypeSelection(parentAccountType);
            loadParentAccountList(parentAccountType);
            setParentAccountSelection(mAccountsDbAdapter.getID(mParentAccountUID));
        }

    }

    /**
     * Selects the corresponding account type in the spinner
     *
     * @param accountType AccountType to be set
     */
    private void setAccountTypeSelection(AccountType accountType) {
        String[] accountTypeEntries = getResources().getStringArray(R.array.key_account_type_entries);
        int accountTypeIndex = Arrays.asList(accountTypeEntries).indexOf(accountType.name());
        mBinding.inputAccountTypeSpinner.setSelection(accountTypeIndex);
    }

    /**
     * Toggles the visibility of the default transfer account input fields.
     * This field is irrelevant for users who do not use double accounting
     */
    private void setDefaultTransferAccountInputsVisible(boolean visible) {
        final int visibility = visible ? View.VISIBLE : View.GONE;
        final View view = getView();
        assert view != null;
        view.findViewById(R.id.layout_default_transfer_account).setVisibility(visibility);
        view.findViewById(R.id.label_default_transfer_account).setVisibility(visibility);
    }

    /**
     * Selects the currency with code <code>currencyCode</code> in the spinner
     *
     * @param currencyCode ISO 4217 currency code to be selected
     */
    private void setSelectedCurrency(String currencyCode) {
        long commodityId = commodityDbAdapter.getID(commodityDbAdapter.getCommodityUID(currencyCode));
        int position = 0;
        for (int i = 0; i < mBinding.inputCurrencySpinner.getCount(); i++) {
            if (commodityId == mBinding.inputCurrencySpinner.getItemIdAtPosition(i)) {
                position = i;
            }
        }
        mBinding.inputCurrencySpinner.setSelection(position);
    }

    /**
     * Selects the account with ID <code>parentAccountId</code> in the parent accounts spinner
     *
     * @param parentAccountId Record ID of parent account to be selected
     */
    private void setParentAccountSelection(long parentAccountId) {
        if (parentAccountId <= 0 || parentAccountId == mRootAccountId) {
            return;
        }

        for (int pos = 0; pos < mParentAccountCursorAdapter.getCount(); pos++) {
            if (mParentAccountCursorAdapter.getItemId(pos) == parentAccountId) {
                mBinding.checkboxParentAccount.setChecked(true);
                mBinding.inputParentAccount.setEnabled(true);
                mBinding.inputParentAccount.setSelection(pos, true);
                break;
            }
        }
    }

    /**
     * Selects the account with ID <code>parentAccountId</code> in the default transfer account spinner
     *
     * @param defaultTransferAccountId Record ID of parent account to be selected
     */
    private void setDefaultTransferAccountSelection(long defaultTransferAccountId, boolean enableTransferAccount) {
        if (defaultTransferAccountId > 0) {
            mBinding.checkboxDefaultTransferAccount.setChecked(enableTransferAccount);
            mBinding.inputDefaultTransferAccount.setEnabled(enableTransferAccount);
        } else
            return;

        for (int pos = 0; pos < mDefaultTransferAccountCursorAdapter.getCount(); pos++) {
            if (mDefaultTransferAccountCursorAdapter.getItemId(pos) == defaultTransferAccountId) {
                mBinding.inputDefaultTransferAccount.setSelection(pos);
                break;
            }
        }
    }

    /**
     * Returns an array of colors used for accounts.
     * The array returned has the actual color values and not the resource ID.
     *
     * @return Integer array of colors used for accounts
     */
    private int[] getAccountColorOptions() {
        Resources res = getResources();
        int colorDefault = ContextCompat.getColor(requireContext(), R.color.title_green);
        TypedArray colorTypedArray = res.obtainTypedArray(R.array.account_colors);
        int colorLength = colorTypedArray.length();
        int[] colorOptions = new int[colorLength];
        for (int i = 0; i < colorLength; i++) {
            colorOptions[i] = colorTypedArray.getColor(i, colorDefault);
        }
        colorTypedArray.recycle();
        return colorOptions;
    }

    /**
     * Shows the color picker dialog
     */
    private void showColorPickerDialog() {
        FragmentManager fragmentManager = getActivity().getSupportFragmentManager();
        int currentColor = Color.LTGRAY;
        if (mAccount != null) {
            currentColor = mAccount.getColor();
        }

        ColorPickerDialog colorPickerDialogFragment = ColorPickerDialog.newInstance(
            R.string.color_picker_default_title,
            getAccountColorOptions(),
            currentColor, 4, 12);
        fragmentManager.setFragmentResultListener(COLOR_PICKER_DIALOG_TAG, this, this);
        colorPickerDialogFragment.show(fragmentManager, COLOR_PICKER_DIALOG_TAG);
    }

    @Override
    public void onFragmentResult(@NonNull String requestKey, @NonNull Bundle result) {
        if (COLOR_PICKER_DIALOG_TAG.equals(requestKey)) {
            int color = result.getInt(ColorPickerDialog.EXTRA_COLOR);
            mBinding.inputColorPicker.setBackgroundColor(color);
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
    private void loadDefaultTransferAccountList() {
        String condition = DatabaseSchema.AccountEntry.COLUMN_UID + " != '" + mAccountUID + "' " //when creating a new account mAccountUID is null, so don't use whereArgs
            + " AND " + DatabaseSchema.AccountEntry.COLUMN_PLACEHOLDER + "=0"
            + " AND " + DatabaseSchema.AccountEntry.COLUMN_HIDDEN + "=0"
            + " AND " + DatabaseSchema.AccountEntry.COLUMN_TYPE + " != ?";

        Cursor defaultTransferAccountCursor = mAccountsDbAdapter.fetchAccountsOrderedByFullName(condition,
            new String[]{AccountType.ROOT.name()});

        if (mBinding.inputDefaultTransferAccount.getCount() <= 0) {
            setDefaultTransferAccountInputsVisible(false);
        }

        mDefaultTransferAccountCursorAdapter = new QualifiedAccountNameCursorAdapter(getActivity(),
            defaultTransferAccountCursor);
        mBinding.inputDefaultTransferAccount.setAdapter(mDefaultTransferAccountCursorAdapter);
    }

    /**
     * Loads the list of possible accounts which can be set as a parent account and initializes the spinner.
     * The allowed parent accounts depends on the account type
     *
     * @param accountType AccountType of account whose allowed parent list is to be loaded
     */
    private void loadParentAccountList(AccountType accountType) {
        String condition = DatabaseSchema.SplitEntry.COLUMN_TYPE + " IN ("
            + getAllowedParentAccountTypes(accountType) + ") AND " + DatabaseSchema.AccountEntry.COLUMN_HIDDEN + "!=1 ";

        if (mAccount != null) {  //if editing an account
            mDescendantAccountUIDs = mAccountsDbAdapter.getDescendantAccountUIDs(mAccount.getUID(), null, null);
            String rootAccountUID = mAccountsDbAdapter.getOrCreateGnuCashRootAccountUID();
            List<String> descendantAccountUIDs = new ArrayList<>(mDescendantAccountUIDs);
            if (rootAccountUID != null)
                descendantAccountUIDs.add(rootAccountUID);
            // limit cyclic account hierarchies.
            condition += " AND (" + DatabaseSchema.AccountEntry.COLUMN_UID + " NOT IN ( '"
                + TextUtils.join("','", descendantAccountUIDs) + "','" + mAccountUID + "' ) )";
        }

        //if we are reloading the list, close the previous cursor first
        if (mParentAccountCursor != null)
            mParentAccountCursor.close();

        mParentAccountCursor = mAccountsDbAdapter.fetchAccountsOrderedByFullName(condition, null);
        final View view = getView();
        assert view != null;
        if (mParentAccountCursor.getCount() <= 0) {
            mBinding.checkboxParentAccount.setChecked(false); //disable before hiding, else we can still read it when saving
            view.findViewById(R.id.layout_parent_account).setVisibility(View.GONE);
            view.findViewById(R.id.label_parent_account).setVisibility(View.GONE);
        } else {
            view.findViewById(R.id.layout_parent_account).setVisibility(View.VISIBLE);
            view.findViewById(R.id.label_parent_account).setVisibility(View.VISIBLE);
        }

        mParentAccountCursorAdapter = new QualifiedAccountNameCursorAdapter(
            getActivity(), mParentAccountCursor);
        mBinding.inputParentAccount.setAdapter(mParentAccountCursorAdapter);
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
            default:
                return Arrays.toString(AccountType.values()).replaceAll("\\[|]", "");
        }
    }

    /**
     * Returns a list of all the available {@link org.gnucash.android.model.AccountType}s as strings
     *
     * @return String list of all account types
     */
    private List<String> getAccountTypeStringList() {
        String[] accountTypes = Arrays.toString(AccountType.values()).replaceAll("\\[|]", "").split(",");
        List<String> accountTypesList = new ArrayList<>();
        for (String accountType : accountTypes) {
            accountTypesList.add(accountType.trim());
        }

        return accountTypesList;
    }

    /**
     * Loads the list of account types into the account type selector spinner
     */
    private void loadAccountTypesList() {
        String[] accountTypes = getResources().getStringArray(R.array.account_type_entry_values);
        ArrayAdapter<String> accountTypesAdapter = new ArrayAdapter<>(
            getActivity(), android.R.layout.simple_spinner_item, accountTypes);

        accountTypesAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mBinding.inputAccountTypeSpinner.setAdapter(accountTypesAdapter);

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
        InputMethodManager imm = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(mBinding.getRoot().getWindowToken(), 0);

        final String action = activity.getIntent().getAction();
        if (action != null && action.equals(Intent.ACTION_INSERT_OR_EDIT)) {
            activity.setResult(Activity.RESULT_OK);
            activity.finish();
        } else {
            activity.getSupportFragmentManager().popBackStack();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mParentAccountCursor != null)
            mParentAccountCursor.close();
        if (mDefaultTransferAccountCursorAdapter != null) {
            mDefaultTransferAccountCursorAdapter.getCursor().close();
        }
    }

    /**
     * Reads the fields from the account form and saves as a new account
     */
    private void saveAccount() {
        Timber.i("Saving account");

        // accounts to update, in case we're updating full names of a sub account tree
        boolean nameChanged = false;
        String newName = getEnteredName();
        if (TextUtils.isEmpty(newName)) {
            mBinding.nameTextInputLayout.setErrorEnabled(true);
            mBinding.nameTextInputLayout.setError(getString(R.string.toast_no_account_name_entered));
            return;
        } else {
            mBinding.nameTextInputLayout.setError(null);
        }
        long commodityId = mBinding.inputCurrencySpinner.getSelectedItemId();
        Commodity commodity = commodityDbAdapter.getRecord(commodityId);
        if (mAccount == null) {
            mAccount = new Account(newName, commodity);
            mAccountsDbAdapter.addRecord(mAccount, DatabaseAdapter.UpdateMethod.insert); //new account, insert it
        } else {
            nameChanged = !mAccount.getName().equals(newName);
            mAccount.setName(newName);
            mAccount.setCommodity(commodity);
        }

        AccountType selectedAccountType = getSelectedAccountType();
        mAccount.setAccountType(selectedAccountType);

        mAccount.setDescription(mBinding.inputAccountDescription.getText().toString());
        mAccount.setPlaceHolderFlag(mBinding.checkboxPlaceholderAccount.isChecked());
        mAccount.setFavorite(mBinding.favoriteStatus.isChecked());
        mAccount.setColor(mSelectedColor);

        long newParentAccountId;
        String newParentAccountUID;
        if (mBinding.checkboxParentAccount.isChecked()) {
            newParentAccountId = mBinding.inputParentAccount.getSelectedItemId();
            newParentAccountUID = mAccountsDbAdapter.getUID(newParentAccountId);
        } else {
            //need to do this explicitly in case user removes parent account
            newParentAccountUID = mRootAccountUID;
            newParentAccountId = mRootAccountId;
        }
        mAccount.setParentUID(newParentAccountUID);

        if (mBinding.checkboxDefaultTransferAccount.isChecked()
            && mBinding.inputDefaultTransferAccount.getSelectedItemId() != Spinner.INVALID_ROW_ID) {
            long id = mBinding.inputDefaultTransferAccount.getSelectedItemId();
            mAccount.setDefaultTransferAccountUID(mAccountsDbAdapter.getUID(id));
        } else {
            //explicitly set in case of removal of default account
            mAccount.setDefaultTransferAccountUID(null);
        }

        long parentAccountId = mParentAccountUID == null ? -1 : mAccountsDbAdapter.getID(mParentAccountUID);
        // update full names
        List<Account> accountsToUpdate = new ArrayList<>();
        if (nameChanged || mDescendantAccountUIDs == null || newParentAccountId != parentAccountId) {
            // current account name changed or new Account or parent account changed
            String newAccountFullName;
            if (newParentAccountId == mRootAccountId) {
                newAccountFullName = mAccount.getName();
            } else {
                newAccountFullName = mAccountsDbAdapter.getAccountFullName(newParentAccountUID) +
                    AccountsDbAdapter.ACCOUNT_NAME_SEPARATOR + mAccount.getName();
            }
            mAccount.setFullName(newAccountFullName);
            if (mDescendantAccountUIDs != null) {
                // modifying existing account, e.g. name changed and/or parent changed
                if ((nameChanged || parentAccountId != newParentAccountId) && mDescendantAccountUIDs.size() > 0) {
                    // parent change, update all full names of descent accounts
                    accountsToUpdate.addAll(mAccountsDbAdapter.getSimpleAccountList(
                        DatabaseSchema.AccountEntry.COLUMN_UID + " IN ('" +
                            TextUtils.join("','", mDescendantAccountUIDs) + "')", null, null
                    ));
                }
                Map<String, Account> mapAccount = new HashMap<>();
                for (Account acct : accountsToUpdate) mapAccount.put(acct.getUID(), acct);
                for (String uid : mDescendantAccountUIDs) {
                    // mAccountsDbAdapter.getDescendantAccountUIDs() will ensure a parent-child order
                    Account acct = mapAccount.get(uid);
                    // mAccount cannot be root, so acct here cannot be top level account.
                    if (mAccount.getUID().equals(acct.getParentUID())) {
                        acct.setFullName(mAccount.getFullName() + AccountsDbAdapter.ACCOUNT_NAME_SEPARATOR + acct.getName());
                    } else {
                        acct.setFullName(
                            mapAccount.get(acct.getParentUID()).getFullName() +
                                AccountsDbAdapter.ACCOUNT_NAME_SEPARATOR +
                                acct.getName()
                        );
                    }
                }
            }
        }
        accountsToUpdate.add(mAccount);

        // bulk update, will not update transactions
        mAccountsDbAdapter.bulkAddRecords(accountsToUpdate, DatabaseAdapter.UpdateMethod.update);

        finishFragment();
    }

    /**
     * Returns the currently selected account type in the spinner
     *
     * @return {@link org.gnucash.android.model.AccountType} currently selected
     */
    private AccountType getSelectedAccountType() {
        int selectedAccountTypeIndex = mBinding.inputAccountTypeSpinner.getSelectedItemPosition();
        String[] accountTypeEntries = getResources().getStringArray(R.array.key_account_type_entries);
        return AccountType.valueOf(accountTypeEntries[selectedAccountTypeIndex]);
    }

    /**
     * Retrieves the name of the account which has been entered in the EditText
     *
     * @return Name of the account which has been entered in the EditText
     */
    private String getEnteredName() {
        return mBinding.inputAccountName.getText().toString().trim();
    }

}
