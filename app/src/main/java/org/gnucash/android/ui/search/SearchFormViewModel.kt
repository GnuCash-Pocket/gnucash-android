package org.gnucash.android.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.util.Calendar

class SearchFormViewModel : ViewModel() {
    val form = SearchForm()

    private val _query = MutableStateFlow<SearchForm?>(null)
    val query: Flow<SearchForm?> = _query

    fun setDescription(value: String?) {
        form.description = value
    }

    fun setNotes(value: String?) {
        form.notes = value
    }

    fun setMemo(value: String?) {
        form.memo = value
    }

    fun setDateStart(value: Long?) {
        form.dateMin = value
    }

    fun setDateEnd(value: Long?) {
        form.dateMax = value
    }

    fun onSearchClicked() {
        showResultsPage()
    }

    private fun showResultsPage() {
        viewModelScope.launch {
            _query.emit(form.copy())
        }
    }

    fun setDateStart(year: Int, month: Int, dayOfMonth: Int) {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month)
            set(Calendar.DAY_OF_MONTH, dayOfMonth)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
        }
        setDateStart(calendar.timeInMillis)
    }

    fun setDateEnd(year: Int, month: Int, dayOfMonth: Int) {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month)
            set(Calendar.DAY_OF_MONTH, dayOfMonth)
            set(Calendar.HOUR_OF_DAY, getMaximum(Calendar.HOUR_OF_DAY))
            set(Calendar.MINUTE, getMaximum(Calendar.MINUTE))
            set(Calendar.SECOND, getMaximum(Calendar.SECOND))
        }
        setDateEnd(calendar.timeInMillis)
    }

    fun setComparison(value: ComparisonType) {
        form.comparisonType = value
    }
}