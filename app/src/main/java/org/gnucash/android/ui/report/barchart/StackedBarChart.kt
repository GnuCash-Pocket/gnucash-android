package org.gnucash.android.ui.report.barchart

import android.content.Context
import com.github.mikephil.charting.charts.BarChart

class StackedBarChart(context: Context) : BarChart(context) {
    init {
        mLegend.isWordWrapEnabled = true
        mLegendRenderer = StackedBarLegendRenderer(mViewPortHandler, mLegend)
    }
}