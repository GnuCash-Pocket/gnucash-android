package org.gnucash.android.ui.transaction;

import static org.gnucash.android.ui.util.TextViewExtKt.displayBalance;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.database.SQLException;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentResultListener;

import org.gnucash.android.R;
import org.gnucash.android.app.GnuCashApplication;
import org.gnucash.android.databinding.ActivityTransactionDetailBinding;
import org.gnucash.android.databinding.ItemSplitAmountInfoBinding;
import org.gnucash.android.databinding.RowBalanceBinding;
import org.gnucash.android.db.adapter.AccountsDbAdapter;
import org.gnucash.android.db.adapter.DatabaseAdapter;
import org.gnucash.android.db.adapter.ScheduledActionDbAdapter;
import org.gnucash.android.db.adapter.TransactionsDbAdapter;
import org.gnucash.android.model.Account;
import org.gnucash.android.model.Money;
import org.gnucash.android.model.ScheduledAction;
import org.gnucash.android.model.Split;
import org.gnucash.android.model.Transaction;
import org.gnucash.android.model.TransactionType;
import org.gnucash.android.ui.common.FormActivity;
import org.gnucash.android.ui.common.Refreshable;
import org.gnucash.android.ui.common.UxArgument;
import org.gnucash.android.ui.homescreen.WidgetConfigurationActivity;
import org.gnucash.android.ui.passcode.PasscodeLockActivity;
import org.gnucash.android.ui.transaction.dialog.BulkMoveDialogFragment;
import org.gnucash.android.util.BackupManager;
import org.gnucash.android.util.DateExtKt;

import java.util.MissingFormatArgumentException;

import timber.log.Timber;

/**
 * Activity for displaying transaction information
 *
 * @author Ngewi Fet <ngewif@gmail.com>
 */
public class TransactionDetailActivity extends PasscodeLockActivity implements FragmentResultListener, Refreshable {
    private String mTransactionUID;
    private String mAccountUID;

    public static final int REQUEST_EDIT_TRANSACTION = 0x10;

    private ActivityTransactionDetailBinding mBinding;
    private final TransactionsDbAdapter transactionsDbAdapter = TransactionsDbAdapter.getInstance();

    private final AccountsDbAdapter accountsDbAdapter = AccountsDbAdapter.getInstance();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mBinding = ActivityTransactionDetailBinding.inflate(getLayoutInflater());
        setContentView(mBinding.getRoot());

        mTransactionUID = getIntent().getStringExtra(UxArgument.SELECTED_TRANSACTION_UID);
        mAccountUID = getIntent().getStringExtra(UxArgument.SELECTED_ACCOUNT_UID);

        if (TextUtils.isEmpty(mTransactionUID) || TextUtils.isEmpty(mAccountUID)) {
            throw new MissingFormatArgumentException("You must specify both the transaction and account GUID");
        }

        setSupportActionBar(mBinding.toolbar);
        ActionBar actionBar = getSupportActionBar();
        assert actionBar != null;
        actionBar.setHomeButtonEnabled(true);
        actionBar.setDisplayHomeAsUpEnabled(true);
        actionBar.setDisplayShowTitleEnabled(false);

        int themeColor = accountsDbAdapter.getActiveAccountColor(this, mAccountUID);
        setTitlesColor(themeColor);
        mBinding.toolbar.setBackgroundColor(themeColor);

        bindViews();

