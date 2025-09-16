/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gnucash.android.ui.colorpicker

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.GridLayout
import androidx.annotation.ColorInt
import androidx.core.view.size
import org.gnucash.android.R
import org.gnucash.android.ui.colorpicker.ColorPickerSwatch.OnColorSelectedListener
import kotlin.math.max

/**
 * A color picker custom view which creates an grid of color squares.  The number of squares per
 * row (and the padding between the squares) is determined by the user.
 */
class ColorPickerPalette @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    GridLayout(context, attrs) {
    private var onColorSelectedListener: OnColorSelectedListener? = null

    private var description: String = ""
    private var descriptionSelected: String = ""

    private var swatchLength = 0
    private var marginSize = 0
    private var columnCount = UNDEFINED
    private val swatches = mutableListOf<ColorPickerSwatch>()

    /**
     * Initialize the size, columns, and listener.  Size should be a pre-defined size (SIZE_LARGE
     * or SIZE_SMALL) from ColorPickerDialogFragment.
     */
    fun init(size: Int, columnCount: Int, listener: OnColorSelectedListener?) {
        if (columnCount > 0) {
            this.columnCount = columnCount
            setColumnCount(columnCount)
        }

        val res = resources
        if (size == SIZE_LARGE) {
            swatchLength = res.getDimensionPixelSize(R.dimen.color_swatch_large)
            marginSize = res.getDimensionPixelSize(R.dimen.color_swatch_margins_large)
        } else {
            swatchLength = res.getDimensionPixelSize(R.dimen.color_swatch_small)
            marginSize = res.getDimensionPixelSize(R.dimen.color_swatch_margins_small)
        }
        onColorSelectedListener = listener

        description = res.getString(R.string.color_swatch_description)
        descriptionSelected = res.getString(R.string.color_swatch_description_selected)
    }

    /**
     * Adds swatches to table in a serpentine format.
     */
    fun drawPalette(colors: IntArray?, @ColorInt selectedColor: Int) {
        if (colors == null) {
            return
        }

        swatches.clear()
        removeAllViews()

        var tableElements = 0

        // Fills the table with swatches based on the array of colors.
        for (color in colors) {
            tableElements++

            val colorSwatch = createColorSwatch(color, selectedColor)
            swatches.add(colorSwatch)
            setSwatchDescription(tableElements, color == selectedColor, colorSwatch)
            addView(colorSwatch)
        }
    }

    /**
     * Add a content description to the specified swatch view. Because the colors get added in a
     * snaking form, every other row will need to compensate for the fact that the colors are added
     * in an opposite direction from their left->right/top->bottom order, which is how the system
     * will arrange them for accessibility purposes.
     */
    private fun setSwatchDescription(index: Int, selected: Boolean, swatch: View) {
        val description = if (selected) {
            String.format(descriptionSelected, index)
        } else {
            String.format(description, index)
        }
        swatch.setContentDescription(description)
    }

    /**
     * Creates a color swatch.
     */
    private fun createColorSwatch(
        @ColorInt color: Int,
        @ColorInt selectedColor: Int
    ): ColorPickerSwatch {
        val view = ColorPickerSwatch(
            context,
            color,
            color == selectedColor,
            onColorSelectedListener
        )
        view.setLayoutParams(generateSwatchParams())
        return view
    }

    private fun generateSwatchParams(): LayoutParams {
        return generateDefaultLayoutParams().apply {
            width = swatchLength
            height = swatchLength
            setMargins(marginSize, marginSize, marginSize, marginSize)
        }
    }

    fun setSelected(@ColorInt color: Int) {
        for (swatch in swatches) {
            swatch.isChecked = swatch.color == color
        }
    }

    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        super.onMeasure(widthSpec, heightSpec)

        if (columnCount < 0) {
            val hPadding = paddingLeft + paddingRight
            val widthSansPadding = measuredWidth - hPadding
            val widthSwatch = marginSize + swatchLength + marginSize
            val columnCount = max(1, (widthSansPadding / widthSwatch))
            // Reset the layout params to recalculate their spans.
            for (i in 0 until size) {
                val child = getChildAt(i)
                child.setLayoutParams(generateSwatchParams())
            }
            setColumnCount(columnCount)
        }
    }

    companion object {
        const val SIZE_LARGE: Int = 1
        const val SIZE_SMALL: Int = 2
    }
}