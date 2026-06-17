package org.gnucash.android.model

import org.gnucash.android.math.numberOfTrailingZeros
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.NumberFormat
import java.util.Locale
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract
import kotlin.math.max

/**
 * Model for commodity prices
 */
class Price : BaseModel {
    var security: Commodity
    var currency: Commodity
    var date: Long = System.currentTimeMillis()
    var source: PriceSource? = null
    var type: Type = Type.Unknown

    constructor() : this(Commodity.DEFAULT_COMMODITY, Commodity.DEFAULT_COMMODITY)

    /**
     * Create new instance with the GUIDs of the commodities
     *
     * @param security the origin commodity
     * @param currency the target commodity
     */
    constructor(security: Commodity, currency: Commodity) {
        this.security = security
        this.currency = currency
    }

    /**
     * Create new instance with the GUIDs of the commodities and the specified exchange rate.
     *
     * @param security the origin commodity
     * @param currency the target commodity
     * @param exchangeRate  exchange rate between the commodities
     */
    constructor(security: Commodity, currency: Commodity, exchangeRate: BigDecimal) :
            this(security, currency) {
        setExchangeRate(exchangeRate)
    }

    /**
     * Create new instance with the GUIDs of the commodities and the specified exchange rate.
     *
     * @param security the origin commodity
     * @param currency the target commodity
     * @param exchangeRateNumerator  exchange rate numerator between the commodities
     * @param exchangeRateDenominator  exchange rate denominator between the commodities
     */
    constructor(
        security: Commodity,
        currency: Commodity,
        exchangeRateNumerator: Long,
        exchangeRateDenominator: Long
    ) : this(security, currency) {
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
            var commonDivisor = 1L
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
        val amount = formatter.format(numerator.divide(denominator, MathContext.DECIMAL64))
        return "$security/$currency=$amount"
    }

    fun toBigDecimal(): BigDecimal {
        val denominator = BigDecimal.valueOf(valueDenom)
        val scale = max(denominator.numberOfTrailingZeros, security.smallestFractionDigits)
        return toBigDecimal(scale)
    }

    fun toBigDecimal(scale: Int): BigDecimal {
        val numerator = BigDecimal.valueOf(valueNum)
        val denominator = BigDecimal.valueOf(valueDenom)
        return numerator.divide(denominator, scale, RoundingMode.HALF_UP)
    }

    val securityUID: String get() = security.uid
    val currencyUID: String get() = currency.uid

    fun setExchangeRate(rate: BigDecimal) {
        // Store 0.1234 as 1234/10000
        val scale = MathContext.DECIMAL64.precision
        val numerator = rate.setScale(scale, RoundingMode.HALF_UP).unscaledValue().toLong()
        val denominator = BigDecimal.ONE.scaleByPowerOfTen(scale).toLong()
        setExchangeRate(numerator, denominator)
    }

    fun setExchangeRate(numerator: Long, denominator: Long) {
        // Store 0.1234 as 1234/10000
        this._valueNum = numerator
        this._valueDenom = denominator
        reduce(numerator, denominator)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Price) return false
        return this.security == other.security
                && this.currency == other.currency
                && this.type == other.type
                && this.valueNum == other.valueNum
                && this.valueDenom == other.valueDenom
                && this.date == other.date
    }

    /**
     * Return a newly-allocated price that's the inverse of the given price, p.
     *
     * Inverse means that the commodity and currency are swapped and the value is the numeric
     * inverse of the original's.
     * The source is set to PRICE_SOURCE_TEMP to prevent it being saved in the pricedb.
     */
    fun invert(): Price {
        // swap numerator and denominator
        val priceOld = this
        return Price(currency, security, valueDenom, valueNum).apply {
            date = priceOld.date
            source = PriceSource.PRICE_SOURCE_TEMP
            type = priceOld.type
        }
    }

    fun copy(
        id: Long = this.id,
        uid: String? = null,
        security: Commodity? = null,
        currency: Commodity? = null,
        source: PriceSource? = null,
        type: Type? = null,
        date: Long? = null,
        rate: BigDecimal? = null
    ): Price {
        val priceOld = this
        val clone = Price(security ?: priceOld.security, currency ?: priceOld.currency)
        clone.id = id
        clone.setUID(uid ?: priceOld.uid)
        clone.date = date ?: priceOld.date
        clone.source = source ?: priceOld.source
        clone.type = type ?: priceOld.type
        clone._valueNum = priceOld._valueNum
        clone._valueDenom = priceOld._valueDenom
        if (rate != null) {
            clone.setExchangeRate(rate)
        }
        return clone
    }

    /**
     * One of:
     * Bid (the market buying price),
     * Ask (the market selling price),
     * Last (the last transaction price),
     * Net Asset Value (mutual fund price per share, NAV for short),
     * or Unknown.
     *
     * Stocks and currencies will usually give their quotes as one of bid, ask or last.
     * Mutual funds are often given as net asset value.
     * For other commodities, simply choose Unknown.
     * This option is for informational purposes only, it is not used by GnuCash.
     */
    enum class Type(val value: String) {
        /** the market buying price */
        Bid("bid"),

        /** Ask (the market selling price) */
        Ask("ask"),

        /** Last (the last transaction price) */
        Last("last"),

        /** Net Asset Value (mutual fund price per share, NAV for short) */
        NetAssetValue("nav"),

        /** 'Transaction' is set when the price is created from an amount and value in a Split
         *  and is not available for users to set via the GUI. */
        Transaction("transaction"),

        Unknown("unknown");

        override fun toString(): String {
            return value
        }

        companion object {
            private val values = Type.entries

            fun of(key: String?): Type {
                val value = key?.lowercase(Locale.ROOT) ?: return Unknown
                return values.firstOrNull { it.value == value } ?: Unknown
            }
        }
    }
}

@OptIn(ExperimentalContracts::class)
fun Price?.isNullOrEmpty(): Boolean {
    contract {
        returns(false) implies (this@isNullOrEmpty != null)
    }

    return this == null || this.valueNum <= 0 || this.valueDenom <= 0
}