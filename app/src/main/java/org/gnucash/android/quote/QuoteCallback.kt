package org.gnucash.android.quote

import org.gnucash.android.model.Price

fun interface QuoteCallback {
    suspend fun onQuote(quote: Price?)
}