/*
 * Copyright (c) 2016 Ngewi Fet <ngewif@gmail.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gnucash.android.ui.settings

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.ActionBar
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDialog
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentResultListener
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import org.gnucash.android.R
import org.gnucash.android.app.GnuCashApplication
import org.gnucash.android.app.GnuCashApplication.Companion.defaultCurrencyCode
import org.gnucash.android.app.MenuFragment
import org.gnucash.android.app.actionBar
import org.gnucash.android.app.finish
import org.gnucash.android.databinding.CardviewBookBinding
import org.gnucash.android.databinding.FragmentBookListBinding
import org.gnucash.android.db.DatabaseHelper
import org.gnucash.android.db.DatabaseSchema.BookEntry
import org.gnucash.android.db.adapter.AccountsDbAdapter
import org.gnucash.android.db.adapter.BooksDbAdapter
import org.gnucash.android.db.adapter.TransactionsDbAdapter
import org.gnucash.android.importer.AccountsTemplate
import org.gnucash.android.model.Book
import org.gnucash.android.ui.account.AccountsActivity
import org.gnucash.android.ui.adapter.AccountsTemplatesAdapter
import org.gnucash.android.ui.adapter.ModelDiff
import org.gnucash.android.ui.adapter.SpinnerItem
import org.gnucash.android.ui.common.Refreshable
import org.gnucash.android.ui.get
import org.gnucash.android.ui.settings.dialog.DeleteBookConfirmationDialog
import org.gnucash.android.util.BookUtils.loadBook
import org.gnucash.android.util.PreferencesHelper.getLastExportTime
import org.gnucash.android.util.chooseDocument
import org.gnucash.android.util.formatMediumDateTime
import org.gnucash.android.util.openBook
import timber.log.Timber

/**
 * Fragment for managing the books in the database
 */
