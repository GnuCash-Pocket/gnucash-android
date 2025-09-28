package org.gnucash.android.db.adapter

import android.database.Cursor
import android.database.sqlite.SQLiteStatement
import androidx.core.content.edit
import org.gnucash.android.R
import org.gnucash.android.app.GnuCashApplication
import org.gnucash.android.db.DatabaseHolder
import org.gnucash.android.db.DatabaseSchema.CommodityEntry
import org.gnucash.android.db.bindBoolean
import org.gnucash.android.db.getInt
import org.gnucash.android.db.getString
import org.gnucash.android.model.Commodity
import org.gnucash.android.model.Commodity.Companion.getLocaleCurrencyCode
import timber.log.Timber

/**
 * Database adapter for [Commodity]
 */
class CommoditiesDbAdapter(
    holder: DatabaseHolder,
    initCommon: Boolean = true
) : DatabaseAdapter<Commodity>(
    holder,
    CommodityEntry.TABLE_NAME,
    arrayOf<String>(
        CommodityEntry.COLUMN_FULLNAME,
        CommodityEntry.COLUMN_NAMESPACE,
        CommodityEntry.COLUMN_MNEMONIC,
        CommodityEntry.COLUMN_LOCAL_SYMBOL,
        CommodityEntry.COLUMN_CUSIP,
        CommodityEntry.COLUMN_SMALLEST_FRACTION,
        CommodityEntry.COLUMN_QUOTE_FLAG,
        CommodityEntry.COLUMN_QUOTE_SOURCE,
        CommodityEntry.COLUMN_QUOTE_TZ
    ),
    true
) {
    private var defaultCommodity: Commodity? = null

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
        } else {
            defaultCommodity = getDefaultCommodity()
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

        Commodity.DEFAULT_COMMODITY = getDefaultCommodity()
        defaultCommodity = Commodity.DEFAULT_COMMODITY
    }

    override fun bind(stmt: SQLiteStatement, commodity: Commodity): SQLiteStatement {
        bindBaseModel(stmt, commodity)
        stmt.bindString(1, commodity.fullname)
        stmt.bindString(2, commodity.namespace)
        stmt.bindString(3, commodity.mnemonic)
        if (commodity.localSymbol != null) {
            stmt.bindString(4, commodity.localSymbol)
        }
        if (commodity.cusip != null) {
            stmt.bindString(5, commodity.cusip)
        }
        stmt.bindLong(6, commodity.smallestFraction.toLong())
        stmt.bindBoolean(7, commodity.quoteFlag)
        if (commodity.quoteSource != null) {
            stmt.bindString(8, commodity.quoteSource)
        }
        if (commodity.getQuoteTimeZoneId() != null) {
            stmt.bindString(9, commodity.getQuoteTimeZoneId())
        }

        return stmt
    }

    override fun buildModelInstance(cursor: Cursor): Commodity {
        val fullname = cursor.getString(CommodityEntry.COLUMN_FULLNAME)
        val mnemonic = cursor.getString(CommodityEntry.COLUMN_MNEMONIC)!!
        val namespace = cursor.getString(CommodityEntry.COLUMN_NAMESPACE)!!
        val cusip = cursor.getString(CommodityEntry.COLUMN_CUSIP)
        val localSymbol = cursor.getString(CommodityEntry.COLUMN_LOCAL_SYMBOL)

        val fraction = cursor.getInt(CommodityEntry.COLUMN_SMALLEST_FRACTION)
        val quoteSource = cursor.getString(CommodityEntry.COLUMN_QUOTE_SOURCE)
        val quoteTZ = cursor.getString(CommodityEntry.COLUMN_QUOTE_TZ)

        val commodity = Commodity(fullname, mnemonic, namespace, fraction)
        populateBaseModelAttributes(cursor, commodity)
        commodity.cusip = cusip
        commodity.quoteSource = quoteSource
        commodity.setQuoteTimeZone(quoteTZ)
        commodity.localSymbol = localSymbol

        return commodity
    }

    override fun fetchAllRecords(): Cursor? {
        return fetchAllRecords(CommodityEntry.COLUMN_MNEMONIC + " ASC")
    }

    /**
     * Fetches all commodities in the database sorted in the specified order
     *
     * @param orderBy SQL statement for orderBy without the ORDER_BY itself
     * @return Cursor holding all commodity records
     */
    fun fetchAllRecords(orderBy: String?): Cursor? {
        return db.query(tableName, null, null, null, null, null, orderBy)
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
        val cursor = fetchAllRecords(where, whereArgs, null) ?: return null
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

        when (currencyCode) {
            "AUD" -> return Commodity.AUD
            "CAD" -> return Commodity.CAD
            "CHF" -> return Commodity.CHF
            "EUR" -> return Commodity.EUR
            "GBP" -> return Commodity.GBP
            "JPY" -> return Commodity.JPY
            "USD" -> return Commodity.USD
            else -> return null
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
        var commodity = commodity
        if (commodity.id != 0L) {
            return commodity
        }
        try {
            commodity = getRecord(commodity.uid)
        } catch (_: Exception) {
            // Commodity not found.
            commodity = getCurrency(commodity.currencyCode)!!
        }
        return commodity
    }

    fun getDefaultCommodity(): Commodity {
        var commodity: Commodity? = defaultCommodity
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
        defaultCommodity = commodity
        return commodity
    }

    fun setDefaultCurrencyCode(currencyCode: String?) {
        val context = holder.context
        val preferences = bookPreferences
        val prefKey = context.getString(R.string.key_default_currency)
        preferences.edit { putString(prefKey, currencyCode) }

        val commodity = getCurrency(currencyCode)
        if (commodity != null) {
            defaultCommodity = commodity
            Commodity.DEFAULT_COMMODITY = commodity
        }
    }

    companion object {
        val instance: CommoditiesDbAdapter get() = GnuCashApplication.commoditiesDbAdapter!!
    }
}
