package org.gnucash.android.ui.report

import com.github.mikephil.charting.components.LegendEntry

class LegendEntryComparator : Comparator<LegendEntry> {
    override fun compare(o1: LegendEntry, o2: LegendEntry): Int {
        val c = o1.label.compareTo(o2.label)
        if (c != 0) return c
        return o1.formColor.compareTo(o2.formColor)
    }
}