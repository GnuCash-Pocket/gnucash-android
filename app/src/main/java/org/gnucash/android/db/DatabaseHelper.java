/*
 * Copyright (c) 2012 - 2015 Ngewi Fet <ngewif@gmail.com>
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

import static org.gnucash.android.db.DatabaseSchema.AccountEntry;
import static org.gnucash.android.db.DatabaseSchema.BudgetAmountEntry;
import static org.gnucash.android.db.DatabaseSchema.BudgetEntry;
import static org.gnucash.android.db.DatabaseSchema.CommodityEntry;
import static org.gnucash.android.db.DatabaseSchema.CommonColumns;
import static org.gnucash.android.db.DatabaseSchema.PriceEntry;
import static org.gnucash.android.db.DatabaseSchema.RecurrenceEntry;
import static org.gnucash.android.db.DatabaseSchema.ScheduledActionEntry;
import static org.gnucash.android.db.DatabaseSchema.SplitEntry;
import static org.gnucash.android.db.DatabaseSchema.TransactionEntry;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteOpenHelper;

import androidx.annotation.NonNull;

import org.gnucash.android.model.Commodity;
import org.xml.sax.SAXException;

import java.io.IOException;

import javax.xml.parsers.ParserConfigurationException;

import timber.log.Timber;

/**
 * Helper class for managing the SQLite database.
 * Creates the database and handles upgrades
 *
 * @author Ngewi Fet <ngewif@gmail.com>
 */
public class DatabaseHelper extends SQLiteOpenHelper {

    /**
     * SQL statement to create the accounts table in the database
     */
    private static final String ACCOUNTS_TABLE_CREATE = "create table " + AccountEntry.TABLE_NAME + " ("
        + AccountEntry._ID + " integer primary key autoincrement, "
        + AccountEntry.COLUMN_UID + " varchar(255) not null UNIQUE, "
        + AccountEntry.COLUMN_NAME + " varchar(255) not null, "
        + AccountEntry.COLUMN_TYPE + " varchar(255) not null, "
        + AccountEntry.COLUMN_CURRENCY + " varchar(255), "
        + AccountEntry.COLUMN_COMMODITY_UID + " varchar(255) not null, "
        + AccountEntry.COLUMN_DESCRIPTION + " varchar(255), "
        + AccountEntry.COLUMN_COLOR_CODE + " varchar(255), "
        + AccountEntry.COLUMN_FAVORITE + " tinyint default 0, "
        + AccountEntry.COLUMN_HIDDEN + " tinyint default 0, "
        + AccountEntry.COLUMN_FULL_NAME + " varchar(255), "
        + AccountEntry.COLUMN_PLACEHOLDER + " tinyint default 0, "
        + AccountEntry.COLUMN_PARENT_ACCOUNT_UID + " varchar(255), "
        + AccountEntry.COLUMN_DEFAULT_TRANSFER_ACCOUNT_UID + " varchar(255), "
        + AccountEntry.COLUMN_NOTES + " text, "
        + AccountEntry.COLUMN_BALANCE + " varchar(255), "
        + AccountEntry.COLUMN_CLEARED_BALANCE + " varchar(255), "
        + AccountEntry.COLUMN_NOCLOSING_BALANCE + " varchar(255), "
        + AccountEntry.COLUMN_RECONCILED_BALANCE + " varchar(255), "
        + AccountEntry.COLUMN_CREATED_AT + " TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, "
        + AccountEntry.COLUMN_MODIFIED_AT + " TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, "
        + "FOREIGN KEY (" + AccountEntry.COLUMN_COMMODITY_UID + ") REFERENCES " + CommodityEntry.TABLE_NAME + " (" + CommodityEntry.COLUMN_UID + ") "
        + ");"
        + createUpdatedAtTrigger(AccountEntry.TABLE_NAME);

