package org.gnucash.android.app

import android.view.MenuItem

class MenuDiff(itemsBefore: List<MenuItem>, itemsAfter: List<MenuItem>) {

    private val items = mutableListOf<MenuItem>()

    init {
        items.addAll(itemsAfter)
        items.removeAll(itemsBefore)
    }

    fun setMenuVisibility(isVisible: Boolean) {
        for (item in items) {
            item.isVisible = isVisible
        }
    }
}
