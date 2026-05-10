package org.gnucash.android.ui.price

import android.database.Cursor
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.gnucash.android.db.DatabaseSchema.PriceEntry
import org.gnucash.android.db.adapter.PricesDbAdapter
import org.gnucash.android.model.Price
import org.gnucash.android.model.PriceSource

typealias PriceCallback = (Price) -> Unit

class PriceListViewModel : ViewModel() {
    private val _prices = MutableStateFlow<Cursor?>(null)
    val prices: StateFlow<Cursor?> = _prices

    private val _command = MutableStateFlow<Command>(Command.None)
    val command: Flow<Command> = _command

    sealed class Command {
        object None : Command()
        object Done : Command()
        object RemoveOld : Command()
        data class Edit(val price: Price?) : Command()
        data class NotifyDeleted(val price: Price) : Command()
        data class NotifyInserted(val price: Price) : Command()
    }

    init {
        load()
    }

    fun load() {
        viewModelScope.launch(Dispatchers.IO) {
            loadImpl()
        }
    }

    private fun loadImpl(pricesDbAdapter: PricesDbAdapter = PricesDbAdapter.instance) {
        val cursor = pricesDbAdapter.fetchAllRecords(
            null,
            null,
            PriceEntry.COLUMN_DATE + " DESC"
        )
        _prices.update { cursor }
    }

    fun onAddPriceClick() {
        viewModelScope.launch {
            _command.emit(Command.Edit(null))
        }
    }

    fun onRemoveOldPricesClick() {
        viewModelScope.launch {
            _command.emit(Command.RemoveOld)
        }
    }

    fun onEditPriceClick(price: Price) {
        viewModelScope.launch {
            _command.emit(Command.Edit(price))
        }

    }

    fun onDeletePriceClick(price: Price) {
        viewModelScope.launch(Dispatchers.IO) {
            val pricesDbAdapter = PricesDbAdapter.instance
            pricesDbAdapter.deleteRecord(price)
            loadImpl(pricesDbAdapter)
        }
    }

    fun onDuplicatePriceClick(price: Price) {
        viewModelScope.launch(Dispatchers.IO) {
            val priceNew = price.copy().apply {
                setUID(null)
                date = System.currentTimeMillis()
                source = PriceSource.PRICE_SOURCE_EDIT_DLG
            }
            val pricesDbAdapter = PricesDbAdapter.instance
            pricesDbAdapter.insert(priceNew)
            loadImpl(pricesDbAdapter)
        }
    }

    fun markCommandProcessed() {
        _command.update { Command.None }
    }
}