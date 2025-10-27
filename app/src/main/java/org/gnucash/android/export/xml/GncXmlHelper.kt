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
package org.gnucash.android.export.xml

import org.gnucash.android.math.toBigDecimal
import org.gnucash.android.model.Commodity
import org.gnucash.android.model.Money
import org.gnucash.android.ui.transaction.TransactionFormFragment
import org.joda.time.DateTimeZone
import org.joda.time.Instant
import org.joda.time.LocalDate
import org.joda.time.format.DateTimeFormat
import org.joda.time.format.DateTimeFormatter
import java.math.BigDecimal
import java.text.NumberFormat
import java.text.ParseException
import java.util.Calendar
import java.util.Date

/**
 * Collection of helper tags and methods for Gnc XML export
 *
 * @author Ngewi Fet <ngewif@gmail.com>
 * @author Yongxin Wang <fefe.wyx@gmail.com>
 */
object GncXmlHelper {
    const val NS_GNUCASH_PREFIX: String = "gnc"
    const val NS_GNUCASH: String = "http://www.gnucash.org/XML/gnc"
    const val NS_GNUCASH_ACCOUNT_PREFIX: String = "gnc-act"
    const val NS_GNUCASH_ACCOUNT: String = "http://www.gnucash.org/XML/gnc-act"
    const val NS_ACCOUNT_PREFIX: String = "act"
    const val NS_ACCOUNT: String = "http://www.gnucash.org/XML/act"
    const val NS_BOOK_PREFIX: String = "book"
    const val NS_BOOK: String = "http://www.gnucash.org/XML/book"
    const val NS_CD_PREFIX: String = "cd"
    const val NS_CD: String = "http://www.gnucash.org/XML/cd"
    const val NS_COMMODITY_PREFIX: String = "cmdty"
    const val NS_COMMODITY: String = "http://www.gnucash.org/XML/cmdty"
    const val NS_PRICE_PREFIX: String = "price"
    const val NS_PRICE: String = "http://www.gnucash.org/XML/price"
    const val NS_SLOT_PREFIX: String = "slot"
    const val NS_SLOT: String = "http://www.gnucash.org/XML/slot"
    const val NS_SPLIT_PREFIX: String = "split"
    const val NS_SPLIT: String = "http://www.gnucash.org/XML/split"
    const val NS_SX_PREFIX: String = "sx"
    const val NS_SX: String = "http://www.gnucash.org/XML/sx"
    const val NS_TRANSACTION_PREFIX: String = "trn"
    const val NS_TRANSACTION: String = "http://www.gnucash.org/XML/trn"
    const val NS_TS_PREFIX: String = "ts"
    const val NS_TS: String = "http://www.gnucash.org/XML/ts"
    const val NS_FS_PREFIX: String = "fs"
    const val NS_FS: String = "http://www.gnucash.org/XML/fs"
    const val NS_BUDGET_PREFIX: String = "bgt"
    const val NS_BUDGET: String = "http://www.gnucash.org/XML/bgt"
    const val NS_RECURRENCE_PREFIX: String = "recurrence"
    const val NS_RECURRENCE: String = "http://www.gnucash.org/XML/recurrence"
    const val NS_LOT_PREFIX: String = "lot"
    const val NS_LOT: String = "http://www.gnucash.org/XML/lot"
    const val NS_ADDRESS_PREFIX: String = "addr"
    const val NS_ADDRESS: String = "http://www.gnucash.org/XML/addr"
    const val NS_BILLTERM_PREFIX: String = "billterm"
    const val NS_BILLTERM: String = "http://www.gnucash.org/XML/billterm"
    const val NS_BT_DAYS_PREFIX: String = "bt-days"
    const val NS_BT_DAYS: String = "http://www.gnucash.org/XML/bt-days"
    const val NS_BT_PROX_PREFIX: String = "bt-prox"
    const val NS_BT_PROX: String = "http://www.gnucash.org/XML/bt-prox"
    const val NS_CUSTOMER_PREFIX: String = "cust"
    const val NS_CUSTOMER: String = "http://www.gnucash.org/XML/cust"
    const val NS_EMPLOYEE_PREFIX: String = "employee"
    const val NS_EMPLOYEE: String = "http://www.gnucash.org/XML/employee"
    const val NS_ENTRY_PREFIX: String = "entry"
    const val NS_ENTRY: String = "http://www.gnucash.org/XML/entry"
    const val NS_INVOICE_PREFIX: String = "invoice"
    const val NS_INVOICE: String = "http://www.gnucash.org/XML/invoice"
    const val NS_JOB_PREFIX: String = "job"
    const val NS_JOB: String = "http://www.gnucash.org/XML/job"
    const val NS_ORDER_PREFIX: String = "order"
    const val NS_ORDER: String = "http://www.gnucash.org/XML/order"
    const val NS_OWNER_PREFIX: String = "owner"
    const val NS_OWNER: String = "http://www.gnucash.org/XML/owner"
    const val NS_TAXTABLE_PREFIX: String = "taxtable"
    const val NS_TAXTABLE: String = "http://www.gnucash.org/XML/taxtable"
    const val NS_TTE_PREFIX: String = "tte"
    const val NS_TTE: String = "http://www.gnucash.org/XML/tte"
    const val NS_VENDOR_PREFIX: String = "vendor"
    const val NS_VENDOR: String = "http://www.gnucash.org/XML/vendor"

