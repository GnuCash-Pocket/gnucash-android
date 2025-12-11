package org.gnucash.android.db.adapter

import android.database.Cursor
import android.database.sqlite.SQLiteStatement
import org.gnucash.android.app.GnuCashApplication
import org.gnucash.android.db.DatabaseSchema.PriceEntry
import org.gnucash.android.db.getLong
import org.gnucash.android.db.getString
import org.gnucash.android.model.Commodity
import org.gnucash.android.model.Price
import org.gnucash.android.util.TimestampHelper.getTimestampFromUtcString
import org.gnucash.android.util.TimestampHelper.getUtcStringFromTimestamp
import java.io.IOException
import java.math.BigDecimal

/**
 * Database adapter for prices
 */
class PricesDbAdapter(val commoditiesDbAdapter: CommoditiesDbAdapter) :
    DatabaseAdapter<Price>(
        commoditiesDbAdapter.holder,
        PriceEntry.TABLE_NAME,
        arrayOf(
            PriceEntry.COLUMN_COMMODITY_UID,
            PriceEntry.COLUMN_CURRENCY_UID,
            PriceEntry.COLUMN_DATE,
            PriceEntry.COLUMN_SOURCE,
            PriceEntry.COLUMN_TYPE,
            PriceEntry.COLUMN_VALUE_NUM,
            PriceEntry.COLUMN_VALUE_DENOM
        ),
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
        stmt.bindString(1, price.commodityUID)
        stmt.bindString(2, price.currencyUID)
        stmt.bindString(3, getUtcStringFromTimestamp(price.date))
        if (price.source != null) {
            stmt.bindString(4, price.source)
        }
        stmt.bindString(5, price.type.value)
        stmt.bindLong(6, price.valueNum)
        stmt.bindLong(7, price.valueDenom)

        return stmt
    }

    override fun buildModelInstance(cursor: Cursor): Price {
        val commodityUID = cursor.getString(PriceEntry.COLUMN_COMMODITY_UID)!!
        val currencyUID = cursor.getString(PriceEntry.COLUMN_CURRENCY_UID)!!
        val dateString = cursor.getString(PriceEntry.COLUMN_DATE)!!
        val source = cursor.getString(PriceEntry.COLUMN_SOURCE)
        val type = cursor.getString(PriceEntry.COLUMN_TYPE)
        val valueNum = cursor.getLong(PriceEntry.COLUMN_VALUE_NUM)
        val valueDenom = cursor.getLong(PriceEntry.COLUMN_VALUE_DENOM)

        val commodity1 = commoditiesDbAdapter.getRecord(commodityUID)
        val commodity2 = commoditiesDbAdapter.getRecord(currencyUID)
        val price = Price(commodity1, commodity2)
        populateBaseModelAttributes(cursor, price)
        price.date = getTimestampFromUtcString(dateString).getTime()
        price.source = source
        price.type = Price.Type.of(type)
        price.valueNum = valueNum
        price.valueDenom = valueDenom

        return price
    }

    /**
     * Get the price for commodity / currency pair.
     * The price can be used to convert from one commodity to another. The 'commodity' is the origin and the 'currency' is the target for the conversion.
     *
     * @param commodityCode Currency code of the commodity which is starting point for conversion
     * @param currencyCode  Currency code of target commodity for the conversion
     * @return The numerator/denominator pair for commodity / currency pair
     */
    fun getPriceForCurrencies(commodityCode: String, currencyCode: String): Price? {
        val commodity = commoditiesDbAdapter.getCurrency(commodityCode)!!
        val currency = commoditiesDbAdapter.getCurrency(currencyCode)!!
        return getPrice(commodity, currency)
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
     * @param commodity the commodity which is starting point for conversion
     * @param currency  the target commodity for the conversion
     * @return The numerator/denominator pair for commodity / currency pair
     */
    fun getPrice(commodity: Commodity, currency: Commodity): Price? {
        val commodityUID = commodity.uid
        val currencyUID = currency.uid
        val key = "$commodityUID/$currencyUID"
        val keyInverse = "$currencyUID/$commodityUID"
        if (isCached) {
            val price = cachePair[key]
            if (price != null) return price
            val priceInverse = cachePair[keyInverse]
            if (priceInverse != null) return priceInverse
        }
        if (commodity == currency) {
            val price = Price(commodity, currency, BigDecimal.ONE)
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
        val where = ("(" + PriceEntry.COLUMN_COMMODITY_UID + " = ? AND " +
                PriceEntry.COLUMN_CURRENCY_UID + " = ?)" +
                " OR (" + PriceEntry.COLUMN_COMMODITY_UID + " = ? AND " +
                PriceEntry.COLUMN_CURRENCY_UID + " = ?)")
        val whereArgs = arrayOf<String?>(commodityUID, currencyUID, currencyUID, commodityUID)
        val orderBy = PriceEntry.COLUMN_DATE + " DESC"
        val cursor = db.query(tableName, null, where, whereArgs, null, null, orderBy, "1")
        // only get the latest price
        try {
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
                return price.inverse()
            }
        } finally {
            cursor.close()
        }
        // TODO Try with intermediate currency, e.g. EUR -> ETB -> ILS
        return null
    }

    override fun addRecord(model: Price, updateMethod: UpdateMethod): Price {
        super.addRecord(model, updateMethod)
        if (isCached) {
            val commodity = model.commodity
            val currency = model.currency
            val commodityUID = commodity.uid
            val currencyUID = currency.uid
            val key = "$commodityUID/$currencyUID"
            val keyInverse = "$currencyUID/$commodityUID"
            val price = cachePair[key]
            if (price == null || price.date < model.date) {
                cachePair[key] = model
                cachePair[keyInverse] = model.inverse()
            }
        }

        return model
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
            val key = "${price.commodityUID}/${price.currencyUID}"
            cachePair[key] = price
            val priceInverse = price.inverse()
            val keyInverse = "${priceInverse.commodityUID}/${priceInverse.currencyUID}"
            cachePair[keyInverse] = priceInverse
        }
    }

    companion object {
        val instance: PricesDbAdapter get() = GnuCashApplication.pricesDbAdapter!!
    }
}
