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

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import org.gnucash.android.R;
import org.gnucash.android.model.Account;
import org.gnucash.android.ui.colorpicker.ColorPickerSwatch.OnColorSelectedListener;

/**
 * A dialog which takes in as input an array of colors and creates a palette allowing the user to
 * select a specific color swatch, which invokes a listener.
 */
public class ColorPickerDialog extends DialogFragment implements OnColorSelectedListener {

    /**
     * Tag for the color picker dialog fragment
     */
    public static final String COLOR_PICKER_DIALOG_TAG = "color_picker_dialog";

    public static final String EXTRA_COLOR = "color";

    public static final int SIZE_LARGE = ColorPickerPalette.SIZE_LARGE;
    public static final int SIZE_SMALL = ColorPickerPalette.SIZE_SMALL;

    private static final String KEY_TITLE_ID = "title_id";
    private static final String KEY_COLORS = "colors";
    private static final String KEY_SELECTED_COLOR = "selected_color";
    private static final String KEY_COLUMNS = "columns";
    private static final String KEY_SIZE = "size";

    private int mTitleResId = R.string.color_picker_default_title;
    @NonNull
    private int[] mColors = new int[0];
    @ColorInt
    private int mSelectedColor = Color.TRANSPARENT;
    private int mColumns;
    private int mSize = ColorPickerPalette.SIZE_LARGE;

    @Nullable
    private ColorPickerPalette mPalette;
    @Nullable
    private OnColorSelectedListener mListener;

    public static ColorPickerDialog newInstance(int titleResId, int[] colors, int selectedColor,
                                                int columns, int size) {
        ColorPickerDialog dialog = new ColorPickerDialog();
        dialog.setArguments(titleResId, columns, size, colors, selectedColor);
        return dialog;
    }

    private void setArguments(int titleResId, int columns, int size, int[] colors, @ColorInt int selectedColor) {
        Bundle bundle = new Bundle();
        bundle.putInt(KEY_TITLE_ID, titleResId);
        bundle.putInt(KEY_COLUMNS, columns);
        bundle.putInt(KEY_SIZE, size);
        bundle.putIntArray(KEY_COLORS, colors);
        bundle.putInt(KEY_SELECTED_COLOR, selectedColor);
        setArguments(bundle);
    }

    @Deprecated
    public void setOnColorSelectedListener(OnColorSelectedListener listener) {
        mListener = listener;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Bundle args = getArguments();
        if (args != null) {
            mTitleResId = args.getInt(KEY_TITLE_ID, mTitleResId);
            mColumns = args.getInt(KEY_COLUMNS, mColumns);
            mSize = args.getInt(KEY_SIZE, mSize);
            mColors = args.getIntArray(KEY_COLORS);
            mSelectedColor = args.getInt(KEY_SELECTED_COLOR, mSelectedColor);
        }

        if (savedInstanceState != null) {
            mColors = savedInstanceState.getIntArray(KEY_COLORS);
            mSelectedColor = savedInstanceState.getInt(KEY_SELECTED_COLOR, mSelectedColor);
        }
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        final Activity activity = requireActivity();

        View view = LayoutInflater.from(activity).inflate(R.layout.color_picker_dialog, null);
        ColorPickerPalette palette = view.findViewById(R.id.color_picker);
        palette.init(mSize, mColumns, this);
        if (mColors != null) {
            palette.drawPalette(mColors, mSelectedColor);
            palette.setVisibility(View.VISIBLE);
        }
        mPalette = palette;

        return new AlertDialog.Builder(activity)
            .setTitle(mTitleResId)
            .setView(view)
            .setNeutralButton(R.string.default_color, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    onColorSelected(Account.DEFAULT_COLOR);
                }
            })
            .setNegativeButton(R.string.alert_dialog_cancel, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dialog.dismiss();
                }
            })
            .create();
    }

    @Override
    public void onColorSelected(@ColorInt final int color) {
        if (mListener != null) {
            mListener.onColorSelected(color);
        }

        Bundle result = new Bundle();
        result.putInt(EXTRA_COLOR, color);
        getParentFragmentManager().setFragmentResult(COLOR_PICKER_DIALOG_TAG, result);

        if (mPalette != null && color != mSelectedColor) {
            mPalette.setSelected(color);
            // Redraw palette to show checkmark on newly selected color before dismissing.
            mPalette.postDelayed(new Runnable() {
                @Override
                public void run() {
                    dismiss();
                }
            }, 300L);
        } else {
            dismiss();
        }
        mSelectedColor = color;
    }

    public int[] getColors() {
        return mColors;
    }

    public int getSelectedColor() {
        return mSelectedColor;
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putIntArray(KEY_COLORS, mColors);
        outState.putInt(KEY_SELECTED_COLOR, mSelectedColor);
    }
}