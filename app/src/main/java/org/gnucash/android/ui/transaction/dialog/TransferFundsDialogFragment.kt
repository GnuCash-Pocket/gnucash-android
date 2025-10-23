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
package org.gnucash.android.ui.transaction.dialog

import android.app.Dialog
import android.database.SQLException
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import androidx.annotation.ColorInt
import androidx.appcompat.app.AlertDialog
import org.gnucash.android.R
import org.gnucash.android.databinding.DialogTransferFundsBinding
import org.gnucash.android.db.adapter.PricesDbAdapter
import org.gnucash.android.model.Commodity
import org.gnucash.android.model.Money
import org.gnucash.android.model.Price
import org.gnucash.android.quote.QuoteCallback
import org.gnucash.android.quote.QuoteProvider
import org.gnucash.android.quote.YahooJson
import org.gnucash.android.ui.transaction.OnTransferFundsListener
import org.gnucash.android.ui.util.TextInputResetError
import org.gnucash.android.ui.util.dialog.VolatileDialogFragment
import org.gnucash.android.ui.util.displayBalance
import org.gnucash.android.util.AmountParser.parse
import timber.log.Timber
import java.math.BigDecimal
import java.text.NumberFormat
import java.text.ParseException

/**
 * Dialog fragment for handling currency conversions when inputting transactions.
 *
 * This is used whenever a multi-currency transaction is being created.
 */
class TransferFundsDialogFragment : VolatileDialogFragment() {
    // FIXME these fields must be persisted for when dialog is changed, e.g. rotated.
    private var originAmount: Money? = null

    // FIXME these fields must be persisted for when dialog is changed, e.g. rotated.
    private var targetCommodity: Commodity? = null

    // FIXME these fields must be persisted for when dialog is changed, e.g. rotated.
    private var onTransferFundsListener: OnTransferFundsListener? = null

    private var pricesDbAdapter = PricesDbAdapter.instance
    private var priceQuoted: Price? = null

    override fun onStart() {
        super.onStart()
        pricesDbAdapter = PricesDbAdapter.instance
    }

