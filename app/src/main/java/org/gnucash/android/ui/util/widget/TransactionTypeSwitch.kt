/*
 * Copyright (c) 2014 Ngewi Fet <ngewif@gmail.com>
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

package org.gnucash.android.ui.util.widget;

import static java.lang.Math.max;
import static java.lang.Math.round;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.widget.CompoundButton;
import android.widget.TextView;

import androidx.appcompat.widget.DrawableUtils;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.content.ContextCompat;

import org.gnucash.android.R;
import org.gnucash.android.model.AccountType;
import org.gnucash.android.model.TransactionType;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * A special type of {@link android.widget.ToggleButton} which displays the appropriate CREDIT/DEBIT labels for the
 * different account types.
 *
 * @author Ngewi Fet <ngewif@gmail.com>
 */
public class TransactionTypeSwitch extends SwitchCompat {
    private AccountType mAccountType = AccountType.ROOT;
    private String textCredit;
    private String textDebit;

    private final List<OnCheckedChangeListener> mOnCheckedChangeListeners = new ArrayList<>();

    private int textWidthMax;
    private final Rect tempRect = new Rect();

    public TransactionTypeSwitch(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init();
    }

    public TransactionTypeSwitch(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public TransactionTypeSwitch(Context context) {
        super(context);
        init();
    }

    private void init() {
        setAccountType(AccountType.BANK);

        // Force red/green colors.
        final boolean isChecked = isChecked();
        post(new Runnable() {
            @Override
            public void run() {
                setChecked(!isChecked);
                setChecked(isChecked);

            }
        });
    }

    public void setAccountType(AccountType accountType) {
        this.mAccountType = accountType;
        final boolean hasDebitBalance = accountType.hasDebitNormalBalance;
        final Context context = getContext();
        final String textDebit;
        final String textCredit;
        switch (accountType) {
            case BANK:
                textDebit = context.getString(R.string.label_deposit);
                textCredit = context.getString(R.string.label_withdrawal);
                break;
            case CASH:
                textDebit = context.getString(R.string.label_receive);
                textCredit = context.getString(R.string.label_spend);
                break;
            case CREDIT:
                textDebit = context.getString(R.string.label_payment);
                textCredit = context.getString(R.string.label_charge);
                break;
            case ASSET:
                textDebit = context.getString(R.string.label_increase);
                textCredit = context.getString(R.string.label_decrease);
                break;
            case LIABILITY:
            case TRADING:
            case EQUITY:
                textDebit = context.getString(R.string.label_decrease);
                textCredit = context.getString(R.string.label_increase);
                break;
            case STOCK:
            case MUTUAL:
            case CURRENCY:
                textDebit = context.getString(R.string.label_buy);
                textCredit = context.getString(R.string.label_sell);
                break;
            case INCOME:
                textDebit = context.getString(R.string.label_charge);
                textCredit = context.getString(R.string.label_income);
                break;
            case EXPENSE:
                textDebit = context.getString(R.string.label_expense);
                textCredit = context.getString(R.string.label_rebate);
                break;
            case PAYABLE:
                textDebit = context.getString(R.string.label_payment);
                textCredit = context.getString(R.string.label_bill);
                break;
            case RECEIVABLE:
                textDebit = context.getString(R.string.label_invoice);
                textCredit = context.getString(R.string.label_payment);
                break;
            case ROOT:
            default:
                textDebit = context.getString(R.string.label_debit);
                textCredit = context.getString(R.string.label_credit);
                break;
        }

        this.textCredit = textCredit;
        this.textDebit = textDebit;
        final String textOff = hasDebitBalance ? textDebit : textCredit;
        final String textOn = hasDebitBalance ? textCredit : textDebit;
        setTextOff(textOff);
        setTextOn(textOn);
        setText(isChecked() ? textOn : textOff);

        TextPaint paint = getPaint();
        float widthOn = paint.measureText(textOn);
        float widthOff = paint.measureText(textOff);
        textWidthMax = round(max(widthOn, widthOff));
    }

    public String getTextCredit() {
        return textCredit;
    }

    public String getTextDebit() {
        return textDebit;
    }

    @Override
    public void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (textWidthMax > 0) {
            final Rect padding = tempRect;
            final int thumbWidth;
            int paddingLeft = 0;
            int paddingRight = 0;
            Drawable thumbDrawable = getThumbDrawable();
            if (thumbDrawable != null) {
                // Cached thumb width does not include padding.
                thumbDrawable.getPadding(padding);
                thumbWidth = thumbDrawable.getIntrinsicWidth() - padding.left - padding.right;
                // Adjust left and right padding to ensure there's enough room for the
                // thumb's padding (when present).
                @SuppressLint("RestrictedApi") final Rect inset = DrawableUtils.getOpticalBounds(thumbDrawable);
                paddingLeft = Math.max(padding.left, inset.left);
                paddingRight = Math.max(padding.right, inset.right);
            } else {
                thumbWidth = 0;
            }
            final int switchWidth = Math.max(getSwitchMinWidth(), 2 * thumbWidth + paddingLeft + paddingRight);
            int width = getPaddingStart() + textWidthMax + switchWidth + getPaddingEnd();
            widthMeasureSpec = MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY);
        }

        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    /**
     * Set a checked change listener to monitor the amount view and currency views and update the display (color & balance accordingly)
     *
     * @param amountView       Amount string {@link android.widget.EditText}
     * @param currencyTextView Currency symbol text view
     */
    public void setAmountFormattingListener(CalculatorEditText amountView, TextView currencyTextView) {
        setOnCheckedChangeListener(new OnTypeChangedListener(amountView, currencyTextView));
    }

