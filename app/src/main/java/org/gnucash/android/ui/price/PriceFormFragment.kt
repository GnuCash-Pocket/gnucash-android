package org.gnucash.android.ui.price

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import androidx.appcompat.app.ActionBar
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.gnucash.android.R
import org.gnucash.android.app.MenuFragment
import org.gnucash.android.app.actionBar
import org.gnucash.android.databinding.FragmentPriceFormBinding
import org.gnucash.android.model.Commodity
import org.gnucash.android.model.Price
import org.gnucash.android.ui.adapter.CommoditiesAdapter
import org.gnucash.android.ui.adapter.DefaultItemSelectedListener
import org.gnucash.android.ui.common.UxArgument
import org.gnucash.android.ui.transaction.TransactionFormFragment.Companion.DATE_FORMATTER
import org.gnucash.android.ui.transaction.TransactionFormFragment.Companion.TIME_FORMATTER
import org.gnucash.android.ui.util.dialog.DatePickerDialogFragment
import org.gnucash.android.ui.util.dialog.TimePickerDialogFragment
import org.gnucash.android.ui.util.widget.CalculatorEditText
import java.math.BigDecimal

class PriceFormFragment : MenuFragment() {

    private val viewModel by viewModels<PriceFormViewModel>()
    private var binding: FragmentPriceFormBinding? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (savedInstanceState == null) {
            val priceUID = arguments?.getString(UxArgument.SELECTED_PRICE_UID)
            viewModel.load(priceUID)
        }

        lifecycleScope.launch {
            viewModel.command.collect {
                processCommand(it)
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.price_form, menu)
    }

    @Deprecated("Deprecated in Java")
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val price = viewModel.price.value

