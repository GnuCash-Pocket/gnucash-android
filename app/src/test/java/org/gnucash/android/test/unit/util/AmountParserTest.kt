package org.gnucash.android.test.unit.util

import org.assertj.core.api.Assertions.assertThat
import org.gnucash.android.test.unit.GnuCashTest
import org.gnucash.android.util.AmountParser
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import java.text.ParseException
import java.util.Locale

class AmountParserTest : GnuCashTest() {
    private lateinit var previousLocale: Locale

    @Before
    fun setUp() {
        previousLocale = Locale.getDefault()
        Locale.setDefault(Locale.US)
    }

    @After
    fun tearDown() {
        Locale.setDefault(previousLocale)
    }

    @Test
    fun testParseIntegerAmount() {
        assertThat(AmountParser.parse("123")).isEqualTo(BigDecimal(123))
    }

    @Test
    fun parseDecimalAmount() {
        assertThat(AmountParser.parse("123.45")).isEqualTo(BigDecimal("123.45"))
    }

    @Test
    fun parseDecimalAmountWithDifferentSeparator() {
        Locale.setDefault(Locale.GERMANY)
        assertThat(AmountParser.parse("123,45")).isEqualTo(BigDecimal("123.45"))

        Locale.setDefault(Locale("es"))
        assertThat(AmountParser.parse("123,45")).isEqualTo(BigDecimal("123.45"))
    }

    @Test(expected = ParseException::class)
    fun withGarbageAtTheBeginning_shouldFailWithException() {
        AmountParser.parse("asdf123.45")
    }

    @Test(expected = ParseException::class)
    fun withGarbageAtTheEnd_shouldFailWithException() {
        AmountParser.parse("123.45asdf")
    }

    @Test(expected = ParseException::class)
    fun emptyString_shouldFailWithException() {
        AmountParser.parse("")
    }
}