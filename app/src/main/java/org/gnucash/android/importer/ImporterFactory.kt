package org.gnucash.android.importer

import android.content.Context
import org.gnucash.android.gnc.GncProgressListener
import org.gnucash.android.importer.sql.SqliteImporter
import org.gnucash.android.importer.xml.GncXmlImporter
import java.io.BufferedInputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.util.zip.GZIPInputStream
import java.util.zip.ZipInputStream

object ImporterFactory {
    private val HEADER_SQLITE3 =
        "SQLite format 3\u0000".toByteArray(StandardCharsets.US_ASCII)
    private const val ZIP_MAGIC = 0x504B0304
    private const val ZIP_MAGIC_EMPTY = 0x504B0506
    private const val ZIP_MAGIC_SPANNED = 0x504B0708

    @Throws(IOException::class)
    fun create(
        context: Context,
        inputStream: InputStream,
        listener: GncProgressListener?
    ): Importer {
        val input = getInputStream(inputStream)
        val bis = input as? BufferedInputStream ?: BufferedInputStream(input)
        bis.mark(16)
        val header = ByteArray(16)
        val read = bis.read(header)
        if (read < header.size) throw EOFException("file too small")
        bis.reset() //push back the header to the stream

        if (header.contentEquals(HEADER_SQLITE3)) {
            return SqliteImporter(context, bis, listener)
        }
        return GncXmlImporter(context, bis, listener)
    }

    @Throws(IOException::class)
    fun getInputStream(inputStream: InputStream): InputStream {
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
}