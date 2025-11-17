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
import org.gnucash.android.db.getLong
import org.gnucash.android.db.getString
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
import org.gnucash.android.export.xml.GncXmlHelper.TAG_TRANSACTION
import org.gnucash.android.export.xml.GncXmlHelper.TAG_TYPE
import org.gnucash.android.export.xml.GncXmlHelper.TAG_VALUE
import org.gnucash.android.export.xml.GncXmlHelper.TAG_XCODE
import org.gnucash.android.export.xml.GncXmlHelper.formatDate
import org.gnucash.android.export.xml.GncXmlHelper.formatDateTime
import org.gnucash.android.export.xml.GncXmlHelper.formatFormula
import org.gnucash.android.gnc.GncProgressListener
import org.gnucash.android.importer.CommoditiesXmlHandler
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
    private fun writeCounts(xmlSerializer: XmlSerializer) {
        // commodities count
        var count = accountsDbAdapter.commoditiesInUseCount
        listener?.onCommodityCount(count)
        writeCount(xmlSerializer, CD_TYPE_COMMODITY, count)

        // accounts count
        count = accountsDbAdapter.getRecordsCount(AccountEntry.COLUMN_TEMPLATE + "=0", null)
        listener?.onAccountCount(count)
        writeCount(xmlSerializer, CD_TYPE_ACCOUNT, count)

        // transactions count
        count =
            transactionsDbAdapter.getRecordsCount(TransactionEntry.COLUMN_TEMPLATE + "=0", null)
        listener?.onTransactionCount(count)
        writeCount(xmlSerializer, CD_TYPE_TRANSACTION, count)

        // scheduled transactions count
        count = scheduledActionDbAdapter.getRecordsCount(ScheduledAction.ActionType.TRANSACTION)
        listener?.onScheduleCount(count)
        writeCount(xmlSerializer, CD_TYPE_SCHEDXACTION, count)

        // budgets count
        count = budgetsDbAdapter.recordsCount
        listener?.onBudgetCount(count)
        writeCount(xmlSerializer, CD_TYPE_BUDGET, count)

        // prices count
        count = pricesDbAdapter.recordsCount
        listener?.onPriceCount(count)
        writeCount(xmlSerializer, CD_TYPE_PRICE, count)
    }

    @Throws(IOException::class)
    private fun writeCount(xmlSerializer: XmlSerializer, type: String, count: Long) {
        if (count <= 0) return
        xmlSerializer.startTag(NS_GNUCASH, TAG_COUNT_DATA)
        xmlSerializer.attribute(NS_CD, ATTR_KEY_TYPE, type)
        xmlSerializer.text(count.toString())
        xmlSerializer.endTag(NS_GNUCASH, TAG_COUNT_DATA)
    }

    @Throws(IOException::class)
    private fun writeSlots(xmlSerializer: XmlSerializer, slots: Collection<Slot>?) {
        if (slots == null || slots.isEmpty()) {
            return
        }
        cancellationSignal.throwIfCanceled()
        for (slot in slots) {
            writeSlot(xmlSerializer, slot)
        }
    }

    @Throws(IOException::class)
    private fun writeSlot(xmlSerializer: XmlSerializer, slot: Slot) {
        xmlSerializer.startTag(null, TAG_SLOT)
        xmlSerializer.startTag(NS_SLOT, TAG_KEY)
        xmlSerializer.text(slot.key)
        xmlSerializer.endTag(NS_SLOT, TAG_KEY)
        xmlSerializer.startTag(NS_SLOT, TAG_VALUE)
        xmlSerializer.attribute(null, ATTR_KEY_TYPE, slot.type)
        if (slot.value != null) {
            if (slot.isDate) {
                xmlSerializer.startTag(null, TAG_GDATE)
                xmlSerializer.text(formatDate(slot.asDate))
                xmlSerializer.endTag(null, TAG_GDATE)
            } else if (slot.isFrame) {
                writeSlots(xmlSerializer, slot.asFrame)
            } else {
                xmlSerializer.text(slot.toString())
            }
        }
        xmlSerializer.endTag(NS_SLOT, TAG_VALUE)
        xmlSerializer.endTag(null, TAG_SLOT)
    }

    @Throws(IOException::class)
    private fun writeAccounts(xmlSerializer: XmlSerializer, isTemplate: Boolean) {
        cancellationSignal.throwIfCanceled()
        Timber.i("export accounts. template: %s", isTemplate)
        if (isTemplate) {
            val account = this.rootTemplateAccount
            if (account == null) {
                Timber.i("No template root account found!")
                return
            }
            writeAccount(xmlSerializer, account)
        } else {
            val rootUID = accountsDbAdapter.rootAccountUID
            if (rootUID.isNullOrEmpty()) {
                throw ExporterException(exportParams, "No root account found!")
            }
            val account = accountsDbAdapter.getRecord(rootUID)
            writeAccount(xmlSerializer, account)
        }
    }

    @Throws(IOException::class)
    private fun writeAccount(xmlSerializer: XmlSerializer, account: Account) {
        cancellationSignal.throwIfCanceled()
        if (listener != null && !account.isTemplate) listener.onAccount(account)
        // write account
        xmlSerializer.startTag(NS_GNUCASH, TAG_ACCOUNT)
        xmlSerializer.attribute(null, ATTR_KEY_VERSION, BOOK_VERSION)
        // account name
        xmlSerializer.startTag(NS_ACCOUNT, TAG_NAME)
        xmlSerializer.text(account.name)
        xmlSerializer.endTag(NS_ACCOUNT, TAG_NAME)
        // account guid
        xmlSerializer.startTag(NS_ACCOUNT, TAG_ID)
        xmlSerializer.attribute(null, ATTR_KEY_TYPE, ATTR_VALUE_GUID)
        xmlSerializer.text(account.uid)
        xmlSerializer.endTag(NS_ACCOUNT, TAG_ID)
        // account type
        xmlSerializer.startTag(NS_ACCOUNT, TAG_TYPE)
        val accountType = account.accountType
        xmlSerializer.text(accountType.name)
        xmlSerializer.endTag(NS_ACCOUNT, TAG_TYPE)
        // commodity
        val commodity = account.commodity
        xmlSerializer.startTag(NS_ACCOUNT, TAG_COMMODITY)
        xmlSerializer.startTag(NS_COMMODITY, TAG_SPACE)
        xmlSerializer.text(commodity.namespace)
        xmlSerializer.endTag(NS_COMMODITY, TAG_SPACE)
        xmlSerializer.startTag(NS_COMMODITY, TAG_ID)
        xmlSerializer.text(commodity.currencyCode)
        xmlSerializer.endTag(NS_COMMODITY, TAG_ID)
        xmlSerializer.endTag(NS_ACCOUNT, TAG_COMMODITY)
        // commodity scu
        xmlSerializer.startTag(NS_ACCOUNT, TAG_COMMODITY_SCU)
        xmlSerializer.text(commodity.smallestFraction.toString())
        xmlSerializer.endTag(NS_ACCOUNT, TAG_COMMODITY_SCU)
        // account description
        val description = account.description
        if (!description.isNullOrEmpty()) {
            xmlSerializer.startTag(NS_ACCOUNT, TAG_DESCRIPTION)
            xmlSerializer.text(description)
            xmlSerializer.endTag(NS_ACCOUNT, TAG_DESCRIPTION)
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
            xmlSerializer.startTag(NS_ACCOUNT, TAG_SLOTS)
            writeSlots(xmlSerializer, slots)
            xmlSerializer.endTag(NS_ACCOUNT, TAG_SLOTS)
        }

        // parent uid
        val parentUID = account.parentUID
        if ((accountType != AccountType.ROOT) && !parentUID.isNullOrEmpty()) {
            xmlSerializer.startTag(NS_ACCOUNT, TAG_PARENT)
            xmlSerializer.attribute(null, ATTR_KEY_TYPE, ATTR_VALUE_GUID)
            xmlSerializer.text(parentUID)
            xmlSerializer.endTag(NS_ACCOUNT, TAG_PARENT)
        } else {
            Timber.d("root account : %s", account.uid)
        }
        xmlSerializer.endTag(NS_GNUCASH, TAG_ACCOUNT)

        // gnucash desktop requires that parent account appears before its descendants.
        val children = accountsDbAdapter.getChildren(account.uid)
        for (childUID in children) {
            val child = accountsDbAdapter.getRecord(childUID)
            writeAccount(xmlSerializer, child)
        }
    }

    /**
     * Serializes transactions from the database to XML
     *
     * @param xmlSerializer XML serializer
     * @param isTemplates   Flag whether to export templates or normal transactions
     * @throws IOException if the XML serializer cannot be written to
     */
    @Throws(IOException::class)
    private fun writeTransactions(xmlSerializer: XmlSerializer, isTemplates: Boolean) {
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
        var trnCommodity: Commodity = Commodity.DEFAULT_COMMODITY
        var transaction: Transaction?
        do {
            cancellationSignal.throwIfCanceled()

            val txUID = cursor.getString("trans_uid")!!
            // new transaction starts
            if (txUIDPrevious != txUID) {
                // there's an old transaction, close it
                if (txUIDPrevious.isNotEmpty()) {
                    xmlSerializer.endTag(NS_TRANSACTION, TAG_SPLITS)
                    xmlSerializer.endTag(NS_GNUCASH, TAG_TRANSACTION)
                }
                // new transaction
                val description = cursor.getString("trans_desc")
                val commodityUID = cursor.getString("trans_commodity")!!
                trnCommodity = commoditiesDbAdapter.getRecord(commodityUID)
                transaction = Transaction(description)
                transaction.setUID(txUID)
                transaction.commodity = trnCommodity
                listener?.onTransaction(transaction)
                xmlSerializer.startTag(NS_GNUCASH, TAG_TRANSACTION)
                xmlSerializer.attribute(null, ATTR_KEY_VERSION, BOOK_VERSION)
                // transaction id
                xmlSerializer.startTag(NS_TRANSACTION, TAG_ID)
                xmlSerializer.attribute(null, ATTR_KEY_TYPE, ATTR_VALUE_GUID)
                xmlSerializer.text(txUID)
                xmlSerializer.endTag(NS_TRANSACTION, TAG_ID)
                // currency
                xmlSerializer.startTag(NS_TRANSACTION, TAG_CURRENCY)
                xmlSerializer.startTag(NS_COMMODITY, TAG_SPACE)
                xmlSerializer.text(trnCommodity.namespace)
                xmlSerializer.endTag(NS_COMMODITY, TAG_SPACE)
                xmlSerializer.startTag(NS_COMMODITY, TAG_ID)
                xmlSerializer.text(trnCommodity.currencyCode)
                xmlSerializer.endTag(NS_COMMODITY, TAG_ID)
                xmlSerializer.endTag(NS_TRANSACTION, TAG_CURRENCY)
                // number
                val number = cursor.getString("trans_num")
                if (!number.isNullOrEmpty()) {
                    xmlSerializer.startTag(NS_TRANSACTION, TAG_NUM)
                    xmlSerializer.text(number)
                    xmlSerializer.endTag(NS_TRANSACTION, TAG_NUM)
                }
                // date posted, time which user put on the transaction
                val datePosted = cursor.getLong("trans_time")
                val strDate = formatDateTime(datePosted)
                xmlSerializer.startTag(NS_TRANSACTION, TAG_DATE_POSTED)
                xmlSerializer.startTag(NS_TS, TAG_DATE)
                xmlSerializer.text(strDate)
                xmlSerializer.endTag(NS_TS, TAG_DATE)
                xmlSerializer.endTag(NS_TRANSACTION, TAG_DATE_POSTED)

                // date entered, time when the transaction was actually created
                val timeEntered = getTimestampFromUtcString(cursor.getString("trans_date_posted")!!)
                xmlSerializer.startTag(NS_TRANSACTION, TAG_DATE_ENTERED)
                xmlSerializer.startTag(NS_TS, TAG_DATE)
                xmlSerializer.text(formatDateTime(timeEntered))
                xmlSerializer.endTag(NS_TS, TAG_DATE)
                xmlSerializer.endTag(NS_TRANSACTION, TAG_DATE_ENTERED)

                // description
                xmlSerializer.startTag(NS_TRANSACTION, TAG_DESCRIPTION)
                xmlSerializer.text(transaction.description)
                xmlSerializer.endTag(NS_TRANSACTION, TAG_DESCRIPTION)
                txUIDPrevious = txUID

                // slots
                val slots = mutableListOf<Slot>()
                slots.add(Slot.gdate(ATTR_KEY_DATE_POSTED, datePosted))

                val notes = cursor.getString("trans_notes")
                if (!notes.isNullOrEmpty()) {
                    slots.add(Slot.string(KEY_NOTES, notes))
                }

                if (!slots.isEmpty()) {
                    xmlSerializer.startTag(NS_TRANSACTION, TAG_SLOTS)
                    writeSlots(xmlSerializer, slots)
                    xmlSerializer.endTag(NS_TRANSACTION, TAG_SLOTS)
                }

                // splits start
                xmlSerializer.startTag(NS_TRANSACTION, TAG_SPLITS)
            }
            xmlSerializer.startTag(NS_TRANSACTION, TAG_SPLIT)
            // split id
            xmlSerializer.startTag(NS_SPLIT, TAG_ID)
            xmlSerializer.attribute(null, ATTR_KEY_TYPE, ATTR_VALUE_GUID)
            xmlSerializer.text(cursor.getString("split_uid"))
            xmlSerializer.endTag(NS_SPLIT, TAG_ID)
            // memo
            val memo = cursor.getString("split_memo")
            if (!memo.isNullOrEmpty()) {
                xmlSerializer.startTag(NS_SPLIT, TAG_MEMO)
                xmlSerializer.text(memo)
                xmlSerializer.endTag(NS_SPLIT, TAG_MEMO)
            }
            // reconciled
            xmlSerializer.startTag(NS_SPLIT, TAG_RECONCILED_STATE)
            //FIXME: retrieve reconciled state from the split in the db
            // xmlSerializer.text(split.reconcileState);
            xmlSerializer.text("n")
            xmlSerializer.endTag(NS_SPLIT, TAG_RECONCILED_STATE)
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
            xmlSerializer.startTag(NS_SPLIT, TAG_VALUE)
            xmlSerializer.text(strValue)
            xmlSerializer.endTag(NS_SPLIT, TAG_VALUE)
            // quantity, in the split account's currency
            val splitQuantityNum = cursor.getLong("split_quantity_num")
            val splitQuantityDenom = cursor.getLong("split_quantity_denom")
            strValue = "0/1"
            if (!isTemplates) {
                strValue = (if (trxType == TransactionType.CREDIT) "-" else "") +
                        splitQuantityNum + "/" + splitQuantityDenom
            }
            xmlSerializer.startTag(NS_SPLIT, TAG_QUANTITY)
            xmlSerializer.text(strValue)
            xmlSerializer.endTag(NS_SPLIT, TAG_QUANTITY)
            // account guid
            xmlSerializer.startTag(NS_SPLIT, TAG_ACCOUNT)
            xmlSerializer.attribute(null, ATTR_KEY_TYPE, ATTR_VALUE_GUID)
            val splitAccountUID = cursor.getString("split_acct_uid")!!
            xmlSerializer.text(splitAccountUID)
            xmlSerializer.endTag(NS_SPLIT, TAG_ACCOUNT)

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

                xmlSerializer.startTag(NS_SPLIT, TAG_SLOTS)
                writeSlots(xmlSerializer, slots)
                xmlSerializer.endTag(NS_SPLIT, TAG_SLOTS)
            }

            xmlSerializer.endTag(NS_TRANSACTION, TAG_SPLIT)
        } while (cursor.moveToNext())
        if (txUIDPrevious.isNotEmpty()) { // there's an unfinished transaction, close it
            xmlSerializer.endTag(NS_TRANSACTION, TAG_SPLITS)
            xmlSerializer.endTag(NS_GNUCASH, TAG_TRANSACTION)
        }
        cursor.close()
    }

    @Throws(IOException::class)
    private fun writeTemplateTransactions(xmlSerializer: XmlSerializer) {
        cancellationSignal.throwIfCanceled()
        if (transactionsDbAdapter.templateTransactionsCount > 0) {
            xmlSerializer.startTag(NS_GNUCASH, TAG_TEMPLATE_TRANSACTIONS)
            writeAccounts(xmlSerializer, true);
            writeTransactions(xmlSerializer, true)
            xmlSerializer.endTag(NS_GNUCASH, TAG_TEMPLATE_TRANSACTIONS)
        }
    }

    /**
     * Serializes [ScheduledAction]s from the database to XML
     *
     * @param xmlSerializer XML serializer
     * @throws IOException
     */
    @Throws(IOException::class)
    private fun writeScheduledTransactions(xmlSerializer: XmlSerializer) {
        Timber.i("write scheduled transactions")
        val actions = scheduledActionDbAdapter.getRecords(ScheduledAction.ActionType.TRANSACTION)
        for (scheduledAction in actions) {
            writeScheduledTransaction(xmlSerializer, scheduledAction)
        }
    }

    @Throws(IOException::class)
    private fun writeScheduledTransaction(
        xmlSerializer: XmlSerializer,
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

        xmlSerializer.startTag(NS_GNUCASH, TAG_SCHEDULED_ACTION)
        xmlSerializer.attribute(null, ATTR_KEY_VERSION, BOOK_VERSION)

        xmlSerializer.startTag(NS_SX, TAG_ID)
        xmlSerializer.attribute(null, ATTR_KEY_TYPE, ATTR_VALUE_GUID)
        xmlSerializer.text(uid)
        xmlSerializer.endTag(NS_SX, TAG_ID)

        var name: String? = scheduledAction.name
        if (name.isNullOrEmpty()) {
            name = transactionsDbAdapter.getAttribute(txUID, TransactionEntry.COLUMN_DESCRIPTION)
        }
        xmlSerializer.startTag(NS_SX, TAG_NAME)
        xmlSerializer.text(name)
        xmlSerializer.endTag(NS_SX, TAG_NAME)

        xmlSerializer.startTag(NS_SX, TAG_ENABLED)
        xmlSerializer.text(if (scheduledAction.isEnabled) "y" else "n")
        xmlSerializer.endTag(NS_SX, TAG_ENABLED)
        xmlSerializer.startTag(NS_SX, TAG_AUTO_CREATE)
        xmlSerializer.text(if (scheduledAction.isAutoCreate) "y" else "n")
        xmlSerializer.endTag(NS_SX, TAG_AUTO_CREATE)
        xmlSerializer.startTag(NS_SX, TAG_AUTO_CREATE_NOTIFY)
        xmlSerializer.text(if (scheduledAction.isAutoCreateNotify) "y" else "n")
        xmlSerializer.endTag(NS_SX, TAG_AUTO_CREATE_NOTIFY)
        xmlSerializer.startTag(NS_SX, TAG_ADVANCE_CREATE_DAYS)
        xmlSerializer.text(scheduledAction.advanceCreateDays.toString())
        xmlSerializer.endTag(NS_SX, TAG_ADVANCE_CREATE_DAYS)
        xmlSerializer.startTag(NS_SX, TAG_ADVANCE_REMIND_DAYS)
        xmlSerializer.text(scheduledAction.advanceRemindDays.toString())
        xmlSerializer.endTag(NS_SX, TAG_ADVANCE_REMIND_DAYS)
        xmlSerializer.startTag(NS_SX, TAG_INSTANCE_COUNT)
        val scheduledActionUID = scheduledAction.uid
        val instanceCount = scheduledActionDbAdapter.getActionInstanceCount(scheduledActionUID)
        xmlSerializer.text(instanceCount.toString())
        xmlSerializer.endTag(NS_SX, TAG_INSTANCE_COUNT)

        //start date
        val scheduleStartTime = scheduledAction.startDate
        writeDate(xmlSerializer, NS_SX, TAG_START, scheduleStartTime)

        val lastRunTime = scheduledAction.lastRunTime
        if (lastRunTime > 0) {
            writeDate(xmlSerializer, NS_SX, TAG_LAST, lastRunTime)
        }

        val endTime = scheduledAction.endDate
        if (endTime > 0) {
            //end date
            writeDate(xmlSerializer, NS_SX, TAG_END, endTime)
        } else { //add number of occurrences
            val totalPlannedCount = scheduledAction.totalPlannedExecutionCount
            if (totalPlannedCount > 0) {
                xmlSerializer.startTag(NS_SX, TAG_NUM_OCCUR)
                xmlSerializer.text(totalPlannedCount.toString())
                xmlSerializer.endTag(NS_SX, TAG_NUM_OCCUR)
            }

            //remaining occurrences
            val remainingCount = totalPlannedCount - scheduledAction.instanceCount
            if (remainingCount > 0) {
                xmlSerializer.startTag(NS_SX, TAG_REM_OCCUR)
                xmlSerializer.text(remainingCount.toString())
                xmlSerializer.endTag(NS_SX, TAG_REM_OCCUR)
            }
        }

        val tag = scheduledAction.tag
        if (!tag.isNullOrEmpty()) {
            xmlSerializer.startTag(NS_SX, TAG_TAG)
            xmlSerializer.text(tag)
            xmlSerializer.endTag(NS_SX, TAG_TAG)
        }

        xmlSerializer.startTag(NS_SX, TAG_TEMPLATE_ACCOUNT)
        xmlSerializer.attribute(null, ATTR_KEY_TYPE, ATTR_VALUE_GUID)
        xmlSerializer.text(accountUID)
        xmlSerializer.endTag(NS_SX, TAG_TEMPLATE_ACCOUNT)

        // FIXME: 11.10.2015 Retrieve the information for this section from the recurrence table */
        xmlSerializer.startTag(NS_SX, TAG_SCHEDULE)
        xmlSerializer.startTag(NS_GNUCASH, TAG_RECURRENCE)
        xmlSerializer.attribute(null, ATTR_KEY_VERSION, RECURRENCE_VERSION)
        writeRecurrence(xmlSerializer, scheduledAction.recurrence)
        xmlSerializer.endTag(NS_GNUCASH, TAG_RECURRENCE)
        xmlSerializer.endTag(NS_SX, TAG_SCHEDULE)

        xmlSerializer.endTag(NS_GNUCASH, TAG_SCHEDULED_ACTION)
    }

    /**
     * Serializes a date as a `tag` which has a nested [GncXmlHelper.TAG_GDATE] which
     * has the date as a text element formatted.
     *
     * @param xmlSerializer XML serializer
     * @param namespace     The tag namespace.
     * @param tag           Enclosing tag
     * @param timeMillis    Date to be formatted and output
     */
    @Throws(IOException::class)
    private fun writeDate(
        xmlSerializer: XmlSerializer,
        namespace: String?,
        tag: String,
        timeMillis: Long
    ) {
        xmlSerializer.startTag(namespace, tag)
        xmlSerializer.startTag(null, TAG_GDATE)
        xmlSerializer.text(formatDate(timeMillis))
        xmlSerializer.endTag(null, TAG_GDATE)
        xmlSerializer.endTag(namespace, tag)
    }

    @Throws(IOException::class)
    private fun writeCommodities(xmlSerializer: XmlSerializer, commodities: List<Commodity>) {
        Timber.i("write commodities")
        var hasTemplate = false
        for (commodity in commodities) {
            writeCommodity(xmlSerializer, commodity)
            if (commodity.isTemplate) {
                hasTemplate = true
            }
        }
        if (!hasTemplate) {
            writeCommodity(xmlSerializer, Commodity.template)
        }
    }

    @Throws(IOException::class)
    private fun writeCommodities(xmlSerializer: XmlSerializer) {
        val commodities = accountsDbAdapter.commoditiesInUse
        writeCommodities(xmlSerializer, commodities)
    }

    @Throws(IOException::class)
    private fun writeCommodity(xmlSerializer: XmlSerializer, commodity: Commodity) {
        listener?.onCommodity(commodity)
        xmlSerializer.startTag(NS_GNUCASH, TAG_COMMODITY)
        xmlSerializer.attribute(null, ATTR_KEY_VERSION, BOOK_VERSION)
        xmlSerializer.startTag(NS_COMMODITY, TAG_SPACE)
        xmlSerializer.text(commodity.namespace)
        xmlSerializer.endTag(NS_COMMODITY, TAG_SPACE)
        xmlSerializer.startTag(NS_COMMODITY, TAG_ID)
        xmlSerializer.text(commodity.currencyCode)
        xmlSerializer.endTag(NS_COMMODITY, TAG_ID)
        if (CommoditiesXmlHandler.SOURCE_CURRENCY != commodity.quoteSource) {
            if (!commodity.fullname.isNullOrEmpty() && !commodity.isCurrency) {
                xmlSerializer.startTag(NS_COMMODITY, TAG_NAME)
                xmlSerializer.text(commodity.fullname)
                xmlSerializer.endTag(NS_COMMODITY, TAG_NAME)
            }
            val cusip = commodity.cusip
            if (!cusip.isNullOrEmpty()) {
                try {
                    // "exchange-code is stored in ISIN/CUSIP"
                    cusip.toInt()
                } catch (_: NumberFormatException) {
                    xmlSerializer.startTag(NS_COMMODITY, TAG_XCODE)
                    xmlSerializer.text(cusip)
                    xmlSerializer.endTag(NS_COMMODITY, TAG_XCODE)
                }
            }
            xmlSerializer.startTag(NS_COMMODITY, TAG_FRACTION)
            xmlSerializer.text(commodity.smallestFraction.toString())
            xmlSerializer.endTag(NS_COMMODITY, TAG_FRACTION)
        }
        if (commodity.quoteFlag) {
            xmlSerializer.startTag(NS_COMMODITY, TAG_GET_QUOTES)
            xmlSerializer.endTag(NS_COMMODITY, TAG_GET_QUOTES)
            xmlSerializer.startTag(NS_COMMODITY, TAG_QUOTE_SOURCE)
            xmlSerializer.text(commodity.quoteSource)
            xmlSerializer.endTag(NS_COMMODITY, TAG_QUOTE_SOURCE)
            val tz = commodity.quoteTimeZone
            xmlSerializer.startTag(NS_COMMODITY, TAG_QUOTE_TZ)
            if (tz != null) {
                xmlSerializer.text(tz.id)
            }
            xmlSerializer.endTag(NS_COMMODITY, TAG_QUOTE_TZ)
        }
        xmlSerializer.endTag(NS_GNUCASH, TAG_COMMODITY)
    }

    @Throws(IOException::class)
    private fun writePrices(xmlSerializer: XmlSerializer) {
        val prices = pricesDbAdapter.allRecords
        if (prices.isEmpty()) return

        Timber.i("write prices")
        xmlSerializer.startTag(NS_GNUCASH, TAG_PRICEDB)
        xmlSerializer.attribute(null, ATTR_KEY_VERSION, "1")
        for (price in prices) {
            writePrice(xmlSerializer, price)
        }
        xmlSerializer.endTag(NS_GNUCASH, TAG_PRICEDB)
    }

    @Throws(IOException::class)
    private fun writePrice(xmlSerializer: XmlSerializer, price: Price) {
        cancellationSignal.throwIfCanceled()
        listener?.onPrice(price)
        xmlSerializer.startTag(null, TAG_PRICE)
        // GUID
        xmlSerializer.startTag(NS_PRICE, TAG_ID)
        xmlSerializer.attribute(null, ATTR_KEY_TYPE, ATTR_VALUE_GUID)
        xmlSerializer.text(price.uid)
        xmlSerializer.endTag(NS_PRICE, TAG_ID)
        // commodity
        val commodity = price.commodity
        xmlSerializer.startTag(NS_PRICE, TAG_COMMODITY)
        xmlSerializer.startTag(NS_COMMODITY, TAG_SPACE)
        xmlSerializer.text(commodity.namespace)
        xmlSerializer.endTag(NS_COMMODITY, TAG_SPACE)
        xmlSerializer.startTag(NS_COMMODITY, TAG_ID)
        xmlSerializer.text(commodity.currencyCode)
        xmlSerializer.endTag(NS_COMMODITY, TAG_ID)
        xmlSerializer.endTag(NS_PRICE, TAG_COMMODITY)
        // currency
        val currency = price.currency
        xmlSerializer.startTag(NS_PRICE, TAG_CURRENCY)
        xmlSerializer.startTag(NS_COMMODITY, TAG_SPACE)
        xmlSerializer.text(currency.namespace)
        xmlSerializer.endTag(NS_COMMODITY, TAG_SPACE)
        xmlSerializer.startTag(NS_COMMODITY, TAG_ID)
        xmlSerializer.text(currency.currencyCode)
        xmlSerializer.endTag(NS_COMMODITY, TAG_ID)
        xmlSerializer.endTag(NS_PRICE, TAG_CURRENCY)
        // time
        xmlSerializer.startTag(NS_PRICE, TAG_TIME)
        xmlSerializer.startTag(NS_TS, TAG_DATE)
        xmlSerializer.text(formatDateTime(price.date))
        xmlSerializer.endTag(NS_TS, TAG_DATE)
        xmlSerializer.endTag(NS_PRICE, TAG_TIME)
        // source
        if (!price.source.isNullOrEmpty()) {
            xmlSerializer.startTag(NS_PRICE, TAG_SOURCE)
            xmlSerializer.text(price.source)
            xmlSerializer.endTag(NS_PRICE, TAG_SOURCE)
        }
        // type, optional
        val type = price.type
        if (type != Price.Type.Unknown) {
            xmlSerializer.startTag(NS_PRICE, TAG_TYPE)
            xmlSerializer.text(type.value)
            xmlSerializer.endTag(NS_PRICE, TAG_TYPE)
        }
        // value
        xmlSerializer.startTag(NS_PRICE, TAG_VALUE)
        xmlSerializer.text("${price.valueNum}/${price.valueDenom}")
        xmlSerializer.endTag(NS_PRICE, TAG_VALUE)
        xmlSerializer.endTag(null, TAG_PRICE)
    }

    /**
     * Exports the recurrence to GnuCash XML, except the recurrence tags itself i.e. the actual recurrence attributes only
     *
     * This is because there are different recurrence start tags for transactions and budgets.<br></br>
     * So make sure to write the recurrence start/closing tags before/after calling this method.
     *
     * @param xmlSerializer XML serializer
     * @param recurrence    Recurrence object
     */
    @Throws(IOException::class)
    private fun writeRecurrence(xmlSerializer: XmlSerializer, recurrence: Recurrence?) {
        if (recurrence == null) return
        val periodType = recurrence.periodType
        xmlSerializer.startTag(NS_RECURRENCE, TAG_MULT)
        xmlSerializer.text(recurrence.multiplier.toString())
        xmlSerializer.endTag(NS_RECURRENCE, TAG_MULT)
        xmlSerializer.startTag(NS_RECURRENCE, TAG_PERIOD_TYPE)
        xmlSerializer.text(periodType.value)
        xmlSerializer.endTag(NS_RECURRENCE, TAG_PERIOD_TYPE)

        val recurrenceStartTime = recurrence.periodStart
        writeDate(xmlSerializer, NS_RECURRENCE, TAG_START, recurrenceStartTime)

        val weekendAdjust = recurrence.weekendAdjust
        if (weekendAdjust != WeekendAdjust.NONE) {
            /* In r17725 and r17751, I introduced this extra XML child
            element, but this means a gnucash-2.2.x cannot read the SX
            recurrence of a >=2.3.x file anymore, which is bad. In order
            to improve this broken backward compatibility for most of the
            cases, we don't write out this XML element as long as it is
            only "none". */
            xmlSerializer.startTag(NS_RECURRENCE, GncXmlHelper.TAG_WEEKEND_ADJ)
            xmlSerializer.text(weekendAdjust.value)
            xmlSerializer.endTag(NS_RECURRENCE, GncXmlHelper.TAG_WEEKEND_ADJ)
        }
    }

    @Throws(IOException::class)
    private fun writeBudgets(xmlSerializer: XmlSerializer) {
        Timber.i("write budgets")
        val cursor = budgetsDbAdapter.fetchAllRecords()
        if (cursor != null) {
            while (cursor.moveToNext()) {
                cancellationSignal.throwIfCanceled()
                val budget = budgetsDbAdapter.buildModelInstance(cursor)
                writeBudget(xmlSerializer, budget)
            }
            cursor.close()
        }
    }

    @Throws(IOException::class)
    private fun writeBudget(xmlSerializer: XmlSerializer, budget: Budget) {
        listener?.onBudget(budget)
        xmlSerializer.startTag(NS_GNUCASH, TAG_BUDGET)
        xmlSerializer.attribute(null, ATTR_KEY_VERSION, BOOK_VERSION)
        // budget id
        xmlSerializer.startTag(NS_BUDGET, TAG_ID)
        xmlSerializer.attribute(null, ATTR_KEY_TYPE, ATTR_VALUE_GUID)
        xmlSerializer.text(budget.uid)
        xmlSerializer.endTag(NS_BUDGET, TAG_ID)
        // budget name
        xmlSerializer.startTag(NS_BUDGET, TAG_NAME)
        xmlSerializer.text(budget.name)
        xmlSerializer.endTag(NS_BUDGET, TAG_NAME)
        // budget description
        val description = budget.description
        if (!description.isNullOrEmpty()) {
            xmlSerializer.startTag(NS_BUDGET, TAG_DESCRIPTION)
            xmlSerializer.text(description)
            xmlSerializer.endTag(NS_BUDGET, TAG_DESCRIPTION)
        }
        // budget periods
        xmlSerializer.startTag(NS_BUDGET, TAG_NUM_PERIODS)
        xmlSerializer.text(budget.numberOfPeriods.toString())
        xmlSerializer.endTag(NS_BUDGET, TAG_NUM_PERIODS)
        // budget recurrence
        xmlSerializer.startTag(NS_BUDGET, TAG_RECURRENCE)
        xmlSerializer.attribute(null, ATTR_KEY_VERSION, RECURRENCE_VERSION)
        writeRecurrence(xmlSerializer, budget.recurrence)
        xmlSerializer.endTag(NS_BUDGET, TAG_RECURRENCE)

        // budget as slots
        xmlSerializer.startTag(NS_BUDGET, TAG_SLOTS)

        writeBudgetAmounts(xmlSerializer, budget)

        // Notes are grouped together.
        writeBudgetNotes(xmlSerializer, budget)

        xmlSerializer.endTag(NS_BUDGET, TAG_SLOTS)
        xmlSerializer.endTag(NS_GNUCASH, TAG_BUDGET)
    }

    @Throws(IOException::class)
    private fun writeBudgetAmounts(xmlSerializer: XmlSerializer, budget: Budget) {
        val slots = mutableListOf<Slot>()

        for (accountID in budget.accounts) {
            cancellationSignal.throwIfCanceled()
            slots.clear()

            val periodCount = budget.numberOfPeriods
            for (period in 0 until periodCount) {
                val budgetAmount = budget.getBudgetAmount(accountID, period)
                if (budgetAmount == null) continue
                val amount = budgetAmount.amount
                if (amount.isAmountZero) continue
                slots.add(Slot.numeric(period.toString(), amount))
            }

            if (slots.isEmpty()) continue

            xmlSerializer.startTag(null, TAG_SLOT)
            xmlSerializer.startTag(NS_SLOT, TAG_KEY)
            xmlSerializer.text(accountID)
            xmlSerializer.endTag(NS_SLOT, TAG_KEY)
            xmlSerializer.startTag(NS_SLOT, TAG_VALUE)
            xmlSerializer.attribute(null, ATTR_KEY_TYPE, ATTR_VALUE_FRAME)
            writeSlots(xmlSerializer, slots)
            xmlSerializer.endTag(NS_SLOT, TAG_VALUE)
            xmlSerializer.endTag(null, TAG_SLOT)
        }
    }

    @Throws(IOException::class)
    private fun writeBudgetNotes(xmlSerializer: XmlSerializer, budget: Budget) {
        val notes = mutableListOf<Slot>()

        for (accountID in budget.accounts) {
            cancellationSignal.throwIfCanceled()
            val frame = mutableListOf<Slot>()

            val periodCount = budget.numberOfPeriods
            for (period in 0 until periodCount) {
                val budgetAmount = budget.getBudgetAmount(accountID, period)
                if (budgetAmount == null) continue
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
            writeSlots(xmlSerializer, slots)
        }
    }

    /**
     * Generates an XML export of the database and writes it to the `writer` output stream
     *
     * @param writer Output stream
     * @throws ExporterException
     */
    @Throws(ExporterException::class)
    fun export(writer: Writer) {
        val book = booksDbAdapter.activeBook
        export(book, writer)
    }

    /**
     * Generates an XML export of the database and writes it to the `writer` output stream
     *
     * @param bookUID the book UID to export.
     * @param writer  Output stream
     * @throws ExporterException
     */
    @Throws(ExporterException::class)
    fun export(bookUID: String, writer: Writer) {
        val book = booksDbAdapter.getRecord(bookUID)
        export(book, writer)
    }

    /**
     * Generates an XML export of the database and writes it to the `writer` output stream
     *
     * @param book   the book to export.
     * @param writer Output stream
     * @throws ExporterException
     */
    @Throws(ExporterException::class)
    fun export(book: Book, writer: Writer) {
        Timber.i("generate export for book %s", book.uid)
        val timeStart = SystemClock.elapsedRealtime()
        try {
            val factory = XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = true
            val xmlSerializer = factory.newSerializer()
            try {
                xmlSerializer.setFeature(
                    "http://xmlpull.org/v1/doc/features.html#indent-output",
                    true
                )
            } catch (_: IllegalStateException) {
                // Feature not supported. No problem
            }
            xmlSerializer.setOutput(writer)
            xmlSerializer.startDocument(StandardCharsets.UTF_8.name(), true)
            // root tag
            xmlSerializer.setPrefix(NS_ACCOUNT_PREFIX, NS_ACCOUNT)
            xmlSerializer.setPrefix(NS_BOOK_PREFIX, NS_BOOK)
            xmlSerializer.setPrefix(NS_GNUCASH_PREFIX, NS_GNUCASH)
            xmlSerializer.setPrefix(NS_CD_PREFIX, NS_CD)
            xmlSerializer.setPrefix(NS_COMMODITY_PREFIX, NS_COMMODITY)
            xmlSerializer.setPrefix(NS_PRICE_PREFIX, NS_PRICE)
            xmlSerializer.setPrefix(NS_SLOT_PREFIX, NS_SLOT)
            xmlSerializer.setPrefix(NS_SPLIT_PREFIX, NS_SPLIT)
            xmlSerializer.setPrefix(NS_SX_PREFIX, NS_SX)
            xmlSerializer.setPrefix(NS_TRANSACTION_PREFIX, NS_TRANSACTION)
            xmlSerializer.setPrefix(NS_TS_PREFIX, NS_TS)
            xmlSerializer.setPrefix(NS_FS_PREFIX, NS_FS)
            xmlSerializer.setPrefix(NS_BUDGET_PREFIX, NS_BUDGET)
            xmlSerializer.setPrefix(NS_RECURRENCE_PREFIX, NS_RECURRENCE)
            xmlSerializer.setPrefix(NS_LOT_PREFIX, NS_LOT)
            xmlSerializer.setPrefix(NS_ADDRESS_PREFIX, NS_ADDRESS)
            xmlSerializer.setPrefix(NS_BILLTERM_PREFIX, NS_BILLTERM)
            xmlSerializer.setPrefix(NS_BT_DAYS_PREFIX, NS_BT_DAYS)
            xmlSerializer.setPrefix(NS_BT_PROX_PREFIX, NS_BT_PROX)
            xmlSerializer.setPrefix(NS_CUSTOMER_PREFIX, NS_CUSTOMER)
            xmlSerializer.setPrefix(NS_EMPLOYEE_PREFIX, NS_EMPLOYEE)
            xmlSerializer.setPrefix(NS_ENTRY_PREFIX, NS_ENTRY)
            xmlSerializer.setPrefix(NS_INVOICE_PREFIX, NS_INVOICE)
            xmlSerializer.setPrefix(NS_JOB_PREFIX, NS_JOB)
            xmlSerializer.setPrefix(NS_ORDER_PREFIX, NS_ORDER)
            xmlSerializer.setPrefix(NS_OWNER_PREFIX, NS_OWNER)
            xmlSerializer.setPrefix(NS_TAXTABLE_PREFIX, NS_TAXTABLE)
            xmlSerializer.setPrefix(NS_TTE_PREFIX, NS_TTE)
            xmlSerializer.setPrefix(NS_VENDOR_PREFIX, NS_VENDOR)
            xmlSerializer.startTag(null, TAG_ROOT)
            // book count
            listener?.onBookCount(1)
            writeCount(xmlSerializer, CD_TYPE_BOOK, 1)
            writeBook(xmlSerializer, book)
            xmlSerializer.endTag(null, TAG_ROOT)
            xmlSerializer.endDocument()
            xmlSerializer.flush()
        } catch (e: Exception) {
            Timber.e(e)
            throw ExporterException(exportParams, e)
        }
        val timeFinish = SystemClock.elapsedRealtime()
        Timber.v("exported in %d ms", timeFinish - timeStart)
    }

    @Throws(IOException::class)
    private fun writeBook(xmlSerializer: XmlSerializer, book: Book) {
        listener?.onBook(book)
        // book
        xmlSerializer.startTag(NS_GNUCASH, TAG_BOOK)
        xmlSerializer.attribute(null, ATTR_KEY_VERSION, BOOK_VERSION)
        // book id
        xmlSerializer.startTag(NS_BOOK, TAG_ID)
        xmlSerializer.attribute(null, ATTR_KEY_TYPE, ATTR_VALUE_GUID)
        xmlSerializer.text(book.uid)
        xmlSerializer.endTag(NS_BOOK, TAG_ID)
        writeCounts(xmlSerializer)
        // export the commodities used in the DB
        writeCommodities(xmlSerializer)
        // prices
        writePrices(xmlSerializer)
        // accounts.
        writeAccounts(xmlSerializer, false)
        // transactions.
        writeTransactions(xmlSerializer, false)
        //transaction templates
        writeTemplateTransactions(xmlSerializer)
        //scheduled actions
        writeScheduledTransactions(xmlSerializer)
        //budgets
        writeBudgets(xmlSerializer)

        xmlSerializer.endTag(NS_GNUCASH, TAG_BOOK)
    }

    @Throws(ExporterException::class)
    override fun writeExport(writer: Writer, exportParams: ExportParams) {
        export(bookUID, writer)
    }
}
