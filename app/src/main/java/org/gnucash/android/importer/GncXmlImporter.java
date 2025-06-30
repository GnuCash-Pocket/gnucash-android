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
package org.gnucash.android.importer;

import static java.util.zip.GZIPInputStream.GZIP_MAGIC;

import android.content.Context;
import android.os.CancellationSignal;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.gnucash.android.db.adapter.TransactionsDbAdapter;
import org.gnucash.android.gnc.GncProgressListener;
import org.gnucash.android.model.Book;
import org.gnucash.android.util.PreferencesHelper;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

import java.io.BufferedInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import timber.log.Timber;

/**
 * Importer for GnuCash XML files and GNCA (GnuCash Android) XML files
 *
 * @author Ngewi Fet <ngewif@gmail.com>
 */
public class GncXmlImporter {

    private static final int ZIP_MAGIC = 0x504B0304;
    private static final int ZIP_MAGIC_EMPTY = 0x504B0506;
    private static final int ZIP_MAGIC_SPANNED = 0x504B0708;

    /**
     * Parse GnuCash XML input and populates the database
     *
     * @param gncXmlInputStream InputStream source of the GnuCash XML file
     * @return GUID of the book into which the XML was imported
     */
    public static String parse(@NonNull Context context, @NonNull InputStream gncXmlInputStream) throws ParserConfigurationException, SAXException, IOException {
        return parseBook(context, gncXmlInputStream, null).getUID();
    }

    /**
     * Parse GnuCash XML input and populates the database
     *
     * @param gncXmlInputStream InputStream source of the GnuCash XML file
     * @param listener          the listener to receive events.
     * @return the book into which the XML was imported
     */
    public static Book parseBook(@NonNull Context context, @NonNull InputStream gncXmlInputStream, @Nullable GncProgressListener listener) throws ParserConfigurationException, SAXException, IOException {
        GncXmlImporter importer = new GncXmlImporter(context, gncXmlInputStream, listener);
        return importer.parse();
    }

    @NonNull
    private static InputStream getInputStream(InputStream inputStream) throws IOException {
        BufferedInputStream bis = new BufferedInputStream(inputStream);
        bis.mark(4);
        int byte0 = bis.read();
        if (byte0 == -1) throw new EOFException("file too small");
        int byte1 = bis.read();
        if (byte1 == -1) throw new EOFException("file too small");
        int byte2 = bis.read();
        if (byte2 == -1) throw new EOFException("file too small");
        int byte3 = bis.read();
        if (byte3 == -1) throw new EOFException("file too small");
        bis.reset(); //push back the signature to the stream

        int signature2 = ((byte1 & 0xFF) << 8) | (byte0 & 0xFF);
        //check if matches standard gzip magic number
        if (signature2 == GZIP_MAGIC) {
            return new GZIPInputStream(bis);
        }

        int signature4 = ((byte3 & 0xFF) << 24) | ((byte2 & 0xFF) << 16) | signature2;
        if ((signature4 == ZIP_MAGIC) || (signature4 == ZIP_MAGIC_EMPTY) || (signature4 == ZIP_MAGIC_SPANNED)) {
            ZipInputStream zis = new ZipInputStream(bis);
            ZipEntry entry = zis.getNextEntry();
            if (entry != null) {
                return zis;
            }
        }

        return bis;
    }

    private static XMLReader createXMLReader(GncXmlHandler handler) throws ParserConfigurationException, SAXException {
        SAXParserFactory spf = SAXParserFactory.newInstance();
        SAXParser sp = spf.newSAXParser();
        XMLReader xr = sp.getXMLReader();
        xr.setContentHandler(handler);
        return xr;
    }

    @NonNull
    private final Context context;
    @NonNull
    private final InputStream inputStream;
    @Nullable
    private final GncProgressListener listener;
    @NonNull
    private final CancellationSignal cancellationSignal = new CancellationSignal();

    public GncXmlImporter(@NonNull Context context, @NonNull InputStream inputStream, @Nullable GncProgressListener listener) {
        this.context = context;
        this.inputStream = inputStream;
        this.listener = listener;
    }

    public Book parse() throws IOException, ParserConfigurationException, SAXException {
        //TODO: Set an error handler which can log errors
        Timber.d("Start import");
        InputStream input = getInputStream(inputStream);
        GncXmlHandler handler = new GncXmlHandler(context, listener, cancellationSignal);
        XMLReader reader = createXMLReader(handler);

        long startTime = System.nanoTime();
        reader.parse(new InputSource(input));
        long endTime = System.nanoTime();
        Timber.d("%d ns spent on importing the file", endTime - startTime);

        Book book = handler.getImportedBook();
        String bookUID = book.getUID();
        PreferencesHelper.setLastExportTime(
            TransactionsDbAdapter.getInstance().getTimestampOfLastModification(),
            bookUID
        );

        return book;
    }

    public void cancel() {
        cancellationSignal.cancel();
    }
}