        return when (item.itemId) {
            R.id.menu_save -> {
                val binding = this.binding ?: return false
                if (validate(binding, price)) {
                    viewModel.onSavePriceClick()
                    true
                } else {
                    false
                }
            }

            R.id.menu_delete -> {
                viewModel.onDeletePriceClick()
                true
            }

            R.id.menu_duplicate -> {
                viewModel.onDuplicatePriceClick()
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val binding = FragmentPriceFormBinding.inflate(inflater, container, false)
        this.binding = binding
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val actionBar: ActionBar? = this.actionBar
        actionBar?.setTitle(R.string.price_editor)

        val binding = this.binding!!
        val context: Context = binding.root.context
        val typesAdapter = PriceTypeAdapter(context)
        val namespaceAdapter = NamespaceAdapter(context)

        val securitiesAdapter = CommoditiesAdapter(context, viewLifecycleOwner).load { adapter ->
            val price = viewModel.price.value
            val position = adapter.getValuePosition(price.security)
            binding.security.setSelection(position)
        }
        binding.security.adapter = securitiesAdapter
        binding.security.onItemSelectedListener =
            DefaultItemSelectedListener { parent: AdapterView<*>,
                                          view: View?,
                                          position: Int,
                                          id: Long ->
                if (view == null) return@DefaultItemSelectedListener
                val commodity = securitiesAdapter.getCommodity(position)!!
                viewModel.onSecuritySelected(commodity)
            }

        val commoditiesAdapter = CommoditiesAdapter(context, viewLifecycleOwner).load { adapter ->
            val price = viewModel.price.value
            binding.currency.setSelection(adapter.getValuePosition(price.currency))
        }
        binding.currency.adapter = commoditiesAdapter
        binding.currency.onItemSelectedListener =
            DefaultItemSelectedListener { parent: AdapterView<*>,
                                          view: View?,
                                          position: Int,
                                          id: Long ->
                if (view == null) return@DefaultItemSelectedListener
                val commodity = commoditiesAdapter.getCommodity(position)!!
                viewModel.onCurrencySelected(commodity)
            }

        binding.namespace.adapter = namespaceAdapter
        binding.namespace.onItemSelectedListener =
            DefaultItemSelectedListener { parent: AdapterView<*>,
                                          view: View?,
                                          position: Int,
                                          id: Long ->
                if (view == null) return@DefaultItemSelectedListener
                val namespace = namespaceAdapter.getType(position)!!
                securitiesAdapter.setNamespace(namespace)

                if (securitiesAdapter.isLoaded) {
                    val price = viewModel.price.value
                    val position = securitiesAdapter.getValuePosition(price.security)
                    if (position >= 0) {
                        binding.security.setSelection(position)
                    } else {
                        viewModel.onSecuritySelected(Commodity.template)
                    }
                }
            }

        binding.type.adapter = typesAdapter
        binding.type.onItemSelectedListener =
            DefaultItemSelectedListener { parent: AdapterView<*>,
                                          view: View?,
                                          position: Int,
                                          id: Long ->
                if (view == null) return@DefaultItemSelectedListener
                val type = typesAdapter.getType(position)!!
                viewModel.onTypeSelected(type)
            }

        val dateListener: DatePickerDialog.OnDateSetListener = { _, year, month, dayOfMonth ->
            viewModel.onDateSelected(year, month, dayOfMonth)
        }
        binding.inputDate.setOnClickListener {
            val price = viewModel.price.value
            val dateMillis = price.date
            DatePickerDialogFragment.newInstance(dateListener, dateMillis)
                .show(parentFragmentManager, "date_picker_dialog")
        }

        val timeListener: TimePickerDialog.OnTimeSetListener = { _, hourOfDay, minute ->
            viewModel.onTimeSelected(hourOfDay, minute)
        }
        binding.inputTime.setOnClickListener {
            val price = viewModel.price.value
            val timeMillis = price.date
            TimePickerDialogFragment.newInstance(timeListener, timeMillis)
                .show(parentFragmentManager, "time_picker_dialog")
        }

        binding.inputExchangeRate.addValueChangedListener(object :
            CalculatorEditText.OnValueChangedListener {
            override fun onValueChanged(value: BigDecimal?) {
                viewModel.onPriceChanged(value)
            }
        })

        binding.error.text = null

        lifecycleScope.launch {
            viewModel.price.collect { price ->
                bind(binding, price)
            }
        }
    }

    private fun bind(binding: FragmentPriceFormBinding, price: Price) {
        val namespaceAdapter = binding.namespace.adapter as NamespaceAdapter
        val namespaceIndex = namespaceAdapter.getCommodityPosition(price.security)
        binding.namespace.setSelection(namespaceIndex)

        val securityAdapter = binding.security.adapter as CommoditiesAdapter
        val securityIndex = securityAdapter.getValuePosition(price.security)
        binding.security.setSelection(securityIndex)

        val currencyAdapter = binding.currency.adapter as CommoditiesAdapter
        val currencyIndex = currencyAdapter.getValuePosition(price.currency)
        binding.currency.setSelection(currencyIndex)

        binding.inputDate.text = DATE_FORMATTER.print(price.date)
        binding.inputTime.text = TIME_FORMATTER.print(price.date)

        val typeAdapter = binding.type.adapter as PriceTypeAdapter
        val typeIndex = typeAdapter.getValuePosition(price.type)
        binding.type.setSelection(typeIndex)

        binding.inputExchangeRate.commodity = price.currency
        binding.inputExchangeRate.setValue(price.toBigDecimal(), true)
    }

    private fun processCommand(command: PriceFormViewModel.Command) {
        when (command) {
            PriceFormViewModel.Command.Done -> parentFragmentManager.popBackStack()
            PriceFormViewModel.Command.None -> Unit
        }
        viewModel.markCommandProcessed()
    }

    private fun validate(binding: FragmentPriceFormBinding, price: Price): Boolean {
        binding.error.text = null
        binding.error.requestFocus()

        if (price.security.isTemplate) {
            binding.error.setText(R.string.price_error_security)
            binding.security.requestFocus()
            return false
        }

        if (!price.currency.isCurrency) {
            binding.error.setText(R.string.price_error_currency)
            binding.currency.requestFocus()
            return false
        }

        if (binding.inputExchangeRate.isInputValid) {
            val rate = binding.inputExchangeRate.value ?: return false
            price.setExchangeRate(rate)
            viewModel.onPriceChanged(rate)
        } else {
            binding.error.setText(R.string.price_error_amount)
            binding.inputExchangeRate.requestFocus()
            return false
        }
        if (price.valueNum == 0L || price.valueDenom == 0L) {
            binding.error.setText(R.string.price_error_amount)
            binding.inputExchangeRate.requestFocus()
            return false
        }
        if (price.valueNum < 0L) {
            binding.error.setText(R.string.price_error_price)
            binding.inputExchangeRate.requestFocus()
            return false
        }

        return true
    }
}