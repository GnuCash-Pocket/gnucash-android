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

import org.gnucash.android.db.adapter.CommoditiesDbAdapter

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
     *
     * @param smallestFraction Smallest fraction as power of ten
     * @throws IllegalArgumentException if the smallest fraction is not a power of 10
     */
    var smallestFraction: Int
) : BaseModel() {
    enum class Namespace {
        ISO4217
    } //Namespace for commodities

    var namespace = Namespace.ISO4217

    /**
     * Returns the mnemonic, or currency code for ISO4217 currencies
     *
     * @return Mnemonic of the commodity
     */
    var cusip: String? = null
    var localSymbol: String? = ""

    var quoteFlag = 0

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
        get() = if (localSymbol == null || localSymbol!!.isEmpty()) {
            mnemonic
        } else localSymbol!!

    /**
     * Returns the (minimum) number of digits that this commodity supports in its fractional part
     *
     * For any unsupported values for the smallest fraction, a default value of 2 is returned.
     * Supported values for the smallest fraction are powers of 10 i.e. 1, 10, 100 etc
     *
     * @return Number of digits in fraction
     * @see .getSmallestFraction
     */
    val smallestFractionDigits: Int
        get() = if (smallestFraction == 0) {
            0
        } else {
            Integer.numberOfTrailingZeros(smallestFraction)
        }

    /**
     * Returns the full name of the currency, or the currency code if there is no full name
     * @return String representation of the commodity
     */
    override fun toString(): String {
        return if (fullname == null || fullname!!.isEmpty()) mnemonic else fullname!!
    }

    /**
     * Overrides [BaseModel.equals] to compare only the currency codes of the commodity.
     *
     * Two commodities are considered equal if they have the same currency code
     *
     * @param o Commodity instance to compare
     * @return `true` if both instances have same currency code, `false` otherwise
     */
    override fun equals(o: Any?): Boolean {
        if (this === o) return true
        if (o == null || javaClass != o.javaClass) return false
        val commodity = o as Commodity
        return mnemonic == commodity.mnemonic
    }

    override fun hashCode(): Int {
        return mnemonic.hashCode()
    }

    companion object {
        @JvmField
        var USD = Commodity("", "USD", 100)
        @JvmField
        var EUR = Commodity("", "EUR", 100)
        @JvmField
        var GBP = Commodity("", "GBP", 100)
        @JvmField
        var CHF = Commodity("", "CHF", 100)
        @JvmField
        var CAD = Commodity("", "CAD", 100)
        @JvmField
        var JPY = Commodity("", "JPY", 1)
        @JvmField
        var AUD = Commodity("", "AUD", 100)

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

        /**
         * Returns an instance of commodity for the specified currencyCode
         *
         * @param currencyCode ISO 4217 currency code (3-letter)
         */
        @JvmStatic
        fun getInstance(currencyCode: String?): Commodity? {
            return when (currencyCode) {
                "USD" -> USD
                "EUR" -> EUR
                "GBP" -> GBP
                "CHF" -> CHF
                "JPY" -> JPY
                "AUD" -> AUD
                "CAD" -> CAD
                else -> CommoditiesDbAdapter.getInstance().getCommodity(currencyCode)
            }
        }
    }
}