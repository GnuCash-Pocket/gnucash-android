package org.gnucash.android.test.unit.db

import org.assertj.core.api.Assertions.assertThat
import org.gnucash.android.db.DatabaseHelper
import org.gnucash.android.db.MigrationHelper
import org.gnucash.android.db.adapter.CommoditiesDbAdapter
import org.gnucash.android.model.Commodity
import org.gnucash.android.test.unit.GnuCashTest
import org.junit.Test

class CommoditiesDbAdapterTest : GnuCashTest() {
    @Test
    fun parseCurrencies() {
        val helper = DatabaseHelper(context, "test")
        val holder = helper.holder
        MigrationHelper.importCommodities(holder)

        val adapter = CommoditiesDbAdapter(holder)

        val currency = adapter.getCurrency("USD")!!
        assertThat(currency.id).isNotZero()
        assertThat(currency.currencyCode).isEqualTo("USD")
        assertThat(currency.mnemonic).isEqualTo("USD")
        assertThat(currency.fullname).isEqualTo("US Dollar")
        assertThat(currency.namespace).isEqualTo(Commodity.COMMODITY_CURRENCY)
        assertThat(currency.cusip).isEqualTo("840")
        assertThat(currency.smallestFraction).isEqualTo(100)
        assertThat(currency.localSymbol).isEqualTo("$")

        val currencyByUID = adapter.getRecord(currency.uid)
        assertThat(currency).isEqualTo(currencyByUID)
    }
}