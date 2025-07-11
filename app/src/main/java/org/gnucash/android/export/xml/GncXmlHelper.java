/*
 * Copyright (c) 2014 - 2015 Ngewi Fet <ngewif@gmail.com>
 * Copyright (c) 2014 Yongxin Wang <fefe.wyx@gmail.com>
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

package org.gnucash.android.export.xml;

import static org.gnucash.android.math.MathExtKt.toBigDecimal;

import org.gnucash.android.model.Commodity;
import org.gnucash.android.model.Money;
import org.gnucash.android.ui.transaction.TransactionFormFragment;
import org.joda.time.DateTimeZone;
import org.joda.time.Instant;
import org.joda.time.LocalDate;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.Calendar;
import java.util.Date;

/**
 * Collection of helper tags and methods for Gnc XML export
 *
 * @author Ngewi Fet <ngewif@gmail.com>
 * @author Yongxin Wang <fefe.wyx@gmail.com>
 */
public abstract class GncXmlHelper {

    public static final String ATTR_KEY_CD_TYPE = "cd:type";
    public static final String ATTR_KEY_TYPE = "type";
    public static final String ATTR_KEY_DATE_POSTED = "date-posted";
    public static final String ATTR_KEY_VERSION = "version";
    public static final String ATTR_VALUE_STRING = "string";
    public static final String ATTR_VALUE_NUMERIC = "numeric";
    public static final String ATTR_VALUE_GUID = "guid";
    public static final String ATTR_VALUE_BOOK = "book";
    public static final String ATTR_VALUE_FRAME = "frame";
    public static final String ATTR_VALUE_GDATE = "gdate";
    public static final String TAG_GDATE = "gdate";

    /*
    Qualified GnuCash XML tag names
     */
    public static final String TAG_ROOT = "gnc-v2";
    public static final String TAG_BOOK = "gnc:book";
    public static final String TAG_BOOK_ID = "book:id";
    public static final String TAG_COUNT_DATA = "gnc:count-data";

    public static final String TAG_COMMODITY = "gnc:commodity";
    public static final String TAG_COMMODITY_FRACTION = "cmdty:fraction";
    public static final String TAG_COMMODITY_GET_QUOTES = "cmdty:get_quotes";
    public static final String TAG_COMMODITY_ID = "cmdty:id";
    public static final String TAG_COMMODITY_NAME = "cmdty:name";
    public static final String TAG_COMMODITY_QUOTE_SOURCE = "cmdty:quote_source";
    public static final String TAG_COMMODITY_QUOTE_TZ = "cmdty:quote_tz";
    public static final String TAG_COMMODITY_SLOTS = "cmdty:slots";
    public static final String TAG_COMMODITY_SPACE = "cmdty:space";
    public static final String TAG_COMMODITY_XCODE = "cmdty:xcode";
    public static final String COMMODITY_CURRENCY = Commodity.COMMODITY_CURRENCY;
    public static final String COMMODITY_ISO4217 = Commodity.COMMODITY_ISO4217;
    public static final String COMMODITY_TEMPLATE = Commodity.COMMODITY_CURRENCY;

    public static final String TAG_ACCOUNT = "gnc:account";
    public static final String TAG_ACCT_NAME = "act:name";
    public static final String TAG_ACCT_ID = "act:id";
    public static final String TAG_ACCT_TYPE = "act:type";
    public static final String TAG_ACCT_COMMODITY = "act:commodity";
    public static final String TAG_ACCT_COMMODITY_SCU = "act:commodity-scu";
    public static final String TAG_ACCT_PARENT = "act:parent";
    public static final String TAG_ACCT_DESCRIPTION = "act:description";
    public static final String TAG_ACCT_TITLE = "gnc-act:title";

    public static final String TAG_SLOT_KEY = "slot:key";
    public static final String TAG_SLOT_VALUE = "slot:value";
    public static final String TAG_ACCT_SLOTS = "act:slots";
    public static final String TAG_SLOT = "slot";