    private fun onCreateBinding(inflater: LayoutInflater): DialogTransferFundsBinding {
        val binding = DialogTransferFundsBinding.inflate(inflater, null, false)

        val fromAmount = originAmount!!
        val fromDecimal = fromAmount.toBigDecimal()
        @ColorInt val colorBalanceZero = binding.amountToConvert.currentTextColor
        binding.amountToConvert.displayBalance(fromAmount, colorBalanceZero)

        val fromCommodity = fromAmount.commodity
        val targetCommodity = this.targetCommodity!!
        val formatterAmount = NumberFormat.getNumberInstance()
        formatterAmount.minimumFractionDigits = targetCommodity.smallestFractionDigits
        formatterAmount.maximumFractionDigits = targetCommodity.smallestFractionDigits
        val formatterRate = NumberFormat.getNumberInstance()
        formatterRate.minimumFractionDigits = SCALE_RATE
        formatterRate.maximumFractionDigits = SCALE_RATE

        val fromCurrencyCode = fromCommodity.currencyCode
        val targetCurrencyCode = targetCommodity.currencyCode
        binding.fromCurrency.text = fromCommodity.formatListItem()
        binding.toCurrency.text = targetCommodity.formatListItem()

        binding.exchangeRateExample.setText(R.string.sample_exchange_rate)
        binding.exchangeRateInverse.text = null
        val textChangeListener = TextInputResetError(
            binding.convertedAmountTextInputLayout,
            binding.exchangeRateTextInputLayout
        )

        binding.inputExchangeRate.addTextChangedListener(textChangeListener)
        binding.inputConvertedAmount.addTextChangedListener(textChangeListener)

        binding.radioConvertedAmount.setOnCheckedChangeListener { _, isChecked ->
            binding.inputConvertedAmount.isEnabled = isChecked
            binding.convertedAmountTextInputLayout.isErrorEnabled = isChecked
            binding.radioExchangeRate.isChecked = !isChecked
            if (isChecked) {
                binding.inputConvertedAmount.requestFocus()
            }
        }

        binding.radioExchangeRate.setOnCheckedChangeListener { _, isChecked ->
            binding.inputExchangeRate.isEnabled = isChecked
            binding.exchangeRateTextInputLayout.isErrorEnabled = isChecked
            binding.btnFetchExchangeRate.isEnabled =
                (isChecked && fromCommodity.isCurrency && targetCommodity.isCurrency)
            binding.radioConvertedAmount.isChecked = !isChecked
            if (isChecked) {
                binding.inputExchangeRate.requestFocus()
            }
        }
        // Enable the "Fetch Quote" button.
        binding.radioExchangeRate.post {
            binding.radioExchangeRate.isChecked = true
        }

        binding.btnFetchExchangeRate.setOnClickListener {
            binding.btnFetchExchangeRate.isEnabled = false
            fetchQuote(binding, fromCommodity, targetCommodity)
        }

        binding.inputExchangeRate.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable) {
                if (!binding.radioExchangeRate.isChecked) return
                val value = s.toString()
                try {
                    val rateDecimal = parse(value)
                    val rate = rateDecimal.toDouble()
                    binding.exchangeRateExample.text = getString(
                        R.string.exchange_rate_example,
                        fromCurrencyCode,
                        formatterRate.format(rate),
                        targetCurrencyCode
                    )
                    if (rate > 0f) {
                        binding.exchangeRateInverse.text = getString(
                            R.string.exchange_rate_example,
                            targetCurrencyCode,
                            formatterRate.format(1 / rate),
                            fromCurrencyCode
                        )
                        val price = fromDecimal * rateDecimal
                        binding.inputConvertedAmount.setText(formatterAmount.format(price))
                    } else {
                        binding.exchangeRateInverse.text = null
                    }
                } catch (_: ParseException) {
                }
            }

            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) =
                Unit

            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) = Unit
        })

        binding.inputConvertedAmount.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable) {
                if (!binding.radioConvertedAmount.isChecked) return
                val value = s.toString()
                try {
                    val amount = parse(value)
                    if (amount > BigDecimal.ZERO) {
                        val rate = amount.toDouble() / fromDecimal.toDouble()
                        binding.exchangeRateExample.text = getString(
                            R.string.exchange_rate_example,
                            fromCurrencyCode,
                            formatterRate.format(rate),
                            targetCurrencyCode
                        )
                        binding.exchangeRateInverse.text = getString(
                            R.string.exchange_rate_example,
                            targetCurrencyCode,
                            formatterRate.format(1 / rate),
                            fromCurrencyCode
                        )
                        binding.inputExchangeRate.setText(formatterRate.format(rate))
                    } else {
                        binding.exchangeRateExample.text = null
                        binding.exchangeRateInverse.text = null
                    }
                } catch (_: ParseException) {
                }
            }

            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) =
                Unit

            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) = Unit
        })

        val price = pricesDbAdapter.getPrice(fromCommodity, targetCommodity)
        if (price != null) {
            // a valid price exists
            val priceDecimal = price.toBigDecimal(SCALE_RATE)

            binding.radioExchangeRate.isChecked = true
            binding.inputExchangeRate.setText(formatterRate.format(priceDecimal))
            binding.btnFetchExchangeRate.isEnabled =
                fromCommodity.isCurrency && targetCommodity.isCurrency

            // convertedAmount = fromAmount * numerator / denominator
            val convertedAmount = fromDecimal * priceDecimal
            binding.inputConvertedAmount.setText(formatterAmount.format(convertedAmount))
        }

        return binding
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val binding = onCreateBinding(layoutInflater)
        val context = binding.root.context
        return AlertDialog.Builder(context, theme)
            .setTitle(R.string.title_transfer_funds)
            .setView(binding.root)
            .setNegativeButton(R.string.btn_cancel) { _, _ ->
                // Dismisses itself.
            }
            .setPositiveButton(R.string.btn_save) { _, _ ->
                transferFunds(originAmount!!.commodity, targetCommodity!!, binding)
            }
            .create()
    }

    /**
     * Converts the currency amount with the given exchange rate and saves the price to the db
     */
    private fun transferFunds(
        originCommodity: Commodity,
        targetCommodity: Commodity,
        binding: DialogTransferFundsBinding
    ) {
        val commoditiesDbAdapter = pricesDbAdapter.commoditiesDbAdapter
        val commodityFrom = commoditiesDbAdapter.loadCommodity(originCommodity)
        val commodityTo = commoditiesDbAdapter.loadCommodity(targetCommodity)
        var price = Price(commodityFrom, commodityTo)
        price.source = Price.SOURCE_USER
        price.type = Price.Type.Transaction
        val originAmount = originAmount!!
        val convertedAmount: Money
        if (binding.radioExchangeRate.isChecked) {
            val rate: BigDecimal
            try {
                rate = parse(binding.inputExchangeRate.text.toString())
            } catch (_: ParseException) {
                binding.exchangeRateTextInputLayout.error = getString(R.string.error_invalid_exchange_rate)
                return
            }
            if (rate <= BigDecimal.ZERO) {
                binding.exchangeRateTextInputLayout.error = getString(R.string.error_invalid_exchange_rate)
                return
            }
            convertedAmount = (originAmount * rate).withCommodity(targetCommodity)

            price.setExchangeRate(rate)
        } else {
            val amount: BigDecimal
            try {
                amount = parse(binding.inputConvertedAmount.text.toString())
            } catch (_: ParseException) {
                binding.convertedAmountTextInputLayout.error = getString(R.string.error_invalid_amount)
                return
            }
            if (amount < BigDecimal.ZERO) {
                binding.convertedAmountTextInputLayout.error = getString(R.string.error_invalid_amount)
                return
            }
            convertedAmount = Money(amount, targetCommodity)

            // fractions cannot be exactly represented by BigDecimal.
            price.valueNum = convertedAmount.numerator * originAmount.denominator
            price.valueDenom = originAmount.numerator * convertedAmount.denominator
        }
        if (priceQuoted != null && priceQuoted == price) {
            price = priceQuoted!!
        }
        try {
            pricesDbAdapter.insert(price)

            onTransferFundsListener?.transferComplete(originAmount, convertedAmount)
        } catch (e: SQLException) {
            Timber.e(e)
        }
    }

    private fun fetchQuote(
        binding: DialogTransferFundsBinding,
        fromCommodity: Commodity,
        targetCommodity: Commodity
    ) {
        binding.exchangeRateTextInputLayout.error = null
        if (!fromCommodity.isCurrency) {
            binding.exchangeRateTextInputLayout.error = "Currency expected"
            return
        }
        if (!targetCommodity.isCurrency) {
            binding.exchangeRateTextInputLayout.error = "Currency expected"
            return
        }
        val formatterRate = NumberFormat.getNumberInstance()
        formatterRate.minimumFractionDigits = SCALE_RATE
        formatterRate.maximumFractionDigits = SCALE_RATE

        val provider: QuoteProvider = YahooJson()
        provider.get(fromCommodity, targetCommodity, this, object : QuoteCallback {
            override fun onQuote(price: Price?) {
                if (price != null) {
                    priceQuoted = price
                    val rate = price.toBigDecimal(SCALE_RATE)
                    binding.inputExchangeRate.setText(formatterRate.format(rate))
                }
                binding.btnFetchExchangeRate.isEnabled = true
            }
        })
    }

    companion object {
        private const val SCALE_RATE = 6

        fun getInstance(
            transactionAmount: Money,
            targetCommodity: Commodity,
            transferFundsListener: OnTransferFundsListener
        ): TransferFundsDialogFragment {
            val fragment = TransferFundsDialogFragment()
            fragment.originAmount = transactionAmount
            fragment.targetCommodity = targetCommodity
            fragment.onTransferFundsListener = transferFundsListener
            return fragment
        }
    }
}
