package org.gnucash.android.test.unit.db

import org.assertj.core.api.Assertions.assertThat
import org.gnucash.android.model.Commodity
import org.gnucash.android.model.Price
import org.junit.Test
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale

/**
 * Test price functions
 */
class PriceDbAdapterTest : DatabaseTest() {
    /**
     * The price table should not override price for any commodity/currency pair
     */
    @Test
    fun `price is not unique per security-commodity pair`() {
        val security = commoditiesDbAdapter.getCurrency("EUR")!!
        val currency = commoditiesDbAdapter.getCurrency("USD")!!

        val price1 = Price(security, currency)
        price1.valueNum = 1340
        price1.valueDenom = 1000
        //the price is reduced
        assertThat(price1.valueNum).isEqualTo(67)
        assertThat(price1.valueDenom).isEqualTo(50)

        pricesDbAdapter.addRecord(price1)
        assertThat(pricesDbAdapter.recordsCount).isOne()

        val price2 = Price(security, currency)
        price2.valueNum = 187
        price2.valueDenom = 100
        //the price is reduced
        assertThat(price2.valueNum).isEqualTo(187)
        assertThat(price2.valueDenom).isEqualTo(100)

        pricesDbAdapter.addRecord(price2)
        assertThat(pricesDbAdapter.recordsCount).isEqualTo(2)

        val savedPrice1 = pricesDbAdapter.allRecords[0]
        assertThat(savedPrice1.uid).isEqualTo(price1.uid)
        assertThat(savedPrice1.valueNum).isEqualTo(67)
        assertThat(savedPrice1.valueDenom).isEqualTo(50)

        val savedPrice2 = pricesDbAdapter.allRecords[1]
        assertThat(savedPrice1.uid).isNotEqualTo(savedPrice2.uid) //different records
        assertThat(savedPrice2.uid).isEqualTo(price2.uid)
        assertThat(savedPrice2.valueNum).isEqualTo(187)
        assertThat(savedPrice2.valueDenom).isEqualTo(100)
        assertThat(savedPrice2.security).isEqualTo(savedPrice1.security)
        assertThat(savedPrice2.currency).isEqualTo(savedPrice1.currency)
        assertThat(savedPrice2.date).isGreaterThanOrEqualTo(savedPrice1.date)

        val price3 = Price(currency, security)
        price3.valueNum = 190
        price3.valueDenom = 100
        pricesDbAdapter.addRecord(price3)

        assertThat(pricesDbAdapter.recordsCount).isEqualTo(3)
    }

    @Test
    fun inverse() {
        Locale.setDefault(Locale.US)
        val commodity1 = Commodity.USD
        val commodity2 = Commodity.EUR

        val rate = BigDecimal(1.17)
        val price = Price(commodity2, commodity1, rate) // 1 EUR = 1.17 USD
        assertThat(price.toBigDecimal(2)).isEqualTo(rate.setScale(2, RoundingMode.HALF_UP))
        pricesDbAdapter.addRecord(price)

        val price12 = pricesDbAdapter.getPrice(commodity1, commodity2)
        assertThat(price12!!).isNotNull
        assertThat(price12.security).isEqualTo(commodity1)
        assertThat(price12.currency).isEqualTo(commodity2)
        assertThat(price12.toBigDecimal(3)).isEqualTo(BigDecimal(0.855).setScale(3, RoundingMode.HALF_UP))

        val price21 = pricesDbAdapter.getPrice(commodity2, commodity1)
        assertThat(price21!!).isNotNull
        assertThat(price21.security).isEqualTo(commodity2)
        assertThat(price21.currency).isEqualTo(commodity1)
        assertThat(price21.toBigDecimal(2)).isEqualTo(rate.setScale(2, RoundingMode.HALF_UP))
    }
}
