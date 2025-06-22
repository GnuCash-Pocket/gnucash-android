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

package org.gnucash.android.ui.colorpicker;


import android.content.Context;
import android.content.res.Resources;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.TableLayout;

import androidx.annotation.ColorInt;

import org.gnucash.android.R;
import org.gnucash.android.ui.colorpicker.ColorPickerSwatch.OnColorSelectedListener;

/**
 * A color picker custom view which creates an grid of color squares.  The number of squares per
 * row (and the padding between the squares) is determined by the user.
 */
public class ColorPickerPalette extends TableLayout {

    public static final int SIZE_LARGE = 1;
    public static final int SIZE_SMALL = 2;

    private OnColorSelectedListener mOnColorSelectedListener;

    private String mDescription;
    private String mDescriptionSelected;

    private int mSwatchLength;
    private int mMarginSize;
    private int mNumColumns;

    public ColorPickerPalette(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public ColorPickerPalette(Context context) {
        super(context);
    }

    /**
     * Initialize the size, columns, and listener.  Size should be a pre-defined size (SIZE_LARGE
     * or SIZE_SMALL) from ColorPickerDialogFragment.
     */
    public void init(int size, int columns, OnColorSelectedListener listener) {
        mNumColumns = columns;
        Resources res = getResources();
        if (size == SIZE_LARGE) {
            mSwatchLength = res.getDimensionPixelSize(R.dimen.color_swatch_large);
            mMarginSize = res.getDimensionPixelSize(R.dimen.color_swatch_margins_large);
        } else {
            mSwatchLength = res.getDimensionPixelSize(R.dimen.color_swatch_small);
            mMarginSize = res.getDimensionPixelSize(R.dimen.color_swatch_margins_small);
        }
        mOnColorSelectedListener = listener;

        mDescription = res.getString(R.string.color_swatch_description);
        mDescriptionSelected = res.getString(R.string.color_swatch_description_selected);
    }

    /**
     * Adds swatches to table in a serpentine format.
     */
    public void drawPalette(int[] colors, int selectedColor) {
        if (colors == null) {
            return;
        }

        removeAllViews();
        ViewGroup table = createTable();
        addView(table);

        int tableElements = 0;
        int rowElements = 0;

        // Fills the table with swatches based on the array of colors.
        for (int color : colors) {
            tableElements++;

            View colorSwatch = createColorSwatch(color, selectedColor);
            setSwatchDescription(tableElements, rowElements, color == selectedColor,
                colorSwatch);
            table.addView(colorSwatch);

            rowElements++;
            if (rowElements == mNumColumns) {
                rowElements = 0;
            }
        }

        // Create blank views to fill the row if the last row has not been filled.
        while ((rowElements > 0) && (rowElements < mNumColumns)) {
            table.addView(createBlankSpace());
            rowElements++;
        }
    }

    /**
     * Add a content description to the specified swatch view. Because the colors get added in a
     * snaking form, every other row will need to compensate for the fact that the colors are added
     * in an opposite direction from their left->right/top->bottom order, which is how the system
     * will arrange them for accessibility purposes.
     */
    private void setSwatchDescription(int index, int rowElements, boolean selected,
                                      View swatch) {
        final String description;
        if (selected) {
            description = String.format(mDescriptionSelected, index);
        } else {
            description = String.format(mDescription, index);
        }
        swatch.setContentDescription(description);
    }

    private ViewGroup createTable() {
        GridLayout table = new GridLayout(getContext());
        table.setColumnCount(mNumColumns);
        return table;
    }

    /**
     * Creates a blank space to fill the row.
     */
    private ImageView createBlankSpace() {
        ImageView view = new ImageView(getContext());
        MarginLayoutParams params = new MarginLayoutParams(mSwatchLength, mSwatchLength);
        params.setMargins(mMarginSize, mMarginSize, mMarginSize, mMarginSize);
        view.setLayoutParams(params);
        return view;
    }

    /**
     * Creates a color swatch.
     */
    private ColorPickerSwatch createColorSwatch(@ColorInt int color, @ColorInt int selectedColor) {
        ColorPickerSwatch view = new ColorPickerSwatch(getContext(), color,
            color == selectedColor, mOnColorSelectedListener);
        MarginLayoutParams params = new MarginLayoutParams(mSwatchLength, mSwatchLength);
        params.setMargins(mMarginSize, mMarginSize, mMarginSize, mMarginSize);
        view.setLayoutParams(params);
        return view;
    }
}