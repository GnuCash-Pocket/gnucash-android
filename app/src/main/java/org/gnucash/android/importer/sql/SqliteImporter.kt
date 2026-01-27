package org.gnucash.android.importer.sql

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import org.gnucash.android.db.DatabaseHelper
import org.gnucash.android.db.DatabaseHolder
import org.gnucash.android.db.adapter.AccountsDbAdapter
import org.gnucash.android.db.adapter.BudgetsDbAdapter
import org.gnucash.android.db.adapter.CommoditiesDbAdapter
import org.gnucash.android.db.adapter.PricesDbAdapter
import org.gnucash.android.db.adapter.RecurrenceDbAdapter
import org.gnucash.android.db.adapter.ScheduledActionDbAdapter
import org.gnucash.android.db.adapter.SplitsDbAdapter
import org.gnucash.android.db.adapter.TransactionsDbAdapter
import org.gnucash.android.db.forEach
import org.gnucash.android.db.getBoolean
import org.gnucash.android.db.getDouble
import org.gnucash.android.db.getInt
import org.gnucash.android.db.getLong
import org.gnucash.android.db.getString
import org.gnucash.android.export.xml.GncXmlHelper.KEY_COLOR
import org.gnucash.android.export.xml.GncXmlHelper.KEY_CREDIT_FORMULA
import org.gnucash.android.export.xml.GncXmlHelper.KEY_CREDIT_NUMERIC
import org.gnucash.android.export.xml.GncXmlHelper.KEY_DEBIT_FORMULA
import org.gnucash.android.export.xml.GncXmlHelper.KEY_DEBIT_NUMERIC
import org.gnucash.android.export.xml.GncXmlHelper.KEY_DEFAULT_TRANSFER_ACCOUNT
import org.gnucash.android.export.xml.GncXmlHelper.KEY_FAVORITE
import org.gnucash.android.export.xml.GncXmlHelper.KEY_NOTES
import org.gnucash.android.export.xml.GncXmlHelper.KEY_SCHED_XACTION
import org.gnucash.android.export.xml.GncXmlHelper.KEY_SPLIT_ACCOUNT_SLOT
import org.gnucash.android.gnc.GncProgressListener
import org.gnucash.android.importer.Importer
import org.gnucash.android.model.Account
import org.gnucash.android.model.AccountType
import org.gnucash.android.model.BaseModel
import org.gnucash.android.model.Book
import org.gnucash.android.model.Budget
import org.gnucash.android.model.BudgetAmount
import org.gnucash.android.model.Commodity
import org.gnucash.android.model.Money
import org.gnucash.android.model.Numeric
import org.gnucash.android.model.PeriodType
import org.gnucash.android.model.Price
import org.gnucash.android.model.Recurrence
import org.gnucash.android.model.ScheduledAction
import org.gnucash.android.model.Slot
import org.gnucash.android.model.Split
import org.gnucash.android.model.Transaction
import org.gnucash.android.model.TransactionType
import org.gnucash.android.model.WeekendAdjust
import org.gnucash.android.util.FileUtils
import org.gnucash.android.util.TimestampHelper
import org.joda.time.format.DateTimeFormat
import org.joda.time.format.DateTimeFormatter
import timber.log.Timber
import java.io.EOFException
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.math.BigDecimal
import java.sql.Timestamp
import java.text.ParseException
import java.util.UUID

