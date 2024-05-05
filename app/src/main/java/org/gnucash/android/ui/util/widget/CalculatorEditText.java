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
package org.gnucash.android.ui.util.widget;

import android.content.Context;
import android.content.res.TypedArray;
import android.inputmethodservice.KeyboardView;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.annotation.XmlRes;
import androidx.appcompat.widget.AppCompatEditText;

import org.gnucash.android.R;
import org.gnucash.android.model.Commodity;
import org.gnucash.android.model.Money;
import org.gnucash.android.ui.common.FormActivity;
import org.gnucash.android.util.AmountParser;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.Locale;

import timber.log.Timber;

/**
 * A custom EditText which supports computations and uses a custom calculator keyboard.
 * <p>After the view is inflated, make sure to call {@link #bindListeners(KeyboardView)}
 * with the view from your layout where the calculator keyboard should be displayed.</p>
 *
 * @author Ngewi Fet <ngewif@gmail.com>
 */
public class CalculatorEditText extends AppCompatEditText {
    @Nullable
    private CalculatorKeyboard mCalculatorKeyboard;

    private Commodity mCommodity = Commodity.DEFAULT_COMMODITY;

    /**
     * Flag which is set if the contents of this view have been modified
     */
    private boolean isContentModified = false;

    @XmlRes
    private int mCalculatorKeysLayout;
    private KeyboardView mCalculatorKeyboardView;
    private final DecimalFormat formatter = (DecimalFormat) NumberFormat.getInstance(Locale.getDefault());

    public CalculatorEditText(Context context) {
        super(context, null);
    }

    public CalculatorEditText(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public CalculatorEditText(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs);
    }

