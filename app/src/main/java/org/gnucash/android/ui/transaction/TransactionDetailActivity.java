package org.gnucash.android.ui.transaction;

import android.app.Activity;
import android.content.Intent;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;

import org.gnucash.android.R;
import org.gnucash.android.app.GnuCashApplication;
import org.gnucash.android.databinding.ActivityTransactionDetailBinding;
import org.gnucash.android.databinding.ItemSplitAmountInfoBinding;
import org.gnucash.android.db.adapter.AccountsDbAdapter;
import org.gnucash.android.db.adapter.ScheduledActionDbAdapter;
import org.gnucash.android.db.adapter.TransactionsDbAdapter;
import org.gnucash.android.model.Money;
import org.gnucash.android.model.ScheduledAction;
import org.gnucash.android.model.Split;
import org.gnucash.android.model.Transaction;
import org.gnucash.android.ui.common.FormActivity;
import org.gnucash.android.ui.common.UxArgument;
import org.gnucash.android.ui.passcode.PasscodeLockActivity;
import org.joda.time.format.DateTimeFormat;

import java.util.MissingFormatArgumentException;

/**
 * Activity for displaying transaction information
 *
 * @author Ngewi Fet <ngewif@gmail.com>
 */
public class TransactionDetailActivity extends PasscodeLockActivity {
    private String mTransactionUID;
    private String mAccountUID;
    private int mDetailTableRows;

    public static final int REQUEST_EDIT_TRANSACTION = 0x10;

    private ActivityTransactionDetailBinding mBinding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mBinding = ActivityTransactionDetailBinding.inflate(getLayoutInflater());
        setContentView(mBinding.getRoot());

        mTransactionUID = getIntent().getStringExtra(UxArgument.SELECTED_TRANSACTION_UID);
        mAccountUID = getIntent().getStringExtra(UxArgument.SELECTED_ACCOUNT_UID);

        if (mTransactionUID == null || mAccountUID == null) {
            throw new MissingFormatArgumentException("You must specify both the transaction and account GUID");
        }

        setSupportActionBar(mBinding.toolbar);

        ActionBar actionBar = getSupportActionBar();
        assert actionBar != null;
        actionBar.setElevation(0);
        actionBar.setHomeButtonEnabled(true);
        actionBar.setDisplayHomeAsUpEnabled(true);
        actionBar.setHomeAsUpIndicator(R.drawable.ic_close_white);
        actionBar.setDisplayShowTitleEnabled(false);

        bindViews();

        int themeColor = AccountsDbAdapter.getActiveAccountColorResource(mAccountUID);
        actionBar.setBackgroundDrawable(new ColorDrawable(themeColor));
        mBinding.toolbar.setBackgroundColor(themeColor);

        getWindow().setStatusBarColor(GnuCashApplication.darken(themeColor));

        mBinding.fabEditTransaction.setOnClickListener(v -> editTransaction());
    }

    class SplitAmountViewHolder {
        View itemView;
        ItemSplitAmountInfoBinding binding;

        public SplitAmountViewHolder(ItemSplitAmountInfoBinding binding, Split split) {
            this.binding = binding;
            itemView = binding.getRoot();

            AccountsDbAdapter accountsDbAdapter = AccountsDbAdapter.getInstance();
            binding.splitAccountName.setText(accountsDbAdapter.getAccountFullName(split.getAccountUID()));
            Money quantity = split.getFormattedQuantity();
            TextView balanceView = quantity.isNegative() ? binding.splitDebit : binding.splitCredit;
            TransactionsActivity.displayBalance(balanceView, quantity);
        }
    }

    /**
     * Reads the transaction information from the database and binds it to the views
     */
    private void bindViews() {
        TransactionsDbAdapter transactionsDbAdapter = TransactionsDbAdapter.getInstance();
        Transaction transaction = transactionsDbAdapter.getRecord(mTransactionUID);

        mBinding.trnDescription.setText(transaction.getDescription());
        mBinding.transactionAccount.setText(getString(R.string.label_inside_account_with_name, AccountsDbAdapter.getInstance().getAccountFullName(mAccountUID)));

        AccountsDbAdapter accountsDbAdapter = AccountsDbAdapter.getInstance();

        Money accountBalance = accountsDbAdapter.getAccountBalance(mAccountUID, -1, transaction.getTimeMillis());
        TextView balanceTextView = accountBalance.isNegative() ? mBinding.balanceDebit : mBinding.balanceCredit;
        TransactionsActivity.displayBalance(balanceTextView, accountBalance);

        mDetailTableRows = mBinding.fragmentTransactionDetails.getChildCount();
        boolean useDoubleEntry = GnuCashApplication.isDoubleEntryEnabled();
        LayoutInflater inflater = LayoutInflater.from(this);
        int index = 0;
        for (Split split : transaction.getSplits()) {
            if (!useDoubleEntry && split.getAccountUID().equals(
                    accountsDbAdapter.getImbalanceAccountUID(split.getValue().getCommodity()))) {
                //do now show imbalance accounts for single entry use case
                continue;
            }
            ItemSplitAmountInfoBinding binding = ItemSplitAmountInfoBinding.inflate(inflater, mBinding.fragmentTransactionDetails, false);
            SplitAmountViewHolder viewHolder = new SplitAmountViewHolder(binding, split);
            mBinding.fragmentTransactionDetails.addView(viewHolder.itemView, index++);
        }


        String timeAndDate = DateTimeFormat.fullDate().print(transaction.getTimeMillis());
        mBinding.trnTimeAndDate.setText(timeAndDate);

        if (transaction.getScheduledActionUID() != null) {
            ScheduledAction scheduledAction = ScheduledActionDbAdapter.getInstance().getRecord(transaction.getScheduledActionUID());
            mBinding.trnRecurrence.setText(scheduledAction.getRepeatString());
            mBinding.rowTrnRecurrence.setVisibility(View.VISIBLE);
        } else {
            mBinding.rowTrnRecurrence.setVisibility(View.GONE);
        }

        if (transaction.getNote() != null && !transaction.getNote().isEmpty()) {
            mBinding.trnNotes.setText(transaction.getNote());
            mBinding.rowTrnNotes.setVisibility(View.VISIBLE);
        } else {
            mBinding.rowTrnNotes.setVisibility(View.GONE);
        }

    }

    /**
     * Refreshes the transaction information
     */
    private void refresh() {
        removeSplitItemViews();
        bindViews();
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
        Intent createTransactionIntent = new Intent(this.getApplicationContext(), FormActivity.class);
        createTransactionIntent.setAction(Intent.ACTION_INSERT_OR_EDIT);
        createTransactionIntent.putExtra(UxArgument.SELECTED_ACCOUNT_UID, mAccountUID);
        createTransactionIntent.putExtra(UxArgument.SELECTED_TRANSACTION_UID, mTransactionUID);
        createTransactionIntent.putExtra(UxArgument.FORM_TYPE, FormActivity.FormType.TRANSACTION.name());
        startActivityForResult(createTransactionIntent, REQUEST_EDIT_TRANSACTION);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == Activity.RESULT_OK) {
            refresh();
        }
    }
}
