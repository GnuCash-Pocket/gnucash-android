/*
 * Copyright (c) 2013 - 2014 Ngewi Fet <ngewif@gmail.com>
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
package org.gnucash.android.export.qif

import org.gnucash.android.model.AccountType
import org.joda.time.format.DateTimeFormat
import org.joda.time.format.DateTimeFormatter

/**
 * @author Ngewi Fet <ngewif@gmail.com>
 */
object QifHelper {
    const val PAYEE_PREFIX: String = "P"
    const val DATE_PREFIX: String = "D"
    const val TOTAL_AMOUNT_PREFIX: String = "T"
    const val MEMO_PREFIX: String = "M"
    const val CATEGORY_PREFIX: String = "L"
    const val SPLIT_MEMO_PREFIX: String = "E"
    const val SPLIT_AMOUNT_PREFIX: String = "$"
    const val SPLIT_CATEGORY_PREFIX: String = "S"
    const val SPLIT_PERCENTAGE_PREFIX: String = "%"
    const val TYPE_PREFIX: String = "T"
    /** Number of the check. Can also be "Deposit", "Transfer", "Print", "ATM", "EFT". */
    const val NUMBER_PREFIX: String = "N"

    /**
     * Cash Flow: Cash Account
     */
    const val TYPE_CASH: String = "Cash"

    /**
     * Cash Flow: Checking & Savings Account
     */
    const val TYPE_BANK: String = "Bank"

    /**
     * Cash Flow: Credit Card Account
     */
    const val TYPE_CCARD: String = "CCard"

    /**
     * Investing: Investment Account
     */
    const val TYPE_INVEST: String = "Invst"

    /**
     * Property & Debt: Asset
     */
    const val TYPE_ASSET: String = "Oth A"

    /**
     * Property & Debt: Liability
     */
    const val TYPE_LIABILITY: String = "Oth L"
    const val TYPE_OTHER_S: String = "Oth S"
    const val TYPE_401K: String = "401(k)/403(b)"
    const val TYPE_PORT: String = "port"

    /**
     * Invoice (Quicken for Business only)
     */
    const val TYPE_INVOICE: String = "Invoice"
    const val ACCOUNT_SECTION: String = "!Account"
    const val TRANSACTION_TYPE_PREFIX: String = "!Type:"
    const val ACCOUNT_NAME_PREFIX: String = "N"
    const val ACCOUNT_DESCRIPTION_PREFIX: String = "D"
    const val NEW_LINE: String = "\n"
    const val INTERNAL_CURRENCY_PREFIX: String = "*"
    const val ENTRY_TERMINATOR: String = "^"

    private val QIF_DATE_FORMATTER: DateTimeFormatter = DateTimeFormat.forPattern("yyyy/M/d")

    /**
     * Formats the date for QIF in the form YYYY/MM/DD.
     * For example 25 January 2013 becomes "2013/1/25".
     *
     * @param timeMillis Time in milliseconds since epoch
     * @return Formatted date from the time
     */
    fun formatDate(timeMillis: Long): String? {
        return QIF_DATE_FORMATTER.print(timeMillis)
    }

    /**
     * Returns the QIF header for the transaction based on the account type.
     * By default, the QIF cash header is used
     *
     * @param accountType AccountType of account
     * @return QIF header for the transactions
     */
    fun getQifAccountType(accountType: AccountType): String {
        return when (accountType) {
            AccountType.CASH,
            AccountType.INCOME,
            AccountType.EXPENSE,
            AccountType.PAYABLE,
            AccountType.RECEIVABLE -> TYPE_CASH

            AccountType.CREDIT -> TYPE_CCARD
            AccountType.ASSET,
            AccountType.EQUITY -> TYPE_ASSET

            AccountType.LIABILITY -> TYPE_LIABILITY
            AccountType.CURRENCY,
            AccountType.STOCK,
            AccountType.TRADING -> TYPE_INVEST

            AccountType.BANK,
            AccountType.MUTUAL -> TYPE_BANK

            else -> TYPE_BANK
        }
    }

    fun getQifAccountType(accountType: String): String {
        return getQifAccountType(AccountType.valueOf(accountType))
    }
}
