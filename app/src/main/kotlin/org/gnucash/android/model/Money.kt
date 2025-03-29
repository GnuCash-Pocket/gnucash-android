/*
 * Copyright (c) 2012 - 2014 Ngewi Fet <ngewif@gmail.com>
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
package org.gnucash.android.model

import android.os.Build
import android.os.Parcel
import android.os.Parcelable
import org.gnucash.android.math.equalsIgnoreScale
import org.gnucash.android.math.isZero
import org.gnucash.android.math.readBigDecimal
import org.gnucash.android.math.toBigDecimal
import timber.log.Timber
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract
import kotlin.math.max

/**
 * Money represents a money amount and a corresponding currency.
 * Money internally uses [BigDecimal] to represent the amounts, which enables it
 * to maintain high precision afforded by BigDecimal. Money objects are immutable and
 * most operations return new Money objects.
 * Money String constructors should not be passed any locale-formatted numbers. Only
 * [Locale.US] is supported e.g. "2.45" will be parsed as 2.45 meanwhile
 * "2,45" will be parsed to 245 although that could be a decimal in [Locale.GERMAN]
 *
 * @author Ngewi Fet<ngewif@gmail.com>
 */
class Money : Number, Comparable<Money>, Parcelable {
    /**
     * Currency of the account
     */
    var commodity: Commodity = Commodity.DEFAULT_COMMODITY
        private set

    /**
     * Amount value held by this object
     */
    private var amount: BigDecimal = BigDecimal.ZERO

    /**
     * Rounding mode to be applied when performing operations
     * Defaults to [RoundingMode.HALF_UP]
     */
    private var roundingMode = RoundingMode.HALF_UP

    /**
     * Creates a new money amount
     *
     * @param amount    Value of the amount
     * @param commodity Commodity of the money
     */
    constructor(amount: BigDecimal, commodity: Commodity) {
        this.amount = amount
        setCommodity(commodity)
    }

    /**
     * Constructs a new money amount given the numerator and denominator of the amount.
     * The rounding mode used for the division is [BigDecimal.ROUND_HALF_EVEN]
     *
     * @param amount    Value of the amount
     * @param currencyCode 3-character currency code string
     */
    constructor(amount: BigDecimal, currencyCode: String) : this(
        amount,
        Commodity.getInstance(currencyCode)
    )

    /**
     * Creates a new money amount
     *
     * @param amount    Value of the amount
     * @param commodity Commodity of the money
     */
    constructor(amount: Double, commodity: Commodity) : this(BigDecimal.valueOf(amount), commodity)

    /**
     * Overloaded constructor.
     * Accepts strings as arguments and parses them to create the Money object
     *
     * @param amount       Numerical value of the Money
     * @param currencyCode Currency code as specified by ISO 4217
     */
    constructor(amount: String?, currencyCode: String) : this(
        BigDecimal(amount),
        currencyCode
    )

    /**
     * Overloaded constructor.
     * Accepts strings as arguments and parses them to create the Money object
     *
     * @param amount    Numerical value of the Money
     * @param commodity Commodity of the money
     */
    constructor(amount: String?, commodity: Commodity) : this(
        BigDecimal(amount),
        commodity
    )

    /**
     * Constructs a new money amount given the numerator and denominator of the amount.
     * The rounding mode used for the division is [BigDecimal.ROUND_HALF_EVEN]
     *
     * @param numerator    Numerator as integer
     * @param denominator  Denominator as integer
     * @param currencyCode 3-character currency code string
     */
    //FIXME beware of 64-bit overflow - only use BigInteger for numerator
    constructor(numerator: Long, denominator: Long, currencyCode: String) : this(
        toBigDecimal(numerator, denominator),
        currencyCode
    )