    const val ATTR_KEY_TYPE: String = "type"
    const val ATTR_KEY_DATE_POSTED: String = "date-posted"
    const val ATTR_KEY_VERSION: String = "version"
    const val ATTR_VALUE_STRING: String = "string"
    const val ATTR_VALUE_NUMERIC: String = "numeric"
    const val ATTR_VALUE_GUID: String = "guid"
    const val ATTR_VALUE_BOOK: String = "book"
    const val ATTR_VALUE_FRAME: String = "frame"
    const val ATTR_VALUE_GDATE: String = "gdate"
    const val TAG_GDATE: String = "gdate"

    /*
    Qualified GnuCash XML tag names
     */
    const val TAG_ROOT: String = "gnc-v2"
    const val TAG_BOOK: String = "book"
    const val TAG_ID: String = "id"
    const val TAG_COUNT_DATA: String = "count-data"

    const val TAG_COMMODITY: String = "commodity"
    const val TAG_FRACTION: String = "fraction"
    const val TAG_GET_QUOTES: String = "get_quotes"
    const val TAG_NAME: String = "name"
    const val TAG_QUOTE_SOURCE: String = "quote_source"
    const val TAG_QUOTE_TZ: String = "quote_tz"
    const val TAG_SPACE: String = "space"
    const val TAG_XCODE: String = "xcode"
    const val COMMODITY_CURRENCY: String = Commodity.COMMODITY_CURRENCY
    const val COMMODITY_ISO4217: String = Commodity.COMMODITY_ISO4217
    const val COMMODITY_TEMPLATE: String = Commodity.COMMODITY_CURRENCY

    const val TAG_ACCOUNT: String = "account"
    const val TAG_TYPE: String = "type"
    const val TAG_COMMODITY_SCU: String = "commodity-scu"
    const val TAG_PARENT: String = "parent"
    const val TAG_DESCRIPTION: String = "description"
    const val TAG_TITLE: String = "title"
    const val TAG_LOTS: String = "lots"

    const val TAG_KEY: String = "key"
    const val TAG_VALUE: String = "value"
    const val TAG_SLOTS: String = "slots"
    const val TAG_SLOT: String = "slot"

    const val TAG_TRANSACTION: String = "transaction"
    const val TAG_CURRENCY: String = "currency"
    const val TAG_DATE_POSTED: String = "date-posted"
    const val TAG_DATE: String = "date"
    const val TAG_DATE_ENTERED: String = "date-entered"
    const val TAG_SPLITS: String = "splits"
    const val TAG_SPLIT: String = "split"
    const val TAG_TEMPLATE_TRANSACTIONS: String = "template-transactions"

    const val TAG_MEMO: String = "memo"
    const val TAG_RECONCILED_STATE: String = "reconciled-state"
    const val TAG_RECONCILED_DATE: String = "reconciled-date"
    const val TAG_QUANTITY: String = "quantity"
    const val TAG_LOT: String = "lot"