    /**
     * SQL statement to create the transactions table in the database
     */
    private static final String TRANSACTIONS_TABLE_CREATE = "create table " + TransactionEntry.TABLE_NAME + " ("
        + TransactionEntry._ID + " integer primary key autoincrement, "
        + TransactionEntry.COLUMN_UID + " varchar(255) not null UNIQUE, "
        + TransactionEntry.COLUMN_DESCRIPTION + " varchar(255), "
        + TransactionEntry.COLUMN_NOTES + " text, "
        + TransactionEntry.COLUMN_TIMESTAMP + " integer not null, "
        + TransactionEntry.COLUMN_EXPORTED + " tinyint default 0, "
        + TransactionEntry.COLUMN_TEMPLATE + " tinyint default 0, "
        + TransactionEntry.COLUMN_CURRENCY + " varchar(255), "
        + TransactionEntry.COLUMN_COMMODITY_UID + " varchar(255) not null, "
        + TransactionEntry.COLUMN_SCHEDX_ACTION_UID + " varchar(255), "
        + TransactionEntry.COLUMN_CREATED_AT + " TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, "
        + TransactionEntry.COLUMN_MODIFIED_AT + " TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, "
        + "FOREIGN KEY (" + TransactionEntry.COLUMN_SCHEDX_ACTION_UID + ") REFERENCES " + ScheduledActionEntry.TABLE_NAME + " (" + ScheduledActionEntry.COLUMN_UID + ") ON DELETE SET NULL, "
        + "FOREIGN KEY (" + TransactionEntry.COLUMN_COMMODITY_UID + ") REFERENCES " + CommodityEntry.TABLE_NAME + " (" + CommodityEntry.COLUMN_UID + ") "
        + ");"
        + createUpdatedAtTrigger(TransactionEntry.TABLE_NAME);

    /**
     * SQL statement to create the transaction splits table
     */
    private static final String SPLITS_TABLE_CREATE = "CREATE TABLE " + SplitEntry.TABLE_NAME + " ("
        + SplitEntry._ID + " integer primary key autoincrement, "
        + SplitEntry.COLUMN_UID + " varchar(255) not null UNIQUE, "
        + SplitEntry.COLUMN_MEMO + " text, "
        + SplitEntry.COLUMN_TYPE + " varchar(255) not null, "
        + SplitEntry.COLUMN_VALUE_NUM + " integer not null, "
        + SplitEntry.COLUMN_VALUE_DENOM + " integer not null, "
        + SplitEntry.COLUMN_QUANTITY_NUM + " integer not null, "
        + SplitEntry.COLUMN_QUANTITY_DENOM + " integer not null, "
        + SplitEntry.COLUMN_ACCOUNT_UID + " varchar(255) not null, "
        + SplitEntry.COLUMN_TRANSACTION_UID + " varchar(255) not null, "
        + SplitEntry.COLUMN_RECONCILE_STATE + " varchar(1) not null default 'n', "
        + SplitEntry.COLUMN_RECONCILE_DATE + " timestamp not null default current_timestamp, "
        + SplitEntry.COLUMN_CREATED_AT + " TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, "
        + SplitEntry.COLUMN_MODIFIED_AT + " TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, "
        + "FOREIGN KEY (" + SplitEntry.COLUMN_ACCOUNT_UID + ") REFERENCES " + AccountEntry.TABLE_NAME + " (" + AccountEntry.COLUMN_UID + ") ON DELETE CASCADE, "
        + "FOREIGN KEY (" + SplitEntry.COLUMN_TRANSACTION_UID + ") REFERENCES " + TransactionEntry.TABLE_NAME + " (" + TransactionEntry.COLUMN_UID + ") ON DELETE CASCADE "
        + ");"
        + createUpdatedAtTrigger(SplitEntry.TABLE_NAME);


