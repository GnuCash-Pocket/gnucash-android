package org.gnucash.android.ui.price

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.gnucash.android.R
import org.gnucash.android.db.adapter.PricesDbAdapter
import org.gnucash.android.model.Commodity
import org.gnucash.android.model.Price
import org.gnucash.android.model.PriceSource
import org.gnucash.android.quote.QuoteCallback
import org.gnucash.android.quote.QuoteProvider
import org.gnucash.android.quote.YahooJson
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
        data class Error(val message: String) : Command()
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
            var price = _price.value
            if (price.source == null) {
                price = price.copy(source = PriceSource.PRICE_SOURCE_EDIT_DLG)
            }
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
            val price = _price.value.copy(
                id = 0L,
                date = System.currentTimeMillis(),
                source = PriceSource.PRICE_SOURCE_EDIT_DLG
            ).apply {
                setUID(null)
            }
            val pricesDbAdapter = PricesDbAdapter.instance
            pricesDbAdapter.insert(price)
            _command.emit(Command.Done)
        }
    }

    fun onSecuritySelected(commodity: Commodity) {
        val price = _price.value
        if (price.security == commodity) return
        _price.update { it.copy(security = commodity, source = PriceSource.PRICE_SOURCE_EDIT_DLG) }
    }

    fun onCurrencySelected(commodity: Commodity) {
        val price = _price.value
        if (price.currency == commodity) return
        _price.update { it.copy(currency = commodity, source = PriceSource.PRICE_SOURCE_EDIT_DLG) }
    }

    fun onTypeSelected(type: Price.Type) {
        val price = _price.value
        if (price.type === type) return
        _price.update { it.copy(type = type, source = PriceSource.PRICE_SOURCE_EDIT_DLG) }
    }

    fun onDateSelected(year: Int, month: Int, dayOfMonth: Int) {
        val price = _price.value
        val dateMillis = Calendar.getInstance().apply {
            timeInMillis = price.date
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month)
            set(Calendar.DAY_OF_MONTH, dayOfMonth)
        }.timeInMillis
        _price.update { it.copy(date = dateMillis, source = PriceSource.PRICE_SOURCE_EDIT_DLG) }
    }

    fun onTimeSelected(hourOfDay: Int, minute: Int) {
        val price = _price.value
        val dateMillis = Calendar.getInstance().apply {
            timeInMillis = price.date
            set(Calendar.HOUR_OF_DAY, hourOfDay)
            set(Calendar.MINUTE, minute)
        }.timeInMillis
        _price.update { it.copy(date = dateMillis, source = PriceSource.PRICE_SOURCE_EDIT_DLG) }
    }

    fun onPriceChanged(rate: BigDecimal?) {
        _price.update { it.copy(rate = rate ?: BigDecimal.ZERO) }
    }

    fun markCommandProcessed() {
        _command.update { Command.None }
    }

    fun fetchQuote(context: Context) {
        viewModelScope.launch {
            val price = price.value
            fetchQuote(context, price.security, price.currency)
        }
    }

    private suspend fun fetchQuote(context: Context, security: Commodity, currency: Commodity) {
        if (!security.isCurrency) {
            _command.emit(Command.Error(context.getString(R.string.price_error_security)))
            return
        }
        if (!currency.isCurrency) {
            _command.emit(Command.Error(context.getString(R.string.price_error_currency)))
            return
        }
        if (security == currency) {
            _price.update { it.copy(rate = BigDecimal.ONE) }
            return
        }
        _command.emit(Command.Error(""))

        val provider: QuoteProvider = YahooJson()
        provider.get(security, currency, viewModelScope, object : QuoteCallback {
            override suspend fun onQuote(quote: Price?) {
                if (quote != null) {
                    _price.update { quote }
                } else {
                    _command.emit(Command.Error(context.getString(R.string.error_invalid_exchange_rate)))
                }
            }
        })
    }

    companion object {
        const val SCALE_RATE = 6
    }
}