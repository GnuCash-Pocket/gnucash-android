package org.gnucash.android.quote

import android.text.format.DateUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.IOException
import org.gnucash.android.model.Commodity
import org.gnucash.android.model.Price
import org.json.JSONObject
import timber.log.Timber
import java.math.BigDecimal
import java.util.Locale

class YahooJson : QuoteProvider {

    private val client by lazy { OkHttpClient() }

    override fun get(
        fromCommodity: Commodity,
        targetCommodity: Commodity,
        scope: CoroutineScope,
        callback: QuoteCallback
    ) {
        scope.launch(Dispatchers.IO) {
            val url = String.format(
                Locale.ROOT,
                YIND_URL,
                fromCommodity.currencyCode,
                targetCommodity.currencyCode
            )
            val request: Request = Request.Builder()
                .url(url)
                .build()

            val result: Result<Price?> = runCatching {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IOException(response.message)
                    }
                    val body = response.body!!
                    val s = body.string()
                    val json = JSONObject(s)
                    val chart = json.getJSONObject("chart")
                    val result = chart.getJSONArray("result")
                    val result0 = result.getJSONObject(0)
                    val meta = result0.getJSONObject("meta")
                    val regularMarketPrice = meta.getDouble("regularMarketPrice")
                    val regularMarketTime = meta.getLong("regularMarketTime")

                    val rate = BigDecimal.valueOf(regularMarketPrice)
                    val price = Price(fromCommodity, targetCommodity, rate).apply {
                        date = regularMarketTime * DateUtils.SECOND_IN_MILLIS
                        source = Price.SOURCE_QUOTE
                        type = Price.Type.Last
                    }
                    price
                }
            }.onFailure { e ->
                Timber.e(e)
            }
            withContext(Dispatchers.Main) {
                callback.onQuote(result.getOrNull())
            }
        }
    }

    companion object {
        private const val YIND_URL =
            "https://query1.finance.yahoo.com/v8/finance/chart/%s%s%%3DX?metrics=high&interval=1d&range=1d"
    }
}