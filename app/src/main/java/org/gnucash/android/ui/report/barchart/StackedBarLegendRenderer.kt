package org.gnucash.android.ui.report.barchart

import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.components.LegendEntry
import com.github.mikephil.charting.data.ChartData
import com.github.mikephil.charting.interfaces.datasets.IBarDataSet
import com.github.mikephil.charting.renderer.LegendRenderer
import com.github.mikephil.charting.utils.ColorTemplate
import com.github.mikephil.charting.utils.ViewPortHandler
import org.gnucash.android.ui.report.LegendEntryComparator

class StackedBarLegendRenderer(viewPortHandler: ViewPortHandler, legend: Legend) :
    LegendRenderer(viewPortHandler, legend) {
    override fun computeLegend(data: ChartData<*>) {
        val legend = mLegend
        if (legend.isLegendCustom) return

        computedEntries.clear()
        val entriesByLabel = sortedSetOf(LegendEntryComparator())

        for (i in 0 until data.dataSetCount) {
            val dataSet = data.getDataSetByIndex(i)
            val clrs = dataSet.colors
            val colorsSize = clrs.size

            if (dataSet is IBarDataSet && dataSet.isStacked) {
                val sLabels = dataSet.stackLabels
                val sLabelsSize = sLabels.size

                var j = 0
                while (j < colorsSize && j < sLabelsSize) {
                    val label = sLabels[j % sLabelsSize]
                    if (!label.isEmpty()) {
                        entriesByLabel.add(
                            LegendEntry(
                                label,
                                dataSet.form,
                                dataSet.formSize,
                                dataSet.formLineWidth,
                                dataSet.formLineDashEffect,
                                clrs[j]!!
                            )
                        )
                    }
                    j++
                }

                if (!dataSet.label.isNullOrEmpty()) {
                    // add the legend description label
                    entriesByLabel.add(
                        LegendEntry(
                            dataSet.label,
                            Legend.LegendForm.NONE,
                            Float.NaN,
                            Float.NaN,
                            null,
                            ColorTemplate.COLOR_NONE
                        )
                    )
                }
            }
        }

        val tf = legend.typeface
        val legendLabelPaint = mLegendLabelPaint
        if (tf != null) legendLabelPaint.setTypeface(tf)
        legendLabelPaint.textSize = legend.textSize
        legendLabelPaint.setColor(legend.textColor)

        legend.setEntries(entriesByLabel.toList())
        // calculate all dimensions of the legend
        legend.calculateDimensions(legendLabelPaint, mViewPortHandler)
    }
}