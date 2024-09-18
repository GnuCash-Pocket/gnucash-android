package org.gnucash.android.inputmethodservice

import android.content.Context
import android.inputmethodservice.KeyboardView
import android.util.AttributeSet
import android.widget.Button
import android.widget.ImageButton
import android.widget.TableLayout
import java.text.DecimalFormatSymbols
import org.gnucash.android.R

class CalculatorKeyboardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : TableLayout(context, attrs) {

    var onKeyboardActionListener: KeyboardView.OnKeyboardActionListener? = null

    private val decimalSeparator: String
    private val minusSign: String
    private val zeroDigit: String
    private val keyCodes = IntArray(0)

    init {
        val symbols = DecimalFormatSymbols.getInstance()
        decimalSeparator = symbols.monetaryDecimalSeparator.toString()
        minusSign = symbols.minusSign.toString()
        zeroDigit = symbols.zeroDigit.toString()
    }

    override fun onFinishInflate() {
        super.onFinishInflate()
        findViewById<Button>(R.id.key_clear).apply {
            setOnClickListener { onKeyboardActionListener?.onKey(KEY_CODE_CLEAR, keyCodes) }
        }
        findViewById<Button>(R.id.key_0).apply {
            text = zeroDigit
            setOnClickListener { onKeyboardActionListener?.onText(zeroDigit) }
        }
        findViewById<Button>(R.id.key_1).apply {
            setOnClickListener { onKeyboardActionListener?.onText("1") }
        }
        findViewById<Button>(R.id.key_2).apply {
            setOnClickListener { onKeyboardActionListener?.onText("2") }
        }
        findViewById<Button>(R.id.key_3).apply {
            setOnClickListener { onKeyboardActionListener?.onText("3") }
        }
        findViewById<Button>(R.id.key_4).apply {
            setOnClickListener { onKeyboardActionListener?.onText("4") }
        }
        findViewById<Button>(R.id.key_5).apply {
            setOnClickListener { onKeyboardActionListener?.onText("5") }
        }
        findViewById<Button>(R.id.key_6).apply {
            setOnClickListener { onKeyboardActionListener?.onText("6") }
        }
        findViewById<Button>(R.id.key_7).apply {
            setOnClickListener { onKeyboardActionListener?.onText("7") }
        }
        findViewById<Button>(R.id.key_8).apply {
            setOnClickListener { onKeyboardActionListener?.onText("8") }
        }
        findViewById<Button>(R.id.key_9).apply {
            setOnClickListener { onKeyboardActionListener?.onText("9") }
        }
        findViewById<Button>(R.id.key_decimal).apply {
            text = decimalSeparator
            setOnClickListener { onKeyboardActionListener?.onText(decimalSeparator) }
        }
        findViewById<ImageButton>(R.id.key_delete).apply {
            setOnClickListener { onKeyboardActionListener?.onKey(KEY_CODE_DELETE, keyCodes) }
        }
        findViewById<Button>(R.id.key_divide).apply {
            setOnClickListener { onKeyboardActionListener?.onText("/") }
        }
        findViewById<Button>(R.id.key_equals).apply {
            setOnClickListener { onKeyboardActionListener?.onKey(KEY_CODE_EVALUATE, keyCodes) }
        }
        findViewById<Button>(R.id.key_left_parenthesis).apply {
            setOnClickListener { onKeyboardActionListener?.onText("(") }
        }
        findViewById<Button>(R.id.key_right_parenthesis).apply {
            setOnClickListener { onKeyboardActionListener?.onText(")") }
        }
        findViewById<Button>(R.id.key_minus).apply {
            text = minusSign
            setOnClickListener { onKeyboardActionListener?.onText(minusSign) }
        }
        findViewById<Button>(R.id.key_multiply).apply {
            setOnClickListener { onKeyboardActionListener?.onText("*") }
        }
        findViewById<Button>(R.id.key_plus).apply {
            setOnClickListener { onKeyboardActionListener?.onText("+") }
        }
    }

    companion object {
        const val KEY_CODE_CLEAR: Int = -3
        const val KEY_CODE_DELETE: Int = -5
        const val KEY_CODE_EVALUATE: Int = '='.code
    }
}