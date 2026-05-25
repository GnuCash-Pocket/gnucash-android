package org.gnucash.android.ui.homescreen

import android.content.Context
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.gnucash.android.db.adapter.BooksDbAdapter
import org.gnucash.android.model.Book
import org.gnucash.android.model.Commodity
import org.gnucash.android.ui.adapter.SpinnerArrayAdapter
import org.gnucash.android.ui.adapter.SpinnerItem
import org.gnucash.android.util.getDocumentName

class BooksAdapter(
    context: Context,
    private val adapter: BooksDbAdapter = BooksDbAdapter.instance,
    private val scope: CoroutineScope
) : SpinnerArrayAdapter<Book>(context) {

    private var loadJob: Job? = null
    private val items = mutableListOf<SpinnerItem<Book>>()

    constructor(
        context: Context,
        lifecycleOwner: LifecycleOwner
    ) : this(
        context = context,
        scope = lifecycleOwner.lifecycleScope
    )

    fun getBook(position: Int): Book? {
        return getItem(position)?.value
    }

    fun load(callback: ((BooksAdapter) -> Unit)? = null): BooksAdapter {
        loadJob?.cancel()
        loadJob = scope.launch(Dispatchers.IO) {
            val records = loadData(adapter)
            val labels = records.map { book ->
                val name = book.displayName ?: book.sourceUri?.getDocumentName(context) ?: book.uid
                SpinnerItem(book, name)
            }
            items.clear()
            items.addAll(labels)
            withContext(Dispatchers.Main) {
                clear()
                addAll(labels)
                callback?.invoke(this@BooksAdapter)
            }
        }
        return this
    }

    private fun loadData(adapter: BooksDbAdapter): List<Book> {
        return adapter.allRecords
    }

    fun getPosition(uid: String?): Int {
        if (uid.isNullOrEmpty()) return NO_SELECTION
        for (i in 0 until count) {
            if (uid == getBook(i)?.uid) {
                return i
            }
        }
        return NO_SELECTION
    }
}