/*
 * Copyright (c) 2014 - 2015 Ngewi Fet <ngewif@gmail.com>
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

package org.gnucash.android.db;

import android.database.sqlite.SQLiteDatabase;

import org.gnucash.android.R;
import org.gnucash.android.app.GnuCashApplication;
import org.gnucash.android.importer.CommoditiesXmlHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import timber.log.Timber;

/**
 * Collection of helper methods which are used during database migrations
 *
 * @author Ngewi Fet <ngewif@gmail.com>
 */
@SuppressWarnings("unused")
public class MigrationHelper {

    /**
     * Imports commodities into the database from XML resource file
     */
    static void importCommodities(SQLiteDatabase db) throws SAXException, ParserConfigurationException, IOException {
        SAXParserFactory spf = SAXParserFactory.newInstance();
        SAXParser sp = spf.newSAXParser();
        XMLReader xr = sp.getXMLReader();

        InputStream commoditiesInputStream = GnuCashApplication.getAppContext().getResources()
            .openRawResource(R.raw.iso_4217_currencies);
        BufferedInputStream bos = new BufferedInputStream(commoditiesInputStream);

        /* Create handler to handle XML Tags ( extends DefaultHandler ) */
        CommoditiesXmlHandler handler = new CommoditiesXmlHandler(db);

        xr.setContentHandler(handler);
        xr.parse(new InputSource(bos));
    }

    public static void migrate(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 16) {
            migrateTo16(db);
        }
    }

    /**
     * Upgrade the database to version 16.
     *
     * @param db the database.
     */
    private static void migrateTo16(SQLiteDatabase db) {
        Timber.i("Upgrading database to version 16");

        String sqlAddQuoteSource = "ALTER TABLE " + DatabaseSchema.CommodityEntry.TABLE_NAME +
            " ADD COLUMN " + DatabaseSchema.CommodityEntry.COLUMN_QUOTE_SOURCE + " varchar(255)";
        String sqlAddQuoteTZ = "ALTER TABLE " + DatabaseSchema.CommodityEntry.TABLE_NAME +
            " ADD COLUMN " + DatabaseSchema.CommodityEntry.COLUMN_QUOTE_TZ + " varchar(100)";

        try {
            db.beginTransaction();
            db.execSQL(sqlAddQuoteSource);
            db.execSQL(sqlAddQuoteTZ);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }
}
