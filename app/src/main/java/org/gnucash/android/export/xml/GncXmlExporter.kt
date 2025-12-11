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

import android.content.Context
import android.os.SystemClock
import org.gnucash.android.db.DatabaseSchema.AccountEntry
import org.gnucash.android.db.DatabaseSchema.SplitEntry
import org.gnucash.android.db.DatabaseSchema.TransactionEntry
import org.gnucash.android.db.adapter.AccountsDbAdapter
import org.gnucash.android.db.forEach
import org.gnucash.android.db.getLong
import org.gnucash.android.db.getString
import org.gnucash.android.export.ExportException
import org.gnucash.android.export.ExportParams
import org.gnucash.android.export.Exporter
import org.gnucash.android.export.xml.GncXmlHelper.ATTR_KEY_DATE_POSTED
import org.gnucash.android.export.xml.GncXmlHelper.ATTR_KEY_TYPE
import org.gnucash.android.export.xml.GncXmlHelper.ATTR_KEY_VERSION
import org.gnucash.android.export.xml.GncXmlHelper.ATTR_VALUE_FRAME
import org.gnucash.android.export.xml.GncXmlHelper.ATTR_VALUE_GUID
import org.gnucash.android.export.xml.GncXmlHelper.BOOK_VERSION
import org.gnucash.android.export.xml.GncXmlHelper.CD_TYPE_ACCOUNT
import org.gnucash.android.export.xml.GncXmlHelper.CD_TYPE_BOOK
import org.gnucash.android.export.xml.GncXmlHelper.CD_TYPE_BUDGET
import org.gnucash.android.export.xml.GncXmlHelper.CD_TYPE_COMMODITY
import org.gnucash.android.export.xml.GncXmlHelper.CD_TYPE_PRICE
import org.gnucash.android.export.xml.GncXmlHelper.CD_TYPE_SCHEDXACTION
import org.gnucash.android.export.xml.GncXmlHelper.CD_TYPE_TRANSACTION
import org.gnucash.android.export.xml.GncXmlHelper.KEY_COLOR
import org.gnucash.android.export.xml.GncXmlHelper.KEY_CREDIT_FORMULA
import org.gnucash.android.export.xml.GncXmlHelper.KEY_CREDIT_NUMERIC
import org.gnucash.android.export.xml.GncXmlHelper.KEY_DEBIT_FORMULA
import org.gnucash.android.export.xml.GncXmlHelper.KEY_DEBIT_NUMERIC
import org.gnucash.android.export.xml.GncXmlHelper.KEY_DEFAULT_TRANSFER_ACCOUNT
import org.gnucash.android.export.xml.GncXmlHelper.KEY_FAVORITE
import org.gnucash.android.export.xml.GncXmlHelper.KEY_HIDDEN
import org.gnucash.android.export.xml.GncXmlHelper.KEY_NOTES
import org.gnucash.android.export.xml.GncXmlHelper.KEY_PLACEHOLDER
import org.gnucash.android.export.xml.GncXmlHelper.KEY_SCHED_XACTION
import org.gnucash.android.export.xml.GncXmlHelper.KEY_SPLIT_ACCOUNT_SLOT
import org.gnucash.android.export.xml.GncXmlHelper.NS_ACCOUNT
import org.gnucash.android.export.xml.GncXmlHelper.NS_ACCOUNT_PREFIX
import org.gnucash.android.export.xml.GncXmlHelper.NS_ADDRESS
import org.gnucash.android.export.xml.GncXmlHelper.NS_ADDRESS_PREFIX
import org.gnucash.android.export.xml.GncXmlHelper.NS_BILLTERM
import org.gnucash.android.export.xml.GncXmlHelper.NS_BILLTERM_PREFIX
import org.gnucash.android.export.xml.GncXmlHelper.NS_BOOK
import org.gnucash.android.export.xml.GncXmlHelper.NS_BOOK_PREFIX
import org.gnucash.android.export.xml.GncXmlHelper.NS_BT_DAYS
import org.gnucash.android.export.xml.GncXmlHelper.NS_BT_DAYS_PREFIX
import org.gnucash.android.export.xml.GncXmlHelper.NS_BT_PROX
import org.gnucash.android.export.xml.GncXmlHelper.NS_BT_PROX_PREFIX
import org.gnucash.android.export.xml.GncXmlHelper.NS_BUDGET
import org.gnucash.android.export.xml.GncXmlHelper.NS_BUDGET_PREFIX
import org.gnucash.android.export.xml.GncXmlHelper.NS_CD
import org.gnucash.android.export.xml.GncXmlHelper.NS_CD_PREFIX
import org.gnucash.android.export.xml.GncXmlHelper.NS_COMMODITY
import org.gnucash.android.export.xml.GncXmlHelper.NS_COMMODITY_PREFIX
import org.gnucash.android.export.xml.GncXmlHelper.NS_CUSTOMER
import org.gnucash.android.export.xml.GncXmlHelper.NS_CUSTOMER_PREFIX
import org.gnucash.android.export.xml.GncXmlHelper.NS_EMPLOYEE
import org.gnucash.android.export.xml.GncXmlHelper.NS_EMPLOYEE_PREFIX
import org.gnucash.android.export.xml.GncXmlHelper.NS_ENTRY
import org.gnucash.android.export.xml.GncXmlHelper.NS_ENTRY_PREFIX
import org.gnucash.android.export.xml.GncXmlHelper.NS_FS
import org.gnucash.android.export.xml.GncXmlHelper.NS_FS_PREFIX
import org.gnucash.android.export.xml.GncXmlHelper.NS_GNUCASH
import org.gnucash.android.export.xml.GncXmlHelper.NS_GNUCASH_PREFIX
import org.gnucash.android.export.xml.GncXmlHelper.NS_INVOICE
import org.gnucash.android.export.xml.GncXmlHelper.NS_INVOICE_PREFIX
import org.gnucash.android.export.xml.GncXmlHelper.NS_JOB
import org.gnucash.android.export.xml.GncXmlHelper.NS_JOB_PREFIX
import org.gnucash.android.export.xml.GncXmlHelper.NS_LOT
import org.gnucash.android.export.xml.GncXmlHelper.NS_LOT_PREFIX
import org.gnucash.android.export.xml.GncXmlHelper.NS_ORDER
import org.gnucash.android.export.xml.GncXmlHelper.NS_ORDER_PREFIX
import org.gnucash.android.export.xml.GncXmlHelper.NS_OWNER
import org.gnucash.android.export.xml.GncXmlHelper.NS_OWNER_PREFIX
import org.gnucash.android.export.xml.GncXmlHelper.NS_PRICE
import org.gnucash.android.export.xml.GncXmlHelper.NS_PRICE_PREFIX
import org.gnucash.android.export.xml.GncXmlHelper.NS_RECURRENCE
import org.gnucash.android.export.xml.GncXmlHelper.NS_RECURRENCE_PREFIX
import org.gnucash.android.export.xml.GncXmlHelper.NS_SLOT
import org.gnucash.android.export.xml.GncXmlHelper.NS_SLOT_PREFIX
import org.gnucash.android.export.xml.GncXmlHelper.NS_SPLIT
import org.gnucash.android.export.xml.GncXmlHelper.NS_SPLIT_PREFIX
import org.gnucash.android.export.xml.GncXmlHelper.NS_SX
import org.gnucash.android.export.xml.GncXmlHelper.NS_SX_PREFIX
import org.gnucash.android.export.xml.GncXmlHelper.NS_TAXTABLE
import org.gnucash.android.export.xml.GncXmlHelper.NS_TAXTABLE_PREFIX
import org.gnucash.android.export.xml.GncXmlHelper.NS_TRANSACTION
import org.gnucash.android.export.xml.GncXmlHelper.NS_TRANSACTION_PREFIX
import org.gnucash.android.export.xml.GncXmlHelper.NS_TS
import org.gnucash.android.export.xml.GncXmlHelper.NS_TS_PREFIX
import org.gnucash.android.export.xml.GncXmlHelper.NS_TTE
import org.gnucash.android.export.xml.GncXmlHelper.NS_TTE_PREFIX
import org.gnucash.android.export.xml.GncXmlHelper.NS_VENDOR
import org.gnucash.android.export.xml.GncXmlHelper.NS_VENDOR_PREFIX
import org.gnucash.android.export.xml.GncXmlHelper.RECURRENCE_VERSION
import org.gnucash.android.export.xml.GncXmlHelper.TAG_ACCOUNT
import org.gnucash.android.export.xml.GncXmlHelper.TAG_ADVANCE_CREATE_DAYS
import org.gnucash.android.export.xml.GncXmlHelper.TAG_ADVANCE_REMIND_DAYS
import org.gnucash.android.export.xml.GncXmlHelper.TAG_AUTO_CREATE
import org.gnucash.android.export.xml.GncXmlHelper.TAG_AUTO_CREATE_NOTIFY
import org.gnucash.android.export.xml.GncXmlHelper.TAG_BOOK
import org.gnucash.android.export.xml.GncXmlHelper.TAG_BUDGET
import org.gnucash.android.export.xml.GncXmlHelper.TAG_CODE
import org.gnucash.android.export.xml.GncXmlHelper.TAG_COMMODITY
import org.gnucash.android.export.xml.GncXmlHelper.TAG_COMMODITY_SCU
import org.gnucash.android.export.xml.GncXmlHelper.TAG_COUNT_DATA
import org.gnucash.android.export.xml.GncXmlHelper.TAG_CURRENCY
import org.gnucash.android.export.xml.GncXmlHelper.TAG_DATE
import org.gnucash.android.export.xml.GncXmlHelper.TAG_DATE_ENTERED
import org.gnucash.android.export.xml.GncXmlHelper.TAG_DATE_POSTED
import org.gnucash.android.export.xml.GncXmlHelper.TAG_DESCRIPTION
import org.gnucash.android.export.xml.GncXmlHelper.TAG_ENABLED
import org.gnucash.android.export.xml.GncXmlHelper.TAG_END
import org.gnucash.android.export.xml.GncXmlHelper.TAG_FRACTION
import org.gnucash.android.export.xml.GncXmlHelper.TAG_GDATE
import org.gnucash.android.export.xml.GncXmlHelper.TAG_GET_QUOTES
import org.gnucash.android.export.xml.GncXmlHelper.TAG_ID
import org.gnucash.android.export.xml.GncXmlHelper.TAG_INSTANCE_COUNT
import org.gnucash.android.export.xml.GncXmlHelper.TAG_KEY
import org.gnucash.android.export.xml.GncXmlHelper.TAG_LAST
import org.gnucash.android.export.xml.GncXmlHelper.TAG_MEMO
import org.gnucash.android.export.xml.GncXmlHelper.TAG_MULT
import org.gnucash.android.export.xml.GncXmlHelper.TAG_NAME
import org.gnucash.android.export.xml.GncXmlHelper.TAG_NUM
import org.gnucash.android.export.xml.GncXmlHelper.TAG_NUM_OCCUR
import org.gnucash.android.export.xml.GncXmlHelper.TAG_NUM_PERIODS
import org.gnucash.android.export.xml.GncXmlHelper.TAG_PARENT
import org.gnucash.android.export.xml.GncXmlHelper.TAG_PERIOD_TYPE
import org.gnucash.android.export.xml.GncXmlHelper.TAG_PRICE
import org.gnucash.android.export.xml.GncXmlHelper.TAG_PRICEDB
import org.gnucash.android.export.xml.GncXmlHelper.TAG_QUANTITY
import org.gnucash.android.export.xml.GncXmlHelper.TAG_QUOTE_SOURCE
import org.gnucash.android.export.xml.GncXmlHelper.TAG_QUOTE_TZ
import org.gnucash.android.export.xml.GncXmlHelper.TAG_RECONCILED_STATE
import org.gnucash.android.export.xml.GncXmlHelper.TAG_RECURRENCE
import org.gnucash.android.export.xml.GncXmlHelper.TAG_REM_OCCUR
import org.gnucash.android.export.xml.GncXmlHelper.TAG_ROOT
import org.gnucash.android.export.xml.GncXmlHelper.TAG_SCHEDULE
import org.gnucash.android.export.xml.GncXmlHelper.TAG_SCHEDULED_ACTION
import org.gnucash.android.export.xml.GncXmlHelper.TAG_SLOT
import org.gnucash.android.export.xml.GncXmlHelper.TAG_SLOTS
import org.gnucash.android.export.xml.GncXmlHelper.TAG_SOURCE
import org.gnucash.android.export.xml.GncXmlHelper.TAG_SPACE
import org.gnucash.android.export.xml.GncXmlHelper.TAG_SPLIT
import org.gnucash.android.export.xml.GncXmlHelper.TAG_SPLITS
import org.gnucash.android.export.xml.GncXmlHelper.TAG_START
import org.gnucash.android.export.xml.GncXmlHelper.TAG_TAG
import org.gnucash.android.export.xml.GncXmlHelper.TAG_TEMPLATE_ACCOUNT
import org.gnucash.android.export.xml.GncXmlHelper.TAG_TEMPLATE_TRANSACTIONS
import org.gnucash.android.export.xml.GncXmlHelper.TAG_TIME
import org.gnucash.android.export.xml.GncXmlHelper.TAG_TIME64
import org.gnucash.android.export.xml.GncXmlHelper.TAG_TRANSACTION
import org.gnucash.android.export.xml.GncXmlHelper.TAG_TYPE
import org.gnucash.android.export.xml.GncXmlHelper.TAG_VALUE
import org.gnucash.android.export.xml.GncXmlHelper.TAG_XCODE
import org.gnucash.android.export.xml.GncXmlHelper.formatDate
import org.gnucash.android.export.xml.GncXmlHelper.formatDateTime
import org.gnucash.android.export.xml.GncXmlHelper.formatFormula
import org.gnucash.android.gnc.GncProgressListener
import org.gnucash.android.importer.xml.CommoditiesXmlHandler
import org.gnucash.android.math.toBigDecimal
import org.gnucash.android.model.Account
import org.gnucash.android.model.AccountType
import org.gnucash.android.model.BaseModel.Companion.generateUID
import org.gnucash.android.model.Book
import org.gnucash.android.model.Budget
import org.gnucash.android.model.Commodity
import org.gnucash.android.model.Price
import org.gnucash.android.model.Recurrence
import org.gnucash.android.model.ScheduledAction
import org.gnucash.android.model.Slot
import org.gnucash.android.model.Transaction
import org.gnucash.android.model.TransactionType
import org.gnucash.android.model.WeekendAdjust
import org.gnucash.android.util.TimestampHelper.getTimestampFromUtcString
import org.gnucash.android.util.formatRGB
import org.xmlpull.v1.XmlPullParserFactory
import org.xmlpull.v1.XmlSerializer
import timber.log.Timber
import java.io.IOException
import java.io.Writer
import java.nio.charset.StandardCharsets
import java.util.TreeMap

