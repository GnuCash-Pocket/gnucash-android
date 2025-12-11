package org.gnucash.android.importer.xml

import android.content.Context
import android.content.res.AssetManager
import org.gnucash.android.app.GnuCashApplication
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory

class AccountsTemplate {

    private val factory by lazy { DocumentBuilderFactory.newInstance() }
    private val builder by lazy { factory.newDocumentBuilder() }

    fun headers(context: Context): List<Header> {
        val result = mutableListOf<Header>()
        val assetNames = getAssetNames(context)
        for (name in assetNames) {
            val header = readHeader(context, name) ?: continue
            result.add(header)
        }
        return result
    }

    private fun getAssetNames(context: Context): List<String> {
        val assets = context.assets
        val locale = GnuCashApplication.defaultLocale
        return getAssetNames(assets, locale)
    }

    private fun getAssetNames(assets: AssetManager, locale: Locale): List<String> {
        val paths = mutableMapOf<String, String>()
        val languageCode = locale.language
        val countryCode = locale.country

        var path = "accounts/$languageCode"
        assets.list(path)?.also { names ->
            for (name in names) {
                paths[name] = "$path/$name"
            }
        }

        if (!countryCode.isNullOrEmpty()) {
            val region = "${languageCode}_${countryCode}"
            path = "accounts/$region"
            assets.list(path)?.also { names ->
                for (name in names) {
                    paths[name] = "$path/$name"
                }
            }
        }

        if (paths.isEmpty()) {
            return getAssetNames(assets, Locale.ENGLISH)
        }

        return paths.values.toList()
    }

    fun readHeader(context: Context, assetId: String): Header? {
        return readHeader(context.assets, assetId)
    }

    fun readHeader(assets: AssetManager, assetId: String): Header? {
        val source = assets.open(assetId)
        val doc = builder.parse(source)

        val root = doc.documentElement
        if (root.tagName != TAG_ROOT) return null
        val titleElements = root.getElementsByTagName(TAG_TITLE)
        if (titleElements.length == 0) return null
        val title = clean(titleElements.item(0).textContent)

        val shortElements = root.getElementsByTagName(TAG_SHORT_DESCRIPTION)
        var shortDescription: String? = null
        if (shortElements.length > 0) {
            shortDescription = clean(shortElements.item(0).textContent)
        }
        val longElements = root.getElementsByTagName(TAG_LONG_DESCRIPTION)
        var longDescription: String? = null
        if (longElements.length > 0) {
            longDescription = clean(longElements.item(0).textContent)
        }

        return Header(assetId, title, shortDescription, longDescription)
    }

    private fun clean(text: String): String = text.trim()
        .replace('\n', ' ')
        .replace("  ", " ")
        .replace("  ", " ")
        .replace("  ", " ")

    data class Header(
        val assetId: String,
        val title: String,
        val shortDescription: String? = null,
        val longDescription: String? = null
    ) {
        override fun toString(): String {
            return title
        }
    }

    companion object {
        const val TAG_ROOT = "gnc-account-example"
        private const val TAG_TITLE = "gnc-act:title"
        private const val TAG_SHORT_DESCRIPTION = "gnc-act:short-description"
        private const val TAG_LONG_DESCRIPTION = "gnc-act:long-description"
    }
}