package org.gnucash.android.model

enum class PriceSource(val value: String) {
    PRICE_SOURCE_EDIT_DLG("user:price-editor"),
    PRICE_SOURCE_FQ("Finance::Quote"),
    PRICE_SOURCE_USER_PRICE("user:price"),
    PRICE_SOURCE_XFER_DLG_VAL("user:xfer-dialog"),
    PRICE_SOURCE_SPLIT_REG("user:split-register"),
    PRICE_SOURCE_SPLIT_IMPORT("user:split-import"),
    PRICE_SOURCE_STOCK_SPLIT("user:stock-split"),
    PRICE_SOURCE_STOCK_TRANSACTION("user:stock-transaction"),
    PRICE_SOURCE_INVOICE("user:invoice-post"),
    PRICE_SOURCE_TEMP("temporary"),
    PRICE_SOURCE_INVALID("invalid");

    override fun toString(): String {
        return value
    }

    companion object {
        private val values = PriceSource.entries

        fun of(key: String?): PriceSource {
            val value = key ?: return PRICE_SOURCE_INVALID
            return values.firstOrNull { it.value == value } ?: PRICE_SOURCE_INVALID
        }
    }
}