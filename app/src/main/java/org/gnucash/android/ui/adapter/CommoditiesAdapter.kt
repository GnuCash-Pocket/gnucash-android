package org.gnucash.android.ui.adapter

import android.content.Context
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.gnucash.android.db.DatabaseSchema.CommodityEntry
import org.gnucash.android.db.adapter.CommoditiesDbAdapter
import org.gnucash.android.model.Commodity

class CommoditiesAdapter(
    context: Context,
    private val adapter: CommoditiesDbAdapter = CommoditiesDbAdapter.instance,
    private val scope: CoroutineScope
) : SpinnerArrayAdapter<Commodity>(context) {

    private var loadJob: Job? = null

    constructor(
        context: Context,
        lifecycleOwner: LifecycleOwner
    ) : this(
        context = context,
        scope = lifecycleOwner.lifecycleScope
    )

    init {
        setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
    }

    fun getCommodity(position: Int): Commodity? {
        return getItem(position)?.value
    }

    fun getPosition(mnemonic: String): Int {
        for (i in 0 until count) {
            val commodity = getCommodity(i)!!
            if (commodity.currencyCode == mnemonic) {
                return i
            }
        }
        return -1
    }

    fun getPosition(commodity: Commodity): Int {
        for (i in 0 until count) {
            if (commodity == getCommodity(i)) {
                return i
            }
        }
        return -1
    }

    fun load(callback: ((CommoditiesAdapter) -> Unit)? = null): CommoditiesAdapter {
        loadJob?.cancel()
        loadJob = scope.launch(Dispatchers.IO) {
            val records = loadData(adapter)
            val labels = records.map { commodity ->
                SpinnerItem(commodity, commodity.formatListItem())
            }
            withContext(Dispatchers.Main) {
                clear()
                addAll(labels)
                callback?.invoke(this@CommoditiesAdapter)
            }
        }
        return this
    }

    private fun loadData(adapter: CommoditiesDbAdapter): List<Commodity> {
        val where = CommodityEntry.COLUMN_MNEMONIC + " <> ?" +
                " AND " + CommodityEntry.COLUMN_NAMESPACE + " <> ?"
        val whereArgs = arrayOf<String?>(Commodity.TEMPLATE, Commodity.TEMPLATE)
        val orderBy = CommodityEntry.COLUMN_MNEMONIC + " ASC"
        return adapter.getAllRecords(where, whereArgs, orderBy)
    }
}