    private static final String SCHEDULED_ACTIONS_TABLE_CREATE = "CREATE TABLE " + ScheduledActionEntry.TABLE_NAME + " ("
        + ScheduledActionEntry._ID + " integer primary key autoincrement, "
        + ScheduledActionEntry.COLUMN_UID + " varchar(255) not null UNIQUE, "
        + ScheduledActionEntry.COLUMN_ACTION_UID + " varchar(255) not null, "
        + ScheduledActionEntry.COLUMN_TYPE + " varchar(255) not null, "
        + ScheduledActionEntry.COLUMN_RECURRENCE_UID + " varchar(255) not null, "
        + ScheduledActionEntry.COLUMN_TEMPLATE_ACCT_UID + " varchar(255) not null, "
        + ScheduledActionEntry.COLUMN_LAST_RUN + " integer default 0, "
        + ScheduledActionEntry.COLUMN_START_TIME + " integer not null, "
        + ScheduledActionEntry.COLUMN_END_TIME + " integer default 0, "
        + ScheduledActionEntry.COLUMN_TAG + " text, "
        + ScheduledActionEntry.COLUMN_ENABLED + " tinyint default 1, " //enabled by default
        + ScheduledActionEntry.COLUMN_AUTO_CREATE + " tinyint default 1, "
        + ScheduledActionEntry.COLUMN_AUTO_NOTIFY + " tinyint default 0, "
        + ScheduledActionEntry.COLUMN_ADVANCE_CREATION + " integer default 0, "
        + ScheduledActionEntry.COLUMN_ADVANCE_NOTIFY + " integer default 0, "
        + ScheduledActionEntry.COLUMN_TOTAL_FREQUENCY + " integer default 0, "
        + ScheduledActionEntry.COLUMN_EXECUTION_COUNT + " integer default 0, "
        + ScheduledActionEntry.COLUMN_CREATED_AT + " TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, "
        + ScheduledActionEntry.COLUMN_MODIFIED_AT + " TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, "
        + "FOREIGN KEY (" + ScheduledActionEntry.COLUMN_RECURRENCE_UID + ") REFERENCES " + RecurrenceEntry.TABLE_NAME + " (" + RecurrenceEntry.COLUMN_UID + ") "
        + ");"
        + createUpdatedAtTrigger(ScheduledActionEntry.TABLE_NAME);

    private static final String COMMODITIES_TABLE_CREATE = "CREATE TABLE " + DatabaseSchema.CommodityEntry.TABLE_NAME + " ("
        + CommodityEntry._ID + " integer primary key autoincrement, "
        + CommodityEntry.COLUMN_UID + " varchar(255) not null UNIQUE, "
        + CommodityEntry.COLUMN_NAMESPACE + " varchar(255) not null default '" + Commodity.COMMODITY_CURRENCY + "', "
        + CommodityEntry.COLUMN_FULLNAME + " varchar(255) not null, "
        + CommodityEntry.COLUMN_MNEMONIC + " varchar(255) not null, "
        + CommodityEntry.COLUMN_LOCAL_SYMBOL + " varchar(255) not null default '', "
        + CommodityEntry.COLUMN_CUSIP + " varchar(255), "
        + CommodityEntry.COLUMN_SMALLEST_FRACTION + " integer not null, "
        + CommodityEntry.COLUMN_QUOTE_SOURCE + " varchar(255), "
        + CommodityEntry.COLUMN_QUOTE_TZ + " varchar(100), "
        + CommodityEntry.COLUMN_CREATED_AT + " TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, "
        + CommodityEntry.COLUMN_MODIFIED_AT + " TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP "
        + ");"
        + createUpdatedAtTrigger(CommodityEntry.TABLE_NAME);

    /**
     * SQL statement to create the commodity prices table
     */
    private static final String PRICES_TABLE_CREATE = "CREATE TABLE " + PriceEntry.TABLE_NAME + " ("
        + PriceEntry._ID + " integer primary key autoincrement, "
        + PriceEntry.COLUMN_UID + " varchar(255) not null UNIQUE, "
        + PriceEntry.COLUMN_COMMODITY_UID + " varchar(255) not null, "
        + PriceEntry.COLUMN_CURRENCY_UID + " varchar(255) not null, "
        + PriceEntry.COLUMN_TYPE + " varchar(255), "
        + PriceEntry.COLUMN_DATE + " TIMESTAMP not null, "
        + PriceEntry.COLUMN_SOURCE + " text, "
        + PriceEntry.COLUMN_VALUE_NUM + " integer not null, "
        + PriceEntry.COLUMN_VALUE_DENOM + " integer not null, "
        + PriceEntry.COLUMN_CREATED_AT + " TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, "
        + PriceEntry.COLUMN_MODIFIED_AT + " TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, "
        + "UNIQUE (" + PriceEntry.COLUMN_COMMODITY_UID + ", " + PriceEntry.COLUMN_CURRENCY_UID + ") ON CONFLICT REPLACE, "
        + "FOREIGN KEY (" + PriceEntry.COLUMN_COMMODITY_UID + ") REFERENCES " + CommodityEntry.TABLE_NAME + " (" + CommodityEntry.COLUMN_UID + ") ON DELETE CASCADE, "
        + "FOREIGN KEY (" + PriceEntry.COLUMN_CURRENCY_UID + ") REFERENCES " + CommodityEntry.TABLE_NAME + " (" + CommodityEntry.COLUMN_UID + ") ON DELETE CASCADE "
        + ");"
        + createUpdatedAtTrigger(PriceEntry.TABLE_NAME);