    /**
     * Constructs a new money amount given the numerator and denominator of the amount.
     * The rounding mode used for the division is [BigDecimal.ROUND_HALF_EVEN]
     *
     * @param numerator    Numerator as integer
     * @param denominator  Denominator as integer
     * @param commodity Commodity of the money
     */
    //FIXME beware of 64-bit overflow - only use BigInteger for numerator
    constructor(numerator: Long, denominator: Long, commodity: Commodity) : this(
        toBigDecimal(numerator, denominator),
        commodity
    )

    /**
     * Constructs a new money amount given the numerator and denominator of the amount.
     * The rounding mode used for the division is [BigDecimal.ROUND_HALF_EVEN]
     *
     * @param numerator    Numerator as integer
     * @param denominator  Denominator as integer
     * @param commodity Commodity of the money
     */
    constructor(numerator: BigInteger, denominator: Long, commodity: Commodity) : this(
        toBigDecimal(numerator, denominator),
        commodity
    )

    /**
     * Copy constructor.
     * Creates a new Money object which is a clone of `money`
     *
     * @param money Money instance to be cloned
     */
    constructor(money: Money) : this(money.toBigDecimal(), money.commodity)

    /**
     * Returns a new `Money` object the currency specified by `currency`
     * and the same value as this one. No value exchange between the currencies is performed.
     *
     * @param commodity [Commodity] to assign to new `Money` object
     * @return [Money] object with same value as current object, but with new `currency`
     */
    fun withCommodity(commodity: Commodity): Money {
        return Money(amount, commodity)
    }

    /**
     * Sets the commodity for the Money
     *
     * No currency conversion is performed
     *
     * @param commodity Commodity instance
     */
    private fun setCommodity(commodity: Commodity) {
        this.commodity = commodity
        amount = amount.setScale(scale, roundingMode)
    }

    /**
     * Sets the commodity for the Money
     *
     * @param currencyCode ISO 4217 currency code
     */
    private fun setCommodity(currencyCode: String) {
        setCommodity(Commodity.getInstance(currencyCode))
    }

    /**
     * Returns the GnuCash format numerator for this amount.
     *
     * Example: Given an amount 32.50$, the numerator will be 3250
     *
     * @return GnuCash numerator for this amount
     */
    //FIXME beware of 64-bit overflow so use BigInteger
    val numerator: Long
        get() = try {
            amount.scaleByPowerOfTen(scale).longValueExact()
        } catch (e: ArithmeticException) {
            val msg = "Currency " + commodity.currencyCode +
                    " with scale " + scale +
                    " has amount " + amount
            Timber.e(e, msg)
            throw ArithmeticException(msg)
        }

    /**
     * Returns the GnuCash amount format denominator for this amount
     *
     * The denominator is 10 raised to the power of number of fractional digits in the currency
     *
     * @return GnuCash format denominator
     */
    val denominator: Long
        get() = BigDecimal.ONE.scaleByPowerOfTen(amount.scale()).longValueExact()

    /**
     * Returns the scale (precision) used for the decimal places of this amount.
     *
     * The scale used depends on the commodity
     *
     * @return Scale of amount as integer
     */
    private val scale: Int
        get() {
            val s = if (commodity.isTemplate) amount.scale() else commodity.smallestFractionDigits
            return max(0, s)
        }

    /**
     * Returns the amount represented by this Money object
     *
     * The scale and rounding mode of the returned value are set to that of this Money object
     *
     * @return [BigDecimal] valure of amount in object
     */
    fun asBigDecimal(): BigDecimal {
        return amount.setScale(scale, roundingMode)
    }

    fun toBigDecimal(): BigDecimal {
        return asBigDecimal()
    }

    /**
     * Returns the amount this object
     *
     * @return Double value of the amount in the object
     */
    override fun toDouble(): Double {
        return amount.toDouble()
    }

    override fun toByte(): Byte {
        return amount.toByte()
    }

    override fun toChar(): Char {
        return amount.toChar()
    }

    override fun toFloat(): Float {
        return amount.toFloat()
    }

    override fun toInt(): Int {
        return amount.toInt()
    }

    override fun toLong(): Long {
        return amount.toLong()
    }

