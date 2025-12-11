package org.gnucash.android.model

import java.text.ParseException

data class Numeric(
    val numerator: Long,
    val denominator: Long
) {
    override fun toString(): String {
        return format()
    }

    fun format(): String {
        return format(numerator, denominator)
    }

    fun reduce(): Numeric {
        var n = numerator
        var d = denominator
        if ((n >= 10) && (d >= 10)) {
            var n10 = n % 10L
            var d10 = d % 10L
            while ((n10 == 0L) && (d10 == 0L) && (n >= 10) && (d >= 10)) {
                n /= 10
                d /= 10
                n10 = n % 10L
                d10 = d % 10L
            }
        }
        return Numeric(n, d)
    }

    companion object {
        /**
         * Strips formatting from a currency string.
         * All non-digit information is removed, but the sign is preserved.
         *
         * @param s String to be stripped
         * @return Stripped string with all non-digits removed
         */
        fun stripCurrencyFormatting(s: String): String {
            if (s.isEmpty()) return s
            //remove all currency formatting and anything else which is not a number
            val sign = s.trim()[0]
            var stripped = s.trim().replace("\\D*".toRegex(), "")
            if (stripped.isEmpty()) return ""
            if (sign == '+' || sign == '-') {
                stripped = sign + stripped
            }
            return stripped
        }

        @Throws(ParseException::class)
        fun parse(value: String): Numeric {
            val index = value.indexOf('/')
            if (index < 0) {
                throw ParseException("Cannot parse numeric: $value", 0)
            }
            val numerator = stripCurrencyFormatting(value.take(index))
            val denominator = stripCurrencyFormatting(value.substring(index + 1))
            return Numeric(numerator.toLong(), denominator.toLong())
        }

        fun format(numerator: Long, denominator: Long): String {
            if (numerator == 0L) return "0/1"
            if (denominator == 0L) return "1/0"
            return "$numerator/$denominator"
        }

        fun format(money: Money): String {
            return format(money.numerator, money.denominator)
        }
    }
}
