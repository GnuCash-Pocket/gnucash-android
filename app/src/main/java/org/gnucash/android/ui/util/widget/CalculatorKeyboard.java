/**
 * Copyright 2013 Maarten Pennings extended by SimplicityApks
 * <p>
 * Modified by:
 * Copyright 2015 Àlex Magaz Graça <rivaldi8@gmail.com>
 * Copyright 2015 Ngewi Fet <ngewif@gmail.com>
 * <p>
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * <p/>
 * If you use this software in a product, an acknowledgment in the product
 * documentation would be appreciated but is not required.
 */

package org.gnucash.android.ui.util.widget;

import static org.gnucash.android.app.ContextExtKt.getActivity;

import android.app.Activity;
import android.content.Context;
import android.inputmethodservice.Keyboard;
import android.inputmethodservice.KeyboardView.OnKeyboardActionListener;
import android.provider.Settings;
import android.text.Editable;
import android.text.InputFilter;
import android.text.Selection;
import android.text.TextUtils;
import android.text.method.DigitsKeyListener;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.gnucash.android.R;
import org.gnucash.android.inputmethodservice.CalculatorKeyboardView;

import java.text.DecimalFormatSymbols;


/**
 * When an activity hosts a keyboardView, this class allows several EditText's to register for it.
 * <p>
 * Known issues:
 * - It's not possible to select text.
 * - When in landscape, the EditText is covered by the keyboard.
 * - No i18n.
 *
 * @author Maarten Pennings, extended by SimplicityApks
 * @author Àlex Magaz Graça <rivaldi8@gmail.com>
 * @author Ngewi Fet <ngewif@gmail.com>
 */
public class CalculatorKeyboard {

    private static final String ACCEPTED = "0123456789١٢٣٤٥٦٧٨٩+*/()";
    private static final int KEY_CODE_CLEAR = CalculatorKeyboardView.KEY_CODE_CLEAR;
    private static final int KEY_CODE_DELETE = CalculatorKeyboardView.KEY_CODE_DELETE;
    private static final int KEY_CODE_EVALUATE = CalculatorKeyboardView.KEY_CODE_EVALUATE;

    /**
     * A link to the KeyboardView that is used to render this CalculatorKeyboard.
     */
    private final CalculatorKeyboardView keyboardView;
    private final Window window;
    private final InputMethodManager inputMethodManager;
    private final boolean isHapticFeedback;

    /**
     * Returns true if the haptic feedback is enabled.
     *
     * @return true if the haptic feedback is enabled in the system settings.
     */
    private boolean isHapticFeedbackEnabled(@NonNull Context context) {
        return Settings.System.getInt(context.getContentResolver(), Settings.System.HAPTIC_FEEDBACK_ENABLED, 0) != 0;
    }

