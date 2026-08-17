package org.gnucash.android.ui.util

import android.content.Context
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import org.gnucash.android.R
import org.gnucash.android.model.Account
import org.gnucash.android.model.Money
import org.gnucash.android.model.isNullOrZero

/**
 * Display the balance of a transaction in a text view and format the text color to match the sign of the amount
 *
 * @param balance {@link org.gnucash.android.model.Money} balance to display.
 * @param colorZero The color for zero balance.
 */
fun TextView.displayBalance(balance: Money?, @ColorInt colorZero: Int) {
    val context: Context = this.context
    @ColorInt val balanceColor = if (balance.isNullOrZero()) {
        colorZero
    } else if (balance.isNegative) {
        ContextCompat.getColor(context, R.color.debit_red)
    } else {
        ContextCompat.getColor(context, R.color.credit_green)
    }

    text = balance?.formattedString()
    setTextColor(balanceColor)
    isVisible = true
}

/**
 * Display the balance of a transaction in a text view and format the text color to match the sign of the amount
 *
 * @param account the account.
 * @param balance {@link org.gnucash.android.model.Money} balance to display.
 * @param colorZero The color for zero balance.
 */
fun TextView.displayBalance(account: Account, balance: Money?, @ColorInt colorZero: Int) {
    var balance = balance
    val context: Context = this.context
    @ColorInt val balanceColor = if (balance.isNullOrZero()) {
        colorZero
    } else {
        val accountType = account.type
        balance = if (accountType.hasDebitNormalBalance == accountType.hasDebitDisplayBalance) {
            balance
        } else {
            -balance
        }
        if (balance.isNegative) {
            ContextCompat.getColor(context, R.color.debit_red)
        } else {
            ContextCompat.getColor(context, R.color.credit_green)
        }
    }

    text = balance?.formattedString()
    setTextColor(balanceColor)
    isVisible = true
}