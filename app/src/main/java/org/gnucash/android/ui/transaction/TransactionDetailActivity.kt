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
import org.gnucash.android.app.requireArguments
import org.gnucash.android.databinding.ActivityTransactionDetailBinding
import org.gnucash.android.databinding.ItemSplitAmountInfoBinding
import org.gnucash.android.databinding.RowBalanceBinding
import org.gnucash.android.db.adapter.AccountsDbAdapter
import org.gnucash.android.db.adapter.AccountsDbAdapter.Companion.ALWAYS
import org.gnucash.android.db.adapter.ScheduledActionDbAdapter
import org.gnucash.android.db.adapter.TransactionsDbAdapter
import org.gnucash.android.model.Account
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

/**
 * Activity for displaying transaction information
 *
 * @author Ngewi Fet <ngewif@gmail.com>
 */
class TransactionDetailActivity : PasscodeLockActivity(), FragmentResultListener, Refreshable {
    private var transaction: Transaction? = null
    private var account: Account? = null

    private var transactionsDbAdapter = TransactionsDbAdapter.instance
    private var accountsDbAdapter = AccountsDbAdapter.instance

    private lateinit var binding: ActivityTransactionDetailBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityTransactionDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        val actionBar: ActionBar? = supportActionBar
        actionBar?.setHomeButtonEnabled(true)
        actionBar?.setDisplayHomeAsUpEnabled(true)
        actionBar?.setDisplayShowTitleEnabled(false)

        transactionsDbAdapter = TransactionsDbAdapter.instance
        accountsDbAdapter = AccountsDbAdapter.instance

