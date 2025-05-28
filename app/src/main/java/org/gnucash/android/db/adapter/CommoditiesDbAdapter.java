package org.gnucash.android.db.adapter;

import static org.gnucash.android.db.DatabaseSchema.CommodityEntry;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.gnucash.android.app.GnuCashApplication;
import org.gnucash.android.model.Commodity;

import java.util.Objects;

import timber.log.Timber;

/**
 * Database adapter for {@link org.gnucash.android.model.Commodity}
 */
public class CommoditiesDbAdapter extends DatabaseAdapter<Commodity> {
    private Commodity defaultCommodity;

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
            CommodityEntry.COLUMN_QUOTE_FLAG,
            CommodityEntry.COLUMN_QUOTE_SOURCE,
            CommodityEntry.COLUMN_QUOTE_TZ
        }, true);
        if (initCommon) {
            initCommon();
        } else {
            defaultCommodity = getDefaultCommodity();
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

        defaultCommodity = Commodity.DEFAULT_COMMODITY = getDefaultCommodity();
    }

    @Nullable
    public static CommoditiesDbAdapter getInstance() {
        return GnuCashApplication.getCommoditiesDbAdapter();
    }

    @Override
    protected @NonNull SQLiteStatement bind(@NonNull SQLiteStatement stmt, @NonNull final Commodity commodity) {
        bindBaseModel(stmt, commodity);
        stmt.bindString(1, commodity.getFullname());
        stmt.bindString(2, commodity.getNamespace());
        stmt.bindString(3, commodity.getMnemonic());
        if (commodity.getLocalSymbol() != null) {
            stmt.bindString(4, commodity.getLocalSymbol());
        }
        if (commodity.getCusip() != null) {
            stmt.bindString(5, commodity.getCusip());
        }
        stmt.bindLong(6, commodity.getSmallestFraction());
        stmt.bindLong(7, commodity.getQuoteFlag() ? 1 : 0);
        if (commodity.getQuoteSource() != null) {
            stmt.bindString(8, commodity.getQuoteSource());
        }
        if (commodity.getQuoteTimeZoneId() != null) {
            stmt.bindString(9, commodity.getQuoteTimeZoneId());
        }

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
        if (isCached) {
            for (Commodity commodity : cache.values()) {
                if (commodity.isCurrency() && commodity.getCurrencyCode().equals(currencyCode)) {
                    return commodity;
                }
            }
        }
        String where = CommodityEntry.COLUMN_MNEMONIC + "=?"
            + " AND " + CommodityEntry.COLUMN_NAMESPACE + " IN ('" + Commodity.COMMODITY_CURRENCY + "','" + Commodity.COMMODITY_ISO4217 + "')";
        String[] whereArgs = new String[]{currencyCode};
        Cursor cursor = fetchAllRecords(where, whereArgs, null);
        try {
            if (cursor.moveToFirst()) {
                Commodity commodity = buildModelInstance(cursor);
                if (isCached) {
                    cache.put(commodity.getUID(), commodity);
                }
                return commodity;
            } else {
                String msg = "Commodity not found in the database: " + currencyCode;
                Timber.e(msg);
            }
        } finally {
            cursor.close();
        }
        return null;
    }

    public String getCommodityUID(String currencyCode) {
        Commodity commodity = getCommodity(currencyCode);
        return (commodity != null) ? commodity.getUID() : null;
    }

    public String getCurrencyCode(@NonNull String guid) {
        Commodity commodity = getRecord(guid);
        if (commodity != null) {
            return commodity.getCurrencyCode();
        }
        throw new IllegalArgumentException("guid " + guid + " not exits in commodity db");
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
        Commodity commodity = defaultCommodity;
        if (commodity != null) {
            return commodity;
        }
        String currencyCode = GnuCashApplication.getDefaultCurrencyCode();
        defaultCommodity = commodity = getCommodity(currencyCode);
        return (commodity != null) ? commodity : Commodity.DEFAULT_COMMODITY;
    }
}
