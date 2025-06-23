package org.gnucash.android.model

import org.gnucash.android.math.numberOfTrailingZeros
import org.gnucash.android.util.TimestampHelper
import java.math.BigDecimal
import java.math.MathContext
import java.sql.Timestamp
import java.text.DecimalFormat
import java.text.NumberFormat
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract
import kotlin.math.max

/**
 * Model for commodity prices
 */
class Price : BaseModel {
    var commodity: Commodity
    var currency: Commodity
    var date: Timestamp = TimestampHelper.getTimestampFromNow()
    var source: String? = null
    var type: PriceType = PriceType.Unknown

    constructor() : this(Commodity.DEFAULT_COMMODITY, Commodity.DEFAULT_COMMODITY)

    /**
     * Create new instance with the GUIDs of the commodities
     *
     * @param commodity1 the origin commodity
     * @param commodity2 the target commodity
     */
    constructor(commodity1: Commodity, commodity2: Commodity) {
        this.commodity = commodity1
        this.currency = commodity2
    }

    /**
     * Create new instance with the GUIDs of the commodities and the specified exchange rate.
     *
     * @param commodity1 the origin commodity
     * @param commodity2 the target commodity
     * @param exchangeRate  exchange rate between the commodities
     */
    constructor(commodity1: Commodity, commodity2: Commodity, exchangeRate: BigDecimal) :
            this(commodity1, commodity2) {
        setExchangeRate(exchangeRate)
    }

    /**
     * Create new instance with the GUIDs of the commodities and the specified exchange rate.
     *
     * @param commodity1 the origin commodity
     * @param commodity2 the target commodity
     * @param exchangeRateNumerator  exchange rate numerator between the commodities
     * @param exchangeRateDenominator  exchange rate denominator between the commodities
     */
    constructor(
        commodity1: Commodity,
        commodity2: Commodity,
        exchangeRateNumerator: Long,
        exchangeRateDenominator: Long
    ) :
            this(commodity1, commodity2) {
        setExchangeRate(exchangeRateNumerator, exchangeRateDenominator)
    }

    private var _valueNum = 0L
    var valueNum: Long
        get() = _valueNum
        set(value) {
            _valueNum = value
            reduce(value, valueDenom)
        }
    private var _valueDenom = 1L
    var valueDenom: Long
        get() = _valueDenom
        set(value) {
            _valueDenom = value
            reduce(valueNum, value)
        }

    private fun reduce(priceNum: Long, priceDenom: Long) {
        var valueNum = priceNum
        var valueDenom = priceDenom
        var isModified = false
        if (valueDenom < 0) {
            valueDenom = -valueDenom
            valueNum = -valueNum
            isModified = true
        }
        if (valueDenom != 0L && valueNum != 0L) {
            var num1 = valueNum
            if (num1 < 0) {
                num1 = -num1
            }
            var num2 = valueDenom
            var commonDivisor: Long = 1
            while (true) {
                var r = num1 % num2
                if (r == 0L) {
                    commonDivisor = num2
                    break
                }
                num1 = r
                r = num2 % num1
                if (r == 0L) {
                    commonDivisor = num1
                    break
                }
                num2 = r
            }
            valueNum /= commonDivisor
            valueDenom /= commonDivisor
            isModified = true
        }
        if (isModified) {
            _valueNum = valueNum
            _valueDenom = valueDenom
        }
    }

    /**
     * Returns the exchange rate as a string formatted with the default locale.
     *
     * It will have up to 6 decimal places.
     *
     * Example: "0.123456"
     */
    override fun toString(): String {
        val numerator = BigDecimal(valueNum)
        val denominator = BigDecimal(valueDenom)
        val precision = currency.smallestFractionDigits
        val formatter = (NumberFormat.getNumberInstance() as DecimalFormat).apply {
            maximumFractionDigits = precision
        }
        return formatter.format(numerator.divide(denominator, MathContext.DECIMAL64))
    }

    fun toBigDecimal(): BigDecimal {
        val denominator = BigDecimal.valueOf(valueDenom)
        val scale = max(denominator.numberOfTrailingZeros, commodity.smallestFractionDigits)
        return toBigDecimal(scale)
    }

    fun toBigDecimal(scale: Int): BigDecimal {
        val numerator = BigDecimal.valueOf(valueNum)
        val denominator = BigDecimal.valueOf(valueDenom)
        return numerator.divide(denominator, scale, BigDecimal.ROUND_HALF_EVEN)
    }

    val commodityUID: String get() = commodity.uid
    val currencyUID: String get() = currency.uid

    fun setExchangeRate(rate: BigDecimal) {
        // Store 0.1234 as 1234/10000
        valueNum = rate.unscaledValue().toLong()
        valueDenom = BigDecimal.ONE.scaleByPowerOfTen(rate.scale()).toLong()
    }

    fun setExchangeRate(numerator: Long, denominator: Long) {
        // Store 0.1234 as 1234/10000
        valueNum = numerator
        valueDenom = denominator
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Price) return false
        return this.commodity.equals(other.commodity)
                && this.currency.equals(other.currency)
                && this.valueNum.equals(other.valueNum)
                && this.valueDenom.equals(other.valueDenom)
    }

    companion object {
        /**
         * String indicating that the price was provided by the user
         */
        const val SOURCE_USER = "user:xfer-dialog"

        /**
         * The price was provided by the Finance::Quote module.
         */
        const val SOURCE_QUOTE = "Finance::Quote"
    }
}

@OptIn(ExperimentalContracts::class)
fun Price?.isNullOrEmpty(): Boolean {
    contract {
        returns(false) implies (this@isNullOrEmpty != null)
    }

    return this == null || this.valueNum <= 0 || this.valueDenom <= 0
}