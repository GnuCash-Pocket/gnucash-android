/*
 * Copyright (c) 2014 - 2015 Ngewi Fet <ngewif@gmail.com>
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
package org.gnucash.android.test.unit.export

import org.assertj.core.api.Assertions.assertThat
import org.gnucash.android.app.GnuCashApplication
import org.gnucash.android.export.xml.GncXmlHelper
import org.gnucash.android.model.Commodity
import org.gnucash.android.test.unit.GnuCashTest
import org.joda.time.DateTimeZone
import org.joda.time.format.DateTimeFormat
import org.junit.Test
import java.math.BigDecimal
import java.sql.Timestamp
import java.text.DateFormat
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Test the helper methods used for generating GnuCash XML
 */
class GncXmlHelperTest : GnuCashTest() {
    /**
     * Tests the parsing of split amounts
     */
    @Test
    @Throws(ParseException::class)
    fun testParseSplitAmount() {
        val splitAmount = "12345/100"
        var amount = GncXmlHelper.parseSplitAmount(splitAmount)
        assertThat(amount.toPlainString()).isEqualTo("123.45")

        amount = GncXmlHelper.parseSplitAmount("1.234,50/100")
        assertThat(amount.toPlainString()).isEqualTo("1234.50")
    }

    @Test(expected = ParseException::class)
    @Throws(ParseException::class)
    fun shouldFailToParseWronglyFormattedInput() {
        GncXmlHelper.parseSplitAmount("123.45")
    }

    @Test
    fun testFormatSplitAmount() {
        val usdCommodity = Commodity("US Dollars", "USD", 100)
        val euroCommodity = Commodity("Euro", "EUR", 100)

        var bigDecimal = BigDecimal("45.90")
        var amount = GncXmlHelper.formatSplitAmount(bigDecimal, usdCommodity)
        assertThat(amount).isEqualTo("4590/100")

        bigDecimal = BigDecimal("350")
        amount = GncXmlHelper.formatSplitAmount(bigDecimal, euroCommodity)
        assertThat(amount).isEqualTo("35000/100")
    }

    @Test
    fun testDateFormat_Leap_UTC() {
        val cal = Calendar.getInstance()
        cal.timeZone = TZ_UTC
        cal[Calendar.YEAR] = 2024
        cal[Calendar.MONTH] = Calendar.FEBRUARY
        cal[Calendar.DAY_OF_MONTH] = 28
        cal[Calendar.HOUR_OF_DAY] = 12
        cal[Calendar.MINUTE] = 34
        cal[Calendar.SECOND] = 56
        cal[Calendar.MILLISECOND] = 999

        var formatted = GncXmlHelper.formatDateTime(cal)
        assertThat(formatted).isEqualTo("2024-02-28 12:34:56 +0000")

        cal.add(Calendar.DAY_OF_MONTH, 1)
        formatted = GncXmlHelper.formatDateTime(cal)
        assertThat(formatted).isEqualTo("2024-02-29 12:34:56 +0000")

        cal.add(Calendar.DAY_OF_MONTH, 1)
        formatted = GncXmlHelper.formatDateTime(cal)
        assertThat(formatted).isEqualTo("2024-03-01 12:34:56 +0000")
    }

    @Test
    fun testDateFormat_Leap_0200() {
        val tz = TimeZone.getTimeZone("Europe/Kiev")
        val cal = Calendar.getInstance()
        cal.timeZone = tz
        cal[Calendar.YEAR] = 2024
        cal[Calendar.MONTH] = Calendar.FEBRUARY
        cal[Calendar.DAY_OF_MONTH] = 28
        cal[Calendar.HOUR_OF_DAY] = 12
        cal[Calendar.MINUTE] = 34
        cal[Calendar.SECOND] = 56
        cal[Calendar.MILLISECOND] = 999

        var formatted = GncXmlHelper.formatDateTime(cal)
        assertThat(formatted).isEqualTo("2024-02-28 12:34:56 +0200")

        cal.add(Calendar.DAY_OF_MONTH, 1)
        formatted = GncXmlHelper.formatDateTime(cal)
        assertThat(formatted).isEqualTo("2024-02-29 12:34:56 +0200")

        cal.add(Calendar.DAY_OF_MONTH, 1)
        formatted = GncXmlHelper.formatDateTime(cal)
        assertThat(formatted).isEqualTo("2024-03-01 12:34:56 +0200")
    }

