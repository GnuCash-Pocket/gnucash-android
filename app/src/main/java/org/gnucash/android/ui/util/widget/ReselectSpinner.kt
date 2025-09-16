package org.gnucash.android.ui.util.widget

import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatSpinner

/**
 * Spinner which fires OnItemSelectedListener even when an item is reselected.
 * Normal Spinners only fire item selected notifications when the selected item changes.
 *
 * This is used in `ReportsActivity` for the time range and in the [ExportFormFragment]
 *
 * It could happen that the selected item is fired twice especially if the item is the first in the list.
 * The Android system does this internally. In order to capture the first one, check whether the view parameter
 * of [OnItemSelectedListener.onItemSelected] is null.
 * That would represent the first call during initialization of the views. This call can be ignored.
 * See [ExportFormFragment.bindViewListeners] for an example
 *
 */
class ReselectSpinner @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : AppCompatSpinner(context, attrs) {

    override fun setSelection(position: Int) {
        val sameSelected = selectedItemPosition == position
        super.setSelection(position)
        if (sameSelected) {
            val listener = onItemSelectedListener
            listener?.onItemSelected(
                this,
                selectedView,
                position,
                selectedItemId
            )
        }
    }
}