    private static final String BUDGETS_TABLE_CREATE = "CREATE TABLE " + BudgetEntry.TABLE_NAME + " ("
        + BudgetEntry._ID + " integer primary key autoincrement, "
        + BudgetEntry.COLUMN_UID + " varchar(255) not null UNIQUE, "
        + BudgetEntry.COLUMN_NAME + " varchar(255) not null, "
        + BudgetEntry.COLUMN_DESCRIPTION + " varchar(255), "
        + BudgetEntry.COLUMN_RECURRENCE_UID + " varchar(255) not null, "
        + BudgetEntry.COLUMN_NUM_PERIODS + " integer, "
        + BudgetEntry.COLUMN_CREATED_AT + " TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, "
        + BudgetEntry.COLUMN_MODIFIED_AT + " TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, "
        + "FOREIGN KEY (" + BudgetEntry.COLUMN_RECURRENCE_UID + ") REFERENCES " + RecurrenceEntry.TABLE_NAME + " (" + RecurrenceEntry.COLUMN_UID + ") "
        + ");"
        + createUpdatedAtTrigger(BudgetEntry.TABLE_NAME);

    private static final String BUDGET_AMOUNTS_TABLE_CREATE = "CREATE TABLE " + BudgetAmountEntry.TABLE_NAME + " ("
        + BudgetAmountEntry._ID + " integer primary key autoincrement, "
        + BudgetAmountEntry.COLUMN_UID + " varchar(255) not null UNIQUE, "
        + BudgetAmountEntry.COLUMN_BUDGET_UID + " varchar(255) not null, "
        + BudgetAmountEntry.COLUMN_ACCOUNT_UID + " varchar(255) not null, "
        + BudgetAmountEntry.COLUMN_AMOUNT_NUM + " integer not null, "
        + BudgetAmountEntry.COLUMN_AMOUNT_DENOM + " integer not null, "
        + BudgetAmountEntry.COLUMN_PERIOD_NUM + " integer not null, "
        + BudgetAmountEntry.COLUMN_NOTES + " text, "
        + BudgetAmountEntry.COLUMN_CREATED_AT + " TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, "
        + BudgetAmountEntry.COLUMN_MODIFIED_AT + " TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, "
        + "FOREIGN KEY (" + BudgetAmountEntry.COLUMN_ACCOUNT_UID + ") REFERENCES " + AccountEntry.TABLE_NAME + " (" + AccountEntry.COLUMN_UID + ") ON DELETE CASCADE, "
        + "FOREIGN KEY (" + BudgetAmountEntry.COLUMN_BUDGET_UID + ") REFERENCES " + BudgetEntry.TABLE_NAME + " (" + BudgetEntry.COLUMN_UID + ") ON DELETE CASCADE "
        + ");"
        + createUpdatedAtTrigger(BudgetAmountEntry.TABLE_NAME);


    private static final String RECURRENCE_TABLE_CREATE = "CREATE TABLE " + RecurrenceEntry.TABLE_NAME + " ("
        + RecurrenceEntry._ID + " integer primary key autoincrement, "
        + RecurrenceEntry.COLUMN_UID + " varchar(255) not null UNIQUE, "
        + RecurrenceEntry.COLUMN_MULTIPLIER + " integer not null default 1, "
        + RecurrenceEntry.COLUMN_PERIOD_TYPE + " varchar(255) not null, "
        + RecurrenceEntry.COLUMN_BYDAY + " varchar(255), "
        + RecurrenceEntry.COLUMN_PERIOD_START + " timestamp not null, "
        + RecurrenceEntry.COLUMN_PERIOD_END + " timestamp, "
        + RecurrenceEntry.COLUMN_CREATED_AT + " TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, "
        + RecurrenceEntry.COLUMN_MODIFIED_AT + " TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP); "
        + createUpdatedAtTrigger(RecurrenceEntry.TABLE_NAME);


