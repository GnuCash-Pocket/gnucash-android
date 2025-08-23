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

    public static final String NS_GNUCASH_PREFIX = "gnc";
    public static final String NS_GNUCASH = "http://www.gnucash.org/XML/gnc";
    public static final String NS_GNUCASH_ACCOUNT_PREFIX = "gnc-act";
    public static final String NS_GNUCASH_ACCOUNT = "http://www.gnucash.org/XML/gnc-act";
    public static final String NS_ACCOUNT_PREFIX = "act";
    public static final String NS_ACCOUNT = "http://www.gnucash.org/XML/act";
    public static final String NS_BOOK_PREFIX = "book";
    public static final String NS_BOOK = "http://www.gnucash.org/XML/book";
    public static final String NS_CD_PREFIX = "cd";
    public static final String NS_CD = "http://www.gnucash.org/XML/cd";
    public static final String NS_COMMODITY_PREFIX = "cmdty";
    public static final String NS_COMMODITY = "http://www.gnucash.org/XML/cmdty";
    public static final String NS_PRICE_PREFIX = "price";
    public static final String NS_PRICE = "http://www.gnucash.org/XML/price";
    public static final String NS_SLOT_PREFIX = "slot";
    public static final String NS_SLOT = "http://www.gnucash.org/XML/slot";
    public static final String NS_SPLIT_PREFIX = "split";
    public static final String NS_SPLIT = "http://www.gnucash.org/XML/split";
    public static final String NS_SX_PREFIX = "sx";
    public static final String NS_SX = "http://www.gnucash.org/XML/sx";
    public static final String NS_TRANSACTION_PREFIX = "trn";
    public static final String NS_TRANSACTION = "http://www.gnucash.org/XML/trn";
    public static final String NS_TS_PREFIX = "ts";
    public static final String NS_TS = "http://www.gnucash.org/XML/ts";
    public static final String NS_FS_PREFIX = "fs";
    public static final String NS_FS = "http://www.gnucash.org/XML/fs";
    public static final String NS_BUDGET_PREFIX = "bgt";
    public static final String NS_BUDGET = "http://www.gnucash.org/XML/bgt";
    public static final String NS_RECURRENCE_PREFIX = "recurrence";
    public static final String NS_RECURRENCE = "http://www.gnucash.org/XML/recurrence";
    public static final String NS_LOT_PREFIX = "lot";
    public static final String NS_LOT = "http://www.gnucash.org/XML/lot";
    public static final String NS_ADDRESS_PREFIX = "addr";
    public static final String NS_ADDRESS = "http://www.gnucash.org/XML/addr";
    public static final String NS_BILLTERM_PREFIX = "billterm";
    public static final String NS_BILLTERM = "http://www.gnucash.org/XML/billterm";
    public static final String NS_BT_DAYS_PREFIX = "bt-days";
    public static final String NS_BT_DAYS = "http://www.gnucash.org/XML/bt-days";
    public static final String NS_BT_PROX_PREFIX = "bt-prox";
    public static final String NS_BT_PROX = "http://www.gnucash.org/XML/bt-prox";
    public static final String NS_CUSTOMER_PREFIX = "cust";
    public static final String NS_CUSTOMER = "http://www.gnucash.org/XML/cust";
    public static final String NS_EMPLOYEE_PREFIX = "employee";
    public static final String NS_EMPLOYEE = "http://www.gnucash.org/XML/employee";
    public static final String NS_ENTRY_PREFIX = "entry";
    public static final String NS_ENTRY = "http://www.gnucash.org/XML/entry";
    public static final String NS_INVOICE_PREFIX = "invoice";
    public static final String NS_INVOICE = "http://www.gnucash.org/XML/invoice";
    public static final String NS_JOB_PREFIX = "job";
    public static final String NS_JOB = "http://www.gnucash.org/XML/job";
    public static final String NS_ORDER_PREFIX = "order";
    public static final String NS_ORDER = "http://www.gnucash.org/XML/order";
    public static final String NS_OWNER_PREFIX = "owner";
    public static final String NS_OWNER = "http://www.gnucash.org/XML/owner";
    public static final String NS_TAXTABLE_PREFIX = "taxtable";
    public static final String NS_TAXTABLE = "http://www.gnucash.org/XML/taxtable";
    public static final String NS_TTE_PREFIX = "tte";
    public static final String NS_TTE = "http://www.gnucash.org/XML/tte";
    public static final String NS_VENDOR_PREFIX = "vendor";
    public static final String NS_VENDOR = "http://www.gnucash.org/XML/vendor";

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
    public static final String TAG_BOOK = "book";
    public static final String TAG_ID = "id";
    public static final String TAG_COUNT_DATA = "count-data";

    public static final String TAG_COMMODITY = "commodity";
    public static final String TAG_FRACTION = "fraction";
    public static final String TAG_GET_QUOTES = "get_quotes";
    public static final String TAG_NAME = "name";
    public static final String TAG_QUOTE_SOURCE = "quote_source";
    public static final String TAG_QUOTE_TZ = "quote_tz";
    public static final String TAG_SPACE = "space";
    public static final String TAG_XCODE = "xcode";
    public static final String COMMODITY_CURRENCY = Commodity.COMMODITY_CURRENCY;
    public static final String COMMODITY_ISO4217 = Commodity.COMMODITY_ISO4217;
    public static final String COMMODITY_TEMPLATE = Commodity.COMMODITY_CURRENCY;

    public static final String TAG_ACCOUNT = "account";
    public static final String TAG_TYPE = "type";
    public static final String TAG_COMMODITY_SCU = "commodity-scu";
    public static final String TAG_PARENT = "parent";
    public static final String TAG_DESCRIPTION = "description";
    public static final String TAG_TITLE = "title";
    public static final String TAG_LOTS = "lots";

    public static final String TAG_KEY = "key";
    public static final String TAG_VALUE = "value";
    public static final String TAG_SLOTS = "slots";
    public static final String TAG_SLOT = "slot";

    public static final String TAG_TRANSACTION = "transaction";
    public static final String TAG_CURRENCY = "currency";
    public static final String TAG_DATE_POSTED = "date-posted";
    public static final String TAG_DATE = "date";
    public static final String TAG_DATE_ENTERED = "date-entered";
    public static final String TAG_SPLITS = "splits";
    public static final String TAG_SPLIT = "split";
    public static final String TAG_TEMPLATE_TRANSACTIONS = "template-transactions";

    public static final String TAG_MEMO = "memo";
    public static final String TAG_RECONCILED_STATE = "reconciled-state";
    public static final String TAG_RECONCILED_DATE = "reconciled-date";
    public static final String TAG_QUANTITY = "quantity";
    public static final String TAG_LOT = "lot";

    public static final String TAG_PRICEDB = "pricedb";
    public static final String TAG_PRICE = "price";
    public static final String TAG_TIME = "time";
    public static final String TAG_SOURCE = "source";

    /**
     * Periodicity of the recurrence.
     * <p>Only currently used for reading old backup files. May be removed in the future. </p>
     *
     * @deprecated Use {@link #TAG_RECURRENCE} instead
     */
    @Deprecated
    public static final String TAG_RECURRENCE_PERIOD = "recurrence_period";

    public static final String TAG_SCHEDULED_ACTION = "schedxaction";
    public static final String TAG_ENABLED = "enabled";
    public static final String TAG_AUTO_CREATE = "autoCreate";
    public static final String TAG_AUTO_CREATE_NOTIFY = "autoCreateNotify";
    public static final String TAG_ADVANCE_CREATE_DAYS = "advanceCreateDays";
    public static final String TAG_ADVANCE_REMIND_DAYS = "advanceRemindDays";
    public static final String TAG_INSTANCE_COUNT = "instanceCount";
    public static final String TAG_START = "start";
    public static final String TAG_LAST = "last";
    public static final String TAG_END = "end";
    public static final String TAG_NUM_OCCUR = "num-occur";
    public static final String TAG_REM_OCCUR = "rem-occur";
    public static final String TAG_TAG = "tag";
    public static final String TAG_TEMPLATE_ACCOUNT = "templ-acct";
    public static final String TAG_SCHEDULE = "schedule";

    public static final String TAG_RECURRENCE = "recurrence";
    public static final String TAG_MULT = "mult";
    public static final String TAG_PERIOD_TYPE = "period_type";
    public static final String TAG_WEEKEND_ADJ = "weekend_adj";

    public static final String TAG_BUDGET = "budget";
    public static final String TAG_NUM_PERIODS = "num-periods";


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
