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
import org.gnucash.android.db.DatabaseHelper.Companion.sqlEscapeLike
import org.gnucash.android.db.DatabaseSchema.TransactionEntry
import org.gnucash.android.db.adapter.AccountsDbAdapter
import org.gnucash.android.db.adapter.TransactionsDbAdapter
import org.gnucash.android.model.Transaction
import timber.log.Timber

class SearchResultsViewModel : ViewModel() {
    var form = SearchForm()

    private val _results = MutableStateFlow<Cursor?>(null)
    val results: StateFlow<Cursor?> = _results

    private val transactionsDbAdapter: TransactionsDbAdapter = TransactionsDbAdapter.instance

    fun search() {
        viewModelScope.launch(Dispatchers.IO) {
            val cursorOld = _results.value
            withContext(Dispatchers.Main) {
                cursorOld?.close()
            }

            val where = buildQuery(form)
            Timber.d("Search transactions: $where")

            val orderBy = TransactionEntry.COLUMN_TIMESTAMP + " DESC"
            val cursor = transactionsDbAdapter.fetchAllRecords(where, null, orderBy)

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

    private fun buildQuery(form: SearchForm): String {
        val comparison = form.comparisonType

        val where = StringBuilder()
        if (!form.description.isNullOrEmpty()) {
            where.append('(')
                .append(TransactionEntry.COLUMN_DESCRIPTION)
                .append(" LIKE ")
                .append(sqlEscapeLike(form.description))
                .append(')')
        }
        if (!form.notes.isNullOrEmpty()) {
            if (where.isNotEmpty()) {
                if (comparison == ComparisonType.All) {
                    where.append(" AND ")
                } else {
                    where.append(" OR ")
                }
            }
            where.append('(')
                .append(TransactionEntry.COLUMN_NOTES)
                .append(" LIKE ")
                .append(sqlEscapeLike(form.notes))
                .append(')')
        }
        if (form.dateMin != null && form.dateMin != AccountsDbAdapter.ALWAYS) {
            if (where.isNotEmpty()) {
                if (comparison == ComparisonType.All) {
                    where.append(" AND ")
                } else {
                    where.append(" OR ")
                }
            }
            where.append('(')
                .append(TransactionEntry.COLUMN_TIMESTAMP)
                .append(" >= ")
                .append(form.dateMin!!)
                .append(')')
        }
        if (form.dateMax != null && form.dateMax != AccountsDbAdapter.ALWAYS) {
            if (where.isNotEmpty()) {
                if (comparison == ComparisonType.All) {
                    where.append(" AND ")
                } else {
                    where.append(" OR ")
                }
            }
            where.append('(')
                .append(TransactionEntry.COLUMN_TIMESTAMP)
                .append(" <= ")
                .append(form.dateMax!!)
                .append(')')
        }

        return where.toString()
    }
}