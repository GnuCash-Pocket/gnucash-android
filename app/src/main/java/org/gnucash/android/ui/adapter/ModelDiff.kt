package org.gnucash.android.ui.adapter

import androidx.recyclerview.widget.DiffUtil
import org.gnucash.android.model.BaseModel

open class ModelDiff<T : BaseModel> : DiffUtil.ItemCallback<T>() {
    override fun areItemsTheSame(oldItem: T, newItem: T): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(oldItem: T, newItem: T): Boolean {
        return oldItem == newItem
    }
}