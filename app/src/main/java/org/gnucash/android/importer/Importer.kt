package org.gnucash.android.importer

import android.content.Context
import android.net.Uri
import android.os.CancellationSignal
import android.os.SystemClock
import android.text.format.DateUtils
import org.gnucash.android.db.DatabaseHelper
import org.gnucash.android.db.adapter.BooksDbAdapter
import org.gnucash.android.gnc.GncProgressListener
import org.gnucash.android.model.Book
import org.gnucash.android.util.PreferencesHelper.setLastExportTime
import timber.log.Timber
import java.io.IOException
import java.io.InputStream
import java.sql.Timestamp

typealias ImportBookCallback = (bookUID: String?) -> Unit

abstract class Importer(
    protected val context: Context,
    private val inputStream: InputStream,
    protected val listener: GncProgressListener?
) {
    protected val cancellationSignal = CancellationSignal()
    protected val booksDbAdapter = BooksDbAdapter.instance

    @Throws(ImportException::class)
    fun parse(uri: Uri): List<Book> {
        //TODO: Set an error handler which can log errors
        Timber.d("Start import")
        val timeStart = SystemClock.elapsedRealtime()
        val books = try {
            parse(uri, inputStream)
        } catch (e: ImportException) {
            throw e
        } catch (e: Throwable) {
            throw ImportException(e)
        }

        for (book in books) {
            val exportTime = getLastModification(book)
            setLastExportTime(context, exportTime, book.uid)
        }

        val timeFinish = SystemClock.elapsedRealtime()
        val timeSeconds = (timeFinish - timeStart) / DateUtils.SECOND_IN_MILLIS
        Timber.v("imported in %s", DateUtils.formatElapsedTime(timeSeconds))
        return books
    }

    @Throws(ImportException::class, IOException::class)
    protected abstract fun parse(uri: Uri, inputStream: InputStream): List<Book>

    open fun cancel() {
        cancellationSignal.cancel()
    }

    private fun getLastModification(book: Book): Timestamp {
        val bookUID = book.uid
        val dbHelper = DatabaseHelper(context, bookUID)
        val holder = dbHelper.readableHolder
        val transactionsDbAdapter = holder.transactionsDbAdapter
        val result = transactionsDbAdapter.timestampOfLastModification
        dbHelper.close()
        return result
    }
}