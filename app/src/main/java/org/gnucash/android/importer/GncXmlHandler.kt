/*
 * Copyright (c) 2013 - 2015 Ngewi Fet <ngewif@gmail.com>
 * Copyright (c) 2014 - 2015 Yongxin Wang <fefe.wyx@gmail.com>
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
package org.gnucash.android.importer

import android.content.ContentValues
import android.content.Context
import android.os.CancellationSignal
import org.gnucash.android.app.GnuCashApplication
import org.gnucash.android.app.GnuCashApplication.Companion.appContext
import org.gnucash.android.db.DatabaseHelper
import org.gnucash.android.db.DatabaseHolder
import org.gnucash.android.db.DatabaseSchema.TransactionEntry
import org.gnucash.android.db.adapter.AccountsDbAdapter
import org.gnucash.android.db.adapter.BooksDbAdapter
import org.gnucash.android.db.adapter.BooksDbAdapter.NoActiveBookFoundException
import org.gnucash.android.db.adapter.BudgetsDbAdapter
import org.gnucash.android.db.adapter.CommoditiesDbAdapter
import org.gnucash.android.db.adapter.PricesDbAdapter
import org.gnucash.android.db.adapter.RecurrenceDbAdapter
import org.gnucash.android.db.adapter.ScheduledActionDbAdapter
import org.gnucash.android.db.adapter.TransactionsDbAdapter
import org.gnucash.android.export.xml.GncXmlHelper.ATTR_KEY_TYPE
import org.gnucash.android.export.xml.GncXmlHelper.ATTR_VALUE_FRAME
import org.gnucash.android.export.xml.GncXmlHelper.ATTR_VALUE_NUMERIC
import org.gnucash.android.export.xml.GncXmlHelper.ATTR_VALUE_STRING
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
import org.gnucash.android.export.xml.GncXmlHelper.KEY_EXPORTED
import org.gnucash.android.export.xml.GncXmlHelper.KEY_FAVORITE
import org.gnucash.android.export.xml.GncXmlHelper.KEY_HIDDEN
import org.gnucash.android.export.xml.GncXmlHelper.KEY_NOTES
import org.gnucash.android.export.xml.GncXmlHelper.KEY_PLACEHOLDER
import org.gnucash.android.export.xml.GncXmlHelper.KEY_SCHED_XACTION
import org.gnucash.android.export.xml.GncXmlHelper.KEY_SPLIT_ACCOUNT_SLOT
import org.gnucash.android.export.xml.GncXmlHelper.NS_ACCOUNT
import org.gnucash.android.export.xml.GncXmlHelper.NS_BOOK
import org.gnucash.android.export.xml.GncXmlHelper.NS_BUDGET
import org.gnucash.android.export.xml.GncXmlHelper.NS_CD
import org.gnucash.android.export.xml.GncXmlHelper.NS_COMMODITY
import org.gnucash.android.export.xml.GncXmlHelper.NS_GNUCASH
import org.gnucash.android.export.xml.GncXmlHelper.NS_GNUCASH_ACCOUNT
import org.gnucash.android.export.xml.GncXmlHelper.NS_PRICE
import org.gnucash.android.export.xml.GncXmlHelper.NS_RECURRENCE
import org.gnucash.android.export.xml.GncXmlHelper.NS_SLOT
import org.gnucash.android.export.xml.GncXmlHelper.NS_SPLIT
import org.gnucash.android.export.xml.GncXmlHelper.NS_SX
import org.gnucash.android.export.xml.GncXmlHelper.NS_TRANSACTION
import org.gnucash.android.export.xml.GncXmlHelper.NS_TS
import org.gnucash.android.export.xml.GncXmlHelper.TAG_ACCOUNT
import org.gnucash.android.export.xml.GncXmlHelper.TAG_ADVANCE_CREATE_DAYS
import org.gnucash.android.export.xml.GncXmlHelper.TAG_ADVANCE_REMIND_DAYS
import org.gnucash.android.export.xml.GncXmlHelper.TAG_AUTO_CREATE
import org.gnucash.android.export.xml.GncXmlHelper.TAG_AUTO_CREATE_NOTIFY
import org.gnucash.android.export.xml.GncXmlHelper.TAG_BOOK
import org.gnucash.android.export.xml.GncXmlHelper.TAG_BUDGET
import org.gnucash.android.export.xml.GncXmlHelper.TAG_COMMODITY
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
import org.gnucash.android.export.xml.GncXmlHelper.TAG_QUANTITY
import org.gnucash.android.export.xml.GncXmlHelper.TAG_QUOTE_SOURCE
import org.gnucash.android.export.xml.GncXmlHelper.TAG_QUOTE_TZ
import org.gnucash.android.export.xml.GncXmlHelper.TAG_RECONCILED_DATE
import org.gnucash.android.export.xml.GncXmlHelper.TAG_RECURRENCE
import org.gnucash.android.export.xml.GncXmlHelper.TAG_RECURRENCE_PERIOD
import org.gnucash.android.export.xml.GncXmlHelper.TAG_REM_OCCUR
import org.gnucash.android.export.xml.GncXmlHelper.TAG_ROOT
import org.gnucash.android.export.xml.GncXmlHelper.TAG_SCHEDULED_ACTION
import org.gnucash.android.export.xml.GncXmlHelper.TAG_SLOT
import org.gnucash.android.export.xml.GncXmlHelper.TAG_SLOTS
import org.gnucash.android.export.xml.GncXmlHelper.TAG_SOURCE
import org.gnucash.android.export.xml.GncXmlHelper.TAG_SPACE
import org.gnucash.android.export.xml.GncXmlHelper.TAG_SPLIT
import org.gnucash.android.export.xml.GncXmlHelper.TAG_START
import org.gnucash.android.export.xml.GncXmlHelper.TAG_TEMPLATE_ACCOUNT
import org.gnucash.android.export.xml.GncXmlHelper.TAG_TEMPLATE_TRANSACTIONS
import org.gnucash.android.export.xml.GncXmlHelper.TAG_TIME
import org.gnucash.android.export.xml.GncXmlHelper.TAG_TITLE
import org.gnucash.android.export.xml.GncXmlHelper.TAG_TRANSACTION
import org.gnucash.android.export.xml.GncXmlHelper.TAG_TYPE
import org.gnucash.android.export.xml.GncXmlHelper.TAG_VALUE
import org.gnucash.android.export.xml.GncXmlHelper.TAG_WEEKEND_ADJ
import org.gnucash.android.export.xml.GncXmlHelper.TAG_XCODE
import org.gnucash.android.export.xml.GncXmlHelper.parseDate
import org.gnucash.android.export.xml.GncXmlHelper.parseDateTime
import org.gnucash.android.export.xml.GncXmlHelper.parseSplitAmount
import org.gnucash.android.gnc.GncProgressListener
import org.gnucash.android.model.Account
import org.gnucash.android.model.AccountType
import org.gnucash.android.model.Book
import org.gnucash.android.model.Budget
import org.gnucash.android.model.Commodity
import org.gnucash.android.model.Money
import org.gnucash.android.model.Money.Companion.createZeroInstance
import org.gnucash.android.model.PeriodType
import org.gnucash.android.model.Price
import org.gnucash.android.model.Recurrence
import org.gnucash.android.model.ScheduledAction
import org.gnucash.android.model.Slot
import org.gnucash.android.model.Split
import org.gnucash.android.model.Transaction
import org.gnucash.android.model.TransactionType
import org.gnucash.android.model.WeekendAdjust
import org.gnucash.android.util.set
import org.xml.sax.Attributes
import org.xml.sax.SAXException
import org.xml.sax.helpers.DefaultHandler
import timber.log.Timber
import java.io.Closeable
import java.math.BigDecimal
import java.sql.Timestamp
import java.text.ParseException
import java.util.Calendar
import java.util.Stack
import java.util.TimeZone

/**
 * Handler for parsing the GnuCash XML file.
 * The discovered accounts and transactions are automatically added to the database
 *
 * @author Ngewi Fet <ngewif@gmail.com>
 * @author Yongxin Wang <fefe.wyx@gmail.com>
 */