    const val TAG_PRICEDB: String = "pricedb"
    const val TAG_PRICE: String = "price"
    const val TAG_TIME: String = "time"
    const val TAG_SOURCE: String = "source"

    /**
     * Periodicity of the recurrence.
     *
     * Only currently used for reading old backup files. May be removed in the future.
     *
     */
    @Deprecated("Use {@link #TAG_RECURRENCE} instead")
    const val TAG_RECURRENCE_PERIOD: String = "recurrence_period"

    const val TAG_SCHEDULED_ACTION: String = "schedxaction"
    const val TAG_ENABLED: String = "enabled"
    const val TAG_AUTO_CREATE: String = "autoCreate"
    const val TAG_AUTO_CREATE_NOTIFY: String = "autoCreateNotify"
    const val TAG_ADVANCE_CREATE_DAYS: String = "advanceCreateDays"
    const val TAG_ADVANCE_REMIND_DAYS: String = "advanceRemindDays"
    const val TAG_INSTANCE_COUNT: String = "instanceCount"
    const val TAG_START: String = "start"
    const val TAG_LAST: String = "last"
    const val TAG_END: String = "end"
    const val TAG_NUM_OCCUR: String = "num-occur"
    const val TAG_REM_OCCUR: String = "rem-occur"
    const val TAG_TAG: String = "tag"
    const val TAG_TEMPLATE_ACCOUNT: String = "templ-acct"
    const val TAG_SCHEDULE: String = "schedule"

    const val TAG_RECURRENCE: String = "recurrence"
    const val TAG_MULT: String = "mult"
    const val TAG_PERIOD_TYPE: String = "period_type"
    const val TAG_WEEKEND_ADJ: String = "weekend_adj"

    const val TAG_BUDGET: String = "budget"
    const val TAG_NUM_PERIODS: String = "num-periods"

    const val RECURRENCE_VERSION: String = "1.0.0"
    const val BOOK_VERSION: String = "2.0.0"
    private val TIME_FORMATTER: DateTimeFormatter =
        DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss Z").withZoneUTC()
    private val DATE_FORMATTER: DateTimeFormatter = DateTimeFormat.forPattern("yyyy-MM-dd")

    const val KEY_PLACEHOLDER: String = "placeholder"
    const val KEY_COLOR: String = "color"
    const val KEY_FAVORITE: String = "favorite"
    const val KEY_HIDDEN: String = "hidden"
    const val KEY_NOTES: String = "notes"
    const val KEY_EXPORTED: String = "exported"
    const val KEY_SCHED_XACTION: String = "sched-xaction"
    const val KEY_SPLIT_ACCOUNT_SLOT: String = "account"
    const val KEY_DEBIT_FORMULA: String = "debit-formula"
    const val KEY_CREDIT_FORMULA: String = "credit-formula"
    const val KEY_DEBIT_NUMERIC: String = "debit-numeric"
    const val KEY_CREDIT_NUMERIC: String = "credit-numeric"
    const val KEY_DEFAULT_TRANSFER_ACCOUNT: String = "default_transfer_account"

    const val CD_TYPE_BOOK: String = "book"
    const val CD_TYPE_BUDGET: String = "budget"
    const val CD_TYPE_COMMODITY: String = "commodity"
    const val CD_TYPE_ACCOUNT: String = "account"
    const val CD_TYPE_TRANSACTION: String = "transaction"
    const val CD_TYPE_SCHEDXACTION: String = "schedxaction"
    const val CD_TYPE_PRICE: String = "price"

    /**
     * Formats dates for the GnuCash XML format
     *
     * @param milliseconds Milliseconds since epoch
     */
    fun formatDate(milliseconds: Long): String {
        return DATE_FORMATTER.print(milliseconds)
    }

    /**
     * Formats dates for the GnuCash XML format
     *
     * @param date the date to format
     */
    fun formatDate(date: LocalDate): String {
        return DATE_FORMATTER.print(date)
    }

    /**
     * Formats dates for the GnuCash XML format
     *
     * @param calendar the calendar to format
     */
    fun formatDate(calendar: Calendar): String {
        val instant = Instant(calendar)
        return DATE_FORMATTER.withZone(DateTimeZone.forTimeZone(calendar.timeZone))
            .print(instant)
    }

