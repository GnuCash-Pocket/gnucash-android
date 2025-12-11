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
package org.gnucash.android.importer.xml

import org.gnucash.android.db.DatabaseHolder
import org.gnucash.android.db.adapter.CommoditiesDbAdapter
import org.gnucash.android.model.Commodity
import org.xml.sax.Attributes
import org.xml.sax.SAXException
import org.xml.sax.helpers.DefaultHandler
import java.util.TreeMap

/**
 * XML stream handler for parsing currencies to add to the database
 */
class CommoditiesXmlHandler(holder: DatabaseHolder) : DefaultHandler() {
    /**
     * List of commodities parsed from the XML file.
     * They will be all added to db at once at the end of the document
     */
    private val commodities: MutableMap<String, Commodity> = TreeMap<String, Commodity>()

    private val commoditiesDbAdapter: CommoditiesDbAdapter = CommoditiesDbAdapter(holder)

    @Throws(SAXException::class)
    override fun startDocument() {
        super.startDocument()
        val commoditiesDb = commoditiesDbAdapter.allRecords
        commodities.clear()
        for (commodity in commoditiesDb) {
            val key = "${commodity.namespace}::${commodity.mnemonic}"
            commodities[key] = commodity
        }
    }

    @Throws(SAXException::class)
    override fun startElement(
        uri: String?,
        localName: String?,
        qualifiedName: String,
        attributes: Attributes
    ) {
        if (qualifiedName == TAG_CURRENCY) {
            val isoCode: String = attributes.getValue(ATTR_ISO_CODE)
            val fullname: String = attributes.getValue(ATTR_FULL_NAME)
            var namespace: String = attributes.getValue(ATTR_NAMESPACE)
            val cusip: String = attributes.getValue(ATTR_EXCHANGE_CODE)
            //TODO: investigate how up-to-date the currency XML list is and use of parts-per-unit vs smallest-fraction.
            //some currencies like XAF have smallest fraction 100, but parts-per-unit of 1.
            // However java.util.Currency agrees only with the parts-per-unit although we use smallest-fraction in the app
            // This could lead to inconsistencies over time
            val smallestFraction: String = attributes.getValue(ATTR_SMALLEST_FRACTION)
            val localSymbol: String = attributes.getValue(ATTR_LOCAL_SYMBOL)

            if (Commodity.COMMODITY_ISO4217 == namespace) {
                namespace = Commodity.COMMODITY_CURRENCY
            }
            val key = "$namespace::$isoCode"
            var commodity = commodities[key]
            if (commodity == null) {
                commodity = Commodity(fullname, isoCode, namespace, smallestFraction.toInt())
                commodity.namespace = namespace
                commodities[key] = commodity
            } else {
                commodity.fullname = fullname
                commodity.smallestFraction = smallestFraction.toInt()
            }
            commodity.cusip = cusip
            commodity.localSymbol = localSymbol
            commodity.quoteSource = SOURCE_CURRENCY
            commoditiesDbAdapter.replace(commodity)
        }
    }

    override fun endDocument() {
        commoditiesDbAdapter.initCommon()
    }

    companion object {
        const val TAG_CURRENCY: String = "currency"
        const val ATTR_ISO_CODE: String = "isocode"
        const val ATTR_FULL_NAME: String = "fullname"
        const val ATTR_NAMESPACE: String = "namespace"
        const val ATTR_EXCHANGE_CODE: String = "exchange-code"
        const val ATTR_SMALLEST_FRACTION: String = "smallest-fraction"
        const val ATTR_LOCAL_SYMBOL: String = "local-symbol"
        const val SOURCE_CURRENCY: String = "currency"
    }
}
