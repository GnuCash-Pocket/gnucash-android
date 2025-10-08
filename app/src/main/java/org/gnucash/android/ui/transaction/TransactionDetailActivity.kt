package org.gnucash.android.ui.transaction

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.database.SQLException
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.annotation.ColorInt
import androidx.appcompat.app.ActionBar
import androidx.core.view.isVisible
import androidx.fragment.app.FragmentResultListener
import org.gnucash.android.R
import org.gnucash.android.app.GnuCashApplication.Companion.isDoubleEntryEnabled
import org.gnucash.android.app.GnuCashApplication.Companion.shouldBackupTransactions
import org.gnucash.android.databinding.ActivityTransactionDetailBinding
import org.gnucash.android.databinding.ItemSplitAmountInfoBinding
import org.gnucash.android.databinding.RowBalanceBinding
import org.gnucash.android.db.adapter.AccountsDbAdapter
import org.gnucash.android.db.adapter.AccountsDbAdapter.Companion.ALWAYS
import org.gnucash.android.db.adapter.ScheduledActionDbAdapter
import org.gnucash.android.db.adapter.TransactionsDbAdapter
import org.gnucash.android.model.Split
import org.gnucash.android.model.Transaction
import org.gnucash.android.model.TransactionType
import org.gnucash.android.ui.common.FormActivity
import org.gnucash.android.ui.common.Refreshable
import org.gnucash.android.ui.common.UxArgument
import org.gnucash.android.ui.homescreen.WidgetConfigurationActivity.Companion.updateAllWidgets
import org.gnucash.android.ui.passcode.PasscodeLockActivity
import org.gnucash.android.ui.transaction.dialog.BulkMoveDialogFragment
import org.gnucash.android.ui.util.displayBalance
import org.gnucash.android.util.BackupManager.backupActiveBookAsync
import org.gnucash.android.util.formatFullDate
import timber.log.Timber
import java.util.MissingFormatArgumentException

/**
 * Activity for displaying transaction information
 *
 * @author Ngewi Fet <ngewif@gmail.com>
 */
class TransactionDetailActivity : PasscodeLockActivity(), FragmentResultListener, Refreshable {
    private var transactionUID: String? = null
    private var accountUID: String? = null

    private lateinit var binding: ActivityTransactionDetailBinding
    private var transactionsDbAdapter = TransactionsDbAdapter.instance

    private val accountsDbAdapter = AccountsDbAdapter.instance

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityTransactionDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        transactionsDbAdapter = TransactionsDbAdapter.instance
        transactionUID = intent.getStringExtra(UxArgument.SELECTED_TRANSACTION_UID)
        accountUID = intent.getStringExtra(UxArgument.SELECTED_ACCOUNT_UID)

        if (transactionUID.isNullOrEmpty() || accountUID.isNullOrEmpty()) {
            throw MissingFormatArgumentException("You must specify both the transaction and account GUID")
        }

        setSupportActionBar(binding.toolbar)
        val actionBar: ActionBar? = supportActionBar
        actionBar?.setHomeButtonEnabled(true)
        actionBar?.setDisplayHomeAsUpEnabled(true)
        actionBar?.setDisplayShowTitleEnabled(false)

        @ColorInt val accountColor = accountsDbAdapter.getActiveAccountColor(this, accountUID)
        setTitlesColor(accountColor)
        binding.toolbar.setBackgroundColor(accountColor)

