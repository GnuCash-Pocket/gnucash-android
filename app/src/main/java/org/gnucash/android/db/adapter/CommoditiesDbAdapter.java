package org.gnucash.android.db.adapter;

import static org.gnucash.android.db.DatabaseSchema.CommodityEntry;
import static org.gnucash.android.model.Commodity.USD;
import static org.gnucash.android.model.Commodity.getLocaleCurrencyCode;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteStatement;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.gnucash.android.R;
import org.gnucash.android.app.GnuCashApplication;
import org.gnucash.android.db.DatabaseHolder;
import org.gnucash.android.model.Commodity;

import java.util.Objects;

import timber.log.Timber;

/**
 * Database adapter for {@link Commodity}
 */
public class CommoditiesDbAdapter extends DatabaseAdapter<Commodity> {
    private Commodity defaultCommodity;

    /**
     * Opens the database adapter with an existing database
     *
     * @param holder Database holder
     */
    public CommoditiesDbAdapter(@NonNull DatabaseHolder holder) {
        this(holder, true);
    }

    /**
     * Opens the database adapter with an existing database
     *
     * @param holder     Database holder
     * @param initCommon initialize commonly used commodities?
     */
    public CommoditiesDbAdapter(@NonNull DatabaseHolder holder, boolean initCommon) {
        super(holder, CommodityEntry.TABLE_NAME, new String[]{
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
        Commodity.AUD = Objects.requireNonNull(getCurrency("AUD"));
        Commodity.CAD = Objects.requireNonNull(getCurrency("CAD"));
        Commodity.CHF = Objects.requireNonNull(getCurrency("CHF"));
        Commodity.EUR = Objects.requireNonNull(getCurrency("EUR"));
        Commodity.GBP = Objects.requireNonNull(getCurrency("GBP"));
        Commodity.JPY = Objects.requireNonNull(getCurrency("JPY"));
        USD = Objects.requireNonNull(getCurrency("USD"));

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

        Commodity commodity = new Commodity(fullname, mnemonic, namespace, fraction);
        populateBaseModelAttributes(cursor, commodity);
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
    public Commodity getCurrency(@Nullable String currencyCode) {
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

        switch (currencyCode) {
            case "AUD":
                return Commodity.AUD;
            case "CAD":
                return Commodity.CAD;
            case "CHF":
                return Commodity.CHF;
            case "EUR":
                return Commodity.EUR;
            case "GBP":
                return Commodity.GBP;
            case "JPY":
                return Commodity.JPY;
            case "USD":
                return USD;
            default:
                return null;
        }
    }

    public String getCommodityUID(String currencyCode) {
        Commodity commodity = getCurrency(currencyCode);
        return (commodity != null) ? commodity.getUID() : null;
    }

    public String getCurrencyCode(@NonNull String guid) {
        Commodity commodity = getRecord(guid);
        if (commodity != null) {
            return commodity.getCurrencyCode();
        }
        throw new IllegalArgumentException("Commodity not found");
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
            commodity = getCurrency(commodity.getCurrencyCode());
        }
        return commodity;
    }

    @NonNull
    public Commodity getDefaultCommodity() {
        Commodity commodity = defaultCommodity;
        if (commodity != null) {
            return commodity;
        }

        Context context = holder.context;
        String prefKey = context.getString(R.string.key_default_currency);
        SharedPreferences preferences = getBookPreferences();
        String currencyCode = preferences.getString(prefKey, null);
        if (currencyCode == null) {
            currencyCode = getLocaleCurrencyCode();
        }
        defaultCommodity = commodity = getCurrency(currencyCode);
        return (commodity != null) ? commodity : Commodity.DEFAULT_COMMODITY;
    }

    public void setDefaultCurrencyCode(@Nullable String currencyCode) {
        Context context = holder.context;
        SharedPreferences preferences = getBookPreferences();
        String prefKey = context.getString(R.string.key_default_currency);
        preferences.edit().putString(prefKey, currencyCode).apply();

        Commodity commodity = getCurrency(currencyCode);
        if (commodity != null) {
            defaultCommodity = commodity;
            Commodity.DEFAULT_COMMODITY = commodity;
        }
    }
}
