package org.gnucash.android.ui.search

import android.database.Cursor
import android.database.SQLException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.gnucash.android.db.adapter.TransactionsDbAdapter
import org.gnucash.android.model.Transaction
import timber.log.Timber

class SearchResultsViewModel : ViewModel() {
    var where: String? = null

    private val _results = MutableStateFlow<Cursor?>(null)
    val results: StateFlow<Cursor?> = _results

    private val transactionsDbAdapter: TransactionsDbAdapter = TransactionsDbAdapter.instance

    fun search() {
        viewModelScope.launch(Dispatchers.IO) {
            val cursorOld = _results.value
            withContext(Dispatchers.Main) {
                cursorOld?.close()
            }

            val where = this@SearchResultsViewModel.where ?: return@launch
            Timber.d("Search transactions: $where")

            val cursor = transactionsDbAdapter.fetchSearch(where)

            withContext(Dispatchers.Main) {
                _results.emit(cursor)
            }
        }
    }

    fun delete(transaction: Transaction) {
        try {
            transactionsDbAdapter.deleteRecord(transaction)
            search()
        } catch (e: SQLException) {
            Timber.e(e)
        }
    }

    fun duplicate(transaction: Transaction) {
        try {
            val duplicate = Transaction(transaction)
            duplicate.time = System.currentTimeMillis()
            transactionsDbAdapter.insert(duplicate)
            search()
        } catch (e: SQLException) {
            Timber.e(e)
        }
    }

    override fun onCleared() {
        super.onCleared()
        try {
            _results.value?.close()
            _results.value = null
        } catch (_: Exception) {
        }
    }
}