        handleIntent()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent()
    }

    private fun handleIntent() {
        this.transaction = requireTransaction()
        this.account = requireAccount()
    }

    override fun onResume() {
        super.onResume()

        @ColorInt val accountColor = accountsDbAdapter.getActiveAccountColor(this, account?.uid)
        setTitlesColor(accountColor)
        binding.toolbar.setBackgroundColor(accountColor)

        refresh()
    }

    override fun onFragmentResult(requestKey: String, result: Bundle) {
        if (BulkMoveDialogFragment.TAG == requestKey) {
            val accountUID = result.getString(UxArgument.SELECTED_ACCOUNT_UID)
            if (!accountUID.isNullOrEmpty()) {
                this.account = accountsDbAdapter.getRecord(accountUID)
            }
            val refresh = result.getBoolean(Refreshable.EXTRA_REFRESH)
            if (refresh) refresh()
        }
    }

    private fun bind(binding: ItemSplitAmountInfoBinding, split: Split) {
        val accountUID = account?.uid
        val splitAccountUID = split.accountUID!!
        val account = accountsDbAdapter.getRecord(splitAccountUID)
        binding.splitAccountName.text = account.fullName
        if (accountUID != splitAccountUID) {
            binding.splitAccountName.setOnClickListener { showAccount(split) }
        }
        val balanceView =
            if (split.type == TransactionType.DEBIT) binding.splitDebit else binding.splitCredit
        @ColorInt val colorBalanceZero = balanceView.currentTextColor
        balanceView.displayBalance(split.getFormattedQuantity(account), colorBalanceZero)
    }

    private fun bind(binding: RowBalanceBinding, account: Account, timeMillis: Long) {
        val accountBalance =
            accountsDbAdapter.getAccountBalance(account, ALWAYS, timeMillis, true)
        val balanceTextView =
            if (account.type.hasDebitDisplayBalance) binding.balanceDebit else binding.balanceCredit
        balanceTextView.displayBalance(accountBalance, balanceTextView.currentTextColor)
    }

    /**
     * Reads the transaction information from the database and binds it to the views
     */
    private fun bindViews(binding: ActivityTransactionDetailBinding, transaction: Transaction) {
        val account = requireAccount()
        // Remove all rows that are not special.
        binding.transactionItems.removeAllViews()

        binding.trnDescription.text = transaction.description
        binding.transactionAccount.text = getString(
            R.string.label_inside_account_with_name,
            account.fullName
        )

        val useDoubleEntry = isDoubleEntryEnabled(this)
        val context: Context = this
        val inflater = layoutInflater
        for (split in transaction.splits) {
            val imbalanceUID =
                accountsDbAdapter.getImbalanceAccountUID(context, split.value.commodity)
            if (split.accountUID == imbalanceUID) {
                //do now show imbalance accounts for single entry use case
                continue
            }
            val splitBinding =
                ItemSplitAmountInfoBinding.inflate(inflater, binding.transactionItems, true)
            bind(splitBinding, split)
            if (!useDoubleEntry) {
                break
            }
        }

        val balanceBinding = RowBalanceBinding.inflate(inflater, binding.transactionItems, true)
        bind(balanceBinding, account, transaction.datePosted)

        val timeAndDate = formatFullDate(transaction.datePosted)
        binding.trnTimeAndDate.text = timeAndDate

        if (transaction.number.isEmpty()) {
            binding.rowTrnNumber.isVisible = false
        } else {
            binding.number.text = transaction.number
            binding.rowTrnNumber.isVisible = true
        }

        if (transaction.notes.isEmpty()) {
            binding.rowTrnNotes.isVisible = false
        } else {
            binding.notes.text = transaction.notes
            binding.rowTrnNotes.isVisible = true
        }

        val actionUID = transaction.scheduledActionUID
        if (actionUID.isNullOrEmpty()) {
            binding.rowTrnRecurrence.isVisible = false
        } else {
            val scheduledAction =
                ScheduledActionDbAdapter.instance.getRecord(actionUID)
            binding.trnRecurrence.text = scheduledAction.getRepeatString(context)
            binding.rowTrnRecurrence.isVisible = true
        }

        binding.fabEdit.setOnClickListener {
            editTransaction(transaction, account)
        }
    }

    override fun refresh() {
        refresh(null)
    }

    override fun refresh(uid: String?) {
        this.transaction = null
        val transaction = requireTransaction()
        bindViews(binding, transaction)
    }

    private fun editTransaction(transaction: Transaction, account: Account) {
        val intent = Intent(this, FormActivity::class.java)
            .setAction(Intent.ACTION_INSERT_OR_EDIT)
            .putExtra(UxArgument.FORM_TYPE, FormActivity.FormType.TRANSACTION.name)
            .putExtra(UxArgument.SELECTED_ACCOUNT_UID, account.uid)
            .putExtra(UxArgument.SELECTED_TRANSACTION_UID, transaction.uid)
        startActivityForResult(intent, REQUEST_REFRESH)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        super.onCreateOptionsMenu(menu)
        menuInflater.inflate(R.menu.transactions_context_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val transaction = transaction ?: return false

        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }

            R.id.menu_move -> {
                moveTransaction(transaction)
                true
            }

            R.id.menu_duplicate -> {
                duplicateTransaction(transaction)
                true
            }

            R.id.menu_delete -> {
                deleteTransaction(transaction)
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (resultCode == RESULT_OK) {
            refresh()
            return
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    private fun moveTransaction(transaction: Transaction) {
        val transactionUID = transaction.uid ?: return
        val accountUID = requireAccount().uid
        val uids = arrayOf(transactionUID)
        val fragment = BulkMoveDialogFragment.newInstance(uids, accountUID)
        val fm = supportFragmentManager
        fm.setFragmentResultListener(BulkMoveDialogFragment.TAG, this, this)
        fragment.show(fm, BulkMoveDialogFragment.TAG)
    }

    private fun deleteTransaction(transaction: Transaction) {
        val activity: Activity = this
        if (shouldBackupTransactions(activity)) {
            backupActiveBookAsync(activity) { result ->
                transactionsDbAdapter.deleteRecord(transaction)
                updateAllWidgets(activity)
                finish()
            }
        } else {
            transactionsDbAdapter.deleteRecord(transaction)
            updateAllWidgets(activity)
            finish()
        }
    }

    private fun duplicateTransaction(transaction: Transaction) {
        val duplicate = transaction.copy(datePosted = System.currentTimeMillis())
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
            .putExtra(UxArgument.SELECTED_ACCOUNT_UID, account?.uid)
        startActivity(intent)
    }

    // Show the transaction in the account.
    fun showAccount(split: Split) {
        val accountUID = split.accountUID ?: return
        val transactionUID = split.transactionUID ?: return
        if (accountUID.isEmpty()) {
            Timber.w("Account UID required")
            return
        }
        if (transactionUID.isEmpty()) {
            Timber.w("Transaction UID required")
            return
        }
        val intent = Intent(this, TransactionsActivity::class.java)
            .setAction(Intent.ACTION_VIEW)
            .putExtra(UxArgument.SELECTED_ACCOUNT_UID, accountUID)
            .putExtra(UxArgument.SELECTED_TRANSACTION_UID, transactionUID)
        startActivityForResult(intent, REQUEST_REFRESH)
    }

    private fun requireTransaction(): Transaction {
        var transaction = this.transaction
        if (transaction != null) {
            return transaction
        }
        val args: Bundle = requireArguments()
        val transactionUID = args.getString(UxArgument.SELECTED_TRANSACTION_UID)!!
        try {
            transaction = transactionsDbAdapter.getRecord(transactionUID)
            this.transaction = transaction
        } catch (e: IllegalArgumentException) {
            Timber.e(e)
        }
        if (transaction == null) {
            setResult(RESULT_CANCELED)
            finish()
            throw IllegalArgumentException("Transaction required")
        }
        return transaction
    }

    private fun requireAccount(): Account {
        var account = this.account
        if (account != null) {
            return account
        }
        val args: Bundle = requireArguments()
        val accountUID = args.getString(UxArgument.SELECTED_ACCOUNT_UID)!!
        try {
            account = accountsDbAdapter.getRecord(accountUID)
            this.account = account
        } catch (e: IllegalArgumentException) {
            Timber.e(e)
        }
        if (account == null) {
            setResult(RESULT_CANCELED)
            finish()
            throw IllegalArgumentException("Account required")
        }
        return account
    }

    companion object {
        // "ForResult" to force refresh afterward.
        private const val REQUEST_REFRESH = 0x0000
    }
}
