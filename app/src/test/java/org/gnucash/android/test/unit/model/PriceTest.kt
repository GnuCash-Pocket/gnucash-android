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

package org.gnucash.android.test.unit.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.fail;

import org.gnucash.android.model.Commodity;
import org.gnucash.android.model.Price;
import org.gnucash.android.test.unit.GnuCashTest;
import org.junit.Test;

import java.math.BigDecimal;
import java.util.Locale;


public class PriceTest extends GnuCashTest {
    @Test
    public void creatingFromExchangeRate_ShouldGetPrecisionRight() {
        Locale.setDefault(Locale.US);
        Commodity commodity1 = Commodity.USD;
        Commodity commodity2 = Commodity.EUR;

        String exchangeRateString = "0.123";
        BigDecimal exchangeRate = new BigDecimal(exchangeRateString);
        Price price = new Price(commodity1, commodity2, exchangeRate);
        // EUR uses 2 fractional digits.
        assertThat(price.toString()).isEqualTo("0.12");

        // ensure we don't get more decimal places than needed (0.123000)
        exchangeRateString = "0.123456";
        exchangeRate = new BigDecimal(exchangeRateString);
        price = new Price(commodity1, commodity2, exchangeRate);
        // EUR uses 2 fractional digits.
        assertThat(price.toString()).isEqualTo("0.12");
    }

    @Test
    public void toString_shouldUseDefaultLocale() {
        Locale.setDefault(Locale.GERMANY);
        Commodity commodity1 = Commodity.EUR;
        Commodity commodity2 = Commodity.USD;

        String exchangeRateString = "1.234";
        BigDecimal exchangeRate = new BigDecimal(exchangeRateString);
        Price price = new Price(commodity1, commodity2, exchangeRate);
        // USD uses 2 fractional digits.
        assertThat(price.toString()).isEqualTo("1,23");
    }

    /**
     * BigDecimal throws an ArithmeticException if it can't represent exactly
     * a result. This can happen with divisions like 1/3 if no precision and
     * round mode is specified with a MathContext.
     */
    @Test
    public void toString_shouldNotFailForInfinitelyLongDecimalExpansion() {
        long numerator = 1;
        long denominator = 3;
        Price price = new Price();

        price.setValueNum(numerator);
        price.setValueDenom(denominator);
        try {
            price.toString();
        } catch (ArithmeticException e) {
            fail("The numerator/denominator division in Price.toString() should not fail.");
        }
    }

    @Test
    public void getNumerator_shouldReduceAutomatically() {
        long numerator = 1;
        long denominator = 3;
        Price price = new Price();

        price.setValueNum(numerator * 2);
        price.setValueDenom(denominator * 2);
        assertThat(price.getValueNum()).isEqualTo(numerator);
    }

    @Test
    public void getDenominator_shouldReduceAutomatically() {
        long numerator = 1;
        long denominator = 3;
        Price price = new Price();

        price.setValueNum(numerator * 2);
        price.setValueDenom(denominator * 2);
        assertThat(price.getValueDenom()).isEqualTo(denominator);
    }
}
