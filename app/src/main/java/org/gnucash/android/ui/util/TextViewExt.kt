package org.gnucash.android.ui.util

import android.content.Context
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.core.content.ContextCompat
import org.gnucash.android.model.Money

/**
 * Display the balance of a transaction in a text view and format the text color to match the sign of the amount
 *
 * @param balance {@link org.gnucash.android.model.Money} balance to display.
 * @param colorZero The color for zero balance.
 */
fun TextView.displayBalance(balance: Money, @ColorInt colorZero: Int) {
    val context: Context = this.context
    @ColorInt val fontColor = if (balance.isAmountZero) {
        colorZero
    } else if (balance.isNegative) {
        ContextCompat.getColor(context, org.gnucash.android.R.color.debit_red)
    } else {
        ContextCompat.getColor(context, org.gnucash.android.R.color.credit_green)
    }

    text = balance.formattedString()
    setTextColor(fontColor)
}