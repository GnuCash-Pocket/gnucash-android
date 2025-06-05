package org.gnucash.android.ui.report.piechart

import com.github.mikephil.charting.data.PieEntry

class PieChartComparator : Comparator<PieChartEntry> {
    override fun compare(o1: PieChartEntry, o2: PieChartEntry): Int {
        return o1.value.compareTo(o2.value)
    }
}