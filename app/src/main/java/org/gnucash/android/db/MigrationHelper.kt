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
package org.gnucash.android.db

import android.content.Context
import android.database.DatabaseUtils.sqlEscapeString
import android.database.SQLException
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import androidx.annotation.VisibleForTesting
import org.gnucash.android.R
import org.gnucash.android.db.DatabaseSchema.AccountEntry
import org.gnucash.android.db.DatabaseSchema.BudgetAmountEntry
import org.gnucash.android.db.DatabaseSchema.CommodityEntry
import org.gnucash.android.db.DatabaseSchema.ScheduledActionEntry
import org.gnucash.android.db.DatabaseSchema.SplitEntry
import org.gnucash.android.db.DatabaseSchema.TransactionEntry
import org.gnucash.android.importer.CommoditiesXmlHandler
import org.gnucash.android.model.Commodity
import org.xml.sax.InputSource
import org.xml.sax.SAXException
import timber.log.Timber
import java.io.IOException
import java.io.InputStream
import javax.xml.parsers.ParserConfigurationException
import javax.xml.parsers.SAXParserFactory

/**
 * Collection of helper methods which are used during database migrations
 *
 * @author Ngewi Fet <ngewif@gmail.com>
 */
object MigrationHelper {
    /**
     * Imports commodities into the database from XML resource file
     */
    @VisibleForTesting
    @Throws(SAXException::class, ParserConfigurationException::class, IOException::class)
    fun importCommodities(holder: DatabaseHolder) {
        val parserFactory = SAXParserFactory.newInstance()
        val parser = parserFactory.newSAXParser()
        val reader = parser.xmlReader

        val inputStream: InputStream = holder.context.resources
            .openRawResource(R.raw.iso_4217_currencies)

        /* Create handler to handle XML Tags ( extends DefaultHandler ) */
        val handler = CommoditiesXmlHandler(holder)

        reader.contentHandler = handler
        reader.parse(InputSource(inputStream))
    }

