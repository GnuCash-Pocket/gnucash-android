/*
 * Copyright (c) 2015 Ngewi Fet <ngewif@gmail.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gnucash.android.ui.util.widget

import android.annotation.SuppressLint
import android.content.Context
import android.text.InputType
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.widget.AppCompatEditText
import androidx.core.view.isVisible
import org.gnucash.android.R
import org.gnucash.android.app.getActivity
import org.gnucash.android.databinding.KbdCalculatorBinding
import org.gnucash.android.inputmethodservice.CalculatorKeyboardView
import org.gnucash.android.model.Commodity
import org.gnucash.android.model.Money
import org.gnucash.android.ui.text.DefaultTextWatcher
import org.gnucash.android.ui.util.widget.CalculatorKeyboard.Companion.filter
import org.gnucash.android.util.AmountParser
import timber.log.Timber
import java.math.BigDecimal
import java.text.DecimalFormat
import java.text.NumberFormat
import java.text.ParseException
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList

/**
 * A custom EditText which supports computations and uses a custom calculator keyboard.
 *
 * After the view is inflated, make sure to call [.bindKeyboard]
 * with the view from your layout where the calculator keyboard should be displayed.
 *
 * @author Ngewi Fet <ngewif@gmail.com>
 */
class CalculatorEditText @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : AppCompatEditText(context, attrs) {
    private var keyboard: CalculatorKeyboard? = null

    /**
     * Flag which is set if the contents of this view have been modified
     */
    private var isContentModified = false
    private var originalText: String? = ""
    private val formatter = (NumberFormat.getInstance(Locale.getDefault()) as DecimalFormat).apply {
        minimumFractionDigits = 0
        isGroupingUsed = false
    }
    private var onBackPressedCallback: OnBackPressedCallback? = null
    private val onValueChangedListeners = CopyOnWriteArrayList<OnValueChangedListener>()

    /**
     * Returns the currency used for computations
     *
     * @return ISO 4217 currency
     */
    var commodity: Commodity = Commodity.DEFAULT_COMMODITY

    val isInputModified: Boolean get() = isContentModified

    /**
     * Initialize.
     */
    init {
        background = null
        isSingleLine = true

        // Disable spell check (hex strings look like words to Android)
        setInputType(InputType.TYPE_NULL)
        setRawInputType(InputType.TYPE_CLASS_NUMBER)

        addFilter(filter)

        // Disable system keyboard appearing on long-press, but for some reason, this prevents the text selection from working.
        setShowSoftInputOnFocus(false)

        addTextChangedListener(DefaultTextWatcher { s ->
            isContentModified = originalText != s.toString()
        })

        setOnEditorActionListener(object : OnEditorActionListener {
            override fun onEditorAction(v: TextView, actionId: Int, event: KeyEvent?): Boolean {
                if ((actionId and EditorInfo.IME_MASK_ACTION) > EditorInfo.IME_ACTION_NONE) {
                    evaluate()
                    return true
                }
                return false
            }
        })

        onFocusChangeListener = object : OnFocusChangeListener {
            override fun onFocusChange(v: View?, hasFocus: Boolean) {
                if (v !== this@CalculatorEditText) return
                if (hasFocus) {
                    setSelection(getText()!!.length)
                    showKeyboard()
                } else {
                    hideKeyboard()
                    evaluate()
                }
            }
        }

        // NOTE By setting the on click listener we can show the custom keyboard again,
        // by tapping on an edit box that already had focus (but that had the keyboard hidden).
        setOnClickListener { v ->
            if (v !== this@CalculatorEditText) return@setOnClickListener
            showKeyboard()
        }

        registerBackPressed()
    }

    private fun bindKeyboard(calculatorKeyboard: CalculatorKeyboard?) {
        keyboard = calculatorKeyboard

        // Although this handler doesn't make sense, if removed, the standard keyboard
        // shows up in addition to the calculator one when the EditText gets a touch event.
        @SuppressLint("ClickableViewAccessibility")
        setOnTouchListener { _, event ->
            onTouchEvent(event)
            return@setOnTouchListener false
        }
    }

    /**
     * Initializes listeners on the EditText
     *
     * @param keyboardView the calculator keyboard view.
     */
    fun bindKeyboard(keyboardView: CalculatorKeyboardView) {
        bindKeyboard(CalculatorKeyboard(keyboardView))
    }