    override fun toShort(): Short {
        return amount.toShort()
    }

    /**
     * An alias for [.toPlainString]
     *
     * @return Money formatted as a string (excludes the currency)
     */
    fun asString(): String {
        return toPlainString()
    }

    /**
     * Returns a string representation of the Money object formatted according to
     * the `locale` and includes the currency symbol.
     * The output precision is limited to the number of fractional digits supported by the currency
     *
     * @param locale Locale to use when formatting the object. Defaults to Locale.getDefault().
     * @return String containing formatted Money representation
     */
    @JvmOverloads
    fun formattedString(locale: Locale = Locale.getDefault()): String {
        if (commodity.isTemplate) return amount.toPlainString()
        val precision = commodity.smallestFractionDigits
        val formatter = (NumberFormat.getCurrencyInstance(locale) as DecimalFormat).apply {
            if (commodity.isCurrency) {
                try {
                    currency = commodity.currency
                } catch (ignore: IllegalArgumentException) {
                }
            }
            decimalFormatSymbols = decimalFormatSymbols.apply { currencySymbol = commodity.symbol }
            minimumFractionDigits = precision
            maximumFractionDigits = precision
        }
        return formatter.format(amount)
    }

    /**
     * Returns a string representation of the Money object formatted according to
     * the `locale` without the currency symbol.
     * The output precision is limited to the number of fractional digits supported by the currency
     *
     * @param locale Locale to use when formatting the object. Defaults to Locale.getDefault().
     * @return String containing formatted Money representation
     */
    @JvmOverloads
    fun formattedStringWithoutSymbol(
        locale: Locale = Locale.getDefault(),
        withGrouping: Boolean = true
    ): String {
        val precision = commodity.smallestFractionDigits
        val format = NumberFormat.getNumberInstance(locale).apply {
            minimumFractionDigits = precision
            maximumFractionDigits = precision
            isGroupingUsed = withGrouping
        }
        return format.format(amount)
    }

    /**
     * Returns a new Money object whose amount is the negated value of this object amount.
     * The original `Money` object remains unchanged.
     *
     * @return Negated `Money` object
     */
    operator fun unaryMinus(): Money {
        return Money(amount.negate(), commodity)
    }

    /**
     * Returns a new `Money` object whose value is the sum of the values of
     * this object and `addend`.
     *
     * @param addend Second operand in the addition.
     * @return Money object whose value is the sum of this object and `money`
     * @throws CurrencyMismatchException if the `Money` objects to be added have different Currencies
     */
    @Throws(CurrencyMismatchException::class)
    operator fun plus(addend: Money): Money {
        if (isAmountZero) return addend
        if (addend.isAmountZero) return this
        if (commodity != addend.commodity) throw CurrencyMismatchException(
            commodity,
            addend.commodity
        )
        val amount = amount.add(addend.amount)
        return Money(amount, commodity)
    }

    operator fun plus(rhs: Int): Money {
        return plus(BigDecimal(rhs))
    }

    operator fun plus(rhs: Long): Money {
        return plus(BigDecimal(rhs))
    }

    operator fun plus(rhs: Double): Money {
        return plus(BigDecimal(rhs))
    }

    operator fun plus(rhs: BigDecimal): Money {
        return Money(amount.add(rhs), commodity)
    }

    /**
     * Returns a new `Money` object whose value is the difference of the values of
     * this object and `subtrahend`.
     * This object is the minuend and the parameter is the subtrahend
     *
     * @param subtrahend Second operand in the subtraction.
     * @return Money object whose value is the difference of this object and `subtrahend`
     * @throws CurrencyMismatchException if the `Money` objects to be added have different Currencies
     */
    @Throws(CurrencyMismatchException::class)
    operator fun minus(subtrahend: Money): Money {
        if (isAmountZero) return -subtrahend
        if (subtrahend.isAmountZero) return this
        if (commodity != subtrahend.commodity) throw CurrencyMismatchException(
            commodity,
            subtrahend.commodity
        )
        val amount = amount.subtract(subtrahend.amount)
        return Money(amount, commodity)
    }