class BookManagerFragment : MenuFragment(), Refreshable, FragmentResultListener {
    private var booksAdapter: BooksAdapter? = null
    private var accountsTemplatesAdapter: AccountsTemplatesAdapter? = null
    private var binding: FragmentBookListBinding? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val binding = FragmentBookListBinding.inflate(inflater, container, false)
        this.binding = binding
        return binding.root
    }

    override fun onDestroy() {
        super.onDestroy()
        accountsTemplatesAdapter = null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val actionBar: ActionBar? = this.actionBar
        actionBar?.setTitle(R.string.title_manage_books)

        val binding = binding!!
        val context = binding.list.context
        booksAdapter = BooksAdapter()

        binding.list.setLayoutManager(LinearLayoutManager(context))
        binding.list.emptyView = binding.empty
        binding.list.adapter = booksAdapter
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    @Deprecated("Deprecated in Java")
    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.book_list_actions, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_create -> {
                val activity: Activity? = activity
                if (activity == null) {
                    Timber.w("Activity expected")
                    return false
                }
                createBook(activity)
                return true
            }

            R.id.menu_open -> {
                chooseDocument(REQUEST_OPEN_DOCUMENT)
                return true
            }

            else -> return false
        }
    }

    override fun refresh() {
        if (isDetached) return
        val booksDbAdapter = BooksDbAdapter.instance
        val records = booksDbAdapter.allRecords
        booksAdapter?.submitList(records)
    }

    override fun refresh(uid: String?) {
        refresh()
    }

    override fun onFragmentResult(requestKey: String, result: Bundle) {
        if (DeleteBookConfirmationDialog.TAG == requestKey) {
            val refresh = result.getBoolean(Refreshable.EXTRA_REFRESH)
            if (refresh) refresh()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (resultCode == Activity.RESULT_OK) {
            if (requestCode == REQUEST_OPEN_DOCUMENT) {
                openBook(requireActivity(), data)
                return
            }
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    private fun createBook(activity: Activity) {
        var adapter = accountsTemplatesAdapter
        if (adapter == null) {
            adapter = AccountsTemplatesAdapter(activity)
            accountsTemplatesAdapter = adapter
        }

        AlertDialog.Builder(activity)
            .setTitle(R.string.title_create_default_accounts)
            .setCancelable(true)
            .setNegativeButton(R.string.alert_dialog_cancel) { _, _ ->
                // Dismisses itself
            }
            .setSingleChoiceItems(adapter, RecyclerView.NO_POSITION) { dialog, which ->
                val item = adapter[which] as SpinnerItem<AccountsTemplate.Header>
                val header = item.value
                val fileId = header.assetId
                dialog.dismiss()
                val currencyCode = defaultCurrencyCode
                AccountsActivity.createDefaultAccounts(activity, currencyCode, fileId, null)
            }
            .show()
    }

    private inner class BooksAdapter : ListAdapter<Book, BookViewHolder>(ModelDiff<Book>()) {
        override fun onCreateViewHolder(
            parent: ViewGroup,
            viewType: Int
        ): BookViewHolder {
            val inflater = LayoutInflater.from(parent.context)
            val binding = CardviewBookBinding.inflate(inflater, parent, false)
            return BookViewHolder(binding)
        }

        override fun onBindViewHolder(
            holder: BookViewHolder,
            position: Int
        ) {
            val item = getItem(position)
            holder.bind(item)
        }
    }

    inner class BookViewHolder(private val binding: CardviewBookBinding) :
        RecyclerView.ViewHolder(binding.root) {
        private val activeBookUID = GnuCashApplication.activeBookUID

        private val nameView = binding.listItem2Lines.primaryText
        private val statsView = binding.listItem2Lines.secondaryText
        private val lastSyncLabel = binding.labelLastSync
        private val lastSyncTimeView = binding.lastSyncTime
        private val optionsMenuView = binding.optionsMenu

        fun bind(book: Book) {
            val bookUID = book.uid

            nameView.text = book.displayName
            if (activeBookUID == bookUID) {
                val context = itemView.context
                nameView.setTextColor(ContextCompat.getColor(context, R.color.theme_primary))
            }

            setLastExportedText(book)
            setStatisticsText(book)
            setUpMenu(book)

            itemView.setOnClickListener { v ->
                //do nothing if the active book is tapped
                if (activeBookUID != bookUID) {
                    loadBook(v.context, bookUID)
                    finish()
                }
            }
        }

        fun setUpMenu(book: Book) {
            val bookUID = book.uid
            val bookName = book.displayName
            optionsMenuView.setOnClickListener { v ->
                val popupMenu = PopupMenu(v.context, v)
                val inflater = popupMenu.menuInflater
                inflater.inflate(R.menu.book_context_menu, popupMenu.menu)

                popupMenu.setOnMenuItemClickListener { item ->
                    when (item.itemId) {
                        R.id.menu_rename -> handleMenuRenameBook(bookName, bookUID)
                        R.id.menu_delete -> handleMenuDeleteBook(bookUID)
                        else -> true
                    }
                }

                if (activeBookUID == bookUID) { //we cannot delete the active book
                    popupMenu.menu.findItem(R.id.menu_delete).isEnabled = false
                }
                popupMenu.show()
            }
        }

        fun handleMenuDeleteBook(bookUID: String): Boolean {
            val fm = parentFragmentManager
            fm.setFragmentResultListener(
                DeleteBookConfirmationDialog.TAG,
                this@BookManagerFragment,
                this@BookManagerFragment
            )
            DeleteBookConfirmationDialog.newInstance(bookUID)
                .show(fm, DeleteBookConfirmationDialog.TAG)
            return true
        }

        /**
         * Opens a dialog for renaming a book
         *
         * @param bookName Current name of the book
         * @param bookUID  GUID of the book
         * @return `true`
         */
        fun handleMenuRenameBook(bookName: String?, bookUID: String): Boolean {
            val dialog = AlertDialog.Builder(requireActivity())
                .setTitle(R.string.title_rename_book)
                .setView(R.layout.dialog_rename_book)
                .setNegativeButton(R.string.btn_cancel) { _, _ ->
                    // Dismisses itself
                }
                .setPositiveButton(R.string.btn_rename) { dialog, _ ->
                    val bookTitle =
                        (dialog as AppCompatDialog).findViewById<EditText>(R.id.input_book_title)!!
                    val bookName = bookTitle.getText().toString().trim()
                    BooksDbAdapter.instance.updateRecord(
                        bookUID,
                        BookEntry.COLUMN_DISPLAY_NAME,
                        bookName
                    )
                    refresh()
                }
                .show()
            val titleView = dialog.findViewById<TextView>(R.id.input_book_title)!!
            titleView.text = bookName
            return true
        }

        fun setLastExportedText(book: Book) {
            val bookUID = book.uid
            val context = itemView.context
            lastSyncLabel.setText(R.string.label_last_export_time)

            val lastSyncTime = getLastExportTime(context, bookUID)
            if (lastSyncTime.time <= 0L) {
                lastSyncTimeView.setText(R.string.last_export_time_never)
            } else {
                lastSyncTimeView.text = formatMediumDateTime(lastSyncTime.time)
            }
        }

        fun setStatisticsText(book: Book) {
            val bookUID = book.uid
            val context = itemView.context
            val dbHelper = DatabaseHelper(context, bookUID)
            val holder = dbHelper.holder
            val trnAdapter = TransactionsDbAdapter(holder)
            val transactionCount = trnAdapter.recordsCount
            val accountsDbAdapter = AccountsDbAdapter(trnAdapter)
            val accountsCount = accountsDbAdapter.recordsCount
            dbHelper.close()

            val transactionStats = resources.getQuantityString(
                R.plurals.book_transaction_stats,
                transactionCount.toInt(),
                transactionCount
            )
            val accountStats = resources.getQuantityString(
                R.plurals.book_account_stats,
                accountsCount.toInt(),
                accountsCount
            )
            val stats = "$accountStats, $transactionStats"
            statsView.text = stats
        }
    }

    companion object {
        private const val REQUEST_OPEN_DOCUMENT = 0x20
    }
}
