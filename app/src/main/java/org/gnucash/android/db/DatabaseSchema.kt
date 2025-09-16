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
package org.gnucash.android.db

import android.database.Cursor
import android.provider.BaseColumns

/**
 * Holds the database schema
 *
 * @author Ngewi Fet <ngewif@gmail.com>
 */
object DatabaseSchema {
    /**
     * Name of database storing information about the books in the application
     */
    const val BOOK_DATABASE_NAME: String = "gnucash_books.db"

    /**
     * Version number of database containing information about the books in the application
     */
    const val BOOK_DATABASE_VERSION: Int = 1

    /**
     * Version number of database containing accounts and transactions info.
     * With any change to the database schema, this number must increase
     */
    const val DATABASE_VERSION: Int = 24

    abstract class CommonColumns : BaseColumns {
        companion object {
            @Column(value = Cursor.FIELD_TYPE_INTEGER, readOnly = true)
            const val COLUMN_ID: String = BaseColumns._ID

            @Column(Cursor.FIELD_TYPE_STRING)
            const val COLUMN_UID: String = "uid"

            @Column(Cursor.FIELD_TYPE_STRING)
            const val COLUMN_CREATED_AT: String = "created_at"

            @Column(Cursor.FIELD_TYPE_STRING)
            const val COLUMN_MODIFIED_AT: String = "modified_at"
        }
    }

    object BookEntry : CommonColumns() {
        const val TABLE_NAME: String = "books"

        @Column(Cursor.FIELD_TYPE_STRING)
        const val COLUMN_UID: String = CommonColumns.COLUMN_UID
        @Column(Cursor.FIELD_TYPE_STRING)
        const val COLUMN_ID: String = CommonColumns.COLUMN_ID
        @Column(Cursor.FIELD_TYPE_STRING)
        const val COLUMN_CREATED_AT: String = CommonColumns.COLUMN_CREATED_AT
        @Column(Cursor.FIELD_TYPE_STRING)
        const val COLUMN_MODIFIED_AT: String = CommonColumns.COLUMN_MODIFIED_AT

        @Column(Cursor.FIELD_TYPE_STRING)
        const val COLUMN_DISPLAY_NAME: String = "name"

        @Column(Cursor.FIELD_TYPE_STRING)
        const val COLUMN_SOURCE_URI: String = "uri"

        @Column(Cursor.FIELD_TYPE_STRING)
        const val COLUMN_ROOT_GUID: String = "root_account_guid"

        @Column(Cursor.FIELD_TYPE_STRING)
        const val COLUMN_TEMPLATE_GUID: String = "root_template_guid"

        @Column(Cursor.FIELD_TYPE_INTEGER)
        const val COLUMN_ACTIVE: String = "is_active"

        @Column(Cursor.FIELD_TYPE_STRING)
        const val COLUMN_LAST_SYNC: String = "last_export_time"
    }

    /**
     * Columns for the account tables
     */
    object AccountEntry : CommonColumns() {
        const val TABLE_NAME: String = "accounts"

        @Column(Cursor.FIELD_TYPE_STRING)
        const val COLUMN_UID: String = CommonColumns.COLUMN_UID
        @Column(Cursor.FIELD_TYPE_STRING)
        const val COLUMN_ID: String = CommonColumns.COLUMN_ID
        @Column(Cursor.FIELD_TYPE_STRING)
        const val COLUMN_CREATED_AT: String = CommonColumns.COLUMN_CREATED_AT
        @Column(Cursor.FIELD_TYPE_STRING)
        const val COLUMN_MODIFIED_AT: String = CommonColumns.COLUMN_MODIFIED_AT

        @Column(Cursor.FIELD_TYPE_STRING)
        const val COLUMN_NAME: String = "name"

        @Column(Cursor.FIELD_TYPE_STRING)
        @Deprecated("")
        const val COLUMN_CURRENCY: String = "currency_code"

        @Column(Cursor.FIELD_TYPE_STRING)
        const val COLUMN_COMMODITY_UID: String = "commodity_uid"

        @Column(Cursor.FIELD_TYPE_STRING)
        const val COLUMN_DESCRIPTION: String = "description"

        @Column(Cursor.FIELD_TYPE_STRING)
        const val COLUMN_PARENT_ACCOUNT_UID: String = "parent_account_uid"

        @Column(Cursor.FIELD_TYPE_INTEGER)
        const val COLUMN_PLACEHOLDER: String = "is_placeholder"

