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
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;

import org.gnucash.android.model.Commodity;
import org.gnucash.android.model.Money;
import org.gnucash.android.test.unit.testutil.ShadowCrashlytics;
import org.gnucash.android.test.unit.testutil.ShadowUserVoice;
import org.gnucash.android.util.AmountParser;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.Locale;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 21, shadows = {ShadowCrashlytics.class, ShadowUserVoice.class})
public class MoneyTest {

    private static final String CURRENCY_CODE = "EUR";
    private Money mMoneyInEur;
    private int mHashcode;
    private String amountString = "15.75";

    @Before
    public void setUp() throws Exception {
        mMoneyInEur = new Money(new BigDecimal(amountString), Commodity.getInstance(CURRENCY_CODE));
        mHashcode = mMoneyInEur.hashCode();
    }

    @Test
    public void testCreation() {
        Locale.setDefault(Locale.US);
        String amount = "12.25";

        Money temp = new Money(amount, CURRENCY_CODE);
        assertThat("12.25").isEqualTo(temp.toPlainString());
        assertThat(temp.getNumerator()).isEqualTo(1225L);
        assertThat(temp.getDenominator()).isEqualTo(100L);

        Commodity commodity = Commodity.getInstance(CURRENCY_CODE);
        temp = new Money(BigDecimal.TEN, commodity);

        assertEquals("10.00", temp.asBigDecimal().toPlainString()); //decimal places for EUR currency
        assertEquals(commodity, temp.getCommodity());
        assertThat("10").isNotEqualTo(temp.asBigDecimal().toPlainString());
    }

    @Test
    public void testAddition() {
        Money result = mMoneyInEur.plus(new Money("5", CURRENCY_CODE));
        assertEquals("20.75", result.toPlainString());
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
        Money result = mMoneyInEur.minus(new Money("2", CURRENCY_CODE));
        assertEquals(new BigDecimal("13.75"), result.asBigDecimal());
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
        Money result = mMoneyInEur.times(new Money(BigDecimal.TEN, Commodity.getInstance(CURRENCY_CODE)));
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
        assertThat(money.getDenominator()).isEqualTo(1);
    }

    @Test
    public void testPrinting() {
        assertEquals(mMoneyInEur.asString(), mMoneyInEur.toPlainString());
        assertEquals(amountString, mMoneyInEur.asString());

        // the unicode for Euro symbol is \u20AC

        String symbol = Currency.getInstance("EUR").getSymbol(Locale.GERMANY);
        String actualOuputDE = mMoneyInEur.formattedString(Locale.GERMANY);
        assertThat(actualOuputDE).isEqualTo("15,75 " + symbol);

        symbol = Currency.getInstance("EUR").getSymbol(Locale.GERMANY);
        String actualOuputUS = mMoneyInEur.formattedString(Locale.US);
        assertThat(actualOuputUS).isEqualTo(symbol + "15.75");

        //always prints with 2 decimal places only
        Money some = new Money("9.7469", CURRENCY_CODE);
        assertEquals("9.75", some.asString());
    }

    public void validateImmutability() {
        assertEquals(mHashcode, mMoneyInEur.hashCode());
        assertEquals(amountString, mMoneyInEur.toPlainString());
        assertNotNull(mMoneyInEur.getCommodity());
        assertEquals(CURRENCY_CODE, mMoneyInEur.getCommodity().getCurrencyCode());
    }

    @Test
    public void overflow() {
        Money rounding = new Money("12345678901234567.89", CURRENCY_CODE);
        assertThat("12345678901234567.89").isEqualTo(rounding.toPlainString());
        assertThat("€12,345,678,901,234,567.89").isEqualTo(rounding.formattedString(Locale.US));
        assertThat(rounding.getNumerator()).isEqualTo(1234567890123456789L);
        assertThat(rounding.getDenominator()).isEqualTo(100L);

        Money overflow = new Money("1234567890123456789.00", CURRENCY_CODE);
        assertThat("1234567890123456789.00").isEqualTo(overflow.toPlainString());
        assertThatThrownBy(() -> overflow.getNumerator()).isInstanceOf(ArithmeticException.class);
    }

    @Test
    @Config(sdk = 25)
    public void evaluate_25() {
        BigDecimal value = AmountParser.evaluate("123456789012345678.90");
        assertNotNull(value);
        assertEquals(123456789012345678.90, value.doubleValue(), 1e-2);
        assertEquals(123456789012345680L, value.longValue());
    }

    @Test
    @Config(sdk = 26)
    public void evaluate_26() {
        BigDecimal value = AmountParser.evaluate("123456789012345678.90");
        assertNotNull(value);
        assertEquals(123456789012345678.90, value.doubleValue(), 1e-2);
        assertEquals(123456789012345678L, value.longValue());
    }
}
