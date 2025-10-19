package org.gnucash.android.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class SearchFormViewModel : ViewModel() {
    val form = SearchForm()

    private val _query = MutableStateFlow<String?>(null)
    val query: Flow<String?> = _query

    fun onSearchClicked() {
        showResultsPage()
    }

    private fun showResultsPage() {
        viewModelScope.launch {
            _query.emit(form.buildSQL())
        }
    }

    fun onSearchShowed() {
        _query.value = null
    }

    fun setComparison(value: ComparisonType) {
        form.comparisonType = value
    }

    fun addDescription(): SearchCriteria.Description {
        return form.addDescription()
    }

    fun addNote(): SearchCriteria.Note {
        return form.addNote()
    }

    fun addMemo(): SearchCriteria.Memo {
        return form.addMemo()
    }

    fun addDate(): SearchCriteria.Date {
        return form.addDate()
    }

    fun addNumeric(): SearchCriteria.Numeric {
        return form.addNumeric()
    }

    fun remove(item: SearchCriteria) {
        form.remove(item)
    }
}