    public static final String TAG_TRANSACTION = "gnc:transaction";
    public static final String TAG_TRX_ID = "trn:id";
    public static final String TAG_TRX_CURRENCY = "trn:currency";
    public static final String TAG_DATE_POSTED = "trn:date-posted";
    public static final String TAG_TS_DATE = "ts:date";
    public static final String TAG_DATE_ENTERED = "trn:date-entered";
    public static final String TAG_TRN_DESCRIPTION = "trn:description";
    public static final String TAG_TRN_SPLITS = "trn:splits";
    public static final String TAG_TRN_SPLIT = "trn:split";
    public static final String TAG_TRN_SLOTS = "trn:slots";
    public static final String TAG_TEMPLATE_TRANSACTIONS = "gnc:template-transactions";

    public static final String TAG_SPLIT_ID = "split:id";
    public static final String TAG_SPLIT_MEMO = "split:memo";
    public static final String TAG_RECONCILED_STATE = "split:reconciled-state";
    public static final String TAG_RECONCILED_DATE = "split:recondiled-date";
    public static final String TAG_SPLIT_ACCOUNT = "split:account";
    public static final String TAG_SPLIT_VALUE = "split:value";
    public static final String TAG_SPLIT_QUANTITY = "split:quantity";
    public static final String TAG_SPLIT_SLOTS = "split:slots";

    public static final String TAG_PRICEDB = "gnc:pricedb";
    public static final String TAG_PRICE = "price";
    public static final String TAG_PRICE_ID = "price:id";
    public static final String TAG_PRICE_COMMODITY = "price:commodity";
    public static final String TAG_PRICE_CURRENCY = "price:currency";
    public static final String TAG_PRICE_TIME = "price:time";
    public static final String TAG_PRICE_SOURCE = "price:source";
    public static final String TAG_PRICE_TYPE = "price:type";
    public static final String TAG_PRICE_VALUE = "price:value";

    /**
     * Periodicity of the recurrence.
     * <p>Only currently used for reading old backup files. May be removed in the future. </p>
     *
     * @deprecated Use {@link #TAG_GNC_RECURRENCE} instead
     */
    @Deprecated
    public static final String TAG_RECURRENCE_PERIOD = "trn:recurrence_period";

    public static final String TAG_SCHEDULED_ACTION = "gnc:schedxaction";
    public static final String TAG_SX_ID = "sx:id";
    public static final String TAG_SX_NAME = "sx:name";
    public static final String TAG_SX_ENABLED = "sx:enabled";
    public static final String TAG_SX_AUTO_CREATE = "sx:autoCreate";
    public static final String TAG_SX_AUTO_CREATE_NOTIFY = "sx:autoCreateNotify";
    public static final String TAG_SX_ADVANCE_CREATE_DAYS = "sx:advanceCreateDays";
    public static final String TAG_SX_ADVANCE_REMIND_DAYS = "sx:advanceRemindDays";
    public static final String TAG_SX_INSTANCE_COUNT = "sx:instanceCount";
    public static final String TAG_SX_START = "sx:start";
    public static final String TAG_SX_LAST = "sx:last";
    public static final String TAG_SX_END = "sx:end";
    public static final String TAG_SX_NUM_OCCUR = "sx:num-occur";
    public static final String TAG_SX_REM_OCCUR = "sx:rem-occur";
    public static final String TAG_SX_TAG = "sx:tag";
    public static final String TAG_SX_TEMPL_ACCOUNT = "sx:templ-acct";
    public static final String TAG_SX_SCHEDULE = "sx:schedule";

    public static final String TAG_GNC_RECURRENCE = "gnc:recurrence";
    public static final String TAG_RX_MULT = "recurrence:mult";
    public static final String TAG_RX_PERIOD_TYPE = "recurrence:period_type";
    public static final String TAG_RX_START = "recurrence:start";
    public static final String TAG_RX_WEEKEND_ADJ = "recurrence:weekend_adj";


