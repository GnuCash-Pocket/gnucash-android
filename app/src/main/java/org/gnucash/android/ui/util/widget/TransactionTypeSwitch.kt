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
package org.gnucash.android.ui.util.widget

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.widget.CompoundButton
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.appcompat.widget.DrawableUtils
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import org.gnucash.android.R
import org.gnucash.android.model.AccountType
import org.gnucash.android.model.TransactionType
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * A special type of [android.widget.ToggleButton] which displays the appropriate CREDIT/DEBIT labels for the
 * different account types.
 *
 * @author Ngewi Fet <ngewif@gmail.com>
 */
class TransactionTypeSwitch @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : SwitchCompat(context, attrs) {
    var accountType = AccountType.ROOT
        set(value) {
            field = value
            setAccountTypeImpl(value)
        }
    var textCredit: String = context.getString(R.string.label_credit)
        private set
    var textDebit: String = context.getString(R.string.label_debit)
        private set

    private var onCheckedChangeListener: OnCheckedChangeListener? = null
    private var textWidthMax = 0
    private val tempRect = Rect()

    @ColorInt
    private val colorRed = ContextCompat.getColor(context, R.color.debit_red)

    @ColorInt
    private val colorGreen = ContextCompat.getColor(context, R.color.credit_green)

    init {
        showText = false
        setAccountTypeImpl(AccountType.BANK)

        // Force red/green colors.
        val isChecked = this.isChecked
        post {
            setChecked(!isChecked)
            setChecked(isChecked)
        }
    }

    private fun setAccountTypeImpl(accountType: AccountType) {
        val hasDebitBalance = accountType.hasDebitNormalBalance
        val context = this.context
        val textDebit: String
        val textCredit: String
        when (accountType) {
            AccountType.BANK -> {
                textDebit = context.getString(R.string.label_deposit)
                textCredit = context.getString(R.string.label_withdrawal)
            }

            AccountType.CASH -> {
                textDebit = context.getString(R.string.label_receive)
                textCredit = context.getString(R.string.label_spend)
            }

            AccountType.CREDIT -> {
                textDebit = context.getString(R.string.label_payment)
                textCredit = context.getString(R.string.label_charge)
            }

            AccountType.ASSET -> {
                textDebit = context.getString(R.string.label_increase)
                textCredit = context.getString(R.string.label_decrease)
            }

            AccountType.LIABILITY, AccountType.TRADING, AccountType.EQUITY -> {
                textDebit = context.getString(R.string.label_decrease)
                textCredit = context.getString(R.string.label_increase)
            }

            AccountType.STOCK, AccountType.MUTUAL, AccountType.CURRENCY -> {
                textDebit = context.getString(R.string.label_buy)
                textCredit = context.getString(R.string.label_sell)
            }

            AccountType.INCOME -> {
                textDebit = context.getString(R.string.label_charge)
                textCredit = context.getString(R.string.label_income)
            }

            AccountType.EXPENSE -> {
                textDebit = context.getString(R.string.label_expense)
                textCredit = context.getString(R.string.label_rebate)
            }

            AccountType.PAYABLE -> {
                textDebit = context.getString(R.string.label_payment)
                textCredit = context.getString(R.string.label_bill)
            }

            AccountType.RECEIVABLE -> {
                textDebit = context.getString(R.string.label_invoice)
                textCredit = context.getString(R.string.label_payment)
            }

            AccountType.ROOT -> {
                textDebit = context.getString(R.string.label_debit)
                textCredit = context.getString(R.string.label_credit)
            }

            else -> {
                textDebit = context.getString(R.string.label_debit)
                textCredit = context.getString(R.string.label_credit)
            }
        }

        this.textCredit = textCredit
        this.textDebit = textDebit
        val textOff = if (hasDebitBalance) textDebit else textCredit
        val textOn = if (hasDebitBalance) textCredit else textDebit
        setTextOff(textOff)
        setTextOn(textOn)
        text = if (isChecked) textOn else textOff

        val paint = getPaint()
        val widthOn = paint.measureText(textOn)
        val widthOff = paint.measureText(textOff)
        textWidthMax = max(widthOn, widthOff).roundToInt()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        var widthMeasureSpec = widthMeasureSpec
        if (textWidthMax > 0) {
            var paddingLeft = 0
            var paddingRight = 0
            val thumbDrawable = this.thumbDrawable
            val thumbWidth: Int
            if (thumbDrawable != null) {
                val padding = tempRect
                // Cached thumb width does not include padding.
                thumbDrawable.getPadding(padding)
                thumbWidth = thumbDrawable.intrinsicWidth - padding.left - padding.right
                // Adjust left and right padding to ensure there's enough room for the
                // thumb's padding (when present).
                @SuppressLint("RestrictedApi")
                val inset = DrawableUtils.getOpticalBounds(thumbDrawable)
                paddingLeft = max(padding.left, inset.left)
                paddingRight = max(padding.right, inset.right)
            } else {
                thumbWidth = 0
            }
            val switchWidth = max(switchMinWidth, (2 * thumbWidth + paddingLeft + paddingRight))
            val width = paddingStart + textWidthMax + switchWidth + paddingEnd
            widthMeasureSpec = MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY)
        }

        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    /**
     * Set a checked change listener to monitor the amount view and currency views and update the display (color & balance accordingly)
     *
     * @param amountView       Amount string [android.widget.EditText]
     * @param currencyTextView Currency symbol text view
     */
    fun setAmountFormattingListener(amountView: CalculatorEditText, currencyTextView: TextView) {
        setOnCheckedChangeListener(OnTypeChangedListener(amountView, currencyTextView))
    }

