package org.gnucash.android.test.unit.db;

import static org.assertj.core.api.Assertions.assertThat;

import org.gnucash.android.db.adapter.CommoditiesDbAdapter;
import org.gnucash.android.db.adapter.PricesDbAdapter;
import org.gnucash.android.model.Commodity;
import org.gnucash.android.model.Price;
import org.gnucash.android.test.unit.GnuCashTest;
import org.junit.Test;

/**
 * Test price functions
 */
public class PriceDbAdapterTest extends GnuCashTest {

    /**
     * The price table should override price for any commodity/currency pair
     * todo: maybe move this to UI testing. Not sure how Robolectric handles this
     */
    @Test
    public void shouldOnlySaveOnePricePerCommodityPair() {
        Commodity commodity = CommoditiesDbAdapter.getInstance().getCommodity("EUR");
        Commodity currency = CommoditiesDbAdapter.getInstance().getCommodity("USD");
        Price price = new Price(commodity, currency);
        price.setValueNum(134);
        price.setValueDenom(100);

        PricesDbAdapter pricesDbAdapter = PricesDbAdapter.getInstance();
        pricesDbAdapter.addRecord(price);

        price = pricesDbAdapter.getRecord(price.getUID());
        assertThat(pricesDbAdapter.getRecordsCount()).isEqualTo(1);
        assertThat(price.getValueNum()).isEqualTo(67); //the price is reduced to 57/100 before saving

        Price price1 = new Price(commodity, currency);
        price1.setValueNum(187);
        price1.setValueDenom(100);
        pricesDbAdapter.addRecord(price1);

        assertThat(pricesDbAdapter.getRecordsCount()).isEqualTo(1);
        Price savedPrice = pricesDbAdapter.getAllRecords().get(0);
        assertThat(savedPrice.getUID()).isEqualTo(price1.getUID()); //different records
        assertThat(savedPrice.getValueNum()).isEqualTo(187);
        assertThat(savedPrice.getValueDenom()).isEqualTo(100);


        Price price2 = new Price(currency, commodity);
        price2.setValueNum(190);
        price2.setValueDenom(100);
        pricesDbAdapter.addRecord(price2);

        assertThat(pricesDbAdapter.getRecordsCount()).isEqualTo(2);
    }
}