        @Column(Cursor.FIELD_TYPE_STRING)
        const val COLUMN_COLOR_CODE: String = "color_code"

        @Column(Cursor.FIELD_TYPE_INTEGER)
        const val COLUMN_FAVORITE: String = "favorite"

        @Column(Cursor.FIELD_TYPE_STRING)
        const val COLUMN_FULL_NAME: String = "full_name"

        @Column(Cursor.FIELD_TYPE_STRING)
        const val COLUMN_TYPE: String = "type"

        @Column(Cursor.FIELD_TYPE_INTEGER)
        const val COLUMN_HIDDEN: String = "is_hidden"

        @Column(Cursor.FIELD_TYPE_STRING)
        const val COLUMN_DEFAULT_TRANSFER_ACCOUNT_UID: String = "default_transfer_account_uid"

        @Column(Cursor.FIELD_TYPE_STRING)
        const val COLUMN_NOTES: String = "notes"

        @Column(Cursor.FIELD_TYPE_INTEGER)
        const val COLUMN_TEMPLATE: String = "is_template"

        /* cached parameters */
        @Column(Cursor.FIELD_TYPE_INTEGER)
        const val COLUMN_BALANCE: String = "balance"

        @Column(Cursor.FIELD_TYPE_INTEGER)
        const val COLUMN_NOCLOSING_BALANCE: String = "noclosing_balance"

        @Column(Cursor.FIELD_TYPE_INTEGER)
        const val COLUMN_CLEARED_BALANCE: String = "cleared_balance"

        @Column(Cursor.FIELD_TYPE_INTEGER)
        const val COLUMN_RECONCILED_BALANCE: String = "reconciled_balance"

        const val INDEX_UID: String = "account_uid_index"
    }

    /**
     * Column schema for the transaction table in the database
     */
    object TransactionEntry : CommonColumns() {
        const val TABLE_NAME: String = "transactions"

        @Column(Cursor.FIELD_TYPE_STRING)
        const val COLUMN_UID: String = CommonColumns.COLUMN_UID
        @Column(Cursor.FIELD_TYPE_STRING)
        const val COLUMN_ID: String = CommonColumns.COLUMN_ID
        @Column(Cursor.FIELD_TYPE_STRING)
        const val COLUMN_CREATED_AT: String = CommonColumns.COLUMN_CREATED_AT
        @Column(Cursor.FIELD_TYPE_STRING)
        const val COLUMN_MODIFIED_AT: String = CommonColumns.COLUMN_MODIFIED_AT

        //The actual names of columns for description and notes are unlike the variable names because of legacy
        //We will not change them now for backwards compatibility reasons. But the variable names make sense
        @Column(Cursor.FIELD_TYPE_STRING)
        const val COLUMN_DESCRIPTION: String = "name"

        @Column(Cursor.FIELD_TYPE_STRING)
        const val COLUMN_NOTES: String = "description"

        @Column(Cursor.FIELD_TYPE_STRING)
        @Deprecated("")
        const val COLUMN_CURRENCY: String = "currency_code"

        @Column(Cursor.FIELD_TYPE_STRING)
        const val COLUMN_COMMODITY_UID: String = "commodity_uid"

        @Column(Cursor.FIELD_TYPE_INTEGER)
        const val COLUMN_TIMESTAMP: String = "timestamp"

        /**
         * Flag for marking transactions which have been exported
         *
         */
        @Column(Cursor.FIELD_TYPE_INTEGER)
        @Deprecated("Transactions are exported based on last modified timestamp")
        const val COLUMN_EXPORTED: String = "is_exported"

        @Column(Cursor.FIELD_TYPE_INTEGER)
        const val COLUMN_TEMPLATE: String = "is_template"

        @Column(Cursor.FIELD_TYPE_STRING)
        const val COLUMN_SCHEDX_ACTION_UID: String = "scheduled_action_uid"

        const val INDEX_UID: String = "transaction_uid_index"
    }

    /**
     * Column schema for the splits table in the database
     */
    object SplitEntry : CommonColumns() {
        const val TABLE_NAME: String = "splits"

        @Column(Cursor.FIELD_TYPE_STRING)
        const val COLUMN_UID: String = CommonColumns.COLUMN_UID
        @Column(Cursor.FIELD_TYPE_STRING)
        const val COLUMN_ID: String = CommonColumns.COLUMN_ID
        @Column(Cursor.FIELD_TYPE_STRING)
        const val COLUMN_CREATED_AT: String = CommonColumns.COLUMN_CREATED_AT
        @Column(Cursor.FIELD_TYPE_STRING)
        const val COLUMN_MODIFIED_AT: String = CommonColumns.COLUMN_MODIFIED_AT