        bindViews(binding)
    }

    override fun onFragmentResult(requestKey: String, result: Bundle) {
        if (BulkMoveDialogFragment.TAG == requestKey) {
            val accountUID = result.getString(UxArgument.SELECTED_ACCOUNT_UID)
            if (!accountUID.isNullOrEmpty()) {
                this.accountUID = accountUID
            }
            val refresh = result.getBoolean(Refreshable.EXTRA_REFRESH)
            if (refresh) refresh()
        }
    }

    private fun bind(binding: ItemSplitAmountInfoBinding, split: Split) {
        val account = accountsDbAdapter.getRecord(split.accountUID!!)
        binding.splitAccountName.text = account.fullName
        val balanceView =
            if (split.type == TransactionType.DEBIT) binding.splitDebit else binding.splitCredit
        @ColorInt val colorBalanceZero = balanceView.currentTextColor
        balanceView.displayBalance(split.getFormattedQuantity(account), colorBalanceZero)
    }

    private fun bind(binding: RowBalanceBinding, accountUID: String, timeMillis: Long) {
        val account = accountsDbAdapter.getRecord(accountUID)
        val accountBalance = accountsDbAdapter.getAccountBalance(accountUID, ALWAYS, timeMillis, true)
        val balanceTextView =
            if (account.accountType.hasDebitDisplayBalance) binding.balanceDebit else binding.balanceCredit
        balanceTextView.displayBalance(accountBalance, balanceTextView.currentTextColor)
    }

    /**
     * Reads the transaction information from the database and binds it to the views
     */
    private fun bindViews(binding: ActivityTransactionDetailBinding) {
        // Remove all rows that are not special.
        binding.transactionItems.removeAllViews()

        val transaction = transactionsDbAdapter.getRecord(transactionUID!!)

        binding.trnDescription.text = transaction.description
        binding.transactionAccount.text = getString(
            R.string.label_inside_account_with_name,
            accountsDbAdapter.getAccountFullName(accountUID!!)
        )

        val useDoubleEntry = isDoubleEntryEnabled(this)
        val context: Context = this
        val inflater = layoutInflater
        for (split in transaction.splits) {
            if (!useDoubleEntry) {
                //do now show imbalance accounts for single entry use case
                continue
            }
            val imbalanceUID =
                accountsDbAdapter.getImbalanceAccountUID(context, split.value.commodity)
            if (split.accountUID == imbalanceUID) {
                //do now show imbalance accounts for single entry use case
                continue
            }
            val splitBinding =
                ItemSplitAmountInfoBinding.inflate(inflater, binding.transactionItems, true)
            bind(splitBinding, split)
        }

        val balanceBinding = RowBalanceBinding.inflate(inflater, binding.transactionItems, true)
        bind(balanceBinding, accountUID!!, transaction.time)

        val timeAndDate = formatFullDate(transaction.time)
        binding.trnTimeAndDate.text = timeAndDate

        if (!transaction.note.isNullOrEmpty()) {
            binding.notes.text = transaction.note
            binding.rowTrnNotes.isVisible = true
        } else {
            binding.rowTrnNotes.isVisible = false
        }

        val actionUID = transaction.scheduledActionUID
        if (actionUID != null) {
            val scheduledAction =
                ScheduledActionDbAdapter.instance.getRecord(actionUID)
            binding.trnRecurrence.text = scheduledAction.getRepeatString(context)
            binding.rowTrnRecurrence.isVisible = true
        } else {
            binding.rowTrnRecurrence.isVisible = false
        }

        binding.fabEditTransaction.setOnClickListener { editTransaction() }
    }

    override fun refresh() {
        refresh(transactionUID)
    }

    override fun refresh(uid: String?) {
        transactionUID = uid
        bindViews(binding)
    }

    private fun editTransaction() {
        val intent = Intent(this, FormActivity::class.java)
            .setAction(Intent.ACTION_INSERT_OR_EDIT)
            .putExtra(UxArgument.SELECTED_ACCOUNT_UID, accountUID)
            .putExtra(UxArgument.SELECTED_TRANSACTION_UID, transactionUID)
            .putExtra(UxArgument.FORM_TYPE, FormActivity.FormType.TRANSACTION.name)
        startActivityForResult(intent, REQUEST_REFRESH)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        super.onCreateOptionsMenu(menu)
        menuInflater.inflate(R.menu.transactions_context_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                finish()
                return true
            }

            R.id.menu_move -> {
                moveTransaction(transactionUID)
                return true
            }

            R.id.menu_duplicate -> {
                duplicateTransaction(transactionUID)
                return true
            }

            R.id.menu_delete -> {
                deleteTransaction(transactionUID)
                return true
            }

            else -> return super.onOptionsItemSelected(item)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (resultCode == RESULT_OK) {
            refresh()
            return
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    private fun moveTransaction(transactionUID: String?) {
        if (transactionUID.isNullOrEmpty()) return
        val uids = arrayOf<String>(transactionUID)
        val fragment = BulkMoveDialogFragment.newInstance(uids, accountUID!!)
        val fm = supportFragmentManager
        fm.setFragmentResultListener(BulkMoveDialogFragment.TAG, this, this)
        fragment.show(fm, BulkMoveDialogFragment.TAG)
    }

    private fun deleteTransaction(transactionUID: String?) {
        if (transactionUID.isNullOrEmpty()) return

        val activity: Activity = this
        if (shouldBackupTransactions(activity)) {
            backupActiveBookAsync(activity) { result ->
                transactionsDbAdapter.deleteRecord(transactionUID)
                updateAllWidgets(activity)
                finish()
            }
        } else {
            transactionsDbAdapter.deleteRecord(transactionUID)
            updateAllWidgets(activity)
            finish()
        }
    }

    private fun duplicateTransaction(transactionUID: String?) {
        if (transactionUID.isNullOrEmpty()) return

        val transaction = transactionsDbAdapter.getRecord(transactionUID)
        val duplicate = Transaction(transaction)
        duplicate.time = System.currentTimeMillis()
        try {
            transactionsDbAdapter.insert(duplicate)
            if (duplicate.id <= 0) return
        } catch (e: SQLException) {
            Timber.e(e)
            return
        }

        // Show the new transaction
        val intent = Intent(intent)
            .putExtra(UxArgument.SELECTED_TRANSACTION_UID, duplicate.uid)
            .putExtra(UxArgument.SELECTED_ACCOUNT_UID, accountUID)
        startActivity(intent)
    }

    companion object {
        // "ForResult" to force refresh afterwards.
        private const val REQUEST_REFRESH = 0x0000
    }
}