    /**
     * Formats dates for the GnuCash XML format
     *
     * @param milliseconds Milliseconds since epoch
     */
    fun formatDateTime(milliseconds: Long): String {
        return TIME_FORMATTER.print(milliseconds)
    }

    /**
     * Formats dates for the GnuCash XML format
     *
     * @param date the date to format
     */
    fun formatDateTime(date: Date): String {
        val instant = Instant(date)
        return TIME_FORMATTER.print(instant)
    }

    /**
     * Formats dates for the GnuCash XML format
     *
     * @param date the date to format
     */
    fun formatDateTime(date: LocalDate): String {
        return TIME_FORMATTER.print(date)
    }

    /**
     * Formats dates for the GnuCash XML format
     *
     * @param calendar the calendar to format
     */
    fun formatDateTime(calendar: Calendar): String {
        val instant = Instant(calendar)
        return TIME_FORMATTER.withZone(DateTimeZone.forTimeZone(calendar.timeZone))
            .print(instant)
    }

    /**
     * Parses a date string formatted in the format "yyyy-MM-dd"
     *
     * @param dateString String date representation
     * @return Time in milliseconds since epoch
     * @throws ParseException if the date string could not be parsed e.g. because of different format
     */
    @Throws(ParseException::class)
    fun parseDate(dateString: String?): Long {
        return DATE_FORMATTER.parseMillis(dateString)
    }

    /**
     * Parses a date string formatted in the format "yyyy-MM-dd HH:mm:ss Z"
     *
     * @param dateString String date representation
     * @return Time in milliseconds since epoch
     * @throws ParseException if the date string could not be parsed e.g. because of different format
     */
    @Throws(ParseException::class)
    fun parseDateTime(dateString: String?): Long {
        return TIME_FORMATTER.parseMillis(dateString)
    }

    /**
     * Parses amount strings from GnuCash XML into [BigDecimal]s.
     * The amounts are formatted as 12345/100
     *
     * @param amountString String containing the amount
     * @return BigDecimal with numerical value
     * @throws ParseException if the amount could not be parsed
     */
    @Throws(ParseException::class)
    fun parseSplitAmount(amountString: String): BigDecimal {
        val index = amountString.indexOf('/')
        if (index < 0) {
            throw ParseException("Cannot parse money string : $amountString", 0)
        }

        val numerator =
            TransactionFormFragment.stripCurrencyFormatting(amountString.substring(0, index))
        val denominator =
            TransactionFormFragment.stripCurrencyFormatting(amountString.substring(index + 1))
        return toBigDecimal(numerator.toLong(), denominator.toLong())
    }

    /**
     * Formats money amounts for splits in the format 2550/100
     *
     * @param amount    Split amount as BigDecimal
     * @param commodity Commodity of the transaction
     * @return Formatted split amount
     */
    @Deprecated("Just use the values for numerator and denominator which are saved in the database")
    fun formatSplitAmount(amount: BigDecimal, commodity: Commodity): String {
        val denomInt = commodity.smallestFraction
        val denom = BigDecimal(denomInt)
        val denomString = denomInt.toString()

        val numerator = TransactionFormFragment.stripCurrencyFormatting(
            (amount * denom).stripTrailingZeros().toPlainString()
        )
        return "$numerator/$denomString"
    }

    fun formatFormula(amount: BigDecimal, commodity: Commodity): String {
        val money = Money(amount, commodity)
        return formatFormula(money)
    }

    fun formatFormula(money: Money): String {
        return money.formattedStringWithoutSymbol()
    }

    fun formatNumeric(numerator: Long, denominator: Long): String {
        if (denominator == 0L) return "1/0"
        if (numerator == 0L) return "0/1"
        var n = numerator
        var d = denominator
        if ((n >= 10) && (d >= 10)) {
            var n10 = n % 10L
            var d10 = d % 10L
            while ((n10 == 0L) && (d10 == 0L) && (n >= 10) && (d >= 10)) {
                n /= 10
                d /= 10
                n10 = n % 10L
                d10 = d % 10L
            }
        }
        return "$n/$d"
    }

    fun formatNumeric(money: Money): String {
        return formatNumeric(money.numerator, money.denominator)
    }
}
