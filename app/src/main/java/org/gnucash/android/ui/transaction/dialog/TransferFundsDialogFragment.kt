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
import android.database.SQLException;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CompoundButton;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import org.gnucash.android.R;
import org.gnucash.android.databinding.DialogTransferFundsBinding;
import org.gnucash.android.db.adapter.CommoditiesDbAdapter;
import org.gnucash.android.db.adapter.DatabaseAdapter;
import org.gnucash.android.db.adapter.PricesDbAdapter;
import org.gnucash.android.model.Commodity;
import org.gnucash.android.model.Money;
import org.gnucash.android.model.Price;
import org.gnucash.android.quote.QuoteCallback;
import org.gnucash.android.quote.QuoteProvider;
import org.gnucash.android.quote.YahooJson;
import org.gnucash.android.ui.transaction.OnTransferFundsListener;
import org.gnucash.android.ui.util.TextInputResetError;
import org.gnucash.android.ui.util.dialog.VolatileDialogFragment;
import org.gnucash.android.util.AmountParser;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.text.ParseException;

import timber.log.Timber;

/**
 * Dialog fragment for handling currency conversions when inputting transactions.
 * <p>This is used whenever a multi-currency transaction is being created.</p>
 */
public class TransferFundsDialogFragment extends VolatileDialogFragment {
    // FIXME these fields must be persisted for when dialog is changed, e.g. rotated.
    private Money mOriginAmount;
    // FIXME these fields must be persisted for when dialog is changed, e.g. rotated.
    private Commodity mTargetCommodity;

    // FIXME these fields must be persisted for when dialog is changed, e.g. rotated.
    private OnTransferFundsListener mOnTransferFundsListener;

    private PricesDbAdapter pricesDbAdapter = PricesDbAdapter.getInstance();
    private Price priceQuoted;

    private static final int SCALE_RATE = 6;

    public static TransferFundsDialogFragment getInstance(
        @NonNull Money transactionAmount,
        @NonNull Commodity targetCommodity,
        @Nullable OnTransferFundsListener transferFundsListener
    ) {
        TransferFundsDialogFragment fragment = new TransferFundsDialogFragment();
        fragment.mOriginAmount = transactionAmount;
        fragment.mTargetCommodity = targetCommodity;
        fragment.mOnTransferFundsListener = transferFundsListener;
        return fragment;
    }

    @Override
    public void onStart() {
        super.onStart();
        pricesDbAdapter = PricesDbAdapter.getInstance();
    }