    @Test
    fun testDateFormat_Threads() {
        val cal = Calendar.getInstance()
        cal.timeZone = TZ_UTC
        cal[Calendar.YEAR] = 2024
        cal[Calendar.MONTH] = Calendar.JANUARY
        cal[Calendar.DAY_OF_MONTH] = 1
        cal[Calendar.HOUR_OF_DAY] = 12
        cal[Calendar.MINUTE] = 34
        cal[Calendar.SECOND] = 56
        cal[Calendar.MILLISECOND] = 999

        val r1 = FormatRunner(cal.clone() as Calendar, "2024-01-01 12:34:56 +0000")
        cal.add(Calendar.DAY_OF_MONTH, 1)
        val r2 = FormatRunner(cal.clone() as Calendar, "2024-01-02 12:34:56 +0000")

        val t1 = Thread(r1)
        val t2 = Thread(r2)
        t1.start()
        t2.start()
        t1.join(2000L)
        t2.join(2000L)
        if (r1.error != null) throw r1.error!!
        if (r2.error != null) throw r2.error!!
    }

    private class FormatRunner(private val calendar: Calendar, private val expected: String) :
        Runnable {
        var error: Throwable? = null

        override fun run() {
            try {
                for (i in 0..999) {
                    assertThat(GncXmlHelper.formatDateTime(calendar)).isEqualTo(expected)
                    try {
                        Thread.sleep(1)
                    } catch (ignore: InterruptedException) {
                    }
                }
            } catch (e: Throwable) {
                error = e
            }
        }
    }

    @Test
    fun testDateTimeFormat_Timestamp_UTC() {
        val cal = Calendar.getInstance()
        cal.timeZone = TZ_UTC
        cal[Calendar.YEAR] = 2024
        cal[Calendar.MONTH] = Calendar.MAY
        cal[Calendar.DAY_OF_MONTH] = 19
        cal[Calendar.HOUR_OF_DAY] = 12
        cal[Calendar.MINUTE] = 34
        cal[Calendar.SECOND] = 56
        cal[Calendar.MILLISECOND] = 999

        val ts = Timestamp(cal.timeInMillis)
        val formatted = GncXmlHelper.formatDateTime(ts)
        assertThat(formatted).isEqualTo("2024-05-19 12:34:56 +0000")
    }

    @Test
    fun testDateFormat_to_DateTimeFormat() {
        val now = System.currentTimeMillis()

        var df = DateFormat.getInstance()
        var dtf = DateTimeFormat.shortDateTime()
        assertThat(df.format(Date(now))).isEqualTo(dtf.print(now))

        df = DateFormat.getDateInstance()
        dtf = DateTimeFormat.mediumDate()
        assertThat(df.format(Date(now))).isEqualTo(dtf.print(now))

        df = DateFormat.getDateInstance(DateFormat.FULL)
        dtf = DateTimeFormat.fullDate()
        assertThat(df.format(Date(now))).isEqualTo(dtf.print(now))
    }

    @Test
    fun testDateTimeFormat_forPattern() {
        val now = System.currentTimeMillis()

        var df: DateFormat = SimpleDateFormat("E", Locale.US)
        var dtf = DateTimeFormat.forPattern("E")
        assertThat(df.format(Date(now))).isEqualTo(dtf.print(now))

        df = SimpleDateFormat("EEEE", GnuCashApplication.getDefaultLocale())
        dtf = DateTimeFormat.forPattern("EEEE")
        assertThat(df.format(Date(now))).isEqualTo(dtf.print(now))

        df = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
        dtf = DateTimeFormat.forPattern("yyyyMMdd_HHmmss")
        assertThat(df.format(Date(now))).isEqualTo(dtf.print(now))

        df = SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", Locale.US)
        df.setTimeZone(TZ_UTC)
        dtf = DateTimeFormat.forPattern("yyyyMMdd'T'HHmmss'Z'").withZoneUTC()
        assertThat(df.format(Date(now))).isEqualTo(dtf.print(now))

        df = SimpleDateFormat("EEE, d MMM")
        dtf = DateTimeFormat.forPattern("EEE, d MMM")
        assertThat(df.format(Date(now))).isEqualTo(dtf.print(now))
    }

    companion object {
        private val TZ_UTC: TimeZone = DateTimeZone.UTC.toTimeZone()
    }
}