    /**
     * Create a custom keyboard, that uses the KeyboardView (with resource id <var>viewid</var>) of the <var>host</var> activity,
     * and load the keyboard layout from xml file <var>layoutid</var> (see {@link Keyboard} for description).
     * Note that the <var>host</var> activity must have a <var>KeyboardView</var> in its layout (typically aligned with the bottom of the activity).
     * Note that the keyboard layout xml file may include key codes for navigation; see the constants in this class for their values.
     *
     * @param keyboardView KeyboardView in the layout
     */
    public CalculatorKeyboard(@NonNull CalculatorKeyboardView keyboardView) {
        this.keyboardView = keyboardView;
        Context context = keyboardView.getContext();
        inputMethodManager = (InputMethodManager) context.getSystemService(Activity.INPUT_METHOD_SERVICE);
        isHapticFeedback = isHapticFeedbackEnabled(context);
        // Hide the standard keyboard initially
        window = getActivity(keyboardView).getWindow();

        OnKeyboardActionListener keyboardActionListener = new OnKeyboardActionListener() {

            @Override
            public void onKey(int primaryCode, int[] keyCodes) {
                View focusCurrent = window.getCurrentFocus();
                if (focusCurrent == null) {
                    return;
                }
                if (!(focusCurrent instanceof CalculatorEditText calculatorEditText)) {
                    return;
                }
                Editable editable = calculatorEditText.getText();
                if (editable == null) {
                    return;
                }

                switch (primaryCode) {
                    case KEY_CODE_DELETE:
                        int start = Selection.getSelectionStart(editable);
                        int end = Selection.getSelectionEnd(editable);
                        editable.delete(Math.max(start - 1, 0), end);
                        break;
                    case KEY_CODE_CLEAR:
                        editable.clear();
                        break;
                    case KEY_CODE_EVALUATE:
                        calculatorEditText.evaluate();
                        break;
                }
            }

            @Override
            public void onPress(int primaryCode) {
                if (primaryCode != 0 && isHapticFeedback) {
                    keyboardView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                }
            }

            @Override
            public void onRelease(int primaryCode) {
            }

            @Override
            public void onText(@Nullable CharSequence text) {
                if (TextUtils.isEmpty(text)) {
                    return;
                }
                View focusCurrent = window.getCurrentFocus();
                if (focusCurrent == null) {
                    return;
                }
                if (!(focusCurrent instanceof CalculatorEditText calculatorEditText)) {
                    return;
                }
                Editable editable = calculatorEditText.getText();
                if (editable == null) {
                    return;
                }

                int start = Selection.getSelectionStart(editable);
                int end = Selection.getSelectionEnd(editable);
                // delete the selection, if chars are selected:
                if (end > start) {
                    editable.delete(start, end);
                }
                editable.insert(start, text);
            }

            @Override
            public void swipeLeft() {
            }

            @Override
            public void swipeRight() {
            }

            @Override
            public void swipeDown() {
            }

            @Override
            public void swipeUp() {
            }
        };
        keyboardView.setOnKeyboardActionListener(keyboardActionListener);
    }

    /**
     * Make the keyboard visible, and hide the system keyboard for view.
     *
     * @param view The view that wants to show the keyboard.
     */
    public void show(@Nullable View view) {
        if (view != null) {
            inputMethodManager.hideSoftInputFromWindow(view.getWindowToken(), 0);
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        }

        keyboardView.setVisibility(View.VISIBLE);
        keyboardView.setEnabled(true);
    }

    /**
     * Make the keyboard invisible.
     */
    public void hide() {
        keyboardView.setVisibility(View.GONE);
        keyboardView.setEnabled(false);
    }

    /**
     * Is the keyboard visible?
     *
     * @return `true` when visible.
     */
    public boolean isVisible() {
        return keyboardView.getVisibility() == View.VISIBLE;
    }

    @NonNull
    public static InputFilter getFilter() {
        final DecimalFormatSymbols symbols = DecimalFormatSymbols.getInstance();
        final char decimalSeparator = symbols.getDecimalSeparator();
        final char decimalMoneySeparator = symbols.getMonetaryDecimalSeparator();
        final char minusSign = symbols.getMinusSign();
        final char zeroDigit = symbols.getZeroDigit();
        final String accepted = ACCEPTED + decimalSeparator + decimalMoneySeparator + minusSign + zeroDigit;
        return DigitsKeyListener.getInstance(accepted);
    }

    public static boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_ENTER) {
            return true;
        }
        int primaryCode = event.getUnicodeChar();
        return primaryCode == KEY_CODE_EVALUATE;
    }

    public static CalculatorKeyboardView rebind(
        @NonNull ViewGroup parent,
        @NonNull CalculatorKeyboardView keyboardView,
        @Nullable CalculatorEditText calculatorEditText
    ) {
        parent.removeView(keyboardView);
        final LayoutInflater layoutInflater = LayoutInflater.from(parent.getContext());
        keyboardView = (CalculatorKeyboardView) layoutInflater.inflate(R.layout.kbd_calculator, parent, false);
        parent.addView(keyboardView);
        if (calculatorEditText != null) {
            calculatorEditText.bindKeyboard(keyboardView);
        }
        return keyboardView;
    }
}
