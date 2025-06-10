package org.gnucash.android.ui.adapter

import android.content.Context
import android.database.DatabaseUtils.sqlEscapeString
import android.text.TextUtils
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.annotation.LayoutRes
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.gnucash.android.R
import org.gnucash.android.db.DatabaseSchema.AccountEntry
import org.gnucash.android.db.adapter.AccountsDbAdapter
import org.gnucash.android.model.Account
import org.gnucash.android.model.AccountType

class QualifiedAccountNameAdapter @JvmOverloads constructor(
    context: Context,
    private val where: String? = null,
    private val whereArgs: Array<String>? = null,
    private var adapter: AccountsDbAdapter = AccountsDbAdapter.getInstance(),
    @LayoutRes resource: Int = android.R.layout.simple_spinner_item
) : ArrayAdapter<QualifiedAccountNameAdapter.Label>(context, resource) {

    private var loadJob: Job? = null

    constructor(
        context: Context,
        adapter: AccountsDbAdapter
    ) : this(
        context = context,
        adapter = adapter,
        where = null
    )

    init {
        setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        load()
    }

    override fun hasStableIds(): Boolean {
        return true
    }

    override fun getItemId(position: Int): Long {
        return getAccount(position)?.id ?: position.toLong()
    }

    fun getAccount(position: Int): Account? {
        if (position < 0) return null
        return getItem(position)?.account
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
        val textView = if (view is TextView) view else view.findViewById(android.R.id.text1)
        textView.ellipsize = TextUtils.TruncateAt.MIDDLE
        return view
    }

    override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
        val account = getAccount(position)!!

        val view = super.getDropDownView(position, convertView, parent)
        val textView = if (view is TextView) view else view.findViewById(android.R.id.text1)
        textView.ellipsize = TextUtils.TruncateAt.MIDDLE

        @DrawableRes val icon = if (account.isFavorite) R.drawable.ic_favorite else 0
        textView.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, icon, 0)

        return view
    }

    fun swapAdapter(adapter: AccountsDbAdapter) {
        this.adapter = adapter
        load()
    }

    fun getDescendants(account: Account): List<Account> {
        return getDescendants(account.uid)
    }

    private fun getDescendants(parentUID: String): List<Account> {
        val result = mutableListOf<Account>()
        populateDescendants(parentUID, result)
        return result
    }

    private fun populateDescendants(parentUID: String, result: MutableList<Account>) {
        val count = count
        for (i in 0 until count) {
            val account = getAccount(i) ?: continue
            if (parentUID == account.parentUID) {
                result.add(account)
                populateDescendants(account.uid, result)
            }
        }
    }

    private fun load() {
        val records = loadData(adapter)
        val labels = records.map { Label(it) }
        clear()
        addAll(labels)
    }

    @JvmOverloads
    fun load(lifecycleOwner: LifecycleOwner, callback: (() -> Unit)? = null) {
        load(lifecycleOwner.lifecycleScope, callback)
    }

    fun load(lifecycleScope: LifecycleCoroutineScope, callback: (() -> Unit)? = null) {
        loadJob?.cancel()
        loadJob = lifecycleScope.launch(Dispatchers.IO) {
            val records = loadData(adapter)
            val labels = records.map { Label(it) }
            lifecycleScope.launch(Dispatchers.Main) {
                clear()
                addAll(labels)
                callback?.invoke()
            }
        }
    }

    private fun loadData(adapter: AccountsDbAdapter): List<Account> {
        val where = where ?: WHERE_NO_ROOT
        val orderBy = ORDER_BY_FAVORITE_THEN_FULL_NAME
        return adapter.getSimpleAccounts(where, whereArgs, orderBy)
    }

    data class Label(val account: Account) {
        override fun toString(): String {
            return account.fullName ?: account.name
        }
    }

    companion object {
        private val WHERE_NO_ROOT =
            AccountEntry.COLUMN_TYPE + " != " + sqlEscapeString(AccountType.ROOT.name) +
                    " AND " + AccountEntry.COLUMN_TEMPLATE + " = 0"

        private const val ORDER_BY_FAVORITE_THEN_FULL_NAME =
            AccountEntry.COLUMN_FAVORITE + " DESC, " + AccountEntry.COLUMN_FULL_NAME + " ASC"

        @JvmStatic
        @JvmOverloads
        fun where(
            context: Context,
            where: String,
            whereArgs: Array<String>? = null
        ): QualifiedAccountNameAdapter = QualifiedAccountNameAdapter(
            context = context,
            where = where,
            whereArgs = whereArgs
        )
    }
}