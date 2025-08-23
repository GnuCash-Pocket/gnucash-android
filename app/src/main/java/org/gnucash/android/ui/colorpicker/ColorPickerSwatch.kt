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
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.shapes.OvalShape
import android.view.LayoutInflater
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.annotation.ColorInt
import androidx.core.view.isVisible
import org.gnucash.android.R


/**
 * Creates a circular swatch of a specified color.  Adds a checkmark if marked as checked.
 */
class ColorPickerSwatch(
    context: Context,
    @ColorInt val color: Int,
    checked: Boolean,
    private val onColorSelectedListener: OnColorSelectedListener?
) : FrameLayout(context) {
    private val checkmarkImage: ImageView
    private val swatchImage: ImageView

    /**
     * Interface for a callback when a color square is selected.
     */
    interface OnColorSelectedListener {
        /**
         * Called when a specific color square has been selected.
         */
        fun onColorSelected(@ColorInt color: Int)
    }

    var isChecked: Boolean
        get() = checkmarkImage.isVisible
        set(value) {
            checkmarkImage.isVisible = value
        }

    init {
        LayoutInflater.from(context).inflate(R.layout.color_picker_swatch, this)
        swatchImage = findViewById<ImageView>(R.id.color_picker_swatch)
        checkmarkImage = findViewById<ImageView>(R.id.color_picker_checkmark)
        checkmarkImage.imageTintList = null // Reset to white.
        setColor(color)
        isChecked = checked
        setOnClickListener {
            onColorSelectedListener?.onColorSelected(color)
        }
    }

    fun setColor(@ColorInt color: Int) {
        val drawable = ShapeDrawable(OvalShape()).apply {
            paint.color = color
            intrinsicWidth = 1
            intrinsicHeight = 1
        }
        swatchImage.setImageDrawable(drawable)
    }
}