    /**
     * Overloaded constructor
     * Reads any attributes which are specified in XML and applies them
     *
     * @param context Activity context
     * @param attrs   View attributes
     */
    private void init(Context context, AttributeSet attrs) {
        TypedArray a = context.getTheme().obtainStyledAttributes(
                attrs,
                R.styleable.CalculatorEditText,
                0, 0);

        try {
            mCalculatorKeysLayout = a.getResourceId(R.styleable.CalculatorEditText_keyboardKeysLayout, R.xml.calculator_keyboard);
        } finally {
            a.recycle();
        }

        addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                isContentModified = true;
            }
        });

        setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (hasFocus) {
                    setSelection(getText().length());
                } else {
                    evaluate();
                }
            }
        });
    }

    public void bindListeners(final CalculatorKeyboard calculatorKeyboard) {
        mCalculatorKeyboard = calculatorKeyboard;
        setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (hasFocus) {
                    setSelection(getText().length());
                    calculatorKeyboard.showCustomKeyboard(v);
                } else {
                    calculatorKeyboard.hideCustomKeyboard();
                    evaluate();
                }
            }
        });

        setOnClickListener(new OnClickListener() {
            // NOTE By setting the on click listener we can show the custom keyboard again,
            // by tapping on an edit box that already had focus (but that had the keyboard hidden).
            @Override
            public void onClick(View v) {
                calculatorKeyboard.showCustomKeyboard(v);
            }
        });

        // Disable spell check (hex strings look like words to Android)
        setInputType(getInputType() | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);

        // Disable system keyboard appearing on long-press, but for some reason, this prevents the text selection from working.
        setShowSoftInputOnFocus(false);

        // Although this handler doesn't make sense, if removed, the standard keyboard
        // shows up in addition to the calculator one when the EditText gets a touch event.
        setOnTouchListener(new OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                // XXX: Use dispatchTouchEvent()?
                onTouchEvent(event);
                return false;
            }
        });

        Context context = getContext();
        if (context instanceof FormActivity) {
            ((FormActivity) context).setOnBackListener(calculatorKeyboard);
        }
    }

    /**
     * Initializes listeners on the EditText
     */
    public void bindListeners(KeyboardView keyboardView) {
        bindListeners(new CalculatorKeyboard(getContext(), keyboardView, mCalculatorKeysLayout));
    }

    /**
     * Returns the calculator keyboard instantiated by this EditText
     *
     * @return CalculatorKeyboard
     */
    public CalculatorKeyboard getCalculatorKeyboard() {
        return mCalculatorKeyboard;
    }

    /**
     * Returns the view Id of the keyboard view
     *
     * @return Keyboard view
     */
    public KeyboardView getCalculatorKeyboardView() {
        return mCalculatorKeyboardView;
    }

    /**
     * Set the keyboard view used for displaying the keyboard
     *
     * @param calculatorKeyboardView Calculator keyboard view
     */
    public void setCalculatorKeyboardView(KeyboardView calculatorKeyboardView) {
        this.mCalculatorKeyboardView = calculatorKeyboardView;
        bindListeners(calculatorKeyboardView);
    }

    /**
     * Returns the XML resource ID describing the calculator keys layout
     *
     * @return XML resource ID
     */
    public @XmlRes int getCalculatorKeysLayout() {
        return mCalculatorKeysLayout;
    }

    /**
     * Sets the XML resource describing the layout of the calculator keys
     *
     * @param calculatorKeysLayout XML resource ID
     */
    public void setCalculatorKeysLayout(@XmlRes int calculatorKeysLayout) {
        this.mCalculatorKeysLayout = calculatorKeysLayout;
        bindListeners(mCalculatorKeyboardView);
    }

    /**
     * Sets the calculator keyboard to use for this EditText
     *
     * @param keyboard Properly initialized calculator keyboard
     */
    public void setCalculatorKeyboard(CalculatorKeyboard keyboard) {
        this.mCalculatorKeyboard = keyboard;
    }

    /**
     * Returns the currency used for computations
     *
     * @return ISO 4217 currency
     */
    public Commodity getCommodity() {
        return mCommodity;
    }

    /**
     * Sets the commodity to use for calculations
     * The commodity determines the number of decimal places used
     *
     * @param commodity ISO 4217 currency
     */
    public void setCommodity(Commodity commodity) {
        this.mCommodity = commodity;
    }

    /**
     * Evaluates the arithmetic expression in the EditText and sets the text property
     *
     * @return Result of arithmetic evaluation which is same as text displayed in EditText
     */
    public String evaluate() {
        String amountString = getCleanString();
        if (TextUtils.isEmpty(amountString)) {
            return "";
        }

        BigDecimal amount = AmountParser.evaluate(amountString);
        if (amount != null) {
            try {
                Money money = new Money(amount, getCommodity());
                // Currently the numerator has a limit of 64 bits.
                money.getNumerator();
            } catch (ArithmeticException e) {
                setError(getContext().getString(R.string.label_error_invalid_expression));
                Timber.w(e, "Invalid expression: %s", amountString);
                return "";
            }
            setValue(amount);
        } else {
            setError(getContext().getString(R.string.label_error_invalid_expression));
            Timber.w("Invalid expression: %s", amountString);
        }
        return getText().toString();
    }

    /**
     * Evaluates the expression in the text and returns true if the result is valid
     *
     * @return @{code true} if the input is valid, {@code false} otherwise
     */
    public boolean isInputValid() {
        String text = evaluate();
        return !text.isEmpty() && getError() == null;
    }

    /**
     * Returns the amount string formatted as a decimal in Locale.US and trimmed.
     * This also converts decimal operators from other locales into a period (.)
     *
     * @return String with the amount in the EditText or empty string if there is no input
     */
    public String getCleanString() {
        return getText().toString().replaceAll(",", ".").trim();
    }

    /**
     * Returns true if the content of this view has been modified
     *
     * @return {@code true} if content has changed, {@code false} otherwise
     */
    public boolean isInputModified() {
        return this.isContentModified;
    }

    /**
     * Returns the value of the amount in the edit text or null if the field is empty.
     * Performs an evaluation of the expression first
     *
     * @return BigDecimal value
     */
    public @Nullable BigDecimal getValue() {
        String text = evaluate();
        if (text.isEmpty()) {
            return null;
        }
        try { //catch any exceptions in the conversion e.g. if a string with only "-" is entered
            return AmountParser.parse(text);
        } catch (ParseException e) {
            String msg = "Error parsing amount string \"" + text + "\" from CalculatorEditText";
            Timber.i(e, msg);
            return null;
        }
    }

    /**
     * Set the text to the value of {@code amount} formatted according to the locale.
     * <p>The number of decimal places are determined by the currency set to the view, and the
     * decimal separator is determined by the device locale. There are no thousandths separators.</p>
     *
     * @param amount BigDecimal amount
     */
    public void setValue(BigDecimal amount) {
        formatter.setMinimumFractionDigits(0);
        formatter.setMaximumFractionDigits(mCommodity.getSmallestFractionDigits());
        formatter.setGroupingUsed(false);
        String resultString = formatter.format(amount);

        setText(resultString);
        setSelection(resultString.length());
    }
}