    public static final String TAG_BUDGET = "gnc:budget";
    public static final String TAG_BUDGET_ID = "bgt:id";
    public static final String TAG_BUDGET_NAME = "bgt:name";
    public static final String TAG_BUDGET_DESCRIPTION = "bgt:description";
    public static final String TAG_BUDGET_NUM_PERIODS = "bgt:num-periods";
    public static final String TAG_BUDGET_RECURRENCE = "bgt:recurrence";
    public static final String TAG_BUDGET_SLOTS = "bgt:slots";


    public static final String RECURRENCE_VERSION = "1.0.0";
    public static final String BOOK_VERSION = "2.0.0";
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss Z").withZoneUTC();
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormat.forPattern("yyyy-MM-dd");

    public static final String KEY_PLACEHOLDER = "placeholder";
    public static final String KEY_COLOR = "color";
    public static final String KEY_FAVORITE = "favorite";
    public static final String KEY_HIDDEN = "hidden";
    public static final String KEY_NOTES = "notes";
    public static final String KEY_EXPORTED = "exported";
    public static final String KEY_SCHED_XACTION = "sched-xaction";
    public static final String KEY_SPLIT_ACCOUNT_SLOT = "account";
    public static final String KEY_DEBIT_FORMULA = "debit-formula";
    public static final String KEY_CREDIT_FORMULA = "credit-formula";
    public static final String KEY_DEBIT_NUMERIC = "debit-numeric";
    public static final String KEY_CREDIT_NUMERIC = "credit-numeric";
    public static final String KEY_FROM_SCHED_ACTION = "from-sched-xaction";
    public static final String KEY_DEFAULT_TRANSFER_ACCOUNT = "default_transfer_account";

    public static final String CD_TYPE_BOOK = "book";
    public static final String CD_TYPE_BUDGET = "budget";
    public static final String CD_TYPE_COMMODITY = "commodity";
    public static final String CD_TYPE_ACCOUNT = "account";
    public static final String CD_TYPE_TRANSACTION = "transaction";
    public static final String CD_TYPE_SCHEDXACTION = "schedxaction";
    public static final String CD_TYPE_PRICE = "price";

    /**
     * Formats dates for the GnuCash XML format
     *
     * @param milliseconds Milliseconds since epoch
     */
    public static String formatDate(long milliseconds) {
        return DATE_FORMATTER.print(milliseconds);
    }

    /**
     * Formats dates for the GnuCash XML format
     *
     * @param date the date to format
     */
    public static String formatDate(LocalDate date) {
        return DATE_FORMATTER.print(date);
    }

    /**
     * Formats dates for the GnuCash XML format
     *
     * @param calendar the calendar to format
     */
    public static String formatDate(Calendar calendar) {
        Instant instant = new Instant(calendar);
        return DATE_FORMATTER.withZone(DateTimeZone.forTimeZone(calendar.getTimeZone())).print(instant);
    }

    /**
     * Formats dates for the GnuCash XML format
     *
     * @param milliseconds Milliseconds since epoch
     */
    public static String formatDateTime(long milliseconds) {
        return TIME_FORMATTER.print(milliseconds);
    }

    /**
     * Formats dates for the GnuCash XML format
     *
     * @param date the date to format
     */
    public static String formatDateTime(Date date) {
        Instant instant = new Instant(date);
        return TIME_FORMATTER.print(instant);
    }

    /**
     * Formats dates for the GnuCash XML format
     *
     * @param date the date to format
     */
    public static String formatDateTime(LocalDate date) {
        return TIME_FORMATTER.print(date);
    }

    /**
     * Formats dates for the GnuCash XML format
     *
     * @param calendar the calendar to format
     */
    public static String formatDateTime(Calendar calendar) {
        Instant instant = new Instant(calendar);
        return TIME_FORMATTER.withZone(DateTimeZone.forTimeZone(calendar.getTimeZone())).print(instant);
    }

