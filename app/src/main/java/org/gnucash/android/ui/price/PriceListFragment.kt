package org.gnucash.android.ui.price

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.ActionBar
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.coroutines.launch
import org.gnucash.android.R
import org.gnucash.android.app.MenuFragment
import org.gnucash.android.app.actionBar
import org.gnucash.android.databinding.FragmentPriceListBinding
import org.gnucash.android.model.Price
import org.gnucash.android.ui.common.UxArgument

class PriceListFragment : MenuFragment() {

    private val viewModel by viewModels<PriceListViewModel>()
    private var binding: FragmentPriceListBinding? = null
    private val pricesAdapter = PriceCursorAdapter(
        onEditPriceClick = ::onEditPriceClick,
        onDeletePriceClick = ::onDeletePriceClick,
        onDuplicatePriceClick = ::onDuplicatePriceClick,
    )

    @Deprecated("Deprecated in Java")
    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.prices_actions, menu)
    }

    @Deprecated("Deprecated in Java")
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_add -> {
                viewModel.onAddPriceClick()
                true
            }

            R.id.menu_delete -> {
                viewModel.onRemoveOldPricesClick()
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
        val binding = FragmentPriceListBinding.inflate(inflater, container, false)
        this.binding = binding
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val actionBar: ActionBar? = this.actionBar
        actionBar?.setTitle(R.string.price_database)

        val binding = this.binding!!

        binding.list.apply {
            setHasFixedSize(true)
            setLayoutManager(LinearLayoutManager(context))
            emptyView = binding.empty
            adapter = pricesAdapter
        }

        binding.fabAdd.setOnClickListener {
            viewModel.onAddPriceClick()
        }

        lifecycleScope.launch {
            viewModel.prices.collect { cursor ->
                pricesAdapter.changeCursor(cursor)
            }
        }

        lifecycleScope.launch {
            viewModel.command.collect {
                processCommand(it)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.load()
    }

    override fun onDestroy() {
        pricesAdapter.changeCursor(null)
        super.onDestroy()
    }

    private fun processCommand(command: PriceListViewModel.Command) {
        when (command) {
            PriceListViewModel.Command.Done -> Unit
            PriceListViewModel.Command.None -> Unit
            PriceListViewModel.Command.RemoveOld -> TODO()
            is PriceListViewModel.Command.Edit -> showPriceEditor(command.price)
            is PriceListViewModel.Command.NotifyDeleted -> notifyDeleted(command.price)
            is PriceListViewModel.Command.NotifyInserted -> notifyInserted(command.price)
        }
        viewModel.markCommandProcessed()
    }

    private fun onEditPriceClick(price: Price) {
        viewModel.onEditPriceClick(price)
    }

    private fun onDeletePriceClick(price: Price) {
        viewModel.onDeletePriceClick(price)
    }

    private fun onDuplicatePriceClick(price: Price) {
        viewModel.onDuplicatePriceClick(price)
    }

    private fun showPriceEditor(price: Price?) {
        val args = Bundle()
        args.putString(UxArgument.SELECTED_PRICE_UID, price?.uid)

        val fragment = PriceFormFragment().apply {
            arguments = args
        }

        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .addToBackStack("edit")
            .commitAllowingStateLoss()
    }

    private fun notifyDeleted(price: Price) {
        val position = pricesAdapter.getItemPosition(price.uid)
        pricesAdapter.notifyItemRemoved(position)
    }

    private fun notifyInserted(price: Price) {
        val position = pricesAdapter.getItemPosition(price.uid)
        pricesAdapter.notifyItemInserted(position)
    }
}