package org.gnucash.android.ui.search

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.ActionBar
import androidx.fragment.app.FragmentResultListener
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.coroutines.launch
import org.gnucash.android.R
import org.gnucash.android.app.DatabaseFragment
import org.gnucash.android.app.GnuCashApplication.Companion.isDoubleEntryEnabled
import org.gnucash.android.app.GnuCashApplication.Companion.shouldBackupTransactions
import org.gnucash.android.app.actionBar
import org.gnucash.android.app.isLandscape
import org.gnucash.android.databinding.FragmentTransactionsListBinding
import org.gnucash.android.model.Transaction
import org.gnucash.android.model.TransactionType
import org.gnucash.android.ui.common.FormActivity
import org.gnucash.android.ui.common.Refreshable
import org.gnucash.android.ui.homescreen.WidgetConfigurationActivity.Companion.updateAllWidgets
import org.gnucash.android.ui.transaction.TransactionDetailActivity
import org.gnucash.android.ui.transaction.dialog.BulkMoveDialogFragment
import org.gnucash.android.util.BackupManager.backupActiveBookAsync
import timber.log.Timber

class SearchResultsFragment : DatabaseFragment(), SearchResultCallback, FragmentResultListener {
    private val viewModel: SearchResultsViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val transactionsDbAdapter = readableDatabaseHolder.transactionsDbAdapter
                return SearchResultsViewModel(transactionsDbAdapter) as T
            }
        }
    }
    private var binding: FragmentTransactionsListBinding? = null
    private var transactionsAdapter: SearchResultsAdapter? = null
    private var isDoubleEntry = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val context = requireContext()

        viewModel.where = requireArguments().getString(EXTRA_FORM)
        isDoubleEntry = isDoubleEntryEnabled(context)

        val accountsDbAdapter = readableDatabaseHolder.accountsDbAdapter
        transactionsAdapter = SearchResultsAdapter(null, accountsDbAdapter, isDoubleEntry, this)
        lifecycleScope.launch {
            viewModel.results.collect { cursor ->
                transactionsAdapter?.changeCursor(cursor)
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val binding = FragmentTransactionsListBinding.inflate(inflater, container, false)
        this.binding = binding
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val actionBar: ActionBar? = this.actionBar
        actionBar?.setTitle(R.string.title_search)

        val binding = this.binding!!
        val context = binding.list.context

        binding.list.setHasFixedSize(true)
        if (context.isLandscape) {
            binding.list.setLayoutManager(GridLayoutManager(context, 2))
        } else {
            binding.list.setLayoutManager(LinearLayoutManager(context))
        }
        binding.list.emptyView = binding.empty
        binding.list.adapter = transactionsAdapter
    }

    override fun invoke(transaction: Transaction, action: SearchResultAction) {
        when (action) {
            SearchResultAction.Delete -> delete(transaction)
            SearchResultAction.Duplicate -> duplicate(transaction)
            SearchResultAction.Edit -> edit(transaction)
            SearchResultAction.Move -> move(transaction)
            SearchResultAction.View -> view(transaction)
        }
    }

    private fun delete(transaction: Transaction) {
        val context: Context = requireContext()
        if (shouldBackupTransactions(context)) {
            backupActiveBookAsync(activity) { _ ->
                deleteImpl(context, transaction)
            }
        } else {
            deleteImpl(context, transaction)
        }
    }

    private fun deleteImpl(context: Context, transaction: Transaction) {
        viewModel.delete(transaction)
        updateAllWidgets(context)
    }

    private fun duplicate(transaction: Transaction) {
        viewModel.duplicate(transaction)
    }

    private fun edit(transaction: Transaction) {
        val transactionUID = transaction.uid
        val accountUID = transaction.getDefaultAccountUID(TransactionType.DEBIT)
        if (transactionUID.isEmpty() || accountUID.isNullOrEmpty()) {
            Timber.w("You must specify both the transaction and account UID")
            return
        }
        FormActivity.showEditTransaction(this, bookUID, accountUID, transactionUID, 0)
    }

    private fun move(transaction: Transaction) {
        val transactionUID = transaction.uid
        val accountUID = transaction.getDefaultAccountUID(TransactionType.DEBIT)
        if (transactionUID.isEmpty() || accountUID.isNullOrEmpty()) {
            Timber.w("You must specify both the transaction and account UID")
            return
        }
        val uids = arrayOf(transactionUID)
        val fm = parentFragmentManager
        fm.setFragmentResultListener(BulkMoveDialogFragment.TAG, viewLifecycleOwner, this)
        val fragment = BulkMoveDialogFragment.newInstance(uids, accountUID)
        fragment.show(fm, BulkMoveDialogFragment.TAG)
    }

    private fun view(transaction: Transaction) {
        val transactionUID = transaction.uid
        val accountUID = transaction.getDefaultAccountUID(TransactionType.DEBIT)
        if (transactionUID.isEmpty() || accountUID.isNullOrEmpty()) {
            Timber.w("You must specify both the transaction and account UID")
            return
        }
        TransactionDetailActivity.show(this, bookUID, accountUID, transactionUID, 0)
    }

    override fun onFragmentResult(requestKey: String, result: Bundle) {
        if (BulkMoveDialogFragment.TAG == requestKey) {
            val refresh = result.getBoolean(Refreshable.EXTRA_REFRESH)
            if (refresh) refresh()
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        viewModel.search()
    }

    companion object {
        const val EXTRA_FORM = "search_where"
    }
}