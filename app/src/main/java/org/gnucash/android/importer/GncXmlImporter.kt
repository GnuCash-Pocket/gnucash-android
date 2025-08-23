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
package org.gnucash.android.importer

import android.content.Context
import android.os.CancellationSignal
import org.gnucash.android.db.adapter.TransactionsDbAdapter
import org.gnucash.android.gnc.GncProgressListener
import org.gnucash.android.model.Book
import org.gnucash.android.util.PreferencesHelper.setLastExportTime
import org.xml.sax.InputSource
import org.xml.sax.SAXException
import org.xml.sax.XMLReader
import timber.log.Timber
import java.io.BufferedInputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.util.zip.GZIPInputStream
import java.util.zip.ZipInputStream
import javax.xml.parsers.ParserConfigurationException
import javax.xml.parsers.SAXParserFactory

/**
 * Importer for GnuCash XML files and GNCA (GnuCash Android) XML files
 *
 * @author Ngewi Fet <ngewif@gmail.com>
 */
class GncXmlImporter(
    private val context: Context,
    private val inputStream: InputStream,
    private val listener: GncProgressListener?
) {
    private val cancellationSignal = CancellationSignal()

    @Throws(IOException::class, ParserConfigurationException::class, SAXException::class)
    fun parse(): Book {
        //TODO: Set an error handler which can log errors
        Timber.d("Start import")
        val input: InputStream = getInputStream(inputStream)
        val handler = GncXmlHandler(context, listener, cancellationSignal)
        val reader: XMLReader = createXMLReader(handler)

        val startTime = System.nanoTime()
        reader.parse(InputSource(input))
        val endTime = System.nanoTime()
        Timber.d("%d ns spent on importing the file", endTime - startTime)

        val book = handler.importedBook
        setLastExportTime(
            TransactionsDbAdapter.instance.timestampOfLastModification,
            book.uid
        )

        return book
    }

    fun cancel() {
        cancellationSignal.cancel()
    }

    companion object {
        private const val ZIP_MAGIC = 0x504B0304
        private const val ZIP_MAGIC_EMPTY = 0x504B0506
        private const val ZIP_MAGIC_SPANNED = 0x504B0708

        /**
         * Parse GnuCash XML input and populates the database
         *
         * @param gncXmlInputStream InputStream source of the GnuCash XML file
         * @return GUID of the book into which the XML was imported
         */
        @Throws(ParserConfigurationException::class, SAXException::class, IOException::class)
        fun parse(context: Context, gncXmlInputStream: InputStream): String {
            return parseBook(context, gncXmlInputStream, null).uid
        }

        /**
         * Parse GnuCash XML input and populates the database
         *
         * @param gncXmlInputStream InputStream source of the GnuCash XML file
         * @param listener          the listener to receive events.
         * @return the book into which the XML was imported
         */
        @Throws(ParserConfigurationException::class, SAXException::class, IOException::class)
        fun parseBook(
            context: Context,
            gncXmlInputStream: InputStream,
            listener: GncProgressListener?
        ): Book {
            val importer = GncXmlImporter(context, gncXmlInputStream, listener)
            return importer.parse()
        }

        @Throws(IOException::class)
        private fun getInputStream(inputStream: InputStream): InputStream {
            val bis = BufferedInputStream(inputStream)
            bis.mark(4)
            val byte0 = bis.read()
            if (byte0 == -1) throw EOFException("file too small")
            val byte1 = bis.read()
            if (byte1 == -1) throw EOFException("file too small")
            val byte2 = bis.read()
            if (byte2 == -1) throw EOFException("file too small")
            val byte3 = bis.read()
            if (byte3 == -1) throw EOFException("file too small")
            bis.reset() //push back the signature to the stream

            val signature2 = ((byte1 and 0xFF) shl 8) or (byte0 and 0xFF)
            //check if matches standard gzip magic number
            if (signature2 == GZIPInputStream.GZIP_MAGIC) {
                return GZIPInputStream(bis)
            }

            val signature4 = ((byte3 and 0xFF) shl 24) or ((byte2 and 0xFF) shl 16) or signature2
            if ((signature4 == ZIP_MAGIC) || (signature4 == ZIP_MAGIC_EMPTY) || (signature4 == ZIP_MAGIC_SPANNED)) {
                val zis = ZipInputStream(bis)
                val entry = zis.nextEntry
                if (entry != null) {
                    return zis
                }
            }

            return bis
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
