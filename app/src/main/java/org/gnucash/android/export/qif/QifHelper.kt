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

package org.gnucash.android.export.qif;

import org.gnucash.android.model.AccountType;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

/**
 * @author Ngewi Fet <ngewif@gmail.com>
 */
public class QifHelper {
    /*
    Prefixes for the QIF file
     */
    public static final String PAYEE_PREFIX = "P";
    public static final String DATE_PREFIX = "D";
    public static final String TOTAL_AMOUNT_PREFIX = "T";
    public static final String MEMO_PREFIX = "M";
    public static final String CATEGORY_PREFIX = "L";
    public static final String SPLIT_MEMO_PREFIX = "E";
    public static final String SPLIT_AMOUNT_PREFIX = "$";
    public static final String SPLIT_CATEGORY_PREFIX = "S";
    public static final String SPLIT_PERCENTAGE_PREFIX = "%";
    public static final String TYPE_PREFIX = "T";

    /**
     * Cash Flow: Cash Account
     */
    public static final String TYPE_CASH = "Cash";
    /**
     * Cash Flow: Checking & Savings Account
     */
    public static final String TYPE_BANK = "Bank";
    /**
     * Cash Flow: Credit Card Account
     */
    public static final String TYPE_CCARD = "CCard";
    /**
     * Investing: Investment Account
     */
    public static final String TYPE_INVEST = "Invst";
    /**
     * Property & Debt: Asset
     */
    public static final String TYPE_ASSET = "Oth A";
    /**
     * Property & Debt: Liability
     */
    public static final String TYPE_LIABILITY = "Oth L";
    public static final String TYPE_OTHER_S = "Oth S";
    public static final String TYPE_401K = "401(k)/403(b)";
    public static final String TYPE_PORT = "port";
    /**
     * Invoice (Quicken for Business only)
     */
    public static final String TYPE_INVOICE = "Invoice";
    public static final String ACCOUNT_SECTION = "!Account";
    public static final String TRANSACTION_TYPE_PREFIX = "!Type:";
    public static final String ACCOUNT_NAME_PREFIX = "N";
    public static final String ACCOUNT_DESCRIPTION_PREFIX = "D";
    public static final String NEW_LINE = "\n";
    public static final String INTERNAL_CURRENCY_PREFIX = "*";

    public static final String ENTRY_TERMINATOR = "^";
    private static final DateTimeFormatter QIF_DATE_FORMATTER = DateTimeFormat.forPattern("yyyy/M/d");

    /**
     * Formats the date for QIF in the form YYYY/MM/DD.
     * For example 25 January 2013 becomes "2013/1/25".
     *
     * @param timeMillis Time in milliseconds since epoch
     * @return Formatted date from the time
     */
    public static final String formatDate(long timeMillis) {
        return QIF_DATE_FORMATTER.print(timeMillis);
    }

    /**
     * Returns the QIF header for the transaction based on the account type.
     * By default, the QIF cash header is used
     *
     * @param accountType AccountType of account
     * @return QIF header for the transactions
     */
    public static String getQifAccountType(AccountType accountType) {
        switch (accountType) {
            case CASH:
            case INCOME:
            case EXPENSE:
            case PAYABLE:
            case RECEIVABLE:
                return TYPE_CASH;
            case CREDIT:
                return TYPE_CCARD;
            case ASSET:
            case EQUITY:
                return TYPE_ASSET;
            case LIABILITY:
                return TYPE_LIABILITY;
            case CURRENCY:
            case STOCK:
            case TRADING:
                return TYPE_INVEST;
            case BANK:
            case MUTUAL:
            default:
                return TYPE_BANK;
        }
    }

    public static String getQifAccountType(String accountType) {
        return getQifAccountType(AccountType.valueOf(accountType));
    }
}
