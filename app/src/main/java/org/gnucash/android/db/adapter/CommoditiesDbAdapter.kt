package org.gnucash.android.db.adapter

import android.database.Cursor
import android.database.sqlite.SQLiteStatement
import androidx.core.content.edit
import org.gnucash.android.R
import org.gnucash.android.app.GnuCashApplication
import org.gnucash.android.db.DatabaseHolder
import org.gnucash.android.db.DatabaseSchema.CommodityEntry
import org.gnucash.android.db.bindBoolean
import org.gnucash.android.db.bindInt
import org.gnucash.android.model.Commodity
import org.gnucash.android.model.Commodity.Companion.getLocaleCurrencyCode
import timber.log.Timber

/**
 * Database adapter for [Commodity]
 */
class CommoditiesDbAdapter(
    holder: DatabaseHolder,
    initCommon: Boolean = false
) : DatabaseAdapter<Commodity>(
    holder,
    CommodityEntry.TABLE_NAME,
    entryColumns,
    true
) {
    private var _defaultCommodity: Commodity? = null

    /**
     * Opens the database adapter with an existing database
     *
     * @param holder     Database holder
     * @param initCommon initialize commonly used commodities?
     */
    /**
     * Opens the database adapter with an existing database
     *
     * @param holder Database holder
     */
    init {
        if (initCommon) {
            initCommon()
        }
    }

    /**
     * initialize commonly used commodities
     */
    fun initCommon() {
        Commodity.AUD = getCurrency("AUD")!!
        Commodity.CAD = getCurrency("CAD")!!
        Commodity.CHF = getCurrency("CHF")!!
        Commodity.EUR = getCurrency("EUR")!!
        Commodity.GBP = getCurrency("GBP")!!
        Commodity.JPY = getCurrency("JPY")!!
        Commodity.USD = getCurrency("USD")!!
        Commodity.DEFAULT_COMMODITY = defaultCommodity
    }

    override fun bind(stmt: SQLiteStatement, commodity: Commodity): SQLiteStatement {
        bindBaseModel(stmt, commodity)
        stmt.bindString(1 + INDEX_COLUMN_FULLNAME, commodity.fullname)
        stmt.bindString(1 + INDEX_COLUMN_NAMESPACE, commodity.namespace)
        stmt.bindString(1 + INDEX_COLUMN_MNEMONIC, commodity.mnemonic)
        if (commodity.localSymbol != null) {
            stmt.bindString(1 + INDEX_COLUMN_LOCAL_SYMBOL, commodity.localSymbol)
        }
        if (commodity.cusip != null) {
            stmt.bindString(1 + INDEX_COLUMN_CUSIP, commodity.cusip)
        }
        stmt.bindInt(1 + INDEX_COLUMN_SMALLEST_FRACTION, commodity.smallestFraction)
        stmt.bindBoolean(1 + INDEX_COLUMN_QUOTE_FLAG, commodity.quoteFlag)
        if (commodity.quoteSource != null) {
            stmt.bindString(1 + INDEX_COLUMN_QUOTE_SOURCE, commodity.quoteSource)
        }
        if (commodity.getQuoteTimeZoneId() != null) {
            stmt.bindString(1 + INDEX_COLUMN_QUOTE_TZ, commodity.getQuoteTimeZoneId())
        }

        return stmt
    }

    override fun buildModelInstance(cursor: Cursor): Commodity {
        val fullname = cursor.getString(INDEX_COLUMN_FULLNAME)
        val mnemonic = cursor.getString(INDEX_COLUMN_MNEMONIC)!!
        val namespace = cursor.getString(INDEX_COLUMN_NAMESPACE)!!
        val cusip = cursor.getString(INDEX_COLUMN_CUSIP)
        val localSymbol = cursor.getString(INDEX_COLUMN_LOCAL_SYMBOL)
        val fraction = cursor.getInt(INDEX_COLUMN_SMALLEST_FRACTION)
        val quoteSource = cursor.getString(INDEX_COLUMN_QUOTE_SOURCE)
        val quoteTZ = cursor.getString(INDEX_COLUMN_QUOTE_TZ)

        val commodity = Commodity(fullname, mnemonic, namespace, fraction)
        populateBaseModelAttributes(cursor, commodity)
        commodity.cusip = cusip
        commodity.quoteSource = quoteSource
        commodity.setQuoteTimeZone(quoteTZ)
        commodity.localSymbol = localSymbol

        return commodity
    }

    override fun fetchAllRecords(
        where: String?,
        whereArgs: Array<String?>?,
        orderBy: String?
    ): Cursor {
        val orderBy = orderBy ?: (CommodityEntry.COLUMN_MNEMONIC + " ASC")
        return super.fetchAllRecords(where, whereArgs, orderBy)
    }

    /**
     * Returns the commodity associated with the ISO4217 currency code
     *
     * @param currencyCode 3-letter currency code
     * @return Commodity associated with code or null if none is found
     */
    fun getCurrency(currencyCode: String?): Commodity? {
        if (currencyCode.isNullOrEmpty()) {
            return null
        }
        if (isCached) {
            for (commodity in cache.values) {
                if (commodity.isCurrency && commodity.currencyCode == currencyCode) {
                    return commodity
                }
            }
        }
        val where = (CommodityEntry.COLUMN_MNEMONIC + "=?"
                + " AND " + CommodityEntry.COLUMN_NAMESPACE
                + " IN ('" + Commodity.COMMODITY_CURRENCY + "','" + Commodity.COMMODITY_ISO4217 + "')")
        val whereArgs = arrayOf<String?>(currencyCode)
        val cursor = fetchAllRecords(where, whereArgs, null)
        try {
            if (cursor.moveToFirst()) {
                val commodity = buildModelInstance(cursor)
                if (isCached) {
                    cache[commodity.uid] = commodity
                }
                return commodity
            } else {
                val msg = "Commodity not found in the database: $currencyCode"
                Timber.e(msg)
            }
        } finally {
            cursor.close()
        }

        return when (currencyCode) {
            "AUD" -> Commodity.AUD
            "CAD" -> Commodity.CAD
            "CHF" -> Commodity.CHF
            "EUR" -> Commodity.EUR
            "GBP" -> Commodity.GBP
            "JPY" -> Commodity.JPY
            "USD" -> Commodity.USD
            else -> null
        }
    }

    fun getCommodityUID(currencyCode: String): String? {
        val commodity = getCurrency(currencyCode)
        return commodity?.uid
    }

    @Throws(IllegalArgumentException::class)
    fun getCurrencyCode(guid: String): String {
        val commodity =
            getRecordOrNull(guid) ?: throw IllegalArgumentException("Commodity not found")
        return commodity.currencyCode
    }

    fun loadCommodity(commodity: Commodity): Commodity {
        if (commodity.id != 0L) {
            return commodity
        }
        return getRecordOrNull(commodity.uid)
            ?: getCommodity(commodity.mnemonic, commodity.namespace)
            ?: getCurrency(commodity.currencyCode)
            ?: commodity
    }

    val defaultCommodity: Commodity
        get() {
            var commodity: Commodity? = _defaultCommodity
            if (commodity != null) {
                return commodity
            }

            val context = holder.context
            val prefKey = context.getString(R.string.key_default_currency)
            val preferences = bookPreferences
            var currencyCode = preferences.getString(prefKey, null)
            if (currencyCode == null) {
                currencyCode = getLocaleCurrencyCode()
            }
            commodity = getCurrency(currencyCode) ?: Commodity.DEFAULT_COMMODITY
            _defaultCommodity = commodity
            return commodity
        }

    fun setDefaultCurrencyCode(currencyCode: String?) {
        val context = holder.context
        val preferences = bookPreferences
        val prefKey = context.getString(R.string.key_default_currency)
        preferences.edit { putString(prefKey, currencyCode) }

        val commodity = getCurrency(currencyCode)
        if (commodity != null) {
            _defaultCommodity = commodity
            Commodity.DEFAULT_COMMODITY = commodity
        }
    }

    fun getCommodity(mnemonic: String, namespace: String): Commodity? {
        val ns = if (namespace == Commodity.COMMODITY_ISO4217) {
            Commodity.COMMODITY_CURRENCY
        } else {
            namespace
        }
        if (isCached) {
            val commodity = cache.values.firstOrNull {
                it.mnemonic == mnemonic && it.namespace == ns
            }
            if (commodity != null) {
                return commodity
            }
        }

        val where = CommodityEntry.COLUMN_MNEMONIC + "=?" +
                " AND " + CommodityEntry.COLUMN_NAMESPACE + "=?"
        val whereArgs = arrayOf<String?>(mnemonic, ns)
        val cursor = fetchAllRecords(where, whereArgs, null)
        cursor.use {
            if (cursor.moveToFirst()) {
                return buildModelInstance(cursor)
            }
        }
        return null
    }

    companion object {
        private val entryColumns = arrayOf(
            CommodityEntry.COLUMN_FULLNAME,
            CommodityEntry.COLUMN_NAMESPACE,
            CommodityEntry.COLUMN_MNEMONIC,
            CommodityEntry.COLUMN_LOCAL_SYMBOL,
            CommodityEntry.COLUMN_CUSIP,
            CommodityEntry.COLUMN_SMALLEST_FRACTION,
            CommodityEntry.COLUMN_QUOTE_FLAG,
            CommodityEntry.COLUMN_QUOTE_SOURCE,
            CommodityEntry.COLUMN_QUOTE_TZ
        )
        private const val INDEX_COLUMN_FULLNAME = 0
        private const val INDEX_COLUMN_NAMESPACE = INDEX_COLUMN_FULLNAME + 1
        private const val INDEX_COLUMN_MNEMONIC = INDEX_COLUMN_NAMESPACE + 1
        private const val INDEX_COLUMN_LOCAL_SYMBOL = INDEX_COLUMN_MNEMONIC + 1
        private const val INDEX_COLUMN_CUSIP = INDEX_COLUMN_LOCAL_SYMBOL + 1
        private const val INDEX_COLUMN_SMALLEST_FRACTION = INDEX_COLUMN_CUSIP + 1
        private const val INDEX_COLUMN_QUOTE_FLAG = INDEX_COLUMN_SMALLEST_FRACTION + 1
        private const val INDEX_COLUMN_QUOTE_SOURCE = INDEX_COLUMN_QUOTE_FLAG + 1
        private const val INDEX_COLUMN_QUOTE_TZ = INDEX_COLUMN_QUOTE_SOURCE + 1

        val instance: CommoditiesDbAdapter get() = GnuCashApplication.commoditiesDbAdapter!!
    }
}