    /**
     * Parses a date string formatted in the format "yyyy-MM-dd"
     *
     * @param dateString String date representation
     * @return Time in milliseconds since epoch
     * @throws ParseException if the date string could not be parsed e.g. because of different format
     */
    public static long parseDate(String dateString) throws ParseException {
        return DATE_FORMATTER.parseMillis(dateString);
    }

    /**
     * Parses a date string formatted in the format "yyyy-MM-dd HH:mm:ss Z"
     *
     * @param dateString String date representation
     * @return Time in milliseconds since epoch
     * @throws ParseException if the date string could not be parsed e.g. because of different format
     */
    public static long parseDateTime(String dateString) throws ParseException {
        return TIME_FORMATTER.parseMillis(dateString);
    }

    /**
     * Parses amount strings from GnuCash XML into {@link java.math.BigDecimal}s.
     * The amounts are formatted as 12345/100
     *
     * @param amountString String containing the amount
     * @return BigDecimal with numerical value
     * @throws ParseException if the amount could not be parsed
     */
    public static BigDecimal parseSplitAmount(String amountString) throws ParseException {
        int index = amountString.indexOf('/');
        if (index < 0) {
            throw new ParseException("Cannot parse money string : " + amountString, 0);
        }

        String numerator = TransactionFormFragment.stripCurrencyFormatting(amountString.substring(0, index));
        String denominator = TransactionFormFragment.stripCurrencyFormatting(amountString.substring(index + 1));
        return toBigDecimal(Long.parseLong(numerator), Long.parseLong(denominator));
    }

    /**
     * Formats money amounts for splits in the format 2550/100
     *
     * @param amount    Split amount as BigDecimal
     * @param commodity Commodity of the transaction
     * @return Formatted split amount
     * @deprecated Just use the values for numerator and denominator which are saved in the database
     */
    @Deprecated
    public static String formatSplitAmount(BigDecimal amount, Commodity commodity) {
        int denomInt = commodity.getSmallestFraction();
        BigDecimal denom = new BigDecimal(denomInt);
        String denomString = Integer.toString(denomInt);

        String numerator = TransactionFormFragment.stripCurrencyFormatting(amount.multiply(denom).stripTrailingZeros().toPlainString());
        return numerator + "/" + denomString;
    }

    /**
     * Format the amount in template transaction splits.
     * <p>GnuCash desktop always formats with a locale dependent format, and that varies per user.<br>
     * So we will use the device locale here and hope that the user has the same locale on the desktop GnuCash</p>
     *
     * @param amount Amount to be formatted
     * @return String representation of amount
     */
    @Deprecated
    public static String formatTemplateSplitAmount(BigDecimal amount) {
        //TODO: If we ever implement an application-specific locale setting, use it here as well
        return NumberFormat.getNumberInstance().format(amount);
    }

    public static String formatFormula(BigDecimal amount, Commodity commodity) {
        Money money = new Money(amount, commodity);
        return formatFormula(money);
    }

    public static String formatFormula(Money money) {
        return money.formattedStringWithoutSymbol();
    }

    public static String formatNumeric(long numerator, long denominator) {
        if (denominator == 0) return "1/0";
        if (numerator == 0) return "0/1";
        long n = numerator;
        long d = denominator;
        if ((n >= 10) && (d >= 10)) {
            long n10 = n % 10L;
            long d10 = d % 10L;
            while ((n10 == 0) && (d10 == 0) && (n >= 10) && (d >= 10)) {
                n /= 10;
                d /= 10;
                n10 = n % 10L;
                d10 = d % 10L;
            }
        }
        return n + "/" + d;
    }

    public static String formatNumeric(Money money) {
        return formatNumeric(money.getNumerator(), money.getDenominator());
    }
}