class GncXmlHandler(
    private val context: Context = appContext,
    private val listener: GncProgressListener? = null,
    private val cancellationSignal: CancellationSignal = CancellationSignal()
) : DefaultHandler(), Closeable {
    /**
     * Adapter for saving the imported accounts
     */
    private var accountsDbAdapter: AccountsDbAdapter? = null

    /**
     * StringBuilder for accumulating characters between XML tags
     */
    private val content = StringBuilder()

    /**
     * Reference to account which is built when each account tag is parsed in the XML file
     */
    private var account: Account? = null

    /**
     * All the accounts found in a file to be imported, used for bulk import mode
     */
    private val accounts = mutableListOf<Account>()

    /**
     * Map of the template accounts to the template transactions UIDs
     */
    private val templateAccountToTransaction = mutableMapOf<String, String>()

    /**
     * Account map for quick referencing from UID
     */
    private val accountMap = mutableMapOf<String, Account>()

    /**
     * ROOT account of the imported book
     */
    private var rootAccount: Account? = null
    private var rootTemplateAccount: Account? = null

    /**
     * Transaction instance which will be built for each transaction found
     */
    private var transaction: Transaction? = null

    /**
     * Accumulate attributes of splits found in this object
     */
    private var split: Split? = null

    /**
     * price table entry
     */
    private var price: Price? = null

    /**
     * The list for all added split for auto-balancing
     */
    private val autoBalanceSplits = mutableListOf<Split>()

    /**
     * [ScheduledAction] instance for each scheduled action parsed
     */
    private var scheduledAction: ScheduledAction? = null

    private var budget: Budget? = null
    private var recurrence: Recurrence? = null
    private var commodity: Commodity? = null
    private val commodities = mutableMapOf<String, Commodity>()

    private var isInTemplates = false

    private val slots = Stack<Slot>()

    private var budgetAccount: Account? = null
    private var budgetPeriod: Long? = null

    /**
     * Flag which says to ignore template transactions until we successfully parse a split amount
     * Is updated for each transaction template split parsed
     */
    private var ignoreTemplateTransaction = true

    /**
     * Flag which notifies the handler to ignore a scheduled action because some error occurred during parsing
     */
    private var ignoreScheduledAction = false

    /**
     * Used for parsing old backup files where recurrence was saved inside the transaction.
     * Newer backup files will not require this
     *
     */
    @Deprecated("Use the new scheduled action elements instead")
    private var recurrencePeriod: Long = 0

    private val booksDbAdapter: BooksDbAdapter = BooksDbAdapter.instance
    private var transactionsDbAdapter: TransactionsDbAdapter? = null
    private var scheduledActionsDbAdapter: ScheduledActionDbAdapter? = null
    private var commoditiesDbAdapter: CommoditiesDbAdapter? = null
    private var pricesDbAdapter: PricesDbAdapter? = null
    private var budgetsDbAdapter: BudgetsDbAdapter? = null
    private val currencyCount = mutableMapOf<String, Int>()

    /**
     * Returns the just-imported book
     *
     * @return the newly imported book
     */
    val importedBook: Book = Book()
    private var holder: DatabaseHolder? = null
    private var countDataType: String? = null
    private var isValidRoot = false
    private var hasBookElement = false
    private val elementNames = Stack<ElementName>()

    /**
     * Creates a handler for handling XML stream events when parsing the XML backup file
     */
    /**
     * Creates a handler for handling XML stream events when parsing the XML backup file
     */
    /**
     * Creates a handler for handling XML stream events when parsing the XML backup file
     */
    init {
        initDb(importedBook.uid)
    }

    private fun initDb(bookUID: String) {
        val databaseHelper = DatabaseHelper(context, bookUID)
        val holder = databaseHelper.holder
        this.holder = holder
        commoditiesDbAdapter = CommoditiesDbAdapter(holder)
        pricesDbAdapter = PricesDbAdapter(commoditiesDbAdapter!!)
        transactionsDbAdapter = TransactionsDbAdapter(commoditiesDbAdapter!!)
        accountsDbAdapter = AccountsDbAdapter(transactionsDbAdapter!!, pricesDbAdapter!!)
        val recurrenceDbAdapter = RecurrenceDbAdapter(holder)
        scheduledActionsDbAdapter = ScheduledActionDbAdapter(recurrenceDbAdapter, transactionsDbAdapter!!)
        budgetsDbAdapter = BudgetsDbAdapter(recurrenceDbAdapter)

        Timber.d("before clean up db")
        // disable foreign key. The database structure should be ensured by the data inserted.
        // it will make insertion much faster.
        accountsDbAdapter!!.enableForeignKey(false)

        recurrenceDbAdapter.deleteAllRecords()
        budgetsDbAdapter!!.deleteAllRecords()
        pricesDbAdapter!!.deleteAllRecords()
        scheduledActionsDbAdapter!!.deleteAllRecords()
        transactionsDbAdapter!!.deleteAllRecords()
        accountsDbAdapter!!.deleteAllRecords()

        commodities.clear()
        val commoditiesDb = commoditiesDbAdapter!!.allRecords
        for (commodity in commoditiesDb) {
            commodities[commodity.key] = commodity
        }
    }

    private fun maybeInitDb(bookUIDOld: String?, bookUIDNew: String) {
        if (bookUIDOld != null && bookUIDOld != bookUIDNew) {
            holder?.close()
            initDb(bookUIDNew)
        }
    }

    @Throws(SAXException::class)
    override fun startElement(
        uri: String,
        localName: String,
        qualifiedName: String?,
        attributes: Attributes
    ) {
        cancellationSignal.throwIfCanceled()
        elementNames.push(ElementName(uri, localName, qualifiedName))
        if (!isValidRoot) {
            if (TAG_ROOT == localName || AccountsTemplate.TAG_ROOT == localName) {
                isValidRoot = true
                return
            }
            throw SAXException("Expected root element")
        }

        when (localName) {
            TAG_BOOK -> handleStartBook(uri)
            TAG_ACCOUNT -> handleStartAccount(uri)
            TAG_TRANSACTION -> handleStartTransaction()
            TAG_SPLIT -> handleStartSplit(uri)
            TAG_TEMPLATE_TRANSACTIONS -> handleStartTemplateTransactions()
            TAG_SCHEDULED_ACTION -> handleStartScheduledAction()
            TAG_PRICE -> handleStartPrice()
            TAG_CURRENCY -> handleStartCurrency()
            TAG_COMMODITY -> handleStartCommodity()
            TAG_BUDGET -> handleStartBudget(uri)
            TAG_RECURRENCE -> handleStartRecurrence(uri)
            TAG_SLOT -> handleStartSlot()
            TAG_VALUE -> handleStartValue(uri, attributes)
            TAG_COUNT_DATA -> handleStartCountData(attributes)
        }
    }

    private fun handleStartTemplateTransactions() {
        isInTemplates = true
    }

    private fun handleStartSlot() {
        slots.push(Slot.empty())
    }

    @Throws(SAXException::class)
    override fun endElement(uri: String, localName: String, qualifiedName: String?) {
        val elementName = elementNames.pop()
        if (!isValidRoot) {
            return
        }
        if (uri != elementName.uri || localName != elementName.localName) {
            throw SAXException("Inconsistent element: {$uri, $localName} Expected $elementName")
        }

        val characterString = content.toString().trim()
        //reset the accumulated characters
        content.setLength(0)

        when (localName) {
            TAG_NAME -> handleEndName(uri, characterString)
            TAG_ID -> handleEndId(uri, characterString)
            TAG_TYPE -> handleEndType(uri, characterString)
            TAG_BOOK, TAG_ROOT, AccountsTemplate.TAG_ROOT -> handleEndBook(localName)
            TAG_SPACE -> handleEndSpace(uri, characterString)
            TAG_FRACTION -> handleEndFraction(characterString)
            TAG_QUOTE_SOURCE -> handleEndQuoteSource(characterString)
            TAG_QUOTE_TZ -> handleEndQuoteTz(characterString)
            TAG_XCODE -> handleEndXcode(characterString)
            TAG_DESCRIPTION -> handleEndDescription(uri, characterString)
            TAG_COMMODITY -> handleEndCommodity(uri)
            TAG_CURRENCY -> handleEndCurrency(uri)
            TAG_PARENT -> handleEndParent(uri, characterString)
            TAG_ACCOUNT -> handleEndAccount(uri, characterString)
            TAG_SLOT -> handleEndSlot()
            TAG_KEY -> handleEndKey(uri, characterString)
            TAG_VALUE -> handleEndValue(uri, characterString)
            TAG_SLOTS -> handleEndSlots(uri)
            TAG_DATE -> handleEndDate(uri, characterString)
            TAG_RECURRENCE_PERIOD -> handleEndPeriod(uri, characterString)
            TAG_MEMO -> handleEndMemo(uri, characterString)
            TAG_QUANTITY -> handleEndQuantity(uri, characterString)
            TAG_SPLIT -> handleEndSplit(uri)
            TAG_TRANSACTION -> handleEndTransaction()
            TAG_TEMPLATE_TRANSACTIONS -> handleEndTemplateTransactions()
            TAG_ENABLED -> handleEndEnabled(uri, characterString)
            TAG_AUTO_CREATE -> handleEndAutoCreate(uri, characterString)
            TAG_AUTO_CREATE_NOTIFY -> handleEndAutoCreateNotify(uri, characterString)
            TAG_ADVANCE_CREATE_DAYS -> handleEndAdvanceCreateDays(uri, characterString)
            TAG_ADVANCE_REMIND_DAYS -> handleEndAdvanceRemindDays(uri, characterString)
            TAG_INSTANCE_COUNT -> handleEndInstanceCount(uri, characterString)
            TAG_NUM -> handleEndNumber(uri, characterString)
            TAG_NUM_OCCUR -> handleEndNumberOccurrence(uri, characterString)
            TAG_REM_OCCUR -> handleEndRemainingOccurrence(uri, characterString)
            TAG_MULT -> handleEndMultiplier(uri, characterString)
            TAG_PERIOD_TYPE -> handleEndPeriodType(uri, characterString)
            TAG_WEEKEND_ADJ -> handleEndWeekendAdjust(uri, characterString)
            TAG_GDATE -> handleEndGDate(characterString)
            TAG_TEMPLATE_ACCOUNT -> handleEndTemplateAccount(uri, characterString)
            TAG_RECURRENCE -> handleEndRecurrence(uri)
            TAG_SCHEDULED_ACTION -> handleEndScheduledAction()
            TAG_SOURCE -> handleEndSource(uri, characterString)
            TAG_PRICE -> handleEndPrice()
            TAG_BUDGET -> handleEndBudget()
            TAG_NUM_PERIODS -> handleEndNumPeriods(uri, characterString)
            TAG_COUNT_DATA -> handleEndCountData(characterString)
            TAG_TITLE -> handleEndTitle(uri, characterString)
        }
    }

    @Throws(SAXException::class)
    override fun characters(chars: CharArray, start: Int, length: Int) {
        content.append(chars, start, length)
    }

    @Throws(SAXException::class)
    override fun endDocument() {
        super.endDocument()

        val imbalanceAccounts = mutableMapOf<String, Account>()
        val imbalancePrefix = AccountsDbAdapter.getImbalanceAccountPrefix(context)
        val rootUID = rootAccount!!.uid
        for (account in accounts) {
            if ((account.parentUID == null && !account.isRoot) || rootUID == account.parentUID) {
                if (account.name.startsWith(imbalancePrefix)) {
                    imbalanceAccounts[account.name.substring(imbalancePrefix.length)] = account
                }
            }
        }

        // Set the account for created balancing splits to correct imbalance accounts
        for (split in autoBalanceSplits) {
            // XXX: yes, getAccountUID() returns a currency UID in this case (see Transaction.createAutoBalanceSplit())
            val currencyUID = split.accountUID
            if (currencyUID == null) continue
            var imbAccount = imbalanceAccounts[currencyUID]
            if (imbAccount == null) {
                val commodity = commoditiesDbAdapter!!.getRecord(currencyUID)
                imbAccount = Account(imbalancePrefix + commodity.currencyCode, commodity)
                imbAccount.parentUID = rootAccount!!.uid
                imbAccount.accountType = AccountType.BANK
                imbalanceAccounts[currencyUID] = imbAccount
                accountsDbAdapter!!.insert(imbAccount)
                listener?.onAccount(imbAccount)
            }
            split.accountUID = imbAccount.uid
        }

        var mostAppearedCurrency: String? = ""
        var mostCurrencyAppearance = 0
        for (entry in currencyCount.entries) {
            if (entry.value > mostCurrencyAppearance) {
                mostCurrencyAppearance = entry.value
                mostAppearedCurrency = entry.key
            }
        }
        if (mostCurrencyAppearance > 0) {
            commoditiesDbAdapter!!.setDefaultCurrencyCode(mostAppearedCurrency)
        }

        saveToDatabase()

        // generate missed scheduled transactions.
        //FIXME ScheduledActionService.schedulePeriodic(context);
    }

    /**
     * Saves the imported data to the database.
     * We on purpose do not set the book active. Only import. Caller should handle activation
     */
    private fun saveToDatabase() {
        accountsDbAdapter!!.enableForeignKey(true)
        maybeClose() //close it after import
    }

    override fun close() {
        holder!!.close()
    }

    private fun maybeClose() {
        var activeBookUID: String? = null
        try {
            activeBookUID = GnuCashApplication.activeBookUID
        } catch (_: NoActiveBookFoundException) {
        }
        val newBookUID = importedBook.uid
        if (activeBookUID == null || activeBookUID != newBookUID) {
            close()
        }
    }

    fun cancel() {
        cancellationSignal.cancel()
    }

    /**
     * Returns the unique identifier of the just-imported book
     *
     * @return GUID of the newly imported book
     */
    val importedBookUID: String
        get() = importedBook.uid

    /**
     * Returns the currency for an account which has been parsed (but not yet saved to the db)
     *
     * This is used when parsing splits to assign the right currencies to the splits
     *
     * @param accountUID GUID of the account
     * @return Commodity of the account
     */
    private fun getCommodityForAccount(accountUID: String): Commodity {
        try {
            return accountMap[accountUID]!!.commodity
        } catch (e: Exception) {
            Timber.e(e)
            return Commodity.DEFAULT_COMMODITY
        }
    }

    /**
     * Sets the by days of the scheduled action to the day of the week of the start time.
     *
     *
     * Until we implement parsing of days of the week for scheduled actions,
     * this ensures they are executed at least once per week.
     */
    private fun setMinimalScheduledActionByDays() {
        val scheduledAction = scheduledAction!!
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = scheduledAction.startTime
        scheduledAction.recurrence!!.byDays = listOf(calendar.get(Calendar.DAY_OF_WEEK))
    }

    private fun getCommodity(commodity: Commodity?): Commodity? {
        if (commodity == null) return null
        var namespace = commodity.namespace
        if (namespace.isEmpty()) return null
        val code = commodity.mnemonic
        if (code.isEmpty()) return null

        if (Commodity.COMMODITY_ISO4217 == namespace) {
            namespace = Commodity.COMMODITY_CURRENCY
        }
        val key = "$namespace::$code"
        return commodities[key]
    }

    @Throws(SAXException::class)
    private fun handleEndAccount(uri: String, value: String) {
        if (NS_GNUCASH == uri) {
            var account = account!!
            if (isInTemplates) {
                // check ROOT account
                if (account.isRoot) {
                    if (rootTemplateAccount == null) {
                        rootTemplateAccount = account
                    } else {
                        throw SAXException("Multiple ROOT Template accounts exist in book")
                    }
                } else if (rootTemplateAccount == null) {
                    account = Account(AccountsDbAdapter.TEMPLATE_ACCOUNT_NAME, Commodity.template)
                    rootTemplateAccount = account
                    rootTemplateAccount!!.accountType = AccountType.ROOT
                }
            } else {
                // check ROOT account
                if (account.isRoot) {
                    if (rootAccount == null) {
                        rootAccount = account
                        importedBook.rootAccountUID = account.uid
                    } else {
                        throw SAXException("Multiple ROOT accounts exist in book")
                    }
                } else if (rootAccount == null) {
                    account = Account(AccountsDbAdapter.ROOT_ACCOUNT_NAME)
                    rootAccount = account
                    rootAccount!!.accountType = AccountType.ROOT
                    importedBook.rootAccountUID = account.uid
                }
                accounts.add(account)
                listener?.onAccount(account)
            }
            accountsDbAdapter!!.insert(account)
            accountMap[account.uid] = account
            // prepare for next input
            this.account = null
        } else if (NS_SPLIT == uri) {
            val accountUID = value
            val split = split!!
            split.accountUID = accountUID
            if (isInTemplates) {
                templateAccountToTransaction[accountUID] = transaction!!.uid
            } else {
                //the split amount uses the account currency
                split.quantity =
                    split.quantity.withCommodity(getCommodityForAccount(accountUID))
                //the split value uses the transaction currency
                split.value = split.value.withCommodity(transaction!!.commodity)
            }
        }
    }

    private fun handleEndAdvanceCreateDays(uri: String, value: String) {
        if (NS_SX == uri) {
            scheduledAction!!.advanceCreateDays = value.toInt()
        }
    }

    private fun handleEndAdvanceRemindDays(uri: String, value: String) {
        if (NS_SX == uri) {
            scheduledAction!!.advanceRemindDays = value.toInt()
        }
    }

    private fun handleEndAutoCreate(uri: String, value: String) {
        if (NS_SX == uri) {
            scheduledAction!!.isAutoCreateNotify = value == "y"
        }
    }

    private fun handleEndAutoCreateNotify(uri: String, value: String) {
        if (NS_SX == uri) {
            scheduledAction!!.isAutoCreateNotify = value == "y"
        }
    }

    private fun handleEndBook(localName: String) {
        if (hasBookElement) {
            if (TAG_BOOK == localName) {
                booksDbAdapter.replace(this.importedBook)
                listener?.onBook(this.importedBook)
            }
        } else {
            booksDbAdapter.replace(this.importedBook)
            listener?.onBook(this.importedBook)
        }
    }

    private fun handleEndBudget() {
        val budget = budget ?: return
        if (!budget.budgetAmounts.isEmpty()) { //ignore if no budget amounts exist for the budget
            // TODO: 01.06.2016 Re-enable import of Budget stuff when the UI is complete */
            budgetsDbAdapter!!.insert(budget)
            listener?.onBudget(budget)
        }
        this.budget = null
    }

    @Throws(SAXException::class)
    private fun handleEndCommodity(uri: String) {
        if (NS_ACCOUNT == uri) {
            val account = account
            if (account != null) {
                val commodity = getCommodity(commodity)
                if (commodity == null) {
                    throw SAXException("Commodity with '${this.commodity}' not found in the database for account")
                }
                account.commodity = commodity
                if (commodity.isCurrency) {
                    val currencyId = commodity.currencyCode
                    var count = currencyCount[currencyId] ?: 0
                    currencyCount[currencyId] = count + 1
                }
            }
        } else if (NS_GNUCASH == uri) {
            var commodity = getCommodity(commodity)
            if (commodity == null) {
                commodity = this.commodity
                commoditiesDbAdapter!!.insert(commodity!!)
                commodities[commodity.key] = commodity
            }
            listener?.onCommodity(commodity)
        } else if (NS_PRICE == uri) {
            val price = price
            if (price != null) {
                val commodity = getCommodity(commodity)
                if (commodity == null) {
                    throw SAXException("Commodity with '" + this.commodity + "' not found in the database for price")
                }
                price.commodity = commodity
            }
        }
        commodity = null
    }

    private fun handleEndCountData(value: String) {
        if (!countDataType.isNullOrEmpty() && value.isNotEmpty()) {
            val count = value.toLong()
            when (countDataType) {
                CD_TYPE_ACCOUNT -> listener?.onAccountCount(count)
                CD_TYPE_BOOK -> listener?.onBookCount(count)
                CD_TYPE_BUDGET -> listener?.onBudgetCount(count)
                CD_TYPE_COMMODITY -> listener?.onCommodityCount(count)
                CD_TYPE_PRICE -> listener?.onPriceCount(count)
                CD_TYPE_SCHEDXACTION -> listener?.onScheduleCount(count)
                CD_TYPE_TRANSACTION -> listener?.onTransactionCount(count)
            }
        }
        countDataType = null
    }

    @Throws(SAXException::class)
    private fun handleEndCurrency(uri: String) {
        val commodity = getCommodity(commodity)
        if (NS_PRICE == uri) {
            if (commodity == null) {
                throw SAXException("Currency with '" + this.commodity + "' not found in the database for price")
            }
            price?.currency = commodity
        } else if (NS_TRANSACTION == uri) {
            if (commodity == null) {
                throw SAXException("Currency with '" + this.commodity + "' not found in the database for transaction")
            }
            transaction?.commodity = commodity
        }
        this.commodity = null
    }

    @Throws(SAXException::class)
    private fun handleEndDate(uri: String, dateString: String) {
        if (NS_TS == uri) {
            try {
                val date = parseDateTime(dateString)

                val elementParent = elementNames.peek()
                val uriParent = elementParent.uri
                val tagParent = elementParent.localName

                if (NS_TRANSACTION == uriParent) {
                    when (tagParent) {
                        TAG_DATE_ENTERED -> transaction!!.createdTimestamp = Timestamp(date)
                        TAG_DATE_POSTED -> transaction!!.time = date
                    }
                } else if (NS_PRICE == uriParent) {
                    if (TAG_TIME == tagParent) {
                        price!!.date = date
                    }
                } else if (NS_SPLIT == uriParent) {
                    if (TAG_RECONCILED_DATE == tagParent) {
                        split!!.reconcileDate = date
                    }
                }
            } catch (e: ParseException) {
                val message = "Unable to parse transaction date $dateString"
                throw SAXException(message, e)
            }
        }
    }

    private fun handleEndDescription(uri: String, description: String) {
        if (NS_ACCOUNT == uri) {
            account!!.description = description
        } else if (NS_BUDGET == uri) {
            budget!!.description = description
        } else if (NS_TRANSACTION == uri) {
            transaction!!.description = description
        }
    }

    private fun handleEndEnabled(uri: String, value: String) {
        if (NS_SX == uri) {
            scheduledAction!!.isEnabled = value == "y"
        }
    }

    private fun handleEndFraction(fraction: String) {
        commodity?.smallestFraction = fraction.toInt()
    }

    @Throws(SAXException::class)
    private fun handleEndGDate(dateString: String) {
        try {
            val date = parseDate(dateString)

            val elementParent = elementNames.peek()
            val uriParent = elementParent.uri
            val tagParent = elementParent.localName

            if (NS_SLOT == uriParent) {
                val slot = slots.peek()
                if (slot.type == Slot.TYPE_GDATE) {
                    slot.value = date
                }
            } else if (NS_RECURRENCE == uriParent) {
                if (TAG_START == tagParent) {
                    recurrence!!.periodStart = date
                } else if (TAG_END == tagParent) {
                    recurrence!!.periodEnd = date
                }
            } else if (NS_SX == uriParent) {
                if (TAG_START == tagParent) {
                    scheduledAction!!.startTime = date
                } else if (TAG_END == tagParent) {
                    scheduledAction!!.endTime = date
                } else if (TAG_LAST == tagParent) {
                    scheduledAction!!.lastRunTime = date
                }
            }
        } catch (e: ParseException) {
            val msg = "Invalid scheduled action date $dateString"
            throw SAXException(msg, e)
        }
    }

    private fun handleEndId(uri: String, id: String) {
        if (NS_ACCOUNT == uri) {
            account!!.setUID(id)
        } else if (NS_BOOK == uri) {
            maybeInitDb(importedBook.uid, id)
            importedBook.setUID(id)
        } else if (NS_BUDGET == uri) {
            budget!!.setUID(id)
        } else if (NS_COMMODITY == uri) {
            commodity!!.mnemonic = id
        } else if (NS_PRICE == uri) {
            price!!.setUID(id)
        } else if (NS_SPLIT == uri) {
            split!!.setUID(id)
        } else if (NS_SX == uri) {
            // The template account name.
            scheduledAction!!.setUID(id)
        } else if (NS_TRANSACTION == uri) {
            transaction!!.setUID(id)
        }
    }

    private fun handleEndInstanceCount(uri: String, value: String) {
        if (NS_SX == uri) {
            scheduledAction!!.instanceCount = value.toInt()
        }
    }

    private fun handleEndKey(uri: String, key: String) {
        if (NS_SLOT == uri) {
            val slot = slots.peek()
            slot.key = key

            if (budget != null && KEY_NOTES != key) {
                if (budgetAccount == null) {
                    val accountUID: String? = key
                    val account = accountMap[accountUID]
                    if (account != null) {
                        budgetAccount = account
                    }
                } else {
                    try {
                        budgetPeriod = key.toLong()
                    } catch (e: NumberFormatException) {
                        Timber.e(e, "Invalid budget period: %s", key)
                    }
                }
            }
        }
    }

    private fun handleEndMemo(uri: String, memo: String) {
        if (NS_SPLIT == uri) {
            split!!.memo = memo
        }
    }

    private fun handleEndMultiplier(uri: String, multiplier: String) {
        if (NS_RECURRENCE == uri) {
            recurrence!!.multiplier = multiplier.toInt()
        }
    }

    private fun handleEndName(uri: String, name: String) {
        if (NS_ACCOUNT == uri) {
            account!!.name = name
            account!!.fullName = name
        } else if (NS_BUDGET == uri) {
            budget!!.name = name
        } else if (NS_COMMODITY == uri) {
            commodity!!.fullname = name
        } else if (NS_SX == uri) {
            scheduledAction!!.name = name
        }
    }

    private fun handleEndNumber(uri: String, value: String) {
        if (NS_TRANSACTION == uri) {
            transaction!!.number = value
        }
    }

    private fun handleEndNumberOccurrence(uri: String, value: String) {
        if (NS_SX == uri) {
            scheduledAction!!.totalPlannedExecutionCount = value.toInt()
        }
    }

    private fun handleEndNumPeriods(uri: String, periods: String) {
        if (NS_BUDGET == uri) {
            budget!!.numberOfPeriods = periods.toLong()
        }
    }

    private fun handleEndParent(uri: String, parent: String) {
        if (NS_ACCOUNT == uri) {
            account!!.parentUID = parent
        }
    }

    private fun handleEndPeriod(uri: String, period: String) {
        if (NS_TRANSACTION == uri) {
            //for parsing of old backup files
            recurrencePeriod = period.toLong()
            transaction!!.isTemplate = recurrencePeriod > 0
        }
    }

    private fun handleEndPeriodType(uri: String, type: String) {
        if (NS_RECURRENCE == uri) {
            val periodType = PeriodType.of(type)
            if (periodType != PeriodType.ONCE) {
                recurrence!!.periodType = periodType
            } else {
                Timber.e("Invalid period: %s", type)
                ignoreScheduledAction = true
            }
        }
    }

    private fun handleEndPrice() {
        val price = price
        if (price != null) {
            pricesDbAdapter!!.insert(price)
            listener?.onPrice(price)
        }
        this.price = null
    }

    @Throws(SAXException::class)
    private fun handleEndQuantity(uri: String, value: String) {
        if (NS_SPLIT == uri) {
            // delay the assignment of currency when the split account is seen
            try {
                val amount = parseSplitAmount(value).abs()
                split!!.quantity = Money(amount, Commodity.DEFAULT_COMMODITY)
            } catch (e: ParseException) {
                val msg = "Invalid split quantity $value"
                throw SAXException(msg, e)
            }
        }
    }

    private fun handleEndQuoteSource(source: String) {
        commodity?.quoteSource = source
    }

    private fun handleEndQuoteTz(tzId: String) {
        if (tzId.isNotEmpty()) {
            commodity?.quoteTimeZone = TimeZone.getTimeZone(tzId)
        }
    }

    private fun handleEndRecurrence(uri: String) {
        if (NS_BUDGET == uri) {
            budget!!.recurrence = recurrence
        } else if (NS_GNUCASH == uri) {
            scheduledAction?.setRecurrence(recurrence)
        }
    }

    private fun handleEndRemainingOccurrence(uri: String, value: String) {
        if (NS_SX == uri) {
            scheduledAction!!.totalPlannedExecutionCount = value.toInt()
        }
    }

    private fun handleEndScheduledAction() {
        val scheduledAction = scheduledAction!!
        if (scheduledAction.actionUID != null && !ignoreScheduledAction) {
            if (scheduledAction.recurrence!!.periodType == PeriodType.WEEK) {
                // TODO: implement parsing of by days for scheduled actions
                setMinimalScheduledActionByDays()
            }
            scheduledActionsDbAdapter!!.insert(scheduledAction)
            listener?.onSchedule(scheduledAction)
            if (scheduledAction.actionType == ScheduledAction.ActionType.TRANSACTION) {
                val transactionUID = scheduledAction.actionUID
                val txValues = ContentValues()
                txValues[TransactionEntry.COLUMN_SCHEDX_ACTION_UID] = scheduledAction.uid
                transactionsDbAdapter!!.updateRecord(transactionUID!!, txValues)
            }
            this.scheduledAction = null
        }
        ignoreScheduledAction = false
    }

    private fun handleEndSlot(slot: Slot = slots.pop()) {
        when (slot.key) {
            KEY_PLACEHOLDER -> account?.isPlaceholder = slot.asString.toBoolean()

            KEY_COLOR -> {
                val color = slot.asString
                //GnuCash exports the account color in format #rrrgggbbb, but we need only #rrggbb.
                //so we trim the last digit in each block, doesn't affect the color much
                try {
                    account?.setColor(color)
                } catch (e: IllegalArgumentException) {
                    //sometimes the color entry in the account file is "Not set" instead of just blank. So catch!
                    Timber.e(e, "Invalid color code \"%s\" for account %s", color, account)
                }
            }

            KEY_FAVORITE -> account?.isFavorite = slot.asString.toBoolean()

            KEY_HIDDEN -> account?.isHidden = slot.asString.toBoolean()

            KEY_DEFAULT_TRANSFER_ACCOUNT -> account?.defaultTransferAccountUID = slot.asString

            KEY_EXPORTED -> transaction?.isExported = slot.asString.toBoolean()

            KEY_SCHED_XACTION -> {
                val split = this.split ?: return
                for (s in slot.asFrame) {
                    when (s.key) {
                        KEY_SPLIT_ACCOUNT_SLOT -> split.scheduledActionAccountUID = s.asGUID

                        KEY_CREDIT_FORMULA -> handleEndSlotTemplateFormula(
                            split,
                            s.asString,
                            TransactionType.CREDIT
                        )

                        KEY_CREDIT_NUMERIC ->
                            handleEndSlotTemplateNumeric(split, s.asNumeric, TransactionType.CREDIT)

                        KEY_DEBIT_FORMULA ->
                            handleEndSlotTemplateFormula(split, s.asString, TransactionType.DEBIT)

                        KEY_DEBIT_NUMERIC ->
                            handleEndSlotTemplateNumeric(split, s.asNumeric, TransactionType.DEBIT)
                    }
                }
            }

            else -> if (!slots.isEmpty()) {
                val head = slots.peek()
                if (head.type == Slot.TYPE_FRAME) {
                    head.add(slot)
                }
            }
        }
    }

    private fun handleEndSlots(uri: String) {
        slots.clear()
    }

    /**
     * Handles the case when we reach the end of the template formula slot
     *
     * @param value Parsed characters containing split amount
     */
    private fun handleEndSlotTemplateFormula(
        split: Split,
        value: String,
        splitType: TransactionType
    ) {
        if (value.isEmpty()) return
        try {
            // HACK: Check for bug #562. If a value has already been set, ignore the one just read
            if (split.value.isAmountZero) {
                var accountUID = split.scheduledActionAccountUID
                if (accountUID.isNullOrEmpty()) {
                    accountUID = split.accountUID!!
                }
                val commodity = getCommodityForAccount(accountUID)

                split.value = Money(value, commodity)
                split.type = splitType
                ignoreTemplateTransaction = false //we have successfully parsed an amount
            }
        } catch (e: NumberFormatException) {
            Timber.e(e, "Error parsing template split formula [%s]", value)
        }
    }

    /**
     * Handles the case when we reach the end of the template numeric slot
     *
     * @param value Parsed characters containing split amount
     */
    private fun handleEndSlotTemplateNumeric(
        split: Split,
        value: String,
        splitType: TransactionType
    ) {
        if (value.isEmpty()) return
        try {
            // HACK: Check for bug #562. If a value has already been set, ignore the one just read
            if (split.value.isAmountZero) {
                val splitAmount = parseSplitAmount(value)
                var accountUID = split.scheduledActionAccountUID
                if (accountUID.isNullOrEmpty()) {
                    accountUID = split.accountUID!!
                }
                val commodity = getCommodityForAccount(accountUID)

                split.value = Money(splitAmount, commodity)
                split.type = splitType
                ignoreTemplateTransaction = false //we have successfully parsed an amount
            }
        } catch (e: NumberFormatException) {
            Timber.e(e, "Error parsing template split numeric [%s]", value)
        } catch (e: ParseException) {
            Timber.e(e, "Error parsing template split numeric [%s]", value)
        }
    }

    private fun handleEndSource(uri: String, source: String) {
        if (NS_PRICE == uri) {
            price!!.source = source
        }
    }

    private fun handleEndSpace(uri: String, space: String) {
        if (NS_COMMODITY == uri) {
            commodity!!.namespace = space
        }
    }

    private fun handleEndSplit(uri: String) {
        //todo: import split reconciled state and date
        if (NS_TRANSACTION == uri) {
            transaction!!.addSplit(split!!)
        }
    }

    private fun handleEndTemplateAccount(uri: String, uid: String) {
        if (NS_SX == uri) {
            val scheduledAction = scheduledAction!!
            if (scheduledAction.actionType == ScheduledAction.ActionType.TRANSACTION) {
                scheduledAction.setTemplateAccountUID(uid)
                val transactionUID = templateAccountToTransaction[uid]
                scheduledAction.actionUID = transactionUID
            } else {
                scheduledAction.actionUID = importedBook.uid
            }
        }
    }

    private fun handleEndTemplateTransactions() {
        isInTemplates = false
    }

    private fun handleEndTitle(uri: String, title: String) {
        if (NS_GNUCASH_ACCOUNT == uri) {
            importedBook.displayName = title
        }
    }

    private fun handleEndTransaction() {
        transaction!!.isTemplate = isInTemplates
        val imbSplit = transaction!!.createAutoBalanceSplit()
        if (imbSplit != null) {
            autoBalanceSplits.add(imbSplit)
        }
        if (isInTemplates) {
            if (!ignoreTemplateTransaction) {
                transactionsDbAdapter!!.insert(transaction!!)
            }
        } else {
            transactionsDbAdapter!!.insert(transaction!!)
            listener?.onTransaction(transaction!!)
        }
        if (recurrencePeriod > 0) { //if we find an old format recurrence period, parse it
            transaction!!.isTemplate = true
            val scheduledAction =
                ScheduledAction.parseScheduledAction(transaction!!, recurrencePeriod)
            scheduledActionsDbAdapter!!.insert(scheduledAction)
            listener?.onSchedule(scheduledAction)
        }
        recurrencePeriod = 0
        ignoreTemplateTransaction = true
        transaction = null
    }

    private fun handleEndType(uri: String, type: String) {
        if (NS_ACCOUNT == uri) {
            val accountType = AccountType.valueOf(type)
            account!!.accountType = accountType
        } else if (NS_PRICE == uri) {
            price!!.type = Price.Type.of(type)
        }
    }

    @Throws(SAXException::class)
    private fun handleEndValue(uri: String, value: String) {
        if (NS_PRICE == uri) {
            val price = price ?: return
            val parts = value.split("/".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            if (parts.size != 2) {
                throw SAXException("Invalid price $value")
            } else {
                price.valueNum = parts[0].toLong()
                price.valueDenom = parts[1].toLong()
                Timber.d("price %s .. %s/%s", value, price.valueNum, price.valueDenom)
            }
        } else if (NS_SLOT == uri) {
            val slot = slots.peek()
            when (slot.type) {
                Slot.TYPE_GUID, Slot.TYPE_NUMERIC, Slot.TYPE_STRING -> slot.value = value
            }
            val budget = budget
            if (budget != null) {
                var isNote = false
                if (slots.size >= 3) {
                    val parent = slots[slots.size - 2]
                    val isParentSlotIsFrame = parent.type == Slot.TYPE_FRAME
                    val grandparent = slots[slots.size - 3]
                    val isGrandparentIsNotes =
                        (grandparent.type == Slot.TYPE_FRAME) && (KEY_NOTES == grandparent.key)
                    isNote = isParentSlotIsFrame && isGrandparentIsNotes
                }

                when (slot.type) {
                    ATTR_VALUE_FRAME -> {
                        budgetAccount = null
                        budgetPeriod = null
                    }

                    ATTR_VALUE_NUMERIC -> {
                        if (!isNote && (budgetAccount != null) && (budgetPeriod != null)) {
                            try {
                                val amount = parseSplitAmount(value)
                                budget.addAmount(budgetAccount!!, budgetPeriod!!, amount)
                            } catch (e: ParseException) {
                                Timber.e(e, "Bad budget amount: %s", value)
                            }
                        }
                        budgetPeriod = null
                    }

                    ATTR_VALUE_STRING -> {
                        if (isNote && (budgetAccount != null) && (budgetPeriod != null)) {
                            var budgetAmount =
                                budget.getBudgetAmount(budgetAccount!!, budgetPeriod!!)
                            if (budgetAmount == null) {
                                budgetAmount = budget.addAmount(
                                    budgetAccount!!,
                                    budgetPeriod!!,
                                    BigDecimal.ZERO
                                )
                            }
                            budgetAmount.notes = value
                        }
                        budgetPeriod = null
                    }
                }
            } else if (KEY_NOTES == slot.key && ATTR_VALUE_STRING == slot.type) {
                transaction?.note = value
                account?.note = value
            }
        } else if (NS_SPLIT == uri) {
            val split = split!!
            try {
                // The value and quantity can have different sign for custom currency(stock).
                // Use the sign of value for split, as it would not be custom currency
                //this is intentional: GnuCash XML formats split amounts, credits are negative, debits are positive.
                split.type =
                    if (value[0] == '-') TransactionType.CREDIT else TransactionType.DEBIT
                val amount = parseSplitAmount(value)
                split.value = Money(amount, Commodity.DEFAULT_COMMODITY)
            } catch (e: ParseException) {
                val msg = "Invalid split quantity $value"
                throw SAXException(msg, e)
            }
        }
    }

    private fun handleEndWeekendAdjust(uri: String, adjust: String) {
        if (NS_RECURRENCE == uri) {
            val weekendAdjust = WeekendAdjust.of(adjust)
            recurrence!!.weekendAdjust = weekendAdjust
        }
    }

    private fun handleEndXcode(xcode: String) {
        commodity?.cusip = xcode
    }

    private fun handleStartAccount(uri: String) {
        if (NS_GNUCASH == uri) {
            // dummy name, will be replaced when we find name tag
            account = Account("")
        }
    }

    private fun handleStartBook(uri: String) {
        if (NS_GNUCASH == uri) {
            hasBookElement = true
        }
    }

    private fun handleStartBudget(uri: String) {
        if (NS_GNUCASH == uri) {
            budget = Budget()
        }
    }

    private fun handleStartCommodity() {
        commodity = Commodity("", "")
    }

    private fun handleStartCountData(attributes: Attributes) {
        countDataType = attributes.getValue(NS_CD, ATTR_KEY_TYPE)
    }

    private fun handleStartCurrency() {
        commodity = Commodity("", "")
    }

    private fun handleStartPrice() {
        price = Price()
    }

    private fun handleStartRecurrence(uri: String) {
        recurrence = Recurrence(PeriodType.MONTH)
    }

    private fun handleStartScheduledAction() {
        //default to transaction type, will be changed during parsing
        scheduledAction = ScheduledAction(ScheduledAction.ActionType.TRANSACTION)
    }

    private fun handleStartSplit(uri: String) {
        if (NS_TRANSACTION == uri) {
            split = Split(createZeroInstance(rootAccount!!.commodity), "")
        }
    }

    private fun handleStartTransaction() {
        transaction = Transaction("") // dummy name will be replaced
        transaction!!.isExported = true // default to exported when import transactions
    }

    private fun handleStartValue(uri: String, attributes: Attributes) {
        if (NS_SLOT == uri) {
            val slot = slots.peek()
            slot.type = attributes.getValue(ATTR_KEY_TYPE)
        }
    }

    private class ElementName(val uri: String, val localName: String, val qualifiedName: String?) {
        override fun toString(): String {
            return "{$uri,$localName, $qualifiedName}"
        }
    }
}
