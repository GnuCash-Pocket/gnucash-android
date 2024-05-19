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

package org.gnucash.android.test.unit.export;

import static org.assertj.core.api.Assertions.assertThat;

import org.gnucash.android.app.GnuCashApplication;
import org.gnucash.android.export.xml.GncXmlHelper;
import org.gnucash.android.model.Commodity;
import org.joda.time.DateTimeZone;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.junit.Test;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Test the helper methods used for generating GnuCash XML
 */
public class GncXmlHelperTest {

    private static final TimeZone TZ_UTC = DateTimeZone.UTC.toTimeZone();

    /**
     * Tests the parsing of split amounts
     */
    @Test
    public void testParseSplitAmount() throws ParseException {
        String splitAmount = "12345/100";
        BigDecimal amount = GncXmlHelper.parseSplitAmount(splitAmount);
        assertThat(amount.toPlainString()).isEqualTo("123.45");

        amount = GncXmlHelper.parseSplitAmount("1.234,50/100");
        assertThat(amount.toPlainString()).isEqualTo("1234.50");
    }

    @Test(expected = ParseException.class)
    public void shouldFailToParseWronglyFormattedInput() throws ParseException {
        GncXmlHelper.parseSplitAmount("123.45");
    }

    @Test
    public void testFormatSplitAmount() {
        Commodity usdCommodity = new Commodity("US Dollars", "USD", 100);
        Commodity euroCommodity = new Commodity("Euro", "EUR", 100);

        BigDecimal bigDecimal = new BigDecimal("45.90");
        String amount = GncXmlHelper.formatSplitAmount(bigDecimal, usdCommodity);
        assertThat(amount).isEqualTo("4590/100");


        bigDecimal = new BigDecimal("350");
        amount = GncXmlHelper.formatSplitAmount(bigDecimal, euroCommodity);
        assertThat(amount).isEqualTo("35000/100");
    }

    @Test
    public void testDateFormat_Leap_UTC() {
        Calendar cal = Calendar.getInstance();
        cal.setTimeZone(TZ_UTC);
        cal.set(Calendar.YEAR, 2024);
        cal.set(Calendar.MONTH, Calendar.FEBRUARY);
        cal.set(Calendar.DAY_OF_MONTH, 28);
        cal.set(Calendar.HOUR_OF_DAY, 12);
        cal.set(Calendar.MINUTE, 34);
        cal.set(Calendar.SECOND, 56);
        cal.set(Calendar.MILLISECOND, 999);

        String formatted = GncXmlHelper.formatDateTime(cal);
        assertThat(formatted).isEqualTo("2024-02-28 12:34:56 +0000");

        cal.add(Calendar.DAY_OF_MONTH, 1);
        formatted = GncXmlHelper.formatDateTime(cal);
        assertThat(formatted).isEqualTo("2024-02-29 12:34:56 +0000");

        cal.add(Calendar.DAY_OF_MONTH, 1);
        formatted = GncXmlHelper.formatDateTime(cal);
        assertThat(formatted).isEqualTo("2024-03-01 12:34:56 +0000");
    }

    @Test
    public void testDateFormat_Leap_0200() {
        TimeZone tz = TimeZone.getTimeZone("Europe/Kiev");
        Calendar cal = Calendar.getInstance();
        cal.setTimeZone(tz);
        cal.set(Calendar.YEAR, 2024);
        cal.set(Calendar.MONTH, Calendar.FEBRUARY);
        cal.set(Calendar.DAY_OF_MONTH, 28);
        cal.set(Calendar.HOUR_OF_DAY, 12);
        cal.set(Calendar.MINUTE, 34);
        cal.set(Calendar.SECOND, 56);
        cal.set(Calendar.MILLISECOND, 999);

        String formatted = GncXmlHelper.formatDateTime(cal);
        assertThat(formatted).isEqualTo("2024-02-28 12:34:56 +0200");

        cal.add(Calendar.DAY_OF_MONTH, 1);
        formatted = GncXmlHelper.formatDateTime(cal);
        assertThat(formatted).isEqualTo("2024-02-29 12:34:56 +0200");

        cal.add(Calendar.DAY_OF_MONTH, 1);
        formatted = GncXmlHelper.formatDateTime(cal);
        assertThat(formatted).isEqualTo("2024-03-01 12:34:56 +0200");
    }