    /**
     * Add listeners to be notified when the checked status changes
     *
     * @param checkedChangeListener Checked change listener
     */
    public void addOnCheckedChangeListener(OnCheckedChangeListener checkedChangeListener) {
        mOnCheckedChangeListeners.add(checkedChangeListener);
    }

    /**
     * Toggles the button checked based on the movement caused by the transaction type for the specified account
     *
     * @param transactionType {@link org.gnucash.android.model.TransactionType} of the split
     */
    public void setChecked(final TransactionType transactionType) {
        post(new Runnable() {
            @Override
            public void run() {
                setChecked(shouldDecreaseBalance(mAccountType, transactionType));
            }
        });
    }

    /**
     * Returns the account type associated with this button
     *
     * @return Type of account
     */
    public AccountType getAccountType() {
        return mAccountType;
    }

    public TransactionType getTransactionType() {
        if (isChecked()) {
            return mAccountType.hasDebitNormalBalance ? TransactionType.CREDIT : TransactionType.DEBIT;
        } else {
            return mAccountType.hasDebitNormalBalance ? TransactionType.DEBIT : TransactionType.CREDIT;
        }
    }

    /**
     * Is the transaction type represents a decrease for the account balance for the `accountType`?
     *
     * @return true if the amount represents a decrease in the account balance, false otherwise
     */
    private boolean shouldDecreaseBalance(AccountType accountType, TransactionType transactionType) {
        return (accountType.hasDebitNormalBalance) ? transactionType == TransactionType.CREDIT : transactionType == TransactionType.DEBIT;
    }

    private class OnTypeChangedListener implements OnCheckedChangeListener {
        private final CalculatorEditText mAmountEditText;
        private final TextView mCurrencyTextView;

        /**
         * Constructor with the amount view
         *
         * @param amountEditText   EditText displaying the amount value
         * @param currencyTextView Currency symbol text view
         */
        public OnTypeChangedListener(CalculatorEditText amountEditText, TextView currencyTextView) {
            this.mAmountEditText = amountEditText;
            this.mCurrencyTextView = currencyTextView;
        }

        @Override
        public void onCheckedChanged(CompoundButton compoundButton, boolean isChecked) {
            setText(isChecked ? getTextOn() : getTextOff());
            if (isChecked) {
                int red = ContextCompat.getColor(getContext(), R.color.debit_red);
                TransactionTypeSwitch.this.setTextColor(red);
                mAmountEditText.setTextColor(red);
                mCurrencyTextView.setTextColor(red);
            } else {
                int green = ContextCompat.getColor(getContext(), R.color.credit_green);
                TransactionTypeSwitch.this.setTextColor(green);
                mAmountEditText.setTextColor(green);
                mCurrencyTextView.setTextColor(green);
            }
            BigDecimal amount = mAmountEditText.getValue();
            if (amount != null) {
                if ((isChecked && amount.signum() > 0) //we switched to debit but the amount is +ve
                    || (!isChecked && amount.signum() < 0)) { //credit but amount is -ve
                    mAmountEditText.setValue(amount.negate());
                }
            }

            for (OnCheckedChangeListener listener : mOnCheckedChangeListeners) {
                listener.onCheckedChanged(compoundButton, isChecked);
            }
        }
    }
}