        @Column(Cursor.FIELD_TYPE_STRING)
        const val COLUMN_TYPE: String = "type"

        /**
         * The value columns are in the currency of the transaction containing the split
         */
        @Column(Cursor.FIELD_TYPE_INTEGER)
        const val COLUMN_VALUE_NUM: String = "value_num"

        @Column(Cursor.FIELD_TYPE_INTEGER)
        const val COLUMN_VALUE_DENOM: String = "value_denom"

        /**
         * The quantity columns are in the currency of the account to which the split belongs
         */
        @Column(Cursor.FIELD_TYPE_INTEGER)
        const val COLUMN_QUANTITY_NUM: String = "quantity_num"

        @Column(Cursor.FIELD_TYPE_INTEGER)
        const val COLUMN_QUANTITY_DENOM: String = "quantity_denom"

        @Column(Cursor.FIELD_TYPE_STRING)
        const val COLUMN_MEMO: String = "memo"

        @Column(Cursor.FIELD_TYPE_STRING)
        const val COLUMN_ACCOUNT_UID: String = "account_uid"

        @Column(Cursor.FIELD_TYPE_STRING)
        const val COLUMN_TRANSACTION_UID: String = "transaction_uid"

        @Column(Cursor.FIELD_TYPE_STRING)
        const val COLUMN_RECONCILE_STATE: String = "reconcile_state"

        @Column(Cursor.FIELD_TYPE_STRING)
        const val COLUMN_RECONCILE_DATE: String = "reconcile_date"

        @Column(Cursor.FIELD_TYPE_STRING)
        const val COLUMN_SCHEDX_ACTION_ACCOUNT_UID: String = "sched_account_uid"

        const val INDEX_UID: String = "split_uid_index"
    }

    object ScheduledActionEntry : CommonColumns() {
        const val TABLE_NAME: String = "scheduled_actions"

        @Column(Cursor.FIELD_TYPE_STRING)
        const val COLUMN_UID: String = CommonColumns.COLUMN_UID
        @Column(Cursor.FIELD_TYPE_STRING)
        const val COLUMN_ID: String = CommonColumns.COLUMN_ID
        @Column(Cursor.FIELD_TYPE_STRING)
        const val COLUMN_CREATED_AT: String = CommonColumns.COLUMN_CREATED_AT
        @Column(Cursor.FIELD_TYPE_STRING)
        const val COLUMN_MODIFIED_AT: String = CommonColumns.COLUMN_MODIFIED_AT

        @Column(Cursor.FIELD_TYPE_STRING)
        const val COLUMN_TYPE: String = "type"

        @Column(Cursor.FIELD_TYPE_STRING)
        const val COLUMN_ACTION_UID: String = "action_uid"

        @Column(Cursor.FIELD_TYPE_INTEGER)
        const val COLUMN_START_TIME: String = "start_time"

        @Column(Cursor.FIELD_TYPE_INTEGER)
        const val COLUMN_END_TIME: String = "end_time"

        @Column(Cursor.FIELD_TYPE_INTEGER)
        const val COLUMN_LAST_RUN: String = "last_run"

        /**
         * Tag for scheduledAction-specific information e.g. backup parameters for backup
         */
        @Column(Cursor.FIELD_TYPE_STRING)
        const val COLUMN_TAG: String = "tag"

        @Column(Cursor.FIELD_TYPE_INTEGER)
        const val COLUMN_ENABLED: String = "is_enabled"

        @Column(Cursor.FIELD_TYPE_INTEGER)
        const val COLUMN_TOTAL_FREQUENCY: String = "total_frequency"

        @Column(Cursor.FIELD_TYPE_INTEGER)
        const val COLUMN_EXECUTION_COUNT: String = "execution_count"

        @Column(Cursor.FIELD_TYPE_STRING)
        const val COLUMN_RECURRENCE_UID: String = "recurrence_uid"

        @Column(Cursor.FIELD_TYPE_INTEGER)
        const val COLUMN_AUTO_CREATE: String = "auto_create"

        @Column(Cursor.FIELD_TYPE_INTEGER)
        const val COLUMN_AUTO_NOTIFY: String = "auto_notify"

        @Column(Cursor.FIELD_TYPE_INTEGER)
        const val COLUMN_ADVANCE_CREATION: String = "adv_creation"

        @Column(Cursor.FIELD_TYPE_INTEGER)
        const val COLUMN_ADVANCE_NOTIFY: String = "adv_notify"

