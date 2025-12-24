package org.gnucash.android.importer

import android.content.Context
import android.os.CancellationSignal
import android.os.SystemClock
import android.text.format.DateUtils
import org.gnucash.android.db.adapter.BooksDbAdapter
import org.gnucash.android.db.adapter.TransactionsDbAdapter
import org.gnucash.android.gnc.GncProgressListener
import org.gnucash.android.model.Book
import org.gnucash.android.util.PreferencesHelper.setLastExportTime
import timber.log.Timber
import java.io.IOException
import java.io.InputStream

typealias ImportBookCallback = (bookUID: String?) -> Unit

abstract class Importer(
    protected val context: Context,
    private val inputStream: InputStream,
    protected val listener: GncProgressListener?
) {
    protected val cancellationSignal = CancellationSignal()
    protected val booksDbAdapter = BooksDbAdapter.instance

    @Throws(ImportException::class)
    fun parse(): List<Book> {
        //TODO: Set an error handler which can log errors
        Timber.d("Start import")
        val timeStart = SystemClock.elapsedRealtime()
        val books = try {
            parse(inputStream)
        } catch (e: ImportException) {
            throw e
        } catch (e: Throwable) {
            throw ImportException(e)
        }

        for (book in books) {
            setLastExportTime(
                context,
                TransactionsDbAdapter.instance.timestampOfLastModification,
                book.uid
            )
        }

        val timeFinish = SystemClock.elapsedRealtime()
        val timeSeconds = (timeFinish - timeStart) / DateUtils.SECOND_IN_MILLIS
        Timber.v("imported in %s", DateUtils.formatElapsedTime(timeSeconds))
        return books
    }

    @Throws(ImportException::class, IOException::class)
    protected abstract fun parse(inputStream: InputStream): List<Book>

    open fun cancel() {
        cancellationSignal.cancel()
    }
}