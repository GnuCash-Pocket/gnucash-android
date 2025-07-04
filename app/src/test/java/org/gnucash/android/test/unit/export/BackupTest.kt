/*
 * Copyright (c) 2015 Ngewi Fet <ngewif@gmail.com>
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
package org.gnucash.android.test.unit.export

import android.content.Context
import org.assertj.core.api.Assertions.assertThat
import org.gnucash.android.R
import org.gnucash.android.app.GnuCashApplication
import org.gnucash.android.db.adapter.BooksDbAdapter
import org.gnucash.android.export.ExportFormat
import org.gnucash.android.export.ExportParams
import org.gnucash.android.export.Exporter
import org.gnucash.android.export.Exporter.ExporterException
import org.gnucash.android.export.xml.GncXmlExporter
import org.gnucash.android.importer.GncXmlImporter
import org.gnucash.android.test.unit.GnuCashTest
import org.junit.Before
import org.junit.Test
import org.xml.sax.SAXException
import timber.log.Timber
import java.io.File
import java.io.IOException
import javax.xml.parsers.ParserConfigurationException

/**
 * Test backup and restore functionality
 */
class BackupTest : GnuCashTest() {
    @Before
    fun setUp() {
        loadDefaultAccounts(context)
    }

    @Test
    @Throws(ExporterException::class)
    fun shouldCreateBackupFileName() {
        val bookUID = GnuCashApplication.getActiveBookUID()
        val exporter: Exporter = GncXmlExporter(
            context, ExportParams(ExportFormat.XML),
            bookUID!!
        )
        val uriExported = exporter.export()

        assertThat(uriExported).isNotNull()
        assertThat(uriExported!!.scheme).isEqualTo("file")
        val file = File(uriExported.path)
        assertThat(file).exists()
            .hasExtension(ExportFormat.XML.extension.substring(1))
    }

    companion object {
        /**
         * Loads the default accounts from file resource
         */
        fun loadDefaultAccounts(context: Context, activate: Boolean = true): String {
            try {
                val bookUID = GncXmlImporter.parse(
                    context,
                    context.resources.openRawResource(R.raw.default_accounts)
                )
                if (activate) {
                    val booksDbAdapter = BooksDbAdapter.getInstance()
                    booksDbAdapter.setActive(bookUID)
                    assertThat(booksDbAdapter.activeBookUID).isEqualTo(bookUID)
                    assertThat(GnuCashApplication.getActiveBookUID()).isEqualTo(bookUID)
                }
                return bookUID
            } catch (e: ParserConfigurationException) {
                Timber.e(e)
                throw RuntimeException("Could not create default accounts")
            } catch (e: SAXException) {
                Timber.e(e)
                throw RuntimeException("Could not create default accounts")
            } catch (e: IOException) {
                Timber.e(e)
                throw RuntimeException("Could not create default accounts")
            }
        }
    }


}
