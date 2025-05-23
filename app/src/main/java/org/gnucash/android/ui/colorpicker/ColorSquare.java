package org.gnucash.android.ui.colorpicker;

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

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.widget.QuickContactBadge;

import org.gnucash.android.R;

/**
 * The color square used as an entry point to launching the {@link ColorPickerDialog}.
 */
public class ColorSquare extends QuickContactBadge {

    public ColorSquare(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public ColorSquare(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    public void setBackgroundColor(int color) {
        Drawable[] colorDrawable = new Drawable[]{
            getContext().getResources().getDrawable(R.drawable.color_square)};
        setImageDrawable(new ColorStateDrawable(colorDrawable, color));
    }
}
