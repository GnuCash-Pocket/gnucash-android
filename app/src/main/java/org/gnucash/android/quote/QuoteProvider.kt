package org.gnucash.android.quote

import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CoroutineScope
import org.gnucash.android.model.Commodity

// TODO choose provider from Settings/Preferences.
interface QuoteProvider {

    fun get(
        fromCommodity: Commodity,
        targetCommodity: Commodity,
        scope: CoroutineScope,
        callback: QuoteCallback
    )

    fun get(
        fromCommodity: Commodity,
        targetCommodity: Commodity,
        lifecycleOwner: LifecycleOwner,
        callback: QuoteCallback
    ) = get(fromCommodity, targetCommodity, lifecycleOwner.lifecycleScope, callback)
}