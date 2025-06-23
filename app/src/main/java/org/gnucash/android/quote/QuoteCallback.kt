package org.gnucash.android.quote

import org.gnucash.android.model.Price

interface QuoteCallback {
    fun onQuote(price: Price)
}