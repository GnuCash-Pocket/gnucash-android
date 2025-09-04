/*
 * Copyright (c) 2016 Àlex Magaz Graça <rivaldi8@gmail.com>
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
package org.gnucash.android.test.unit.model

import junit.framework.TestCase.fail
import org.assertj.core.api.Assertions.assertThat
import org.gnucash.android.db.adapter.PricesDbAdapter
import org.gnucash.android.model.Commodity
import org.gnucash.android.model.Price
import org.gnucash.android.test.unit.GnuCashTest
import org.junit.Test
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale

class PriceTest : GnuCashTest() {
    @Test
    fun creatingFromExchangeRate_ShouldGetPrecisionRight() {
        Locale.setDefault(Locale.US)
        val commodity1 = Commodity.USD
        val commodity2 = Commodity.EUR

        var exchangeRateString = "0.123"
        var exchangeRate = BigDecimal(exchangeRateString)
        var price = Price(commodity1, commodity2, exchangeRate)
        // EUR uses 2 fractional digits.
        assertThat(price.toString()).isEqualTo("0.12")

        // ensure we don't get more decimal places than needed (0.123000)
        exchangeRateString = "0.123456"
        exchangeRate = BigDecimal(exchangeRateString)
        price = Price(commodity1, commodity2, exchangeRate)
        // EUR uses 2 fractional digits.
        assertThat(price.toString()).isEqualTo("0.12")
    }

    @Test
    fun toString_shouldUseDefaultLocale() {
        Locale.setDefault(Locale.GERMANY)
        val commodity1 = Commodity.EUR
        val commodity2 = Commodity.USD

        val exchangeRateString = "1.234"
        val exchangeRate = BigDecimal(exchangeRateString)
        val price = Price(commodity1, commodity2, exchangeRate)
        // USD uses 2 fractional digits.
        assertThat(price.toString()).isEqualTo("1,23")
    }

    /**
     * BigDecimal throws an ArithmeticException if it can't represent exactly
     * a result. This can happen with divisions like 1/3 if no precision and
     * round mode is specified with a MathContext.
     */
    @Test
    fun toString_shouldNotFailForInfinitelyLongDecimalExpansion() {
        val numerator: Long = 1
        val denominator: Long = 3
        val price = Price()

        price.valueNum = numerator
        price.valueDenom = denominator
        try {
            price.toString()
        } catch (e: ArithmeticException) {
            fail("The numerator/denominator division in Price.toString() should not fail.")
        }
    }

    @Test
    fun numerator_shouldReduceAutomatically() {
        val numerator: Long = 1
        val denominator: Long = 3
        val price = Price()

        price.valueNum = numerator * 2
        price.valueDenom = denominator * 2
        assertThat(price.valueNum).isEqualTo(numerator)
    }

    @Test
    fun denominator_shouldReduceAutomatically() {
        val numerator: Long = 1
        val denominator: Long = 3
        val price = Price()

        price.valueNum = numerator * 2
        price.valueDenom = denominator * 2
        assertThat(price.valueDenom).isEqualTo(denominator)
    }

    @Test
    fun inverse() {
        Locale.setDefault(Locale.US)
        val commodity1 = Commodity.USD
        val commodity2 = Commodity.EUR

        val rate = BigDecimal(1.17)
        val pricesDbAdapter = PricesDbAdapter.instance
        val price = Price(commodity2, commodity1, rate) // 1 EUR = 1.17 USD
        assertThat(price.toBigDecimal(2)).isEqualTo(rate.setScale(2, RoundingMode.HALF_UP))
        pricesDbAdapter.addRecord(price)

        val price12 = pricesDbAdapter.getPrice(commodity1, commodity2)
        assertThat(price12!!).isNotNull
        assertThat(price12.commodity).isEqualTo(commodity1)
        assertThat(price12.currency).isEqualTo(commodity2)
        assertThat(price12.toBigDecimal(3)).isEqualTo(BigDecimal(0.855).setScale(3, RoundingMode.HALF_UP))

        val price21 = pricesDbAdapter.getPrice(commodity2, commodity1)
        assertThat(price21!!).isNotNull
        assertThat(price21.commodity).isEqualTo(commodity2)
        assertThat(price21.currency).isEqualTo(commodity1)
        assertThat(price21.toBigDecimal(2)).isEqualTo(rate.setScale(2, RoundingMode.HALF_UP))
    }
}