class SqliteImporter(context: Context, inputStream: InputStream, listener: GncProgressListener?) :
    Importer(context, inputStream, listener) {
    private lateinit var holder: DatabaseHolder
    private lateinit var accountsDbAdapter: AccountsDbAdapter
    private lateinit var transactionsDbAdapter: TransactionsDbAdapter
    private lateinit var splitsDbAdapter: SplitsDbAdapter
    private lateinit var scheduledActionsDbAdapter: ScheduledActionDbAdapter
    private lateinit var recurrenceDbAdapter: RecurrenceDbAdapter
    private lateinit var commoditiesDbAdapter: CommoditiesDbAdapter
    private lateinit var pricesDbAdapter: PricesDbAdapter
    private lateinit var budgetsDbAdapter: BudgetsDbAdapter

    private val dateTimeFormatter: DateTimeFormatter =
        DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss").withZoneUTC()
    private val dateCompactFormatter: DateTimeFormatter =
        DateTimeFormat.forPattern("yyyyMMdd").withZoneUTC()

    /**
     * Map of the template accounts to the template transactions UIDs
     */
    private val templateAccountToTransaction = mutableMapOf<String, String>()

    override fun parse(inputStream: InputStream): List<Book> {
        // 1. copy the stream to a cache file
        val file = copyToFile(inputStream)
        // 2. copy from the Desktop db to the Pocket db
        return pipeBooks(file)
    }

    @Throws(IOException::class)
    private fun copyToFile(inputStream: InputStream): File {
        val available = inputStream.available()
        val cacheDir = context.cacheDir
        cacheDir.mkdirs()
        val name = UUID.randomUUID().toString() + ".db"
        val file = File(cacheDir, name)
        val outputStream = FileOutputStream(file)
        val size = FileUtils.copy(inputStream, outputStream)
        if (size == 0L) throw EOFException()
        if (size < available) throw EOFException()
        return file
    }

    private fun pipeBooks(file: File): List<Book> {
        val db = SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY)
        return pipeBooks(db)
    }

    private fun pipeBooks(db: SQLiteDatabase): List<Book> {
        val books = mutableListOf<Book>()
        val cursor = db.query("books", null, null, null, null, null, null)
        cursor.moveToFirst()
        listener?.onBookCount(cursor.count.toLong())

        cursor.forEach { cursor ->
            cancellationSignal.throwIfCanceled()
            val book = pipeBook(cursor)

            pipeSlots(db, book).forEach { slot ->
                // TODO apply slot to book
            }

            listener?.onBook(book)
            val displayName = book.displayName
            booksDbAdapter.replace(book)
            book.displayName = displayName
            books.add(book)

            initDb(book).use {
                pipeCommodities(db)
                pipePrices(db)
                val accounts = pipeAccounts(db, book).associateBy { it.uid }
                pipeTransactionsWithSplits(db, accounts)
                pipeScheduledTransactions(db, accounts)
                pipeBudgets(db, accounts)
            }
        }

        return books
    }

    private fun pipeBook(cursor: Cursor): Book {
        val guid = cursor.getString("guid")!!
        val rootAccountUID = cursor.getString("root_account_guid")!!
        val rootTemplateUID = cursor.getString("root_template_guid")!!

        val book = Book()
        book.setUID(guid)
        book.rootAccountUID = rootAccountUID
        book.rootTemplateUID = rootTemplateUID

        return book
    }

    private fun initDb(book: Book): DatabaseHolder {
        val dbHelper = DatabaseHelper(context, book.uid)
        val holder = dbHelper.readableHolder
        this.holder = holder
        commoditiesDbAdapter = CommoditiesDbAdapter(holder)
        pricesDbAdapter = PricesDbAdapter(commoditiesDbAdapter)
        splitsDbAdapter = SplitsDbAdapter(commoditiesDbAdapter)
        transactionsDbAdapter = TransactionsDbAdapter(splitsDbAdapter)
        accountsDbAdapter = AccountsDbAdapter(transactionsDbAdapter, pricesDbAdapter)
        recurrenceDbAdapter = RecurrenceDbAdapter(holder)
        scheduledActionsDbAdapter =
            ScheduledActionDbAdapter(recurrenceDbAdapter, transactionsDbAdapter)
        budgetsDbAdapter = BudgetsDbAdapter(recurrenceDbAdapter)

        budgetsDbAdapter.deleteAllRecords()
        scheduledActionsDbAdapter.deleteAllRecords()
        recurrenceDbAdapter.deleteAllRecords()
        pricesDbAdapter.deleteAllRecords()
        splitsDbAdapter.deleteAllRecords()
        transactionsDbAdapter.deleteAllRecords()
        accountsDbAdapter.deleteAllRecords()

        return holder
    }

    private fun pipeAccounts(db: SQLiteDatabase, book: Book): List<Account> {
        cancellationSignal.throwIfCanceled()
        val cursor = db.query("accounts", null, null, null, null, null, null)
        cursor.moveToFirst()
        listener?.onAccountCount(cursor.count.toLong())

        val accounts = mutableListOf<Account>()

        pipeAccounts(db, book.rootAccountUID, false, accounts)
        book.rootTemplateUID?.let {
            pipeAccounts(db, it, true, accounts)
        }

        return accounts
    }

    private fun pipeAccounts(
        db: SQLiteDatabase,
        accountUID: String,
        template: Boolean,
        accounts: MutableList<Account>
    ) {
        val where = "guid = ?"
        val whereArgs = arrayOf(accountUID)
        val cursor = db.query("accounts", null, where, whereArgs, null, null, null)

        cursor.forEach { cursor ->
            cancellationSignal.throwIfCanceled()
            val account = pipeAccount(db, cursor, template)
            listener?.onAccount(account)
            accountsDbAdapter.insert(account)
            accounts.add(account)

            val descendants = pipeChildrenAccounts(db, accountUID, template)
            accounts.addAll(descendants)
        }
    }

    private fun pipeChildrenAccounts(
        db: SQLiteDatabase,
        parentUID: String,
        template: Boolean
    ): List<Account> {
        val result = mutableListOf<Account>()
        val children = mutableListOf<Account>()
        val where = "parent_guid = ?"
        val whereArgs = arrayOf(parentUID)
        val cursor = db.query("accounts", null, where, whereArgs, null, null, null)

        cursor.forEach { cursor ->
            val account = pipeAccount(db, cursor, template)
            children.add(account)
        }
        result.addAll(children)
        children.forEach { account ->
            cancellationSignal.throwIfCanceled()
            listener?.onAccount(account)
            accountsDbAdapter.insert(account)

            val descendants = pipeChildrenAccounts(db, account.uid, template)
            result.addAll(descendants)
        }

        return result
    }

    private fun pipeAccount(db: SQLiteDatabase, cursor: Cursor, template: Boolean): Account {
        val guid = cursor.getString("guid")!!
        val name = cursor.getString("name")!!
        val type = cursor.getString("account_type")!!
        val commodityGuid = cursor.getString("commodity_guid")
        val parentGuid = cursor.getString("parent_guid")
        val code = cursor.getString("code")
        val description = cursor.getString("description")
        val hidden = cursor.getBoolean("hidden")
        val placeholder = cursor.getBoolean("placeholder")
        val commodity = commodityGuid?.let { commoditiesDbAdapter.getRecord(commodityGuid) }
            ?: commoditiesDbAdapter.defaultCommodity

        val account = Account(name, commodity)
        account.setUID(guid)
        account.accountType = AccountType.valueOf(type)
        account.parentUID = parentGuid
        account.code = code
        account.description = description
        account.isHidden = hidden
        account.isPlaceholder = placeholder
        account.isTemplate = template

        pipeSlots(db, account).forEach { slot ->
            when (slot.key) {
                KEY_COLOR -> {
                    val color = slot.asString
                    try {
                        account.setColor(color)
                    } catch (e: IllegalArgumentException) {
                        //sometimes the color entry in the account file is "Not set" instead of just blank. So catch!
                        Timber.e(e, "Invalid color code \"%s\" for account %s", color, account)
                    }
                }

                KEY_DEFAULT_TRANSFER_ACCOUNT ->
                    account.defaultTransferAccountUID = slot.asString

                KEY_FAVORITE -> account.isFavorite = slot.asString.toBoolean()

                KEY_NOTES -> account.note = slot.asString
            }
        }

        return account
    }

    private fun pipeBudgets(db: SQLiteDatabase, accounts: Map<String, Account>) {
        cancellationSignal.throwIfCanceled()
        val accountUIDs = accounts.keys
        val cursor = db.query("budgets", null, null, null, null, null, null)
        cursor.moveToFirst()
        listener?.onBudgetCount(cursor.count.toLong())
        cursor.forEach { cursor ->
            cancellationSignal.throwIfCanceled()
            val budget = pipeBudget(db, cursor, accounts)
            // Does the budget belong to the book?
            val intersection = budget.accounts.intersect(accountUIDs)
            if (intersection.isNotEmpty()) {
                listener?.onBudget(budget)
                budgetsDbAdapter.insert(budget)
            }
        }
    }

    private fun pipeBudget(
        db: SQLiteDatabase,
        cursor: Cursor,
        accounts: Map<String, Account>
    ): Budget {
        val guid = cursor.getString("guid")!!
        val name = cursor.getString("name")!!
        val description = cursor.getString("description")
        val periods = cursor.getInt("num_periods")

        val budget = Budget()
        budget.setUID(guid)
        budget.name = name
        budget.description = description
        budget.numberOfPeriods = periods

        val recurrences = pipeRecurrences(db, budget)
        budget.recurrence = recurrences[recurrences.lastIndex]
        pipeBudgetAmounts(db, budget)

        pipeSlots(db, budget).forEach { slot ->
            when (slot.key) {
                KEY_NOTES -> {
                    for (slotAccount in slot.asFrame) {
                        val accountUID = slotAccount.key
                        val account = accounts[accountUID] ?: continue
                        for (slotNote in slotAccount.asFrame) {
                            try {
                                val periodNum = slotNote.key.toInt()
                                var budgetAmount = budget.getBudgetAmount(accountUID, periodNum)
                                if (budgetAmount == null) {
                                    budgetAmount =
                                        budget.addAmount(account, periodNum, BigDecimal.ZERO)
                                }
                                budgetAmount.notes = slotNote.asString
                            } catch (e: NumberFormatException) {
                                Timber.e(e, "Invalid budget period: %s", slotNote.key)
                            }
                        }
                    }
                }
            }
        }

        return budget
    }

    private fun pipeBudgetAmounts(db: SQLiteDatabase, budget: Budget) {
        val where = "budget_guid = ?"
        val whereArgs = arrayOf(budget.uid)
        val cursor = db.query("budget_amounts", null, where, whereArgs, null, null, null)
        cursor.forEach { cursor ->
            cancellationSignal.throwIfCanceled()
            val budgetAmount = pipeBudgetAmount(cursor)
            budget.addAmount(budgetAmount)
        }
    }

    private fun pipeBudgetAmount(cursor: Cursor): BudgetAmount {
        val guid = cursor.getString("id")!!
        val accountGuid = cursor.getString("account_guid")!!
        val periodNum = cursor.getInt("period_num")
        val amountNum = cursor.getLong("amount_num")
        val amountDenom = cursor.getLong("amount_denom")
        val commodity = accountsDbAdapter.getCommodity(accountGuid)

        val budgetAmount = BudgetAmount()
        budgetAmount.setUID(guid)
        budgetAmount.accountUID = accountGuid
        budgetAmount.periodIndex = periodNum
        budgetAmount.amount = Money(amountNum, amountDenom, commodity)

        return budgetAmount
    }

    private fun pipeCommodities(db: SQLiteDatabase) {
        cancellationSignal.throwIfCanceled()
        val cursor = db.query("commodities", null, null, null, null, null, null)
        cursor.moveToFirst()
        listener?.onCommodityCount(cursor.count.toLong())
        cursor.forEach { cursor ->
            cancellationSignal.throwIfCanceled()
            val commodity = pipeCommodity(db, cursor)
            listener?.onCommodity(commodity)
            commoditiesDbAdapter.insert(commodity)
        }
    }

    private fun pipeCommodity(db: SQLiteDatabase, cursor: Cursor): Commodity {
        val guid = cursor.getString("guid")!!
        val namespace = cursor.getString("namespace")!!
        val mnemonic = cursor.getString("mnemonic")!!
        val fullname = cursor.getString("fullname")
        val cusip = cursor.getString("cusip")
        val fraction = cursor.getInt("fraction")
        val quoteSource = cursor.getString("quote_source")
        val quoteTz = cursor.getString("quote_tz")

        val commodityOld = commoditiesDbAdapter.getCommodity(mnemonic, namespace)
        if (commodityOld != null) {
            commoditiesDbAdapter.deleteRecord(commodityOld)
        }

        val commodity = Commodity(
            fullname = fullname,
            mnemonic = mnemonic,
            namespace = namespace
        )
        commodity.setUID(guid)
        commodity.cusip = cusip
        commodity.smallestFraction = fraction
        commodity.quoteSource = quoteSource
        commodity.setQuoteTimeZone(quoteTz)

        return commodity
    }

    private fun pipePrices(db: SQLiteDatabase) {
        cancellationSignal.throwIfCanceled()
        val cursor = db.query("prices", null, null, null, null, null, null)
        cursor.moveToFirst()
        listener?.onPriceCount(cursor.count.toLong())
        cursor.forEach { cursor ->
            cancellationSignal.throwIfCanceled()
            val price = pipePrice(cursor)
            listener?.onPrice(price)
            pricesDbAdapter.insert(price)
        }
    }

    private fun pipePrice(cursor: Cursor): Price {
        val guid = cursor.getString("guid")!!
        val commodityGuid = cursor.getString("commodity_guid")!!
        val currencyGuid = cursor.getString("currency_guid")!!
        val date = cursor.getString("date")!!
        val source = cursor.getString("source")
        val type = cursor.getString("type")
        val valueNum = cursor.getLong("value_num")
        val valueDenom = cursor.getLong("value_denom")

        val price = Price()
        price.setUID(guid)
        price.commodity = commoditiesDbAdapter.getRecord(commodityGuid)
        price.currency = commoditiesDbAdapter.getRecord(currencyGuid)
        price.date = parseDateTime(date)
        price.source = source
        price.type = Price.Type.of(type)
        price.valueNum = valueNum
        price.valueDenom = valueDenom

        return price
    }

    private fun pipeRecurrences(db: SQLiteDatabase, owner: BaseModel): List<Recurrence> {
        val result = mutableListOf<Recurrence>()
        val where = "obj_guid = ?"
        val whereArgs = arrayOf(owner.uid)
        val cursor = db.query("recurrences", null, where, whereArgs, null, null, null)
        cursor.forEach { cursor ->
            cancellationSignal.throwIfCanceled()
            val recurrence = pipeRecurrence(cursor)
            result.add(recurrence)
        }
        return result
    }

    private fun pipeRecurrence(cursor: Cursor): Recurrence {
        val mult = cursor.getInt("recurrence_mult")
        val periodType = cursor.getString("recurrence_period_type")!!
        val periodStart = cursor.getString("recurrence_period_start")!!
        val weekendAdjust = cursor.getString("recurrence_weekend_adjust")!!

        val recurrence = Recurrence(PeriodType.of(periodType))
        recurrence.multiplier = mult
        recurrence.periodStart = parseCompactDate(periodStart)
        recurrence.weekendAdjust = WeekendAdjust.of(weekendAdjust)

        return recurrence
    }

    private fun pipeScheduledTransactions(db: SQLiteDatabase, accounts: Map<String, Account>) {
        cancellationSignal.throwIfCanceled()
        val cursor = db.query("schedxactions", null, null, null, null, null, null)
        cursor.moveToFirst()
        listener?.onScheduleCount(cursor.count.toLong())
        cursor.forEach { cursor ->
            cancellationSignal.throwIfCanceled()
            val scheduledAction = pipeScheduledTransaction(db, cursor)
            // Does the transaction belong to the book?
            if (accounts.containsKey(scheduledAction.templateAccountUID)) {
                listener?.onSchedule(scheduledAction)
                scheduledActionsDbAdapter.insert(scheduledAction)
            }
        }
    }

    private fun pipeScheduledTransaction(db: SQLiteDatabase, cursor: Cursor): ScheduledAction {
        val guid = cursor.getString("guid")!!
        val name = cursor.getString("name")!!
        val enabled = cursor.getBoolean("enabled")
        val startDate = cursor.getString("start_date")
        val endDate = cursor.getString("end_date")
        val lastOccur = cursor.getString("last_occur")
        val numOccur = cursor.getInt("num_occur")
        val remOccur = cursor.getInt("rem_occur")
        val autoCreate = cursor.getBoolean("auto_create")
        val autoNotify = cursor.getBoolean("auto_notify")
        val advCreation = cursor.getInt("adv_creation")
        val advNotify = cursor.getInt("adv_notify")
        val instanceCount = cursor.getInt("instance_count")
        val templateActGuid = cursor.getString("template_act_guid")

        val scheduledAction = ScheduledAction()
        scheduledAction.setUID(guid)
        scheduledAction.name = name
        scheduledAction.isEnabled = enabled
        scheduledAction.startDate = parseCompactDate(startDate)
        scheduledAction.endDate = parseCompactDate(endDate)
        scheduledAction.lastRunDate = parseCompactDate(lastOccur)
        scheduledAction.totalPlannedExecutionCount = numOccur
        scheduledAction.isAutoCreate = autoCreate
        scheduledAction.isAutoCreateNotify = autoNotify
        scheduledAction.advanceCreateDays = advCreation
        scheduledAction.advanceRemindDays = advNotify
        scheduledAction.instanceCount = instanceCount
        scheduledAction.setTemplateAccountUID(templateActGuid)
        scheduledAction.actionUID = templateAccountToTransaction[templateActGuid]!!

        val recurrences = pipeRecurrences(db, scheduledAction)
        scheduledAction.setRecurrence(recurrences[recurrences.lastIndex])

        return scheduledAction
    }

    private fun pipeSlots(db: SQLiteDatabase, owner: BaseModel): List<Slot> {
        return pipeSlots(db, owner.uid)
    }

    private fun pipeSlots(
        db: SQLiteDatabase,
        ownerUID: String,
        ownerName: String? = null
    ): List<Slot> {
        val result = mutableListOf<Slot>()
        val where = "obj_guid = ?"
        val whereArgs = arrayOf(ownerUID)
        val cursor = db.query("slots", null, where, whereArgs, null, null, null)
        cursor.forEach { cursor ->
            val slot = pipeSlot(db, cursor, ownerName)
            result.add(slot)
        }
        return result
    }

    private fun pipeSlot(db: SQLiteDatabase, cursor: Cursor, ownerName: String? = null): Slot {
        val name = cursor.getString("name")!!
        val slotType = cursor.getInt("slot_type")

        val key = if (ownerName != null && name.startsWith("$ownerName/")) {
            name.substring(ownerName.length + 1)
        } else {
            name
        }
        val type = Slot.Type.of(slotType)
        val slot = Slot(key, type)
        when (type) {
            Slot.Type.INVALID -> Unit

            Slot.Type.INT64 -> slot.value = cursor.getLong("int64_val")

            Slot.Type.DOUBLE -> slot.value = cursor.getDouble("double_val")

            Slot.Type.NUMERIC -> {
                val numericNum = cursor.getLong("numeric_val_num")
                val numericDenom = cursor.getLong("numeric_val_denom")
                slot.value = Numeric(numericNum, numericDenom)
            }

            Slot.Type.STRING -> slot.value = cursor.getString("string_val")!!

            Slot.Type.GUID -> slot.value = cursor.getString("guid_val")!!

            Slot.Type.TIME64 -> slot.value = parseDateTime(cursor.getString("timespec_val")!!)

            Slot.Type.PLACEHOLDER_DONT_USE -> Unit

            Slot.Type.GLIST -> TODO()

            Slot.Type.FRAME -> {
                val guid = cursor.getString("guid_val")!!
                slot.value = pipeSlots(db, guid, name)
            }

            Slot.Type.GDATE -> slot.value = parseCompactDate(cursor.getString("gdate_val")!!)
        }

        return slot
    }

    private fun pipeSplit(
        db: SQLiteDatabase,
        cursor: Cursor,
        accounts: Map<String, Account>,
        transaction: Transaction
    ): Split {
        val guid = cursor.getString("guid")!!
        val accountGuid = cursor.getString("account_guid")!!
        val memo = cursor.getString("memo")!!
        //val action = cursor.getString("action")!!
        val reconcileState = cursor.getString("reconcile_state")!!
        val reconcileDate = cursor.getString("reconcile_date")
        val valueNum = cursor.getLong("value_num")
        val valueDenom = cursor.getLong("value_denom")
        val quantityNum = cursor.getLong("quantity_num")
        val quantityDenom = cursor.getLong("quantity_denom")
        //val lotGuid = cursor.getString("lot_guid")

        val account = accounts[accountGuid] ?: accountsDbAdapter.getRecord(accountGuid)
        val value = Money(valueNum, valueDenom, transaction.commodity)
        val quantity = Money(quantityNum, quantityDenom, account.commodity)
        var isTemplate = account.isTemplate || transaction.isTemplate

        val split = Split(value, quantity, account)
        split.setUID(guid)
        split.memo = memo
        split.reconcileState = reconcileState[0]
        if (!reconcileDate.isNullOrEmpty()) {
            split.reconcileDate = TimestampHelper.getTimestampFromUtcString(reconcileDate).getTime()
        }

        pipeSlots(db, split).forEach { slot ->
            when (slot.key) {
                KEY_SCHED_XACTION -> {
                    isTemplate = true

                    for (s in slot.asFrame) {
                        when (s.key) {
                            KEY_SPLIT_ACCOUNT_SLOT -> split.scheduledActionAccountUID = s.asGUID

                            KEY_CREDIT_FORMULA -> handleSlotTemplateFormula(
                                account,
                                split,
                                s.asString,
                                TransactionType.CREDIT
                            )

                            KEY_CREDIT_NUMERIC -> handleSlotTemplateNumeric(
                                account,
                                split,
                                s.asNumeric,
                                TransactionType.CREDIT
                            )

                            KEY_DEBIT_FORMULA -> handleSlotTemplateFormula(
                                account,
                                split,
                                s.asString,
                                TransactionType.DEBIT
                            )

                            KEY_DEBIT_NUMERIC -> handleSlotTemplateNumeric(
                                account,
                                split,
                                s.asNumeric,
                                TransactionType.DEBIT
                            )
                        }
                    }
                }

                else -> {
                    Timber.v("pipeSplits $slot")
                }
            }
        }

        if (isTemplate) {
            transaction.isTemplate = true
            templateAccountToTransaction[account.uid] = transaction.uid
        }
        transaction.addSplit(split)

        return split
    }

    private fun pipeSplits(
        db: SQLiteDatabase,
        transaction: Transaction,
        accountsByUID: Map<String, Account>
    ): List<Split> {
        val where = "tx_guid = ?"
        val whereArgs = arrayOf(transaction.uid)
        val cursor = db.query("splits", null, where, whereArgs, null, null, null)
        cursor.forEach { cursor ->
            cancellationSignal.throwIfCanceled()
            pipeSplit(db, cursor, accountsByUID, transaction)
        }
        return transaction.splits
    }

    private fun pipeTransactionsWithSplits(db: SQLiteDatabase, accounts: Map<String, Account>) {
        cancellationSignal.throwIfCanceled()
        val cursor = db.query("transactions", null, null, null, null, null, null)
        cursor.moveToFirst()
        listener?.onTransactionCount(cursor.count.toLong())

        cursor.forEach { cursor ->
            cancellationSignal.throwIfCanceled()
            val transaction = pipeTransaction(db, cursor)
            val splits = pipeSplits(db, transaction, accounts)
            // Does the transaction belong to the book?
            if (splits.any { accounts.containsKey(it.accountUID) }) {
                listener?.onTransaction(transaction)
                transactionsDbAdapter.insert(transaction)
            }
        }
    }

    private fun pipeTransaction(db: SQLiteDatabase, cursor: Cursor): Transaction {
        val guid = cursor.getString("guid")!!
        val commodityGuid = cursor.getString("currency_guid")!!
        val num = cursor.getString("num")
        val description = cursor.getString("description").orEmpty()
        val postDate = cursor.getString("post_date")
        val enterDate = cursor.getString("enter_date")
        val commodity = commoditiesDbAdapter.getRecord(commodityGuid)

        val transaction = Transaction(description)
        transaction.setUID(guid)
        transaction.number = num.orEmpty()
        transaction.commodity = commodity
        postDate?.let { transaction.time = parseDateTime(it) }
        enterDate?.let {
            transaction.createdTimestamp = Timestamp(parseDateTime(it))
        }
        transaction.isTemplate = commodity.isTemplate
        transaction.isExported = true

        pipeSlots(db, transaction).forEach { slot ->
            when (slot.key) {
                KEY_NOTES -> transaction.notes = slot.asString
            }
        }

        return transaction
    }

    private fun parseDateTime(date: String): Long {
        return dateTimeFormatter.parseMillis(date)
    }

    private fun parseCompactDate(date: String?): Long {
        if (date.isNullOrEmpty()) return 0L
        return dateCompactFormatter.parseMillis(date)
    }

    /**
     * Handles the case when we reach the end of the template formula slot
     *
     * @param value Parsed characters containing split amount
     */
    private fun handleSlotTemplateFormula(
        account: Account,
        split: Split,
        value: String,
        splitType: TransactionType
    ) {
        if (value.isEmpty()) return
        try {
            // HACK: Check for bug #562. If a value has already been set, ignore the one just read
            if (split.value.isAmountZero) {
                split.value = Money(value, account.commodity)
                split.type = splitType
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
    private fun handleSlotTemplateNumeric(
        account: Account,
        split: Split,
        value: Numeric,
        splitType: TransactionType
    ) {
        try {
            // HACK: Check for bug #562. If a value has already been set, ignore the one just read
            if (split.value.isAmountZero) {
                split.value = Money(value, account.commodity)
                split.type = splitType
            }
        } catch (e: NumberFormatException) {
            Timber.e(e, "Error parsing template split numeric [%s]", value)
        } catch (e: ParseException) {
            Timber.e(e, "Error parsing template split numeric [%s]", value)
        }
    }
}