package org.gnucash.android.ui.search

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.ActionBar
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentResultListener
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.coroutines.launch
import org.gnucash.android.R
import org.gnucash.android.app.GnuCashApplication.Companion.isDoubleEntryEnabled
import org.gnucash.android.app.GnuCashApplication.Companion.shouldBackupTransactions
import org.gnucash.android.app.actionBar
import org.gnucash.android.databinding.FragmentTransactionsListBinding
import org.gnucash.android.model.Transaction
import org.gnucash.android.model.TransactionType
import org.gnucash.android.ui.common.FormActivity
import org.gnucash.android.ui.common.Refreshable
import org.gnucash.android.ui.common.UxArgument
import org.gnucash.android.ui.homescreen.WidgetConfigurationActivity.Companion.updateAllWidgets
import org.gnucash.android.ui.transaction.TransactionDetailActivity
import org.gnucash.android.ui.transaction.dialog.BulkMoveDialogFragment
import org.gnucash.android.util.BackupManager.backupActiveBookAsync

class SearchResultsFragment : Fragment(), SearchResultCallback, FragmentResultListener {
    private val viewModel by viewModels<SearchResultsViewModel>()
    private var binding: FragmentTransactionsListBinding? = null
    private var transactionsAdapter: SearchResultsAdapter? = null
    private var isDoubleEntry = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val context = requireContext()

        if (savedInstanceState == null) {
            viewModel.form = requireArguments().getForm()
        }
        isDoubleEntry = isDoubleEntryEnabled(context)

        transactionsAdapter = SearchResultsAdapter(null, isDoubleEntry, this)
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
    ): View? {
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
        if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
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
            SearchResultAction.Edit -> edit(requireContext(), transaction)
            SearchResultAction.Move -> move(transaction)
            SearchResultAction.View -> view(requireContext(), transaction)
        }
    }

    private fun delete(transaction: Transaction) {
        val context: Context = requireContext()
        if (shouldBackupTransactions(context)) {
            backupActiveBookAsync(activity) { result ->
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

    private fun edit(context: Context, transaction: Transaction) {
        val accountUID = transaction.getDefaultAccountUID(TransactionType.CREDIT) ?: return
        val intent = Intent(context, FormActivity::class.java)
            .putExtra(UxArgument.FORM_TYPE, FormActivity.FormType.TRANSACTION.name)
            .putExtra(UxArgument.SELECTED_TRANSACTION_UID, transaction.uid)
            .putExtra(UxArgument.SELECTED_ACCOUNT_UID, accountUID)
        startActivity(intent)
    }

    private fun move(transaction: Transaction) {
        val accountUID = transaction.getDefaultAccountUID(TransactionType.CREDIT) ?: return
        val uids = arrayOf(transaction.uid)
        val fm = parentFragmentManager
        fm.setFragmentResultListener(BulkMoveDialogFragment.TAG, viewLifecycleOwner, this)
        val fragment = BulkMoveDialogFragment.newInstance(uids, accountUID)
        fragment.show(fm, BulkMoveDialogFragment.TAG)
    }

    private fun view(context: Context, transaction: Transaction) {
        val accountUID = transaction.getDefaultAccountUID(TransactionType.CREDIT) ?: return
        val intent = Intent(context, TransactionDetailActivity::class.java)
            .putExtra(UxArgument.SELECTED_TRANSACTION_UID, transaction.uid)
            .putExtra(UxArgument.SELECTED_ACCOUNT_UID, accountUID)
        startActivity(intent)
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
}