    /**
     * Initializes listeners on the EditText
     *
     * @param keyboardBinding the calculator keyboard binding.
     */
    fun bindKeyboard(keyboardBinding: KbdCalculatorBinding) {
        bindKeyboard(keyboardBinding.calculatorKeyboard)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (CalculatorKeyboard.onKeyDown(keyCode, event)) {
            evaluate()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    /**
     * Evaluates the arithmetic expression in the EditText and sets the text property
     *
     * @return Result of arithmetic evaluation which is same as text displayed in EditText
     */
    fun evaluate(): String {
        val amountString = cleanString
        if (amountString.isEmpty()) {
            return ""
        }

        val amount = AmountParser.evaluate(amountString)
        if (amount != null) {
            try {
                val money = Money(amount, commodity)
                // Currently the numerator has a limit of 64 bits.
                money.numerator
            } catch (e: ArithmeticException) {
                error = context.getString(R.string.label_error_invalid_expression)
                Timber.w(e, "Invalid amount: %s", amountString)
                return ""
            }
            this.value = amount
        } else {
            error = context.getString(R.string.label_error_invalid_expression)
            Timber.w("Invalid amount: %s", amountString)
        }
        return getText().toString()
    }

    /**
     * Evaluates the expression in the text and returns true if the result is valid
     *
     * @return @{code true} if the input is valid, `false` otherwise
     */
    val isInputValid: Boolean
        get() {
            val text = evaluate()
            return !text.isEmpty() && error == null
        }

    /**
     * Returns the amount string formatted as a decimal in Locale.US and trimmed.
     * This also converts decimal operators from other locales into a period (.)
     *
     * @return String with the amount in the EditText or empty string if there is no input
     */
    val cleanString: String
        //convert "ARABIC DECIMAL SEPARATOR" U+066B into period
        //convert "ARABIC-INDIC DIGIT ZERO" U+0660 into western zero and do the same for all digits
        get() = getText().toString()
            .replace("[,\u066B]".toRegex(), ".")
            .replace("\u0660".toRegex(), "0")
            .replace("\u0661".toRegex(), "1")
            .replace("\u0662".toRegex(), "2")
            .replace("\u0662".toRegex(), "2")
            .replace("\u0663".toRegex(), "3")
            .replace("\u0664".toRegex(), "4")
            .replace("\u0665".toRegex(), "5")
            .replace("\u0666".toRegex(), "6")
            .replace("\u0667".toRegex(), "7")
            .replace("\u0668".toRegex(), "8")
            .replace("\u0669".toRegex(), "9")
            .trim()

    var value: BigDecimal?
        /**
         * Returns the value of the amount in the edit text or null if the field is empty.
         * Performs an evaluation of the expression first
         *
         * @return BigDecimal value
         */
        get() {
            val text = evaluate()
            if (text.isEmpty()) {
                return null
            }
            try { //catch any exceptions in the conversion e.g. if a string with only "-" is entered
                return AmountParser.parse(text)
            } catch (e: ParseException) {
                val msg = "Error parsing amount string \"$text\" from CalculatorEditText"
                Timber.i(e, msg)
                return null
            }
        }
        /**
         * Set the text to the value of `amount` formatted according to the locale.
         *
         * The number of decimal places are determined by the currency set to the view, and the
         * decimal separator is determined by the device locale. There are no thousandths separators.
         *
         * @param amount BigDecimal amount
         */
        set(amount) {
            setValue(amount, false)
        }

    fun setValue(amount: BigDecimal?, isOriginal: Boolean) {
        formatter.maximumFractionDigits = commodity.smallestFractionDigits
        val formatted = if (amount != null) formatter.format(amount) else ""

        if (isOriginal) {
            originalText = formatted
        } else {
            notifyEvaluate(amount)
        }

        setTextToEnd(formatted)
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (isVisible && isFocused) {
            showKeyboard()
        }
    }

    private fun registerBackPressed() {
        var callback = onBackPressedCallback
        if (callback == null) {
            val activity = (getActivity() as? ComponentActivity) ?: return
            callback = object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (!hideKeyboard()) {
                        isEnabled = false
                        activity.onBackPressed()
                    }
                }
            }
            onBackPressedCallback = callback
            activity.onBackPressedDispatcher.addCallback(callback)
        }
    }

    fun showKeyboard(): Boolean {
        val keyboard = this.keyboard ?: return false
        if (!keyboard.isVisible) {
            keyboard.show(this)
            return true
        }
        return false
    }

    fun hideKeyboard(): Boolean {
        val keyboard = this.keyboard ?: return false
        if (keyboard.isVisible) {
            keyboard.hide()
            return true
        }
        return false
    }

    interface OnValueChangedListener {
        fun onValueChanged(value: BigDecimal?)
    }

    fun addValueChangedListener(listener: OnValueChangedListener) {
        onValueChangedListeners.add(listener)
    }

    private fun notifyEvaluate(value: BigDecimal?) {
        for (listener in onValueChangedListeners) {
            listener.onValueChanged(value)
        }
    }
}
