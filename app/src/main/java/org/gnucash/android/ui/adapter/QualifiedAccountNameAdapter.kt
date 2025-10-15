package org.gnucash.android.ui.adapter

import android.content.Context
import android.database.DatabaseUtils.sqlEscapeString
import android.text.TextUtils
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.gnucash.android.R
import org.gnucash.android.db.DatabaseSchema.AccountEntry
import org.gnucash.android.db.adapter.AccountsDbAdapter
import org.gnucash.android.model.Account
import org.gnucash.android.model.AccountType

class QualifiedAccountNameAdapter(
    context: Context,
    private val where: String? = null,
    private val whereArgs: Array<String?>? = null,
    var adapter: AccountsDbAdapter = AccountsDbAdapter.instance,
    private val scope: CoroutineScope
) : SpinnerArrayAdapter<Account>(context) {

    private var loadJob: Job? = null

    constructor(
        context: Context,
        adapter: AccountsDbAdapter,
        scope: CoroutineScope
    ) : this(
        context = context,
        where = null,
        whereArgs = null,
        adapter = adapter,
        scope = scope
    )

    constructor(
        context: Context,
        adapter: AccountsDbAdapter,
        lifecycleOwner: LifecycleOwner
    ) : this(
        context = context,
        where = null,
        whereArgs = null,
        adapter = adapter,
        lifecycleOwner = lifecycleOwner
    )

    constructor(
        context: Context,
        where: String?,
        whereArgs: Array<String?>?,
        adapter: AccountsDbAdapter,
        lifecycleOwner: LifecycleOwner
    ) : this(
        context = context,
        where = where,
        whereArgs = whereArgs,
        adapter = adapter,
        scope = lifecycleOwner.lifecycleScope
    )

    constructor(
        context: Context,
        lifecycleOwner: LifecycleOwner
    ) : this(
        context = context,
        where = null,
        whereArgs = null,
        adapter = AccountsDbAdapter.instance,
        lifecycleOwner = lifecycleOwner
    )

    init {
        setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
    }

    override fun hasStableIds(): Boolean {
        return true
    }

    override fun getItemId(position: Int): Long {
        return getAccount(position)?.id ?: position.toLong()
    }

    fun getAccount(position: Int): Account? {
        if (position < 0) return null
        return getItem(position)?.value
    }

    fun getUID(position: Int): String? {
        return getAccount(position)?.uid
    }

    fun getAccount(uid: String?): Account? {
        if (uid.isNullOrEmpty()) return null

        val count = count
        for (i in 0 until count) {
            val account = getAccount(i) ?: continue
            if (account.uid == uid) return account
        }
        return null
    }

    fun getPosition(uid: String?): Int {
        if (uid.isNullOrEmpty()) return -1

        val count = count
        for (i in 0 until count) {
            if (getUID(i) == uid) return i
        }
        return -1
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = super.getView(position, convertView, parent)
        val textView = (view as? TextView) ?: view.findViewById(android.R.id.text1)
        textView.ellipsize = TextUtils.TruncateAt.MIDDLE
        return view
    }

    override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
        val account = getAccount(position)!!

        val view = super.getDropDownView(position, convertView, parent)
        val textView = (view as? TextView) ?: view.findViewById(android.R.id.text1)
        textView.ellipsize = TextUtils.TruncateAt.MIDDLE

        @DrawableRes val icon = if (account.isFavorite) R.drawable.ic_favorite else 0
        textView.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, icon, 0)

        return view
    }

    fun swapAdapter(adapter: AccountsDbAdapter) {
        this.adapter = adapter
        load()
    }

    fun load(callback: ((QualifiedAccountNameAdapter) -> Unit)? = null): QualifiedAccountNameAdapter {
        loadJob?.cancel()
        loadJob = scope.launch(Dispatchers.IO) {
            val records = loadData(adapter)
            val items = records.map { account ->
                val label = if (account.fullName.isNullOrBlank()) {
                    account.name
                } else {
                    account.fullName!!
                }
                SpinnerItem(account, label)
            }
            withContext(Dispatchers.Main) {
                clear()
                addAll(items)
                callback?.invoke(this@QualifiedAccountNameAdapter)
            }
        }
        return this
    }

    private fun loadData(adapter: AccountsDbAdapter): List<Account> {
        val where = where ?: WHERE_NO_ROOT
        val whereArgs = whereArgs
        val orderBy = ORDER_BY_FAVORITE_THEN_FULL_NAME
        return adapter.getAllRecords(where, whereArgs, orderBy)
    }

    fun getAccountDb(uid: String): Account? {
        return getAccount(uid) ?: adapter.getRecordOrNull(uid)
    }

    companion object {
        private val WHERE_NO_ROOT =
            AccountEntry.COLUMN_TYPE + " != " + sqlEscapeString(AccountType.ROOT.name) +
                    " AND " + AccountEntry.COLUMN_TEMPLATE + " = 0"

        private const val ORDER_BY_FAVORITE_THEN_FULL_NAME =
            AccountEntry.COLUMN_FAVORITE + " DESC, " + AccountEntry.COLUMN_FULL_NAME + " ASC"
    }
}