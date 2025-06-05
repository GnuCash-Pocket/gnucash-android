package org.gnucash.android.ui.report.piechart

import androidx.annotation.ColorInt
import com.github.mikephil.charting.data.PieEntry

data class PieChartEntry(
    val entry: PieEntry,
    @ColorInt val color: Int
) {
    val value: Float get() = entry.value
}