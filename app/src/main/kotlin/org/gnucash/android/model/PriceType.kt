package org.gnucash.android.model

import java.util.Locale

/**
 * One of:
 * Bid (the market buying price),
 * Ask (the market selling price),
 * Last (the last transaction price),
 * Net Asset Value (mutual fund price per share, NAV for short),
 * or Unknown.
 *
 * Stocks and currencies will usually give their quotes as one of bid, ask or last.
 * Mutual funds are often given as net asset value.
 * For other commodities, simply choose Unknown.
 * This option is for informational purposes only, it is not used by GnuCash.
 */
enum class PriceType(val value: String) {
    /** the market buying price */
    Bid("bid"),

    /** Ask (the market selling price) */
    Ask("ask"),

    /** Last (the last transaction price) */
    Last("last"),

    /** Net Asset Value (mutual fund price per share, NAV for short) */
    NetAssetValue("nav"),

    Transaction("transaction"),

    Unknown("unknown");

    companion object {
        private val values = PriceType.values()

        @JvmStatic
        fun of(key: String?): PriceType {
            val value = key?.lowercase(Locale.ROOT) ?: return Unknown
            return values.firstOrNull { it.value == value } ?: Unknown
        }
    }
}