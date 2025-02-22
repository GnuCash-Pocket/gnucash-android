package org.gnucash.android.db.adapter;

import static org.gnucash.android.db.DatabaseSchema.CommodityEntry;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.gnucash.android.app.GnuCashApplication;
import org.gnucash.android.db.DatabaseSchema;
import org.gnucash.android.model.Commodity;

import java.util.Objects;

import timber.log.Timber;

/**
 * Database adapter for {@link org.gnucash.android.model.Commodity}
 */
public class CommoditiesDbAdapter extends DatabaseAdapter<Commodity> {
    /**
     * Opens the database adapter with an existing database
     *
     * @param db SQLiteDatabase object
     */
    public CommoditiesDbAdapter(@NonNull SQLiteDatabase db) {
        this(db, true);
    }

    /**
     * Opens the database adapter with an existing database
     *
     * @param db         SQLiteDatabase object
     * @param initCommon initialize commonly used commodities?
     */
    public CommoditiesDbAdapter(@NonNull SQLiteDatabase db, boolean initCommon) {
        super(db, CommodityEntry.TABLE_NAME, new String[]{
            CommodityEntry.COLUMN_FULLNAME,
            CommodityEntry.COLUMN_NAMESPACE,
            CommodityEntry.COLUMN_MNEMONIC,
            CommodityEntry.COLUMN_LOCAL_SYMBOL,
            CommodityEntry.COLUMN_CUSIP,
            CommodityEntry.COLUMN_SMALLEST_FRACTION,
            CommodityEntry.COLUMN_QUOTE_SOURCE,
            CommodityEntry.COLUMN_QUOTE_TZ
        });
        if (initCommon) {
            initCommon();
        }
    }

    /**
     * initialize commonly used commodities
     */
    public void initCommon() {
        Commodity.AUD = Objects.requireNonNull(getCommodity("AUD"));
        Commodity.CAD = Objects.requireNonNull(getCommodity("CAD"));
        Commodity.CHF = Objects.requireNonNull(getCommodity("CHF"));
        Commodity.EUR = Objects.requireNonNull(getCommodity("EUR"));
        Commodity.GBP = Objects.requireNonNull(getCommodity("GBP"));
        Commodity.JPY = Objects.requireNonNull(getCommodity("JPY"));
        Commodity.USD = Objects.requireNonNull(getCommodity("USD"));

        Commodity.DEFAULT_COMMODITY = getDefaultCommodity();
    }

    @Nullable
    public static CommoditiesDbAdapter getInstance() {
        return GnuCashApplication.getCommoditiesDbAdapter();
    }

    @Override
    protected @NonNull SQLiteStatement bind(@NonNull SQLiteStatement stmt, @NonNull final Commodity commodity) {
        stmt.clearBindings();
        stmt.bindString(1, commodity.getFullname());
        stmt.bindString(2, commodity.getNamespace());
        stmt.bindString(3, commodity.getMnemonic());
        if (commodity.getLocalSymbol() != null) {
            stmt.bindString(4, commodity.getLocalSymbol());
        } else {
            stmt.bindNull(4);
        }
        if (commodity.getCusip() != null) {
            stmt.bindString(5, commodity.getCusip());
        } else {
            stmt.bindNull(5);
        }
        stmt.bindLong(6, commodity.getSmallestFraction());
        if (commodity.getQuoteSource() != null) {
            stmt.bindString(7, commodity.getQuoteSource());
        } else {
            stmt.bindNull(7);
        }
        if (commodity.getQuoteTimeZoneId() != null) {
            stmt.bindString(8, commodity.getQuoteTimeZoneId());
        } else {
            stmt.bindNull(8);
        }
        stmt.bindString(9, commodity.getUID());

        return stmt;
    }

