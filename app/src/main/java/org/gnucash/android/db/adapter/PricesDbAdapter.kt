package org.gnucash.android.db.adapter

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteStatement
import org.gnucash.android.app.GnuCashApplication
import org.gnucash.android.db.DatabaseSchema.PriceEntry
import org.gnucash.android.db.bindTimestamp
import org.gnucash.android.db.getTimestamp
import org.gnucash.android.model.Commodity
import org.gnucash.android.model.Price
import org.gnucash.android.model.PriceSource
import java.io.IOException
import java.math.BigDecimal

/**
 * Database adapter for prices
 */
class PricesDbAdapter(val commoditiesDbAdapter: CommoditiesDbAdapter) : DatabaseAdapter<Price>(
    commoditiesDbAdapter.holder,
    PriceEntry.TABLE_NAME,
    entryColumns,
    true
) {
    private val cachePair = mutableMapOf<String, Price>()
    private var cachePairLoaded = false

    @Throws(IOException::class)
    override fun close() {
        commoditiesDbAdapter.close()
        cachePair.clear()
        super.close()
    }

    override fun bind(stmt: SQLiteStatement, price: Price): SQLiteStatement {
        bindBaseModel(stmt, price)
        stmt.bindString(1 + INDEX_COLUMN_SECURITY_UID, price.securityUID)
        stmt.bindString(1 + INDEX_COLUMN_CURRENCY_UID, price.currencyUID)
        stmt.bindTimestamp(1 + INDEX_COLUMN_DATE, price.date)
        if (price.source != null) {
            stmt.bindString(1 + INDEX_COLUMN_SOURCE, price.source!!.value)
        }
        stmt.bindString(1 + INDEX_COLUMN_TYPE, price.type.value)
        stmt.bindLong(1 + INDEX_COLUMN_VALUE_NUM, price.valueNum)
        stmt.bindLong(1 + INDEX_COLUMN_VALUE_DENOM, price.valueDenom)

        return stmt
    }

    override fun buildModelInstance(cursor: Cursor): Price {
        val securityUID = cursor.getString(INDEX_COLUMN_SECURITY_UID)!!
        val currencyUID = cursor.getString(INDEX_COLUMN_CURRENCY_UID)!!
        val dateString = cursor.getTimestamp(INDEX_COLUMN_DATE)!!
        val source = cursor.getString(INDEX_COLUMN_SOURCE)
        val type = cursor.getString(INDEX_COLUMN_TYPE)
        val valueNum = cursor.getLong(INDEX_COLUMN_VALUE_NUM)
        val valueDenom = cursor.getLong(INDEX_COLUMN_VALUE_DENOM)

        val security = commoditiesDbAdapter.getRecord(securityUID)
        val currency = commoditiesDbAdapter.getRecord(currencyUID)
        val price = Price(security, currency)
        populateBaseModelAttributes(cursor, price)
        price.date = dateString.time
        price.source = PriceSource.of(source)
        price.type = Price.Type.of(type)
        price.valueNum = valueNum
        price.valueDenom = valueDenom

        return price
    }

    /**
     * Get the price for commodity / currency pair.
     * The price can be used to convert from one commodity to another. The 'commodity' is the origin and the 'currency' is the target for the conversion.
     *
     * @param securityCode Currency code of the commodity which is starting point for conversion
     * @param currencyCode  Currency code of target commodity for the conversion
     * @return The numerator/denominator pair for commodity / currency pair
     */
    fun getPriceForCurrencies(securityCode: String, currencyCode: String): Price? {
        val security = commoditiesDbAdapter.getCurrency(securityCode)!!
        val currency = commoditiesDbAdapter.getCurrency(currencyCode)!!
        return getPrice(security, currency)
    }

    /**
     * Get the price for commodity / currency pair.
     * The price can be used to convert from one commodity to another. The 'commodity' is the origin and the 'currency' is the target for the conversion.
     *
     * @param commodityUID GUID of the commodity which is starting point for conversion
     * @param currencyUID  GUID of target commodity for the conversion
     * @return The numerator/denominator pair for commodity / currency pair
     */
    fun getPrice(commodityUID: String, currencyUID: String): Price? {
        val commodity = commoditiesDbAdapter.getRecord(commodityUID)
        val currency = commoditiesDbAdapter.getRecord(currencyUID)
        return getPrice(commodity, currency)
    }

    /**
     * Get the price for commodity / currency pair.
     * The price can be used to convert from one commodity to another. The 'commodity' is the origin and the 'currency' is the target for the conversion.
     *
     * @param security the commodity which is starting point for conversion
     * @param currency  the target commodity for the conversion
     * @return The numerator/denominator pair for commodity / currency pair
     */
    fun getPrice(security: Commodity, currency: Commodity): Price? {
        val securityUID = security.uid
        val currencyUID = currency.uid
        val key = "$securityUID/$currencyUID"
        val keyInverse = "$currencyUID/$securityUID"
        if (isCached) {
            val price = cachePair[key]
            if (price != null) return price
            val priceInverse = cachePair[keyInverse]
            if (priceInverse != null) return priceInverse
        }
        if (security == currency) {
            val price = Price(security, currency, BigDecimal.ONE)
            if (isCached) {
                cachePair[key] = price
            }
            return price
        }

        // Cache all the prices.
        if (isCached && !cachePairLoaded) {
            cacheAll()
            cachePairLoaded = true

            // Try hit the cache again.
            val price = cachePair[key]
            if (price != null) return price
            val priceInverse = cachePair[keyInverse]
            if (priceInverse != null) return priceInverse
            return null
        }

        // the commodity and currency can be swapped
        val where = "(" + PriceEntry.COLUMN_SECURITY_UID + " = ? AND " +
                PriceEntry.COLUMN_CURRENCY_UID + " = ?)" +
                " OR (" + PriceEntry.COLUMN_SECURITY_UID + " = ? AND " +
                PriceEntry.COLUMN_CURRENCY_UID + " = ?)"
        val whereArgs = arrayOf<String?>(securityUID, currencyUID, currencyUID, securityUID)
        val orderBy = PriceEntry.COLUMN_DATE + " DESC"
        val cursor = db.query(tableName, allColumns, where, whereArgs, null, null, orderBy, "1")
        // only get the latest price
        cursor.use { cursor ->
            if (cursor.moveToFirst()) {
                val price = buildModelInstance(cursor)
                val valueNum = price.valueNum
                val valueDenom = price.valueDenom
                if (valueNum <= 0 || valueDenom <= 0) {
                    // this should not happen!
                    return null
                }
                // swap numerator and denominator
                if (price.currencyUID == currencyUID) {
                    return price
                }
                return price.invert()
            }
        }
        // TODO Try with intermediate currency, e.g. EUR -> ETB -> ILS
        return null
    }

    override fun addRecord(model: Price, updateMethod: UpdateMethod): Price {
        super.addRecord(model, updateMethod)
        if (isCached) {
            val security = model.security
            val currency = model.currency
            val securityUID = security.uid
            val currencyUID = currency.uid
            val key = "$securityUID/$currencyUID"
            val keyInverse = "$currencyUID/$securityUID"
            val price = cachePair[key]
            if (price == null || (price.date < model.date)
                || (price.date == model.date && price.modifiedTimestamp < model.modifiedTimestamp)) {
                cachePair[key] = model
                cachePair[keyInverse] = model.invert()
            }
        }

        return model
    }

    override fun deleteAllRecords(): Int {
        cachePair.clear()
        return super.deleteAllRecords()
    }

    override fun updateRecord(uid: String, contentValues: ContentValues): Int {
        cachePair.clear()
        return super.updateRecord(uid, contentValues)
    }

    private fun cacheAll() {
        // override the older price
        val orderBy = PriceEntry.COLUMN_DATE + " ASC"
        val prices = getAllRecords(null, null, orderBy)
        for (price in prices) {
            val valueNum = price.valueNum
            val valueDenom = price.valueDenom
            if (valueNum <= 0 || valueDenom <= 0) {
                // this should not happen!
                continue
            }
            val key = "${price.securityUID}/${price.currencyUID}"
            cachePair[key] = price
            val priceInverse = price.invert()
            val keyInverse = "${priceInverse.securityUID}/${priceInverse.currencyUID}"
            cachePair[keyInverse] = priceInverse
        }
    }

    companion object {
        private val entryColumns = arrayOf(
            PriceEntry.COLUMN_SECURITY_UID,
            PriceEntry.COLUMN_CURRENCY_UID,
            PriceEntry.COLUMN_DATE,
            PriceEntry.COLUMN_SOURCE,
            PriceEntry.COLUMN_TYPE,
            PriceEntry.COLUMN_VALUE_NUM,
            PriceEntry.COLUMN_VALUE_DENOM
        )
        private const val INDEX_COLUMN_SECURITY_UID = 0
        private const val INDEX_COLUMN_CURRENCY_UID = INDEX_COLUMN_SECURITY_UID + 1
        private const val INDEX_COLUMN_DATE = INDEX_COLUMN_CURRENCY_UID + 1
        private const val INDEX_COLUMN_SOURCE = INDEX_COLUMN_DATE + 1
        private const val INDEX_COLUMN_TYPE = INDEX_COLUMN_SOURCE + 1
        private const val INDEX_COLUMN_VALUE_NUM = INDEX_COLUMN_TYPE + 1
        private const val INDEX_COLUMN_VALUE_DENOM = INDEX_COLUMN_VALUE_NUM + 1

        val instance: PricesDbAdapter get() = GnuCashApplication.pricesDbAdapter!!
    }
}