    @NonNull
    private DialogTransferFundsBinding onCreateBinding(LayoutInflater inflater) {
        final DialogTransferFundsBinding binding = DialogTransferFundsBinding.inflate(inflater, null, false);

        final Money fromAmount = mOriginAmount;
        final BigDecimal fromDecimal = fromAmount.toBigDecimal();
        @ColorInt int colorBalanceZero = binding.amountToConvert.getCurrentTextColor();
        displayBalance(binding.amountToConvert, fromAmount, colorBalanceZero);

        final Commodity fromCommodity = fromAmount.getCommodity();
        final Commodity targetCommodity = mTargetCommodity;
        final NumberFormat formatterAmount = NumberFormat.getNumberInstance();
        formatterAmount.setMinimumFractionDigits(targetCommodity.getSmallestFractionDigits());
        formatterAmount.setMaximumFractionDigits(targetCommodity.getSmallestFractionDigits());
        final NumberFormat formatterRate = NumberFormat.getNumberInstance();
        formatterRate.setMinimumFractionDigits(SCALE_RATE);
        formatterRate.setMaximumFractionDigits(SCALE_RATE);

        final String fromCurrencyCode = fromCommodity.getCurrencyCode();
        final String targetCurrencyCode = targetCommodity.getCurrencyCode();
        binding.fromCurrency.setText(fromCommodity.formatListItem());
        binding.toCurrency.setText(targetCommodity.formatListItem());

        binding.exchangeRateExample.setText(R.string.sample_exchange_rate);
        binding.exchangeRateInverse.setText(null);
        final TextInputResetError textChangeListener = new TextInputResetError(
            binding.convertedAmountTextInputLayout,
            binding.exchangeRateTextInputLayout
        );

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
                binding.btnFetchExchangeRate.setEnabled(isChecked && fromCommodity.isCurrency() && targetCommodity.isCurrency());
                binding.radioConvertedAmount.setChecked(!isChecked);
                if (isChecked) {
                    binding.inputExchangeRate.requestFocus();
                }
            }
        });

        binding.btnFetchExchangeRate.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                binding.btnFetchExchangeRate.setEnabled(false);
                fetchQuote(binding, fromCommodity, targetCommodity);
            }
        });

        binding.inputExchangeRate.addTextChangedListener(new TextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                if (!binding.radioExchangeRate.isChecked()) return;
                String value = s.toString();
                try {
                    BigDecimal rateDecimal = AmountParser.parse(value);
                    float rate = rateDecimal.floatValue();
                    binding.exchangeRateExample.setText(
                        getString(
                            R.string.exchange_rate_example,
                            fromCurrencyCode,
                            formatterRate.format(rate),
                            targetCurrencyCode
                        )
                    );
                    if (rate > 0f) {
                        binding.exchangeRateInverse.setText(
                            getString(
                                R.string.exchange_rate_example,
                                targetCurrencyCode,
                                formatterRate.format(1 / rate),
                                fromCurrencyCode
                            )
                        );
                        BigDecimal price = fromDecimal.multiply(rateDecimal);
                        binding.inputConvertedAmount.setText(formatterAmount.format(price));
                    } else {
                        binding.exchangeRateInverse.setText(null);
                    }
                } catch (ParseException ignore) {
                }
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }
        });

        binding.inputConvertedAmount.addTextChangedListener(new TextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                if (!binding.radioConvertedAmount.isChecked()) return;
                String value = s.toString();
                try {
                    BigDecimal amount = AmountParser.parse(value);
                    if (amount.compareTo(BigDecimal.ZERO) > 0) {
                        float rate = amount.floatValue() / fromDecimal.floatValue();
                        binding.exchangeRateExample.setText(
                            getString(
                                R.string.exchange_rate_example,
                                fromCurrencyCode,
                                formatterRate.format(rate),
                                targetCurrencyCode
                            )
                        );
                        binding.exchangeRateInverse.setText(
                            getString(
                                R.string.exchange_rate_example,
                                targetCurrencyCode,
                                formatterRate.format(1 / rate),
                                fromCurrencyCode
                            )
                        );
                        binding.inputExchangeRate.setText(formatterRate.format(rate));
                    } else {
                        binding.exchangeRateExample.setText(null);
                        binding.exchangeRateInverse.setText(null);
                    }
                } catch (ParseException ignore) {
                }
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }
        });

        Price price = pricesDbAdapter.getPrice(fromCommodity, targetCommodity);
        if (price != null) {
            // a valid price exists
            BigDecimal priceDecimal = price.toBigDecimal(SCALE_RATE);

            binding.radioExchangeRate.setChecked(true);
            binding.inputExchangeRate.setText(formatterRate.format(priceDecimal));
            binding.btnFetchExchangeRate.setEnabled(fromCommodity.isCurrency() && targetCommodity.isCurrency());

            // convertedAmount = fromAmount * numerator / denominator
            BigDecimal convertedAmount = fromDecimal.multiply(priceDecimal);
            binding.inputConvertedAmount.setText(formatterAmount.format(convertedAmount));
        }

        return binding;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        final DialogTransferFundsBinding binding = onCreateBinding(getLayoutInflater());
        final Context context = binding.getRoot().getContext();
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
                    transferFunds(mOriginAmount.getCommodity(), mTargetCommodity, binding);
                }
            })
            .create();
    }

    /**
     * Converts the currency amount with the given exchange rate and saves the price to the db
     */
    private void transferFunds(
        @NonNull Commodity originCommodity,
        @NonNull Commodity targetCommodity,
        @NonNull DialogTransferFundsBinding binding
    ) {
        CommoditiesDbAdapter commoditiesDbAdapter = pricesDbAdapter.commoditiesDbAdapter;
        Commodity commodityFrom = commoditiesDbAdapter.loadCommodity(originCommodity);
        if (commodityFrom == null) {
            Timber.e("Origin commodity not found in db!");
            return;
        }
        Commodity commodityTo = commoditiesDbAdapter.loadCommodity(targetCommodity);
        if (commodityTo == null) {
            Timber.e("Target commodity not found in db!");
            return;
        }
        Price price = new Price(commodityFrom, commodityTo);
        price.setSource(Price.SOURCE_USER);
        price.setType(Price.Type.Transaction);
        final Money convertedAmount;
        if (binding.radioExchangeRate.isChecked()) {
            final BigDecimal rate;
            try {
                rate = AmountParser.parse(binding.inputExchangeRate.getText().toString());
            } catch (ParseException e) {
                binding.exchangeRateTextInputLayout.setError(getString(R.string.error_invalid_exchange_rate));
                return;
            }
            if (rate.compareTo(BigDecimal.ZERO) <= 0) {
                binding.exchangeRateTextInputLayout.setError(getString(R.string.error_invalid_exchange_rate));
                return;
            }
            convertedAmount = mOriginAmount.times(rate).withCommodity(targetCommodity);

            price.setExchangeRate(rate);
        } else {
            final BigDecimal amount;
            try {
                amount = AmountParser.parse(binding.inputConvertedAmount.getText().toString());
            } catch (ParseException e) {
                binding.convertedAmountTextInputLayout.setError(getString(R.string.error_invalid_amount));
                return;
            }
            if (amount.compareTo(BigDecimal.ZERO) < 0) {
                binding.convertedAmountTextInputLayout.setError(getString(R.string.error_invalid_amount));
                return;
            }
            convertedAmount = new Money(amount, targetCommodity);

            // fractions cannot be exactly represented by BigDecimal.
            price.setValueNum(convertedAmount.getNumerator() * mOriginAmount.getDenominator());
            price.setValueDenom(mOriginAmount.getNumerator() * convertedAmount.getDenominator());
        }
        if (priceQuoted != null && priceQuoted.equals(price)) {
            price = priceQuoted;
        }
        try {
            pricesDbAdapter.addRecord(price, DatabaseAdapter.UpdateMethod.insert);

            if (mOnTransferFundsListener != null) {
                mOnTransferFundsListener.transferComplete(mOriginAmount, convertedAmount);
            }
        } catch (SQLException e) {
            Timber.e(e);
        }
    }

    private void fetchQuote(final DialogTransferFundsBinding binding, Commodity fromCommodity, Commodity targetCommodity) {
        binding.exchangeRateTextInputLayout.setError(null);
        if (!fromCommodity.isCurrency()) {
            binding.exchangeRateTextInputLayout.setError("Currency expected");
            return;
        }
        if (!targetCommodity.isCurrency()) {
            binding.exchangeRateTextInputLayout.setError("Currency expected");
            return;
        }
        final NumberFormat formatterRate = NumberFormat.getNumberInstance();
        formatterRate.setMinimumFractionDigits(SCALE_RATE);
        formatterRate.setMaximumFractionDigits(SCALE_RATE);

        QuoteProvider provider = new YahooJson();
        provider.get(fromCommodity, targetCommodity, this, new QuoteCallback() {

            @Override
            public void onQuote(@NonNull Price price) {
                priceQuoted = price;
                BigDecimal rate = price.toBigDecimal(SCALE_RATE);
                binding.inputExchangeRate.setText(formatterRate.format(rate));
                binding.btnFetchExchangeRate.setEnabled(true);
            }
        });
    }
}
