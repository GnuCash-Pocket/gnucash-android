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

import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import androidx.annotation.ColorInt
import androidx.annotation.StringRes
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import org.gnucash.android.R
import org.gnucash.android.model.Account
import org.gnucash.android.ui.colorpicker.ColorPickerSwatch.OnColorSelectedListener

/**
 * A dialog which takes in as input an array of colors and creates a palette allowing the user to
 * select a specific color swatch, which invokes a listener.
 */
class ColorPickerDialog : DialogFragment(), OnColorSelectedListener {
    @StringRes
    private var titleResId = R.string.color_picker_default_title

    @ColorInt
    private var selectedColor = Color.TRANSPARENT
    private var size = ColorPickerPalette.SIZE_LARGE

    private var palette: ColorPickerPalette? = null
    private var listener: OnColorSelectedListener? = null

    private fun setArguments(
        titleResId: Int,
        size: Int,
        @ColorInt selectedColor: Int
    ) {
        arguments = Bundle().apply {
            putInt(KEY_TITLE_ID, titleResId)
            putInt(KEY_SIZE, size)
            putInt(KEY_SELECTED_COLOR, selectedColor)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val args = arguments
        if (args != null) {
            titleResId = args.getInt(KEY_TITLE_ID, titleResId)
            size = args.getInt(KEY_SIZE, size)
            selectedColor = args.getInt(KEY_SELECTED_COLOR, selectedColor)
        }

        if (savedInstanceState != null) {
            selectedColor = savedInstanceState.getInt(KEY_SELECTED_COLOR, selectedColor)
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val activity: Activity = requireActivity()
        val context: Context = activity

        val view = layoutInflater.inflate(R.layout.color_picker_dialog, null)
        val palette = view.findViewById<ColorPickerPalette>(R.id.color_picker)
        palette.init(size, COLUMNS_GTK, this)

        val colors = context.resources.getIntArray(R.array.colors_gtk)
        val colorNames = context.resources.getStringArray(R.array.colors_names_gtk)
        if (colors.isNotEmpty()) {
            palette.drawPalette(colors, colorNames, selectedColor)
            palette.isVisible = true
        }
        this.palette = palette

        return AlertDialog.Builder(activity, theme)
            .setTitle(titleResId)
            .setView(view)
            .setNeutralButton(R.string.default_color, { _, _ ->
                onColorSelected(Account.DEFAULT_COLOR)
            })
            .setNegativeButton(R.string.alert_dialog_cancel) { _, _ ->
                // Dismisses itself
            }
            .create()
    }

    override fun onColorSelected(@ColorInt color: Int) {
        listener?.onColorSelected(color)

        val result = Bundle()
        result.putInt(EXTRA_COLOR, color)
        parentFragmentManager.setFragmentResult(COLOR_PICKER_DIALOG_TAG, result)

        val palette = palette
        if (palette != null && color != selectedColor) {
            palette.setSelected(color)
            // Redraw palette to show checkmark on newly selected color before dismissing.
            palette.postDelayed({
                dismiss()
            }, 300L)
        } else {
            dismiss()
        }
        selectedColor = color
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_SELECTED_COLOR, selectedColor)
    }

    companion object {
        /**
         * Tag for the color picker dialog fragment
         */
        const val COLOR_PICKER_DIALOG_TAG: String = "color_picker_dialog"

        const val EXTRA_COLOR: String = "color"

        const val SIZE_LARGE: Int = ColorPickerPalette.SIZE_LARGE
        const val SIZE_SMALL: Int = ColorPickerPalette.SIZE_SMALL

        private const val KEY_SELECTED_COLOR = "selected_color"
        private const val KEY_SIZE = "size"
        private const val KEY_TITLE_ID = "title_id"

        private const val COLUMNS_GTK = 5

        fun newInstance(
            titleResId: Int,
            size: Int,
            selectedColor: Int
        ): ColorPickerDialog {
            return ColorPickerDialog().apply {
                setArguments(titleResId, size, selectedColor)
            }
        }
    }
}