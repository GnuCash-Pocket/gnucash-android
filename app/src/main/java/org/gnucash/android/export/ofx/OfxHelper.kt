/*
 * Copyright (c) 2014 Ngewi Fet <ngewif@gmail.com>
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
package org.gnucash.android.export.ofx

import android.text.format.DateUtils
import org.gnucash.android.BuildConfig
import org.gnucash.android.model.Account
import org.gnucash.android.model.AccountType
import org.joda.time.DateTimeZone
import org.joda.time.format.DateTimeFormat
import java.util.Locale
import java.util.TimeZone
import kotlin.math.absoluteValue

/**
 * Helper class with collection of useful method and constants for the OFX export
 *
 * @author Ngewi Fet <ngewif@gmail.com>
 */
object OfxHelper {
    /**
     * A date formatter used when creating file names for the exported data
     */
    const val OFX_DATE_PATTERN: String = "yyyyMMddHHmmss.SSS"

    /**
     * The Transaction ID is usually the client ID sent in a request.
     * Since the data exported is not as a result of a request, we use 0
     */
    const val UNSOLICITED_TRANSACTION_ID: String = "0"

    /**
     * Header for OFX documents
     */
    const val OFX_HEADER: String =
        "OFX OFXHEADER=\"200\" VERSION=\"211\" SECURITY=\"NONE\" OLDFILEUID=\"NONE\" NEWFILEUID=\"NONE\""

    /**
     * SGML header for OFX. Used for compatibility with desktop GnuCash
     */
    const val OFX_SGML_HEADER: String =
        "ENCODING:UTF-8\nOFXHEADER:100\nDATA:OFXSGML\nVERSION:211\nSECURITY:NONE\nCHARSET:UTF-8\nCOMPRESSION:NONE\nOLDFILEUID:NONE\nNEWFILEUID:NONE\n"

    const val TAG_ROOT = "OFX"
    /*
     * XML tag name constants for the OFX file
     */
    const val TAG_TRANSACTION_UID: String = "TRNUID"
    const val TAG_BANK_MESSAGES_V1: String = "BANKMSGSRSV1"
    // Default currency for the statement
    const val TAG_CURRENCY_DEF: String = "CURDEF"
    const val TAG_BANK_ID: String = "BANKID"
    const val TAG_ACCOUNT_ID: String = "ACCTID"
    const val TAG_ACCOUNT_TYPE: String = "ACCTTYPE"
    // Account-from aggregate
    const val TAG_BANK_ACCOUNT_FROM: String = "BANKACCTFROM"
    // Ledger balance amount
    const val TAG_BALANCE_AMOUNT: String = "BALAMT"
    // Balance date
    const val TAG_DATE_AS_OF: String = "DTASOF"
    // Ledger balance aggregate
    const val TAG_LEDGER_BALANCE: String = "LEDGERBAL"
    // Start date for transaction data
    const val TAG_DATE_START: String = "DTSTART"
    // Value that client should send in next <DTSTART> request to ensure that it does not miss any transactions
    const val TAG_DATE_END: String = "DTEND"
    const val TAG_TRANSACTION_TYPE: String = "TRNTYPE"
    const val TAG_DATE_POSTED: String = "DTPOSTED"
    const val TAG_DATE_USER: String = "DTUSER"
    const val TAG_TRANSACTION_AMOUNT: String = "TRNAMT"
    // Financial Institution Transaction ID
    const val TAG_TRANSACTION_FITID: String = "FITID"
    const val TAG_NAME: String = "NAME"
    const val TAG_MEMO: String = "MEMO"
    const val TAG_BANK_ACCOUNT_TO: String = "BANKACCTTO"
    // statement transaction data
    const val TAG_BANK_TRANSACTION_LIST: String = "BANKTRANLIST"
    // Statement-response aggregate
    const val TAG_STATEMENT_TRANSACTIONS: String = "STMTRS"
    // statement transaction
    const val TAG_STATEMENT_TRANSACTION: String = "STMTTRN"
    const val TAG_STATEMENT_TRANSACTION_RESPONSE: String = "STMTTRNRS"
    const val TAG_CHECK_NUMBER: String = "CHECKNUM"

    // Credit Card Message Set Response Messages
    const val TAG_CC_MESSAGES_V1: String = "CREDITCARDMSGSRSV1"
    // The credit card download response
    const val TAG_CC_STATEMENT_TRANSACTION_RESPONSE: String = "CCSTMTTRNRS"
    // Credit-card-download-response aggregate
    const val TAG_CC_STATEMENT_TRANSACTIONS: String = "CCSTMTRS"
    // Account from aggregate
    const val TAG_CC_ACCOUNT_FROM: String = "CCACCTFROM"
    const val TAG_CC_ACCOUNT_TO: String = "CACCTTO"

    // TODO: investigate whether skipping the imbalance accounts makes sense.
    // Also, using locale-dependant names here is error-prone.
    const val IGNORE_IMBALANCE = true

    /**
     * ID which will be used as the bank ID for OFX from this app
     */
    var APP_ID: String = BuildConfig.APPLICATION_ID

    /**
     * Returns the current time formatted using the pattern [.OFX_DATE_PATTERN]
     *
     * @return Current time as a formatted string
     * @see .getOfxFormattedTime
     */
    val formattedCurrentTime: String
        get() = formatTime(System.currentTimeMillis())

    /**
     * Returns a formatted string representation of time in `milliseconds`.
     * According to the OFX Banking Specification,
     * "The complete form is: YYYYMMDDHHMMSS.XXX [gmt offset[:tz name]]"
     * "For example, “19961005132200.124[-5:EST]” represents October 5, 1996, at 1:22 and 124 milliseconds p.m., in Eastern Standard Time.
     * This is the same as 6:22 p.m. Greenwich Mean Time (GMT)."
     *
     * @param time Long value representing the time to be formatted
     * @return Formatted string representation of time in `milliseconds`
     */
    fun formatTime(time: Long): String {
        return formatTime(time, TimeZone.getDefault())
    }

    /**
     * Returns a formatted string representation of time in `milliseconds`.
     * According to the OFX Banking Specification,
     * "The complete form is: YYYYMMDDHHMMSS.XXX [gmt offset[:tz name]]"
     * "For example, “19961005132200.124[-5:EST]” represents October 5, 1996, at 1:22 and 124 milliseconds p.m., in Eastern Standard Time.
     * This is the same as 6:22 p.m. Greenwich Mean Time (GMT)."
     *
     * @param date     Long value representing the time to be formatted
     * @param timeZone the time zone.
     * @return Formatted string representation of time in `milliseconds`
     */
    fun formatTime(date: Long, timeZone: TimeZone): String {
        val zone = DateTimeZone.forTimeZone(timeZone)
        val formatter = DateTimeFormat.forPattern(OFX_DATE_PATTERN).withZone(zone)
        val offsetMillis = zone.getOffset(date)
        val hours = ((offsetMillis / DateUtils.HOUR_IN_MILLIS) % 24).absoluteValue
        val sign = if (offsetMillis > 0) "+" else if (offsetMillis < 0) "-" else ""
        val tzName = zone.getShortName(date, Locale.ROOT)
        return formatter.print(date) + "[" + sign + hours + ":" + tzName + "]"
    }

    val Account.isBanking: Boolean get() {
        val type = OfxAccountType.of(type)
        return (type == OfxAccountType.CHECKING) || (type == OfxAccountType.SAVINGS)
    }

    val Account.isCreditCard: Boolean get() {
        return type == AccountType.CREDIT
    }
}