    operator fun minus(rhs: Int): Money {
        return minus(BigDecimal(rhs))
    }

    operator fun minus(rhs: Long): Money {
        return minus(BigDecimal(rhs))
    }

    operator fun minus(rhs: Double): Money {
        return minus(BigDecimal(rhs))
    }

    operator fun minus(rhs: BigDecimal): Money {
        return Money(amount.subtract(rhs), commodity)
    }

    /**
     * Returns a new `Money` object whose value is the quotient of the values of
     * this object and `divisor`.
     * This object is the dividend and `divisor` is the divisor
     *
     * This method uses the rounding mode [BigDecimal.ROUND_HALF_EVEN]
     *
     * @param divisor Second operand in the division.
     * @return Money object whose value is the quotient of this object and `divisor`
     * @throws CurrencyMismatchException if the `Money` objects to be added have different Currencies
     */
    @Throws(CurrencyMismatchException::class)
    operator fun div(divisor: Money): Money {
        if (isAmountZero) return createZeroInstance(divisor.commodity)
        if (commodity != divisor.commodity) throw CurrencyMismatchException(
            commodity,
            divisor.commodity
        )
        val amount = amount.divide(divisor.amount, scale, roundingMode)
        return Money(amount, commodity)
    }

    /**
     * Returns a new `Money` object whose value is the quotient of the division of this objects
     * value by the factor `divisor`
     *
     * @param divisor Second operand in the addition.
     * @return Money object whose value is the quotient of this object and `divisor`
     */
    operator fun div(divisor: Int): Money {
        return div(BigDecimal(divisor))
    }

    operator fun div(divisor: Long): Money {
        return div(BigDecimal(divisor))
    }

    operator fun div(divisor: Double): Money {
        return div(BigDecimal(divisor))
    }

    operator fun div(divisor: BigDecimal): Money {
        val amount = amount.divide(divisor, scale, roundingMode)
        return Money(amount, commodity)
    }

    /**
     * Returns a new `Money` object whose value is the product of the values of
     * this object and `money`.
     *
     * @param factor Second operand in the multiplication.
     * @return Money object whose value is the product of this object and `money`
     * @throws CurrencyMismatchException if the `Money` objects to be added have different Currencies
     */
    @Throws(CurrencyMismatchException::class)
    operator fun times(factor: Money): Money {
        if (isAmountZero) return this
        if (factor.isAmountZero) return factor
        if (commodity != factor.commodity) throw CurrencyMismatchException(
            commodity,
            factor.commodity
        )
        val amount = amount.multiply(factor.amount)
        return Money(amount, commodity)
    }

    /**
     * Returns a new `Money` object whose value is the product of this object
     * and the factor `multiplier`
     *
     * @param factor Factor to multiply the amount by.
     * @return Money object whose value is the product of this objects values and `multiplier`
     */
    operator fun times(factor: BigDecimal): Money {
        return Money(amount.multiply(factor), commodity)
    }

    /**
     * Returns a new `Money` object whose value is the product of this object
     * and the factor `multiplier`
     *
     * The currency of the returned object is the same as the current object
     *
     * @param factor Factor to multiply the amount by.
     * @return Money object whose value is the product of this objects values and `multiplier`
     */
    operator fun times(factor: Int): Money {
        return times(BigDecimal(factor))
    }

    operator fun times(factor: Long): Money {
        return times(BigDecimal(factor))
    }

    operator fun times(factor: Double): Money {
        return times(BigDecimal(factor))
    }

    operator fun times(price: Price): Money {
        return times(price.toBigDecimal())
    }

    /**
     * Returns true if the amount held by this Money object is negative
     *
     * @return `true` if the amount is negative, `false` otherwise.
     */
    val isNegative: Boolean
        get() = amount.compareTo(BigDecimal.ZERO) < 0

