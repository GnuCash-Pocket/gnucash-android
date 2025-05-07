/*
 * Copyright (c) 2015 Ngewi Fet <ngewif@gmail.com>
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
import org.gnucash.android.db.adapter.CommoditiesDbAdapter
import org.gnucash.android.math.numberOfTrailingZeros
import java.util.TimeZone

/**
 * Commodities are the currencies used in the application.
 * At the moment only ISO4217 currencies are supported
 */
class Commodity(
    /**
     * Official full name of the currency
     */
    var fullname: String?,
    /**
     * This is the currency code for ISO4217 currencies
     */
    var mnemonic: String,
    /**
     * The smallest fraction supported by the commodity as a power of 10.
     *
     * i.e. for commodities with no fractions, 1 is returned, for commodities with 2 fractions, 100 is returned
     *
     * The fraction is a power of 10. So commodities with 2 fraction digits, have fraction of 10^2 = 100.<br />
     * If the parameter is any other value, a default fraction of 100 will be set
     */
    var smallestFraction: Int = DEFAULT_SMALLEST_FRACTION
) : BaseModel(), Parcelable {

    var namespace = COMMODITY_CURRENCY
        set(value) {
            var ns = value
            if (value == COMMODITY_ISO4217) ns = COMMODITY_CURRENCY
            field = ns
        }

    val isCurrency: Boolean
        get() = (COMMODITY_CURRENCY == namespace || COMMODITY_ISO4217 == namespace)

    val isTemplate: Boolean
        get() = (TEMPLATE == namespace) || (TEMPLATE == mnemonic) || (TEMPLATE == cusip)

    /**
     * Returns the mnemonic, or currency code for ISO4217 currencies
     *
     * @return Mnemonic of the commodity
     */
    var cusip: String? = null

    var localSymbol: String? = ""

    val quoteFlag: Boolean get() = !quoteSource.isNullOrEmpty()
    var quoteSource: String? = null
    var quoteTimeZone: TimeZone? = null

    /**
     * Alias for [.getMnemonic]
     *
     * @return ISO 4217 code for this commodity
     */
    val currencyCode: String
        get() = mnemonic

    /**
     * Returns the symbol for this commodity.
     *
     * Normally this would be the local symbol, but in it's absence, the mnemonic (currency code)
     * is returned.
     *
     * @return
     */
    val symbol: String
        get() = if (localSymbol.isNullOrEmpty()) mnemonic else localSymbol!!

    /**
     * Returns the (minimum) number of digits that this commodity supports in its fractional part
     *
     * For any unsupported values for the smallest fraction, a default value of 2 is returned.
     * Supported values for the smallest fraction are powers of 10 i.e. 1, 10, 100 etc
     *
     * @return Number of digits in fraction
     * @see smallestFraction
     */
    val smallestFractionDigits: Int
        get() = smallestFraction.numberOfTrailingZeros

    constructor(parcel: Parcel) : this(
        parcel.readString(),
        parcel.readString()!!,
        parcel.readInt()
    ) {
        setUID(parcel.readString())
        namespace = parcel.readString()!!
    }

    override fun describeContents(): Int = 0

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeString(fullname)
        dest.writeString(mnemonic)
        dest.writeInt(smallestFraction)
        dest.writeString(uid)
        dest.writeString(namespace)
    }

    /**
     * Returns the full name of the currency, or the currency code if there is no full name
     * @return String representation of the commodity
     */
    override fun toString(): String {
        return "${namespace}::${mnemonic}"
    }

    /**
     * Overrides [BaseModel.equals] to compare only the currency codes of the commodity.
     *
     * Two commodities are considered equal if they have the same currency code
     *
     * @param o Commodity instance to compare
     * @return `true` if both instances have same currency code, `false` otherwise
     */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false
        val that = other as Commodity
        return (this.mnemonic == that.mnemonic) && (this.namespace == that.namespace)
    }

    override fun hashCode(): Int {
        return mnemonic.hashCode()
    }

    fun setQuoteTimeZone(id: String?) {
        if (id.isNullOrEmpty()) {
            this.quoteTimeZone = null
        } else {
            this.quoteTimeZone = TimeZone.getTimeZone(id)
        }
    }

    fun getQuoteTimeZoneId(): String? = quoteTimeZone?.id

    companion object {
        const val COMMODITY_CURRENCY = "CURRENCY"
        const val COMMODITY_ISO4217 = "ISO4217"
        const val TEMPLATE = "template"
        const val DEFAULT_SMALLEST_FRACTION = 100

        /**
         * ISO 4217 currency code for "No Currency"
         */
        const val NO_CURRENCY_CODE = "XXX"

        @JvmField
        var USD = Commodity("US Dollar", "USD", DEFAULT_SMALLEST_FRACTION)

        @JvmField
        var EUR = Commodity("Euro", "EUR", DEFAULT_SMALLEST_FRACTION)

        @JvmField
        var GBP = Commodity("Pound Sterling", "GBP", DEFAULT_SMALLEST_FRACTION)

        @JvmField
        var CHF = Commodity("Swiss Franc", "CHF", DEFAULT_SMALLEST_FRACTION)

        @JvmField
        var CAD = Commodity("Canadian Dollar", "CAD", DEFAULT_SMALLEST_FRACTION)

        @JvmField
        var JPY = Commodity("Yen", "JPY", 1)

        @JvmField
        var AUD = Commodity("Australian Dollar", "AUD", DEFAULT_SMALLEST_FRACTION)

        /**
         * Default commodity for device locale
         *
         * This value is set when a new application instance is created in [GnuCashApplication.onCreate].
         * The value initialized here is just a placeholder for unit tests
         */
        @JvmField
        var DEFAULT_COMMODITY = Commodity(
            USD.fullname,
            USD.mnemonic,
            USD.smallestFraction
        ) //this value is a stub. Will be overwritten when the app is launched

        @JvmField
        val template = Commodity(TEMPLATE, TEMPLATE, 1).apply {
            namespace = TEMPLATE
            cusip = TEMPLATE
            localSymbol = TEMPLATE
        }

        /**
         * Returns an instance of commodity for the specified currencyCode
         *
         * @param currencyCode ISO 4217 currency code (3-letter)
         * @return the commodity, or default commodity.
         */
        @JvmStatic
        fun getInstance(currencyCode: String?): Commodity {
            if (currencyCode.isNullOrEmpty()) {
                return DEFAULT_COMMODITY
            }
            when (currencyCode) {
                "AUD" -> return AUD
                "CAD" -> return CAD
                "CHF" -> return CHF
                "EUR" -> return EUR
                "GBP" -> return GBP
                "JPY" -> return JPY
                "USD" -> return USD
            }

            val adapter = CommoditiesDbAdapter.getInstance()
            return adapter?.getCommodity(currencyCode) ?: DEFAULT_COMMODITY
        }

        @JvmStatic
        fun formatListItem(currencyCode: String, name: String?): String {
            if (name.isNullOrEmpty()) {
                return currencyCode
            }
            return "$currencyCode ($name)"
        }

        @JvmField
        val CREATOR = object : Parcelable.Creator<Commodity> {
            override fun createFromParcel(parcel: Parcel): Commodity {
                return Commodity(parcel)
            }

            override fun newArray(size: Int): Array<Commodity?> {
                return arrayOfNulls(size)
            }
        }
    }

    fun formatListItem(): String {
        return formatListItem(currencyCode, fullname)
    }
}

fun Parcel.writeCommodity(value: Commodity?, flags: Int) {
    writeParcelable(value, flags)
}

fun Parcel.readCommodity(): Commodity? {
    val clazz = Commodity::class.java
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        readParcelable(clazz.classLoader, clazz)
    } else {
        @Suppress("DEPRECATION")
        readParcelable(clazz.classLoader)
    }
}
