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
import org.gnucash.android.model.Transaction;
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
    private AccountType mAccountType = AccountType.EXPENSE;

    private final List<OnCheckedChangeListener> mOnCheckedChangeListeners = new ArrayList<>();

    private int textWidthMax;
    private final Rect tempRect = new Rect();

    public TransactionTypeSwitch(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public TransactionTypeSwitch(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public TransactionTypeSwitch(Context context) {
        super(context);
    }

    public void setAccountType(AccountType accountType) {
        this.mAccountType = accountType;
        Context context = getContext();
        final String textOn;
        final String textOff;
        switch (mAccountType) {
            case CASH:
                textOn = context.getString(R.string.label_spend);
                textOff = context.getString(R.string.label_receive);
                break;
            case BANK:
                textOn = context.getString(R.string.label_withdrawal);
                textOff = context.getString(R.string.label_deposit);
                break;
            case CREDIT:
                textOn = context.getString(R.string.label_payment);
                textOff = context.getString(R.string.label_charge);
                break;
            case ASSET:
            case EQUITY:
            case LIABILITY:
                textOn = context.getString(R.string.label_decrease);
                textOff = context.getString(R.string.label_increase);
                break;
            case INCOME:
                textOn = context.getString(R.string.label_charge);
                textOff = context.getString(R.string.label_income);
                break;
            case EXPENSE:
                textOn = context.getString(R.string.label_rebate);
                textOff = context.getString(R.string.label_expense);
                break;
            case PAYABLE:
                textOn = context.getString(R.string.label_payment);
                textOff = context.getString(R.string.label_bill);
                break;
            case RECEIVABLE:
                textOn = context.getString(R.string.label_payment);
                textOff = context.getString(R.string.label_invoice);
                break;
            case STOCK:
            case MUTUAL:
                textOn = context.getString(R.string.label_buy);
                textOff = context.getString(R.string.label_sell);
                break;
            case CURRENCY:
            case ROOT:
            default:
                textOn = context.getString(R.string.label_debit);
                textOff = context.getString(R.string.label_credit);
                break;
        }

        setTextOn(textOn);
        setTextOff(textOff);
        setText(isChecked() ? textOn : textOff);

        TextPaint paint = getPaint();
        float widthOn = paint.measureText(textOn);
        float widthOff = paint.measureText(textOff);
        textWidthMax = round(max(widthOn, widthOff));
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
                @SuppressLint("RestrictedApi")
                final Rect inset = DrawableUtils.getOpticalBounds(thumbDrawable);
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
     * @param amountView        Amount string {@link android.widget.EditText}
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
    public void setChecked(TransactionType transactionType) {
        setChecked(Transaction.shouldDecreaseBalance(mAccountType, transactionType));
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
        if (mAccountType.hasDebitNormalBalance()) {
            return isChecked() ? TransactionType.CREDIT : TransactionType.DEBIT;
        } else {
            return isChecked() ? TransactionType.DEBIT : TransactionType.CREDIT;
        }
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
