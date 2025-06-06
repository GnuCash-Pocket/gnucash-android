/*
 * Copyright (c) 2013 Ngewi Fet <ngewif@gmail.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gnucash.android.export

import androidx.annotation.StringRes
import org.gnucash.android.R
import java.util.Locale

/**
 * Enumeration of the different export formats supported by the application
 *
 * @author Ngewi Fet <ngewif></ngewif>@gmail.com>
 */
enum class ExportFormat(
    @JvmField
    val value: String,
    /**
     * The file extension for this export format including the period e.g. ".qif"
     */
    @JvmField
    val extension: String,
    /** The MIME type. */
    @JvmField
    val mimeType: String = "*/*",
    /**
     * Full name of the export format acronym
     */
    private val description: String,
    /** The label id. */
    @JvmField
    @StringRes val labelId: Int
) {
    QIF("QIF", ".qif", "application/qif", "Quicken Interchange Format", R.string.file_format_qif),
    OFX("OFX", ".ofx", "application/x-ofx", "Open Financial eXchange", R.string.file_format_ofx),
    XML("XML", ".xac", "application/x-gnucash", "GnuCash XML", R.string.file_format_xml),
    CSVA("CSVA", ".csv", "text/csv", "GnuCash accounts CSV", R.string.file_format_csv),
    CSVT("CSVT", ".csv", "text/csv", "GnuCash transactions CSV", R.string.file_format_csv);

    override fun toString(): String {
        return description
    }

    companion object {
        private val values = values()

        @JvmStatic
        fun of(key: String?): ExportFormat {
            val value = key?.uppercase(Locale.ROOT) ?: return XML
            return values.firstOrNull { it.value == value } ?: XML
        }
    }
}