    /**
     * Returns the string representation of the amount (without currency) of the Money object.
     *
     *
     * This string is not locale-formatted. The decimal operator is a period (.)
     * For a locale-formatted version, see the method `formattedStringWithoutSymbol()`.
     *
     * @return String representation of the amount (without currency) of the Money object
     */
    fun toPlainString(): String {
        return amount.toPlainString()
    }

    /**
     * Returns the string representation of the Money object (value + currency) formatted according
     * to the default locale
     *
     * @return String representation of the amount formatted with default locale
     */
    override fun toString(): String {
        return formattedString()
    }

    override fun hashCode(): Int {
        var result = amount.hashCode()
        result = 31 * result + commodity.hashCode()
        return result
    }

    /**
     * Two Money objects are only equal if their amount (value) and currencies are equal
     *
     * @param other Object to compare with
     * @return `true` if the objects are equal, `false` otherwise
     */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null) return false
        if (javaClass != other.javaClass) return false
        val that = other as Money
        if (amount != that.amount && !amount.equalsIgnoreScale(that.amount)) return false
        return commodity == that.commodity
    }

    @Throws(CurrencyMismatchException::class)
    override fun compareTo(other: Money): Int {
        if (commodity != other.commodity) throw CurrencyMismatchException(
            commodity,
            other.commodity
        )
        return amount.compareTo(other.amount)
    }

    /**
     * Returns a new instance of [Money] object with the absolute value of the current object
     *
     * @return Money object with absolute value of this instance
     */
    fun abs(): Money {
        return Money(amount.abs(), commodity)
    }

    /**
     * Checks if the value of this amount is exactly equal to zero.
     *
     * @return `true` if this money amount is zero, `false` otherwise
     */
    val isAmountZero: Boolean
        get() = amount.isZero

    constructor(parcel: Parcel) {
        amount = parcel.readBigDecimal()!!
        setCommodity(parcel.readCommodity()!!)
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeSerializable(amount)
        dest.writeCommodity(commodity, flags)
    }

    override fun describeContents(): Int = 0

    inner class CurrencyMismatchException(s: String) : IllegalArgumentException(s) {
        constructor() : this("Cannot perform operation on Money instances with different currencies")

        constructor(
            commodity1: Commodity,
            commodity2: Commodity
        ) : this("Cannot perform operation on Money instances with different currencies: $commodity1 ~ $commodity2")
    }

    companion object {
        /**
         * Creates a new Money instance with 0 amount and the `currencyCode`
         *
         * @param currencyCode Currency to use for this money instance
         * @return Money object with value 0 and currency `currencyCode`
         */
        @JvmStatic
        fun createZeroInstance(currencyCode: String): Money {
            val commodity = Commodity.getInstance(currencyCode)
            return createZeroInstance(commodity)
        }

        /**
         * Creates a new Money instance with 0 amount and the `currencyCode`
         *
         * @param commodity Commodity to use for this money instance
         * @return Money object with value 0 and commodity
         */
        @JvmStatic
        fun createZeroInstance(commodity: Commodity): Money {
            return Money(BigDecimal.ZERO, commodity)
        }

        @JvmField
        val CREATOR = object : Parcelable.Creator<Money> {
            override fun createFromParcel(parcel: Parcel): Money {
                return Money(parcel)
            }

            override fun newArray(size: Int): Array<Money?> {
                return arrayOfNulls(size)
            }
        }
    }
}

fun Parcel.writeMoney(value: Money?, flags: Int) {
    writeParcelable(value, flags)
}

fun Parcel.readMoney(): Money? {
    val clazz = Money::class.java
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        readParcelable(clazz.classLoader, clazz)
    } else {
        @Suppress("DEPRECATION")
        readParcelable(clazz.classLoader)
    }
}

@OptIn(ExperimentalContracts::class)
fun Money?.isNullOrZero(): Boolean {
    contract {
        returns(false) implies (this@isNullOrZero != null)
    }

    return this == null || this.isAmountZero
}