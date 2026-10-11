/*
 * Copyright (c) 2014 Ngewi Fet <ngewif@gmail.com>
 * Copyright (c) 2014 Yongxin Wang <fefe.wyx@gmail.com>
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
package org.gnucash.android.importer.xml

import android.content.Context
import android.net.Uri
import org.gnucash.android.gnc.GncProgressListener
import org.gnucash.android.importer.Importer
import org.gnucash.android.model.Book
import org.xml.sax.InputSource
import org.xml.sax.SAXException
import org.xml.sax.XMLReader
import java.io.IOException
import java.io.InputStream
import javax.xml.parsers.ParserConfigurationException
import javax.xml.parsers.SAXParserFactory

/**
 * Importer for GnuCash XML files and GNCA (GnuCash Android) XML files
 *
 * @author Ngewi Fet <ngewif@gmail.com>
 */
class GncXmlImporter(
    context: Context,
    inputStream: InputStream,
    listener: GncProgressListener?
) : Importer(context, inputStream, listener) {

    @Throws(IOException::class, ParserConfigurationException::class, SAXException::class)
    override fun parse(uri: Uri, inputStream: InputStream): List<Book> {
        val handler = GncXmlHandler(context, uri, listener, cancellationSignal)
        val reader = createXMLReader(handler)
        reader.parse(InputSource(inputStream))
        return handler.importedBooks
    }

    companion object {
        /**
         * Parse GnuCash XML input and populates the database
         *
         * @param inputStream InputStream source of the GnuCash XML file
         * @return GUID of the book into which the XML was imported
         */
        @Throws(ParserConfigurationException::class, SAXException::class, IOException::class)
        fun parse(context: Context, uri: Uri, inputStream: InputStream): String {
            return parseBook(context, uri, inputStream, null).uid
        }

        /**
         * Parse GnuCash XML input and populates the database
         *
         * @param inputStream InputStream source of the GnuCash XML file
         * @param listener          the listener to receive events.
         * @return the book into which the XML was imported
         */
        @Throws(ParserConfigurationException::class, SAXException::class, IOException::class)
        fun parseBook(
            context: Context,
            uri: Uri,
            inputStream: InputStream,
            listener: GncProgressListener?
        ): Book {
            val importer = GncXmlImporter(context, inputStream, listener)
            return importer.parse(uri)[0]
        }

        @Throws(ParserConfigurationException::class, SAXException::class)
        private fun createXMLReader(handler: GncXmlHandler): XMLReader {
            val spf = SAXParserFactory.newInstance()
            spf.isNamespaceAware = true
            val sp = spf.newSAXParser()
            val xr = sp.xmlReader
            xr.contentHandler = handler
            return xr
        }
    }
}