    @Override
    public Commodity buildModelInstance(@NonNull final Cursor cursor) {
        String fullname = cursor.getString(cursor.getColumnIndexOrThrow(CommodityEntry.COLUMN_FULLNAME));
        String mnemonic = cursor.getString(cursor.getColumnIndexOrThrow(CommodityEntry.COLUMN_MNEMONIC));
        String namespace = cursor.getString(cursor.getColumnIndexOrThrow(CommodityEntry.COLUMN_NAMESPACE));
        String cusip = cursor.getString(cursor.getColumnIndexOrThrow(CommodityEntry.COLUMN_CUSIP));
        String localSymbol = cursor.getString(cursor.getColumnIndexOrThrow(CommodityEntry.COLUMN_LOCAL_SYMBOL));

        int fraction = cursor.getInt(cursor.getColumnIndexOrThrow(CommodityEntry.COLUMN_SMALLEST_FRACTION));
        String quoteSource = cursor.getString(cursor.getColumnIndexOrThrow(CommodityEntry.COLUMN_QUOTE_SOURCE));
        String quoteTZ = cursor.getString(cursor.getColumnIndexOrThrow(CommodityEntry.COLUMN_QUOTE_TZ));

        Commodity commodity = new Commodity(fullname, mnemonic, fraction);
        populateBaseModelAttributes(cursor, commodity);
        commodity.setNamespace(namespace);
        commodity.setCusip(cusip);
        commodity.setQuoteSource(quoteSource);
        commodity.setQuoteTimeZone(quoteTZ);
        commodity.setLocalSymbol(localSymbol);

        return commodity;
    }

    @Override
    public Cursor fetchAllRecords() {
        return fetchAllRecords(CommodityEntry.COLUMN_MNEMONIC + " ASC");
    }

    /**
     * Fetches all commodities in the database sorted in the specified order
     *
     * @param orderBy SQL statement for orderBy without the ORDER_BY itself
     * @return Cursor holding all commodity records
     */
    public Cursor fetchAllRecords(String orderBy) {
        return mDb.query(mTableName, null, null, null, null, null, orderBy);
    }

    /**
     * Returns the commodity associated with the ISO4217 currency code
     *
     * @param currencyCode 3-letter currency code
     * @return Commodity associated with code or null if none is found
     */
    @Nullable
    public Commodity getCommodity(String currencyCode) {
        if (TextUtils.isEmpty(currencyCode)) {
            return null;
        }
        Cursor cursor = fetchAllRecords(CommodityEntry.COLUMN_MNEMONIC + "=?", new String[]{currencyCode}, null);
        Commodity commodity = null;
        if (cursor.moveToNext()) {
            commodity = buildModelInstance(cursor);
        } else {
            String msg = "Commodity not found in the database: " + currencyCode;
            Timber.e(msg);
        }
        cursor.close();
        return commodity;
    }

    public String getCurrencyCode(@NonNull String guid) {
        Cursor cursor = mDb.query(mTableName, new String[]{CommodityEntry.COLUMN_MNEMONIC},
            DatabaseSchema.CommonColumns.COLUMN_UID + " = ?", new String[]{guid},
            null, null, null);
        try {
            if (cursor.moveToNext()) {
                return cursor.getString(cursor.getColumnIndexOrThrow(CommodityEntry.COLUMN_MNEMONIC));
            } else {
                throw new IllegalArgumentException("guid " + guid + " not exits in commodity db");
            }
        } finally {
            cursor.close();
        }
    }

    @Nullable
    public Commodity loadCommodity(@NonNull Commodity commodity) {
        if (commodity.id != 0) {
            return commodity;
        }
        try {
            commodity = getRecord(commodity.getUID());
        } catch (Exception e) {
            // Commodity not found.
            commodity = getCommodity(commodity.getCurrencyCode());
        }
        return commodity;
    }

    @NonNull
    public Commodity getDefaultCommodity() {
        String commodityCode = GnuCashApplication.getDefaultCurrencyCode();
        Commodity commodity = getCommodity(commodityCode);
        return (commodity != null) ? commodity : Commodity.DEFAULT_COMMODITY;
    }
}
