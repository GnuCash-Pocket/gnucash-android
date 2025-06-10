/*
 * Copyright (c) 2012 Ngewi Fet <ngewif@gmail.com>
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

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.assertj.core.api.Assertions.within
import org.gnucash.android.model.Commodity
import org.gnucash.android.model.Commodity.Companion.USD
import org.gnucash.android.model.Commodity.Companion.getInstance
import org.gnucash.android.model.Money
import org.gnucash.android.model.Money.Companion.createZeroInstance
import org.gnucash.android.model.Money.CurrencyMismatchException
import org.gnucash.android.test.unit.GnuCashTest
import org.gnucash.android.util.AmountParser
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.robolectric.annotation.Config
import java.math.BigDecimal
import java.util.Currency
import java.util.Locale

class MoneyTest : GnuCashTest() {
    private lateinit var moneyInEur: Money
    private var moneyHashcode = 0
    private val amountString = "15.75"

    @Before
    fun setUp() {
        moneyInEur = Money(BigDecimal(amountString), getInstance(CURRENCY_EUR))
        moneyHashcode = moneyInEur.hashCode()
    }

    @Test
    fun testCreation() {
        Locale.setDefault(Locale.US)
        val amount = "12.25"

        var temp = Money(amount, CURRENCY_EUR)
        assertThat(temp.toPlainString()).isEqualTo("12.25")
        assertThat(temp.numerator).isEqualTo(1225L)
        assertThat(temp.denominator).isEqualTo(100L)

        val commodity = getInstance(CURRENCY_EUR)
        temp = Money(BigDecimal.TEN, commodity)

        assertThat(temp.toBigDecimal().toPlainString()).isEqualTo("10.00") //decimal places for EUR currency
        assertThat(temp.commodity).isEqualTo(commodity)
        assertThat(temp.toBigDecimal().toPlainString()).isNotEqualTo("10")
    }

    @Test
    fun testAddition() {
        val result = moneyInEur.plus(Money("5", CURRENCY_EUR))
        assertThat(result.toPlainString()).isEqualTo("20.75")
        assertNotSame(result, moneyInEur)
        validateImmutability()
    }

    @Test(expected = CurrencyMismatchException::class)
    fun testAdditionWithIncompatibleCurrency() {
        val addend = Money("4", "USD")
        moneyInEur.plus(addend)
    }

    @Test
    fun testSubtraction() {
        val result = moneyInEur.minus(Money("2", CURRENCY_EUR))
        assertThat(result.toBigDecimal()).isEqualTo(BigDecimal("13.75"))
        assertNotSame(result, moneyInEur)
        validateImmutability()
    }

    @Test(expected = CurrencyMismatchException::class)
    fun testSubtractionWithDifferentCurrency() {
        val other = Money("4", "USD")
        moneyInEur.minus(other)
    }

    @Test
    fun testMultiplication() {
        val result = moneyInEur.times(Money(BigDecimal.TEN, getInstance(CURRENCY_EUR)))
        assertThat("157.50").isEqualTo(result.toPlainString())
        assertThat(result).isNotEqualTo(moneyInEur)
        validateImmutability()
    }

    @Test(expected = CurrencyMismatchException::class)
    fun testMultiplicationWithDifferentCurrencies() {
        val other = Money("4", "USD")
        moneyInEur.times(other)
    }

    @Test
    fun testDivision() {
        val result = moneyInEur.div(2)
        assertThat(result.toPlainString()).isEqualTo("7.88")
        assertThat(result).isNotEqualTo(moneyInEur)
        validateImmutability()
    }

    @Test(expected = CurrencyMismatchException::class)
    fun testDivisionWithDifferentCurrency() {
        val other = Money("4", "USD")
        moneyInEur.div(other)
    }

    @Test
    fun testNegation() {
        val result = moneyInEur.unaryMinus()
        assertThat(result.toPlainString()).startsWith("-")
        validateImmutability()
    }

    @Test
    fun testFractionParts() {
        var money = Money("14.15", "USD")
        assertThat(money.numerator).isEqualTo(1415L)
        assertThat(money.denominator).isEqualTo(100L)

        money = Money("125", "JPY")
        assertThat(money.numerator).isEqualTo(125L)
        assertThat(money.denominator).isEqualTo(1L)
    }

    @Test
    fun nonMatchingCommodityFraction_shouldThrowException() {
        val money = Money("12.345", "JPY")
        assertThat(money.numerator).isEqualTo(12L)
        assertThat(money.denominator).isEqualTo(1L)
    }

    @Test
    fun testPrinting() {
        assertThat(moneyInEur.toPlainString()).isEqualTo(moneyInEur.asString())
        assertThat(moneyInEur.asString()).isEqualTo(amountString)

        // the unicode for Euro symbol is \u20AC
        var symbol = Currency.getInstance("EUR").getSymbol(Locale.GERMANY)
        val actualOutputDE = moneyInEur.formattedString(Locale.GERMANY)
        assertThat(actualOutputDE).isEqualTo("15,75 $symbol")

        symbol = Currency.getInstance("EUR").getSymbol(Locale.GERMANY)
        val actualOutputUS = moneyInEur.formattedString(Locale.US)
        assertThat(actualOutputUS).isEqualTo(symbol + "15.75")

        //always prints with 2 decimal places only
        val some = Money("9.7469", CURRENCY_EUR)
        assertThat(some.asString()).isEqualTo("9.7469")
        assertThat(some.formattedString(Locale.US)).isEqualTo("€9.75")
    }

    fun validateImmutability() {
        assertThat(moneyInEur.hashCode()).isEqualTo(moneyHashcode)
        assertThat(moneyInEur.toPlainString()).isEqualTo(amountString)
        assertThat(moneyInEur.commodity).isNotNull()
        assertThat(moneyInEur.commodity.currencyCode).isEqualTo(CURRENCY_EUR)
    }

    @Test
    fun overflow() {
        val rounding = Money("12345678901234567.89", CURRENCY_EUR)
        assertThat(rounding.toPlainString()).isEqualTo("12345678901234567.89")
        assertThat(rounding.numerator).isEqualTo(1234567890123456789L)
        assertThat(rounding.denominator).isEqualTo(100L)
        assertThat(rounding.formattedString(Locale.US)).isEqualTo("€12,345,678,901,234,567.89")

        val overflow = Money("1234567890123456789.00", CURRENCY_EUR)
        assertThat(overflow.toPlainString()).isEqualTo("1234567890123456789.00")
        assertThatThrownBy { overflow.numerator }.isInstanceOf(ArithmeticException::class.java)
    }

    @Test
    @Config(sdk = [25])
    fun evaluate_25() {
        val value = AmountParser.evaluate("123456789012345678.90")
        assertThat(value).isNotNull()
        assertThat(value!!.toDouble()).isCloseTo(123456789012345678.90, within(1e-2))
        assertThat(value.toLong()).isEqualTo(123456789012345680L)
    }

    @Test
    @Config(sdk = [26])
    fun evaluate_26() {
        val value = AmountParser.evaluate("123456789012345678.90")
        assertThat(value).isNotNull()
        assertThat(value!!.toDouble()).isCloseTo(123456789012345678.90, within(1e-2))
        assertThat(value.toLong()).isEqualTo(123456789012345678L)
    }

    @Test
    fun add_different_currencies() {
        Commodity.DEFAULT_COMMODITY = Commodity.USD
        val money = createZeroInstance(Commodity.DEFAULT_COMMODITY)
        var addend = Money(0.0, Commodity.USD)
        var sum = money.plus(addend)
        assertThat(sum.isAmountZero).isTrue()
        assertThat(sum.toDouble()).isEqualTo(0.0)
        assertThat(sum.commodity).isEqualTo(Commodity.USD)

        addend = Money(123.45, Commodity.USD)
        sum = money.plus(addend)
        assertThat(sum.isAmountZero).isFalse()
        assertThat(sum.toDouble()).isEqualTo(123.45)
        assertThat(sum.commodity).isEqualTo(Commodity.USD)

        addend = Money(0.0, Commodity.EUR)
        sum = money.plus(addend)
        assertThat(sum.isAmountZero).isTrue()
        assertThat(sum.toDouble()).isEqualTo(0.0)
        assertThat(sum.commodity).isEqualTo(Commodity.EUR)

        val money1 = Money(100.00, Commodity.USD)
        val money2 = Money(123.45, Commodity.EUR)
        assertThrows(CurrencyMismatchException::class.java) { money1.plus(money2) }
    }

    @Test
    fun scale() {
        val d = 123.0
        val m0 = Money(BigDecimal.valueOf(d).setScale(0), Commodity.TEMPLATE)
        val m1 = Money(BigDecimal.valueOf(d).setScale(1), Commodity.TEMPLATE)
        val m2 = Money(BigDecimal.valueOf(d).setScale(2), Commodity.TEMPLATE)
        assertThat(m0.toDouble()).isEqualTo(d)
        assertThat(m1.toDouble()).isEqualTo(d)
        assertThat(m2.toDouble()).isEqualTo(d)
        assertThat(m0).isEqualTo(m1)
        assertThat(m0).isEqualTo(m2)
        assertThat(m1).isEqualTo(m0)
        assertThat(m1).isEqualTo(m2)
        assertThat(m2).isEqualTo(m0)
        assertThat(m2).isEqualTo(m1)

        val m3 = Money(BigDecimal.valueOf(d + 0.4).setScale(1), Commodity.TEMPLATE)
        assertThat(m3).isNotEqualTo(m0)
        assertThat(m3).isNotEqualTo(m1)
        assertThat(m3).isNotEqualTo(m2)
    }

    @Test
    fun scale_1() {
        var money = Money(123.0, Commodity.JPY)
        assertThat(money.commodity.smallestFraction).isOne()
        assertThat(money.commodity.smallestFractionDigits).isZero()
        assertThat(money.numerator).isEqualTo(123L)
        assertThat(money.denominator).isEqualTo(1L)

        money = Money(123.4, Commodity.JPY)
        assertThat(money.commodity.smallestFraction).isOne()
        assertThat(money.commodity.smallestFractionDigits).isZero()
        assertThat(money.numerator).isEqualTo(123L)
        assertThat(money.denominator).isEqualTo(1L)

        money = Money(123.45, Commodity.JPY)
        assertThat(money.commodity.smallestFraction).isOne()
        assertThat(money.commodity.smallestFractionDigits).isZero()
        assertThat(money.numerator).isEqualTo(123L)
        assertThat(money.denominator).isEqualTo(1L)

        money = Money(123.456, Commodity.JPY)
        assertThat(money.commodity.smallestFraction).isOne()
        assertThat(money.commodity.smallestFractionDigits).isZero()
        assertThat(money.numerator).isEqualTo(123L)
        assertThat(money.denominator).isEqualTo(1L)
    }

    @Test
    fun scale_10() {
        val commodity = Commodity("scale-10", "S10", smallestFraction = 10)
        val money = Money(123.456, commodity)
        assertThat(money.commodity.smallestFraction).isEqualTo(10)
        assertThat(money.commodity.smallestFractionDigits).isEqualTo(1)
        assertThat(money.numerator).isEqualTo(1235L)
        assertThat(money.denominator).isEqualTo(10L)
    }

    @Test
    fun scale_10_template() {
        val money = Money(999, 10, Commodity.template)
        assertThat(money.commodity.smallestFraction).isOne()
        assertThat(money.commodity.smallestFractionDigits).isZero()
        assertThat(money.numerator).isEqualTo(999L)
        assertThat(money.denominator).isEqualTo(10L)
    }

    @Test
    fun scale_100() {
        val money = Money(123.456, Commodity.USD)
        assertThat(money.commodity.smallestFraction).isEqualTo(100)
        assertThat(money.commodity.smallestFractionDigits).isEqualTo(2)
        assertThat(money.numerator).isEqualTo(12346L)
        assertThat(money.denominator).isEqualTo(100L)
    }

    @Test
    fun scale_template() {
        val money = Money(123456L, 1000L, Commodity.template)
        assertThat(money.numerator).isEqualTo(123456L)
        assertThat(money.denominator).isEqualTo(1000L)
    }

    @Test
    fun money_BigDecimal() {
        val money1 = Money(123.45, USD)
        val money2 = Money("123.45", USD)
        val money3 = Money(12345, 100, USD)
        assertThat(money1).isEqualTo(money2)
        assertThat(money1).isEqualTo(money3)
        assertThat(money2).isEqualTo(money3)

        val money4 = Money(123, 1, USD)
        val money5 = Money("123", USD)
        assertThat(money4).isEqualTo(money5)
    }

    companion object {
        private const val CURRENCY_EUR = "EUR"
    }
}
