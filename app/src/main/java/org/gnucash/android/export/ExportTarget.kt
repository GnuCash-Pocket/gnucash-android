package org.gnucash.android.export

import java.util.Locale

/**
 * Options for the destination of the exported transactions file.
 * It could be stored on the [.SD_CARD] or exported through another program via [.SHARING]
 */
enum class ExportTarget(val value: String) {
    URI("URI"),
    DROPBOX("DROPBOX"),
    OWNCLOUD("OWNCLOUD"),
    SHARING("SHARING"),
    SD_CARD("SD_CARD");

    companion object {
        private val _values = values()

        fun of(key: String?): ExportTarget {
            val value = key?.uppercase(Locale.ROOT) ?: return SD_CARD
            return _values.firstOrNull { it.value == value } ?: SD_CARD
        }
    }
}