package org.gnucash.android.ui.price

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.gnucash.android.db.adapter.PricesDbAdapter
import org.gnucash.android.model.Commodity
import org.gnucash.android.model.Price
import org.gnucash.android.model.PriceSource
import java.math.BigDecimal
import java.util.Calendar

class PriceFormViewModel : ViewModel() {
    private val _price = MutableStateFlow(Price())
    val price: StateFlow<Price> = _price

    private val _command = MutableStateFlow<Command>(Command.None)
    val command: Flow<Command> = _command

    sealed class Command {
        object None : Command()
        object Done : Command()
    }

    fun load(priceUID: String?) {
        viewModelScope.launch(Dispatchers.IO) {
            loadPrice(priceUID)
        }
    }

    fun loadPrice(priceUID: String?) {
        val pricesDbAdapter = PricesDbAdapter.instance
        if (priceUID.isNullOrEmpty()) {
            _price.update { Price() }
        } else {
            val price = pricesDbAdapter.getRecord(priceUID)
            _price.update { price }
        }
    }

    fun onSavePriceClick() {
        viewModelScope.launch {
            val price = _price.value.copy(source = PriceSource.PRICE_SOURCE_EDIT_DLG)
            val pricesDbAdapter = PricesDbAdapter.instance
            pricesDbAdapter.replace(price)
            _command.emit(Command.Done)
        }
    }

    fun onDeletePriceClick() {
        viewModelScope.launch {
            val price = _price.value
            val pricesDbAdapter = PricesDbAdapter.instance
            pricesDbAdapter.deleteRecord(price)
            _command.emit(Command.Done)
        }
    }

    fun onDuplicatePriceClick() {
        viewModelScope.launch {
            val price = _price.value.copy().apply {
                setUID(null)
                date = System.currentTimeMillis()
                source = PriceSource.PRICE_SOURCE_EDIT_DLG
            }
            val pricesDbAdapter = PricesDbAdapter.instance
            pricesDbAdapter.insert(price)
            _command.emit(Command.Done)
        }
    }

    fun onSecuritySelected(commodity: Commodity) {
        val price = _price.value
        if (price.security == commodity) return
        _price.update { it.copy(security = commodity) }
    }

    fun onCurrencySelected(commodity: Commodity) {
        val price = _price.value
        if (price.currency == commodity) return
        _price.update { it.copy(currency = commodity) }
    }

    fun onTypeSelected(type: Price.Type) {
        val price = _price.value
        if (price.type === type) return
        _price.update { it.copy(type = type) }
    }

    fun onDateSelected(year: Int, month: Int, dayOfMonth: Int) {
        val price = _price.value
        val dateMillis = Calendar.getInstance().apply {
            timeInMillis = price.date
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month)
            set(Calendar.DAY_OF_MONTH, dayOfMonth)
        }.timeInMillis
        _price.update { it.copy(date = dateMillis) }
    }

    fun onTimeSelected(hourOfDay: Int, minute: Int) {
        val price = _price.value
        val dateMillis = Calendar.getInstance().apply {
            timeInMillis = price.date
            set(Calendar.HOUR_OF_DAY, hourOfDay)
            set(Calendar.MINUTE, minute)
        }.timeInMillis
        _price.update { it.copy(date = dateMillis) }
    }

    fun onPriceChanged(rate: BigDecimal?) {
        _price.update { it.copy(rate = rate ?: BigDecimal.ZERO) }
    }

    fun markCommandProcessed() {
        _command.update { Command.None }
    }
}