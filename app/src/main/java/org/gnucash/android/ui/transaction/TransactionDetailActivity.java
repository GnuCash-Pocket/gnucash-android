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
import org.gnucash.android.db.adapter.AccountsDbAdapter;
import org.gnucash.android.db.adapter.DatabaseAdapter;
import org.gnucash.android.db.adapter.ScheduledActionDbAdapter;
import org.gnucash.android.db.adapter.TransactionsDbAdapter;
import org.gnucash.android.model.Money;
import org.gnucash.android.model.ScheduledAction;
import org.gnucash.android.model.Split;
import org.gnucash.android.model.Transaction;
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
    private int mDetailTableRows;

    public static final int REQUEST_EDIT_TRANSACTION = 0x10;

    private ActivityTransactionDetailBinding mBinding;
    @ColorInt
    private int colorBalanceZero;
    private final TransactionsDbAdapter transactionsDbAdapter = TransactionsDbAdapter.getInstance();

    private final AccountsDbAdapter accountsDbAdapter = AccountsDbAdapter.getInstance();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mBinding = ActivityTransactionDetailBinding.inflate(getLayoutInflater());
        colorBalanceZero = mBinding.balanceCredit.getCurrentTextColor();
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

    class SplitAmountViewHolder {
        private final View itemView;
        @ColorInt
        private final int colorBalanceZero;

        public SplitAmountViewHolder(ItemSplitAmountInfoBinding binding, Split split) {
            itemView = binding.getRoot();

            binding.splitAccountName.setText(accountsDbAdapter.getAccountFullName(split.getAccountUID()));
            Money quantity = split.getFormattedQuantity();
            TextView balanceView = quantity.isNegative() ? binding.splitDebit : binding.splitCredit;
            colorBalanceZero = balanceView.getCurrentTextColor();
            displayBalance(balanceView, quantity, colorBalanceZero);
        }
    }

    /**
     * Reads the transaction information from the database and binds it to the views
     */
    private void bindViews() {
        Transaction transaction = transactionsDbAdapter.getRecord(mTransactionUID);

        mBinding.trnDescription.setText(transaction.getDescription());
        mBinding.transactionAccount.setText(getString(R.string.label_inside_account_with_name, accountsDbAdapter.getAccountFullName(mAccountUID)));

        Money accountBalance = accountsDbAdapter.getAccountBalance(mAccountUID, -1, transaction.getTimeMillis(), false);
        TextView balanceTextView = accountBalance.isNegative() ? mBinding.balanceDebit : mBinding.balanceCredit;
        displayBalance(balanceTextView, accountBalance, colorBalanceZero);

        mDetailTableRows = mBinding.fragmentTransactionDetails.getChildCount();
        boolean useDoubleEntry = GnuCashApplication.isDoubleEntryEnabled();
        Context context = this;
        LayoutInflater inflater = LayoutInflater.from(context);
        int index = 0;
        for (Split split : transaction.getSplits()) {
            if (!useDoubleEntry && split.getAccountUID().equals(
                accountsDbAdapter.getImbalanceAccountUID(context, split.getValue().getCommodity()))) {
                //do now show imbalance accounts for single entry use case
                continue;
            }
            ItemSplitAmountInfoBinding binding = ItemSplitAmountInfoBinding.inflate(inflater, mBinding.fragmentTransactionDetails, false);
            SplitAmountViewHolder viewHolder = new SplitAmountViewHolder(binding, split);
            mBinding.fragmentTransactionDetails.addView(viewHolder.itemView, index++);
        }

        String timeAndDate = DateExtKt.formatFullDate(transaction.getTimeMillis());
        mBinding.trnTimeAndDate.setText(timeAndDate);

        if (transaction.getScheduledActionUID() != null) {
            ScheduledAction scheduledAction = ScheduledActionDbAdapter.getInstance().getRecord(transaction.getScheduledActionUID());
            mBinding.trnRecurrence.setText(scheduledAction.getRepeatString(context));
            mBinding.rowTrnRecurrence.setVisibility(View.VISIBLE);
        } else {
            mBinding.rowTrnRecurrence.setVisibility(View.GONE);
        }

        if (transaction.getNote() != null && !transaction.getNote().isEmpty()) {
            mBinding.notes.setText(transaction.getNote());
            mBinding.rowTrnNotes.setVisibility(View.VISIBLE);
        } else {
            mBinding.rowTrnNotes.setVisibility(View.GONE);
        }
    }

    @Override
    public void refresh() {
        removeSplitItemViews();
        bindViews();
    }

    @Override
    public void refresh(String uid) {
        mTransactionUID = uid;
        refresh();
    }

    /**
     * Remove the split item views from the transaction detail prior to refreshing them
     */
    private void removeSplitItemViews() {
        // Remove all rows that are not special.
        mBinding.fragmentTransactionDetails.removeViews(0, mBinding.fragmentTransactionDetails.getChildCount() - mDetailTableRows);
        mBinding.balanceDebit.setText("");
        mBinding.balanceCredit.setText("");
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
