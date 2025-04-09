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

package org.gnucash.android.test.unit.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertThrows;

import org.gnucash.android.model.Commodity;
import org.gnucash.android.model.Money;
import org.gnucash.android.test.unit.GnuCashTest;
import org.gnucash.android.util.AmountParser;
import org.junit.Before;
import org.junit.Test;
import org.robolectric.annotation.Config;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.Locale;

public class MoneyTest extends GnuCashTest {

    private static final String CURRENCY_EUR = "EUR";
    private Money mMoneyInEur;
    private int mHashcode;
    private String amountString = "15.75";

    @Before
    public void setUp() throws Exception {
        mMoneyInEur = new Money(new BigDecimal(amountString), Commodity.getInstance(CURRENCY_EUR));
        mHashcode = mMoneyInEur.hashCode();
    }

    @Test
    public void testCreation() {
        Locale.setDefault(Locale.US);
        String amount = "12.25";

        Money temp = new Money(amount, CURRENCY_EUR);
        assertThat(temp.toPlainString()).isEqualTo("12.25");
        assertThat(temp.getNumerator()).isEqualTo(1225L);
        assertThat(temp.getDenominator()).isEqualTo(100L);

        Commodity commodity = Commodity.getInstance(CURRENCY_EUR);
        temp = new Money(BigDecimal.TEN, commodity);

        assertThat(temp.asBigDecimal().toPlainString()).isEqualTo("10.00"); //decimal places for EUR currency
        assertThat(temp.getCommodity()).isEqualTo(commodity);
        assertThat(temp.asBigDecimal().toPlainString()).isNotEqualTo("10");
    }

    @Test
    public void testAddition() {
        Money result = mMoneyInEur.plus(new Money("5", CURRENCY_EUR));
        assertThat(result.toPlainString()).isEqualTo("20.75");
        assertNotSame(result, mMoneyInEur);
        validateImmutability();
    }

    @Test(expected = Money.CurrencyMismatchException.class)
    public void testAdditionWithIncompatibleCurrency() {
        Money addend = new Money("4", "USD");
        mMoneyInEur.plus(addend);
    }

    @Test
    public void testSubtraction() {
        Money result = mMoneyInEur.minus(new Money("2", CURRENCY_EUR));
        assertThat(result.asBigDecimal()).isEqualTo(new BigDecimal("13.75"));
        assertNotSame(result, mMoneyInEur);
        validateImmutability();
    }

    @Test(expected = Money.CurrencyMismatchException.class)
    public void testSubtractionWithDifferentCurrency() {
        Money other = new Money("4", "USD");
        mMoneyInEur.minus(other);
    }

    @Test
    public void testMultiplication() {
        Money result = mMoneyInEur.times(new Money(BigDecimal.TEN, Commodity.getInstance(CURRENCY_EUR)));
        assertThat("157.50").isEqualTo(result.toPlainString());
        assertThat(result).isNotEqualTo(mMoneyInEur);
        validateImmutability();
    }

    @Test(expected = Money.CurrencyMismatchException.class)
    public void testMultiplicationWithDifferentCurrencies() {
        Money other = new Money("4", "USD");
        mMoneyInEur.times(other);
    }

    @Test
    public void testDivision() {
        Money result = mMoneyInEur.div(2);
        assertThat(result.toPlainString()).isEqualTo("7.88");
        assertThat(result).isNotEqualTo(mMoneyInEur);
        validateImmutability();
    }

    @Test(expected = Money.CurrencyMismatchException.class)
    public void testDivisionWithDifferentCurrency() {
        Money other = new Money("4", "USD");
        mMoneyInEur.div(other);
    }

    @Test
    public void testNegation() {
        Money result = mMoneyInEur.unaryMinus();
        assertThat(result.toPlainString()).startsWith("-");
        validateImmutability();
    }

    @Test
    public void testFractionParts() {
        Money money = new Money("14.15", "USD");
        assertThat(money.getNumerator()).isEqualTo(1415L);
        assertThat(money.getDenominator()).isEqualTo(100L);

        money = new Money("125", "JPY");
        assertThat(money.getNumerator()).isEqualTo(125L);
        assertThat(money.getDenominator()).isEqualTo(1L);
    }

