package org.gnucash.android.util

import android.os.Build
import androidx.annotation.RequiresApi
import net.objecthunter.exp4j.ExpressionBuilder
import timber.log.Timber
import java.math.BigDecimal
import java.text.DecimalFormat
import java.text.NumberFormat
import java.text.ParseException
import java.text.ParsePosition

/**
 * Parses amounts as String into BigDecimal.
 */
object AmountParser {
    /**
     * Parses `amount` and returns it as a BigDecimal.
     *
     * @param amount String with the amount to parse.
     * @return The amount parsed as a BigDecimal.
     * @throws ParseException if the full string couldn't be parsed as an amount.
     */
    @Throws(ParseException::class)
    fun parse(amount: String?): BigDecimal {
        if (amount == null || amount.isEmpty()) {
            throw ParseException("Parse error", 0)
        }
        val formatter = NumberFormat.getNumberInstance() as DecimalFormat
        formatter.isParseBigDecimal = true
        val parsePosition = ParsePosition(0)
        val parsedAmount = formatter.parse(amount, parsePosition) as BigDecimal?

        // Ensure any mistyping by the user is caught instead of partially parsed
        if ((parsedAmount == null) || (parsePosition.index < amount.length)) {
            throw ParseException("Parse error", parsePosition.errorIndex)
        }

        return parsedAmount
    }

    fun evaluate(expressionString: String?): BigDecimal? {
        if (expressionString.isNullOrEmpty()) {
            return null
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return evaluate26(expressionString)
        }
        return evaluate16(expressionString)
    }

    private fun evaluate16(expressionString: String): BigDecimal? {
        val builder = ExpressionBuilder(expressionString)

        try {
            val expression = builder.build()
            if (expression != null && expression.validate().isValid) {
                return BigDecimal.valueOf(expression.evaluate())
            }
        } catch (e: Exception) {
            Timber.w(e, "Invalid expression: %s", expressionString)
        }
        return null
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun evaluate26(expressionString: String): BigDecimal? {
        val expression = com.ezylang.evalex.Expression(expressionString)
        try {
            val value = expression.evaluate()
            if (value != null && value.isNumberValue) {
                return value.numberValue
            }
        } catch (e: Exception) {
            Timber.w(e, "Invalid expression: %s", expressionString)
        }
        return null
    }
}