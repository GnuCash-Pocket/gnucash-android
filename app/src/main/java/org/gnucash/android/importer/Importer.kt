package org.gnucash.android.importer

import android.content.Context
import android.os.CancellationSignal
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
        val startTime = System.nanoTime()
        val books = try {
            parse(inputStream)
        } catch (e: ImportException) {
            throw e
        } catch (e: Throwable) {
            throw ImportException(e)
        }
        val endTime = System.nanoTime()
        Timber.d("%d ns spent on importing the file", endTime - startTime)

        for (book in books) {
            setLastExportTime(
                context,
                TransactionsDbAdapter.instance.timestampOfLastModification,
                book.uid
            )
        }
        return books
    }

    @Throws(ImportException::class, IOException::class)
    protected abstract fun parse(inputStream: InputStream): List<Book>

    open fun cancel() {
        cancellationSignal.cancel()
    }
}