    @Test
    public void nonMatchingCommodityFraction_shouldThrowException() {
        Money money = new Money("12.345", "JPY");
        assertThat(money.getNumerator()).isEqualTo(12L);
        assertThat(money.getDenominator()).isEqualTo(1L);
    }

    @Test
    public void testPrinting() {
        assertThat(mMoneyInEur.toPlainString()).isEqualTo(mMoneyInEur.asString());
        assertThat(mMoneyInEur.asString()).isEqualTo(amountString);

        // the unicode for Euro symbol is \u20AC

        String symbol = Currency.getInstance("EUR").getSymbol(Locale.GERMANY);
        String actualOutputDE = mMoneyInEur.formattedString(Locale.GERMANY);
        assertThat(actualOutputDE).isEqualTo("15,75 " + symbol);

        symbol = Currency.getInstance("EUR").getSymbol(Locale.GERMANY);
        String actualOutputUS = mMoneyInEur.formattedString(Locale.US);
        assertThat(actualOutputUS).isEqualTo(symbol + "15.75");

        //always prints with 2 decimal places only
        Money some = new Money("9.7469", CURRENCY_EUR);
        assertThat(some.asString()).isEqualTo("9.75");
    }

    public void validateImmutability() {
        assertThat(mMoneyInEur.hashCode()).isEqualTo(mHashcode);
        assertThat(mMoneyInEur.toPlainString()).isEqualTo(amountString);
        assertThat(mMoneyInEur.getCommodity()).isNotNull();
        assertThat(mMoneyInEur.getCommodity().getCurrencyCode()).isEqualTo(CURRENCY_EUR);
    }

    @Test
    public void overflow() {
        Money rounding = new Money("12345678901234567.89", CURRENCY_EUR);
        assertThat(rounding.toPlainString()).isEqualTo("12345678901234567.89");
        assertThat(rounding.getNumerator()).isEqualTo(1234567890123456789L);
        assertThat(rounding.getDenominator()).isEqualTo(100L);
        assertThat(rounding.formattedString(Locale.US)).isEqualTo("€12,345,678,901,234,567.89");

        Money overflow = new Money("1234567890123456789.00", CURRENCY_EUR);
        assertThat(overflow.toPlainString()).isEqualTo("1234567890123456789.00");
        assertThatThrownBy(() -> overflow.getNumerator()).isInstanceOf(ArithmeticException.class);
    }

    @Test
    @Config(sdk = 25)
    public void evaluate_25() {
        BigDecimal value = AmountParser.evaluate("123456789012345678.90");
        assertThat(value).isNotNull();
        assertThat(value.doubleValue()).isCloseTo(123456789012345678.90, within(1e-2));
        assertThat(value.longValue()).isEqualTo(123456789012345680L);
    }

    @Test
    @Config(sdk = 26)
    public void evaluate_26() {
        BigDecimal value = AmountParser.evaluate("123456789012345678.90");
        assertThat(value).isNotNull();
        assertThat(value.doubleValue()).isCloseTo(123456789012345678.90, within(1e-2));
        assertThat(value.longValue()).isEqualTo(123456789012345678L);
    }

    @Test
    public void add_different_currencies() {
        Commodity.DEFAULT_COMMODITY = Commodity.USD;
        Money money = Money.createZeroInstance(Commodity.DEFAULT_COMMODITY);
        Money addend = new Money(0.0, Commodity.USD);
        Money sum = money.plus(addend);
        assertThat(sum.isAmountZero()).isTrue();
        assertThat(sum.toDouble()).isEqualTo(0.0);
        assertThat(sum.getCommodity()).isEqualTo(Commodity.USD);

        addend = new Money(123.45, Commodity.USD);
        sum = money.plus(addend);
        assertThat(sum.isAmountZero()).isFalse();
        assertThat(sum.toDouble()).isEqualTo(123.45);
        assertThat(sum.getCommodity()).isEqualTo(Commodity.USD);

        addend = new Money(0.0, Commodity.EUR);
        sum = money.plus(addend);
        assertThat(sum.isAmountZero()).isTrue();
        assertThat(sum.toDouble()).isEqualTo(0.0);
        assertThat(sum.getCommodity()).isEqualTo(Commodity.EUR);

        final Money money1 = new Money(100.00, Commodity.USD);
        final Money money2 = new Money(123.45, Commodity.EUR);
        assertThrows(Money.CurrencyMismatchException.class, () -> money1.plus(money2));
    }