        mBinding.fabEditTransaction.setOnClickListener(v -> editTransaction());
    }

    @Override
    public void onFragmentResult(@NonNull String requestKey, @NonNull Bundle result) {
        if (BulkMoveDialogFragment.TAG.equals(requestKey)) {
            String accountrUID = result.getString(UxArgument.SELECTED_ACCOUNT_UID);
            if (!TextUtils.isEmpty(accountrUID)) {
                mAccountUID = accountrUID;
            }
            boolean refresh = result.getBoolean(Refreshable.EXTRA_REFRESH);
            if (refresh) refresh();
        }
    }

    private void bind(ItemSplitAmountInfoBinding binding, Split split) {
        Account account = accountsDbAdapter.getSimpleRecord(split.getAccountUID());
        binding.splitAccountName.setText(account.getFullName());
        TextView balanceView = split.getType() == TransactionType.DEBIT ? binding.splitDebit : binding.splitCredit;
        @ColorInt int colorBalanceZero = balanceView.getCurrentTextColor();
        displayBalance(balanceView, split.getFormattedQuantity(account), colorBalanceZero);
    }

    private void bind(RowBalanceBinding binding, String accountUID, long timeMillis) {
        Account account = accountsDbAdapter.getSimpleRecord(accountUID);
        Money accountBalance = accountsDbAdapter.getAccountBalance(accountUID, -1, timeMillis, true);
        TextView balanceTextView = account.getAccountType().hasDebitDisplayBalance ? binding.balanceDebit : binding.balanceCredit;
        displayBalance(balanceTextView, accountBalance, balanceTextView.getCurrentTextColor());
    }

    /**
     * Reads the transaction information from the database and binds it to the views
     */
    private void bindViews() {
        ActivityTransactionDetailBinding binding = mBinding;
        if (binding == null) return;
        // Remove all rows that are not special.
        binding.transactionItems.removeAllViews();

        Transaction transaction = transactionsDbAdapter.getRecord(mTransactionUID);

        binding.trnDescription.setText(transaction.getDescription());
        binding.transactionAccount.setText(getString(R.string.label_inside_account_with_name, accountsDbAdapter.getAccountFullName(mAccountUID)));

        boolean useDoubleEntry = GnuCashApplication.isDoubleEntryEnabled();
        Context context = this;
        LayoutInflater inflater = LayoutInflater.from(context);
        for (Split split : transaction.getSplits()) {
            if (!useDoubleEntry && split.getAccountUID().equals(
                accountsDbAdapter.getImbalanceAccountUID(context, split.getValue().getCommodity()))) {
                //do now show imbalance accounts for single entry use case
                continue;
            }
            ItemSplitAmountInfoBinding splitBinding = ItemSplitAmountInfoBinding.inflate(inflater, binding.transactionItems, true);
            bind(splitBinding, split);
        }

        RowBalanceBinding balanceBinding = RowBalanceBinding.inflate(inflater, binding.transactionItems, true);
        bind(balanceBinding, mAccountUID, transaction.getTimeMillis());

        String timeAndDate = DateExtKt.formatFullDate(transaction.getTimeMillis());
        binding.trnTimeAndDate.setText(timeAndDate);

        if (!TextUtils.isEmpty(transaction.getNote())) {
            binding.notes.setText(transaction.getNote());
            binding.rowTrnNotes.setVisibility(View.VISIBLE);
        } else {
            binding.rowTrnNotes.setVisibility(View.GONE);
        }

        if (transaction.getScheduledActionUID() != null) {
            ScheduledAction scheduledAction = ScheduledActionDbAdapter.getInstance().getRecord(transaction.getScheduledActionUID());
            binding.trnRecurrence.setText(scheduledAction.getRepeatString(context));
            binding.rowTrnRecurrence.setVisibility(View.VISIBLE);
        } else {
            binding.rowTrnRecurrence.setVisibility(View.GONE);
        }
    }

    @Override
    public void refresh() {
        bindViews();
    }

    @Override
    public void refresh(String uid) {
        mTransactionUID = uid;
        refresh();
    }

    private void editTransaction() {
        Intent intent = new Intent(this, FormActivity.class)
            .setAction(Intent.ACTION_INSERT_OR_EDIT)
            .putExtra(UxArgument.SELECTED_ACCOUNT_UID, mAccountUID)
            .putExtra(UxArgument.SELECTED_TRANSACTION_UID, mTransactionUID)
            .putExtra(UxArgument.FORM_TYPE, FormActivity.FormType.TRANSACTION.name());
        startActivityForResult(intent, REQUEST_EDIT_TRANSACTION);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.transactions_context_menu, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                return true;
            case R.id.menu_move:
                moveTransaction(mTransactionUID);
                return true;
            case R.id.menu_duplicate:
                duplicateTransaction(mTransactionUID);
                return true;
            case R.id.menu_delete:
                deleteTransaction(mTransactionUID);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == Activity.RESULT_OK) {
            refresh();
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    private void moveTransaction(@Nullable String transactionUID) {
        if (TextUtils.isEmpty(transactionUID)) return;
        String[] uids = new String[]{transactionUID};
        BulkMoveDialogFragment fragment = BulkMoveDialogFragment.newInstance(uids, mAccountUID);
        FragmentManager fm = getSupportFragmentManager();
        fm.setFragmentResultListener(BulkMoveDialogFragment.TAG, this, this);
        fragment.show(fm, BulkMoveDialogFragment.TAG);
    }

    private void deleteTransaction(@Nullable final String transactionUID) {
        if (TextUtils.isEmpty(transactionUID)) return;

        final Activity activity = this;
        if (GnuCashApplication.shouldBackupTransactions(activity)) {
            BackupManager.backupActiveBookAsync(activity, result -> {
                transactionsDbAdapter.deleteRecord(transactionUID);
                WidgetConfigurationActivity.updateAllWidgets(activity);
                finish();
                return null;
            });
        } else {
            transactionsDbAdapter.deleteRecord(transactionUID);
            WidgetConfigurationActivity.updateAllWidgets(activity);
            finish();
        }
    }

    private void duplicateTransaction(@Nullable String transactionUID) {
        if (TextUtils.isEmpty(transactionUID)) return;

        Transaction transaction = transactionsDbAdapter.getRecord(transactionUID);
        Transaction duplicate = new Transaction(transaction, true);
        duplicate.setTime(System.currentTimeMillis());
        try {
            transactionsDbAdapter.addRecord(duplicate, DatabaseAdapter.UpdateMethod.insert);
            if (duplicate.id <= 0) return;
        } catch (SQLException e) {
            Timber.e(e);
            return;
        }

        // Show the new transaction
        Intent intent = new Intent(getIntent())
            .putExtra(UxArgument.SELECTED_TRANSACTION_UID, duplicate.getUID())
            .putExtra(UxArgument.SELECTED_ACCOUNT_UID, mAccountUID);
        startActivity(intent);
    }
}
