/*
 * Copyright (c) 2015 Ngewi Fet <ngewif@gmail.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gnucash.android.importer;

import android.database.sqlite.SQLiteDatabase;

import androidx.annotation.Nullable;

import org.gnucash.android.app.GnuCashApplication;
import org.gnucash.android.db.adapter.CommoditiesDbAdapter;
import org.gnucash.android.db.adapter.DatabaseAdapter;
import org.gnucash.android.model.Commodity;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * XML stream handler for parsing currencies to add to the database
 */
public class CommoditiesXmlHandler extends DefaultHandler {

    public static final String TAG_CURRENCY = "currency";
    public static final String ATTR_ISO_CODE = "isocode";
    public static final String ATTR_FULL_NAME = "fullname";
    public static final String ATTR_NAMESPACE = "namespace";
    public static final String ATTR_EXCHANGE_CODE = "exchange-code";
    public static final String ATTR_SMALLEST_FRACTION = "smallest-fraction";
    public static final String ATTR_LOCAL_SYMBOL = "local-symbol";
    private static final String SOURCE_CURRENCY = "currency";
    /**
     * List of commodities parsed from the XML file.
     * They will be all added to db at once at the end of the document
     */
    private final Map<String, Commodity> commodities = new TreeMap<>();

    private final CommoditiesDbAdapter mCommoditiesDbAdapter;

    public CommoditiesXmlHandler(@Nullable SQLiteDatabase db) {
        if (db == null) {
            mCommoditiesDbAdapter = GnuCashApplication.getCommoditiesDbAdapter();
        } else {
            mCommoditiesDbAdapter = new CommoditiesDbAdapter(db, false);
        }
    }

    @Override
    public void startDocument() throws SAXException {
        super.startDocument();
        List<Commodity> commoditiesDb = mCommoditiesDbAdapter.getAllRecords();
        commodities.clear();
        for (Commodity commodity : commoditiesDb) {
            String key = commodity.getNamespace() + "::" + commodity.getMnemonic();;
            commodities.put(key, commodity);
        }
    }

    @Override
    public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
        if (qName.equals(TAG_CURRENCY)) {
            String isoCode = attributes.getValue(ATTR_ISO_CODE);
            String fullname = attributes.getValue(ATTR_FULL_NAME);
            String namespace = attributes.getValue(ATTR_NAMESPACE);
            String cusip = attributes.getValue(ATTR_EXCHANGE_CODE);
            //TODO: investigate how up-to-date the currency XML list is and use of parts-per-unit vs smallest-fraction.
            //some currencies like XAF have smallest fraction 100, but parts-per-unit of 1.
            // However java.util.Currency agrees only with the parts-per-unit although we use smallest-fraction in the app
            // This could lead to inconsistencies over time
            String smallestFraction = attributes.getValue(ATTR_SMALLEST_FRACTION);
            String localSymbol = attributes.getValue(ATTR_LOCAL_SYMBOL);

            if (Commodity.COMMODITY_ISO4217.equals(namespace)) {
                namespace = Commodity.COMMODITY_CURRENCY;
            }
            String key = namespace + "::" + isoCode;
            Commodity commodity = commodities.get(key);
            if (commodity == null) {
                commodity = new Commodity(fullname, isoCode, Integer.parseInt(smallestFraction));
                commodity.setNamespace(namespace);
                commodities.put(key, commodity);
            } else {
                commodity.setFullname(fullname);
                commodity.setSmallestFraction(Integer.parseInt(smallestFraction));
            }
            commodity.setCusip(cusip);
            commodity.setLocalSymbol(localSymbol);
            commodity.setQuoteSource(SOURCE_CURRENCY);
        }
    }

    @Override
    public void endDocument() {
        List<Commodity> records = new ArrayList<>(commodities.values());
        mCommoditiesDbAdapter.bulkAddRecords(records, DatabaseAdapter.UpdateMethod.replace);
        mCommoditiesDbAdapter.initCommon();
    }
}