    @Test
    public void scale() {
        final double d = 123.0;
        final Money m0 = new Money(BigDecimal.valueOf(d).setScale(0), Commodity.TEMPLATE);
        final Money m1 = new Money(BigDecimal.valueOf(d).setScale(1), Commodity.TEMPLATE);
        final Money m2 = new Money(BigDecimal.valueOf(d).setScale(2), Commodity.TEMPLATE);
        assertThat(m0.doubleValue()).isEqualTo(d);
        assertThat(m1.doubleValue()).isEqualTo(d);
        assertThat(m2.doubleValue()).isEqualTo(d);
        assertThat(m0).isEqualTo(m1);
        assertThat(m0).isEqualTo(m2);
        assertThat(m1).isEqualTo(m0);
        assertThat(m1).isEqualTo(m2);
        assertThat(m2).isEqualTo(m0);
        assertThat(m2).isEqualTo(m1);

        final Money m3 = new Money(BigDecimal.valueOf(d + 0.4).setScale(1), Commodity.TEMPLATE);
        assertThat(m3).isNotEqualTo(m0);
        assertThat(m3).isNotEqualTo(m1);
        assertThat(m3).isNotEqualTo(m2);
    }

    @Test
    public void scale_1() {
        Money money = new Money(123.0, Commodity.JPY);
        assertThat(money.getCommodity().getSmallestFraction()).isEqualTo(1);
        assertThat(money.getCommodity().getSmallestFractionDigits()).isEqualTo(0);
        assertThat(money.getNumerator()).isEqualTo(123L);
        assertThat(money.getDenominator()).isEqualTo(1L);

        money = new Money(123.4, Commodity.JPY);
        assertThat(money.getCommodity().getSmallestFraction()).isEqualTo(1);
        assertThat(money.getCommodity().getSmallestFractionDigits()).isEqualTo(0);
        assertThat(money.getNumerator()).isEqualTo(123L);
        assertThat(money.getDenominator()).isEqualTo(1L);

        money = new Money(123.45, Commodity.JPY);
        assertThat(money.getCommodity().getSmallestFraction()).isEqualTo(1);
        assertThat(money.getCommodity().getSmallestFractionDigits()).isEqualTo(0);
        assertThat(money.getNumerator()).isEqualTo(123L);
        assertThat(money.getDenominator()).isEqualTo(1L);

        money = new Money(123.456, Commodity.JPY);
        assertThat(money.getCommodity().getSmallestFraction()).isEqualTo(1);
        assertThat(money.getCommodity().getSmallestFractionDigits()).isEqualTo(0);
        assertThat(money.getNumerator()).isEqualTo(123L);
        assertThat(money.getDenominator()).isEqualTo(1L);
    }

    @Test
    public void scale_10() {
        Commodity commodity = new Commodity("scale-10", "S10", 10);
        final Money money = new Money(123.456, commodity);
        assertThat(money.getCommodity().getSmallestFraction()).isEqualTo(10);
        assertThat(money.getCommodity().getSmallestFractionDigits()).isEqualTo(1);
        assertThat(money.getNumerator()).isEqualTo(1235L);
        assertThat(money.getDenominator()).isEqualTo(10L);
    }

    @Test
    public void scale_100() {
        final Money money = new Money(123.456, Commodity.USD);
        assertThat(money.getCommodity().getSmallestFraction()).isEqualTo(100);
        assertThat(money.getCommodity().getSmallestFractionDigits()).isEqualTo(2);
        assertThat(money.getNumerator()).isEqualTo(12346L);
        assertThat(money.getDenominator()).isEqualTo(100L);
    }

    @Test
    public void scale_template() {
        final Money money = new Money(123456L, 1000L, Commodity.template);
        assertThat(money.getNumerator()).isEqualTo(123456L);
        assertThat(money.getDenominator()).isEqualTo(1000L);
    }
}
