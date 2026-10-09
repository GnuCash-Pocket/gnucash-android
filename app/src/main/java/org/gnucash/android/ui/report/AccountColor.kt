package org.gnucash.android.ui.report

import androidx.annotation.ColorInt
import com.github.mikephil.charting.utils.ColorTemplate

data class AccountColor(
    val accountUID: String,
    val name: String,
    @field:ColorInt val color: Int
) {
    companion object {
        val EMPTY = AccountColor("", "", ColorTemplate.COLOR_NONE)
    }
}
