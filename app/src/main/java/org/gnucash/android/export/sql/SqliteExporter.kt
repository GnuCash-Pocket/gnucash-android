package org.gnucash.android.export.sql

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.SQLException
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteStatement
import androidx.core.database.sqlite.transaction
import org.gnucash.android.BuildConfig
import org.gnucash.android.db.DatabaseSchema.AccountEntry
import org.gnucash.android.db.DatabaseSchema.SplitEntry
import org.gnucash.android.db.DatabaseSchema.TransactionEntry
import org.gnucash.android.db.adapter.SplitsDbAdapter.Companion.INDEX_COLUMN_ACCOUNT_UID
import org.gnucash.android.db.adapter.SplitsDbAdapter.Companion.INDEX_COLUMN_MEMO
import org.gnucash.android.db.adapter.SplitsDbAdapter.Companion.INDEX_COLUMN_QUANTITY_DENOM
import org.gnucash.android.db.adapter.SplitsDbAdapter.Companion.INDEX_COLUMN_QUANTITY_NUM
import org.gnucash.android.db.adapter.SplitsDbAdapter.Companion.INDEX_COLUMN_RECONCILE_DATE
import org.gnucash.android.db.adapter.SplitsDbAdapter.Companion.INDEX_COLUMN_RECONCILE_STATE
import org.gnucash.android.db.adapter.SplitsDbAdapter.Companion.INDEX_COLUMN_SCHEDX_ACTION_ACCOUNT_UID
import org.gnucash.android.db.adapter.SplitsDbAdapter.Companion.INDEX_COLUMN_TRANSACTION_UID
import org.gnucash.android.db.adapter.SplitsDbAdapter.Companion.INDEX_COLUMN_TYPE
import org.gnucash.android.db.adapter.SplitsDbAdapter.Companion.INDEX_COLUMN_VALUE_DENOM
import org.gnucash.android.db.adapter.SplitsDbAdapter.Companion.INDEX_COLUMN_VALUE_NUM
import org.gnucash.android.db.adapter.TransactionsDbAdapter
import org.gnucash.android.db.bindBoolean
import org.gnucash.android.db.bindInt
import org.gnucash.android.db.forEach
import org.gnucash.android.db.getTimestamp
import org.gnucash.android.db.insert
import org.gnucash.android.export.ExportException
import org.gnucash.android.export.ExportParams
import org.gnucash.android.export.Exporter
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
import org.gnucash.android.export.xml.GncXmlHelper.formatFormula
import org.gnucash.android.gnc.GncProgressListener
import org.gnucash.android.math.toBigDecimal
import org.gnucash.android.model.Account
import org.gnucash.android.model.BaseModel
import org.gnucash.android.model.Book
import org.gnucash.android.model.Budget
import org.gnucash.android.model.BudgetAmount
import org.gnucash.android.model.Commodity
import org.gnucash.android.model.Price
import org.gnucash.android.model.Recurrence
import org.gnucash.android.model.ScheduledAction
import org.gnucash.android.model.Slot
import org.gnucash.android.model.TransactionType
import org.gnucash.android.util.formatRGB
import org.gnucash.android.util.set
import org.joda.time.format.DateTimeFormat
import org.joda.time.format.DateTimeFormatter
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.io.Writer
import kotlin.math.max

/**
 * Export to SQLite3 database.
 * https://wiki.gnucash.org/wiki/SQL
 */
