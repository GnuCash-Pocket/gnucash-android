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

import static org.gnucash.android.ui.util.TextViewExtKt.displayBalance;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CompoundButton;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;

import org.gnucash.android.R;
import org.gnucash.android.databinding.DialogTransferFundsBinding;
import org.gnucash.android.db.adapter.CommoditiesDbAdapter;
import org.gnucash.android.db.adapter.PricesDbAdapter;
import org.gnucash.android.model.Commodity;
import org.gnucash.android.model.Money;
import org.gnucash.android.model.Price;
import org.gnucash.android.ui.transaction.OnTransferFundsListener;
import org.gnucash.android.ui.util.dialog.VolatileDialogFragment;
import org.gnucash.android.util.AmountParser;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.text.ParseException;


/**
 * Dialog fragment for handling currency conversions when inputting transactions.
 * <p>This is used whenever a multi-currency transaction is being created.</p>
 */
public class TransferFundsDialogFragment extends VolatileDialogFragment {
    private Money mOriginAmount;
    private Commodity mTargetCommodity;

    private OnTransferFundsListener mOnTransferFundsListener;

    private DialogTransferFundsBinding binding;
    @ColorInt
    private int colorBalanceZero;

    public static TransferFundsDialogFragment getInstance(Money transactionAmount,
                                                          String targetCurrencyCode,
                                                          OnTransferFundsListener transferFundsListener) {
        return getInstance(
            transactionAmount,
            CommoditiesDbAdapter.getInstance().getCommodity(targetCurrencyCode),
            transferFundsListener
        );
    }

    public static TransferFundsDialogFragment getInstance(Money transactionAmount,
                                                          Commodity targetCurrency,
                                                          OnTransferFundsListener transferFundsListener) {
        TransferFundsDialogFragment fragment = new TransferFundsDialogFragment();
        // FIXME these fields must be persisted for when dialog is changed, e.g. rotated.
        fragment.mOriginAmount = transactionAmount;
        fragment.mTargetCommodity = targetCurrency;
        fragment.mOnTransferFundsListener = transferFundsListener;
        return fragment;
    }

    @NonNull
    private DialogTransferFundsBinding onCreateBinding(LayoutInflater inflater) {
        final DialogTransferFundsBinding binding = DialogTransferFundsBinding.inflate(inflater, null, false);
        this.binding = binding;

        colorBalanceZero = binding.amountToConvert.getCurrentTextColor();

        displayBalance(binding.amountToConvert, mOriginAmount, colorBalanceZero);
        Commodity fromCommodity = mOriginAmount.getCommodity();
        Commodity targetCommodity = mTargetCommodity;
        String fromCurrencyCode = fromCommodity.getCurrencyCode();
        String targetCurrencyCode = targetCommodity.getCurrencyCode();
        binding.fromCurrency.setText(fromCurrencyCode);
        binding.toCurrency.setText(targetCurrencyCode);
        binding.targetCurrency.setText(targetCurrencyCode);

        binding.labelExchangeRateExample.setText(String.format(getString(R.string.sample_exchange_rate),
            fromCurrencyCode,
            targetCurrencyCode));
        final InputLayoutErrorClearer textChangeListener = new InputLayoutErrorClearer();

        String commodityUID = fromCommodity.getUID();
        String currencyUID = targetCommodity.getUID();
        PricesDbAdapter pricesDbAdapter = PricesDbAdapter.getInstance();
        Pair<Long, Long> pricePair = pricesDbAdapter.getPrice(commodityUID, currencyUID);

        if (pricePair.first > 0 && pricePair.second > 0) {
            // a valid price exists
            Price price = new Price(fromCommodity, targetCommodity);
            price.setValueNum(pricePair.first);
            price.setValueDenom(pricePair.second);
            BigDecimal priceDecimal = price.toBigDecimal();
            NumberFormat formatter = NumberFormat.getNumberInstance();

            binding.inputExchangeRate.setText(formatter.format(priceDecimal));

            // convertedAmount = mOriginAmount * numerator / denominator
            BigDecimal convertedAmount = mOriginAmount.toBigDecimal().multiply(priceDecimal);
            binding.inputConvertedAmount.setText(formatter.format(convertedAmount));
        }

        binding.inputExchangeRate.addTextChangedListener(textChangeListener);
        binding.inputConvertedAmount.addTextChangedListener(textChangeListener);

        binding.radioConvertedAmount.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                binding.inputConvertedAmount.setEnabled(isChecked);
                binding.convertedAmountTextInputLayout.setErrorEnabled(isChecked);
                binding.radioExchangeRate.setChecked(!isChecked);
                if (isChecked) {
                    binding.inputConvertedAmount.requestFocus();
                }
            }
        });

        binding.radioExchangeRate.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                binding.inputExchangeRate.setEnabled(isChecked);
                binding.exchangeRateTextInputLayout.setErrorEnabled(isChecked);
                binding.btnFetchExchangeRate.setEnabled(isChecked);
                binding.radioConvertedAmount.setChecked(!isChecked);
                if (isChecked) {
                    binding.inputExchangeRate.requestFocus();
                }
            }
        });

        binding.btnFetchExchangeRate.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //TODO: Pull the exchange rate for the currency here
            }
        });

        return binding;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        DialogTransferFundsBinding binding = onCreateBinding(getLayoutInflater());
        Context context = binding.getRoot().getContext();
        return new AlertDialog.Builder(context, getTheme())
            .setTitle(R.string.title_transfer_funds)
            .setView(binding.getRoot())
            .setNegativeButton(R.string.btn_cancel, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    // Dismisses itself.
                }
            })
            .setPositiveButton(R.string.btn_save, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    transferFunds();
                }
            })
            .create();
    }

    /**
     * Converts the currency amount with the given exchange rate and saves the price to the db
     */
    private void transferFunds() {
        Commodity originCommodity = mOriginAmount.getCommodity();
        Commodity targetCommodity = mTargetCommodity;
        Money convertedAmount = null;

        Price price = null;
        if (binding.radioExchangeRate.isChecked()) {
            BigDecimal rate;
            try {
                rate = AmountParser.parse(binding.inputExchangeRate.getText().toString());
            } catch (ParseException e) {
                binding.exchangeRateTextInputLayout.setError(getString(R.string.error_invalid_exchange_rate));
                return;
            }
            convertedAmount = mOriginAmount.times(rate).withCurrency(targetCommodity);

            price = new Price(originCommodity, targetCommodity, rate);
        } else if (binding.radioConvertedAmount.isChecked()) {
            BigDecimal amount;
            try {
                amount = AmountParser.parse(binding.inputConvertedAmount.getText().toString());
            } catch (ParseException e) {
                binding.convertedAmountTextInputLayout.setError(getString(R.string.error_invalid_amount));
                return;
            }
            convertedAmount = new Money(amount, mTargetCommodity);

            price = new Price(originCommodity, targetCommodity);
            // fractions cannot be exactly represented by BigDecimal.
            price.setValueNum(convertedAmount.getNumerator() * mOriginAmount.getDenominator());
            price.setValueDenom(mOriginAmount.getNumerator() * convertedAmount.getDenominator());
        }
        if (price != null) {
            price.setSource(Price.SOURCE_USER);
            PricesDbAdapter.getInstance().addRecord(price);
        }

        if (mOnTransferFundsListener != null && convertedAmount != null) {
            mOnTransferFundsListener.transferComplete(mOriginAmount, convertedAmount);
        }
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
            binding.convertedAmountTextInputLayout.setErrorEnabled(false);
            binding.exchangeRateTextInputLayout.setErrorEnabled(false);
        }
    }
}