/**
 * Creates a GnuCash XML representation of the accounts and transactions
 *
 * @author Ngewi Fet <ngewif@gmail.com>
 * @author Yongxin Wang <fefe.wyx@gmail.com>
 */
class GncXmlExporter(
    context: Context,
    params: ExportParams,
    bookUID: String,
    listener: GncProgressListener? = null
) : Exporter(context, params, bookUID, listener) {
    /**
     * Root account for template accounts
     */
    private var rootTemplateAccount: Account? = null
        get() {
            var account = field
            if (account != null) {
                return account
            }
            var where = AccountEntry.COLUMN_TYPE + "=? AND " + AccountEntry.COLUMN_TEMPLATE + "=1"
            var whereArgs = arrayOf<String?>(AccountType.ROOT.name)
            var accounts =
                accountsDbAdapter.getAllRecords(where, whereArgs)
            if (accounts.isEmpty()) {
                val commodity = commoditiesDbAdapter.getCurrency(Commodity.TEMPLATE)
                if (commodity != null) {
                    Commodity.template.setUID(commodity.uid)
                    where =
                        AccountEntry.COLUMN_TYPE + "=? AND " + AccountEntry.COLUMN_COMMODITY_UID + "=?"
                    whereArgs = arrayOf(
                        AccountType.ROOT.name,
                        commodity.uid
                    )
                } else {
                    where = AccountEntry.COLUMN_TYPE + "=? AND " + AccountEntry.COLUMN_NAME + "=?"
                    whereArgs = arrayOf(
                        AccountType.ROOT.name,
                        AccountsDbAdapter.TEMPLATE_ACCOUNT_NAME
                    )
                }
                accounts = accountsDbAdapter.getAllRecords(where, whereArgs)
                if (accounts.isEmpty()) {
                    account = Account(AccountsDbAdapter.TEMPLATE_ACCOUNT_NAME, Commodity.template)
                    account.accountType = AccountType.ROOT
                    field = account
                    return account
                }
            }
            account = accounts[0]
            field = account
            return account
        }

    private val transactionToTemplateAccounts: MutableMap<String, Account> = TreeMap()

    @Throws(IOException::class)
    private fun writeCounts(serializer: XmlSerializer) {
        // commodities count
        var count = accountsDbAdapter.commoditiesInUseCount
        listener?.onCommodityCount(count)
        writeCount(serializer, CD_TYPE_COMMODITY, count)

        // accounts count
        count = accountsDbAdapter.getRecordsCount(AccountEntry.COLUMN_TEMPLATE + "=0", null)
        listener?.onAccountCount(count)
        writeCount(serializer, CD_TYPE_ACCOUNT, count)

        // transactions count
        count =
            transactionsDbAdapter.getRecordsCount(TransactionEntry.COLUMN_TEMPLATE + "=0", null)
        listener?.onTransactionCount(count)
        writeCount(serializer, CD_TYPE_TRANSACTION, count)

        // scheduled transactions count
        count = scheduledActionDbAdapter.getRecordsCount(ScheduledAction.ActionType.TRANSACTION)
        listener?.onScheduleCount(count)
        writeCount(serializer, CD_TYPE_SCHEDXACTION, count)

        // budgets count
        count = budgetsDbAdapter.recordsCount
        listener?.onBudgetCount(count)
        writeCount(serializer, CD_TYPE_BUDGET, count)

        // prices count
        count = pricesDbAdapter.recordsCount
        listener?.onPriceCount(count)
        writeCount(serializer, CD_TYPE_PRICE, count)
    }

    @Throws(IOException::class)
    private fun writeCount(serializer: XmlSerializer, type: String, count: Long) {
        if (count <= 0) return
        serializer.startTag(NS_GNUCASH, TAG_COUNT_DATA)
        serializer.attribute(NS_CD, ATTR_KEY_TYPE, type)
        serializer.text(count.toString())
        serializer.endTag(NS_GNUCASH, TAG_COUNT_DATA)
    }

    @Throws(IOException::class)
    private fun writeSlots(serializer: XmlSerializer, slots: Collection<Slot>?) {
        if (slots == null || slots.isEmpty()) {
            return
        }
        cancellationSignal.throwIfCanceled()
        for (slot in slots) {
            writeSlot(serializer, slot)
        }
    }

    @Throws(IOException::class)
    private fun writeSlot(serializer: XmlSerializer, slot: Slot) {
        serializer.startTag(null, TAG_SLOT)
        serializer.startTag(NS_SLOT, TAG_KEY)
        serializer.text(slot.key)
        serializer.endTag(NS_SLOT, TAG_KEY)
        serializer.startTag(NS_SLOT, TAG_VALUE)
        serializer.attribute(null, ATTR_KEY_TYPE, slot.type.attribute)
        if (slot.value != null) {
            if (slot.isDate) {
                serializer.startTag(null, TAG_GDATE)
                serializer.text(formatDate(slot.asDate))
                serializer.endTag(null, TAG_GDATE)
            } else if (slot.isDateTime) {
                serializer.startTag(null, TAG_TIME64)
                serializer.text(formatDate(slot.asDateTime))
                serializer.endTag(null, TAG_TIME64)
            } else if (slot.isFrame) {
                writeSlots(serializer, slot.asFrame)
            } else {
                serializer.text(slot.value?.toString())
            }
        }
        serializer.endTag(NS_SLOT, TAG_VALUE)
        serializer.endTag(null, TAG_SLOT)
    }

    @Throws(IOException::class)
    private fun writeAccounts(serializer: XmlSerializer, isTemplate: Boolean) {
        cancellationSignal.throwIfCanceled()
        Timber.i("export accounts. template: %s", isTemplate)
        if (isTemplate) {
            val account = rootTemplateAccount
            if (account == null) {
                Timber.i("Template root account required")
                return
            }
            writeAccount(serializer, account)
        } else {
            val rootUID = accountsDbAdapter.rootAccountUID
            if (rootUID.isEmpty()) {
                throw ExportException(exportParams, "Root account required")
            }
            val account = accountsDbAdapter.getRecord(rootUID)
            writeAccount(serializer, account)
        }
    }

    @Throws(IOException::class)
    private fun writeAccount(serializer: XmlSerializer, account: Account) {
        cancellationSignal.throwIfCanceled()
        if (listener != null && !account.isTemplate) listener.onAccount(account)
        // write account
        serializer.startTag(NS_GNUCASH, TAG_ACCOUNT)
        serializer.attribute(null, ATTR_KEY_VERSION, BOOK_VERSION)
        // account name
        serializer.startTag(NS_ACCOUNT, TAG_NAME)
        serializer.text(account.name)
        serializer.endTag(NS_ACCOUNT, TAG_NAME)
        // account guid
        serializer.startTag(NS_ACCOUNT, TAG_ID)
        serializer.attribute(null, ATTR_KEY_TYPE, ATTR_VALUE_GUID)
        serializer.text(account.uid)
        serializer.endTag(NS_ACCOUNT, TAG_ID)
        // account type
        serializer.startTag(NS_ACCOUNT, TAG_TYPE)
        val accountType = account.accountType
        serializer.text(accountType.name)
        serializer.endTag(NS_ACCOUNT, TAG_TYPE)
        // commodity
        val commodity = account.commodity
        serializer.startTag(NS_ACCOUNT, TAG_COMMODITY)
        serializer.startTag(NS_COMMODITY, TAG_SPACE)
        serializer.text(commodity.namespace)
        serializer.endTag(NS_COMMODITY, TAG_SPACE)
        serializer.startTag(NS_COMMODITY, TAG_ID)
        serializer.text(commodity.currencyCode)
        serializer.endTag(NS_COMMODITY, TAG_ID)
        serializer.endTag(NS_ACCOUNT, TAG_COMMODITY)
        // commodity scu
        serializer.startTag(NS_ACCOUNT, TAG_COMMODITY_SCU)
        serializer.text(commodity.smallestFraction.toString())
        serializer.endTag(NS_ACCOUNT, TAG_COMMODITY_SCU)
        // account code
        val code = account.code
        if (!code.isNullOrEmpty()) {
            serializer.startTag(NS_ACCOUNT, TAG_CODE)
            serializer.text(code)
            serializer.endTag(NS_ACCOUNT, TAG_CODE)
        }
        // account description
        val description = account.description
        if (!description.isNullOrEmpty()) {
            serializer.startTag(NS_ACCOUNT, TAG_DESCRIPTION)
            serializer.text(description)
            serializer.endTag(NS_ACCOUNT, TAG_DESCRIPTION)
        }
        // account slots, color, placeholder, default transfer account, favorite
        val slots = mutableListOf<Slot>()

        if (account.isPlaceholder) {
            slots.add(Slot.string(KEY_PLACEHOLDER, "true"))
        }

        val color = account.color
        if (color != Account.DEFAULT_COLOR) {
            slots.add(Slot.string(KEY_COLOR, formatRGB(color)))
        }

        val defaultTransferAcctUID = account.defaultTransferAccountUID
        if (!defaultTransferAcctUID.isNullOrEmpty()) {
            slots.add(Slot.string(KEY_DEFAULT_TRANSFER_ACCOUNT, defaultTransferAcctUID))
        }

        if (account.isFavorite) {
            slots.add(Slot.string(KEY_FAVORITE, "true"))
        }

        if (account.isHidden) {
            slots.add(Slot.string(KEY_HIDDEN, "true"))
        }

        val notes = account.note
        if (!notes.isNullOrEmpty()) {
            slots.add(Slot.string(KEY_NOTES, notes))
        }

        if (!slots.isEmpty()) {
            serializer.startTag(NS_ACCOUNT, TAG_SLOTS)
            writeSlots(serializer, slots)
            serializer.endTag(NS_ACCOUNT, TAG_SLOTS)
        }

        // parent uid
        val parentUID = account.parentUID
        if ((accountType != AccountType.ROOT) && !parentUID.isNullOrEmpty()) {
            serializer.startTag(NS_ACCOUNT, TAG_PARENT)
            serializer.attribute(null, ATTR_KEY_TYPE, ATTR_VALUE_GUID)
            serializer.text(parentUID)
            serializer.endTag(NS_ACCOUNT, TAG_PARENT)
        } else {
            Timber.d("root account : %s", account.uid)
        }
        serializer.endTag(NS_GNUCASH, TAG_ACCOUNT)

        // gnucash desktop requires that parent account appears before its descendants.
        val children = accountsDbAdapter.getChildren(account.uid)
        for (childUID in children) {
            val child = accountsDbAdapter.getRecord(childUID)
            writeAccount(serializer, child)
        }
    }

    /**
     * Serializes transactions from the database to XML
     *
     * @param serializer XML serializer
     * @param isTemplates   Flag whether to export templates or normal transactions
     * @throws IOException if the XML serializer cannot be written to
     */
    @Throws(IOException::class)
    private fun writeTransactions(serializer: XmlSerializer, isTemplates: Boolean) {
        Timber.i("write transactions")
        val projection = arrayOf<String?>(
            "t." + TransactionEntry.COLUMN_UID + " AS trans_uid",
            "t." + TransactionEntry.COLUMN_DESCRIPTION + " AS trans_desc",
            "t." + TransactionEntry.COLUMN_NOTES + " AS trans_notes",
            "t." + TransactionEntry.COLUMN_TIMESTAMP + " AS trans_time",
            "t." + TransactionEntry.COLUMN_EXPORTED + " AS trans_exported",
            "t." + TransactionEntry.COLUMN_COMMODITY_UID + " AS trans_commodity",
            "t." + TransactionEntry.COLUMN_CREATED_AT + " AS trans_date_posted",
            "t." + TransactionEntry.COLUMN_SCHEDX_ACTION_UID + " AS trans_from_sched_action",
            "t." + TransactionEntry.COLUMN_NUMBER + " AS trans_num",
            "s." + SplitEntry.COLUMN_ID + " AS split_id",
            "s." + SplitEntry.COLUMN_UID + " AS split_uid",
            "s." + SplitEntry.COLUMN_MEMO + " AS split_memo",
            "s." + SplitEntry.COLUMN_TYPE + " AS split_type",
            "s." + SplitEntry.COLUMN_VALUE_NUM + " AS split_value_num",
            "s." + SplitEntry.COLUMN_VALUE_DENOM + " AS split_value_denom",
            "s." + SplitEntry.COLUMN_QUANTITY_NUM + " AS split_quantity_num",
            "s." + SplitEntry.COLUMN_QUANTITY_DENOM + " AS split_quantity_denom",
            "s." + SplitEntry.COLUMN_ACCOUNT_UID + " AS split_acct_uid",
            "s." + SplitEntry.COLUMN_SCHEDX_ACTION_ACCOUNT_UID + " AS split_sched_xaction_acct_uid"
        )
        val where: String = if (isTemplates) {
            "t." + TransactionEntry.COLUMN_TEMPLATE + "=1"
        } else {
            "t." + TransactionEntry.COLUMN_TEMPLATE + "=0"
        }
        val orderBy = ("trans_date_posted ASC"
                + ", t." + TransactionEntry.COLUMN_UID + " ASC"
                + ", t." + TransactionEntry.COLUMN_TIMESTAMP + " ASC"
                + ", " + "split_id ASC")
        val cursor =
            transactionsDbAdapter.fetchTransactionsWithSplits(projection, where, null, orderBy)
                ?: return
        if (!cursor.moveToFirst()) {
            cursor.close()
            return
        }

        if (isTemplates) {
            cancellationSignal.throwIfCanceled()
            val rootTemplateAccount = this.rootTemplateAccount!!
            transactionToTemplateAccounts[""] = rootTemplateAccount

            //FIXME: Retrieve the template account GUIDs from the scheduled action table and create accounts with that
            //this will allow use to maintain the template account GUID when we import from the desktop and also use the same for the splits
            do {
                val txUID = cursor.getString("trans_uid") ?: continue
                val account = Account(generateUID(), Commodity.template)
                account.accountType = AccountType.BANK
                account.parentUID = rootTemplateAccount.uid
                transactionToTemplateAccounts[txUID] = account
            } while (cursor.moveToNext())

            //push cursor back to before the beginning
            cursor.moveToFirst()
        }

        // FIXME: 12.10.2015 export split reconciled_state and reconciled_date to the export */
        var txUIDPrevious = ""
        var trnCommodity = commoditiesDbAdapter.defaultCommodity
        var transaction: Transaction?
        do {
            cancellationSignal.throwIfCanceled()

            val txUID = cursor.getString("trans_uid")!!
            // new transaction starts
            if (txUIDPrevious != txUID) {
                // there's an old transaction, close it
                if (txUIDPrevious.isNotEmpty()) {
                    serializer.endTag(NS_TRANSACTION, TAG_SPLITS)
                    serializer.endTag(NS_GNUCASH, TAG_TRANSACTION)
                }
                // new transaction
                val description = cursor.getString("trans_desc").orEmpty()
                val commodityUID = cursor.getString("trans_commodity")!!
                trnCommodity = commoditiesDbAdapter.getRecord(commodityUID)
                transaction = Transaction(description)
                transaction.setUID(txUID)
                transaction.commodity = trnCommodity
                listener?.onTransaction(transaction)
                serializer.startTag(NS_GNUCASH, TAG_TRANSACTION)
                serializer.attribute(null, ATTR_KEY_VERSION, BOOK_VERSION)
                // transaction id
                serializer.startTag(NS_TRANSACTION, TAG_ID)
                serializer.attribute(null, ATTR_KEY_TYPE, ATTR_VALUE_GUID)
                serializer.text(txUID)
                serializer.endTag(NS_TRANSACTION, TAG_ID)
                // currency
                serializer.startTag(NS_TRANSACTION, TAG_CURRENCY)
                serializer.startTag(NS_COMMODITY, TAG_SPACE)
                serializer.text(trnCommodity.namespace)
                serializer.endTag(NS_COMMODITY, TAG_SPACE)
                serializer.startTag(NS_COMMODITY, TAG_ID)
                serializer.text(trnCommodity.currencyCode)
                serializer.endTag(NS_COMMODITY, TAG_ID)
                serializer.endTag(NS_TRANSACTION, TAG_CURRENCY)
                // number
                val number = cursor.getString("trans_num")
                if (!number.isNullOrEmpty()) {
                    serializer.startTag(NS_TRANSACTION, TAG_NUM)
                    serializer.text(number)
                    serializer.endTag(NS_TRANSACTION, TAG_NUM)
                }
                // date posted, time which user put on the transaction
                val datePosted = cursor.getLong("trans_time")
                val strDate = formatDateTime(datePosted)
                serializer.startTag(NS_TRANSACTION, TAG_DATE_POSTED)
                serializer.startTag(NS_TS, TAG_DATE)
                serializer.text(strDate)
                serializer.endTag(NS_TS, TAG_DATE)
                serializer.endTag(NS_TRANSACTION, TAG_DATE_POSTED)

                // date entered, time when the transaction was actually created
                val timeEntered = getTimestampFromUtcString(cursor.getString("trans_date_posted")!!)
                serializer.startTag(NS_TRANSACTION, TAG_DATE_ENTERED)
                serializer.startTag(NS_TS, TAG_DATE)
                serializer.text(formatDateTime(timeEntered))
                serializer.endTag(NS_TS, TAG_DATE)
                serializer.endTag(NS_TRANSACTION, TAG_DATE_ENTERED)

                // description
                serializer.startTag(NS_TRANSACTION, TAG_DESCRIPTION)
                serializer.text(transaction.description)
                serializer.endTag(NS_TRANSACTION, TAG_DESCRIPTION)
                txUIDPrevious = txUID

                // slots
                val slots = mutableListOf<Slot>()
                slots.add(Slot.gdate(ATTR_KEY_DATE_POSTED, datePosted))

                val notes = cursor.getString("trans_notes")
                if (!notes.isNullOrEmpty()) {
                    slots.add(Slot.string(KEY_NOTES, notes))
                }

                if (!slots.isEmpty()) {
                    serializer.startTag(NS_TRANSACTION, TAG_SLOTS)
                    writeSlots(serializer, slots)
                    serializer.endTag(NS_TRANSACTION, TAG_SLOTS)
                }

                // splits start
                serializer.startTag(NS_TRANSACTION, TAG_SPLITS)
            }
            serializer.startTag(NS_TRANSACTION, TAG_SPLIT)
            // split id
            serializer.startTag(NS_SPLIT, TAG_ID)
            serializer.attribute(null, ATTR_KEY_TYPE, ATTR_VALUE_GUID)
            serializer.text(cursor.getString("split_uid"))
            serializer.endTag(NS_SPLIT, TAG_ID)
            // memo
            val memo = cursor.getString("split_memo")
            if (!memo.isNullOrEmpty()) {
                serializer.startTag(NS_SPLIT, TAG_MEMO)
                serializer.text(memo)
                serializer.endTag(NS_SPLIT, TAG_MEMO)
            }
            // reconciled
            serializer.startTag(NS_SPLIT, TAG_RECONCILED_STATE)
            //FIXME: retrieve reconciled state from the split in the db
            // serializer.text(split.reconcileState);
            serializer.text("n")
            serializer.endTag(NS_SPLIT, TAG_RECONCILED_STATE)
            //todo: if split is reconciled, add reconciled date
            // value, in the transaction's currency
            val trxType = TransactionType.of(cursor.getString("split_type"))
            val splitValueNum = cursor.getLong("split_value_num")
            val splitValueDenom = cursor.getLong("split_value_denom")
            val splitAmount = toBigDecimal(splitValueNum, splitValueDenom)
            var strValue = "0/100"
            if (!isTemplates) { //when doing normal transaction export
                strValue = (if (trxType == TransactionType.CREDIT) "-" else "") +
                        splitValueNum + "/" + splitValueDenom
            }
            serializer.startTag(NS_SPLIT, TAG_VALUE)
            serializer.text(strValue)
            serializer.endTag(NS_SPLIT, TAG_VALUE)
            // quantity, in the split account's currency
            val splitQuantityNum = cursor.getLong("split_quantity_num")
            val splitQuantityDenom = cursor.getLong("split_quantity_denom")
            strValue = "0/1"
            if (!isTemplates) {
                strValue = (if (trxType == TransactionType.CREDIT) "-" else "") +
                        splitQuantityNum + "/" + splitQuantityDenom
            }
            serializer.startTag(NS_SPLIT, TAG_QUANTITY)
            serializer.text(strValue)
            serializer.endTag(NS_SPLIT, TAG_QUANTITY)
            // account guid
            serializer.startTag(NS_SPLIT, TAG_ACCOUNT)
            serializer.attribute(null, ATTR_KEY_TYPE, ATTR_VALUE_GUID)
            val splitAccountUID = cursor.getString("split_acct_uid")!!
            serializer.text(splitAccountUID)
            serializer.endTag(NS_SPLIT, TAG_ACCOUNT)

            //if we are exporting a template transaction, then we need to add some extra slots
            if (isTemplates) {
                val slots = mutableListOf<Slot>()
                val frame = mutableListOf<Slot>()
                var sched_xaction_acct_uid = cursor.getString("split_sched_xaction_acct_uid")
                if (sched_xaction_acct_uid.isNullOrEmpty()) {
                    sched_xaction_acct_uid = splitAccountUID
                }
                frame.add(Slot.guid(KEY_SPLIT_ACCOUNT_SLOT, sched_xaction_acct_uid))
                if (trxType == TransactionType.CREDIT) {
                    frame.add(
                        Slot.string(KEY_CREDIT_FORMULA, formatFormula(splitAmount, trnCommodity))
                    )
                    frame.add(Slot.numeric(KEY_CREDIT_NUMERIC, splitValueNum, splitValueDenom))
                    frame.add(Slot.string(KEY_DEBIT_FORMULA, ""))
                    frame.add(Slot.numeric(KEY_DEBIT_NUMERIC, 0, 1))
                } else {
                    frame.add(Slot.string(KEY_CREDIT_FORMULA, ""))
                    frame.add(Slot.numeric(KEY_CREDIT_NUMERIC, 0, 1))
                    frame.add(
                        Slot.string(KEY_DEBIT_FORMULA, formatFormula(splitAmount, trnCommodity))
                    )
                    frame.add(Slot.numeric(KEY_DEBIT_NUMERIC, splitValueNum, splitValueDenom))
                }
                slots.add(Slot.frame(KEY_SCHED_XACTION, frame))

                serializer.startTag(NS_SPLIT, TAG_SLOTS)
                writeSlots(serializer, slots)
                serializer.endTag(NS_SPLIT, TAG_SLOTS)
            }

            serializer.endTag(NS_TRANSACTION, TAG_SPLIT)
        } while (cursor.moveToNext())
        if (txUIDPrevious.isNotEmpty()) { // there's an unfinished transaction, close it
            serializer.endTag(NS_TRANSACTION, TAG_SPLITS)
            serializer.endTag(NS_GNUCASH, TAG_TRANSACTION)
        }
        cursor.close()
    }

    @Throws(IOException::class)
    private fun writeTemplateTransactions(serializer: XmlSerializer) {
        cancellationSignal.throwIfCanceled()
        if (transactionsDbAdapter.templateTransactionsCount > 0) {
            serializer.startTag(NS_GNUCASH, TAG_TEMPLATE_TRANSACTIONS)
            writeAccounts(serializer, true);
            writeTransactions(serializer, true)
            serializer.endTag(NS_GNUCASH, TAG_TEMPLATE_TRANSACTIONS)
        }
    }

    /**
     * Serializes [ScheduledAction]s from the database to XML
     *
     * @param serializer XML serializer
     * @throws IOException
     */
    @Throws(IOException::class)
    private fun writeScheduledTransactions(serializer: XmlSerializer) {
        Timber.i("write scheduled transactions")
        val actions = scheduledActionDbAdapter.getRecords(ScheduledAction.ActionType.TRANSACTION)
        for (scheduledAction in actions) {
            writeScheduledTransaction(serializer, scheduledAction)
        }
    }

    @Throws(IOException::class)
    private fun writeScheduledTransaction(
        serializer: XmlSerializer,
        scheduledAction: ScheduledAction
    ) {
        if (scheduledAction.actionType != ScheduledAction.ActionType.TRANSACTION) {
            return
        }
        val uid = scheduledAction.uid
        val txUID = scheduledAction.actionUID ?: return
        var accountUID = scheduledAction.templateAccountUID
        var account: Account? = null
        if (accountUID.isNotEmpty()) {
            try {
                account = accountsDbAdapter.getRecord(accountUID)
            } catch (_: Exception) {
            }
        }
        if (account == null) {
            val where = AccountEntry.COLUMN_NAME + "=? AND " + AccountEntry.COLUMN_TEMPLATE + "=1"
            val whereArgs = arrayOf<String?>(uid)
            val accounts = accountsDbAdapter.getAllRecords(where, whereArgs)
            //if the action UID does not belong to a transaction we've seen before, skip it
            account = if (accounts.isEmpty()) {
                transactionToTemplateAccounts[txUID] ?: return
            } else {
                accounts[0]
            }
            account.name = uid
            accountUID = account.uid
        }
        listener?.onSchedule(scheduledAction)

        serializer.startTag(NS_GNUCASH, TAG_SCHEDULED_ACTION)
        serializer.attribute(null, ATTR_KEY_VERSION, BOOK_VERSION)

        serializer.startTag(NS_SX, TAG_ID)
        serializer.attribute(null, ATTR_KEY_TYPE, ATTR_VALUE_GUID)
        serializer.text(uid)
        serializer.endTag(NS_SX, TAG_ID)

        var name = scheduledAction.name
        if (name.isEmpty()) {
            name = transactionsDbAdapter.getAttribute(txUID, TransactionEntry.COLUMN_DESCRIPTION)
        }
        serializer.startTag(NS_SX, TAG_NAME)
        serializer.text(name)
        serializer.endTag(NS_SX, TAG_NAME)

        serializer.startTag(NS_SX, TAG_ENABLED)
        serializer.text(if (scheduledAction.isEnabled) "y" else "n")
        serializer.endTag(NS_SX, TAG_ENABLED)
        serializer.startTag(NS_SX, TAG_AUTO_CREATE)
        serializer.text(if (scheduledAction.isAutoCreate) "y" else "n")
        serializer.endTag(NS_SX, TAG_AUTO_CREATE)
        serializer.startTag(NS_SX, TAG_AUTO_CREATE_NOTIFY)
        serializer.text(if (scheduledAction.isAutoCreateNotify) "y" else "n")
        serializer.endTag(NS_SX, TAG_AUTO_CREATE_NOTIFY)
        serializer.startTag(NS_SX, TAG_ADVANCE_CREATE_DAYS)
        serializer.text(scheduledAction.advanceCreateDays.toString())
        serializer.endTag(NS_SX, TAG_ADVANCE_CREATE_DAYS)
        serializer.startTag(NS_SX, TAG_ADVANCE_REMIND_DAYS)
        serializer.text(scheduledAction.advanceRemindDays.toString())
        serializer.endTag(NS_SX, TAG_ADVANCE_REMIND_DAYS)
        serializer.startTag(NS_SX, TAG_INSTANCE_COUNT)
        val scheduledActionUID = scheduledAction.uid
        val instanceCount = scheduledActionDbAdapter.getActionInstanceCount(scheduledActionUID)
        serializer.text(instanceCount.toString())
        serializer.endTag(NS_SX, TAG_INSTANCE_COUNT)

        //start date
        val scheduleStartTime = scheduledAction.startDate
        writeDate(serializer, NS_SX, TAG_START, scheduleStartTime)

        val lastRunTime = scheduledAction.lastRunTime
        if (lastRunTime > 0) {
            writeDate(serializer, NS_SX, TAG_LAST, lastRunTime)
        }

        val endTime = scheduledAction.endDate
        if (endTime > 0) {
            //end date
            writeDate(serializer, NS_SX, TAG_END, endTime)
        } else { //add number of occurrences
            val totalPlannedCount = scheduledAction.totalPlannedExecutionCount
            if (totalPlannedCount > 0) {
                serializer.startTag(NS_SX, TAG_NUM_OCCUR)
                serializer.text(totalPlannedCount.toString())
                serializer.endTag(NS_SX, TAG_NUM_OCCUR)
            }

            //remaining occurrences
            val remainingCount = totalPlannedCount - scheduledAction.instanceCount
            if (remainingCount > 0) {
                serializer.startTag(NS_SX, TAG_REM_OCCUR)
                serializer.text(remainingCount.toString())
                serializer.endTag(NS_SX, TAG_REM_OCCUR)
            }
        }

        val tag = scheduledAction.tag
        if (!tag.isNullOrEmpty()) {
            serializer.startTag(NS_SX, TAG_TAG)
            serializer.text(tag)
            serializer.endTag(NS_SX, TAG_TAG)
        }

        serializer.startTag(NS_SX, TAG_TEMPLATE_ACCOUNT)
        serializer.attribute(null, ATTR_KEY_TYPE, ATTR_VALUE_GUID)
        serializer.text(accountUID)
        serializer.endTag(NS_SX, TAG_TEMPLATE_ACCOUNT)

        // FIXME: 11.10.2015 Retrieve the information for this section from the recurrence table */
        serializer.startTag(NS_SX, TAG_SCHEDULE)
        serializer.startTag(NS_GNUCASH, TAG_RECURRENCE)
        serializer.attribute(null, ATTR_KEY_VERSION, RECURRENCE_VERSION)
        writeRecurrence(serializer, scheduledAction.recurrence)
        serializer.endTag(NS_GNUCASH, TAG_RECURRENCE)
        serializer.endTag(NS_SX, TAG_SCHEDULE)

        serializer.endTag(NS_GNUCASH, TAG_SCHEDULED_ACTION)
    }

    /**
     * Serializes a date as a `tag` which has a nested [GncXmlHelper.TAG_GDATE] which
     * has the date as a text element formatted.
     *
     * @param serializer XML serializer
     * @param namespace     The tag namespace.
     * @param tag           Enclosing tag
     * @param timeMillis    Date to be formatted and output
     */
    @Throws(IOException::class)
    private fun writeDate(
        serializer: XmlSerializer,
        namespace: String?,
        tag: String,
        timeMillis: Long
    ) {
        serializer.startTag(namespace, tag)
        serializer.startTag(null, TAG_GDATE)
        serializer.text(formatDate(timeMillis))
        serializer.endTag(null, TAG_GDATE)
        serializer.endTag(namespace, tag)
    }

    @Throws(IOException::class)
    private fun writeCommodities(serializer: XmlSerializer, commodities: List<Commodity>) {
        Timber.i("write commodities")
        var hasTemplate = false
        for (commodity in commodities) {
            writeCommodity(serializer, commodity)
            if (commodity.isTemplate) {
                hasTemplate = true
            }
        }
        if (!hasTemplate) {
            writeCommodity(serializer, Commodity.template)
        }
    }

    @Throws(IOException::class)
    private fun writeCommodities(serializer: XmlSerializer) {
        val commodities = accountsDbAdapter.commoditiesInUse
        writeCommodities(serializer, commodities)
    }

    @Throws(IOException::class)
    private fun writeCommodity(serializer: XmlSerializer, commodity: Commodity) {
        listener?.onCommodity(commodity)
        serializer.startTag(NS_GNUCASH, TAG_COMMODITY)
        serializer.attribute(null, ATTR_KEY_VERSION, BOOK_VERSION)
        serializer.startTag(NS_COMMODITY, TAG_SPACE)
        serializer.text(commodity.namespace)
        serializer.endTag(NS_COMMODITY, TAG_SPACE)
        serializer.startTag(NS_COMMODITY, TAG_ID)
        serializer.text(commodity.currencyCode)
        serializer.endTag(NS_COMMODITY, TAG_ID)
        if (CommoditiesXmlHandler.SOURCE_CURRENCY != commodity.quoteSource) {
            if (!commodity.fullname.isNullOrEmpty() && !commodity.isCurrency) {
                serializer.startTag(NS_COMMODITY, TAG_NAME)
                serializer.text(commodity.fullname)
                serializer.endTag(NS_COMMODITY, TAG_NAME)
            }
            val cusip = commodity.cusip
            if (!cusip.isNullOrEmpty()) {
                try {
                    // "exchange-code is stored in ISIN/CUSIP"
                    cusip.toInt()
                } catch (_: NumberFormatException) {
                    serializer.startTag(NS_COMMODITY, TAG_XCODE)
                    serializer.text(cusip)
                    serializer.endTag(NS_COMMODITY, TAG_XCODE)
                }
            }
            serializer.startTag(NS_COMMODITY, TAG_FRACTION)
            serializer.text(commodity.smallestFraction.toString())
            serializer.endTag(NS_COMMODITY, TAG_FRACTION)
        }
        if (commodity.quoteFlag) {
            serializer.startTag(NS_COMMODITY, TAG_GET_QUOTES)
            serializer.endTag(NS_COMMODITY, TAG_GET_QUOTES)
            serializer.startTag(NS_COMMODITY, TAG_QUOTE_SOURCE)
            serializer.text(commodity.quoteSource)
            serializer.endTag(NS_COMMODITY, TAG_QUOTE_SOURCE)
            val tz = commodity.quoteTimeZone
            serializer.startTag(NS_COMMODITY, TAG_QUOTE_TZ)
            if (tz != null) {
                serializer.text(tz.id)
            }
            serializer.endTag(NS_COMMODITY, TAG_QUOTE_TZ)
        }
        serializer.endTag(NS_GNUCASH, TAG_COMMODITY)
    }

    @Throws(IOException::class)
    private fun writePrices(serializer: XmlSerializer) {
        val prices = pricesDbAdapter.allRecords
        if (prices.isEmpty()) return

        Timber.i("write prices")
        serializer.startTag(NS_GNUCASH, TAG_PRICEDB)
        serializer.attribute(null, ATTR_KEY_VERSION, "1")
        for (price in prices) {
            writePrice(serializer, price)
        }
        serializer.endTag(NS_GNUCASH, TAG_PRICEDB)
    }

    @Throws(IOException::class)
    private fun writePrice(serializer: XmlSerializer, price: Price) {
        cancellationSignal.throwIfCanceled()
        listener?.onPrice(price)
        serializer.startTag(null, TAG_PRICE)
        // GUID
        serializer.startTag(NS_PRICE, TAG_ID)
        serializer.attribute(null, ATTR_KEY_TYPE, ATTR_VALUE_GUID)
        serializer.text(price.uid)
        serializer.endTag(NS_PRICE, TAG_ID)
        // commodity
        val commodity = price.commodity
        serializer.startTag(NS_PRICE, TAG_COMMODITY)
        serializer.startTag(NS_COMMODITY, TAG_SPACE)
        serializer.text(commodity.namespace)
        serializer.endTag(NS_COMMODITY, TAG_SPACE)
        serializer.startTag(NS_COMMODITY, TAG_ID)
        serializer.text(commodity.currencyCode)
        serializer.endTag(NS_COMMODITY, TAG_ID)
        serializer.endTag(NS_PRICE, TAG_COMMODITY)
        // currency
        val currency = price.currency
        serializer.startTag(NS_PRICE, TAG_CURRENCY)
        serializer.startTag(NS_COMMODITY, TAG_SPACE)
        serializer.text(currency.namespace)
        serializer.endTag(NS_COMMODITY, TAG_SPACE)
        serializer.startTag(NS_COMMODITY, TAG_ID)
        serializer.text(currency.currencyCode)
        serializer.endTag(NS_COMMODITY, TAG_ID)
        serializer.endTag(NS_PRICE, TAG_CURRENCY)
        // time
        serializer.startTag(NS_PRICE, TAG_TIME)
        serializer.startTag(NS_TS, TAG_DATE)
        serializer.text(formatDateTime(price.date))
        serializer.endTag(NS_TS, TAG_DATE)
        serializer.endTag(NS_PRICE, TAG_TIME)
        // source
        if (!price.source.isNullOrEmpty()) {
            serializer.startTag(NS_PRICE, TAG_SOURCE)
            serializer.text(price.source)
            serializer.endTag(NS_PRICE, TAG_SOURCE)
        }
        // type, optional
        val type = price.type
        if (type != Price.Type.Unknown) {
            serializer.startTag(NS_PRICE, TAG_TYPE)
            serializer.text(type.value)
            serializer.endTag(NS_PRICE, TAG_TYPE)
        }
        // value
        serializer.startTag(NS_PRICE, TAG_VALUE)
        serializer.text("${price.valueNum}/${price.valueDenom}")
        serializer.endTag(NS_PRICE, TAG_VALUE)
        serializer.endTag(null, TAG_PRICE)
    }

    /**
     * Exports the recurrence to GnuCash XML, except the recurrence tags itself i.e. the actual recurrence attributes only
     *
     * This is because there are different recurrence start tags for transactions and budgets.<br></br>
     * So make sure to write the recurrence start/closing tags before/after calling this method.
     *
     * @param serializer XML serializer
     * @param recurrence    Recurrence object
     */
    @Throws(IOException::class)
    private fun writeRecurrence(serializer: XmlSerializer, recurrence: Recurrence?) {
        if (recurrence == null) return
        val periodType = recurrence.periodType
        serializer.startTag(NS_RECURRENCE, TAG_MULT)
        serializer.text(recurrence.multiplier.toString())
        serializer.endTag(NS_RECURRENCE, TAG_MULT)
        serializer.startTag(NS_RECURRENCE, TAG_PERIOD_TYPE)
        serializer.text(periodType.value)
        serializer.endTag(NS_RECURRENCE, TAG_PERIOD_TYPE)

        val recurrenceStartTime = recurrence.periodStart
        writeDate(serializer, NS_RECURRENCE, TAG_START, recurrenceStartTime)

        val weekendAdjust = recurrence.weekendAdjust
        if (weekendAdjust != WeekendAdjust.NONE) {
            /* In r17725 and r17751, I introduced this extra XML child
            element, but this means a gnucash-2.2.x cannot read the SX
            recurrence of a >=2.3.x file anymore, which is bad. In order
            to improve this broken backward compatibility for most of the
            cases, we don't write out this XML element as long as it is
            only "none". */
            serializer.startTag(NS_RECURRENCE, GncXmlHelper.TAG_WEEKEND_ADJ)
            serializer.text(weekendAdjust.value)
            serializer.endTag(NS_RECURRENCE, GncXmlHelper.TAG_WEEKEND_ADJ)
        }
    }

    @Throws(IOException::class)
    private fun writeBudgets(serializer: XmlSerializer) {
        Timber.i("write budgets")
        budgetsDbAdapter.fetchAllRecords().forEach { cursor ->
            cancellationSignal.throwIfCanceled()
            val budget = budgetsDbAdapter.buildModelInstance(cursor)
            writeBudget(serializer, budget)
        }
    }

    @Throws(IOException::class)
    private fun writeBudget(serializer: XmlSerializer, budget: Budget) {
        listener?.onBudget(budget)
        serializer.startTag(NS_GNUCASH, TAG_BUDGET)
        serializer.attribute(null, ATTR_KEY_VERSION, BOOK_VERSION)
        // budget id
        serializer.startTag(NS_BUDGET, TAG_ID)
        serializer.attribute(null, ATTR_KEY_TYPE, ATTR_VALUE_GUID)
        serializer.text(budget.uid)
        serializer.endTag(NS_BUDGET, TAG_ID)
        // budget name
        serializer.startTag(NS_BUDGET, TAG_NAME)
        serializer.text(budget.name)
        serializer.endTag(NS_BUDGET, TAG_NAME)
        // budget description
        val description = budget.description
        if (!description.isNullOrEmpty()) {
            serializer.startTag(NS_BUDGET, TAG_DESCRIPTION)
            serializer.text(description)
            serializer.endTag(NS_BUDGET, TAG_DESCRIPTION)
        }
        // budget periods
        serializer.startTag(NS_BUDGET, TAG_NUM_PERIODS)
        serializer.text(budget.numberOfPeriods.toString())
        serializer.endTag(NS_BUDGET, TAG_NUM_PERIODS)
        // budget recurrence
        serializer.startTag(NS_BUDGET, TAG_RECURRENCE)
        serializer.attribute(null, ATTR_KEY_VERSION, RECURRENCE_VERSION)
        writeRecurrence(serializer, budget.recurrence)
        serializer.endTag(NS_BUDGET, TAG_RECURRENCE)

        // budget as slots
        serializer.startTag(NS_BUDGET, TAG_SLOTS)

        writeBudgetAmounts(serializer, budget)

        // Notes are grouped together.
        writeBudgetNotes(serializer, budget)

        serializer.endTag(NS_BUDGET, TAG_SLOTS)
        serializer.endTag(NS_GNUCASH, TAG_BUDGET)
    }

    @Throws(IOException::class)
    private fun writeBudgetAmounts(serializer: XmlSerializer, budget: Budget) {
        val slots = mutableListOf<Slot>()

        for (accountID in budget.accounts) {
            cancellationSignal.throwIfCanceled()
            slots.clear()

            val periodCount = budget.numberOfPeriods
            for (period in 0 until periodCount) {
                val budgetAmount = budget.getBudgetAmount(accountID, period) ?: continue
                val amount = budgetAmount.amount
                if (amount.isAmountZero) continue
                slots.add(Slot.numeric(period.toString(), amount))
            }

            if (slots.isEmpty()) continue

            serializer.startTag(null, TAG_SLOT)
            serializer.startTag(NS_SLOT, TAG_KEY)
            serializer.text(accountID)
            serializer.endTag(NS_SLOT, TAG_KEY)
            serializer.startTag(NS_SLOT, TAG_VALUE)
            serializer.attribute(null, ATTR_KEY_TYPE, ATTR_VALUE_FRAME)
            writeSlots(serializer, slots)
            serializer.endTag(NS_SLOT, TAG_VALUE)
            serializer.endTag(null, TAG_SLOT)
        }
    }

    @Throws(IOException::class)
    private fun writeBudgetNotes(serializer: XmlSerializer, budget: Budget) {
        val notes = mutableListOf<Slot>()

        for (accountID in budget.accounts) {
            cancellationSignal.throwIfCanceled()
            val frame = mutableListOf<Slot>()

            val periodCount = budget.numberOfPeriods
            for (period in 0 until periodCount) {
                val budgetAmount = budget.getBudgetAmount(accountID, period) ?: continue
                val note = budgetAmount.notes
                if (note.isNullOrEmpty()) continue
                frame.add(Slot.string(period.toString(), note))
            }

            if (!frame.isEmpty()) {
                notes.add(Slot.frame(accountID, frame))
            }
        }

        if (!notes.isEmpty()) {
            val slots = mutableListOf<Slot>()
            slots.add(Slot.frame(KEY_NOTES, notes))
            writeSlots(serializer, slots)
        }
    }

    /**
     * Generates an XML export of the database and writes it to the `writer` output stream
     *
     * @param writer Output stream
     * @throws ExportException
     */
    @Throws(ExportException::class)
    fun export(writer: Writer) {
        val book = booksDbAdapter.activeBook
        export(book, writer)
    }

    /**
     * Generates an XML export of the database and writes it to the `writer` output stream
     *
     * @param bookUID the book UID to export.
     * @param writer  Output stream
     * @throws ExportException
     */
    @Throws(ExportException::class)
    fun export(bookUID: String, writer: Writer) {
        val book = booksDbAdapter.getRecord(bookUID)
        export(book, writer)
    }

    /**
     * Generates an XML export of the database and writes it to the `writer` output stream
     *
     * @param book   the book to export.
     * @param writer Output stream
     * @throws ExportException
     */
    @Throws(ExportException::class, IllegalArgumentException::class, IllegalStateException::class)
    fun export(book: Book, writer: Writer) {
        Timber.i("generate export for book %s", book.uid)
        val timeStart = SystemClock.elapsedRealtime()
        try {
            val factory = XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = true
            val serializer = factory.newSerializer()
            try {
                serializer.setFeature(
                    "http://xmlpull.org/v1/doc/features.html#indent-output",
                    true
                )
            } catch (_: IllegalStateException) {
                // Feature not supported. No problem
            }
            serializer.setOutput(writer)
            serializer.startDocument(StandardCharsets.UTF_8.name(), true)
            // root tag
            serializer.setPrefix(NS_ACCOUNT_PREFIX, NS_ACCOUNT)
            serializer.setPrefix(NS_BOOK_PREFIX, NS_BOOK)
            serializer.setPrefix(NS_GNUCASH_PREFIX, NS_GNUCASH)
            serializer.setPrefix(NS_CD_PREFIX, NS_CD)
            serializer.setPrefix(NS_COMMODITY_PREFIX, NS_COMMODITY)
            serializer.setPrefix(NS_PRICE_PREFIX, NS_PRICE)
            serializer.setPrefix(NS_SLOT_PREFIX, NS_SLOT)
            serializer.setPrefix(NS_SPLIT_PREFIX, NS_SPLIT)
            serializer.setPrefix(NS_SX_PREFIX, NS_SX)
            serializer.setPrefix(NS_TRANSACTION_PREFIX, NS_TRANSACTION)
            serializer.setPrefix(NS_TS_PREFIX, NS_TS)
            serializer.setPrefix(NS_FS_PREFIX, NS_FS)
            serializer.setPrefix(NS_BUDGET_PREFIX, NS_BUDGET)
            serializer.setPrefix(NS_RECURRENCE_PREFIX, NS_RECURRENCE)
            serializer.setPrefix(NS_LOT_PREFIX, NS_LOT)
            serializer.setPrefix(NS_ADDRESS_PREFIX, NS_ADDRESS)
            serializer.setPrefix(NS_BILLTERM_PREFIX, NS_BILLTERM)
            serializer.setPrefix(NS_BT_DAYS_PREFIX, NS_BT_DAYS)
            serializer.setPrefix(NS_BT_PROX_PREFIX, NS_BT_PROX)
            serializer.setPrefix(NS_CUSTOMER_PREFIX, NS_CUSTOMER)
            serializer.setPrefix(NS_EMPLOYEE_PREFIX, NS_EMPLOYEE)
            serializer.setPrefix(NS_ENTRY_PREFIX, NS_ENTRY)
            serializer.setPrefix(NS_INVOICE_PREFIX, NS_INVOICE)
            serializer.setPrefix(NS_JOB_PREFIX, NS_JOB)
            serializer.setPrefix(NS_ORDER_PREFIX, NS_ORDER)
            serializer.setPrefix(NS_OWNER_PREFIX, NS_OWNER)
            serializer.setPrefix(NS_TAXTABLE_PREFIX, NS_TAXTABLE)
            serializer.setPrefix(NS_TTE_PREFIX, NS_TTE)
            serializer.setPrefix(NS_VENDOR_PREFIX, NS_VENDOR)
            serializer.startTag(null, TAG_ROOT)
            // book count
            listener?.onBookCount(1)
            writeCount(serializer, CD_TYPE_BOOK, 1)
            writeBook(serializer, book)
            serializer.endTag(null, TAG_ROOT)
            serializer.endDocument()
            serializer.flush()
        } catch (e: Exception) {
            Timber.e(e)
            throw ExportException(exportParams, e)
        }
        val timeFinish = SystemClock.elapsedRealtime()
        Timber.v("exported in %d ms", timeFinish - timeStart)
    }

    @Throws(IOException::class)
    private fun writeBook(serializer: XmlSerializer, book: Book) {
        listener?.onBook(book)
        // book
        serializer.startTag(NS_GNUCASH, TAG_BOOK)
        serializer.attribute(null, ATTR_KEY_VERSION, BOOK_VERSION)
        // book id
        serializer.startTag(NS_BOOK, TAG_ID)
        serializer.attribute(null, ATTR_KEY_TYPE, ATTR_VALUE_GUID)
        serializer.text(book.uid)
        serializer.endTag(NS_BOOK, TAG_ID)
        writeCounts(serializer)
        // export the commodities used in the DB
        writeCommodities(serializer)
        // prices
        writePrices(serializer)
        // accounts.
        writeAccounts(serializer, false)
        // transactions.
        writeTransactions(serializer, false)
        //transaction templates
        writeTemplateTransactions(serializer)
        //scheduled actions
        writeScheduledTransactions(serializer)
        //budgets
        writeBudgets(serializer)

        serializer.endTag(NS_GNUCASH, TAG_BOOK)
    }

    @Throws(ExportException::class)
    override fun writeExport(writer: Writer, exportParams: ExportParams) {
        export(bookUID, writer)
    }
}
