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


import static java.lang.Math.max;

import android.content.Context;
import android.content.res.Resources;
import android.util.AttributeSet;
import android.view.View;
import android.widget.GridLayout;

import androidx.annotation.ColorInt;

import org.gnucash.android.R;
import org.gnucash.android.ui.colorpicker.ColorPickerSwatch.OnColorSelectedListener;

import java.util.ArrayList;
import java.util.List;

/**
 * A color picker custom view which creates an grid of color squares.  The number of squares per
 * row (and the padding between the squares) is determined by the user.
 */
public class ColorPickerPalette extends GridLayout {

    public static final int SIZE_LARGE = 1;
    public static final int SIZE_SMALL = 2;

    private OnColorSelectedListener mOnColorSelectedListener;

    private String mDescription;
    private String mDescriptionSelected;

    private int mSwatchLength;
    private int mMarginSize;
    private int mNumColumns = UNDEFINED;
    private final List<ColorPickerSwatch> swatches = new ArrayList<>();

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
        if (columns > 0) {
            mNumColumns = columns;
            setColumnCount(columns);
        }

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

        swatches.clear();
        removeAllViews();

        int tableElements = 0;

        // Fills the table with swatches based on the array of colors.
        for (int color : colors) {
            tableElements++;

            ColorPickerSwatch colorSwatch = createColorSwatch(color, selectedColor);
            swatches.add(colorSwatch);
            setSwatchDescription(tableElements, color == selectedColor, colorSwatch);
            addView(colorSwatch);
        }
    }

    /**
     * Add a content description to the specified swatch view. Because the colors get added in a
     * snaking form, every other row will need to compensate for the fact that the colors are added
     * in an opposite direction from their left->right/top->bottom order, which is how the system
     * will arrange them for accessibility purposes.
     */
    private void setSwatchDescription(int index, boolean selected, View swatch) {
        final String description;
        if (selected) {
            description = String.format(mDescriptionSelected, index);
        } else {
            description = String.format(mDescription, index);
        }
        swatch.setContentDescription(description);
    }

    /**
     * Creates a color swatch.
     */
    private ColorPickerSwatch createColorSwatch(@ColorInt int color, @ColorInt int selectedColor) {
        ColorPickerSwatch view = new ColorPickerSwatch(getContext(), color,
            color == selectedColor, mOnColorSelectedListener);
        view.setLayoutParams(generateSwatchParams());
        return view;
    }

    private LayoutParams generateSwatchParams() {
        LayoutParams params = generateDefaultLayoutParams();
        params.width = mSwatchLength;
        params.height = mSwatchLength;
        params.setMargins(mMarginSize, mMarginSize, mMarginSize, mMarginSize);
        return params;
    }

    public void setSelected(@ColorInt int color) {
        for (ColorPickerSwatch swatch : swatches) {
            swatch.setChecked(swatch.color == color);
        }
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        super.onMeasure(widthSpec, heightSpec);

        if (mNumColumns < 0) {
            int hPadding = getPaddingLeft() + getPaddingRight();
            final int widthSansPadding = getMeasuredWidth() - hPadding;
            int widthSwatch = mMarginSize + mSwatchLength + mMarginSize;
            int columnCount = max(1, widthSansPadding / widthSwatch);
            int count = getChildCount();
            // Reset the layout params to recalculate their spans.
            for (int i = 0; i < count; i++) {
                View child = getChildAt(i);
                child.setLayoutParams(generateSwatchParams());
            }
            setColumnCount(columnCount);
        }
    }
}