    @Test
    public void testDateFormat_Threads() throws Throwable {
        Calendar cal = Calendar.getInstance();
        cal.setTimeZone(TZ_UTC);
        cal.set(Calendar.YEAR, 2024);
        cal.set(Calendar.MONTH, Calendar.JANUARY);
        cal.set(Calendar.DAY_OF_MONTH, 1);
        cal.set(Calendar.HOUR_OF_DAY, 12);
        cal.set(Calendar.MINUTE, 34);
        cal.set(Calendar.SECOND, 56);
        cal.set(Calendar.MILLISECOND, 999);

        FormatRunner r1 = new FormatRunner((Calendar) cal.clone(), "2024-01-01 12:34:56 +0000");
        cal.add(Calendar.DAY_OF_MONTH, 1);
        FormatRunner r2 = new FormatRunner((Calendar) cal.clone(), "2024-01-02 12:34:56 +0000");

        Thread t1 = new Thread(r1);
        Thread t2 = new Thread(r2);
        t1.start();
        t2.start();
        t1.join(2000L);
        t2.join(2000L);
        if (r1.error != null) {
            throw r1.error;
        }
        if (r2.error != null) {
            throw r2.error;
        }
    }

    private static class FormatRunner implements Runnable {

        private final Calendar calendar;
        private final String expected;
        Throwable error;

        private FormatRunner(Calendar calendar, String expected) {
            this.calendar = calendar;
            this.expected = expected;
        }

        @Override
        public void run() {
            try {
                for (int i = 0; i < 1000; i++) {
                    assertThat(GncXmlHelper.formatDateTime(calendar)).isEqualTo(expected);
                    try {
                        Thread.sleep(1);
                    } catch (InterruptedException ignore) {
                    }
                }
            } catch (Throwable e) {
                error = e;
            }
        }
    }

    @Test
    public void testDateTimeFormat_Timestamp_UTC() {
        Calendar cal = Calendar.getInstance();
        cal.setTimeZone(TZ_UTC);
        cal.set(Calendar.YEAR, 2024);
        cal.set(Calendar.MONTH, Calendar.MAY);
        cal.set(Calendar.DAY_OF_MONTH, 19);
        cal.set(Calendar.HOUR_OF_DAY, 12);
        cal.set(Calendar.MINUTE, 34);
        cal.set(Calendar.SECOND, 56);
        cal.set(Calendar.MILLISECOND, 999);

        Timestamp ts = new Timestamp(cal.getTimeInMillis());
        String formatted = GncXmlHelper.formatDateTime(ts);
        assertThat(formatted).isEqualTo("2024-05-19 12:34:56 +0000");
    }

    @Test
    public void testDateFormat_to_DateTimeFormat() {
        long now = System.currentTimeMillis();

        DateFormat df = DateFormat.getInstance();
        DateTimeFormatter dtf = DateTimeFormat.shortDateTime();
        assertThat(df.format(new Date(now))).isEqualTo(dtf.print(now));

        df = DateFormat.getDateInstance();
        dtf = DateTimeFormat.mediumDate();
        assertThat(df.format(new Date(now))).isEqualTo(dtf.print(now));

        df = DateFormat.getDateInstance(DateFormat.FULL);
        dtf = DateTimeFormat.fullDate();
        assertThat(df.format(new Date(now))).isEqualTo(dtf.print(now));
    }

    @Test
    public void testDateTimeFormat_forPattern() {
        long now = System.currentTimeMillis();

        DateFormat df = new SimpleDateFormat("E", Locale.US);
        DateTimeFormatter dtf = DateTimeFormat.forPattern("E");
        assertThat(df.format(new Date(now))).isEqualTo(dtf.print(now));

        df = new SimpleDateFormat("EEEE", GnuCashApplication.getDefaultLocale());
        dtf = DateTimeFormat.forPattern("EEEE");
        assertThat(df.format(new Date(now))).isEqualTo(dtf.print(now));

        df = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US);
        dtf = DateTimeFormat.forPattern("yyyyMMdd_HHmmss");
        assertThat(df.format(new Date(now))).isEqualTo(dtf.print(now));

        df = new SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", Locale.US);
        df.setTimeZone(TZ_UTC);
        dtf = DateTimeFormat.forPattern("yyyyMMdd'T'HHmmss'Z'").withZoneUTC();
        assertThat(df.format(new Date(now))).isEqualTo(dtf.print(now));

        df = new SimpleDateFormat("EEE, d MMM");
        dtf = DateTimeFormat.forPattern("EEE, d MMM");
        assertThat(df.format(new Date(now))).isEqualTo(dtf.print(now));
    }
}
