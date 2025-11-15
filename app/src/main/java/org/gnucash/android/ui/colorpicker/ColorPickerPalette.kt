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
import android.content.res.Configuration
import android.graphics.Color
import android.util.AttributeSet
import android.view.ViewGroup
import androidx.annotation.ColorInt
import androidx.core.view.setPadding
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.gnucash.android.R
import org.gnucash.android.ui.colorpicker.ColorPickerSwatch.OnColorSelectedListener
import kotlin.math.max
import kotlin.math.min

/**
 * A color picker custom view which creates an grid of color squares.  The number of squares per
 * row (and the padding between the squares) is determined by the user.
 */
class ColorPickerPalette @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    RecyclerView(context, attrs) {
    private var onColorSelectedListener: OnColorSelectedListener? = null
    private var swatchSizePx = 0
    private var marginSizePx = 0
    private var spanCount = UNDEFINED
    private var swatchAdapter: ColorPickerAdapter? = null

    /**
     * Initialize the size, columns, and listener.
     *
     * @param size  Size should be a pre-defined size (`ColorPickerPalette.SIZE_LARGE` or `ColorPickerPalette.SIZE_SMALL`)
     * @param columnCount the maximum number of columns.
     * @param listener the listener for when the color is selected.
     */
    fun init(size: Int, spanCount: Int, listener: OnColorSelectedListener?) {
        this.spanCount = if (spanCount >= 1) spanCount else UNDEFINED

        val res = resources
        applyOrientation(res.configuration.orientation)

        if (size == SIZE_LARGE) {
            swatchSizePx = res.getDimensionPixelSize(R.dimen.color_swatch_large)
            marginSizePx = res.getDimensionPixelSize(R.dimen.color_swatch_margins_large)
        } else {
            swatchSizePx = res.getDimensionPixelSize(R.dimen.color_swatch_small)
            marginSizePx = res.getDimensionPixelSize(R.dimen.color_swatch_margins_small)
        }
        onColorSelectedListener = listener
    }

    /**
     * Adds swatches to table in a serpentine format.
     */
    fun drawPalette(colors: IntArray?, colorNames: Array<String>?, @ColorInt selectedColor: Int) {
        if (colors == null) {
            return
        }

        swatchAdapter =
            ColorPickerAdapter(colors, colorNames?.toList() ?: emptyList(), selectedColor)
        adapter = swatchAdapter
    }

    fun setSelected(@ColorInt color: Int) {
        val adapter = swatchAdapter ?: return
        for (swatch in adapter.swatches) {
            swatch.isChecked = swatch.color == color
        }
    }

    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        super.onMeasure(widthSpec, heightSpec)

        if (spanCount < 1) {
            val count: Int
            val layoutManager = layoutManager as GridLayoutManager
            if (layoutManager.orientation == GridLayoutManager.VERTICAL) {
                val hPadding = paddingLeft + paddingRight
                val widthSansPadding = measuredWidth - hPadding
                val widthSwatch = marginSizePx + swatchSizePx + marginSizePx
                count = max(1, (widthSansPadding / widthSwatch))
            } else {
                val vPadding = paddingTop + paddingBottom
                val heightSansPadding = measuredHeight - vPadding
                val heightSwatch = marginSizePx + swatchSizePx + marginSizePx
                count = max(1, (heightSansPadding / heightSwatch))
            }
            layoutManager.spanCount = count
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        applyOrientation(newConfig.orientation)
    }

    private fun applyOrientation(orientation: Int) {
        val layoutManager = object : GridLayoutManager(context, max(1, spanCount)) {
            override fun checkLayoutParams(lp: RecyclerView.LayoutParams): Boolean {
                if (!super.checkLayoutParams(lp)) return false

                val sizeGrid = if (orientation == VERTICAL) {
                    measuredWidth - paddingStart - paddingEnd
                } else {
                    measuredHeight - paddingTop - paddingBottom
                }
                var sizeCell = min(swatchSizePx, max(0, sizeGrid / spanCount))
                if (sizeCell == 0) {
                    sizeCell = swatchSizePx
                }
                lp.width = sizeCell
                lp.height = sizeCell
                return true
            }
        }
        if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            layoutManager.orientation = GridLayoutManager.HORIZONTAL
        } else {
            layoutManager.orientation = GridLayoutManager.VERTICAL
        }
        this.layoutManager = layoutManager
    }

    private class ColorPickerViewHolder(private val swatch: ColorPickerSwatch) :
        ViewHolder(swatch) {
        fun bind(
            position: Int,
            @ColorInt color: Int,
            colorName: String?,
            @ColorInt selectedColor: Int
        ) {
            val selected = color == selectedColor
            swatch.setColor(color)
            swatch.isChecked = selected

            val context: Context = itemView.context
            val name = colorName ?: (position + 1).toString()
            swatch.contentDescription = if (selected) {
                context.getString(R.string.color_swatch_description_selected, name)
            } else {
                context.getString(R.string.color_swatch_description, name)
            }
        }
    }

    private inner class ColorPickerAdapter(
        private val colors: IntArray,
        private val colorNames: List<String>,
        @field:ColorInt
        private val selectedColor: Int = Color.TRANSPARENT
    ) : Adapter<ColorPickerViewHolder>() {
        private val _swatches = mutableListOf<ColorPickerSwatch>()
        val swatches: List<ColorPickerSwatch> = _swatches

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ColorPickerViewHolder {
            val swatch = createColorSwatch(parent)
            _swatches.add(swatch)
            return ColorPickerViewHolder(swatch)
        }

        override fun onBindViewHolder(holder: ColorPickerViewHolder, position: Int) {
            @ColorInt val color = colors[position]
            val colorName: String? = if (position < colorNames.size) {
                colorNames[position]
            } else {
                null
            }
            holder.bind(position, color, colorName, selectedColor)
        }

        override fun getItemCount(): Int {
            return colors.size
        }

        /**
         * Creates a color swatch.
         */
        private fun createColorSwatch(parent: ViewGroup): ColorPickerSwatch {
            val view = ColorPickerSwatch(
                context = parent.context,
                onColorSelectedListener = onColorSelectedListener
            )
            view.setPadding(marginSizePx)
            return view
        }
    }

    companion object {
        const val SIZE_LARGE: Int = 1
        const val SIZE_SMALL: Int = 2

        private const val UNDEFINED = Int.MIN_VALUE
    }
}