    /**
     * Constructor
     *
     * @param context      Application context
     * @param databaseName Name of the database
     */
    public DatabaseHelper(Context context, String databaseName) {
        super(context, databaseName, null, DatabaseSchema.DATABASE_VERSION);

    }

    /**
     * Creates an update trigger to update the updated_at column for all records in the database.
     * This has to be run per table, and is currently appended to the create table statement.
     *
     * @param tableName Name of table on which to create trigger
     * @return SQL statement for creating trigger
     */
    static String createUpdatedAtTrigger(String tableName) {
        return "CREATE TRIGGER update_time_trigger "
            + "  AFTER UPDATE ON " + tableName + " FOR EACH ROW"
            + "  BEGIN " + "UPDATE " + tableName
            + "  SET " + CommonColumns.COLUMN_MODIFIED_AT + " = CURRENT_TIMESTAMP"
            + "  WHERE OLD." + CommonColumns.COLUMN_UID + " = NEW." + CommonColumns.COLUMN_UID + ";"
            + "  END;";
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        createDatabaseTables(db);
    }

    @Override
    public void onOpen(SQLiteDatabase db) {
        super.onOpen(db);
        db.execSQL("PRAGMA foreign_keys=ON");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        MigrationHelper.migrate(db, oldVersion, newVersion);
    }

    /**
     * Creates the tables in the database and import default commodities into the database
     *
     * @param db Database instance
     */
    private void createDatabaseTables(SQLiteDatabase db) {
        Timber.i("Creating database tables");
        db.execSQL(ACCOUNTS_TABLE_CREATE);
        db.execSQL(TRANSACTIONS_TABLE_CREATE);
        db.execSQL(SPLITS_TABLE_CREATE);
        db.execSQL(SCHEDULED_ACTIONS_TABLE_CREATE);
        db.execSQL(COMMODITIES_TABLE_CREATE);
        db.execSQL(PRICES_TABLE_CREATE);
        db.execSQL(RECURRENCE_TABLE_CREATE);
        db.execSQL(BUDGETS_TABLE_CREATE);
        db.execSQL(BUDGET_AMOUNTS_TABLE_CREATE);

        String createAccountUidIndex = "CREATE UNIQUE INDEX '" + AccountEntry.INDEX_UID + "' ON "
            + AccountEntry.TABLE_NAME + "(" + AccountEntry.COLUMN_UID + ")";

        String createTransactionUidIndex = "CREATE UNIQUE INDEX '" + TransactionEntry.INDEX_UID + "' ON "
            + TransactionEntry.TABLE_NAME + "(" + TransactionEntry.COLUMN_UID + ")";

        String createSplitUidIndex = "CREATE UNIQUE INDEX '" + SplitEntry.INDEX_UID + "' ON "
            + SplitEntry.TABLE_NAME + "(" + SplitEntry.COLUMN_UID + ")";

        String createScheduledEventUidIndex = "CREATE UNIQUE INDEX '" + ScheduledActionEntry.INDEX_UID
            + "' ON " + ScheduledActionEntry.TABLE_NAME + "(" + ScheduledActionEntry.COLUMN_UID + ")";

        String createCommodityUidIndex = "CREATE UNIQUE INDEX '" + CommodityEntry.INDEX_UID
            + "' ON " + CommodityEntry.TABLE_NAME + "(" + CommodityEntry.COLUMN_UID + ")";

        String createPriceUidIndex = "CREATE UNIQUE INDEX '" + PriceEntry.INDEX_UID
            + "' ON " + PriceEntry.TABLE_NAME + "(" + PriceEntry.COLUMN_UID + ")";

        String createBudgetUidIndex = "CREATE UNIQUE INDEX '" + BudgetEntry.INDEX_UID
            + "' ON " + BudgetEntry.TABLE_NAME + "(" + BudgetEntry.COLUMN_UID + ")";

        String createBudgetAmountUidIndex = "CREATE UNIQUE INDEX '" + BudgetAmountEntry.INDEX_UID
            + "' ON " + BudgetAmountEntry.TABLE_NAME + "(" + BudgetAmountEntry.COLUMN_UID + ")";

        String createRecurrenceUidIndex = "CREATE UNIQUE INDEX '" + RecurrenceEntry.INDEX_UID
            + "' ON " + RecurrenceEntry.TABLE_NAME + "(" + RecurrenceEntry.COLUMN_UID + ")";

        db.execSQL(createAccountUidIndex);
        db.execSQL(createTransactionUidIndex);
        db.execSQL(createSplitUidIndex);
        db.execSQL(createScheduledEventUidIndex);
        db.execSQL(createCommodityUidIndex);
        db.execSQL(createPriceUidIndex);
        db.execSQL(createBudgetUidIndex);
        db.execSQL(createRecurrenceUidIndex);
        db.execSQL(createBudgetAmountUidIndex);
        createResetBalancesTriggers(db);

        try {
            MigrationHelper.importCommodities(db);
        } catch (SAXException | ParserConfigurationException | IOException e) {
            String msg = "Error loading currencies into the database";
            Timber.e(e, msg);
            throw new SQLiteException(msg, e);
        }
    }

