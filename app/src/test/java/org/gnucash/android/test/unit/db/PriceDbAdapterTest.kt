package org.gnucash.android.test.unit.db

import org.assertj.core.api.Assertions.assertThat
import org.gnucash.android.db.adapter.CommoditiesDbAdapter
import org.gnucash.android.db.adapter.PricesDbAdapter
import org.gnucash.android.model.Price
import org.gnucash.android.test.unit.GnuCashTest
import org.junit.Test

/**
 * Test price functions
 */
class PriceDbAdapterTest : GnuCashTest() {
    /**
     * The price table should override price for any commodity/currency pair
     * todo: maybe move this to UI testing. Not sure how Robolectric handles this
     */
    @Test
    fun shouldOnlySaveOnePricePerCommodityPair() {
        val commodity = CommoditiesDbAdapter.getInstance()!!.getCommodity("EUR")
        val currency = CommoditiesDbAdapter.getInstance()!!.getCommodity("USD")
        var price = Price(commodity!!, currency!!)
        price.valueNum = 134
        price.valueDenom = 100

        val pricesDbAdapter = PricesDbAdapter.getInstance()
        pricesDbAdapter.addRecord(price)

        price = pricesDbAdapter.getRecord(price.uid)
        assertThat(pricesDbAdapter.recordsCount).isOne()
        assertThat(price.valueNum)
            .isEqualTo(67) //the price is reduced to 57/100 before saving

        val price1 = Price(commodity, currency)
        price1.valueNum = 187
        price1.valueDenom = 100
        pricesDbAdapter.addRecord(price1)

        assertThat(pricesDbAdapter.recordsCount).isOne()
        val savedPrice = pricesDbAdapter.allRecords[0]
        assertThat(savedPrice.uid).isEqualTo(price1.uid) //different records
        assertThat(savedPrice.valueNum).isEqualTo(187)
        assertThat(savedPrice.valueDenom).isEqualTo(100)

        val price2 = Price(currency, commodity)
        price2.valueNum = 190
        price2.valueDenom = 100
        pricesDbAdapter.addRecord(price2)

        assertThat(pricesDbAdapter.recordsCount).isEqualTo(2)
    }
}