    fun migrate(context: Context, db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 16) {
            migrateTo16(db)
        }
        if (oldVersion < 17) {
            migrateTo17(db)
        }
        if (oldVersion < 18) {
            migrateTo18(db)
        }
        if (oldVersion < 19) {
            migrateTo19(db)
        }
        if ((oldVersion >= 19) && (oldVersion < 21)) {
            migrateTo21(db)
        }
        if (oldVersion < 23) {
            migrateTo23(context, db)
        }
        if (oldVersion < 24) {
            migrateTo24(db)
        }
        if (oldVersion < 25) {
            migrateTo25(db)
        }
    }

    /**
     * Upgrade the database to version 16.
     *
     * @param db the database.
     */
    private fun migrateTo16(db: SQLiteDatabase) {
        Timber.i("Upgrading database to version 16")

        val sqlAddQuoteSource = "ALTER TABLE " + CommodityEntry.TABLE_NAME +
                " ADD COLUMN " + CommodityEntry.COLUMN_QUOTE_SOURCE + " varchar(255)"
        val sqlAddQuoteTZ = "ALTER TABLE " + CommodityEntry.TABLE_NAME +
                " ADD COLUMN " + CommodityEntry.COLUMN_QUOTE_TZ + " varchar(100)"

        db.execSQL(sqlAddQuoteSource)
        db.execSQL(sqlAddQuoteTZ)
    }

    /**
     * Upgrade the database to version 17.
     *
     * @param db the database.
     */
    private fun migrateTo17(db: SQLiteDatabase) {
        Timber.i("Upgrading database to version 17")

        val sqlAddBudgetNotes = "ALTER TABLE " + BudgetAmountEntry.TABLE_NAME +
                " ADD COLUMN " + BudgetAmountEntry.COLUMN_NOTES + " text"

        db.execSQL(sqlAddBudgetNotes)
    }

    /**
     * Upgrade the database to version 18.
     *
     * @param db the database.
     */
    private fun migrateTo18(db: SQLiteDatabase) {
        Timber.i("Upgrading database to version 18")

        val sqlAddNotes = ("ALTER TABLE " + AccountEntry.TABLE_NAME
                + " ADD COLUMN " + AccountEntry.COLUMN_NOTES + " text")
        val sqlAddBalance = ("ALTER TABLE " + AccountEntry.TABLE_NAME
                + " ADD COLUMN " + AccountEntry.COLUMN_BALANCE + " varchar(255)")
        val sqlAddClearedBalance = ("ALTER TABLE " + AccountEntry.TABLE_NAME
                + " ADD COLUMN " + AccountEntry.COLUMN_CLEARED_BALANCE + " varchar(255)")
        val sqlAddNoClosingBalance = ("ALTER TABLE " + AccountEntry.TABLE_NAME
                + " ADD COLUMN " + AccountEntry.COLUMN_NOCLOSING_BALANCE + " varchar(255)")
        val sqlAddReconciledBalance = ("ALTER TABLE " + AccountEntry.TABLE_NAME
                + " ADD COLUMN " + AccountEntry.COLUMN_RECONCILED_BALANCE + " varchar(255)")

        db.execSQL(sqlAddNotes)
        db.execSQL(sqlAddBalance)
        db.execSQL(sqlAddClearedBalance)
        db.execSQL(sqlAddNoClosingBalance)
        db.execSQL(sqlAddReconciledBalance)
        DatabaseHelper.createResetBalancesTriggers(db)
    }

    /**
     * Upgrade the database to version 19.
     *
     * @param db the database.
     */
    private fun migrateTo19(db: SQLiteDatabase) {
        Timber.i("Upgrading database to version 19")

        // Fetch list of accounts with mismatched currencies.
        val sqlAccountCurrencyWrong =
            "SELECT DISTINCT a." + AccountEntry.COLUMN_CURRENCY + ", a." + AccountEntry.COLUMN_COMMODITY_UID + ", c." + CommodityEntry.COLUMN_UID +
                    " FROM " + AccountEntry.TABLE_NAME + " a, " + CommodityEntry.TABLE_NAME + " c" +
                    " WHERE a." + AccountEntry.COLUMN_CURRENCY + " = c." + CommodityEntry.COLUMN_MNEMONIC +
                    " AND (c." + CommodityEntry.COLUMN_NAMESPACE + " = " + sqlEscapeString(Commodity.COMMODITY_CURRENCY) +
                    " OR c." + CommodityEntry.COLUMN_NAMESPACE + " = " + sqlEscapeString(Commodity.COMMODITY_ISO4217) + ")" +
                    " AND a." + AccountEntry.COLUMN_COMMODITY_UID + " != c." + CommodityEntry.COLUMN_UID
        val cursor = db.rawQuery(sqlAccountCurrencyWrong, null)
        val accountsWrong = mutableListOf<AccountCurrency>()
        if (cursor.moveToFirst()) {
            do {
                val currencyCode = cursor.getString(0)
                val commodityUIDOld = cursor.getString(1)
                val commodityUIDNew = cursor.getString(2)
                accountsWrong.add(AccountCurrency(currencyCode, commodityUIDOld, commodityUIDNew))
            } while (cursor.moveToNext())
        }
        cursor.close()

        // Update with correct commodities.
        for (accountWrong in accountsWrong) {
            val sql = "UPDATE " + AccountEntry.TABLE_NAME +
                    " SET " + AccountEntry.COLUMN_COMMODITY_UID + " = ${sqlEscapeString(accountWrong.commodityUIDNew)}" +
                    " WHERE " + AccountEntry.COLUMN_CURRENCY + " = ${sqlEscapeString(accountWrong.currencyCode)}" +
                    " AND " + AccountEntry.COLUMN_COMMODITY_UID + " = ${sqlEscapeString(accountWrong.commodityUIDOld)}"
            db.execSQL(sql)
        }
    }

    /**
     * Upgrade the database to version 21.
     *
     * @param db the database.
     */
    private fun migrateTo21(db: SQLiteDatabase) {
        Timber.i("Upgrading database to version 21")

        if (DatabaseHelper.hasTableColumn(
                db,
                AccountEntry.TABLE_NAME,
                AccountEntry.COLUMN_CURRENCY
            )
        ) {
            return
        }

        // Restore the currency code column that was deleted in v19.
        val sqlAccountCurrency =
            "ALTER TABLE " + AccountEntry.TABLE_NAME + " ADD COLUMN " + AccountEntry.COLUMN_CURRENCY + " varchar(255)"
        try {
            db.execSQL(sqlAccountCurrency)
        } catch (e: SQLException) {
            Timber.e(e)
        }

        // Restore the currency code column.
        val sqlTransactionCurrency =
            "ALTER TABLE " + TransactionEntry.TABLE_NAME + " ADD COLUMN " + TransactionEntry.COLUMN_CURRENCY + " varchar(255)"
        try {
            db.execSQL(sqlTransactionCurrency)
        } catch (e: SQLException) {
            Timber.e(e)
        }
    }

    /**
     * Upgrade the database to version 23.
     *
     * @param context the context.
     * @param db      the database.
     */
    private fun migrateTo23(context: Context, db: SQLiteDatabase) {
        Timber.i("Upgrading database to version 23")

        val hasColumnQuoteFlag = DatabaseHelper.hasTableColumn(
            db,
            CommodityEntry.TABLE_NAME,
            CommodityEntry.COLUMN_QUOTE_FLAG
        )
        if (!hasColumnQuoteFlag) {
            // Restore the currency code column that was deleted in v19.
            val sqlCommodityFlag =
                "ALTER TABLE " + CommodityEntry.TABLE_NAME + " ADD COLUMN " + CommodityEntry.COLUMN_QUOTE_FLAG + " tinyint default 0"
            try {
                db.execSQL(sqlCommodityFlag)
            } catch (e: SQLException) {
                Timber.e(e)
            }
        }

        try {
            val holder = DatabaseHolder(context, db)
            importCommodities(holder)
        } catch (e: SAXException) {
            val msg = "Error loading currencies into the database"
            Timber.e(e, msg)
            throw SQLiteException(msg, e)
        } catch (e: ParserConfigurationException) {
            val msg = "Error loading currencies into the database"
            Timber.e(e, msg)
            throw SQLiteException(msg, e)
        } catch (e: IOException) {
            val msg = "Error loading currencies into the database"
            Timber.e(e, msg)
            throw SQLiteException(msg, e)
        }
    }

    /**
     * Upgrade the database to version 24.
     *
     * @param db the database.
     */
    private fun migrateTo24(db: SQLiteDatabase) {
        Timber.i("Upgrading database to version 24")

        if (!DatabaseHelper.hasTableColumn(
                db,
                AccountEntry.TABLE_NAME,
                AccountEntry.COLUMN_TEMPLATE
            )
        ) {
            val sqlAccountTemplate = "ALTER TABLE " + AccountEntry.TABLE_NAME +
                    " ADD COLUMN " + AccountEntry.COLUMN_TEMPLATE + " tinyint default 0"
            db.execSQL(sqlAccountTemplate)
        }

        if (!DatabaseHelper.hasTableColumn(
                db,
                SplitEntry.TABLE_NAME,
                SplitEntry.COLUMN_SCHEDX_ACTION_ACCOUNT_UID
            )
        ) {
            val sqlAddSchedxActionAccount = "ALTER TABLE " + SplitEntry.TABLE_NAME +
                    " ADD COLUMN " + SplitEntry.COLUMN_SCHEDX_ACTION_ACCOUNT_UID + " varchar(255)"
            db.execSQL(sqlAddSchedxActionAccount)
        }
    }

    /**
     * Upgrade the database to version 25.
     *
     * @param db the database.
     */
    private fun migrateTo25(db: SQLiteDatabase) {
        Timber.i("Upgrading database to version 25")

        if (!DatabaseHelper.hasTableColumn(
                db,
                ScheduledActionEntry.TABLE_NAME,
                ScheduledActionEntry.COLUMN_NAME
            )
        ) {
            val sqlActionName = "ALTER TABLE " + ScheduledActionEntry.TABLE_NAME +
                    " ADD COLUMN " + ScheduledActionEntry.COLUMN_NAME + " varchar(255)"
            db.execSQL(sqlActionName)
        }
    }

    private class AccountCurrency(
        val currencyCode: String,
        val commodityUIDOld: String,
        val commodityUIDNew: String
    )
}