    /**
     * Escape the given argument for use in a {@code LIKE} statement.
     *
     * @hide
     */
    public static String escapeForLike(@NonNull String arg) {
        // Shamelessly borrowed from android.database.DatabaseUtils
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < arg.length(); i++) {
            final char c = arg.charAt(i);
            switch (c) {
                case '%':
                case '_':
                    sb.append('\\');
                    break;
            }
            sb.append(c);
        }
        return sb.toString();
    }

    static void createResetBalancesTriggers(SQLiteDatabase db) {
        String sqlReset = "UPDATE " + AccountEntry.TABLE_NAME + " SET "
            + AccountEntry.COLUMN_BALANCE + " = NULL, "
            + AccountEntry.COLUMN_CLEARED_BALANCE + " = NULL, "
            + AccountEntry.COLUMN_NOCLOSING_BALANCE + " = NULL, "
            + AccountEntry.COLUMN_RECONCILED_BALANCE + " = NULL";

        String sqlWhenDelete = "CREATE TRIGGER reset_balances_delete_" + SplitEntry.TABLE_NAME
            + " AFTER DELETE ON " + SplitEntry.TABLE_NAME
            + " BEGIN " + sqlReset + "; END;";
        db.execSQL(sqlWhenDelete);

        String sqlWhenInsert = "CREATE TRIGGER reset_balances_insert_" + SplitEntry.TABLE_NAME
            + " AFTER INSERT ON " + SplitEntry.TABLE_NAME
            + " BEGIN " + sqlReset + "; END;";
        db.execSQL(sqlWhenInsert);

        String sqlWhenUpdate = "CREATE TRIGGER reset_balances_update_" + SplitEntry.TABLE_NAME
            + " AFTER UPDATE ON " + SplitEntry.TABLE_NAME
            + " BEGIN " + sqlReset + "; END;";
        db.execSQL(sqlWhenUpdate);

        // Expect that `reset_balances_delete_splits` trigger would be called
        // *but*
        // 'Triggers are not activated by foreign key actions.'
        String sqlWhenDeleteTx = "CREATE TRIGGER reset_balances_delete_" + TransactionEntry.TABLE_NAME
            + " AFTER DELETE ON " + TransactionEntry.TABLE_NAME
            + " BEGIN " + sqlReset + "; END;";
        db.execSQL(sqlWhenDeleteTx);

        String sqlWhenDeleteAccount = "CREATE TRIGGER reset_balances_delete_" + AccountEntry.TABLE_NAME
            + " AFTER DELETE ON " + AccountEntry.TABLE_NAME
            + " BEGIN " + sqlReset + "; END;";
        db.execSQL(sqlWhenDeleteAccount);

        String sqlWhenUpdateAccount = "CREATE TRIGGER reset_balances_update_" + AccountEntry.TABLE_NAME
            + " AFTER UPDATE OF "
            + AccountEntry.COLUMN_COMMODITY_UID + ", "
            + AccountEntry.COLUMN_PARENT_ACCOUNT_UID + ", "
            + AccountEntry.COLUMN_TYPE
            + " ON " + AccountEntry.TABLE_NAME
            + " BEGIN " + sqlReset + "; END;";
        db.execSQL(sqlWhenUpdateAccount);
    }
}
