/**
 * Copyright 2013 Maarten Pennings extended by SimplicityApks
 *
 *
 * Modified by:
 * Copyright 2015 Àlex Magaz Graça <rivaldi8@gmail.com>
 * Copyright 2015 Ngewi Fet <ngewif@gmail.com>
 *
 *
 *
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 *
 * If you use this software in a product, an acknowledgment in the product
 * documentation would be appreciated but is not required.
 */
package org.gnucash.android.ui.util.widget

import android.app.Activity
import android.content.Context
import android.inputmethodservice.KeyboardView.OnKeyboardActionListener
import android.provider.Settings
import android.text.InputFilter
import android.text.Selection
import android.text.method.DigitsKeyListener
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import androidx.core.view.isVisible
import org.gnucash.android.R
import org.gnucash.android.app.getActivity
import org.gnucash.android.inputmethodservice.CalculatorKeyboardView
import java.text.DecimalFormatSymbols
import kotlin.math.max

/**
 * When an activity hosts a keyboardView, this class allows several EditText's to register for it.
 *
 *
 * Known issues:
 * - It's not possible to select text.
 * - When in landscape, the EditText is covered by the keyboard.
 * - No i18n.
 *
 * @author Maarten Pennings, extended by SimplicityApks
 * @author Àlex Magaz Graça <rivaldi8@gmail.com>
 * @author Ngewi Fet <ngewif@gmail.com>
 */
class CalculatorKeyboard(
    /**
     * A link to the KeyboardView that is used to render this CalculatorKeyboard.
     */
    private val keyboardView: CalculatorKeyboardView
) {
    private val window: Window? get() = keyboardView.getActivity()?.window
    private val inputMethodManager: InputMethodManager
    private val isHapticFeedback: Boolean

    /**
     * Returns true if the haptic feedback is enabled.
     *
     * @return true if the haptic feedback is enabled in the system settings.
     */
    private fun isHapticFeedbackEnabled(context: Context): Boolean {
        return Settings.System.getInt(
            context.contentResolver,
            Settings.System.HAPTIC_FEEDBACK_ENABLED,
            0
        ) != 0
    }

    /**
     * Create a custom keyboard, that uses the KeyboardView (with resource id <var>viewId</var>) of the <var>host</var> activity,
     * and load the keyboard layout from xml file <var>layoutId</var> (see [Keyboard] for description).
     * Note that the <var>host</var> activity must have a <var>KeyboardView</var> in its layout (typically aligned with the bottom of the activity).
     * Note that the keyboard layout xml file may include key codes for navigation; see the constants in this class for their values.
     *
     * @param keyboardView KeyboardView in the layout
     */
    init {
        val context = keyboardView.context
        inputMethodManager =
            context.getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
        isHapticFeedback = isHapticFeedbackEnabled(context)

        // Hide the standard keyboard initially
        val keyboardActionListener = object : OnKeyboardActionListener {
            override fun onKey(primaryCode: Int, keyCodes: IntArray) {
                val window = window ?: return
                val focusCurrent = (window.currentFocus as? CalculatorEditText) ?: return
                val editable = focusCurrent.getText() ?: return

                when (primaryCode) {
                    KEY_CODE_DELETE -> {
                        val start = Selection.getSelectionStart(editable)
                        val end = Selection.getSelectionEnd(editable)
                        editable.delete(max((start - 1), 0), end)
                    }

                    KEY_CODE_CLEAR -> editable.clear()

                    KEY_CODE_EVALUATE -> focusCurrent.evaluate()
                }
            }

            override fun onPress(primaryCode: Int) {
                if (primaryCode != 0 && isHapticFeedback) {
                    keyboardView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                }
            }

            override fun onRelease(primaryCode: Int) = Unit

            override fun onText(text: CharSequence?) {
                if (text.isNullOrEmpty()) {
                    return
                }
                val focusCurrent = (window?.currentFocus as? CalculatorEditText) ?: return
                val editable = focusCurrent.getText() ?: return

                val start = Selection.getSelectionStart(editable)
                val end = Selection.getSelectionEnd(editable)
                // delete the selection, if chars are selected:
                if (end > start) {
                    editable.delete(start, end)
                }
                editable.insert(start, text)
            }

            override fun swipeLeft() = Unit

            override fun swipeRight() = Unit

            override fun swipeDown() = Unit

            override fun swipeUp() = Unit
        }
        keyboardView.onKeyboardActionListener = keyboardActionListener
    }

    /**
     * Make the keyboard visible, and hide the system keyboard for view.
     *
     * @param view The view that wants to show the keyboard.
     */
    fun show(view: View?) {
        if (view != null) {
            inputMethodManager.hideSoftInputFromWindow(view.windowToken, 0)
            window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
        }

        keyboardView.isVisible = true
        keyboardView.isEnabled = true
    }

    /**
     * Make the keyboard invisible.
     */
    fun hide() {
        keyboardView.isVisible = false
        keyboardView.isEnabled = false
    }

    /**
     * Is the keyboard visible?
     *
     * @return `true` when visible.
     */
    val isVisible: Boolean
        get() = keyboardView.isVisible

    companion object {
        private const val ACCEPTED = "0123456789١٢٣٤٥٦٧٨٩+*/()"
        private const val KEY_CODE_CLEAR = CalculatorKeyboardView.KEY_CODE_CLEAR
        private const val KEY_CODE_DELETE = CalculatorKeyboardView.KEY_CODE_DELETE
        private const val KEY_CODE_EVALUATE = CalculatorKeyboardView.KEY_CODE_EVALUATE

        val filter: InputFilter
            get() {
                val symbols = DecimalFormatSymbols.getInstance()
                val decimalSeparator = symbols.decimalSeparator
                val decimalMoneySeparator = symbols.monetaryDecimalSeparator
                val minusSign = symbols.minusSign
                val zeroDigit = symbols.zeroDigit
                val accepted: String =
                    ACCEPTED + decimalSeparator + decimalMoneySeparator + minusSign + zeroDigit
                return DigitsKeyListener.getInstance(accepted)
            }

        fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
            if (keyCode == KeyEvent.KEYCODE_ENTER) {
                return true
            }
            val primaryCode = event.unicodeChar
            return primaryCode == KEY_CODE_EVALUATE
        }

        fun rebind(
            parent: ViewGroup,
            keyboardView: CalculatorKeyboardView,
            calculatorEditText: CalculatorEditText?
        ): CalculatorKeyboardView {
            var keyboardView = keyboardView
            parent.removeView(keyboardView)
            val layoutInflater = LayoutInflater.from(parent.context)
            keyboardView = layoutInflater.inflate(
                R.layout.kbd_calculator,
                parent,
                false
            ) as CalculatorKeyboardView
            parent.addView(keyboardView)
            calculatorEditText?.bindKeyboard(keyboardView)
            return keyboardView
        }
    }
}