        @Column(Cursor.FIELD_TYPE_STRING)
        const val COLUMN_TEMPLATE_ACCT_UID: String = "template_act_uid"

        const val INDEX_UID: String = "scheduled_action_uid_index"
    }

    object CommodityEntry : CommonColumns() {
        const val TABLE_NAME: String = "commodities"

        @Column(Cursor.FIELD_TYPE_STRING)
        const val COLUMN_UID: String = CommonColumns.COLUMN_UID
        @Column(Cursor.FIELD_TYPE_STRING)
        const val COLUMN_ID: String = CommonColumns.COLUMN_ID
        @Column(Cursor.FIELD_TYPE_STRING)
        const val COLUMN_CREATED_AT: String = CommonColumns.COLUMN_CREATED_AT
        @Column(Cursor.FIELD_TYPE_STRING)
        const val COLUMN_MODIFIED_AT: String = CommonColumns.COLUMN_MODIFIED_AT

        /**
         * The namespace field denotes the namespace for this commodity,
         * either a currency or symbol from a quote source
         */
        @Column(Cursor.FIELD_TYPE_STRING)
        const val COLUMN_NAMESPACE: String = "namespace"

        /**
         * The fullname is the official full name of the currency
         */
        @Column(Cursor.FIELD_TYPE_STRING)
        const val COLUMN_FULLNAME: String = "fullname"

        /**
         * The mnemonic is the official abbreviated designation for the currency
         */
        @Column(Cursor.FIELD_TYPE_STRING)
        const val COLUMN_MNEMONIC: String = "mnemonic"

        @Column(Cursor.FIELD_TYPE_STRING)
        const val COLUMN_LOCAL_SYMBOL: String = "local_symbol"

        /**
         * The fraction is the number of sub-units that the basic commodity can be divided into
         */
        @Column(Cursor.FIELD_TYPE_INTEGER)
        const val COLUMN_SMALLEST_FRACTION: String = "fraction"

        /**
         * A CUSIP is a nine-character alphanumeric code that identifies a North American financial security
         * for the purposes of facilitating clearing and settlement of trades
         */
        @Column(Cursor.FIELD_TYPE_STRING)
        const val COLUMN_CUSIP: String = "cusip"

        /**
         * TRUE if prices are to be downloaded for this commodity from a quote source
         */
        @Column(Cursor.FIELD_TYPE_INTEGER)
        @Deprecated("")
        const val COLUMN_QUOTE_FLAG: String = "quote_flag"

        /**
         * Prices are to be downloaded for this commodity from a quote source.
         */
        @Column(Cursor.FIELD_TYPE_STRING)
        const val COLUMN_QUOTE_SOURCE: String = "quote_source"

        /**
         * Time zone of the quote source.
         */
        @Column(Cursor.FIELD_TYPE_STRING)
        const val COLUMN_QUOTE_TZ: String = "quote_tz"

        const val INDEX_UID: String = "commodities_uid_index"
    }


    object PriceEntry : CommonColumns() {
        const val TABLE_NAME: String = "prices"

        @Column(Cursor.FIELD_TYPE_STRING)
        const val COLUMN_UID: String = CommonColumns.COLUMN_UID
        @Column(Cursor.FIELD_TYPE_STRING)
        const val COLUMN_ID: String = CommonColumns.COLUMN_ID
        @Column(Cursor.FIELD_TYPE_STRING)
        const val COLUMN_CREATED_AT: String = CommonColumns.COLUMN_CREATED_AT
        @Column(Cursor.FIELD_TYPE_STRING)
        const val COLUMN_MODIFIED_AT: String = CommonColumns.COLUMN_MODIFIED_AT

        @Column(Cursor.FIELD_TYPE_STRING)
        const val COLUMN_COMMODITY_UID: String = "commodity_guid"

        @Column(Cursor.FIELD_TYPE_STRING)
        const val COLUMN_CURRENCY_UID: String = "currency_guid"

        @Column(Cursor.FIELD_TYPE_STRING)
        const val COLUMN_DATE: String = "date"

        @Column(Cursor.FIELD_TYPE_STRING)
        const val COLUMN_SOURCE: String = "source"

        @Column(Cursor.FIELD_TYPE_STRING)
        const val COLUMN_TYPE: String = "type"

        @Column(Cursor.FIELD_TYPE_INTEGER)
        const val COLUMN_VALUE_NUM: String = "value_num"

