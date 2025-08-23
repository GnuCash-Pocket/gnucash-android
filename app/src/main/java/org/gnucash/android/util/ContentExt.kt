package org.gnucash.android.util

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.res.AssetManager
import android.content.res.Resources
import android.net.Uri
import android.provider.DocumentsContract
import timber.log.Timber
import java.io.FileNotFoundException
import java.io.InputStream
import java.math.BigDecimal
import java.util.Locale

private val PROJECTION_DOCUMENT_NAME = arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
private const val INDEX_DOCUMENT_NAME = 0

fun Uri.getDocumentName(context: Context?): String {
    if (ContentResolver.SCHEME_ANDROID_RESOURCE == scheme) {
        return ""
    }
    var name: String = this.authority ?: this.host.orEmpty()
    val lastPath = this.lastPathSegment
    if (!lastPath.isNullOrEmpty()) {
        name = lastPath
        val indexSlash = lastPath.lastIndexOf('/')
        if ((indexSlash >= 0) && (indexSlash < name.lastIndex)) {
            name = name.substring(indexSlash + 1)
        }
    }
    if (context != null) {
        try {
            val resolver: ContentResolver = context.contentResolver
            val cursor = resolver.query(this, PROJECTION_DOCUMENT_NAME, null, null, null)
            if (cursor != null) {
                if (cursor.moveToFirst()) {
                    name = cursor.getString(INDEX_DOCUMENT_NAME)
                }
                cursor.close()
            }
        } catch (e: Exception) {
            Timber.w(e, "Cannot get document name for %s", this)
            return ""
        }
    }
    return name
}

/**
 * Apply the default locale.
 *
 * @param locale  the locale to set.
 * @return the context with the applied locale.
 */
fun Context.applyLocale(locale: Locale): Context {
    Locale.setDefault(locale)
    val res = resources ?: Resources.getSystem()!!
    val config = res.configuration
    config.setLocale(locale)
    resources?.updateConfiguration(config, res.displayMetrics)
    return createConfigurationContext(config)
}

fun Uri.isAsset(): Boolean {
    return (ContentResolver.SCHEME_FILE == scheme) && ("/android_asset" == authority)
}

fun Uri.getAssetPath(): String {
    var path: String = this.path ?: return ""
    if (path[0] == '/') {
        path = path.substring(1)
    }
    return path
}

@Throws(FileNotFoundException::class)
fun Uri.openStream(context: Context): InputStream? {
    if (isAsset()) {
        val assets: AssetManager = context.getAssets()
        return assets.open(getAssetPath())
    }
    val contentResolver: ContentResolver = context.getContentResolver()
    return contentResolver.openInputStream(this)
}

operator fun ContentValues.set(key: String, value: Boolean) {
    put(key, value)
}

operator fun ContentValues.set(key: String, value: Double) {
    put(key, value)
}

operator fun ContentValues.set(key: String, value: Float) {
    put(key, value)
}

operator fun ContentValues.set(key: String, value: Int) {
    put(key, value)
}

operator fun ContentValues.set(key: String, value: Long) {
    put(key, value)
}

operator fun ContentValues.set(key: String, value: String?) {
    if (value == null) {
        putNull(key)
    } else {
        put(key, value)
    }
}

operator fun ContentValues.set(key: String, value: BigDecimal) {
    put(key, value.toString())
}