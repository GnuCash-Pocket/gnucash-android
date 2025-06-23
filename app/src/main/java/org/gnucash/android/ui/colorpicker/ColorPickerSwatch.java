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
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.res.ResourcesCompat;

import org.gnucash.android.R;

/**
 * Creates a circular swatch of a specified color.  Adds a checkmark if marked as checked.
 */
public class ColorPickerSwatch extends FrameLayout implements View.OnClickListener {
    @ColorInt
    public final int color;
    @NonNull
    private final ImageView mCheckmarkImage;
    @NonNull
    private final ImageView mSwatchImage;
    @Nullable
    private final OnColorSelectedListener mOnColorSelectedListener;

    /**
     * Interface for a callback when a color square is selected.
     */
    public interface OnColorSelectedListener {

        /**
         * Called when a specific color square has been selected.
         */
        void onColorSelected(@ColorInt int color);
    }

    public ColorPickerSwatch(@NonNull Context context,
                             @ColorInt int color,
                             boolean checked,
                             @Nullable OnColorSelectedListener listener
    ) {
        super(context);
        this.color = color;
        mOnColorSelectedListener = listener;

        LayoutInflater.from(context).inflate(R.layout.color_picker_swatch, this);
        mSwatchImage = findViewById(R.id.color_picker_swatch);
        mCheckmarkImage = findViewById(R.id.color_picker_checkmark);
        setColor(color);
        setChecked(checked);
        setOnClickListener(this);
    }

    protected void setColor(@ColorInt int color) {
        Drawable colorDrawable = ResourcesCompat.getDrawable(getResources(), R.drawable.color_picker_swatch, null);
        colorDrawable.setTint(color);
        mSwatchImage.setImageDrawable(colorDrawable);
    }

    public void setChecked(boolean checked) {
        if (checked) {
            mCheckmarkImage.setVisibility(View.VISIBLE);
        } else {
            mCheckmarkImage.setVisibility(View.GONE);
        }
    }

    @Override
    public void onClick(View v) {
        if (mOnColorSelectedListener != null) {
            mOnColorSelectedListener.onColorSelected(color);
        }
    }
}