        @Column(Cursor.FIELD_TYPE_INTEGER)
        const val COLUMN_VALUE_DENOM: String = "value_denom"

        const val INDEX_UID: String = "prices_uid_index"
    }

    object BudgetEntry : CommonColumns() {
        const val TABLE_NAME: String = "budgets"

        @Column(Cursor.FIELD_TYPE_STRING)
        const val COLUMN_UID: String = CommonColumns.COLUMN_UID
        @Column(Cursor.FIELD_TYPE_STRING)
        const val COLUMN_ID: String = CommonColumns.COLUMN_ID
        @Column(Cursor.FIELD_TYPE_STRING)
        const val COLUMN_CREATED_AT: String = CommonColumns.COLUMN_CREATED_AT
        @Column(Cursor.FIELD_TYPE_STRING)
        const val COLUMN_MODIFIED_AT: String = CommonColumns.COLUMN_MODIFIED_AT

        @Column(Cursor.FIELD_TYPE_STRING)
        const val COLUMN_NAME: String = "name"

        @Column(Cursor.FIELD_TYPE_STRING)
        const val COLUMN_DESCRIPTION: String = "description"

        @Column(Cursor.FIELD_TYPE_INTEGER)
        const val COLUMN_NUM_PERIODS: String = "num_periods"

        @Column(Cursor.FIELD_TYPE_STRING)
        const val COLUMN_RECURRENCE_UID: String = "recurrence_uid"

        const val INDEX_UID: String = "budgets_uid_index"
    }

    object BudgetAmountEntry : CommonColumns() {
        const val TABLE_NAME: String = "budget_amounts"

        @Column(Cursor.FIELD_TYPE_STRING)
        const val COLUMN_UID: String = CommonColumns.COLUMN_UID
        @Column(Cursor.FIELD_TYPE_STRING)
        const val COLUMN_ID: String = CommonColumns.COLUMN_ID
        @Column(Cursor.FIELD_TYPE_STRING)
        const val COLUMN_CREATED_AT: String = CommonColumns.COLUMN_CREATED_AT
        @Column(Cursor.FIELD_TYPE_STRING)
        const val COLUMN_MODIFIED_AT: String = CommonColumns.COLUMN_MODIFIED_AT

        @Column(Cursor.FIELD_TYPE_STRING)
        const val COLUMN_BUDGET_UID: String = "budget_uid"

        @Column(Cursor.FIELD_TYPE_STRING)
        const val COLUMN_ACCOUNT_UID: String = "account_uid"

        @Column(Cursor.FIELD_TYPE_INTEGER)
        const val COLUMN_PERIOD_NUM: String = "period_num"

        @Column(Cursor.FIELD_TYPE_INTEGER)
        const val COLUMN_AMOUNT_NUM: String = "amount_num"

        @Column(Cursor.FIELD_TYPE_INTEGER)
        const val COLUMN_AMOUNT_DENOM: String = "amount_denom"
        const val COLUMN_NOTES: String = "notes"

        const val INDEX_UID: String = "budget_amounts_uid_index"
    }

    object RecurrenceEntry : CommonColumns() {
        const val TABLE_NAME: String = "recurrences"

        @Column(Cursor.FIELD_TYPE_STRING)
        const val COLUMN_UID: String = CommonColumns.COLUMN_UID
        @Column(Cursor.FIELD_TYPE_STRING)
        const val COLUMN_ID: String = CommonColumns.COLUMN_ID
        @Column(Cursor.FIELD_TYPE_STRING)
        const val COLUMN_CREATED_AT: String = CommonColumns.COLUMN_CREATED_AT
        @Column(Cursor.FIELD_TYPE_STRING)
        const val COLUMN_MODIFIED_AT: String = CommonColumns.COLUMN_MODIFIED_AT

        @Column(Cursor.FIELD_TYPE_INTEGER)
        const val COLUMN_MULTIPLIER: String = "recurrence_mult"

        @Column(Cursor.FIELD_TYPE_STRING)
        const val COLUMN_PERIOD_TYPE: String = "recurrence_period_type"

        @Column(Cursor.FIELD_TYPE_STRING)
        const val COLUMN_PERIOD_START: String = "recurrence_period_start"

        @Column(Cursor.FIELD_TYPE_STRING)
        const val COLUMN_PERIOD_END: String = "recurrence_period_end"

        @Column(Cursor.FIELD_TYPE_STRING)
        const val COLUMN_BYDAY: String = "recurrence_byday"

        const val INDEX_UID: String = "recurrence_uid_index"
    }
}