class SqliteExporter(
    context: Context,
    params: ExportParams,
    bookUID: String,
    listener: GncProgressListener? = null
) : Exporter(context, params, bookUID, listener) {

    private val dateTimeFormatter: DateTimeFormatter =
        DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss").withZoneUTC()
    private val dateCompactFormatter: DateTimeFormatter =
        DateTimeFormat.forPattern("yyyyMMdd").withZoneUTC()

    private lateinit var statementAccount: SQLiteStatement
    private lateinit var statementBudget: SQLiteStatement
    private lateinit var statementBudgetAmount: SQLiteStatement
    private lateinit var statementCommodity: SQLiteStatement
    private lateinit var statementPrice: SQLiteStatement
    private lateinit var statementRecurrence: SQLiteStatement
    private lateinit var statementScheduledTransaction: SQLiteStatement
    private lateinit var statementSlot: SQLiteStatement
    private lateinit var statementSplit: SQLiteStatement
    private lateinit var statementTransaction: SQLiteStatement

    override fun writeToFile(exportParams: ExportParams): File {
        val cacheFile = getExportCacheFile(exportParams)
        try {
            createDatabase(cacheFile).use { db ->
                db.enableWriteAheadLogging()
                db.setForeignKeyConstraintsEnabled(false)
                writeExport(db)
                db.setForeignKeyConstraintsEnabled(true)
            }
        } catch (ee: ExportException) {
            throw ee
        } catch (e: Exception) {
            throw ExportException(exportParams, e)
        }
        return cacheFile
    }

    @Throws(IOException::class)
    private fun createDatabase(file: File): SQLiteDatabase {
        // Open or create the database file in the cache directory
        return SQLiteDatabase.openOrCreateDatabase(file, null)!!
    }

    override fun writeExport(writer: Writer, exportParams: ExportParams) = Unit

    @Throws(ExportException::class)
    private fun writeExport(db: SQLiteDatabase) {
        val book = booksDbAdapter.getRecord(bookUID)
        writeBook(db, book)
    }

    @Throws(SQLException::class)
    private fun writeBook(db: SQLiteDatabase, book: Book) {
        Timber.i("writ book")
        cancellationSignal.throwIfCanceled()
        listener?.onBookCount(1)
        writeSchema(db)
        pipeBook(db, book)
        pipeCommodities(db)
        pipePrices(db)
        pipeAccounts(db)
        pipeTransactions(db)
        pipeSplits(db)
        pipeScheduledTransactions()
        pipeBudgets()
    }

    @Throws(SQLException::class)
    private fun writeSchema(db: SQLiteDatabase) {
        val sqlLock = "CREATE TABLE gnclock (" +
                "    hostname VARCHAR(255)," +
                "    pid      INT" +
                ")"
        db.execSQL(sqlLock)

        val sqlVersions = "CREATE TABLE versions (" +
                "    table_name    TEXT(50) PRIMARY KEY NOT NULL," +
                "    table_version INTEGER NOT NULL" +
                ")"
        db.execSQL(sqlVersions)
        addVersion(db, "Gnucash", 5000013)
        addVersion(db, BuildConfig.APPLICATION_ID, BuildConfig.VERSION_CODE)

        createTableAccounts(db)
        createTableBillTerms(db)
        createTableBooks(db)
        createTableBudgetAmounts(db)
        createTableBudgets(db)
        createTableCommodities(db)
        createTableCustomers(db)
        createTableEmployees(db)
        createTableEntries(db)
        createTableInvoices(db)
        createTableJobs(db)
        createTableLots(db)
        createTableOrders(db)
        createTablePrices(db)
        createTableRecurrences(db)
        createTableScheduledTransactions(db)
        createTableSlots(db)
        createTableSplits(db)
        createTableTaxTables(db)
        createTableTaxTableEntries(db)
        createTableTransactions(db)
        createTableVendors(db)
    }

    private fun createTableAccounts(db: SQLiteDatabase) {
        val sql = "CREATE TABLE accounts (" +
                "    guid           TEXT(32) PRIMARY KEY NOT NULL," +
                "    name           TEXT(2048) NOT NULL," +
                "    account_type   TEXT(2048) NOT NULL," +
                "    commodity_guid TEXT(32)," +
                "    commodity_scu  INTEGER NOT NULL," +
                "    non_std_scu    INTEGER NOT NULL," +
                "    parent_guid    TEXT(32)," +
                "    code           TEXT(2048)," +
                "    description    TEXT(2048)," +
                "    hidden         INTEGER," +
                "    placeholder    INTEGER" +
                ")"
        db.execSQL(sql)
        addVersion(db, "accounts", 1)

        statementAccount = db.compileStatement(
            StringBuilder("INSERT INTO accounts (")
                .append("guid")
                .append(',').append("name")
                .append(',').append("account_type")
                .append(',').append("commodity_guid")
                .append(',').append("commodity_scu")
                .append(',').append("non_std_scu")
                .append(',').append("parent_guid")
                .append(',').append("code")
                .append(',').append("description")
                .append(',').append("hidden")
                .append(',').append("placeholder")
                .append(") VALUES (?,?,?,?,?,?,?,?,?,?,?)")
                .toString()
        )
    }

    private fun createTableBillTerms(db: SQLiteDatabase) {
        val sql = "CREATE TABLE billterms (" +
                "    guid           TEXT(32) PRIMARY KEY NOT NULL," +
                "    name           TEXT(2048) NOT NULL," +
                "    description    TEXT(2048) NOT NULL," +
                "    refcount       INTEGER NOT NULL," +
                "    invisible      INTEGER NOT NULL," +
                "    parent         TEXT(32)," +
                "    type           TEXT(2048) NOT NULL," +
                "    duedays        INTEGER," +
                "    discountdays   INTEGER," +
                "    discount_num   BIGINT," +
                "    discount_denom BIGINT," +
                "    cutoff         INTEGER" +
                ")"
        db.execSQL(sql)
        addVersion(db, "billterms", 2)
    }

    private fun createTableBooks(db: SQLiteDatabase) {
        val sql = "CREATE TABLE books (" +
                "    guid               TEXT(32) PRIMARY KEY NOT NULL," +
                "    root_account_guid  TEXT(32) NOT NULL," +
                "    root_template_guid TEXT(32) NOT NULL" +
                ")"
        db.execSQL(sql)
        addVersion(db, "books", 1)
    }

    private fun createTableBudgets(db: SQLiteDatabase) {
        val sql = "CREATE TABLE budgets (" +
                "    guid        TEXT(32) PRIMARY KEY NOT NULL," +
                "    name        TEXT(2048) NOT NULL," +
                "    description TEXT(2048)," +
                "    num_periods INTEGER NOT NULL" +
                ")"
        db.execSQL(sql)
        addVersion(db, "budgets", 1)

        statementBudget = db.compileStatement(
            StringBuilder("INSERT INTO budgets (")
                .append("guid")
                .append(',').append("name")
                .append(',').append("description")
                .append(',').append("num_periods")
                .append(") VALUES (?,?,?,?)")
                .toString()
        )
    }

    private fun createTableBudgetAmounts(db: SQLiteDatabase) {
        val sql = "CREATE TABLE budget_amounts (" +
                "    id           INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL," +
                "    budget_guid  TEXT(32) NOT NULL," +
                "    account_guid TEXT(32) NOT NULL," +
                "    period_num   INTEGER NOT NULL," +
                "    amount_num   BIGINT NOT NULL," +
                "    amount_denom BIGINT NOT NULL" +
                ")"
        db.execSQL(sql)
        addVersion(db, "budget_amounts", 1)

        statementBudgetAmount = db.compileStatement(
            StringBuilder("INSERT INTO budget_amounts (")
                .append("budget_guid")
                .append(',').append("account_guid")
                .append(',').append("period_num")
                .append(',').append("amount_num")
                .append(',').append("amount_denom")
                .append(") VALUES (?,?,?,?,?)")
                .toString()
        )
    }

    private fun createTableCommodities(db: SQLiteDatabase) {
        val sql = "CREATE TABLE commodities (" +
                "    guid         TEXT(32) PRIMARY KEY NOT NULL," +
                "    namespace    TEXT(2048) NOT NULL," +
                "    mnemonic     TEXT(2048) NOT NULL," +
                "    fullname     TEXT(2048)," +
                "    cusip        TEXT(2048)," +
                "    fraction     INTEGER NOT NULL," +
                "    quote_flag   INTEGER NOT NULL," +
                "    quote_source TEXT(2048)," +
                "    quote_tz     TEXT(2048)" +
                ")"
        db.execSQL(sql)
        addVersion(db, "commodities", 1)

        statementCommodity = db.compileStatement(
            StringBuilder("INSERT INTO commodities (")
                .append("guid")
                .append(',').append("namespace")
                .append(',').append("mnemonic")
                .append(',').append("fullname")
                .append(',').append("cusip")
                .append(',').append("fraction")
                .append(',').append("quote_flag")
                .append(',').append("quote_source")
                .append(',').append("quote_tz")
                .append(") VALUES (?,?,?,?,?,?,?,?,?)")
                .toString()
        )
    }

    private fun createTableCustomers(db: SQLiteDatabase) {
        val sql = "CREATE TABLE customers (" +
                "    guid           TEXT(32) PRIMARY KEY NOT NULL," +
                "    name           TEXT(2048) NOT NULL," +
                "    id             TEXT(2048) NOT NULL," +
                "    notes          TEXT(2048) NOT NULL," +
                "    active         INTEGER NOT NULL," +
                "    discount_num   BIGINT NOT NULL," +
                "    discount_denom BIGINT NOT NULL," +
                "    credit_num     BIGINT NOT NULL," +
                "    credit_denom   BIGINT NOT NULL," +
                "    currency       TEXT(32) NOT NULL," +
                "    tax_override   INTEGER NOT NULL," +
                "    addr_name      TEXT(1024)," +
                "    addr_addr1     TEXT(1024)," +
                "    addr_addr2     TEXT(1024)," +
                "    addr_addr3     TEXT(1024)," +
                "    addr_addr4     TEXT(1024)," +
                "    addr_phone     TEXT(128)," +
                "    addr_fax       TEXT(128)," +
                "    addr_email     TEXT(256)," +
                "    shipaddr_name  TEXT(1024)," +
                "    shipaddr_addr1 TEXT(1024)," +
                "    shipaddr_addr2 TEXT(1024)," +
                "    shipaddr_addr3 TEXT(1024)," +
                "    shipaddr_addr4 TEXT(1024)," +
                "    shipaddr_phone TEXT(128)," +
                "    shipaddr_fax   TEXT(128)," +
                "    shipaddr_email TEXT(256)," +
                "    terms          TEXT(32)," +
                "    tax_included   INTEGER," +
                "    taxtable       TEXT(32)" +
                ")"
        db.execSQL(sql)
        addVersion(db, "customers", 2)
    }

    private fun createTableEmployees(db: SQLiteDatabase) {
        val sql = "CREATE TABLE employees (" +
                "    guid          TEXT(32) PRIMARY KEY NOT NULL," +
                "    username      TEXT(2048) NOT NULL," +
                "    id            TEXT(2048) NOT NULL," +
                "    language      TEXT(2048) NOT NULL," +
                "    acl           TEXT(2048) NOT NULL," +
                "    active        INTEGER NOT NULL," +
                "    currency      TEXT(32) NOT NULL," +
                "    ccard_guid    TEXT(32)," +
                "    workday_num   BIGINT NOT NULL," +
                "    workday_denom BIGINT NOT NULL," +
                "    rate_num      BIGINT NOT NULL," +
                "    rate_denom    BIGINT NOT NULL," +
                "    addr_name     TEXT(1024)," +
                "    addr_addr1    TEXT(1024)," +
                "    addr_addr2    TEXT(1024)," +
                "    addr_addr3    TEXT(1024)," +
                "    addr_addr4    TEXT(1024)," +
                "    addr_phone    TEXT(128)," +
                "    addr_fax      TEXT(128)," +
                "    addr_email    TEXT(256)" +
                ")"
        db.execSQL(sql)
        addVersion(db, "employees", 2)
    }

    private fun createTableEntries(db: SQLiteDatabase) {
        val sql = "CREATE TABLE entries (" +
                "    guid             TEXT(32) PRIMARY KEY NOT NULL," +
                "    date             TEXT(19) NOT NULL," +
                "    date_entered     TEXT(19)," +
                "    description      TEXT(2048)," +
                "    action           TEXT(2048)," +
                "    notes            TEXT(2048)," +
                "    quantity_num     BIGINT," +
                "    quantity_denom   BIGINT," +
                "    i_acct           TEXT(32)," +
                "    i_price_num      BIGINT," +
                "    i_price_denom    BIGINT," +
                "    i_discount_num   BIGINT," +
                "    i_discount_denom BIGINT," +
                "    invoice          TEXT(32)," +
                "    i_disc_type      TEXT(2048)," +
                "    i_disc_how       TEXT(2048)," +
                "    i_taxable        INTEGER," +
                "    i_taxincluded    INTEGER," +
                "    i_taxtable       TEXT(32)," +
                "    b_acct           TEXT(32)," +
                "    b_price_num      BIGINT," +
                "    b_price_denom    BIGINT," +
                "    bill             TEXT(32)," +
                "    b_taxable        INTEGER," +
                "    b_taxincluded    INTEGER," +
                "    b_taxtable       TEXT(32)," +
                "    b_paytype        INTEGER," +
                "    billable         INTEGER," +
                "    billto_type      INTEGER," +
                "    billto_guid      TEXT(32)," +
                "    order_guid       TEXT(32)" +
                ")"
        db.execSQL(sql)
        addVersion(db, "entries", 4)
    }

    private fun createTableInvoices(db: SQLiteDatabase) {
        val sql = "CREATE TABLE invoices (" +
                "    guid             TEXT(32) PRIMARY KEY NOT NULL," +
                "    id               TEXT(2048) NOT NULL," +
                "    date_opened      TEXT(19)," +
                "    date_posted      TEXT(19)," +
                "    notes            TEXT(2048) NOT NULL," +
                "    active           INTEGER NOT NULL," +
                "    currency         TEXT(32) NOT NULL," +
                "    owner_type       INTEGER," +
                "    owner_guid       TEXT(32)," +
                "    terms            TEXT(32)," +
                "    billing_id       TEXT(2048)," +
                "    post_txn         TEXT(32)," +
                "    post_lot         TEXT(32)," +
                "    post_acc         TEXT(32)," +
                "    billto_type      INTEGER," +
                "    billto_guid      TEXT(32)," +
                "    charge_amt_num   BIGINT," +
                "    charge_amt_denom BIGINT" +
                ")"
        db.execSQL(sql)
        addVersion(db, "invoices", 4)
    }

    private fun createTableJobs(db: SQLiteDatabase) {
        val sql = "CREATE TABLE jobs (" +
                "    guid       TEXT(32) PRIMARY KEY NOT NULL," +
                "    id         TEXT(2048) NOT NULL," +
                "    name       TEXT(2048) NOT NULL," +
                "    reference  TEXT(2048) NOT NULL," +
                "    active     INTEGER NOT NULL," +
                "    owner_type INTEGER," +
                "    owner_guid TEXT(32)" +
                ")"
        db.execSQL(sql)
        addVersion(db, "jobs", 1)
    }

    private fun createTableLots(db: SQLiteDatabase) {
        val sql = "CREATE TABLE lots (" +
                "    guid         TEXT(32) PRIMARY KEY NOT NULL," +
                "    account_guid TEXT(32)," +
                "    is_closed    INTEGER NOT NULL" +
                ")"
        db.execSQL(sql)
        addVersion(db, "lots", 2)
    }

    private fun createTableOrders(db: SQLiteDatabase) {
        val sql = "CREATE TABLE orders (" +
                "    guid        TEXT(32) PRIMARY KEY NOT NULL," +
                "    id          TEXT(2048) NOT NULL," +
                "    notes       TEXT(2048) NOT NULL," +
                "    reference   TEXT(2048) NOT NULL," +
                "    active      INTEGER NOT NULL," +
                "    date_opened TEXT(19) NOT NULL," +
                "    date_closed TEXT(19) NOT NULL," +
                "    owner_type  INTEGER NOT NULL," +
                "    owner_guid  TEXT(32) NOT NULL" +
                ")"
        db.execSQL(sql)
        addVersion(db, "orders", 1)
    }

    private fun createTablePrices(db: SQLiteDatabase) {
        val sql = "CREATE TABLE prices (" +
                "    guid           TEXT(32) PRIMARY KEY NOT NULL," +
                "    commodity_guid TEXT(32) NOT NULL," +
                "    currency_guid  TEXT(32) NOT NULL," +
                "    date           TEXT(19) NOT NULL," +
                "    source         TEXT(2048)," +
                "    type           TEXT(2048)," +
                "    value_num      BIGINT NOT NULL," +
                "    value_denom    BIGINT NOT NULL" +
                ")"
        db.execSQL(sql)
        addVersion(db, "prices", 3)

        statementPrice = db.compileStatement(
            StringBuilder("INSERT INTO prices (")
                .append("guid")
                .append(',').append("commodity_guid")
                .append(',').append("currency_guid")
                .append(',').append("date")
                .append(',').append("source")
                .append(',').append("type")
                .append(',').append("value_num")
                .append(',').append("value_denom")
                .append(") VALUES (?,?,?,?,?,?,?,?)")
                .toString()
        )
    }

    private fun createTableRecurrences(db: SQLiteDatabase) {
        val sql = "CREATE TABLE recurrences (" +
                "    id                        INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL," +
                "    obj_guid                  TEXT(32) NOT NULL," +
                "    recurrence_mult           INTEGER NOT NULL," +
                "    recurrence_period_type    TEXT(2048) NOT NULL," +
                "    recurrence_period_start   TEXT(8) NOT NULL," +
                "    recurrence_weekend_adjust TEXT(2048) NOT NULL" +
                ")"
        db.execSQL(sql)
        addVersion(db, "recurrences", 2)

        statementRecurrence = db.compileStatement(
            StringBuilder("INSERT INTO recurrences (")
                .append("obj_guid")
                .append(',').append("recurrence_mult")
                .append(',').append("recurrence_period_type")
                .append(',').append("recurrence_period_start")
                .append(',').append("recurrence_weekend_adjust")
                .append(") VALUES (?,?,?,?,?)")
                .toString()
        )
    }

    private fun createTableScheduledTransactions(db: SQLiteDatabase) {
        val sql = "CREATE TABLE schedxactions (" +
                "    guid              TEXT(32) PRIMARY KEY NOT NULL," +
                "    name              TEXT(2048)," +
                "    enabled           INTEGER NOT NULL," +
                "    start_date        TEXT(8)," +
                "    end_date          TEXT(8)," +
                "    last_occur        TEXT(8)," +
                "    num_occur         INTEGER NOT NULL," +
                "    rem_occur         INTEGER NOT NULL," +
                "    auto_create       INTEGER NOT NULL," +
                "    auto_notify       INTEGER NOT NULL," +
                "    adv_creation      INTEGER NOT NULL," +
                "    adv_notify        INTEGER NOT NULL," +
                "    instance_count    INTEGER NOT NULL," +
                "    template_act_guid TEXT(32) NOT NULL" +
                ")"
        db.execSQL(sql)
        addVersion(db, "schedxactions", 1)

        statementScheduledTransaction = db.compileStatement(
            StringBuilder("INSERT INTO schedxactions (")
                .append("guid")
                .append(',').append("name")
                .append(',').append("enabled")
                .append(',').append("start_date")
                .append(',').append("end_date")
                .append(',').append("last_occur")
                .append(',').append("num_occur")
                .append(',').append("rem_occur")
                .append(',').append("auto_create")
                .append(',').append("auto_notify")
                .append(',').append("adv_creation")
                .append(',').append("adv_notify")
                .append(',').append("instance_count")
                .append(',').append("template_act_guid")
                .append(") VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)")
                .toString()
        )
    }

    private fun createTableSlots(db: SQLiteDatabase) {
        val sql = "CREATE TABLE slots (" +
                "    id                INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL," +
                "    obj_guid          TEXT(32) NOT NULL," +
                "    name              TEXT(4096) NOT NULL," +
                "    slot_type         INTEGER NOT NULL," +
                "    int64_val         BIGINT," +
                "    string_val        TEXT(4096)," +
                "    double_val        REAL," +
                "    timespec_val      TEXT(19)," +
                "    guid_val          TEXT(32)," +
                "    numeric_val_num   BIGINT," +
                "    numeric_val_denom BIGINT," +
                "    gdate_val         TEXT(8)" +
                ")"
        db.execSQL(sql)
        addVersion(db, "slots", 4)

        statementSlot = db.compileStatement(
            StringBuilder("INSERT INTO slots (")
                .append("obj_guid")
                .append(',').append("name")
                .append(',').append("slot_type")
                .append(',').append("int64_val")
                .append(',').append("string_val")
                .append(',').append("double_val")
                .append(',').append("timespec_val")
                .append(',').append("guid_val")
                .append(',').append("numeric_val_num")
                .append(',').append("numeric_val_denom")
                .append(',').append("gdate_val")
                .append(") VALUES (?,?,?,?,?,?,?,?,?,?,?)")
                .toString()
        )
    }

    private fun createTableSplits(db: SQLiteDatabase) {
        val sql = "CREATE TABLE splits (" +
                "    guid            TEXT(32) PRIMARY KEY NOT NULL," +
                "    tx_guid         TEXT(32) NOT NULL," +
                "    account_guid    TEXT(32) NOT NULL," +
                "    memo            TEXT(2048) NOT NULL," +
                "    action          TEXT(2048) NOT NULL," +
                "    reconcile_state TEXT(1) NOT NULL," +
                "    reconcile_date  TEXT(19)," +
                "    value_num       BIGINT NOT NULL," +
                "    value_denom     BIGINT NOT NULL," +
                "    quantity_num    BIGINT NOT NULL," +
                "    quantity_denom  BIGINT NOT NULL," +
                "    lot_guid        TEXT(32)" +
                ")"
        db.execSQL(sql)
        addVersion(db, "splits", 5)

        statementSplit = db.compileStatement(
            StringBuilder("INSERT INTO splits (")
                .append("guid")
                .append(',').append("tx_guid")
                .append(',').append("account_guid")
                .append(',').append("memo")
                .append(',').append("action")
                .append(',').append("reconcile_state")
                .append(',').append("reconcile_date")
                .append(',').append("value_num")
                .append(',').append("value_denom")
                .append(',').append("quantity_num")
                .append(',').append("quantity_denom")
                .append(',').append("lot_guid")
                .append(") VALUES (?,?,?,?,?,?,?,?,?,?,?,?)")
                .toString()
        )
    }

    private fun createTableTaxTables(db: SQLiteDatabase) {
        val sql = "CREATE TABLE taxtables (" +
                "    guid      TEXT(32) PRIMARY KEY NOT NULL," +
                "    name      TEXT(50) NOT NULL," +
                "    refcount  BIGINT NOT NULL," +
                "    invisible INTEGER NOT NULL," +
                "    parent    TEXT(32)" +
                ")"
        db.execSQL(sql)
        addVersion(db, "taxtables", 2)
    }

    private fun createTableTaxTableEntries(db: SQLiteDatabase) {
        val sql = "CREATE TABLE taxtable_entries (" +
                "    id           INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL," +
                "    taxtable     TEXT(32) NOT NULL," +
                "    account      TEXT(32) NOT NULL," +
                "    amount_num   BIGINT NOT NULL," +
                "    amount_denom BIGINT NOT NULL," +
                "    type         INTEGER NOT NULL" +
                ")"
        db.execSQL(sql)
        addVersion(db, "taxtable_entries", 3)
    }

    private fun createTableTransactions(db: SQLiteDatabase) {
        val sql = "CREATE TABLE transactions (" +
                "    guid          TEXT(32) PRIMARY KEY NOT NULL," +
                "    currency_guid TEXT(32) NOT NULL," +
                "    num           TEXT(2048) NOT NULL," +
                "    post_date     TEXT(19)," +
                "    enter_date    TEXT(19)," +
                "    description   TEXT(2048)" +
                ")"
        db.execSQL(sql)
        addVersion(db, "transactions", 4)

        statementTransaction = db.compileStatement(
            StringBuilder("INSERT INTO transactions (")
                .append("guid")
                .append(',').append("currency_guid")
                .append(',').append("num")
                .append(',').append("post_date")
                .append(',').append("enter_date")
                .append(',').append("description")
                .append(") VALUES (?,?,?,?,?,?)")
                .toString()
        )
    }

    private fun createTableVendors(db: SQLiteDatabase) {
        val sql = "CREATE TABLE vendors (" +
                "    guid         TEXT(32) PRIMARY KEY NOT NULL," +
                "    name         TEXT(2048) NOT NULL," +
                "    id           TEXT(2048) NOT NULL," +
                "    notes        TEXT(2048) NOT NULL," +
                "    currency     TEXT(32) NOT NULL," +
                "    active       INTEGER NOT NULL," +
                "    tax_override INTEGER NOT NULL," +
                "    addr_name    TEXT(1024)," +
                "    addr_addr1   TEXT(1024)," +
                "    addr_addr2   TEXT(1024)," +
                "    addr_addr3   TEXT(1024)," +
                "    addr_addr4   TEXT(1024)," +
                "    addr_phone   TEXT(128)," +
                "    addr_fax     TEXT(128)," +
                "    addr_email   TEXT(256)," +
                "    terms        TEXT(32)," +
                "    tax_inc      TEXT(2048)," +
                "    tax_table    TEXT(32)" +
                ")"
        db.execSQL(sql)
        addVersion(db, "vendors", 1)
    }

    private fun addVersion(db: SQLiteDatabase, name: String, version: Int) {
        val values = ContentValues()
        values["table_name"] = name
        values["table_version"] = version
        db.insert("versions", values)
    }

    private fun formatDateTime(date: Long): String {
        return dateTimeFormatter.print(date)
    }

    private fun formatCompactDate(date: Long = 0L): String {
        return dateCompactFormatter.print(date)
    }

    /**
     * Assume that all the accounts in the book are relevant, so
     * just pipe *all* the records (including the templates).
     */
    private fun pipeAccounts(db: SQLiteDatabase) {
        Timber.i("pipe accounts")
        cancellationSignal.throwIfCanceled()

        val orderBy = AccountEntry.COLUMN_ID + " ASC"
        val accounts = accountsDbAdapter.getAllRecords(null, null, orderBy)
        listener?.onAccountCount(accounts.size.toLong())

        db.transaction {
            accounts.forEach { account ->
                pipeAccount(account)
            }
        }
    }

    private fun pipeAccount(account: Account) {
        listener?.onAccount(account)

        val statement = statementAccount
        statement.clearBindings()
        statement.bindString(1, account.uid)
        statement.bindString(2, account.name)
        statement.bindString(3, account.type.name)
        statement.bindString(4, account.commodity.uid)
        statement.bindInt(5, account.commodity.smallestFraction)
        statement.bindInt(6, 0)
        account.parentUID?.let { statement.bindString(7, it) }
        statement.bindNull(8)
        statement.bindString(9, account.description)
        statement.bindBoolean(10, account.isHidden)
        statement.bindBoolean(11, account.isPlaceholder)

        val id = statement.executeInsert()
        if (id <= 0) throw ExportException(exportParams, "Failed to pipe account")

        val color = account.color
        if (color != Account.DEFAULT_COLOR) {
            pipeSlot(account, Slot.string(KEY_COLOR, formatRGB(color)))
        }
        val defaultTransferAcctUID = account.defaultTransferAccountUID
        if (!defaultTransferAcctUID.isNullOrEmpty()) {
            pipeSlot(account, Slot.string(KEY_DEFAULT_TRANSFER_ACCOUNT, defaultTransferAcctUID))
        }
        if (account.isFavorite) {
            pipeSlot(account, Slot.string(KEY_FAVORITE, "true"))
        }
        val notes = account.notes
        if (!notes.isNullOrEmpty()) {
            pipeSlot(account, Slot.string(KEY_NOTES, notes))
        }
    }

    private fun pipeBook(db: SQLiteDatabase, book: Book) {
        cancellationSignal.throwIfCanceled()
        listener?.onBook(book)

        val values = ContentValues()
        values["guid"] = book.uid
        values["root_account_guid"] = book.rootAccountUID
        values["root_template_guid"] = book.rootTemplateUID
        db.insert("books", values)
    }

    private fun pipeBudgets() {
        Timber.i("pipe budgets")
        cancellationSignal.throwIfCanceled()
        val budgets = budgetsDbAdapter.allRecords
        listener?.onBudgetCount(budgets.size.toLong())

        for (budget in budgets) {
            pipeBudget(budget)
        }
    }

    private fun pipeBudget(budget: Budget) {
        listener?.onBudget(budget)

        val statement = statementBudget
        statement.clearBindings()
        statement.bindString(1, budget.uid)
        statement.bindString(2, budget.name)
        budget.description?.let { statement.bindString(3, it) }
        statement.bindInt(4, budget.numberOfPeriods)

        val id = statement.executeInsert()
        if (id <= 0) throw ExportException(exportParams, "Failed to pipe budget")

        pipeRecurrence(budget, budget.recurrence)
        pipeBudgetAmounts(budget)
        pipeBudgetNotes(budget)
    }

    private fun pipeBudgetAmounts(budget: Budget) {
        for (accountUID in budget.accounts) {
            val amounts = budget.amounts[accountUID] ?: continue
            for (amount in amounts) {
                pipeBudgetAmount(amount)
            }
        }
    }

    private fun pipeBudgetAmount(budgetAmount: BudgetAmount) {
        val amount = budgetAmount.amount.toNumeric().reduce()

        val statement = statementBudgetAmount
        statement.clearBindings()
        statement.bindString(1, budgetAmount.budgetUID)
        statement.bindString(2, budgetAmount.accountUID!!)
        statement.bindInt(3, budgetAmount.periodIndex)
        statement.bindLong(4, amount.numerator)
        statement.bindLong(5, amount.denominator)

        val id = statement.executeInsert()
        if (id <= 0) throw ExportException(exportParams, "Failed to pipe budge amount")
    }

    private fun pipeBudgetNotes(budget: Budget) {
        val notes = mutableListOf<Slot>()

        for (accountUID in budget.accounts) {
            cancellationSignal.throwIfCanceled()
            val frame = mutableListOf<Slot>()

            val amounts = budget.amounts[accountUID] ?: continue
            for (amount in amounts) {
                val note = amount.notes
                if (note.isNullOrEmpty()) continue
                val period = amount.periodIndex
                frame.add(Slot.string(period.toString(), note))
            }

            if (!frame.isEmpty()) {
                notes.add(Slot.frame(accountUID, frame))
            }
        }

        if (notes.isNotEmpty()) {
            pipeSlot(budget, Slot.frame(KEY_NOTES, notes))
        }
    }

    private fun pipeCommodities(db: SQLiteDatabase) {
        Timber.i("pipe commodities")
        cancellationSignal.throwIfCanceled()
        val commodities = (accountsDbAdapter.allCommoditiesInUse +
                transactionsDbAdapter.getAllCommoditiesInUse())
            .distinctBy { it.uid }
            .sortedBy { it.id }
        listener?.onCommodityCount(commodities.size.toLong())

        db.transaction {
            var hasTemplate = false
            for (commodity in commodities) {
                pipeCommodity(commodity)
                if (commodity.isTemplate) {
                    hasTemplate = true
                }
            }
            if (!hasTemplate) {
                val commodity = commoditiesDbAdapter.loadCommodity(Commodity.template)
                pipeCommodity(commodity)
            }
        }
    }

    private fun pipeCommodity(commodity: Commodity) {
        listener?.onCommodity(commodity)

        val statement = statementCommodity
        statement.clearBindings()
        statement.bindString(1, commodity.uid)
        statement.bindString(2, commodity.namespace)
        statement.bindString(3, commodity.mnemonic)
        commodity.fullname?.let { statement.bindString(4, it) }
        commodity.cusip?.let { statement.bindString(5, it) }
        statement.bindInt(6, commodity.smallestFraction)
        statement.bindBoolean(7, commodity.quoteFlag)
        commodity.quoteSource?.let { statement.bindString(8, it) }
        commodity.quoteTimeZone?.id?.let { statement.bindString(9, it) }

        val id = statement.executeInsert()
        if (id <= 0) throw ExportException(exportParams, "Failed to pipe commodity")
    }

    private fun pipePrices(db: SQLiteDatabase) {
        Timber.i("pipe prices")
        cancellationSignal.throwIfCanceled()
        val prices = pricesDbAdapter.allRecords
        listener?.onPriceCount(prices.size.toLong())

        db.transaction {
            for (price in prices) {
                pipePrice(price)
            }
        }
    }

    private fun pipePrice(price: Price) {
        listener?.onPrice(price)

        val statement = statementPrice
        statement.clearBindings()
        statement.bindString(1, price.uid)
        statement.bindString(2, price.commodityUID)
        statement.bindString(3, price.currencyUID)
        statement.bindString(4, formatDateTime(price.date))
        price.source?.let { statement.bindString(5, it) }
        statement.bindString(6, price.type.value)
        statement.bindLong(7, price.valueNum)
        statement.bindLong(8, price.valueDenom)

        val id = statement.executeInsert()
        if (id <= 0) throw ExportException(exportParams, "Failed to pipe price")
    }

    private fun pipeRecurrence(owner: BaseModel, recurrence: Recurrence) {
        val statement = statementRecurrence
        statement.clearBindings()
        statement.bindString(1, owner.uid)
        statement.bindInt(2, recurrence.multiplier)
        statement.bindString(3, recurrence.periodType.value)
        statement.bindString(4, formatCompactDate(recurrence.periodStart))
        statement.bindString(5, recurrence.weekendAdjust.value)

        val id = statement.executeInsert()
        if (id <= 0) throw ExportException(exportParams, "Failed to pipe recurrence")
    }

    private fun pipeScheduledTransactions() {
        Timber.i("pipe scheduled transactions")
        cancellationSignal.throwIfCanceled()
        val actions = scheduledActionDbAdapter.getRecords(ScheduledAction.ActionType.TRANSACTION)
        listener?.onScheduleCount(actions.size.toLong())

        for (action in actions) {
            pipeScheduledTransaction(action)
        }
    }

    private fun pipeScheduledTransaction(scheduledAction: ScheduledAction) {
        listener?.onSchedule(scheduledAction)

        val statement = statementScheduledTransaction
        statement.clearBindings()
        statement.bindString(1, scheduledAction.uid)
        statement.bindString(2, scheduledAction.name)
        statement.bindBoolean(3, scheduledAction.isEnabled)
        if (scheduledAction.startDate > 0L) {
            statement.bindString(4, formatCompactDate(scheduledAction.startDate))
        }
        if (scheduledAction.endDate > 0L) {
            statement.bindString(5, formatCompactDate(scheduledAction.endDate))
        }
        if (scheduledAction.lastRunDate > 0L) {
            statement.bindString(6, formatCompactDate(scheduledAction.lastRunDate))
        }
        statement.bindInt(7, scheduledAction.totalPlannedExecutionCount)
        statement.bindInt(
            8,
            max(0, scheduledAction.totalPlannedExecutionCount - scheduledAction.instanceCount)
        )
        statement.bindBoolean(9, scheduledAction.isAutoCreate)
        statement.bindBoolean(10, scheduledAction.isAutoCreateNotify)
        statement.bindInt(11, scheduledAction.advanceCreateDays)
        statement.bindInt(12, scheduledAction.advanceRemindDays)
        statement.bindInt(13, scheduledAction.instanceCount)
        statement.bindString(14, scheduledAction.templateAccountUID)

        val id = statement.executeInsert()
        if (id <= 0) throw ExportException(exportParams, "Failed to pipe scheduled transaction")

        pipeRecurrence(scheduledAction, scheduledAction.recurrence)
    }

    private fun pipeSplits(db: SQLiteDatabase) {
        Timber.i("pipe splits")
        cancellationSignal.throwIfCanceled()

        val orderBy = SplitEntry.COLUMN_ID + " ASC"
        val cursor = splitsDbAdapter.fetchAllRecords(null, null, orderBy)
        cursor.moveToFirst()
        listener?.onTransactionCount(cursor.count.toLong())

        db.transaction {
            cursor.forEach { cursor ->
                pipeSplit(cursor)
            }
        }
    }

    private fun pipeSlots(ownerUID: String, ownerName: String? = null, slots: List<Slot>) {
        val parentName = ownerName?.let { "$it/" }.orEmpty()
        for (slot in slots) {
            val name = parentName + slot.key
            pipeSlot(ownerUID, name, slot)
        }
    }

    private fun pipeSlot(owner: BaseModel, slot: Slot) {
        pipeSlot(owner.uid, slot.key, slot)
    }

    private fun pipeSlot(ownerUID: String, slot: Slot) {
        pipeSlot(ownerUID, slot.key, slot)
    }

    private fun pipeSlot(ownerUID: String, name: String, slot: Slot) {
        val statement = statementSlot
        statement.clearBindings()
        statement.bindString(1, ownerUID)
        statement.bindString(2, name)
        statement.bindInt(3, slot.type.value)

        var parentUID: String? = null
        var children: List<Slot>? = null

        when (slot.type) {
            Slot.Type.INVALID -> Unit

            Slot.Type.INT64 -> statement.bindLong(4, slot.asLong)

            Slot.Type.STRING -> statement.bindString(5, slot.asString)

            Slot.Type.DOUBLE -> statement.bindDouble(6, slot.asDouble)

            Slot.Type.TIME64 -> statement.bindString(7, formatDateTime(slot.asDateTime))

            Slot.Type.GUID -> statement.bindString(8, slot.asGUID)

            Slot.Type.NUMERIC -> {
                val numeric = slot.asNumeric
                statement.bindLong(9, numeric.numerator)
                statement.bindLong(10, numeric.denominator)
            }

            Slot.Type.GDATE -> statement.bindString(11, formatCompactDate(slot.asDate))

            Slot.Type.PLACEHOLDER_DONT_USE -> Unit

            Slot.Type.GLIST -> TODO()

            Slot.Type.FRAME -> {
                val guid = BaseModel.generateUID()
                statement.bindString(8, guid)
                // Defer the children until after inserting this slot.
                parentUID = guid
                children = slot.asFrame
            }
        }

        val id = statement.executeInsert()
        if (id <= 0) throw ExportException(exportParams, "Failed to pipe slot")

        if (parentUID != null && children != null) {
            pipeSlots(parentUID, name, children)
        }
    }

    private fun pipeSplit(cursor: Cursor) {
        val valueNum = cursor.getLong(INDEX_COLUMN_VALUE_NUM)
        val valueDenom = cursor.getLong(INDEX_COLUMN_VALUE_DENOM)
        val quantityNum = cursor.getLong(INDEX_COLUMN_QUANTITY_NUM)
        val quantityDenom = cursor.getLong(INDEX_COLUMN_QUANTITY_DENOM)
        val typeName = cursor.getString(INDEX_COLUMN_TYPE)!!
        val accountUID = cursor.getString(INDEX_COLUMN_ACCOUNT_UID)!!
        val transxUID = cursor.getString(INDEX_COLUMN_TRANSACTION_UID)!!
        val memo = cursor.getString(INDEX_COLUMN_MEMO).orEmpty()
        val reconcileState = cursor.getString(INDEX_COLUMN_RECONCILE_STATE)
        val reconcileDate = cursor.getTimestamp(INDEX_COLUMN_RECONCILE_DATE)
        val actionAccountUID = cursor.getString(INDEX_COLUMN_SCHEDX_ACTION_ACCOUNT_UID)
        val uid = cursor.getString(splitsDbAdapter.INDEX_COLUMN_UID)!!

        cancellationSignal.throwIfCanceled()
        listener?.onTransaction(transxUID)

        val isCredit = typeName == TransactionType.CREDIT.value

        val statement = statementSplit
        statement.clearBindings()
        statement.bindString(1, uid)
        statement.bindString(2, transxUID)
        statement.bindString(3, accountUID)
        statement.bindString(4, memo)
        statement.bindString(5, "")
        statement.bindString(6, reconcileState)
        statement.bindString(7, formatDateTime(reconcileDate?.time ?: 0L))
        statement.bindLong(8, 0L)
        statement.bindLong(9, 100L)
        statement.bindLong(10, 0L)
        statement.bindLong(11, 1L)
        statement.bindNull(12)

        if (actionAccountUID.isNullOrEmpty()) {
            val sign = if (isCredit) -1 else +1
            statement.bindLong(8, sign * valueNum)
            statement.bindLong(9, valueDenom)
            statement.bindLong(10, sign * quantityNum)
            statement.bindLong(11, quantityDenom)
            val id = statement.executeInsert()
            if (id <= 0) throw ExportException(exportParams, "Failed to pipe split")
        } else {
            val id = statement.executeInsert()
            if (id <= 0) throw ExportException(exportParams, "Failed to pipe split")

            val frame = mutableListOf<Slot>()
            frame.add(Slot.guid(KEY_SPLIT_ACCOUNT_SLOT, actionAccountUID))
            val value = toBigDecimal(valueNum, valueDenom)
            if (isCredit) {
                frame.add(Slot.string(KEY_CREDIT_FORMULA, formatFormula(value)))
                frame.add(Slot.numeric(KEY_CREDIT_NUMERIC, valueNum, valueDenom))
                frame.add(Slot.string(KEY_DEBIT_FORMULA, ""))
                frame.add(Slot.numeric(KEY_DEBIT_NUMERIC, 0, 1))
            } else {
                frame.add(Slot.string(KEY_CREDIT_FORMULA, ""))
                frame.add(Slot.numeric(KEY_CREDIT_NUMERIC, 0, 1))
                frame.add(Slot.string(KEY_DEBIT_FORMULA, formatFormula(value)))
                frame.add(Slot.numeric(KEY_DEBIT_NUMERIC, valueNum, valueDenom))
            }
            pipeSlot(uid, Slot.frame(KEY_SCHED_XACTION, frame))
        }
    }

    /**
     * Assume that all the transactions in the book are relevant, so
     * just pipe *all* the records (including the templates).
     */
    private fun pipeTransactions(db: SQLiteDatabase) {
        Timber.i("pipe transactions")
        cancellationSignal.throwIfCanceled()

        val count = transactionsDbAdapter.getRecordsCount(null, null)
        listener?.onTransactionCount(count)

        db.transaction {
            val orderBy = TransactionEntry.COLUMN_ID + " ASC"
            val cursor = transactionsDbAdapter.fetchAllRecords(null, null, orderBy)
            cursor.forEach { cursor ->
                pipeTransaction(cursor)
            }
        }
    }

    private fun pipeTransaction(cursor: Cursor) {
        val description = cursor.getString(TransactionsDbAdapter.INDEX_COLUMN_DESCRIPTION)!!
        val datePosted = cursor.getLong(TransactionsDbAdapter.INDEX_COLUMN_DATE_POSTED)
        val notes = cursor.getString(TransactionsDbAdapter.INDEX_COLUMN_NOTES).orEmpty()
        val commodityUID = cursor.getString(TransactionsDbAdapter.INDEX_COLUMN_COMMODITY_UID)!!
        val number = cursor.getString(TransactionsDbAdapter.INDEX_COLUMN_NUMBER).orEmpty()
        val uid = cursor.getString(transactionsDbAdapter.INDEX_COLUMN_UID)!!
        val dateEntered = cursor.getTimestamp(transactionsDbAdapter.INDEX_COLUMN_CREATED_AT)!!

        cancellationSignal.throwIfCanceled()
        listener?.onTransaction(description)

        val statement = statementTransaction
        statement.clearBindings()
        statement.bindString(1, uid)
        statement.bindString(2, commodityUID)
        statement.bindString(3, number)
        statement.bindString(4, formatDateTime(datePosted))
        statement.bindString(5, formatDateTime(dateEntered.time))
        statement.bindString(6, description)

        val id = statement.executeInsert()
        if (id <= 0) throw ExportException(exportParams, "Failed to pipe transaction")

        if (notes.isNotEmpty()) {
            pipeSlot(uid, Slot.string(KEY_NOTES, notes))
        }
    }
}