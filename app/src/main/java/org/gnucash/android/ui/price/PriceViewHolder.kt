package org.gnucash.android.ui.price

import android.content.Context
import android.database.Cursor
import android.text.format.DateUtils
import android.view.MenuItem
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.widget.PopupMenu
import androidx.recyclerview.widget.RecyclerView
import org.gnucash.android.R
import org.gnucash.android.databinding.CardviewPriceBinding
import org.gnucash.android.db.adapter.PricesDbAdapter
import org.gnucash.android.export.xml.GncXmlHelper.formatFormula
import org.gnucash.android.model.Price
import org.gnucash.android.util.formatMediumDate

class PriceViewHolder(
    binding: CardviewPriceBinding,
    private val useAbsoluteDate: Boolean,
    private val onEditPriceClick: PriceCallback,
    private val onDeletePriceClick: PriceCallback,
    private val onDuplicatePriceClick: PriceCallback,
) : RecyclerView.ViewHolder(binding.root), PopupMenu.OnMenuItemClickListener {
    private val primaryText: TextView = binding.listItem2Lines.primaryText
    private val secondaryText: TextView = binding.listItem2Lines.secondaryText
    private val dateText: TextView = binding.date
    private val amountText: TextView = binding.amount
    private val optionsMenu: ImageView = binding.optionsMenu

    private val pricesDbAdapter = PricesDbAdapter.instance
    private var price: Price? = null

    fun bind(cursor: Cursor) {
        val context = itemView.context
        val price = pricesDbAdapter.buildModelInstance(cursor)
        this.price = price

        itemView.setOnClickListener {
            onEditPriceClick(price)
        }

        primaryText.text = price.security.formatListItem()
        secondaryText.text = price.currency.formatListItem()
        dateText.text = if (useAbsoluteDate) {
            formatMediumDate(price.date)
        } else {
            formatPrettyDate(context, price.date)
        }
        amountText.text = formatFormula(price.toBigDecimal())

        optionsMenu.setOnClickListener { v ->
            val popupMenu = PopupMenu(v.context, v)
            popupMenu.setOnMenuItemClickListener(this@PriceViewHolder)
            val inflater = popupMenu.menuInflater
            val menu = popupMenu.menu
            inflater.inflate(R.menu.price_item, menu)
            popupMenu.show()
        }
    }

    private fun formatPrettyDate(context: Context, time: Long): String {
        return DateUtils.getRelativeDateTimeString(
            context,
            time,
            DateUtils.MINUTE_IN_MILLIS,
            DateUtils.WEEK_IN_MILLIS,
            0
        ).toString()
    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        val price = price ?: return false

        return when (item.itemId) {
            R.id.menu_delete -> {
                onDeletePriceClick(price)
                true
            }

            R.id.menu_duplicate -> {
                onDuplicatePriceClick(price)
                true
            }

            R.id.menu_edit -> {
                onEditPriceClick(price)
                true
            }

            else -> false
        }
    }
}