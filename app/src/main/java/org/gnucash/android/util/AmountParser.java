package org.gnucash.android.util;

import android.annotation.TargetApi;
import android.os.Build;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import net.objecthunter.exp4j.Expression;
import net.objecthunter.exp4j.ExpressionBuilder;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.text.ParseException;
import java.text.ParsePosition;

import timber.log.Timber;

/**
 * Parses amounts as String into BigDecimal.
 */
public class AmountParser {
    /**
     * Parses {@code amount} and returns it as a BigDecimal.
     *
     * @param amount String with the amount to parse.
     * @return The amount parsed as a BigDecimal.
     * @throws ParseException if the full string couldn't be parsed as an amount.
     */
    public static BigDecimal parse(String amount) throws ParseException {
        if (amount == null || amount.isEmpty()) {
            throw new ParseException("Parse error", 0);
        }
        DecimalFormat formatter = (DecimalFormat) NumberFormat.getNumberInstance();
        formatter.setParseBigDecimal(true);
        ParsePosition parsePosition = new ParsePosition(0);
        BigDecimal parsedAmount = (BigDecimal) formatter.parse(amount, parsePosition);

        // Ensure any mistyping by the user is caught instead of partially parsed
        if ((parsedAmount == null) || (parsePosition.getIndex() < amount.length()))
            throw new ParseException("Parse error", parsePosition.getErrorIndex());

        return parsedAmount;
    }

    @Nullable
    public static BigDecimal evaluate(@Nullable String expressionString) {
        if (TextUtils.isEmpty(expressionString)) {
            return null;
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return evaluate16(expressionString);
        }
        return evaluate26(expressionString);
    }

    @Nullable
    private static BigDecimal evaluate16(@NonNull String expressionString) {
        ExpressionBuilder builder = new ExpressionBuilder(expressionString);

        try {
            Expression expression = builder.build();
            if (expression != null && expression.validate().isValid()) {
                return new BigDecimal(expression.evaluate());
            }
        } catch (Exception e) {
            Timber.w(e, "Invalid amount: %s", expressionString);
        }
        return null;
    }

    @TargetApi(Build.VERSION_CODES.O)
    @Nullable
    private static BigDecimal evaluate26(@NonNull String expressionString) {
        com.ezylang.evalex.Expression expression = new com.ezylang.evalex.Expression(expressionString);
        try {
            com.ezylang.evalex.data.EvaluationValue value = expression.evaluate();
            if (value != null && value.isNumberValue()) {
                return value.getNumberValue();
            }
        } catch (Exception e) {
            Timber.w(e, "Invalid amount: %s", expressionString);
        }
        return null;
    }
}