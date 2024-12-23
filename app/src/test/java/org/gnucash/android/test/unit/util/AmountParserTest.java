package org.gnucash.android.test.unit.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.gnucash.android.util.AmountParser;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.math.BigDecimal;
import java.text.ParseException;
import java.util.Locale;

public class AmountParserTest {
    private Locale mPreviousLocale;

    @Before
    public void setUp() {
        mPreviousLocale = Locale.getDefault();
        Locale.setDefault(Locale.US);
    }

    @After
    public void tearDown() {
        Locale.setDefault(mPreviousLocale);
    }

    @Test
    public void testParseIntegerAmount() throws Exception {
        assertThat(AmountParser.parse("123")).isEqualTo(new BigDecimal(123));
    }

    @Test
    public void parseDecimalAmount() throws Exception {
        assertThat(AmountParser.parse("123.45")).isEqualTo(new BigDecimal("123.45"));
    }

    @Test
    public void parseDecimalAmountWithDifferentSeparator() throws Exception {
        Locale.setDefault(Locale.GERMANY);
        assertThat(AmountParser.parse("123,45")).isEqualTo(new BigDecimal("123.45"));

        Locale.setDefault(new Locale("es"));
        assertThat(AmountParser.parse("123,45")).isEqualTo(new BigDecimal("123.45"));
    }

    @Test(expected = ParseException.class)
    public void withGarbageAtTheBeginning_shouldFailWithException() throws Exception {
        AmountParser.parse("asdf123.45");
    }

    @Test(expected = ParseException.class)
    public void withGarbageAtTheEnd_shouldFailWithException() throws ParseException {
        AmountParser.parse("123.45asdf");
    }

    @Test(expected = ParseException.class)
    public void emptyString_shouldFailWithException() throws ParseException {
        AmountParser.parse("");
    }
}