    /**
     * Add listeners to be notified when the checked status changes
     *
     * @param listener Checked change listener
     */
    fun addOnCheckedChangeListener(listener: OnCheckedChangeListener) {
        this.onCheckedChangeListener = listener
    }

    /**
     * Toggles the button checked based on the movement caused by the transaction type for the specified account
     *
     * @param transactionType [TransactionType] of the split
     */
    fun setChecked(transactionType: TransactionType) {
        post {
            isChecked = shouldDecreaseBalance(accountType, transactionType)
        }
    }

    val transactionType: TransactionType
        get() = if (isChecked) {
            if (accountType.hasDebitNormalBalance) TransactionType.CREDIT else TransactionType.DEBIT
        } else {
            if (accountType.hasDebitNormalBalance) TransactionType.DEBIT else TransactionType.CREDIT
        }

    /**
     * Is the transaction type represents a decrease for the account balance for the `accountType`?
     *
     * @return true if the amount represents a decrease in the account balance, false otherwise
     */
    private fun shouldDecreaseBalance(
        accountType: AccountType,
        transactionType: TransactionType
    ): Boolean {
        return if (accountType.hasDebitNormalBalance) {
            transactionType == TransactionType.CREDIT
        } else {
            transactionType == TransactionType.DEBIT
        }
    }

    private inner class OnTypeChangedListener(
        private val amountEditText: CalculatorEditText,
        private val currencyTextView: TextView
    ) : OnCheckedChangeListener {
        override fun onCheckedChanged(compoundButton: CompoundButton, isChecked: Boolean) {
            text = if (isChecked) textOn else textOff
            if (isChecked) {
                setTextColor(colorRed)
                amountEditText.setTextColor(colorRed)
                currencyTextView.setTextColor(colorRed)
            } else {
                setTextColor(colorGreen)
                amountEditText.setTextColor(colorGreen)
                currencyTextView.setTextColor(colorGreen)
            }
            val amount = amountEditText.value
            if (amount != null) {
                if ((isChecked && amount.signum() > 0) //we switched to debit but the amount is +ve
                    || (!isChecked && amount.signum() < 0)
                ) { //credit but amount is -ve
                    amountEditText.value = -amount
                }
            }

            onCheckedChangeListener?.onCheckedChanged(compoundButton, isChecked)
        }
    }
}
