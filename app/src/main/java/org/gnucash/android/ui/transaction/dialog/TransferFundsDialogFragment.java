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

package org.gnucash.android.ui.transaction.dialog;

import android.app.Dialog;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import org.gnucash.android.R;
import org.gnucash.android.databinding.DialogTransferFundsBinding;
import org.gnucash.android.db.adapter.CommoditiesDbAdapter;
import org.gnucash.android.db.adapter.PricesDbAdapter;
import org.gnucash.android.model.Commodity;
import org.gnucash.android.model.Money;
import org.gnucash.android.model.Price;
import org.gnucash.android.ui.transaction.OnTransferFundsListener;
import org.gnucash.android.ui.transaction.TransactionsActivity;
import org.gnucash.android.util.AmountParser;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.text.ParseException;


/**
 * Dialog fragment for handling currency conversions when inputting transactions.
 * <p>This is used whenever a multi-currency transaction is being created.</p>
 */
public class TransferFundsDialogFragment extends DialogFragment {
    Money mOriginAmount;
    private Commodity mTargetCommodity;

    OnTransferFundsListener mOnTransferFundsListener;

    private DialogTransferFundsBinding mBinding;

    public static TransferFundsDialogFragment getInstance(Money transactionAmount, String targetCurrencyCode,
                                                          OnTransferFundsListener transferFundsListener) {
        TransferFundsDialogFragment fragment = new TransferFundsDialogFragment();
        fragment.mOriginAmount = transactionAmount;
        fragment.mTargetCommodity = CommoditiesDbAdapter.getInstance().getCommodity(targetCurrencyCode);
        fragment.mOnTransferFundsListener = transferFundsListener;
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        mBinding = DialogTransferFundsBinding.inflate(inflater, container, false);
        View view = mBinding.getRoot();

        TransactionsActivity.displayBalance(mBinding.amountToConvert, mOriginAmount);
        String fromCurrencyCode = mOriginAmount.getCommodity().getCurrencyCode();
        mBinding.fromCurrency.setText(fromCurrencyCode);
        mBinding.toCurrency.setText(mTargetCommodity.getCurrencyCode());
        mBinding.targetCurrency.setText(mTargetCommodity.getCurrencyCode());

        mBinding.labelExchangeRateExample.setText(String.format(getString(R.string.sample_exchange_rate),
                fromCurrencyCode,
                mTargetCommodity.getCurrencyCode()));
        final InputLayoutErrorClearer textChangeListener = new InputLayoutErrorClearer();

        CommoditiesDbAdapter commoditiesDbAdapter = CommoditiesDbAdapter.getInstance();
        String commodityUID = commoditiesDbAdapter.getCommodityUID(fromCurrencyCode);
        String currencyUID = mTargetCommodity.getUID();
        PricesDbAdapter pricesDbAdapter = PricesDbAdapter.getInstance();
        Pair<Long, Long> pricePair = pricesDbAdapter.getPrice(commodityUID, currencyUID);

        if (pricePair.first > 0 && pricePair.second > 0) {
            // a valid price exists
            Price price = new Price(commodityUID, currencyUID);
            price.setValueNum(pricePair.first);
            price.setValueDenom(pricePair.second);
            mBinding.inputExchangeRate.setText(price.toString());

            BigDecimal numerator = new BigDecimal(pricePair.first);
            BigDecimal denominator = new BigDecimal(pricePair.second);
            // convertedAmount = mOriginAmount * numerator / denominator
            BigDecimal convertedAmount = mOriginAmount.asBigDecimal().multiply(numerator)
                    .divide(denominator, mTargetCommodity.getSmallestFractionDigits(), BigDecimal.ROUND_HALF_EVEN);
            DecimalFormat formatter = (DecimalFormat) NumberFormat.getNumberInstance();
            mBinding.inputConvertedAmount.setText(formatter.format(convertedAmount));
        }

        mBinding.inputExchangeRate.addTextChangedListener(textChangeListener);
        mBinding.inputConvertedAmount.addTextChangedListener(textChangeListener);

        mBinding.radioConvertedAmount.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                mBinding.inputConvertedAmount.setEnabled(isChecked);
                mBinding.convertedAmountTextInputLayout.setErrorEnabled(isChecked);
                mBinding.radioExchangeRate.setChecked(!isChecked);
                if (isChecked) {
                    mBinding.inputConvertedAmount.requestFocus();
                }
            }
        });

        mBinding.radioExchangeRate.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                mBinding.inputExchangeRate.setEnabled(isChecked);
                mBinding.exchangeRateTextInputLayout.setErrorEnabled(isChecked);
                mBinding.btnFetchExchangeRate.setEnabled(isChecked);
                mBinding.radioConvertedAmount.setChecked(!isChecked);
                if (isChecked) {
                    mBinding.inputExchangeRate.requestFocus();
                }
            }
        });

        mBinding.btnFetchExchangeRate.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //TODO: Pull the exchange rate for the currency here
            }
        });

        mBinding.defaultButtons.btnCancel.setOnClickListener(unusedView -> dismiss());
        mBinding.defaultButtons.btnSave.setOnClickListener(unusedView -> transferFunds());
        return view;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Dialog dialog = super.onCreateDialog(savedInstanceState);
        dialog.setTitle(R.string.title_transfer_funds);
        return dialog;
    }

    /**
     * Converts the currency amount with the given exchange rate and saves the price to the db
     */
    private void transferFunds() {
        Price price;

        String originCommodityUID = mOriginAmount.getCommodity().getUID();
        String targetCommodityUID = mTargetCommodity.getUID();
        Money convertedAmount = null;

        if (mBinding.radioExchangeRate.isChecked()) {
            BigDecimal rate;
            try {
                rate = AmountParser.parse(mBinding.inputExchangeRate.getText().toString());
            } catch (ParseException e) {
                mBinding.exchangeRateTextInputLayout.setError(getString(R.string.error_invalid_exchange_rate));
                return;
            }
            convertedAmount = mOriginAmount.times(rate).withCurrency(mTargetCommodity);

            price = new Price(originCommodityUID, targetCommodityUID, rate);
            price.setSource(Price.SOURCE_USER);
            PricesDbAdapter.getInstance().addRecord(price);
        } else if (mBinding.radioConvertedAmount.isChecked()) {
            BigDecimal amount;
            try {
                amount = AmountParser.parse(mBinding.inputConvertedAmount.getText().toString());
            } catch (ParseException e) {
                mBinding.convertedAmountTextInputLayout.setError(getString(R.string.error_invalid_amount));
                return;
            }
            convertedAmount = new Money(amount, mTargetCommodity);

            price = new Price(originCommodityUID, targetCommodityUID);
            // fractions cannot be exactly represented by BigDecimal.
            price.setValueNum(convertedAmount.getNumerator() * mOriginAmount.getDenominator());
            price.setValueDenom(mOriginAmount.getNumerator() * convertedAmount.getDenominator());
            price.setSource(Price.SOURCE_USER);
            PricesDbAdapter.getInstance().addRecord(price);
        }

        if (mOnTransferFundsListener != null && convertedAmount != null) {
            mOnTransferFundsListener.transferComplete(mOriginAmount, convertedAmount);
        }

        dismiss();
    }

    /**
     * Hides the error message from mBinding.convertedAmountTextInputLayout and mBinding.exchangeRateTextInputLayout
     * when the user edits their content.
     */
    private class InputLayoutErrorClearer implements TextWatcher {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
        }

        @Override
        public void afterTextChanged(Editable s) {
            mBinding.convertedAmountTextInputLayout.setErrorEnabled(false);
            mBinding.exchangeRateTextInputLayout.setErrorEnabled(false);
        }
    }
}
