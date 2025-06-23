package org.gnucash.android.quote

import android.text.format.DateUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import org.gnucash.android.model.Commodity
import org.gnucash.android.model.Price
import org.gnucash.android.model.PriceType
import org.json.JSONException
import org.json.JSONObject
import timber.log.Timber
import java.math.BigDecimal
import java.sql.Timestamp
import java.util.Locale

class YahooJson : QuoteProvider {

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

            val client = OkHttpClient()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Timber.e(response.message)
                    return@use
                }
                val body = response.body!!
                val s = body.string()
                try {
                    val json = JSONObject(s)
                    val chart = json.getJSONObject("chart")
                    val result = chart.getJSONArray("result")
                    val result0 = result.getJSONObject(0)
                    val meta = result0.getJSONObject("meta")
                    val regularMarketPrice = meta.getDouble("regularMarketPrice")
                    val regularMarketTime = meta.getLong("regularMarketTime")

                    val rate = BigDecimal.valueOf(regularMarketPrice)
                    val price = Price(fromCommodity, targetCommodity, rate).apply {
                        date = Timestamp(regularMarketTime * DateUtils.SECOND_IN_MILLIS)
                        source = Price.SOURCE_QUOTE
                        type = PriceType.Last
                    }
                    launch(Dispatchers.Main) {
                        callback.onQuote(price)
                    }
                } catch (e: JSONException) {
                    Timber.e(e)
                }
            }
        }
    }

    companion object {
        private const val YIND_URL =
            "https://query1.finance.yahoo.com/v8/finance/chart/%s%s%%3DX?metrics=high&interval=1d&range=1d"
    }
}