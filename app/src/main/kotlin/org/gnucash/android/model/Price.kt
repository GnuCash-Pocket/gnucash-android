package org.gnucash.android.model

import org.gnucash.android.util.TimestampHelper
import java.math.BigDecimal
import java.math.MathContext
import java.sql.Timestamp
import java.text.DecimalFormat
import java.text.NumberFormat

/**
 * Model for commodity prices
 */
class Price : BaseModel {
    var commodity: Commodity? = null
    var currency: Commodity? = null
    var date: Timestamp
    var source: String? = null
    var type: String? = null

    constructor() : this(null, null)

    /**
     * Create new instance with the GUIDs of the commodities
     *
     * @param commodity the origin commodity
     * @param currency  the target commodity
     */
    constructor(commodity: Commodity?, currency: Commodity?) {
        this.commodity = commodity
        this.currency = currency
        date = TimestampHelper.getTimestampFromNow()
    }

    /**
     * Create new instance with the GUIDs of the commodities and the specified exchange rate.
     *
     * @param commodity1 the origin commodity
     * @param commodity2 the target commodity
     * @param exchangeRate  exchange rate between the commodities
     */
    constructor(commodity1: Commodity?, commodity2: Commodity?, exchangeRate: BigDecimal) :
            this(commodity1, commodity2) {
        // Store 0.1234 as 1234/10000
        valueNum = exchangeRate.unscaledValue().toLong()
        valueDenom = BigDecimal.ONE.scaleByPowerOfTen(exchangeRate.scale()).toLong()
    }

    private var _valueNum = 0L
    var valueNum: Long
        get() = _valueNum
        set(value) {
            _valueNum = value
            reduce(value, valueDenom)
        }
    private var _valueDenom = 0L
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
        val formatter = NumberFormat.getNumberInstance() as DecimalFormat
        formatter.maximumFractionDigits = 6
        return formatter.format(numerator.divide(denominator, MathContext.DECIMAL32))
    }

    fun toBigDecimal(): BigDecimal {
        val numerator = BigDecimal(valueNum)
        val denominator = BigDecimal(valueDenom)

        return numerator.divide(
            denominator,
            currency?.smallestFractionDigits ?: 100,
            BigDecimal.ROUND_HALF_EVEN
        )
    }

    val commodityUID: String? get() = commodity?.uID
    val currencyUID: String? get() = currency?.uID

    companion object {
        /**
         * String indicating that the price was provided by the user
         */
        const val SOURCE_USER = "user:xfer-dialog"
    }
}
